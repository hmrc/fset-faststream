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
import model.{ FirstReminder, SecondReminder }
import model.OnlineTestCommands.Phase1TestProfile
import model.ProgressStatuses._
import model.command.ProgressResponse
import org.joda.time.DateTime
import play.api.Logger
import repositories._
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import services.AuditService
import services.onlinetesting.OnlineTestService.TestExtensionException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestExtensionService {
  def extendTestGroupExpiryTime(applicationId: String, extraDays: Int): Future[Unit]
}

class OnlineTestExtensionServiceImpl(
  appRepository: GeneralApplicationRepository,
  otRepository: OnlineTestRepository,
  auditService: AuditService,
  dateTimeFactory: DateTimeFactory
) extends OnlineTestExtensionService {

  import OnlineTestExtensionServiceImpl._

  override def extendTestGroupExpiryTime(applicationId: String, extraDays: Int): Future[Unit] = {

    val extension = for {
      progressResponse <- appRepository.findProgress(applicationId)
      phase1TestGroup <- otRepository.getPhase1TestGroup(applicationId)
    } yield {
      (progressResponse, phase1TestGroup) match {
        case (progress, Some(group)) if progress.phase1TestsExpired =>
          Extension(dateTimeFactory.nowLocalTimeZone.plusDays(extraDays), true, group, progressResponse)
        case (progress, Some(group)) if (progressResponse.phase1TestsInvited || progressResponse.phase1TestsStarted) =>
          Extension(group.expirationDate.plusDays(extraDays), false, group, progressResponse)
        case (progress, None) =>
          throw TestExtensionException("No Phase1TestGroupAvailable for the given application")
        case _ =>
          throw TestExtensionException("Application is in an invalid status for test extension")
      }
    }

    for {
      Extension(date, expired, profile, progress) <- extension
      _ <- otRepository.updateGroupExpiryTime(applicationId, date)
      _ <- getProgressStatusesToRemove(date, profile, progress).fold(NoOp)(p => appRepository.removeProgressStatuses(applicationId, p))
    } yield {
      audit(expired, applicationId)
    }

  }

  private def audit(expired: Boolean, applicationId: String): Unit = {
    if (expired) { auditEvent("ExpiredTestsExtended", applicationId) }
    else { auditEvent("NonExpiredTestsExtended", applicationId) }
  }

  private def auditEvent(eventName: String, applicationId: String): Unit = {
    Logger.info(s"$eventName for applicationId '$applicationId'")

    auditService.logEventNoRequest(eventName, Map(
      "applicationId" -> applicationId
    ))
  }

}

private final case class Extension(extendedExpiryDate: DateTime, expired: Boolean, profile: Phase1TestProfile, progress: ProgressResponse)

object OnlineTestExtensionServiceImpl {

  val NoOp: Future[Unit] = Future.successful(())

  def getProgressStatusesToRemove(extendedExpiryDate: DateTime,
                                  profile: Phase1TestProfile,
                                  progress: ProgressResponse): Option[List[ProgressStatus]] = {

    val today = DateTime.now()
    val progressList = (Set.empty[ProgressStatus]
        ++ cond(progress.phase1TestsExpired, PHASE1_TESTS_EXPIRED)
        ++ cond(profile.hasNotStartedYet, PHASE1_TESTS_STARTED)
        ++ cond(extendedExpiryDate.minusHours(SecondReminder.hoursBeforeReminder).isAfter(today), PHASE1_TESTS_SECOND_REMINDER)
        ++ cond(extendedExpiryDate.minusHours(FirstReminder.hoursBeforeReminder).isAfter(today), PHASE1_TESTS_FIRST_REMINDER)).toList
    if(progressList.isEmpty) { None } else { Some(progressList) }
  }

  private[this] def cond[T]( lazyCondition : => Boolean, value : T ) : Set[T] = if(lazyCondition) Set(value) else Set.empty
}

object OnlineTestExtensionService extends OnlineTestExtensionServiceImpl(
  applicationRepository, onlineTestRepository, AuditService, DateTimeFactory
)
