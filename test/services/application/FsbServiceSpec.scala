/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services.application

import connectors.EmailClient
import model.EvaluationResults.{ Green, Red }
import model.persisted.{ ContactDetails, FsbTestGroup, SchemeEvaluationResult }
import model._
import org.mockito.Mockito._
import repositories.application.GeneralApplicationMongoRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import repositories.{ SchemeRepository, SchemeYamlRepository }
import testkit.MockitoImplicits._
import testkit.{ ExtendedTimeout, UnitSpec }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }

import scala.concurrent.Await
import scala.concurrent.duration._
import uk.gov.hmrc.http.HeaderCarrier

class FsbServiceSpec extends UnitSpec with ExtendedTimeout {


  "fsb evaluation" must {
    "evaluate scheme to Eligible for Job Offer if results are Green" in new TestFixture {
      val res = FsbTestGroup(List(SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString)))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(res))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(res.evaluation.result)
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_PASSED)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)).thenReturnAsync()
      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)
    }

    "evaluate scheme to Final FAILED if results are red and no more schemes selected" in new TestFixture {
      val curSchemeStatus = List(SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString))
      val res = FsbTestGroup(List(SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Red.toString)))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(res))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(), res.evaluation.result)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()
      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
    }

    "fail to evaluate scheme GES_DS if FCO results where not submitted" in new TestFixture {
      val curSchemeStatus = List(
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Red.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString)
      )
      val res = FsbTestGroup(List(SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString)))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(res))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(), res.evaluation.result)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()
      intercept[IllegalArgumentException] {
        Await.result(service.evaluateFsbCandidate(uid)(hc), 1.second)
      }
    }


    "evaluate DS as failed, and then GES_DS as failed too, but do not evaluate GES as EAC evaluation hasn't happened yet" in new TestFixture {
      val curSchemeStatus = List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      )

      val res = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Red.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(res))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      // DS
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(),
        List(
          SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Red.toString),
          SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString),
          SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
        )
      )).thenReturnAsync()

      // GES_DS
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(),
        List(
          SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Red.toString),
          SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Red.toString),
          SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)

        )
      )).thenReturnAsync()

      // more fsb required
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any())).thenReturnAsync()

      Await.result(service.evaluateFsbCandidate(uid)(hc), 2.seconds)
      verify(mockApplicationRepo, times(2)).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)
    }

    "evaluate scheme GES_DS as failed, and then GES as failed, but finally DS passed" in new TestFixture {
      val curSchemeStatus = List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString),
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Red.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString)
      )
      val res = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Red.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(res))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      // GES_DS
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(),
        List(
          SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Red.toString),
          SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Red.toString),
          SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString),
          SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString)
        )
      )).thenReturnAsync()

      // GES
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(),
        List(
          SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Red.toString),
          SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Red.toString),
          SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Red.toString),
          SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString)
        )
      )).thenReturnAsync()

      // DS
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_PASSED)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)).thenReturnAsync()
      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)
    }
  }

  trait TestFixture {

    val hc = HeaderCarrier()

    val uid = UniqueIdentifier.randomUniqueIdentifier

    val mockFsbRepo = mock[FsbRepository]
    val mockApplicationRepo = mock[GeneralApplicationMongoRepository]
    val mockContactDetailsRepo = mock[ContactDetailsRepository]
    val mockSchemeRepo = SchemeYamlRepository
    val mockEmailClient = mock[EmailClient]

    val cand1 = Candidate("123", None, Some("t@t.com"), Some("Leia"), Some("Amadala"), None, None, None, None, None, None, None)
    val cd1 = ContactDetails(outsideUk = false, Address("line1a"), Some("123"), Some("UK"), "t@t.com", "12345")

    val service = new FsbService {
      override val fsbRepo: FsbRepository = mockFsbRepo
      override val applicationRepo: GeneralApplicationMongoRepository = mockApplicationRepo
      override val contactDetailsRepo: ContactDetailsRepository = mockContactDetailsRepo
      override val schemeRepo: SchemeRepository = mockSchemeRepo
      override val emailClient: EmailClient = mockEmailClient
    }
  }

}
