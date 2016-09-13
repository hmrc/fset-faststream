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

import controllers.OnlineTestDetails
import model.EvaluationResults._
import model.OnlineTestCommands
import model.OnlineTestCommands.{OnlineTestApplication, OnlineTestApplicationWithCubiksUser, OnlineTestProfile}
import model.PersistedObjects.{ApplicationForNotification, ApplicationIdWithUserIdAndStatus, ExpiringOnlineTest}
import org.joda.time.{DateTime, LocalDate}
import repositories.application.OnlineTestRepository

import scala.collection.mutable
import scala.concurrent.Future

/**
 * @deprecated Please use Mockito
 */
object OnlineTestInMemoryRepository extends OnlineTestInMemoryRepository

/**
 * @deprecated Please use Mockito
 */
class OnlineTestInMemoryRepository extends OnlineTestRepository {
  val inMemoryRepo = new mutable.HashMap[String, RuleCategoryResult]

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] =
    Future.successful(Some(OnlineTestApplication("appId", "appStatus", "userId", false, false, "Test Preferred Name", None)))

  def getOnlineTestDetails(userId: String): Future[OnlineTestDetails] = Future.successful {
    val date = DateTime.now
    OnlineTestDetails(date, date.plusDays(4), "http://www.google.co.uk", "123@test.com", isOnlineTestEnabled = true)
  }

  def updateStatus(userId: String, status: String): Future[Unit] = Future.successful(Unit)

  def updateExpiryTime(userId: String, expirationDate: DateTime): Future[Unit] = Future.successful(Unit)

  def consumeToken(token: String): Future[Unit] = Future.successful(Unit)

  def storeOnlineTestProfileAndUpdateStatusToInvite(applicationId: String, onlineTestProfile: OnlineTestProfile): Future[Unit] =
    Future.successful(Unit)

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = Future.successful(None)

  def nextApplicationPendingExpiry: Future[Option[ExpiringOnlineTest]] = Future.successful(None)

  def nextApplicationPendingFailure: Future[Option[ApplicationForNotification]] = Future.successful(None)

  def nextApplicationReadyForReportRetriving: Future[Option[OnlineTestApplicationWithCubiksUser]] = Future.successful(None)

  def nextApplicationReadyForPDFReportRetrieving(): Future[Option[OnlineTestApplicationWithCubiksUser]] = Future.successful(None)

  def saveOnlineTestReport(applicationId: String, report: String): Future[Unit] = Future.successful(None)

  def saveCandidateAllocationStatus(applicationId: String, applicationStatus: String, expireDate: Option[LocalDate]): Future[Unit] =
    Future.successful(())

  def storeOnlineTestProfile(applicationId: String, onlineTestProfile: OnlineTestCommands.OnlineTestProfile): Future[Unit] = ???

  def removeOnlineTestEvaluationAndReports(applicationId: String): Future[Unit] = ???

  def savePassMarkScoreWithoutApplicationStatusUpdate(applicationId: String, version: String, p: RuleCategoryResult): Future[Unit] = ???
}
