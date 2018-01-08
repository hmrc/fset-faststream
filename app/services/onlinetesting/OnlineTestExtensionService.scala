/*
 * Copyright 2018 HM Revenue & Customs
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
import model.persisted.Phase1TestProfile
import model.ProgressStatuses._
import model.command.ProgressResponse
import model.stc.{AuditEvent, AuditEvents, DataStoreEvents}
import model.{ Phase1FirstReminder, Phase1SecondReminder }
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase1TestRepository
import services.AuditService
import services.stc.{ StcEventService, EventSink}
import services.onlinetesting.Exceptions.TestExtensionException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object OnlineTestExtensionService extends OnlineTestExtensionService {
  val appRepository = applicationRepository
  val otRepository = phase1TestRepository
  val auditService = AuditService
  val dateTimeFactory = DateTimeFactory
  val eventService = StcEventService
}

trait OnlineTestExtensionService extends EventSink {
  val appRepository: GeneralApplicationRepository
  val otRepository: Phase1TestRepository
  val auditService: AuditService
  val dateTimeFactory: DateTimeFactory
  import OnlineTestExtensionServiceImpl._

  def extendTestGroupExpiryTime(applicationId: String, extraDays: Int, actionTriggeredBy: String)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {

    val extension = for {
      progressResponse <- appRepository.findProgress(applicationId)
      phase1TestGroup <- otRepository.getTestGroup(applicationId)
    } yield {
      (progressResponse, phase1TestGroup) match {
        case (progress, Some(group)) if progress.phase1ProgressResponse.phase1TestsExpired =>
          Extension(dateTimeFactory.nowLocalTimeZone.plusDays(extraDays), expired = true, group, progressResponse)
        case (_, Some(group)) if progressResponse.phase1ProgressResponse.phase1TestsInvited ||
          progressResponse.phase1ProgressResponse.phase1TestsStarted =>
          Extension(group.expirationDate.plusDays(extraDays), expired = false, group, progressResponse)
        case (_, None) =>
          throw TestExtensionException("No Phase1TestGroupAvailable for the given application")
        case _ =>
          throw TestExtensionException("Application is in an invalid status for test extension")
      }
    }

    for {
      Extension(date, expired, profile, progress) <- extension
      _ <- otRepository.updateGroupExpiryTime(applicationId, date, otRepository.phaseName)
      _ <- getProgressStatusesToRemove(date, profile, progress).fold(NoOp)(p => appRepository.removeProgressStatuses(applicationId, p))
    } yield {
      audit(expired, applicationId) ::
      DataStoreEvents.OnlineExerciseExtended(applicationId, actionTriggeredBy) ::
      Nil
    }

  }

  private def audit(expired: Boolean, applicationId: String): AuditEvent = {
    val details = Map("applicationId" -> applicationId)
    if (expired) {
      AuditEvents.ExpiredTestsExtended(details)
    } else {
      AuditEvents.NonExpiredTestsExtended(details)
    }
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
        ++ cond(progress.phase1ProgressResponse.phase1TestsExpired, PHASE1_TESTS_EXPIRED)
        ++ cond(profile.hasNotStartedYet, PHASE1_TESTS_STARTED)
        ++ cond(extendedExpiryDate.minusHours(Phase1SecondReminder.hoursBeforeReminder).isAfter(today), PHASE1_TESTS_SECOND_REMINDER)
        ++ cond(extendedExpiryDate.minusHours(Phase1FirstReminder.hoursBeforeReminder).isAfter(today), PHASE1_TESTS_FIRST_REMINDER)).toList
    if(progressList.isEmpty) { None } else { Some(progressList) }
  }

  private[this] def cond[T]( lazyCondition : => Boolean, value : T ) : Set[T] = if(lazyCondition) Set(value) else Set.empty
}
