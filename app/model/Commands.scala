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

  object Implicits {
    implicit val addressFormat: OFormat[Address] = Json.format[Address]

    implicit val personalDetailsAddedFormat: OFormat[PersonalDetailsAdded] = Json.format[PersonalDetailsAdded]

    implicit val tooManyEntriesFormat: OFormat[TooManyEntries] = Json.format[TooManyEntries]
    implicit val noResultsReturnedFormat: OFormat[NoResultsReturned] = Json.format[NoResultsReturned]


    implicit val candidateFormat: OFormat[Candidate] = Json.format[Candidate]
    implicit val preferencesWithContactDetailsFormat: OFormat[PreferencesWithContactDetails] = Json.format[PreferencesWithContactDetails]



    implicit val onlineTestDetailsFormat: OFormat[OnlineTestDetails] = Json.format[OnlineTestDetails]
    implicit val onlineTestFormat: OFormat[OnlineTest] = Json.format[OnlineTest]
    implicit val onlineTestStatusFormat: OFormat[OnlineTestStatus] = Json.format[OnlineTestStatus]
    implicit val userIdWrapperFormat: OFormat[UserIdWrapper] = Json.format[UserIdWrapper]

    implicit val phoneAndEmailFormat: OFormat[PhoneAndEmail] = Json.format[PhoneAndEmail]
    implicit val reportWithPersonalDetailsFormat: OFormat[ReportWithPersonalDetails] = Json.format[ReportWithPersonalDetails]
  }
}
