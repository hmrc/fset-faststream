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

package model.exchange

import model.persisted.Test
import play.api.libs.json.{Json, OFormat}

import java.time.OffsetDateTime // This is needed for DateTime serialization

case class Phase1TestGroupWithNames(applicationId: String, expirationDate: OffsetDateTime, activeTests: Seq[PsiTest])

object Phase1TestGroupWithNames {
  implicit val phase1TestGroupWithNamesFormat: OFormat[Phase1TestGroupWithNames] = Json.format[Phase1TestGroupWithNames]
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
}

object PsiTest {
  implicit val psiTestFormat: OFormat[PsiTest] = Json.format[PsiTest]
}
