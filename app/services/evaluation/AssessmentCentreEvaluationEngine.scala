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

package services.evaluation

import com.google.inject.ImplementedBy

import javax.inject.Singleton
import model.AssessmentPassMarksSchemesAndScores
import model.EvaluationResults._

@ImplementedBy(classOf[AssessmentCentreEvaluationEngineImpl])
trait AssessmentCentreEvaluationEngine {
  def evaluate(candidateScores: AssessmentPassMarksSchemesAndScores): AssessmentEvaluationResult
}

@Singleton
class AssessmentCentreEvaluationEngineImpl extends AssessmentCentreEvaluationEngine with AssessmentScoreCalculator
  with AssessmentCentreAllSchemesEvaluator {

  def evaluate(candidateScores: AssessmentPassMarksSchemesAndScores): AssessmentEvaluationResult = {
    val appId = candidateScores.scores.applicationId

    val exerciseAverages = fetchExerciseAverages(candidateScores.scores, appId.toString)
    logger.debug(s"ExerciseAverages = $exerciseAverages - applicationId = ${appId.toString}")

    val evaluationResult = evaluateSchemes(appId.toString(), candidateScores.passmark, exerciseAverages, candidateScores.schemes)
    AssessmentEvaluationResult(FsacResults(exerciseAverages), evaluationResult)
  }
}
