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

import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.play.json.Codecs

import scala.util.Try

case class FsbSchemeResult(applicationId: String, results: List[SchemeEvaluationResult])

object FsbSchemeResult {
  implicit val jsonFormat: OFormat[FsbSchemeResult] = Json.format[FsbSchemeResult]

  def fromBson(doc: Document): Option[FsbSchemeResult] = {
    val applicationId = doc.get("applicationId").get.asString().getValue
    val fsbTestGroupOpt = doc.get("testGroups").map( _.asDocument().get("FSB") )
      .flatMap( fsb => Try(Codecs.fromBson[FsbTestGroup](fsb)).toOption )
    fsbTestGroupOpt.map(fsbTestGroup => FsbSchemeResult(applicationId, fsbTestGroup.evaluation.result))
  }
}
