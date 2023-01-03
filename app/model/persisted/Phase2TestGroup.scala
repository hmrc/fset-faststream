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

package model.persisted

import org.joda.time.DateTime
import org.mongodb.scala.bson.BsonValue
import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._ // Needed to handle storing ISODate format
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.play.json.Codecs

case class Phase2TestGroup(expirationDate: DateTime,
  tests: List[PsiTest],
  evaluation: Option[PassmarkEvaluation] = None
) extends PsiTestProfile

object Phase2TestGroup {
  implicit val phase2TestProfileFormat = Json.format[Phase2TestGroup]

  implicit class BsonOps(val phase2TestGroup: Phase2TestGroup) extends AnyVal {
    def toBson: BsonValue = Codecs.toBson(phase2TestGroup)
  }
}
