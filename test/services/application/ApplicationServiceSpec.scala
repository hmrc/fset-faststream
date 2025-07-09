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

import model.Commands.PhoneNumber
import model.EvaluationResults.{Amber, Green, Red}
import model.Exceptions.{LastSchemeWithdrawException, PassMarkEvaluationNotFound, SiftExpiredException}
import model.ProgressStatuses.ProgressStatus
import model._
import model.command._
import model.exchange.sift.SiftAnswersStatus
import model.persisted.{ContactDetails, FsbTestGroup, PassmarkEvaluation, SchemeEvaluationResult}
import model.stc.AuditEvents
import org.mockito.ArgumentMatchers.{any, eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import repositories.onlinetesting._
import repositories.personaldetails.PersonalDetailsRepository
import repositories.schemepreferences.SchemePreferencesRepository
import repositories.sift.ApplicationSiftRepository
import repositories.{AssessorAssessmentScoresMongoRepository, MediaRepository, ReviewerAssessmentScoresMongoRepository, TestSchemeRepository}
import scheduler.fixer.FixBatch
import scheduler.fixer.RequiredFixes.{PassToPhase2, ResetPhase1TestInvitedSubmitted}
import services.allocation.CandidateAllocationService
import services.events.EventsService
import services.onlinetesting.phase1.EvaluatePhase1ResultService
import services.onlinetesting.phase2.EvaluatePhase2ResultService
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import services.sift.{ApplicationSiftService, SiftAnswersService}
import services.stc.StcEventServiceFixture
import testkit.MockitoImplicits._
import testkit.{ExtendedTimeout, UnitSpec}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{OffsetDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ApplicationServiceSpec extends UnitSpec with ExtendedTimeout with Schemes {

  "fix" must {
    "process all issues we have examples of" in new TestFixture {
      when(appRepositoryMock.getApplicationsToFix(FixBatch(PassToPhase2, 1))).thenReturn(getApplicationsToFixSuccess2)
      when(appRepositoryMock.getApplicationsToFix(FixBatch(ResetPhase1TestInvitedSubmitted, 1))).thenReturn(getApplicationsToFixSuccess1)
      when(appRepositoryMock.fix(candidate1, FixBatch(PassToPhase2, 1))).thenReturn(Future.successful(Some(candidate1)))
      when(appRepositoryMock.fix(candidate2, FixBatch(PassToPhase2, 1))).thenReturn(Future.successful(Some(candidate2)))
      when(appRepositoryMock.fix(candidate3, FixBatch(ResetPhase1TestInvitedSubmitted, 1))).
        thenReturn(Future.successful(Some(candidate3)))

      underTest.fix(FixBatch(PassToPhase2, 1) :: FixBatch(ResetPhase1TestInvitedSubmitted, 1) :: Nil)(hc, rh).futureValue

      verify(appRepositoryMock, times(3)).fix(any[Candidate], any[FixBatch])
      verify(auditEventHandlerMock, times(3)).handle(any[AuditEvents.FixedProdData])(
        any[HeaderCarrier], any[RequestHeader], any[ExecutionContext])
      verifyNoInteractions(pdRepositoryMock, cdRepositoryMock, dataStoreEventHandlerMock, emailEventHandlerMock)
      verifyNoMoreInteractions(auditEventHandlerMock)
    }

    "don't fix anything if no issues are detected" in new TestFixture {
      when(appRepositoryMock.getApplicationsToFix(FixBatch(PassToPhase2, 1))).thenReturn(getApplicationsToFixEmpty)

      underTest.fix(FixBatch(PassToPhase2, 1) :: Nil)(hc, rh).futureValue

      verify(appRepositoryMock, never).fix(any[Candidate], any[FixBatch])
      verifyNoInteractions(auditEventHandlerMock)
    }

    "proceed with the other searches if one of them fails" in new TestFixture {
      when(appRepositoryMock.getApplicationsToFix(FixBatch(PassToPhase2, 1))).thenReturn(getApplicationsToFixSuccess1)
      when(appRepositoryMock.getApplicationsToFix(FixBatch(ResetPhase1TestInvitedSubmitted, 1))).thenReturn(failure)
      when(appRepositoryMock.fix(candidate3, FixBatch(PassToPhase2, 1))).thenReturn(Future.successful(Some(candidate3)))

      val result = underTest.fix(FixBatch(PassToPhase2, 1) :: FixBatch(ResetPhase1TestInvitedSubmitted, 1) :: Nil)(hc, rh)
      result.failed.futureValue mustBe generalException

      verify(appRepositoryMock, times(1)).fix(candidate3, FixBatch(PassToPhase2, 1))
      verify(auditEventHandlerMock).handle(any[AuditEvents.FixedProdData])(any[HeaderCarrier], any[RequestHeader], any[ExecutionContext])
      verifyNoMoreInteractions(auditEventHandlerMock)
    }

    "retrieve passed schemes for Faststream application" in new TestFixture {
      val faststreamApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Faststream,
        userId, testAccountId, ProgressResponse(applicationId), None, None)
      val passmarkEvaluation = PassmarkEvaluation("", None,
        List(SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(GovernmentOperationalResearchService, Red.toString)),
        "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(faststreamApplication))
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(Commercial)
    }

    "retrieve passed schemes for Faststream application with fast pass approved" in new TestFixture {
      val faststreamApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Faststream,
        userId, testAccountId, ProgressResponse(applicationId, fastPassAccepted = true), None, None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(faststreamApplication))
      when(schemePreferencesRepoMock.find(eqTo(applicationId))).thenReturn(Future.successful(SelectedSchemes(List(Commercial),
        orderAgreed = true, eligible = true)))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(Commercial)
    }

    "retrieve passed schemes for Edip application" in new TestFixture {
      val edipApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Edip, userId, testAccountId,
        ProgressResponse(applicationId), None, None)
      val passmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(Edip, Green.toString)), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(edipApplication))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(Edip)
    }

    "retrieve passed schemes for Sdip application" in new TestFixture {
      val sdipApplication = ApplicationResponse(applicationId, "", ApplicationRoute.Sdip, userId, testAccountId,
        ProgressResponse(applicationId), None, None)
      val passmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(Sdip, Green.toString)), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(sdipApplication))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(passmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(Sdip)
    }

    "retrieve passed schemes for SdipFaststream application" in new TestFixture {
      val application = ApplicationResponse(applicationId, "", ApplicationRoute.SdipFaststream, userId, testAccountId,
        ProgressResponse(applicationId), None, None
      )
      val phase1PassmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(Sdip, Green.toString),
        SchemeEvaluationResult(Finance, Green.toString)), "", None)

      val phase3PassmarkEvaluation = PassmarkEvaluation("", None,
        List(SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(GovernmentOperationalResearchService, Red.toString),
          SchemeEvaluationResult(Finance, Red.toString)
        ), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(application))
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(phase3PassmarkEvaluation))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(phase1PassmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(Sdip)
    }

    "retrieve schemes for SdipFaststream when the applicant has failed Faststream prior to Phase 3 tests" in new TestFixture {
      val application = ApplicationResponse(applicationId, "", ApplicationRoute.SdipFaststream, userId, testAccountId,
        ProgressResponse(applicationId), None, None
      )
      val phase1PassmarkEvaluation = PassmarkEvaluation("", None, List(SchemeEvaluationResult(Sdip, Green.toString)), "", None)

      when(appRepositoryMock.findByUserId(eqTo(userId), eqTo(frameworkId))).thenReturn(Future.successful(application))
      when(evalPhase3ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.failed(PassMarkEvaluationNotFound(applicationId)))
      when(evalPhase1ResultMock.getPassmarkEvaluation(eqTo(applicationId))(any[ExecutionContext]))
        .thenReturn(Future.successful(phase1PassmarkEvaluation))

      val passedSchemes = underTest.getPassedSchemes(userId, frameworkId).futureValue

      passedSchemes mustBe List(Sdip)
    }
  }

  "Override submission deadline" must {
    "update the submission deadline in the repository" in new TestFixture {
      val newDeadline = OffsetDateTime.of(2016, 5, 21, 23, 59, 59, 0, ZoneOffset.UTC)

      when(appRepositoryMock.updateSubmissionDeadline(applicationId, newDeadline)).thenReturnAsync()

      underTest.overrideSubmissionDeadline(applicationId, newDeadline)

      verify(appRepositoryMock, times(1)).updateSubmissionDeadline(eqTo(applicationId), eqTo(newDeadline))
    }
  }

  "current scheme status with failure details" must {
    "return no failure reasons when all schemes are green" in new TestFixture {
      List(phase1EvaluationRepositoryMock, phase2EvaluationRepositoryMock, phase3EvaluationRepositoryMock).foreach { repo =>
        when(repo.getPassMarkEvaluation(any[String])(any[ExecutionContext])).thenReturnAsync(
          PassmarkEvaluation(
            "version-1",
            None,
            List(
              SchemeEvaluationResult(Commercial, Green.toString),
              SchemeEvaluationResult(Finance, Green.toString)
            ),
            "resultVersion-1",
            None
          )
        )
      }

      when(siftRepoMock.getSiftEvaluations(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(Finance, Green.toString)
        )
      )

      when(fsacRepoMock.getFsacEvaluatedSchemes(any[String]())).thenReturnAsync(
        Some(Seq(
          SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(Finance, Green.toString)
        ))
      )

      when(fsbRepoMock.findByApplicationId(any[String]())).thenReturnAsync(
        Some(FsbTestGroup(
          List(
            SchemeEvaluationResult(Commercial, Green.toString),
            SchemeEvaluationResult(Finance, Green.toString)
          )
        ))
      )

      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(Finance, Green.toString)
        )
      )

      whenReady(underTest.currentSchemeStatusWithFailureDetails("application-1")) { ready =>
        ready.forall(_.failedAt.isEmpty) mustBe true
        ready.forall(_.result == Green.toString) mustBe true
      }
    }

    "return a failure reason against all red schemes when one is failed" in new TestFixture {
      List(phase1EvaluationRepositoryMock, phase2EvaluationRepositoryMock, phase3EvaluationRepositoryMock).foreach { repo =>
        when(repo.getPassMarkEvaluation(any[String])(any[ExecutionContext])).thenReturnAsync(
          PassmarkEvaluation(
            "version-1",
            None,
            List(
              SchemeEvaluationResult(Commercial, Green.toString),
              SchemeEvaluationResult(Finance, Green.toString)
            ),
            "resultVersion-1",
            None
          )
        )
      }

      when(siftRepoMock.getSiftEvaluations(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(Finance, Green.toString)
        )
      )

      when(fsacRepoMock.getFsacEvaluatedSchemes(any[String]())).thenReturnAsync(
        Some(Seq(
          SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(Finance, Red.toString)
        ))
      )

      when(fsbRepoMock.findByApplicationId(any[String]())).thenReturnAsync(
        Some(FsbTestGroup(
          List(
            SchemeEvaluationResult(Commercial, Green.toString),
            SchemeEvaluationResult(Finance, Red.toString)
          )
        ))
      )

      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(Finance, Red.toString)
        )
      )

      whenReady(underTest.currentSchemeStatusWithFailureDetails("application-1")) { ready =>
        ready.count(_.failedAt.isDefined) mustBe 1
        ready.find(_.failedAt.isDefined).get.failedAt mustBe Some("assessment centre")
      }
    }

    "return all failure reasons when all schemes are red" in new TestFixture {
      List(phase1EvaluationRepositoryMock, phase2EvaluationRepositoryMock, phase3EvaluationRepositoryMock).foreach { repo =>
        when(repo.getPassMarkEvaluation(any[String])(any[ExecutionContext])).thenReturnAsync(
          PassmarkEvaluation(
            "version-1",
            None,
            List(
              SchemeEvaluationResult(Property, Green.toString),
              SchemeEvaluationResult(GovernmentOperationalResearchService, Green.toString),
              SchemeEvaluationResult(Commercial, Green.toString),
              SchemeEvaluationResult(Finance, Red.toString)
            ),
            "resultVersion-1",
            None
          )
        )
      }

      when(siftRepoMock.getSiftEvaluations(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(Property, Green.toString),
          SchemeEvaluationResult(GovernmentOperationalResearchService, Red.toString),
          SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(Finance, Red.toString)
        )
      )

      when(fsacRepoMock.getFsacEvaluatedSchemes(any[String]())).thenReturnAsync(
        Some(Seq(
          SchemeEvaluationResult(Property, Green.toString),
          SchemeEvaluationResult(GovernmentOperationalResearchService, Red.toString),
          SchemeEvaluationResult(Commercial, Red.toString),
          SchemeEvaluationResult(Finance, Red.toString)
        ))
      )

      when(fsbRepoMock.findByApplicationId(any[String]())).thenReturnAsync(
        Some(FsbTestGroup(
          List(
            SchemeEvaluationResult(Property, Red.toString),
            SchemeEvaluationResult(GovernmentOperationalResearchService, Red.toString),
            SchemeEvaluationResult(Commercial, Red.toString),
            SchemeEvaluationResult(Finance, Red.toString)
          )
        ))
      )

      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturnAsync(
        Seq(
          SchemeEvaluationResult(Property, Red.toString),
          SchemeEvaluationResult(GovernmentOperationalResearchService, Red.toString),
          SchemeEvaluationResult(Commercial, Red.toString),
          SchemeEvaluationResult(Finance, Red.toString)
        )
      )

      whenReady(underTest.currentSchemeStatusWithFailureDetails("application-1")) { ready =>
        ready.forall(_.failedAt.isDefined) mustBe true
        ready.find(_.schemeId == Property).get.failedAt.get mustBe "final selection board"
        ready.find(_.schemeId == GovernmentOperationalResearchService).get.failedAt.get mustBe "sift stage"
        ready.find(_.schemeId == Commercial).get.failedAt.get mustBe "assessment centre"
        ready.find(_.schemeId == Finance).get.failedAt.get mustBe "online tests"
      }
    }
  }

  trait TestFixture extends StcEventServiceFixture {

    val appRepositoryMock         = mock[GeneralApplicationRepository]
    val pdRepositoryMock          = mock[PersonalDetailsRepository]
    val cdRepositoryMock          = mock[ContactDetailsRepository]
    val schemePreferencesRepoMock = mock[SchemePreferencesRepository]
    val mediaRepoMock             = mock[MediaRepository]
    val evalPhase1ResultMock      = mock[EvaluatePhase1ResultService]
    val evalPhase2ResultMock      = mock[EvaluatePhase2ResultService]
    val evalPhase3ResultMock      = mock[EvaluatePhase3ResultService]

    val phase1EvaluationRepositoryMock = mock[OnlineTestEvaluationRepository]
    val phase2EvaluationRepositoryMock = mock[OnlineTestEvaluationRepository]
    val phase3EvaluationRepositoryMock = mock[OnlineTestEvaluationRepository]

//    val phase1TestRepository2Mock = mock[Phase1TestMongoRepository2]
//    val phase2TestRepository2Mock = mock[Phase2TestMongoRepository2]

    val siftServiceMock          = mock[ApplicationSiftService]
    val siftAnswersServiceMock   = mock[SiftAnswersService]
//    val schemeRepoMock           = mock[SchemeRepository2]

    val phase1TestRepositoryMock = mock[Phase1TestRepository]
    val phase2TestRepositoryMock = mock[Phase2TestRepository]
    val phase3TestRepositoryMock = mock[Phase3TestRepository]

    val siftRepoMock             = mock[ApplicationSiftRepository]
    val fsacRepoMock             = mock[AssessmentCentreRepository]
    val eventsServiceMock        = mock[EventsService]
    val fsbRepoMock              = mock[FsbRepository]
    val civilServiceExperienceRepositoryMock   = mock[CivilServiceExperienceDetailsRepository]
    val candidateAllocationServiceMock         = mock[CandidateAllocationService]
    val assistanceDetailsRepositoryMock = mock[AssistanceDetailsRepository]
    val assessorAssessmentScoresRepositoryMock = mock[AssessorAssessmentScoresMongoRepository]
    val reviewerAssessmentScoresRepositoryMock = mock[ReviewerAssessmentScoresMongoRepository]
    val assistanceDetailsRepoMock = mock[AssistanceDetailsRepository]

    val schemeRepoMock = new TestSchemeRepository {
      override lazy val siftableSchemeIds = Seq(Commercial, DiplomaticAndDevelopment, GovernmentEconomicsService)
      override lazy val noSiftEvaluationRequiredSchemeIds = Seq(DiplomaticAndDevelopment, Edip, OperationalDelivery,
        GovernmentCommunicationService, HousesOfParliament, HumanResources, ProjectDelivery,
        ScienceAndEngineering
      )
      override lazy val nonSiftableSchemeIds = Seq(Digital, HumanResources, OperationalDelivery)
      override lazy val numericTestSiftRequirementSchemeIds = Seq(Commercial, Finance)
      override lazy val formMustBeFilledInSchemeIds = Seq(DiplomaticAndDevelopment, GovernmentEconomicsService)
      override lazy val siftableAndEvaluationRequiredSchemeIds = Seq(Commercial, GovernmentEconomicsService)

      override def schemeRequiresFsb(id: SchemeId) = id match {
        // Only needed for the fsb specific scheme withdrawal tests
        case DiplomaticAndDevelopment => true
        case OperationalDelivery => false
        case HousesOfParliament => true
        // All other tests
        case _ => false
      }
    }

    val underTest = new ApplicationService(
      appRepositoryMock,
      pdRepositoryMock,
      cdRepositoryMock,
      schemePreferencesRepoMock,
      mediaRepoMock,
      evalPhase1ResultMock,
      evalPhase2ResultMock,
      evalPhase3ResultMock,
      phase1EvaluationRepositoryMock,
      phase2EvaluationRepositoryMock,
      phase3EvaluationRepositoryMock,
//      phase1TestRepository2Mock,
//      phase2TestRepository2Mock,
      siftServiceMock,
      siftAnswersServiceMock,
      schemeRepoMock,
      phase1TestRepositoryMock,
      phase2TestRepositoryMock,
      phase3TestRepositoryMock,
      siftRepoMock,
      fsacRepoMock,
      eventsServiceMock,
      fsbRepoMock,
      civilServiceExperienceRepositoryMock,
      candidateAllocationServiceMock,
      assistanceDetailsRepositoryMock,
      assessorAssessmentScoresRepositoryMock,
      reviewerAssessmentScoresRepositoryMock,
      stcEventServiceMock
    )

    val userId = "userId"
    val applicationId = "appId"
    val testAccountId = "testAccountId"
    val frameworkId = ""

    val candidate1 = Candidate(userId = "user123", applicationId = Some("appId234"), testAccountId = None, email = Some("test1@localhost"),
      firstName = None, lastName = None, preferredName = None, dateOfBirth = None, address = None, postCode = None, country = None,
      applicationRoute = None, applicationStatus = None
    )

    val cd1 = ContactDetails(outsideUk = false, Address("line1"), postCode = None, country = None, "email@email.com", "123":PhoneNumber)

    val candidate2 = Candidate(userId = "user456", applicationId = Some("appId4567"), testAccountId = None, email = Some("test2@localhost"),
      None, None, None, None, None, None, None, None, None)

    val candidate3 = Candidate(userId = "user569", applicationId = Some("appId84512"), testAccountId = None, email = Some("test3@localhost"),
      firstName = None, lastName = None, preferredName = None, dateOfBirth = None, address = None, postCode = None, country = None,
      applicationRoute = None, applicationStatus = None
    )

    val generalException = new RuntimeException("something went wrong")
    val failure = Future.failed(generalException)

    val getApplicationsToFixSuccess2: Future[List[Candidate]] = Future.successful(candidate1 :: candidate2 :: Nil)
    val getApplicationsToFixSuccess1: Future[List[Candidate]] = Future.successful(candidate3 :: Nil)
    val getApplicationsToFixFailure: Future[List[Candidate]] = Future.failed(generalException)
    val getApplicationsToFixEmpty: Future[List[Candidate]] = Future.successful(Nil)
    val success = Future.successful(())
  }

  // Moved out of the trait to here as not getting picked up by the withdraw tests if they are run in isolation
  // with all other tests commented out
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val rh: RequestHeader = mock[RequestHeader]
}
