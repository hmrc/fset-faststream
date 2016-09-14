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

import model.{ApplicationStatus, ProgressStatuses}
import org.joda.time.LocalDate
import reactivemongo.bson.BSONDocument


trait CommonBSONDocuments {

  def applicationStatusBSON( progressStatus: ProgressStatuses.ProgressStatus): BSONDocument = {

    BSONDocument(
      "applicationStatus" -> progressStatus.applicationStatus,
      s"progress-status.$progressStatus" -> true,
      s"progress-status-dates.$progressStatus" -> LocalDate.now()
    )
  }

  def applicationStatusBSON(status: String)(implicit progressStatus: String = status.toLowerCase) = {
    BSONDocument(
      "applicationStatus" -> status,
      s"progress-status.$progressStatus" -> true,
      s"progress-status-dates.$progressStatus" -> LocalDate.now()
    )
  }

}
