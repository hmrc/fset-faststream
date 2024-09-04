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

package model.assessmentscores

import model.UniqueIdentifier
import play.api.libs.json.{Json, OFormat}

import java.time.OffsetDateTime

case class AssessmentScoresExercise(
                                     attended: Boolean,

                                     // was seeingTheBigPictureScores
                                     relatesScores: Option[RelatesScores] = None,
                                     relatesFeedback: Option[String] = None,

                                     // was makingEffectiveDecisions
                                     thinksScores: Option[ThinksScores] = None,
                                     thinksFeedback: Option[String] = None,

                                     // was communicatingAndInfluencing
                                     strivesScores: Option[StrivesScores] = None,
                                     strivesFeedback: Option[String] = None,

                                     // was workingTogetherDevelopingSelfAndOthers
                                     adaptsScores: Option[AdaptsScores] = None,
                                     adaptsFeedback: Option[String] = None,

                                     updatedBy: UniqueIdentifier,
                                     savedDate: Option[OffsetDateTime] = None,
                                     submittedDate: Option[OffsetDateTime] = None,
                                     overallAverage: Option[Double] = None,
                                     version: Option[String] = None
) extends AssessmentScoresSection {
  def isSubmitted = submittedDate.isDefined

  override def toString: String = {
    s"attended:$attended," +
    s"relatesScores:$relatesScores," +
    s"relatesFeedback:$relatesFeedback," +
    s"thinksScores:$thinksScores," +
    s"thinksFeedback:$thinksFeedback," +
    s"strivesScores:$strivesScores," +
    s"strivesFeedback:$strivesFeedback," +
    s"adaptsScores:$adaptsScores," +
    s"adaptsFeedback:$adaptsFeedback," +
    s"updatedBy:$updatedBy," +
    s"savedDate:$savedDate," +
    s"submittedDate:$submittedDate," +
    s"overallAverage:$overallAverage," +
    s"version:$version"
  }

  def toExchange =
    AssessmentScoresExerciseExchange(
      attended,
      relatesScores,
      relatesFeedback,
      thinksScores,
      thinksFeedback,
      strivesScores,
      strivesFeedback,
      adaptsScores,
      adaptsFeedback,
      updatedBy,
      savedDate,
      submittedDate,
      overallAverage,
      version
    )
}

object AssessmentScoresExercise {
  import repositories.formats.MongoJavatimeFormats.Implicits._ // Needed to handle storing ISODate format in Mongo
  implicit val jsonFormat: OFormat[AssessmentScoresExercise] = Json.format[AssessmentScoresExercise]
}

//scalastyle:off line.size.limit
case class AssessmentScoresExerciseExchange(
                                             attended: Boolean,

                                             // was seeingTheBigPicture
                                             relatesScores: Option[RelatesScores] = None,
                                             relatesFeedback: Option[String] = None,

                                             // was makingEffectiveDecisions
                                             thinksScores: Option[ThinksScores] = None,
                                             thinksFeedback: Option[String] = None,

                                             // was communicatingAndInfluencing
                                             strivesScores: Option[StrivesScores] = None,
                                             strivesFeedback: Option[String] = None,

                                             // was workingTogetherDevelopingSelfAndOthers
                                             adaptsScores: Option[AdaptsScores] =None,
                                             adaptsFeedback: Option[String] = None,

                                             updatedBy: UniqueIdentifier,
                                             savedDate: Option[OffsetDateTime] = None,
                                             submittedDate: Option[OffsetDateTime] = None,
                                             overallAverage: Option[Double] = None,
                                             version: Option[String] = None
                                   ) extends AssessmentScoresSection {
  //scalastyle:on
  def toPersistence =
    AssessmentScoresExercise(
      attended,
      relatesScores,
      relatesFeedback,
      thinksScores,
      thinksFeedback,
      strivesScores,
      strivesFeedback,
      adaptsScores,
      adaptsFeedback,
      updatedBy,
      savedDate,
      submittedDate,
      overallAverage,
      version
    )

  override def toString: String = s"attended=$attended," +
    s"relatesScores=$relatesScores," +
    s"relatesFeedback=$relatesFeedback," +
    s"thinksScores=$thinksScores," +
    s"thinksFeedback=$thinksFeedback," +
    s"strivesScores=$strivesScores," +
    s"strivesFeedback=$strivesFeedback," +
    s"adaptsScores=$adaptsScores," +
    s"adaptsFeedback=$adaptsFeedback," +
    s"updatedBy=$updatedBy," +
    s"savedDate=$savedDate," +
    s"submittedDate=$submittedDate," +
    s"overallAverage=$overallAverage," +
    s"version=$version"
}

object AssessmentScoresExerciseExchange {
  implicit val jsonFormat: OFormat[AssessmentScoresExerciseExchange] = Json.format[AssessmentScoresExerciseExchange]
}
