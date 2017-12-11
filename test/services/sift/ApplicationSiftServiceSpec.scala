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

package services.sift

import connectors.EmailClient
import factories.DateTimeFactoryMock
import model.ProgressStatuses.ProgressStatus
import model._
import model.command.ApplicationForSift
import model.persisted.{ ContactDetailsExamples, SchemeEvaluationResult }
import model.sift.FixUserStuckInSiftEntered
import org.joda.time.LocalDate
import reactivemongo.bson.BSONDocument
import repositories.{ BSONDateTimeHandler, SchemeRepository }
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.sift.ApplicationSiftRepository
import testkit.ScalaMockImplicits._
import testkit.ScalaMockUnitWithAppSpec

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class ApplicationSiftServiceSpec extends ScalaMockUnitWithAppSpec {

  trait TestFixture  {
    val appId = "applicationId"
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockSiftRepo = mock[ApplicationSiftRepository]
    val mockContactDetailsRepo = mock[ContactDetailsRepository]
    val mockEmailClient = mock[EmailClient]
    val mockSchemeRepo = new SchemeRepository {
      override lazy val schemes: Seq[Scheme] = Seq(
        Scheme("DigitalAndTechnology", "DaT", "Digital and Technology", civilServantEligible = false, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = false, fsbType = None, schemeGuide = None, schemeQuestion = None
        ),
        Scheme("GovernmentSocialResearchService", "GSR", "GovernmentSocialResearchService", civilServantEligible = false, None,
          Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None,  schemeGuide = None, schemeQuestion = None
        ),
        Scheme("Commercial", "GCS", "Commercial", civilServantEligible = false, None, Some(SiftRequirement.NUMERIC_TEST),
          siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None
        ),
        Scheme("HousesOfParliament", "HOP", "Houses of Parliament", civilServantEligible = true, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = false, fsbType = None, schemeGuide = None, schemeQuestion = None
        ),
        Scheme("ProjectDelivery", "PDFS", "Project Delivery", civilServantEligible = true, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = false, fsbType = None, schemeGuide = None, schemeQuestion = None
        ),
        Scheme("ScienceAndEngineering", "SEFS", "Science And Engineering", civilServantEligible = true, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = false, fsbType = None, schemeGuide = None, schemeQuestion = None
        ),
        Scheme("Edip", "EDIP", "Early Diversity Internship Programme", civilServantEligible = true, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = false, fsbType = None, schemeGuide = None, schemeQuestion = None
        ),
        Scheme("Sdip", "SDIP", "Summer Diversity Internship Programme", civilServantEligible = true, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None
        ),
        Scheme("Generalist", "GFS", "Generalist", civilServantEligible = true, None, None, siftEvaluationRequired = false,
          fsbType = None, schemeGuide = None, schemeQuestion = None
        )
      )

      override def siftableSchemeIds: Seq[SchemeId] = Seq(SchemeId("GovernmentSocialResearchService"), SchemeId("Commercial"))
    }

    val service = new ApplicationSiftService {
      def applicationSiftRepo: ApplicationSiftRepository = mockSiftRepo
      def applicationRepo: GeneralApplicationRepository = mockAppRepo
      def schemeRepo: SchemeRepository = mockSchemeRepo
      def contactDetailsRepo: ContactDetailsRepository = mockContactDetailsRepo
      def emailClient: EmailClient = mockEmailClient
      def dateTimeFactory = DateTimeFactoryMock
    }
  }

  trait SiftUpdateTest extends TestFixture {
    val progressStatusUpdateBson = (status: ProgressStatus) => BSONDocument(
      s"progress-status.$status" -> true,
      s"progress-status-timestamp.$status" -> BSONDateTimeHandler.write(DateTimeFactoryMock.nowLocalTimeZone)
    )

    def currentSchemeUpdateBson(schemeResult: SchemeEvaluationResult*) = BSONDocument(
        "currentSchemeStatus" -> schemeResult.map { s =>
          BSONDocument("schemeId" -> s.schemeId.value, "result" -> s.result)
        }
      )

    val schemeSiftResult = SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Green.toString)
    val queryBson = BSONDocument("applicationId" -> appId)
    val updateBson = BSONDocument("test" -> "test")
  }

  "An ApplicationSiftService.progressApplicationToSift" must {

    "progress all applications regardless of failures" in new TestFixture {
      val applicationsToProgressToSift = List(
        ApplicationForSift("appId1", "userId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))),
        ApplicationForSift("appId2", "userId2", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString))),
        ApplicationForSift("appId3", "userId3", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
            List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)))
      )

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId1", ProgressStatuses.SIFT_READY).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId2", ProgressStatuses.SIFT_ENTERED)
        .returning(Future.failed(new Exception))
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId3", ProgressStatuses.SIFT_READY).returningAsync

      whenReady(service.progressApplicationToSiftStage(applicationsToProgressToSift)) { results =>

        val failedApplications = Seq(applicationsToProgressToSift(1))
        val passedApplications = Seq(applicationsToProgressToSift.head, applicationsToProgressToSift(2))
        results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "progress candidate to SIFT_READY (eligible to be sifted) when the candidate is only in the running for schemes " +
      "requiring a numeric test and form based schemes are failed" in new TestFixture {
      val applicationToProgressToSift = List(
        ApplicationForSift("appId1", "userId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString),
            SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString)
          )
        )
      )

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId1", ProgressStatuses.SIFT_READY).returningAsync

      whenReady(service.progressApplicationToSiftStage(applicationToProgressToSift)) { results =>

        val failedApplications = Nil
        val passedApplications = Seq(applicationToProgressToSift.head)
        results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "progress candidate to SIFT_READY (eligible to be sifted) when the candidate is still in the running for schemes " +
      "requiring a numeric test and Generalist and form based schemes are failed" in new TestFixture {
      val applicationToProgressToSift = List(
        ApplicationForSift("appId1", "userId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString),
            SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString),
            SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString)
          )
        )
      )

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId1", ProgressStatuses.SIFT_READY).returningAsync

      whenReady(service.progressApplicationToSiftStage(applicationToProgressToSift)) { results =>

        val failedApplications = Nil
        val passedApplications = Seq(applicationToProgressToSift.head)
        results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "find relevant applications for scheme sifting" in new TestFixture {
      val candidates = Seq(Candidate("userId1", Some("appId1"), Some(""), Some(""), Some(""), Some(""), Some(LocalDate.now), Some(Address("")),
        Some("E1 7UA"), Some("UK"), Some(ApplicationRoute.Faststream), Some("")))

      (mockSiftRepo.findApplicationsReadyForSchemeSift _).expects(*).returningAsync(candidates)

      whenReady(service.findApplicationsReadyForSchemeSift(SchemeId("scheme1"))) { result =>
        result mustBe candidates
      }
    }

    "sift and update progress status for a candidate" in new SiftUpdateTest {
      (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)

      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(schemeSiftResult),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED)
      )

      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Green.toString)
      ))
      (mockSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync
      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.Faststream)

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift and update progress status for an SdipFaststream candidate who fails SDIP" in new SiftUpdateTest {
      (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)

      override val schemeSiftResult = SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Red.toString)
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString) :: schemeSiftResult :: Nil: _*),
        progressStatusUpdateBson(ProgressStatuses.SDIP_FAILED_AT_SIFT)
      )
      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      ))
      (mockSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync
      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.SdipFaststream)

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift and update progress status for an SdipFaststream candidate who passes SDIP" in new SiftUpdateTest {
      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.SdipFaststream)

      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      ))

      override val schemeSiftResult = SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString) :: schemeSiftResult :: Nil: _*),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED)
      )
      (mockSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync

      (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))
      )

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift but do not update progress status for an SdipFaststream candidate whose SDIP has not been sifted" in new SiftUpdateTest {
      (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)

      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.SdipFaststream)

      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      ))

      override val schemeSiftResult = SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Red.toString)
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(schemeSiftResult :: SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString) :: Nil: _*)
        // No progressStatusUpdateBson document expected because we have not yet sifted Sdip
      )
      (mockSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift and update progress status for an SdipFaststream candidate whose SDIP has been sifted and whose fast stream " +
      "schemes are Red or Withdrawn, all require a sift and have been sifted" in new SiftUpdateTest {
      (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString)
      ))

      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.SdipFaststream)

      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      ))

      override val schemeSiftResult = SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString) ::
          SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString) ::
          schemeSiftResult :: Nil: _*),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED),
        progressStatusUpdateBson(ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN)
      )
      (mockSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift and update progress status for an SdipFaststream candidate whose SDIP has been sifted and whose fast stream " +
      "schemes are Withdrawn and have not been sifted" in new SiftUpdateTest {
      (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)

      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.SdipFaststream)

      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      ))

      override val schemeSiftResult = SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString) ::
          SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Withdrawn.toString) ::
          schemeSiftResult :: Nil: _*),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED),
        progressStatusUpdateBson(ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN)
      )
      (mockSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift and update progress status for an SdipFaststream candidate whose SDIP has been sifted and whose fast stream " +
      "schemes which require a sift are Red or Withdrawn and have been sifted, but also has a fast stream scheme that " +
      "does not require a sift" in new SiftUpdateTest {
      (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString)
      ))

      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.SdipFaststream)

      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString)
      ))

      override val schemeSiftResult = SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString) ::
          SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString) ::
          schemeSiftResult ::
          SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString) :: Nil: _*),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED)
      )
      (mockSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift a candidate with remaining schemes to sift" in new SiftUpdateTest {
      (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)

       val currentStatus = Seq(
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      )
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(currentStatus:_*)
      )

      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.Faststream)
      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(currentStatus)
      (mockSiftRepo.siftApplicationForScheme _).expects("applicationId", schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift candidate and update progress status if remaining schemes don't require sift" in new SiftUpdateTest {
      (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)

      val currentStatus = Seq(
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("HousesOfParliament"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      )
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(currentStatus:_*),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED)
      )

      override val schemeSiftResult = SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)

      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.Faststream)
      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(currentStatus)
      (mockSiftRepo.siftApplicationForScheme _).expects("applicationId", schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift candidate and update progress status if remaining schemes are generalists and/or dont require sift" in new SiftUpdateTest {
      (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)

      val currentStatus = Seq(
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("HousesOfParliament"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      )
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(currentStatus: _*),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED)
      )

      override val schemeSiftResult = SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)

      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.Faststream)
      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(currentStatus)
      (mockSiftRepo.siftApplicationForScheme _).expects("applicationId", schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }
  }

  "sendSiftEnteredNotification" must {
    "send email to the right candidate" in new TestFixture {
      val candidate = CandidateExamples.minCandidate("userId")
      val contactDetails = ContactDetailsExamples.ContactDetailsUK

      (mockAppRepo.find(_ : String)).expects(appId).returningAsync(Some(candidate))
      (mockContactDetailsRepo.find _ ).expects("userId").returningAsync(contactDetails)
      (mockEmailClient.notifyCandidateSiftEnteredAdditionalQuestions(_: String, _: String)(_: HeaderCarrier))
        .expects(contactDetails.email, candidate.name, *).returningAsync

      whenReady(service.sendSiftEnteredNotification(appId)(new HeaderCarrier())) { result => result mustBe unit }
    }
  }

  "findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase" must {
    "return no candidates if the candidates have no numeric test schemes" in new TestFixture {
      (mockSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(
        FixUserStuckInSiftEntered("app1", Seq(SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString)))
      ))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase) { result => result mustBe Nil }
    }

    "return no candidates if the candidates have no green numeric test schemes" in new TestFixture {
      (mockSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(
        FixUserStuckInSiftEntered("app1", Seq(
          SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString),
          SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Red.toString)
        ))
      ))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase) { result => result mustBe Nil }
    }

    "return candidates if the candidates have at least one green numeric test scheme" in new TestFixture {
      val oneCandidate = FixUserStuckInSiftEntered("app1", Seq(
        SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      ))

      (mockSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(oneCandidate))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase) { result =>
        result mustBe Seq(oneCandidate) }
    }

    "return candidates if the candidates only have one green numeric test scheme" in new TestFixture {
      val oneCandidate = FixUserStuckInSiftEntered("app1", Seq(
        SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Red.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      ))

      (mockSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(oneCandidate))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase) { result =>
        result mustBe Seq(oneCandidate) }
    }
  }

  "findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes" must {
    "return candidates if the candidates still have numeric test schemes and have withdrawn from all form schemes" in new TestFixture {
      val oneCandidate = FixUserStuckInSiftEntered("app1", Seq(
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      ))

      (mockSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(oneCandidate))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes) { result =>
        result mustBe Seq(oneCandidate) }
    }

    "return no candidates if the candidates have no green numeric test schemes" in new TestFixture {
      (mockSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(
        FixUserStuckInSiftEntered("app1", Seq(
          SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Withdrawn.toString),
          SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString),
          SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Red.toString)
        ))
      ))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes) { result =>
        result mustBe Nil }
    }

    "return no candidates if the candidates have no withdrawn schemes" in new TestFixture {
      (mockSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(
        FixUserStuckInSiftEntered("app1", Seq(
          SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Red.toString),
          SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString),
          SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
        ))
      ))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes) { result => result mustBe Nil }
    }
  }
}
