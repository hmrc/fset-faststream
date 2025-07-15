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
}

trait Phase2Thresholds {
  def test1: PassMarkThreshold
  def test2: PassMarkThreshold
}

trait Phase3Thresholds {
  def videoInterview: PassMarkThreshold
}

// TODO: we can delete this class
trait AssessmentCentreCompetencyThresholds {
  def relates: PassMarkThreshold
  def thinks: PassMarkThreshold
  def strives: PassMarkThreshold
  def adapts: PassMarkThreshold
  def overall: PassMarkThreshold
}

trait AssessmentCentreExerciseThresholds {
  def exercise1: PassMarkThreshold
  def exercise2: PassMarkThreshold
  def exercise3: PassMarkThreshold
  def overall: PassMarkThreshold
}

case class Phase1PassMarkThresholds(
                                     test1: PassMarkThreshold,
                                     test2: PassMarkThreshold
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

// TODO: we can delete this class
// These are competency pass marks. The 1st version of the FSAC evaluation used these pass marks
case class AssessmentCentreCompetencyPassMarkThresholds(
                                                         override val relates: PassMarkThreshold,
                                                         override val thinks: PassMarkThreshold,
                                                         override val strives: PassMarkThreshold,
                                                         override val adapts: PassMarkThreshold,
                                                         override val overall: PassMarkThreshold
                                   ) extends PassMarkThresholds with AssessmentCentreCompetencyThresholds {
  override def toString =
    s"relates=$relates," +
    s"thinks=$thinks," +
    s"strives=$strives," +
    s"adapts=$adapts," +
    s"overall=$overall"
}

object AssessmentCentreCompetencyPassMarkThresholds {
  implicit val jsonFormat: OFormat[AssessmentCentreCompetencyPassMarkThresholds] = Json.format[AssessmentCentreCompetencyPassMarkThresholds]
}

// These are exercise pass marks. The latest version of the FSAC evaluation uses these pass marks
case class AssessmentCentreExercisePassMarkThresholds(
                                                       override val exercise1: PassMarkThreshold,
                                                       override val exercise2: PassMarkThreshold,
                                                       override val exercise3: PassMarkThreshold,
                                                       override val overall: PassMarkThreshold
                                   ) extends PassMarkThresholds with AssessmentCentreExerciseThresholds {
  override def toString =
    s"exercise1=$exercise1," +
    s"exercise2=$exercise2," +
    s"exercise3=$exercise3," +
    s"overall=$overall"
}

object AssessmentCentreExercisePassMarkThresholds {
  implicit val jsonFormat: OFormat[AssessmentCentreExercisePassMarkThresholds] = Json.format[AssessmentCentreExercisePassMarkThresholds]
}
