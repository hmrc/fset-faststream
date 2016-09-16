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

import factories.DateTimeFactory
import org.joda.time.DateTime
import repositories._
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import services.onlinetesting.OnlineTestService.TestExtensionException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestExtensionService {
  def extendTestGroupExpiryTime(groupKey: String, applicationId: String, extraDays: Int): Future[Unit]
}

class OnlineTestExtensionServiceImpl(
  appRepository: GeneralApplicationRepository,
  otRepository: OnlineTestRepository,
  dateTime: DateTimeFactory
) extends OnlineTestExtensionService {

  override def extendTestGroupExpiryTime(groupKey: String, applicationId: String, extraDays: Int): Future[Unit] = {
    // Check the state of this user
    appRepository.findProgress(applicationId).map { progressResponse =>
      if (progressResponse.phase1TestsInvited || progressResponse.phase1TestsStarted) {
        // Get existing expiry
        //otRepository.getPhase1TestProfile(applicationId).map { testGroup =>

        //}
        // Extend
      } else if (progressResponse.phase1TestsExpired) {

      } else {
        throw TestExtensionException("Application is in an invalid status for test extension")
      }

      //otRepository.getOnlineTestApplication(applicationId).flatMap {
      //case Some(onlineTestApp) =>
      //onlineTestExtensionService.extendExpiryTime(onlineTestApp, extension.extraDays).map { _ =>

      //   }
      //}

      /*val userId = application.userId
    for {
      expiryDate <- getExpiryDate(userId)
      newExpiryDate = calculateNewExpiryDate(expiryDate, extraDays)
      // TODO FAST STREAM FIX ME _ <- otRepository.updateExpiryTime(userId, newExpiryDate)
    } yield ()*/
    }
  }
    /*
  private def getExpiryDate(userId: String): Future[DateTime] = {
    // TODO FAST STREAM FIX ME
    Future.successful(DateTime.now())
    //otRepository.getPhase1TestProfile(userId).map(_.expireDate)
  }

  private def calculateNewExpiryDate(expiryDate: DateTime, extraDays: Int): DateTime =
    max(dateTime.nowLocalTimeZone, expiryDate).plusDays(extraDays)

  private def max(dateTime1: DateTime, dateTime2: DateTime): DateTime = {
    implicit val dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
    List(dateTime1, dateTime2).max
  }*/
}

object OnlineTestExtensionService extends OnlineTestExtensionServiceImpl(applicationRepository, onlineTestRepository, DateTimeFactory)
