/*
 * Copyright 2023 HM Revenue & Customs
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

import model.ProgressStatuses.ProgressStatus
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document

trait OnlineTestCommonBSONDocuments {
  def inviteToTestBSON[P <: ProgressStatus](targetProgressStatus: P) = {
    Document("$and" -> BsonArray(
      Document("applicationStatus" -> targetProgressStatus.applicationStatus.toString),
      Document(s"progress-status.${targetProgressStatus.key}" -> true),
      Document("$or" -> BsonArray(
        Document("assistance-details.guaranteedInterview" -> Document("$ne" -> true)),
        Document("assistance-details.adjustmentsConfirmed" -> true)
      ))
    ))
  }
}
