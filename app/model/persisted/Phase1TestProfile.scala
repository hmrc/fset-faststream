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
import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.play.json.Codecs

import java.time.OffsetDateTime

case class Phase1TestProfile(expirationDate: OffsetDateTime,
                             tests: List[PsiTest],
                             evaluation: Option[PassmarkEvaluation] = None) extends PsiTestProfile

object Phase1TestProfile {

  implicit val phase1TestProfileFormat = Json.format[Phase1TestProfile]

  implicit class BsonOps(val phase1TestProfile: Phase1TestProfile) extends AnyVal {
    def toBson: BsonValue = Codecs.toBson(phase1TestProfile)
  }
}

case class PsiTest(inventoryId: String,
                   orderId: String,
                   usedForResults: Boolean,
                   testUrl: String,
                   testProvider: String = "psi",
                   invitationDate: OffsetDateTime,
                   startedDateTime: Option[OffsetDateTime] = None,
                   completedDateTime: Option[OffsetDateTime] = None,
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
  def toExchange = model.exchange.PsiTest(
    inventoryId,
    orderId,
    usedForResults,
    testUrl,
    testProvider,
    invitationDate,
    startedDateTime,
    completedDateTime,
    resultsReadyToDownload,
    assessmentId,
    reportId,
    normId,
    reportLinkURL,
    reportStatus,
    testResult,
    invigilatedAccessCode
  )
}

object PsiTest {
  implicit val psiTestFormat: OFormat[PsiTest] = Json.format[PsiTest]
}
