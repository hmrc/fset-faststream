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

package model.command

import model.persisted.PassmarkEvaluation
import play.api.libs.json.{ Json, OFormat }
import reactivemongo.bson.BSONDocument

case class ApplicationForFsac(
  applicationId: String,
  evaluationResult: PassmarkEvaluation
)

object ApplicationForFsac{
  implicit val applicationForFsacFormat: OFormat[ApplicationForFsac] = Json.format[ApplicationForFsac]

  implicit def applicationForFsacBsonReads(document: BSONDocument): ApplicationForFsac = {
    val applicationId = document.getAs[String]("applicationId").get
    val testGroupsRoot = document.getAs[BSONDocument]("testGroups").get
    val phase3PassMarks = testGroupsRoot.getAs[BSONDocument]("PHASE3").get
    val phase3Evaluation = phase3PassMarks.getAs[PassmarkEvaluation]("evaluation").get
    ApplicationForFsac(applicationId, phase3Evaluation)
  }
}
