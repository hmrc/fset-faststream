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

package services.sift

import factories.DateTimeFactory
import model.ProgressStatuses.{ ProgressStatus, SIFT_EXPIRED, SIFT_FIRST_REMINDER, SIFT_SECOND_REMINDER }
import model.command.ProgressResponse
import model.sift.{ SiftFirstReminder, SiftSecondReminder }
import model.stc.{ AuditEvent, AuditEvents, DataStoreEvents }
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.sift.ApplicationSiftRepository
import services.stc.{ EventSink, StcEventService }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SiftExpiryExtensionService extends SiftExpiryExtensionService {
  val appRepository = applicationRepository
  val siftRepository = applicationSiftRepository
  val dateTimeFactory = DateTimeFactory
  val eventService = StcEventService
}

trait SiftExpiryExtensionService extends EventSink {
  val appRepository: GeneralApplicationRepository
  val siftRepository: ApplicationSiftRepository
  val dateTimeFactory: DateTimeFactory
  import SiftExpiryExtensionServiceImpl._

  def extendExpiryTime(applicationId: String, extraDays: Int, actionTriggeredBy: String)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {

    val extension = for {
      progressResponse <- appRepository.findProgress(applicationId)
      siftTestGroup <- siftRepository.getTestGroup(applicationId)
    } yield {

      (progressResponse, siftTestGroup) match {
        case (progress, Some(group)) if progress.siftProgressResponse.siftExpired =>
          Extension(dateTimeFactory.nowLocalTimeZone.plusDays(extraDays), expired = true, progressResponse)
        case (progress, Some(group)) if !progress.siftProgressResponse.siftExpired &&
          progress.siftProgressResponse.siftEntered =>
          Extension(group.expirationDate.plusDays(extraDays), expired = false, progressResponse)
        case (_, None) =>
          throw SiftExtensionException("No Sift test group available for the given application")
        case _ =>
          throw SiftExtensionException("Application is in an invalid state for sift extension")
      }
    }

    for {
      Extension(extendedExpiryDate, expired, progress) <- extension
      _ <- siftRepository.updateExpiryTime(applicationId, extendedExpiryDate)
      _ <- getProgressStatusesToRemove(progress).fold(NoOp)(p => appRepository.removeProgressStatuses(applicationId, p))
    } yield {

      audit(expired, applicationId) ::
        DataStoreEvents.SiftNumericExerciseExtended(applicationId, actionTriggeredBy) ::
        Nil
    }
  }

  private def audit(expired: Boolean, applicationId: String): AuditEvent = {
    val details = Map("applicationId" -> applicationId)
    if (expired) {
      AuditEvents.ExpiredSiftExtended(details)
    } else {
      AuditEvents.NonExpiredSiftExtended(details)
    }
  }
}

case class SiftExtensionException(message: String) extends Exception(message)

private final case class Extension(extendedExpiryDate: DateTime, expired: Boolean, progress: ProgressResponse)

object SiftExpiryExtensionServiceImpl {

  val NoOp: Future[Unit] = Future.successful(())

  def getProgressStatusesToRemove(progress: ProgressResponse): Option[List[ProgressStatus]] = {

    val progressStatusList = (Set.empty[ProgressStatus]
      ++ cond(progress.siftProgressResponse.siftExpired, SIFT_EXPIRED)
      ++ cond(progress.siftProgressResponse.siftSecondReminder, SIFT_SECOND_REMINDER)
      ++ cond(progress.siftProgressResponse.siftFirstReminder, SIFT_FIRST_REMINDER)
      ).toList

    if (progressStatusList.isEmpty) { None } else { Some(progressStatusList) }
  }

  private def cond[T]( lazyCondition : => Boolean, value : T ) : Set[T] = if(lazyCondition) Set(value) else Set.empty
}
