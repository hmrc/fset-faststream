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

package model

import controllers._
import model.ApplicationRoute.ApplicationRoute
import model.Exceptions.{ NoResultsReturned, TooManyEntries }
import model.OnlineTestCommands.Implicits._
import model.OnlineTestCommands.TestResult
import model.assessmentscores.AssessmentScoresAllExercises
import org.joda.time.{ DateTime, LocalDate, LocalTime }
import play.api.libs.json._

import scala.language.implicitConversions
import model.command.ProgressResponse
import model.persisted.{ QuestionnaireAnswer, QuestionnaireQuestion }
import model.report.QuestionnaireReportItem

//scalastyle:off
object Commands {

  type PostCode = String
  type PhoneNumber = String
  type IsNonSubmitted = Boolean

}
