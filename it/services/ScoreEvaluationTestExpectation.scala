/*
 * Copyright 2016 HM Revenue & Customs
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

package services

import model.EvaluationResults.{CompetencyAverageResult, PerSchemeEvaluation, Result}

case class ScoreEvaluationTestExpectation(location1Scheme1: String, location1Scheme2: Option[String], location2Scheme1: Option[String],
                                          location2Scheme2: Option[String], alternativeScheme: Option[String], applicationStatus: String,
                                          passmarkVersion: Option[String])

case class AssessmentScoreEvaluationTestExpectation(location1Scheme1: Option[String], location1Scheme2: Option[String],
                                                    location2Scheme1: Option[String], location2Scheme2: Option[String],
                                                    alternativeScheme: Option[String], applicationStatus: String,
                                                    passmarkVersion: Option[String], passedMinimumCompetencyLevel: Option[Boolean],
                                                    leadingAndCommunicatingAverage: Option[Double],
                                                    collaboratingAndPartneringAverage: Option[Double],
                                                    deliveringAtPaceAverage: Option[Double],
                                                    makingEffectiveDecisionsAverage: Option[Double],
                                                    changingAndImprovingAverage: Option[Double],
                                                    buildingCapabilityForAllAverage: Option[Double],
                                                    motivationFitAverage: Option[Double],
                                                    overallScore: Option[Double],
                                                    schemesEvaluation: Option[String]) {

  def competencyAverage: Option[CompetencyAverageResult] = {
    val allResults = List(leadingAndCommunicatingAverage, collaboratingAndPartneringAverage,
      deliveringAtPaceAverage, makingEffectiveDecisionsAverage, changingAndImprovingAverage,
      buildingCapabilityForAllAverage, motivationFitAverage)

    require(allResults.forall(_.isDefined) || allResults.forall(_.isEmpty), "all competecy or none of them must be defined")

    if (allResults.forall(_.isDefined)) {
      Some(CompetencyAverageResult(
        leadingAndCommunicatingAverage.get,
        collaboratingAndPartneringAverage.get,
        deliveringAtPaceAverage.get,
        makingEffectiveDecisionsAverage.get,
        changingAndImprovingAverage.get,
        buildingCapabilityForAllAverage.get,
        motivationFitAverage.get,
        overallScore.get))
    } else {
      None
    }
  }

  def allSchemesEvaluationExpectatons = schemesEvaluation.map { s =>
    s.split("\\|").map { schemeAndResult =>
      val Array(scheme, result) = schemeAndResult.split(":")
      PerSchemeEvaluation(scheme, Result(result))
    }.toList
  }
}
