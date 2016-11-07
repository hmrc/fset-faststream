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

import model.ProgressStatuses.ProgressStatus
import reactivemongo.bson.{ BSONArray, BSONDocument }

trait OnlineTestCommonBSONDocuments {
  def inviteToTestBSON[P <: ProgressStatus](targetProgressStatus: P, invigilatedKeyToExclude: String) = {
    BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> targetProgressStatus.applicationStatus.toString),
      BSONDocument(s"progress-status.${targetProgressStatus.key}" -> true),
      BSONDocument("$or" -> BSONArray(
        BSONDocument("$and" -> BSONArray(
          BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> false),
          BSONDocument("assistance-details.needsSupportAtVenue" -> false),
          BSONDocument("assistance-details.guaranteedInterview" -> BSONDocument("$ne" -> true)))),
        BSONDocument("$and" -> BSONArray(
          BSONDocument("$or" -> BSONArray(
            BSONDocument("assistance-details.guaranteedInterview" -> true),
            BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> true),
            BSONDocument("assistance-details.needsSupportAtVenue" -> true)
          )),
          BSONDocument("assistance-details.adjustmentsConfirmed" -> true),
          BSONDocument("assistance-details.typeOfAdjustments" -> BSONDocument("$ne" -> invigilatedKeyToExclude)))
        ))
      ))
    )
    // Invigilated etray with adjustments confirmed
    /*BSONDocument("$and" -> BSONArray(
      BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> true),
      BSONDocument("assistance-details.adjustmentsConfirmed" -> true),
      BSONDocument("assistance-details.typeOfAdjustments" -> "etrayInvigilated")
    )),*/

    // TODO: We want to distinguish between invigilated and non-invigilated at this point because we might want to deliver
    // functionality even if invigilated test functionality is not ready. In that case we will remove some code
  }
}
