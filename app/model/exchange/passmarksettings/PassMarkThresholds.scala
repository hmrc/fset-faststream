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

package model.exchange.passmarksettings

import play.api.libs.json.{Json, OFormat}

trait PassMarkThresholds

trait Phase1Thresholds {
  def test1: PassMarkThreshold
  def test2: PassMarkThreshold
  def test3: PassMarkThreshold
}

trait Phase2Thresholds {
  def test1: PassMarkThreshold
  def test2: PassMarkThreshold
}

trait Phase3Thresholds {
  def videoInterview: PassMarkThreshold
}

trait AssessmentCentreCompetencyThresholds {
  def seeingTheBigPicture: PassMarkThreshold
  def makingEffectiveDecisions: PassMarkThreshold
  def communicatingAndInfluencing: PassMarkThreshold
  def workingTogetherDevelopingSelfAndOthers: PassMarkThreshold
  def overall: PassMarkThreshold
}

trait AssessmentCentreExerciseThresholds {
  def writtenExercise: PassMarkThreshold
  def teamExercise: PassMarkThreshold
  def leadershipExercise: PassMarkThreshold
  def overall: PassMarkThreshold
}

case class Phase1PassMarkThresholds(
                                     test1: PassMarkThreshold,
                                     test2: PassMarkThreshold,
                                     test3: PassMarkThreshold
) extends PassMarkThresholds with Phase1Thresholds

object Phase1PassMarkThresholds {
  implicit val phase1PassMarkThresholds: OFormat[Phase1PassMarkThresholds] = Json.format[Phase1PassMarkThresholds]
}

case class Phase2PassMarkThresholds(
                                     test1: PassMarkThreshold,
                                     test2: PassMarkThreshold
) extends PassMarkThresholds with Phase2Thresholds

object Phase2PassMarkThresholds {
  implicit val phase2PassMarkThresholds: OFormat[Phase2PassMarkThresholds] = Json.format[Phase2PassMarkThresholds]
}

case class Phase3PassMarkThresholds(
  videoInterview: PassMarkThreshold
) extends PassMarkThresholds with Phase3Thresholds

object Phase3PassMarkThresholds {
  implicit val phase3PassMarkThresholds: OFormat[Phase3PassMarkThresholds] = Json.format[Phase3PassMarkThresholds]
}

// These are competency pass marks. The 1st version of the FSAC evaluation used these pass marks
case class AssessmentCentreCompetencyPassMarkThresholds(
                                    override val seeingTheBigPicture: PassMarkThreshold,
                                    override val makingEffectiveDecisions: PassMarkThreshold,
                                    override val communicatingAndInfluencing: PassMarkThreshold,
                                    override val workingTogetherDevelopingSelfAndOthers: PassMarkThreshold,
                                    override val overall: PassMarkThreshold
                                   ) extends PassMarkThresholds with AssessmentCentreCompetencyThresholds {
  override def toString =
    s"seeingTheBigPicture=$seeingTheBigPicture," +
    s"makingEffectiveDecisions=$makingEffectiveDecisions," +
    s"communicatingAndInfluencing=$communicatingAndInfluencing," +
    s"workingTogetherDevelopingSelfAndOthers=$workingTogetherDevelopingSelfAndOthers," +
    s"overall=$overall"
}

object AssessmentCentreCompetencyPassMarkThresholds {
  implicit val jsonFormat: OFormat[AssessmentCentreCompetencyPassMarkThresholds] = Json.format[AssessmentCentreCompetencyPassMarkThresholds]
}

// These are exercise pass marks. The latest version of the FSAC evaluation uses these pass marks
case class AssessmentCentreExercisePassMarkThresholds(
                                    override val writtenExercise: PassMarkThreshold,
                                    override val teamExercise: PassMarkThreshold,
                                    override val leadershipExercise: PassMarkThreshold,
                                    override val overall: PassMarkThreshold
                                   ) extends PassMarkThresholds with AssessmentCentreExerciseThresholds {
  override def toString =
    s"writtenExercise=$writtenExercise," +
    s"teamExercise=$teamExercise," +
    s"leadershipExercise=$leadershipExercise," +
    s"overall=$overall"
}

object AssessmentCentreExercisePassMarkThresholds {
  implicit val jsonFormat: OFormat[AssessmentCentreExercisePassMarkThresholds] = Json.format[AssessmentCentreExercisePassMarkThresholds]
}


