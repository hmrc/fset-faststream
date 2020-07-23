/*
 * Copyright 2020 HM Revenue & Customs
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

import connectors.OnlineTestEmailClient
import model.EvaluationResults.{ Green, Red }
import model._
import model.exchange.FsbScoresAndFeedback
import model.persisted.fsb.ScoresAndFeedback
import model.persisted.{ ContactDetails, FsbTestGroup, SchemeEvaluationResult }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories.SchemeRepository
import repositories.application.GeneralApplicationMongoRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import services.scheme.SchemePreferencesService
import testkit.MockitoImplicits._
import testkit.{ ExtendedTimeout, UnitSpec }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Await
import scala.concurrent.duration._

class FsbServiceSpec extends UnitSpec with ExtendedTimeout {

  "find scores and feedback" must {
    "handle no data" in new TestFixture {
      when(mockFsbRepo.findScoresAndFeedback(any[String])).thenReturnAsync(None)
      val result = service.findScoresAndFeedback(appId).futureValue
      result mustBe None
    }

    "handle data" in new TestFixture {
      when(mockFsbRepo.findScoresAndFeedback(any[String])).thenReturnAsync(Some(ScoresAndFeedback(1.12, "feedback")))
      val result = service.findScoresAndFeedback(appId).futureValue
      result mustBe Some(FsbScoresAndFeedback(1.12, "feedback"))
    }
  }

  "fsb evaluation" must {
    "evaluate scheme to Eligible for Job Offer if results are Green" in new TestFixture {
      val res = FsbTestGroup(List(SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString)))
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)
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
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(res))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(), res.evaluation.result)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()
      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
    }

    "fail to evaluate scheme GES_DS if FCO results were not submitted" in new TestFixture {
      val curSchemeStatus = List(
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Red.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString)
      )
      val res = FsbTestGroup(List(SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString)))
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)
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
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)
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
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)
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

    /**
      * DiplomaticServiceEconomists (code: GES-DS, fsbType: EAC_DS) TODO: clarify with Paul to rename fsbType to EAC_FCO
      * - GovernmentEconomicsService (code: GES, fsbType: EAC)
      * - DiplomaticService (code: DS, fsbType: FCO)
      *
      * At FSB the separate parts are named correctly:
      * EAC pass/fail FCO pass/fail previous actual outcome expected outcome (now fixed)
      * pass          fail          offered a job           fail
      *
      */

    "Pass the candidate who is only in the running for GES-DS if the candidate passes " +
      "both the EAC and FCO parts of the fsb" in new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticServiceEconomists)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_PASSED)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)).thenReturnAsync()
      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)
    }

    "Fail the candidate who is only in the running for GES-DS if the candidate passes " +
      "the EAC part but fails the DS (FCO) part of the GES_DS fsb" in new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Red.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticServiceEconomists)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()

      // Mocking required to send the failure email
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any())).thenReturnAsync()

      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
      // verify the failure email is sent out
      verify(mockEmailClient).notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any[HeaderCarrier])
    }

    "Fail the candidate who is only in the running for GES-DS if the candidate fails the EAC part but passes " +
      "the DS (FCO) part of the GES_DS fsb. Note the candidate should not be invited to the DS part " +
      "if they fail the EAC part (so this should never happen unless they also have DS as a separate scheme)" in new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Red.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticServiceEconomists)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()

      // Mocking required to send the failure email
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any())).thenReturnAsync()

      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
      // verify the failure email is sent out
      verify(mockEmailClient).notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any[HeaderCarrier])
    }

    "Fail the candidate who is in the running for GES-DS and DS schemes who passes the EAC part but fails the FCO part of " +
      "the GES_DS fsb" in new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Red.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticServiceEconomists, DSSchemeIds.DiplomaticService)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString)
      )
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()

      // Mocking required to send the failure email
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any())).thenReturnAsync()

      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
      // verify the failure email is sent out
      verify(mockEmailClient).notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any[HeaderCarrier])
    }

    "Fail the candidate who is in the running for GES-DS and GES schemes who fails the EAC part and passes the FCO part of " +
      "the GES_DS fsb" in new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticService, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Red.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticServiceEconomists, DSSchemeIds.GovernmentEconomicsService)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticServiceEconomists, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      )
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()

      // Mocking required to send the failure email
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any())).thenReturnAsync()

      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
      // verify the failure email is sent out
      verify(mockEmailClient).notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any[HeaderCarrier])
    }
  }

  trait TestFixture {

    val hc = HeaderCarrier()
    val uid = UniqueIdentifier.randomUniqueIdentifier
    val appId = "appId"

    val mockApplicationRepo = mock[GeneralApplicationMongoRepository]
    val mockContactDetailsRepo = mock[ContactDetailsRepository]
    val mockFsbRepo = mock[FsbRepository]
    val mockSchemeRepo = mock[SchemeRepository]
    val mockSchemePreferencesService = mock[SchemePreferencesService]
    val mockEmailClient = mock[OnlineTestEmailClient] //TODO:changed type was EmailClient

    val cand1 = Candidate("123", None, None, Some("t@t.com"), Some("Leia"), Some("Amadala"), None, None, None, None, None, None, None)
    val cd1 = ContactDetails(outsideUk = false, Address("line1a"), Some("123"), Some("UK"), "t@t.com", "12345")

    val service = new FsbService(
      mockApplicationRepo,
      mockContactDetailsRepo,
      mockFsbRepo,
      mockSchemeRepo,
      mockSchemePreferencesService,
      mockEmailClient
    )

    val schemes = List(
      SchemeId("DigitalAndTechnology"),
      SchemeId("DiplomaticService"),
      SchemeId("DiplomaticServiceEconomists"),
      SchemeId("GovernmentEconomicsService")
    )

    val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
  }
}
