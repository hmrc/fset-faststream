/*
 * Copyright 2017 HM Revenue & Customs
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

package repositories.fsb

import factories.DateTimeFactory
import model.persisted.FsbTestGroup
import reactivemongo.api.DB
import reactivemongo.bson.BSONObjectID
import repositories.CollectionNames
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

object FsbTestRepository {
}

class FsbTestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[FsbTestGroup, BSONObjectID](CollectionNames.APPLICATION, mongo,
    model.persisted.FsbTestGroup.format, ReactiveMongoFormats.objectIdFormats
  )
