/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import play.api.libs.json.{ Json, OFormat }
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

case class Phase1TestProfile(expirationDate: DateTime,
                             tests: List[PsiTest],
                             evaluation: Option[PassmarkEvaluation] = None) extends PsiTestProfile

object Phase1TestProfile {

  import repositories.BSONDateTimeHandler

  implicit val bsonHandler: BSONHandler[BSONDocument, Phase1TestProfile] = Macros.handler[Phase1TestProfile]
  implicit val phase1TestProfileFormat = Json.format[Phase1TestProfile]
}

case class PsiTest(inventoryId: String,
                   orderId: String,
                   usedForResults: Boolean,
                   testUrl: String,
                   testProvider: String = "psi",
                   invitationDate: DateTime,
                   startedDateTime: Option[DateTime] = None,
                   completedDateTime: Option[DateTime] = None,
                   resultsReadyToDownload: Boolean = false,
                   assessmentId: String,
                   reportId: String,
                   normId: String,
                   reportLinkURL: Option[String] = None,
                   reportStatus: Option[String] = None,
                   testResult: Option[model.persisted.PsiTestResult] = None,
                   invigilatedAccessCode: Option[String] = None
                  ) extends Test {
  def isCompleted = completedDateTime.isDefined
}

object PsiTest {
  import repositories.BSONDateTimeHandler

  implicit val psiTestHandler: BSONHandler[BSONDocument, PsiTest] = Macros.handler[PsiTest]
  implicit val psiTestFormat: OFormat[PsiTest] = Json.format[PsiTest]
}
