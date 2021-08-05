/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers

import config.TestFixtureBase
import factories.DateTimeFactoryMock
import model.Exceptions.EventNotFoundException
import model.UniqueIdentifier
import model.assessmentscores._
import model.command.AssessmentScoresCommands._
import model.fsacscores.AssessmentScoresFinalFeedbackExamples
import org.joda.time.DateTimeZone
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import repositories.AssessmentScoresRepository
import services.assessmentscores.AssessmentScoresService
import testkit.MockitoImplicits._
import testkit.UnitWithAppSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps

class AssessorAssessmentScoresControllerSpec extends AssessmentScoresControllerSpec {
  override val userIdForAudit = "assessorId"
  override val assessmentScoresOneExerciseSaved = "AssessorAssessmentScoresOneExerciseSaved"
  override val assessmentScoresAllExercisesSubmitted = "ReviewerAssessmentScoresAllExercisesSubmitted"
  override val assessmentScoresOneExerciseSubmitted = "ReviewerAssessmentScoresOneExerciseSubmitted"
}

class ReviewerAssessmentScoresControllerSpec extends AssessmentScoresControllerSpec {
  override val userIdForAudit = "reviewerId"
  override val assessmentScoresOneExerciseSaved = "ReviewerAssessmentScoresOneExerciseSaved"
  override val assessmentScoresAllExercisesSubmitted = "ReviewerAssessmentScoresAllExercisesSubmitted"
  override val assessmentScoresOneExerciseSubmitted = "ReviewerAssessmentScoresOneExerciseSubmitted"
}

trait AssessmentScoresControllerSpec extends UnitWithAppSpec {

  val userIdForAudit: String
  val assessmentScoresOneExerciseSaved: String
  val assessmentScoresAllExercisesSubmitted: String
  val assessmentScoresOneExerciseSubmitted: String
  val writtenExercise = "writtenExercise"

  "submit exercise" should {
    "save exercise, send AssessmentScoresOneExerciseSubmitted audit event and return OK" in new TestFixture {
      val exerciseScores = AssessmentScoresExerciseExamples.Example1.copy(
        submittedDate = AssessmentScoresExerciseExamples.Example1.submittedDate.map(_.withZone(DateTimeZone.forOffsetHours(1))))
      val request = fakeRequest(AssessmentScoresSubmitExerciseRequest(appId, writtenExercise, exerciseScores))

      when(mockService.submitExercise(eqTo(appId), eqTo(AssessmentScoresSectionType.writtenExercise),
        any())).thenReturn(Future.successful(()))
      val auditDetails = Map(
        "applicationId" -> appId.toString(),
        "exercise" -> AssessmentScoresSectionType.writtenExercise.toString,
        userIdForAudit -> exerciseScores.updatedBy.toString())

      val response = controller.submitExercise()(request)

      status(response) mustBe OK
      verify(mockService).submitExercise(eqTo(appId), eqTo(AssessmentScoresSectionType.writtenExercise), any())
      verify(mockAuditService).logEvent(eqTo(assessmentScoresOneExerciseSubmitted), eqTo(auditDetails))(any[HeaderCarrier], any[RequestHeader])
    }
  }

  "save exercise" should {
    "save exercise, send AssessmentScoresOneExerciseSaved audit event and return OK" in new TestFixture {
      val exerciseScores = AssessmentScoresExerciseExamples.Example1.copy(
        submittedDate = AssessmentScoresExerciseExamples.Example1.submittedDate.map(_.withZone(DateTimeZone.forOffsetHours(1))))
      val request = fakeRequest(AssessmentScoresSubmitExerciseRequest(appId, writtenExercise, exerciseScores))

      when(mockService.saveExercise(eqTo(appId), eqTo(AssessmentScoresSectionType.writtenExercise),
        any())).thenReturn(Future.successful(()))
      val auditDetails = Map(
        "applicationId" -> appId.toString(),
        "exercise" -> AssessmentScoresSectionType.writtenExercise.toString,
        userIdForAudit -> exerciseScores.updatedBy.toString())

      val response = controller.saveExercise()(request)

      status(response) mustBe OK
      verify(mockService).saveExercise(eqTo(appId), eqTo(AssessmentScoresSectionType.writtenExercise), any())
      verify(mockAuditService).logEvent(eqTo(assessmentScoresOneExerciseSaved), eqTo(auditDetails))(any[HeaderCarrier], any[RequestHeader])
    }
  }

  "submit final feedback" should {
    "save final feedback, send AssessmentScoresOneExerciseSubmitted and AssessmentScoresAllExercisesSubmitted" +
      " audit events and return OK" in new TestFixture {
      val finalFeedback = AssessmentScoresFinalFeedbackExamples.Example1.copy(
             acceptedDate = AssessmentScoresFinalFeedbackExamples.Example1.acceptedDate.withZone(DateTimeZone.forOffsetHours(1)))
      val request = fakeRequest(AssessmentScoresFinalFeedbackSubmitRequest(appId, finalFeedback))

      when(mockService.submitFinalFeedback(eqTo(appId),
        any())).thenReturn(Future.successful(()))
      val oneExerciseAuditDetails = Map(
        "applicationId" -> appId.toString(),
        "exercise" -> "finalFeedback",
        userIdForAudit -> finalFeedback.updatedBy.toString())
      val allExercisesAuditDetails = Map(
        "applicationId" -> appId.toString(),
        userIdForAudit -> finalFeedback.updatedBy.toString())

      val response = controller.submitFinalFeedback()(request)

      status(response) mustBe OK
      verify(mockService).submitFinalFeedback(eqTo(appId), any())
      verify(mockAuditService).logEvent(eqTo(assessmentScoresOneExerciseSubmitted),
        eqTo(oneExerciseAuditDetails))(any[HeaderCarrier], any[RequestHeader])
      verify(mockAuditService).logEvent(eqTo(assessmentScoresAllExercisesSubmitted),
        eqTo(allExercisesAuditDetails))(any[HeaderCarrier], any[RequestHeader])
    }
  }

  "findAssessmentScoresWithCandidateSummaryByApplicationId" should {
    "return OK with corresponding assessment scores" in new TestFixture {
      val expectedResponse = AssessmentScoresFindResponse(
        AssessmentScoresCandidateSummary(appId, "firstName", "lastName", "venue",
          DateTimeFactoryMock.nowLocalDate, UniqueIdentifier.randomUniqueIdentifier),
        Some(AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise))
      when(mockService.findAssessmentScoresWithCandidateSummaryByApplicationId(appId)).thenReturn(
        Future.successful(expectedResponse))

      val response = controller.findAssessmentScoresWithCandidateSummaryByApplicationId(appId)(fakeRequest)

      status(response) mustBe OK
      contentAsJson(response) mustBe Json.toJson(expectedResponse)
    }

    "return NOT_FOUND if there is any error" in new TestFixture {
      when(mockService.findAssessmentScoresWithCandidateSummaryByApplicationId(appId)).thenReturn(
        Future.failed(EventNotFoundException(eventId.toString())))
      val response = controller.findAssessmentScoresWithCandidateSummaryByApplicationId(appId)(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
  }

  "findAssessmentScoresWithCandidateSummaryByEventId" should {
    "return OK with corresponding assessment scores" in new TestFixture {
      val expectedResponse = List(AssessmentScoresFindResponse(
        AssessmentScoresCandidateSummary(appId, "firstName", "lastName", "venue",
          DateTimeFactoryMock.nowLocalDate, sessionId),
        Some(AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise)))
      when(mockService.findAssessmentScoresWithCandidateSummaryByEventId(eventId)).thenReturn(
        Future.successful(expectedResponse))

      val response = controller.findAssessmentScoresWithCandidateSummaryByEventId(eventId)(fakeRequest)

      status(response) mustBe OK
      contentAsJson(response) mustBe Json.toJson(expectedResponse)
    }

    "return NOT_FOUND if there is any error" in new TestFixture {
      when(mockService.findAssessmentScoresWithCandidateSummaryByEventId(eventId)).thenReturn(
        Future.failed(EventNotFoundException(eventId.toString())))
      val response = controller.findAssessmentScoresWithCandidateSummaryByEventId(eventId)(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
  }

  "findAcceptedAssessmentScoresByApplicationId" should {
    "return OK with reviewer accepted assessment scores" in new TestFixture {
      val expectedResponse = Some(AssessmentScoresAllExercises(appId))
      when(mockService.findAcceptedAssessmentScoresAndFeedbackByApplicationId(appId)).thenReturnAsync(expectedResponse)

      val response = controller.findAcceptedAssessmentScoresByApplicationId(appId)(fakeRequest)

      status(response) mustBe OK
      contentAsJson(response) mustBe Json.toJson(expectedResponse)
    }

    "return NOT_FOUND if the data cannot be located" in new TestFixture {
      when(mockService.findAcceptedAssessmentScoresAndFeedbackByApplicationId(appId)).thenReturnAsync(None)
      val response = controller.findAcceptedAssessmentScoresByApplicationId(appId)(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
  }

  "resetExercises" should {
    def toResetExercisesAuditDetails(resetExercisesRequest: ResetExercisesRequest) =
      Map(
        "applicationId" -> resetExercisesRequest.applicationId.toString,
        "resetWrittenExercise" -> resetExercisesRequest.written.toString,
        "resetTeamExercise" -> resetExercisesRequest.team.toString,
        "resetLeadershipExercise" -> resetExercisesRequest.leadership.toString
      )

    "reset all exercises and return OK when all are specified" in new TestFixture {
      when(mockAssessmentScoresRepository.resetExercise(appId, List(writtenExercise, teamExercise, leadershipExercise)))
        .thenReturnAsync()
      val resetExercisesRequest = ResetExercisesRequest(appId, written = true, team = true, leadership = true)
      val request = fakeRequest(resetExercisesRequest)
      val response = controller.resetExercises(appId)(request)
      status(response) mustBe OK

      val auditDetails = toResetExercisesAuditDetails(resetExercisesRequest)
      verify(mockAuditService).logEvent(eqTo(assessmentScoresResetLogEvent),
        eqTo(auditDetails))(any[HeaderCarrier], any[RequestHeader])
    }

    "reset analysis exercise only and return OK when analysis is specified" in new TestFixture {
      when(mockAssessmentScoresRepository.resetExercise(appId, List(writtenExercise)))
        .thenReturnAsync()
      val resetExercisesRequest = ResetExercisesRequest(appId, written = true, team = false, leadership = false)
      val request = fakeRequest(resetExercisesRequest)
      val response = controller.resetExercises(appId)(request)
      status(response) mustBe OK

      val auditDetails = toResetExercisesAuditDetails(resetExercisesRequest)
      verify(mockAuditService).logEvent(eqTo(assessmentScoresResetLogEvent),
        eqTo(auditDetails))(any[HeaderCarrier], any[RequestHeader])
    }

    "reset group exercise only and return OK when group is specified" in new TestFixture {
      when(mockAssessmentScoresRepository.resetExercise(appId, List(teamExercise)))
        .thenReturnAsync()
      val resetExercisesRequest = ResetExercisesRequest(appId, written = false, team = true, leadership = false)
      val request = fakeRequest(resetExercisesRequest)
      val response = controller.resetExercises(appId)(request)
      status(response) mustBe OK

      val auditDetails = toResetExercisesAuditDetails(resetExercisesRequest)
      verify(mockAuditService).logEvent(eqTo(assessmentScoresResetLogEvent),
        eqTo(auditDetails))(any[HeaderCarrier], any[RequestHeader])
    }

    "reset leadership exercise only and return OK when leadership is specified" in new TestFixture {
      when(mockAssessmentScoresRepository.resetExercise(appId, List(leadershipExercise)))
        .thenReturnAsync()
      val resetExercisesRequest = ResetExercisesRequest(appId, written = false, team = false, leadership = true)
      val request = fakeRequest(resetExercisesRequest)
      val response = controller.resetExercises(appId)(request)
      status(response) mustBe OK

      val auditDetails = toResetExercisesAuditDetails(resetExercisesRequest)
      verify(mockAuditService).logEvent(eqTo(assessmentScoresResetLogEvent),
        eqTo(auditDetails))(any[HeaderCarrier], any[RequestHeader])
    }

    "return INTERNAL_SERVER_ERROR if an exception is thrown" in new TestFixture {
      when(mockAssessmentScoresRepository.resetExercise(appId, List(writtenExercise)))
        .thenReturn(Future.failed(new Exception("BOOM")))
      val resetExercisesRequest = ResetExercisesRequest(appId, written = true, team = false, leadership = false)
      val request = fakeRequest(resetExercisesRequest)
      val response = controller.resetExercises(appId)(request)
      status(response) mustBe INTERNAL_SERVER_ERROR
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockService = mock[AssessmentScoresService]
    val mockAssessmentScoresRepository = mock[AssessmentScoresRepository]

    val appId = AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise.applicationId
    val sessionId = UniqueIdentifier.randomUniqueIdentifier
    val eventId = UniqueIdentifier.randomUniqueIdentifier
    val writtenExercise = "writtenExercise"
    val teamExercise = "teamExercise"
    val leadershipExercise = "leadershipExercise"
    val assessmentScoresResetLogEvent = "AssessmentScoresReset"
    val stubCC = stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer))

    class TestController extends AssessmentScoresController(stubCC) {
      val service = mockService
      val repository = mockAssessmentScoresRepository
      val auditService = mockAuditService

      val AssessmentScoresOneExerciseSaved = assessmentScoresOneExerciseSaved
      val AssessmentScoresOneExerciseSubmitted = assessmentScoresOneExerciseSubmitted
      val AssessmentScoresAllExercisesSubmitted = assessmentScoresAllExercisesSubmitted
      val UserIdForAudit = userIdForAudit
    }

    def controller = new TestController
  }
}
