/*
 * Copyright 2023 HM Revenue & Customs
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
import model.EvaluationResults.{Green, Red}
import model._
import model.exchange.FsbScoresAndFeedback
import model.persisted.fsb.ScoresAndFeedback
import model.persisted.{ContactDetails, FsbTestGroup, SchemeEvaluationResult}
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import repositories.SchemeRepository
import repositories.application.GeneralApplicationMongoRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import services.scheme.SchemePreferencesService
import testkit.MockitoImplicits._
import testkit.{ExtendedTimeout, UnitSpec}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class FsbServiceSpec extends UnitSpec with ExtendedTimeout with Schemes {

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
      val res = FsbTestGroup(List(SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Green.toString)))
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(res))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(res.evaluation.result)
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_PASSED)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)).thenReturnAsync()
      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)
    }

    "evaluate scheme to Final FAILED if results are red and no more schemes selected" in new TestFixture {
      val curSchemeStatus = List(SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Green.toString))
      val res = FsbTestGroup(List(SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Red.toString)))
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(res))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(), res.evaluation.result)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()
      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
    }

    // Old test before we replaced EAC_DS with GES_DS
    "fail to evaluate scheme GES_DS if FCO results were not submitted" ignore new TestFixture {
      val curSchemeStatus = List(
        SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Red.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString)
      )
      val res = FsbTestGroup(List(SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString)))
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

    // Old test before we replaced EAC_DS with GES_DS
    "evaluate DS as failed, and then GES_DS as failed too, but do not evaluate GES as EAC evaluation " +
      "hasn't happened yet" ignore new TestFixture {
      val curSchemeStatus = List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      )

      val res = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Red.toString)
      ))
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(res))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      // DS
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(),
        List(
          SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Red.toString),
          SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString),
          SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
        )
      )).thenReturnAsync()

      // GES_DS
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(),
        List(
          SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Red.toString),
          SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Red.toString),
          SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)

        )
      )).thenReturnAsync()

      // more fsb required
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any(), any())).thenReturnAsync()

      Await.result(service.evaluateFsbCandidate(uid)(hc), 2.seconds)
      verify(mockApplicationRepo, times(2)).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)
    }

    // Old test before we replaced EAC_DS with GES_DS
    "evaluate scheme GES_DS as failed, and then GES as failed, but finally DS passed" ignore new TestFixture {
      val curSchemeStatus = List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString),
        SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Red.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Green.toString)
      )
      val res = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Red.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Green.toString)
      ))
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(res))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      // GES_DS
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(),
        List(
          SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Red.toString),
          SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Red.toString),
          SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString),
          SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Green.toString)
        )
      )).thenReturnAsync()

      // GES
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(uid.toString(),
        List(
          SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Red.toString),
          SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Red.toString),
          SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Red.toString),
          SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Green.toString)
        )
      )).thenReturnAsync()

      // DS
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_PASSED)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)).thenReturnAsync()
      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)
    }

    /**
      * DiplomaticAndDevelopmentEconomics (code: GES-DS, fsbType: EAC_DS) TODO: clarify with Paul to rename fsbType to EAC_FCO
      * - GovernmentEconomicsService (code: GES, fsbType: EAC)
      * - DiplomaticAndDevelopment (code: DS, fsbType: FCO)
      *
      * At FSB the separate parts are named correctly:
      * EAC pass/fail FCO pass/fail previous actual outcome expected outcome (now fixed)
      * pass          fail          offered a job           fail
      *
      */
    // Old test before we replaced EAC_DS with GES_DS
    "Pass the candidate who is only in the running for GES-DS if the candidate passes " +
      "both the EAC and FCO parts of the fsb" ignore new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticAndDevelopmentEconomics)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_PASSED)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)).thenReturnAsync()
      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)
    }

    // This test replaces the one above
    "Pass the candidate who is only in the running for GES-DS if the candidate passes the GES_DS fsb" in new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticAndDevelopmentEconomics)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_PASSED)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)).thenReturnAsync()
      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER)
    }

    // Old test before we replaced EAC_DS with GES_DS
    "Fail the candidate who is only in the running for GES-DS if the candidate passes " +
      "the EAC part but fails the DS (FCO) part of the GES_DS fsb" ignore new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Red.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticAndDevelopmentEconomics)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()

      // Mocking required to send the failure email
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any(), any())).thenReturnAsync()

      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
      // verify the failure email is sent out
      verify(mockEmailClient).notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any[HeaderCarrier], any[ExecutionContext])
    }

    // This test replaces the one above
    "Fail the candidate who is only in the running for GES-DS if the candidate fails the GES_DS fsb" in new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Red.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticAndDevelopmentEconomics)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()

      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
      // verify no failure email is sent out
      verifyNoInteractions(mockEmailClient)
    }

    // Old test before we replaced EAC_DS with GES_DS
    "Fail the candidate who is only in the running for GES-DS if the candidate fails the EAC part but passes " +
      "the DS (FCO) part of the GES_DS fsb. Note the candidate should not be invited to the DS part " +
      "if they fail the EAC part (so this should never happen unless they also have DS as a separate scheme)" ignore new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Red.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticAndDevelopmentEconomics)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString))
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()

      // Mocking required to send the failure email
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any(), any())).thenReturnAsync()

      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
      // verify the failure email is sent out
      verify(mockEmailClient).notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any[HeaderCarrier], any[ExecutionContext])
    }

    // Old test before we replaced EAC_DS with GES_DS
    "Fail the candidate who is in the running for GES-DS and DS schemes who passes the EAC part but fails the FCO part of " +
      "the GES_DS fsb" ignore new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Red.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticAndDevelopmentEconomics, DSSchemeIds.DiplomaticAndDevelopment)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Green.toString)
      )
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()

      // Mocking required to send the failure email
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any(), any())).thenReturnAsync()

      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
      // verify the failure email is sent out
      verify(mockEmailClient).notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any[HeaderCarrier], any[ExecutionContext])
    }

    // Replaces the test above
    "Set the candidate to ALL_FSBS_AND_FSACS_FAILED who is in the running for GES and GES-DS schemes who fails the GES fsb and " +
      "so does not take the GES-DS fsb" in new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Red.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.GovernmentEconomicsService, DSSchemeIds.DiplomaticAndDevelopmentEconomics)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString)
      )
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturnAsync()

      // Mocking required to send the failure email
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any(), any())).thenReturnAsync()

      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
      // verify the failure email is sent out
      verify(mockEmailClient).notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any[HeaderCarrier], any[ExecutionContext])
    }

    // Old test before we replaced EAC_DS with GES_DS
    "Fail the candidate who is in the running for GES-DS and GES schemes who fails the EAC part and passes the FCO part of " +
      "the GES_DS fsb" ignore new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopment, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Red.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticAndDevelopmentEconomics, DSSchemeIds.GovernmentEconomicsService)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      )
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturnAsync()
      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)).thenReturnAsync()

      // Mocking required to send the failure email
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any(), any())).thenReturnAsync()

      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
      // verify the failure email is sent out
      verify(mockEmailClient).notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any[HeaderCarrier], any[ExecutionContext])
    }

    // Replaces the test above
    "Set the candidate to FSB_FAILED who is in the running for GES-DS and GES schemes who fails GES-DS fsb and has not yet taken the " +
      "EAC fsb" in new TestFixture {
      val fsbResult = FsbTestGroup(List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Red.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      ))
      when(mockFsbRepo.findByApplicationId(uid.toString())).thenReturnAsync(Some(fsbResult))

      override val schemes = List(DSSchemeIds.DiplomaticAndDevelopmentEconomics, DSSchemeIds.GovernmentEconomicsService)
      override val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
      when(mockSchemePreferencesService.find(uid.toString())).thenReturnAsync(selectedSchemes)

      // This is the css after FSAC and before FSB evaluation
      val curSchemeStatus = List(
        SchemeEvaluationResult(DSSchemeIds.DiplomaticAndDevelopmentEconomics, Green.toString),
        SchemeEvaluationResult(DSSchemeIds.GovernmentEconomicsService, Green.toString)
      )
      when(mockApplicationRepo.getCurrentSchemeStatus(uid.toString())).thenReturnAsync(curSchemeStatus)

      when(mockApplicationRepo.addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)).thenReturnAsync()
      when(mockFsbRepo.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturnAsync()

      // Mocking required to send the failure email
      when(mockApplicationRepo.find(uid.toString())).thenReturnAsync(Some(cand1))
      when(mockContactDetailsRepo.find(cand1.userId)).thenReturnAsync(cd1)
      when(mockEmailClient.notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any(), any())).thenReturnAsync()

      service.evaluateFsbCandidate(uid)(hc).futureValue
      verify(mockApplicationRepo).addProgressStatusAndUpdateAppStatus(uid.toString(), ProgressStatuses.FSB_FAILED)
      // verify the failure email is sent out
      verify(mockEmailClient).notifyCandidateOnFinalFailure(eqTo(cd1.email), eqTo(cand1.name))(any[HeaderCarrier], any[ExecutionContext])
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

    val cand1 = Candidate(userId = "123", applicationId = None, testAccountId = None, Some("t@t.com"), Some("Leia"), Some("Amadala"),
      preferredName = None, dateOfBirth = None, address = None, postCode = None, country = None, applicationRoute = None,
      applicationStatus = None)
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
      DigitalDataTechnologyAndCyber,
      DiplomaticAndDevelopment,
      DiplomaticAndDevelopmentEconomics,
      GovernmentEconomicsService
    )

    val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
  }
}
