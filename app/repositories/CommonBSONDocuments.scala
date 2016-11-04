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

package repositories

import model.ApplicationStatus.ApplicationStatus
import model.ProgressStatuses.ProgressStatus
import model.{ ApplicationStatus, ProgressStatuses }
import org.joda.time.DateTime
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.BSONDocument

trait CommonBSONDocuments {

  def applicationStatusBSON(applicationStatus: ApplicationStatus) = {
    // TODO the progress status should be propagated up to the caller, rather than default, but that will
    // require widespread changes, and using a default in here is better than the previous implementation
    // that just set the progress status to applicationStatus.toString, which produced invalid progress statuses
    val defaultProgressStatus = ProgressStatuses.tryToGetDefaultProgressStatus(applicationStatus)
    defaultProgressStatus match {
      case Some(progressStatus) =>
        BSONDocument(
          "applicationStatus" -> applicationStatus,
          s"progress-status.${progressStatus.key}" -> true,
          s"progress-status-timestamp.${progressStatus.key}" -> DateTime.now()
        )
        // For in progress application status we store application status in
        // progress-status-timestamp.
      case _ if applicationStatus == ApplicationStatus.IN_PROGRESS =>
        BSONDocument(
          "applicationStatus" -> applicationStatus,
          s"progress-status.${ApplicationStatus.IN_PROGRESS}" -> true,
          s"progress-status-timestamp.${ApplicationStatus.IN_PROGRESS}" -> DateTime.now()
        )
      case _ =>
        BSONDocument(
          "applicationStatus" -> applicationStatus
        )
    }
  }

  def applicationStatusBSON(progressStatus: ProgressStatus) = {
    BSONDocument(
      "applicationStatus" -> progressStatus.applicationStatus,
      s"progress-status.${progressStatus.key}" -> true,
      s"progress-status-timestamp.${progressStatus.key}" -> DateTime.now()
    )
  }

  def progressStatusGuardBSON(progressStatus: ProgressStatus) = {
    BSONDocument(
      "applicationStatus" -> progressStatus.applicationStatus,
      s"progress-status.${progressStatus.key}" -> true
    )
  }

  def validateSingleWriteOrThrow(failureException: => Exception)(writeResult: UpdateWriteResult) = writeResult.n match {
    case 1 => ()
    case _ => throw failureException
  }
}
