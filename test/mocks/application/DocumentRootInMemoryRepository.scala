/*
 * Copyright 2016 HM Revenue & Customs
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
import model.AssessmentScheduleCommands.{ ApplicationForAssessmentAllocation, ApplicationForAssessmentAllocationResult }
import model.Commands._
import model.EvaluationResults.AssessmentRuleCategoryResult
import model.Exceptions.ApplicationNotFound
import model.OnlineTestCommands.OnlineTestApplication
import model._
import model.command._
import model.persisted._
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

  override def find(applicationIds: List[String]): Future[List[Candidate]] = ???

  override def find(applicationId: String): Future[Option[Candidate]] = ???

  override def create(userId: String, frameworkId: String, applicationRoute:ApplicationRoute): Future[ApplicationResponse] = {

    val applicationId = java.util.UUID.randomUUID().toString
    val applicationCreated = ApplicationResponse(applicationId, "CREATED", ApplicationRoute.Faststream, userId,
      ProgressResponse(applicationId), None)

    inMemoryRepo += applicationId -> applicationCreated
    Future.successful(applicationCreated)
  }

  override def findProgress(applicationId: String): Future[ProgressResponse] = applicationId match {
    case "1111-1234" => Future.failed(new ApplicationNotFound(applicationId))
    case _ => Future.successful(ProgressResponse(applicationId, true))
  }

  val inMemoryRepo = new mutable.HashMap[String, ApplicationResponse]

  override def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse] = userId match {
    case "invalidUser" => Future.failed(new ApplicationNotFound("invalidUser"))
    case _ =>
      val applicationId = "1111-1111"
      val applicationCreated = ApplicationResponse(applicationId, "CREATED", ApplicationRoute.Faststream, userId,
        ProgressResponse(applicationId), None)
      Future.successful(applicationCreated)
  }

  override def findApplicationsForAssessmentAllocation(locations: List[String], start: Int,
    end: Int): Future[ApplicationForAssessmentAllocationResult] = {
    Future.successful(ApplicationForAssessmentAllocationResult(List(ApplicationForAssessmentAllocation("firstName", "lastName", "userId1",
      "applicationId1", "No", DateTime.now)), 1))
  }

  override def findStatus(applicationId: String): Future[ApplicationStatusDetails] = Future.successful(
    ApplicationStatusDetails("", ApplicationRoute.Faststream))

  override def submit(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def withdraw(applicationId: String, reason: WithdrawApplication): Future[Unit] = Future.successful(Unit)

  override def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit] = Future.successful(Unit)

  override def preview(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def findByCriteria(firstOrPreferredName: Option[String],
    lastName: Option[String], dateOfBirth: Option[LocalDate],
    userIds: List[String] = List.empty): Future[List[Candidate]] = Future.successful(List.empty[Candidate])

  override def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]] = {
    val app1 = ("user1", true, PreferencesWithContactDetails(Some("John"), Some("Smith"), Some("Jo"),
      Some("user1@email.com"), None, Some("location1"), Some("location1Scheme1"), Some("location1Scheme2"), Some("location2"),
      Some("location2Scheme1"), Some("location2Scheme2"), None, Some("2016-12-25 13:00:14")))
    val app2 = ("user2", true, PreferencesWithContactDetails(Some("John"), Some("Smith"), Some("Jo"),
      None, None, None, None, None, None, None, None, None, None))
    Future.successful(app1 :: app2 :: Nil)
  }

  override def findCandidateByUserId(userId: String): Future[Option[Candidate]] = Future.successful(None)

  override def candidateProgressReportNotWithdrawn(frameworkId: String): Future[List[CandidateProgressReportItem]] =
    candidateProgressReport(frameworkId)

  override def candidateProgressReport(frameworkId: String): Future[List[CandidateProgressReportItem]] = Future.successful(List(
    CandidateProgressReportItem("", Some("registered"),
      List(SchemeType.DigitalAndTechnology, SchemeType.Commercial), None, None, None, None, None, None, None, None, None, None))
  )

  override def diversityReport(frameworkId: String): Future[List[ApplicationForDiversityReport]] = ???

  override def onlineTestPassMarkReport(frameworkId: String): Future[List[ApplicationForOnlineTestPassMarkReport]] = ???

  override def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]] =
    Future.successful(
      List(
        AdjustmentReportItem("1", Some("11"), Some("John"), Some("Smith"), Some("Spiderman"), None, None, Some("Yes"),
          Some(ApplicationStatus.SUBMITTED), Some("Need help for online tests"), Some("Need help at the venue"),
          Some("Yes"), Some("A wooden leg")),
        AdjustmentReportItem("2", Some("22"), Some("Jones"), Some("Batman"), None, None, None, None,
          Some(ApplicationStatus.PHASE1_TESTS), None, Some("Need help at the venue"), None, None),
        AdjustmentReportItem("3", Some("33"), Some("Kathrine"), Some("Jones"), Some("Supergirl"), None, None, None,
          Some(ApplicationStatus.PHASE1_TESTS_PASSED), Some("Need help for online tests"), None,
          Some("Yes"), Some("A glass eye"))
      )
    )

  override def findApplicationIdsByLocation(location: String): Future[List[String]] = Future.successful(List())

  override def confirmAdjustments(applicationId: String, data: Adjustments): Future[Unit] = Future.successful(Unit)

  override def findAdjustments(applicationId: String): Future[Option[Adjustments]] =
    Future.successful(
      Some(Adjustments(Some(List("etrayTimeExtension")), Some(true), Some(AdjustmentDetail(Some(5))), None))
    )

  override def saveAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit] = Future.successful(Unit)

  override def findAdjustmentsComment(applicationId: String): Future[Option[AdjustmentsComment]] =
    Future.successful(
      Some(AdjustmentsComment(Some("comment")))
    )

  override def rejectAdjustment(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]] =
    Future.successful(
      List(
        CandidateAwaitingAllocation("1", "John", "Smith", "Spiderman", "London", Some("Some adjustments"), new LocalDate(1988, 1, 21)),
        CandidateAwaitingAllocation("2", "James", "Jones", "Batman", "Bournemouth", Some("Some adjustments"), new LocalDate(1992, 11, 30)),
        CandidateAwaitingAllocation("3", "Katherine", "Jones", "Supergirl", "Queer Camel", None, new LocalDate(1990, 2, 12))
      )
    )

  override def findFailedTestForNotification(failedTestType: FailedTestType): Future[Option[NotificationFailedTest]] = {
    Future.successful(Some(NotificationFailedTest("31009ccc-1ac3-4d55-9c53-1908a13dc5e1", "fbb466a3-13a3-4dd0-93d6-9dfa764a5555", "George")))
  }

  override def gisByApplication(applicationId: String): Future[Boolean] = ???

  override def overallReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]] = ???

  override def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]] = ???

  override def updateStatus(applicationId: String, applicationStatus: ApplicationStatus): Future[Unit] = ???

  override def applicationsWithAssessmentScoresAccepted(frameworkId: String): Future[List[ApplicationPreferences]] = ???

  override def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]] = ???

  override def nextAssessmentCentrePassedOrFailedApplication(): Future[Option[ApplicationForNotification]] = ???

  override def applicationsPassedInAssessmentCentre(frameworkId: String): Future[List[ApplicationPreferencesWithTestResults]] = ???

  override def fix(application: Candidate, issue: FixBatch): Future[Option[Candidate]] = ???

  override def getApplicationsToFix(issue: FixBatch): Future[List[Candidate]] = ???

  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]] = ???

  def saveAssessmentScoreEvaluation(applicationId: String, passmarkVersion: String, evaluationResult: AssessmentRuleCategoryResult,
    newApplicationStatus: ApplicationStatus): Future[Unit] = ???

  def addProgressStatusAndUpdateAppStatus(appId: String, progressStatus: ProgressStatuses.ProgressStatus): Future[Unit] = ???

  def removeProgressStatuses(appId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit] = ???

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = ???
}
