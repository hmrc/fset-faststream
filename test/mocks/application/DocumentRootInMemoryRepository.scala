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

import model.AssessmentScheduleCommands.{ ApplicationForAssessmentAllocation, ApplicationForAssessmentAllocationResult }
import model.Commands._
import model.command._
import model.EvaluationResults.AssessmentRuleCategoryResult
import model.Exceptions.ApplicationNotFound
import model.FastPassDetails
import model.PersistedObjects.ApplicationForNotification
import org.joda.time.{ DateTime, LocalDate }
import repositories.application.GeneralApplicationRepository

import scala.collection.mutable
import scala.concurrent.Future
object DocumentRootInMemoryRepository extends DocumentRootInMemoryRepository

/**
 * @deprecated Please use Mockito
 */
class DocumentRootInMemoryRepository extends GeneralApplicationRepository {

  override def find(applicationIds: List[String]): Future[List[Candidate]] = ???

  override def create(userId: String, frameworkId: String): Future[ApplicationResponse] = {

    val applicationId = java.util.UUID.randomUUID().toString
    val applicationCreated = ApplicationResponse(applicationId, "CREATED", userId,
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
      val applicationCreated = ApplicationResponse(applicationId, "CREATED", userId,
        ProgressResponse(applicationId), None)
      Future.successful(applicationCreated)
  }

  override def findApplicationsForAssessmentAllocation(locations: List[String], start: Int,
    end: Int): Future[ApplicationForAssessmentAllocationResult] = {
    Future.successful(ApplicationForAssessmentAllocationResult(List(ApplicationForAssessmentAllocation("firstName", "lastName", "userId1",
      "applicationId1", "No", DateTime.now)), 1))
  }

  override def submit(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def withdraw(applicationId: String, reason: WithdrawApplicationRequest): Future[Unit] = Future.successful(Unit)

  override def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit] = Future.successful(Unit)

  override def preview(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def findByCriteria(lastName: Option[String], dateOfBirth: Option[LocalDate]): Future[List[Candidate]] =
    Future.successful(List.empty[Candidate])

  override def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]] = {
    val app1 = ("user1", true, PreferencesWithContactDetails(Some("John"), Some("Smith"), Some("Jo"),
      Some("user1@email.com"), None, Some("location1"), Some("location1Scheme1"), Some("location1Scheme2"), Some("location2"),
      Some("location2Scheme1"), Some("location2Scheme2"), None, Some("2016-12-25 13:00:14")))
    val app2 = ("user2", true, PreferencesWithContactDetails(Some("John"), Some("Smith"), Some("Jo"),
      None, None, None, None, None, None, None, None, None, None))
    Future.successful(app1 :: app2 :: Nil)
  }

  override def findCandidateByUserId(userId: String): Future[Option[Candidate]] = Future.successful(None)

  override def overallReportNotWithdrawn(frameworkId: String): Future[List[Report]] = overallReport(frameworkId)

  override def overallReport(frameworkId: String): Future[List[Report]] = Future.successful(List(
    Report("123", Some("SUBMITTED"), Some("London"), Some("Business"), None, None, None, None,
      Some("Yes"), Some("Yes"), Some("Yes"), Some("No"), Some("No"), Some("No"), Some("No"), None),
    Report("456", Some("IN_PROGRESS"), Some("London"), Some("Business"), None, None, None, None,
      Some("Yes"), Some("Yes"), Some("Yes"), Some("No"), Some("No"), Some("No"), Some("No"), None),
    Report("789", Some("SUBMITTED"), Some("London"), Some("Business"), None, None, None, None,
      Some("Yes"), Some("Yes"), Some("Yes"), Some("No"), Some("No"), Some("No"), Some("No"), None)
  ))

  override def adjustmentReport(frameworkId: String): Future[List[AdjustmentReport]] =
    Future.successful(
      List(
        AdjustmentReport("1", Some("John"), Some("Smith"), Some("Spiderman"), None, None, Some("Some adjustments"), Some("Yes"), Some("Yes")),
        AdjustmentReport("2", Some("James"), Some("Jones"), Some("Batman"), None, None, Some("Some adjustments"), Some("Yes"), Some("No")),
        AdjustmentReport("3", Some("Kathrine"), Some("Jones"), Some("Supergirl"), None, None, Some("Some adjustments"), Some("Yes"), Some("No"))
      )
    )

  override def findApplicationIdsByLocation(location: String): Future[List[String]] = Future.successful(List())

  override def confirmAdjustment(applicationId: String, data: AdjustmentManagement): Future[Unit] = Future.successful(Unit)

  override def rejectAdjustment(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]] =
    Future.successful(
      List(
        CandidateAwaitingAllocation("1", "John", "Smith", "Spiderman", "London", Some("Some adjustments"), new LocalDate(1988, 1, 21)),
        CandidateAwaitingAllocation("2", "James", "Jones", "Batman", "Bournemouth", Some("Some adjustments"), new LocalDate(1992, 11, 30)),
        CandidateAwaitingAllocation("3", "Katherine", "Jones", "Supergirl", "Queer Camel", None, new LocalDate(1990, 2, 12))
      )
    )

  override def gisByApplication(userId: String): Future[Boolean] = ???

  override def overallReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]] = ???

  override def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]] = ???

  override def updateStatus(applicationId: String, status: String): Future[Unit] = ???

  override def applicationsWithAssessmentScoresAccepted(frameworkId: String): Future[List[ApplicationPreferences]] = ???

  override def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]] = ???

  override def nextAssessmentCentrePassedOrFailedApplication(): Future[Option[ApplicationForNotification]] = ???

  override def applicationsPassedInAssessmentCentre(frameworkId: String): Future[List[ApplicationPreferencesWithTestResults]] = ???

  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]] = ???

  def saveAssessmentScoreEvaluation(applicationId: String, passmarkVersion: String, evaluationResult: AssessmentRuleCategoryResult,
    newApplicationStatus: String): Future[Unit] = ???
}
