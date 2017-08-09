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

package helpers

import connectors.exchange.SchemeEvaluationResult
import connectors.{ ApplicationClient, ReferenceDataClient, SiftClient }
import connectors.exchange.referencedata.{ Scheme, SiftRequirement }
import connectors.exchange.sift.SiftAnswersStatus
import models._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

case class CurrentSchemeStatus(
  scheme: Scheme,
  status: SchemeStatus.Status,
  failedAtStage: Option[String]
)

class CachedUserWithSchemeData(
  val user: CachedUser,
  val application: ApplicationData,
  val allSchemes: Seq[Scheme],
  val rawSchemesStatus: Seq[SchemeEvaluationResult]
) {

  lazy val currentSchemesStatus = rawSchemesStatus flatMap { schemeResult =>
    allSchemes.find(_.id == schemeResult.schemeId).map { scheme =>

      val (status, failedAt) = schemeResult.result match {
        case "Red" => (SchemeStatus.Red, Some("online tests"))
        case "Green" => (SchemeStatus.Green, None)
        case "Withdrawn" => (SchemeStatus.Withdrawn, None)
      }

      CurrentSchemeStatus(scheme, status, failedAt)
    }
  }

  lazy val successfulSchemes = currentSchemesStatus.filter(_.status == SchemeStatus.Green)
  lazy val failedSchemes = currentSchemesStatus.filter(_.status == SchemeStatus.Red)
  lazy val withdrawnSchemes = currentSchemesStatus.collect { case s if s.status == SchemeStatus.Withdrawn => s.scheme}
  lazy val schemesForSiftForms = successfulSchemes.collect { case s if s.scheme.siftRequirement.contains(SiftRequirement.FORM) => s.scheme }

  lazy val noSuccessfulSchemes = successfulSchemes.size
  lazy val noFailedSchemes = failedSchemes.size
  lazy val noWithdrawnSchemes = withdrawnSchemes.size

  lazy val hasFormRequirement = successfulSchemes.exists(_.scheme.siftRequirement.contains(SiftRequirement.FORM))
  lazy val hasNumericRequirement = successfulSchemes.exists(_.scheme.siftRequirement.contains(SiftRequirement.NUMERIC_TEST))
}

object CachedUserWithSchemeData {
  def apply(
    user: CachedUser,
    application: ApplicationData,
    allSchemes: Seq[Scheme],
    rawSchemesStatus: Seq[SchemeEvaluationResult]): CachedUserWithSchemeData =
      new CachedUserWithSchemeData(user, application, allSchemes, rawSchemesStatus
  )
}
