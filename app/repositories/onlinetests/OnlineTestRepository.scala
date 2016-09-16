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

package repositories.onlinetests

import model.OnlineTestCommands.Phase1TestProfile
import org.joda.time.DateTime

import scala.concurrent.Future

trait OnlineTestRepository {

  //def nextApplicationPendingExpiry: Future[Option[ExpiringOnlineTest]]

  //def nextApplicationPendingFailure: Future[Option[ApplicationForNotification]]

  //def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]]

  def getPhase1TestProfile(applicationId: String): Future[Option[Phase1TestProfile]]

  def updateGroupExpiryTime(groupKey: String, newExpirationDate: DateTime): Future[Unit]

  def insertPhase1TestProfile(applicationId: String, phase1TestProfile: Phase1TestProfile): Future[Unit]

  def setTestStatusFlag(applicationId: String, testToken: String, flag: OnlineTestStatusFlags.Value): Future[Unit]
  //def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]]

  //def updateXMLReportSaved(applicationId: String): Future[Unit]

  //def nextApplicationPassMarkProcessing(currentVersion: String): Future[Option[ApplicationIdWithUserIdAndStatus]]

  //def savePassMarkScore(applicationId: String, version: String, p: RuleCategoryResult, applicationStatus: String): Future[Unit]

  //def savePassMarkScoreWithoutApplicationStatusUpdate(applicationId: String, version: String, p: RuleCategoryResult): Future[Unit]

  //def removeCandidateAllocationStatus(applicationId: String): Future[Unit]

  //def saveCandidateAllocationStatus(applicationId: String, applicationStatus: String, expireDate: Option[LocalDate]): Future[Unit]
}
