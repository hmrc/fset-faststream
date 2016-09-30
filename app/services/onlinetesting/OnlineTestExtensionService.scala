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

package services.onlinetesting

import model.ProgressStatuses.{ PHASE1_TESTS_EXPIRED, PHASE1_TESTS_INVITED, PHASE1_TESTS_STARTED }
import model.events.EventTypes.Events
import model.events.{ AuditEvents, DataStoreEvents }
import org.joda.time.DateTime
import repositories._
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import services.onlinetesting.OnlineTestService.TestExtensionException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestExtensionService {
  def extendTestGroupExpiryTime(applicationId: String, extraDays: Int, issuerUserId: String): Future[Events]
}

class OnlineTestExtensionServiceImpl(
  appRepository: GeneralApplicationRepository,
  otRepository: OnlineTestRepository
) extends OnlineTestExtensionService {

  override def extendTestGroupExpiryTime(applicationId: String, extraDays: Int, issuerUserId: String): Future[Events] = {
    // Check the state of this user
    appRepository.findProgress(applicationId) flatMap { progressResponse =>
      if (progressResponse.phase1TestsExpired) {
        for {
          _ <- otRepository.updateGroupExpiryTime(applicationId, DateTime.now().withDurationAdded(86400 * extraDays * 1000, 1))
          phase1TestGroup <- otRepository.getPhase1TestGroup(applicationId)
          progressStatusToSet = if (phase1TestGroup.get.hasNotStartedYet) { PHASE1_TESTS_INVITED } else { PHASE1_TESTS_STARTED }
          progressStatusesToRemove = List(PHASE1_TESTS_EXPIRED) ++ (if (progressStatusToSet == PHASE1_TESTS_INVITED) {
            List(PHASE1_TESTS_STARTED)
          } else {
            Nil
          })
          _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, progressStatusToSet)
          _ <- appRepository.removeProgressStatuses(applicationId, progressStatusesToRemove)
        } yield {
          AuditEvents.ExpiredTestsExtended(Map("applicationId" -> applicationId)) ::
          DataStoreEvents.OnlineExerciseExtended(applicationId, issuerUserId) ::
          Nil
        }
      } else if (progressResponse.phase1TestsInvited || progressResponse.phase1TestsStarted) {
        for {
          phase1TestProfile <- otRepository.getPhase1TestGroup(applicationId)
          existingExpiry = phase1TestProfile.get.expirationDate
          _ <- otRepository.updateGroupExpiryTime(applicationId, existingExpiry.withDurationAdded(86400 * extraDays * 1000, 1))
        } yield {
          AuditEvents.NonExpiredTestsExtended(Map("applicationId" -> applicationId)) ::
          DataStoreEvents.OnlineExerciseExtended(applicationId, issuerUserId) ::
          Nil
        }
      } else {
        throw TestExtensionException("Application is in an invalid status for test extension")
      }
    }
  }
}

object OnlineTestExtensionService extends OnlineTestExtensionServiceImpl(
  applicationRepository, onlineTestRepository
)
