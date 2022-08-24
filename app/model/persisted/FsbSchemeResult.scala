/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.play.json.Codecs

import scala.util.Try
//import reactivemongo.bson.{ BSONDocument, BSONDocumentReader }

case class FsbSchemeResult(applicationId: String, results: List[SchemeEvaluationResult])

object FsbSchemeResult {
  implicit val jsonFormat = Json.format[FsbSchemeResult]

/*
  implicit object FsbResultReader extends BSONDocumentReader[Option[FsbSchemeResult]] {
    def read(document: BSONDocument): Option[FsbSchemeResult] = {
      for {
        applicationId <- document.getAs[String]("applicationId")
        testGroups <- document.getAs[BSONDocument]("testGroups")
        fsbTestGroup <- testGroups.getAs[FsbTestGroup]("FSB")
      } yield FsbSchemeResult(applicationId, fsbTestGroup.evaluation.result)
    }
  }*/

  def fromBson(doc: Document): Option[FsbSchemeResult] = {
    val applicationId = doc.get("applicationId").get.asString().getValue
    val fsbTestGroupOpt = doc.get("testGroups").map( _.asDocument().get("FSB") )
      .flatMap( fsb => Try(Codecs.fromBson[FsbTestGroup](fsb)).toOption )
    fsbTestGroupOpt.map(fsbTestGroup => FsbSchemeResult(applicationId, fsbTestGroup.evaluation.result))
  }
}
