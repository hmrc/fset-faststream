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

package mocks.application

import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.Commands._
import model.EvaluationResults.AssessmentEvaluationResult
import model.Exceptions.ApplicationNotFound
import model.OnlineTestCommands.OnlineTestApplication
import model.exchange.CandidatesEligibleForEventResponse
import model.{ NotificationTestTypeSdipFs, _ }
import model.command._
import model.persisted._
import model.persisted.eventschedules.EventType.EventType
import model.report._
import org.joda.time.{ DateTime, LocalDate }
import repositories.application.GeneralApplicationRepository
import scheduler.fixer.FixBatch

import scala.collection.mutable
import scala.concurrent.Future

object DocumentRootInMemoryRepository extends DocumentRootInMemoryRepository

/**
 * @deprecated Please use Mockito
 */
// scalastyle:off number.of.methods
class DocumentRootInMemoryRepository extends GeneralApplicationRepository {

  override def findSdipFaststreamInvitedToVideoInterview: Future[Seq[Candidate]] = ???

  override def find(applicationIds: Seq[String]): Future[List[Candidate]] = ???
  override def updateSubmissionDeadline(applicationId: String, newDeadline: DateTime) = ???

  def archive(appId: String, originalUserId: String, userIdToArchiveWith: String,
              frameworkId: String, appRoute: ApplicationRoute): Future[Unit] = ???

  override def findAllFileInfo: Future[List[CandidateFileInfo]] = ???
  
  override def find(applicationId: String): Future[Option[Candidate]] = ???

  override def create(userId: String, frameworkId: String, applicationRoute:ApplicationRoute): Future[ApplicationResponse] = {

    val applicationId = java.util.UUID.randomUUID().toString
    val applicationCreated = ApplicationResponse(applicationId, "CREATED", ApplicationRoute.Faststream, userId,
      ProgressResponse(applicationId), None, None)

    inMemoryRepo += applicationId -> applicationCreated
    Future.successful(applicationCreated)
  }

  override def findProgress(applicationId: String): Future[ProgressResponse] = applicationId match {
    case "1111-1234" => Future.failed(ApplicationNotFound(applicationId))
    case _ => Future.successful(ProgressResponse(applicationId, personalDetails = true))
  }

  val inMemoryRepo = new mutable.HashMap[String, ApplicationResponse]

  override def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse] = userId match {
    case "invalidUser" => Future.failed(ApplicationNotFound("invalidUser"))
    case _ =>
      val applicationId = "1111-1111"
      val applicationCreated = ApplicationResponse(applicationId, "CREATED", ApplicationRoute.Faststream, userId,
        ProgressResponse(applicationId), None, None)
      Future.successful(applicationCreated)
  }

  override def findStatus(applicationId: String): Future[ApplicationStatusDetails] = Future.successful(
    ApplicationStatusDetails("", ApplicationRoute.Faststream, None, None, None))

  override def submit(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def withdraw(applicationId: String, reason: WithdrawApplication): Future[Unit] = Future.successful(Unit)

  override def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit] = Future.successful(Unit)

  override def preview(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def findByCriteria(firstOrPreferredName: Option[String],
    lastName: Option[String], dateOfBirth: Option[LocalDate],
    userIds: List[String] = List.empty): Future[List[Candidate]] = Future.successful(List.empty[Candidate])

  override def findCandidateByUserId(userId: String): Future[Option[Candidate]] = Future.successful(None)

  override def findApplicationIdsByLocation(location: String): Future[List[String]] = Future.successful(List())

  override def confirmAdjustments(applicationId: String, data: Adjustments): Future[Unit] = Future.successful(Unit)

  override def findAdjustments(applicationId: String): Future[Option[Adjustments]] =
    Future.successful(
      Some(Adjustments(Some(List("etrayTimeExtension")), Some(true), Some(AdjustmentDetail(Some(5))), None))
    )

  override def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit] = Future.successful(Unit)

  override def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment] =
    Future.successful(AdjustmentsComment("comment"))

  override def removeAdjustmentsComment(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def rejectAdjustment(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def findTestForNotification(notificationType: NotificationTestType): Future[Option[TestResultNotification]] = {
    Future.successful(Some(TestResultNotification("31009ccc-1ac3-4d55-9c53-1908a13dc5e1", "fbb466a3-13a3-4dd0-93d6-9dfa764a5555", "George")))
  }

  override def findTestForSdipFsNotification(notificationType: NotificationTestTypeSdipFs): Future[Option[TestResultSdipFsNotification]] = {
    Future.successful(Some(TestResultSdipFsNotification(
      "31009ccc-1ac3-4d55-9c53-1908a13dc5e1", "fbb466a3-13a3-4dd0-93d6-9dfa764a5555", ApplicationStatus.PHASE2_TESTS_FAILED, "George")
    ))
  }

  override def gisByApplication(applicationId: String): Future[Boolean] = ???

  override def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]] = ???

  override def updateStatus(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit] = ???

  override def fix(application: Candidate, issue: FixBatch): Future[Option[Candidate]] = ???

  override def getApplicationsToFix(issue: FixBatch): Future[List[Candidate]] = ???

  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]] = ???

  def saveAssessmentScoreEvaluation(applicationId: String, passmarkVersion: String, evaluationResult: AssessmentEvaluationResult,
    newApplicationStatus: ApplicationStatus): Future[Unit] = ???

  def addProgressStatusAndUpdateAppStatus(appId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = ???

  def removeProgressStatuses(appId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit] = ???

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = ???

  def fixDataByRemovingETray(appId: String): Future[Unit] = ???

  def fixDataByRemovingProgressStatus(appId: String, progressStatus: String): Future[Unit] = ???

  def removeWithdrawReason(applicationId: String): Future[Unit] = ???

  def updateApplicationRoute(appId: String, appRoute: ApplicationRoute, newAppRoute: ApplicationRoute): Future[Unit] = ???

  def fixDataByRemovingVideoInterviewFailed(appId: String): Future[Unit] = ???

  override def findAllocatedApplications(applicationIds: List[String]): Future[CandidatesEligibleForEventResponse] = ???

  override def getCurrentSchemeStatus(applicationId: String): Future[Seq[SchemeEvaluationResult]] = ???

  override def findCandidatesEligibleForEventAllocation(
    locations: List[String], eventType: EventType, schemeId: Option[SchemeId]): Future[CandidatesEligibleForEventResponse] = ???

  override def resetApplicationAllocationStatus(applicationId: String, eventType: EventType): Future[Unit] = ???

  override def setFailedToAttendAssessmentStatus(applicationId: String, eventType: EventType): Future[Unit] = ???

  override def withdrawScheme(applicationId: String, schemeWithdraw: WithdrawScheme, schemeStatus: Seq[SchemeEvaluationResult]) = ???

  override def getApplicationRoute(applicationId: String) = ???

  override def getLatestProgressStatuses = ???

  override def getProgressStatusTimestamps(applicationId: String) = ???

  override def countByStatus(applicationStatus: ApplicationStatus) = ???

  override def count(implicit ec : scala.concurrent.ExecutionContext): Future[Int] = ???

  override def updateCurrentSchemeStatus(applicationId: String, results: Seq[SchemeEvaluationResult]) = ???
}
