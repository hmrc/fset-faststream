/*
 * Copyright 2018 HM Revenue & Customs
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

package services.evaluation

import model.EvaluationResults._
import model.SchemeId
import model.exchange.passmarksettings.{ AssessmentCentrePassMark, AssessmentCentrePassMarkSettings, PassMarkThreshold }
import model.persisted.SchemeEvaluationResult

trait AssessmentCentreAllSchemesEvaluator {

  def evaluateSchemes(applicationId: String,
    passmark: AssessmentCentrePassMarkSettings,
    overallScore: Double,
    schemes: Seq[SchemeId]): Seq[SchemeEvaluationResult] = {
    schemes.map { scheme =>
      val assessmentCentrePassMark = passmark.schemes.find { _.schemeId == scheme }
        .getOrElse(throw new IllegalStateException(s"Did not find assessment centre pass mark for scheme = $scheme, " +
          s"applicationId = $applicationId"))
      val result = evaluateScore(assessmentCentrePassMark, passmark, overallScore)
      SchemeEvaluationResult(scheme, result.toString)
    }
  }

  private def evaluateScore(scheme: AssessmentCentrePassMark,
    passmark: AssessmentCentrePassMarkSettings,
    overallScore: Double): Result = {
    val passmarkSetting = scheme.schemeThresholds.assessmentCentre
    determineSchemeResult(overallScore, passmarkSetting)
  }

  private def determineSchemeResult(overallScore: Double, passmarkThreshold: PassMarkThreshold): Result = {
    if (overallScore >= passmarkThreshold.passThreshold) {
      Green
    } else if (overallScore >= passmarkThreshold.failThreshold) {
      Amber
    } else {
      Red
    }
  }
}
