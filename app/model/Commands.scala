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

package model

import controllers._
import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.CandidateScoresCommands.Implicits._
import model.Exceptions.{NoResultsReturned, TooManyEntries}
import model.OnlineTestCommands.Implicits._
import model.OnlineTestCommands.TestResult
import model.PassmarkPersistedObjects.{AssessmentCentrePassMarkInfo, AssessmentCentrePassMarkScheme}
import model.PassmarkPersistedObjects.Implicits._
import model.PersistedObjects.{PersistedAnswer, PersistedQuestion}
import model.SchemeType.SchemeType
import org.joda.time.{DateTime, LocalDate, LocalTime}
import play.api.libs.json._

import scala.language.implicitConversions
import model.command.{AssessmentCentre, ProgressResponse}
import model.exchange.passmarksettings.Phase1PassMarkSettings
import model.report.{CandidateProgressReport, QuestionnaireReportItem, QuestionnaireReportItem$}

//scalastyle:off
object Commands {

  case class CreateApplicationRequest(userId: String, frameworkId: String)

  case class ApplicationCreated(applicationId: String, applicationStatus: String, userId: String)

  case class PersonalDetailsAdded(applicationId: String, userId: String)

  type PostCode = String
  type PhoneNumber = String

  case class Report(applicationId: String, progress: Option[String], firstLocation: Option[String],
                    firstLocationFirstScheme: Option[String], firstLocationSecondScheme: Option[String], secondLocation: Option[String],
                    secondLocationFirstScheme: Option[String], secondLocationSecondScheme: Option[String], alevels: Option[String],
                    stemlevels: Option[String], alternativeLocation: Option[String], alternativeScheme: Option[String], hasDisability: Option[String],
                    hasAdjustments: Option[String], guaranteedInterview: Option[String], issue: Option[String])

  case class ReportWithPersonalDetails(applicationId: String, userId: String, progress: Option[String], firstLocation: Option[String],
                                       firstLocationFirstScheme: Option[String], firstLocationSecondScheme: Option[String], secondLocation: Option[String],
                                       secondLocationFirstScheme: Option[String], secondLocationSecondScheme: Option[String], alevels: Option[String],
                                       stemlevels: Option[String], alternativeLocation: Option[String], alternativeScheme: Option[String], hasDisability: Option[String],
                                       hasAdjustments: Option[String], guaranteedInterview: Option[String], firstName: Option[String], lastName: Option[String],
                                       preferredName: Option[String], dateOfBirth: Option[String], cubiksUserId: Option[Int])

  case class CandidateAwaitingAllocation(
                                          userId: String,
                                          firstName: String,
                                          lastName: String,
                                          preferredName: String,
                                          preferredLocation1: String,
                                          adjustments: Option[String],
                                          dateOfBirth: LocalDate
                                        )

  case class AssessmentCentreAllocationReport(
                                               firstName: String,
                                               lastName: String,
                                               preferredName: String,
                                               emailAddress: String,
                                               phoneNumber: String,
                                               preferredLocation1: String,
                                               adjustments: Option[String],
                                               dateOfBirth: LocalDate
                                             )

  case class PhoneAndEmail(phone: Option[String], email: Option[String])

  type IsNonSubmitted = Boolean

  case class PreferencesWithContactDetails(firstName: Option[String], lastName: Option[String], preferredName: Option[String],
                                           email: Option[String], telephone: Option[String], location1: Option[String], location1Scheme1: Option[String],
                                           location1Scheme2: Option[String], location2: Option[String], location2Scheme1: Option[String],
                                           location2Scheme2: Option[String], progress: Option[String], timeApplicationCreated: Option[String])

  case class OnlineTestPassmarkEvaluationSchemes(
                                                  location1Scheme1: Option[String] = None,
                                                  location1Scheme2: Option[String] = None,
                                                  location2Scheme1: Option[String] = None,
                                                  location2Scheme2: Option[String] = None,
                                                  alternativeScheme: Option[String] = None
                                                )

  case class ApplicationPreferences(userId: String, applicationId: String, location1: Option[String],
                                    location1Scheme1: Option[String], location1Scheme2: Option[String],
                                    location2: Option[String], location2Scheme1: Option[String],
                                    location2Scheme2: Option[String], alternativeLocation: Option[String],
                                    alternativeScheme: Option[String],
                                    needsAssistance: Option[String],
                                    guaranteedInterview: Option[String],
                                    needsAdjustment: Option[String],
                                    aLevel: Option[String],
                                    stemLevel: Option[String],
                                    onlineTestPassmarkEvaluations: OnlineTestPassmarkEvaluationSchemes)

  case class PersonalInfo(firstName: Option[String], lastName: Option[String], preferredName: Option[String],
                          aLevel: Option[String], stemLevel: Option[String])

  case class CandidateScoresSummary(
                                     avgLeadingAndCommunicating: Option[Double],
                                     avgCollaboratingAndPartnering: Option[Double],
                                     avgDeliveringAtPace: Option[Double],
                                     avgMakingEffectiveDecisions: Option[Double],
                                     avgChangingAndImproving: Option[Double],
                                     avgBuildingCapabilityForAll: Option[Double],
                                     avgMotivationFit: Option[Double],
                                     totalScore: Option[Double]
                                   )

  case class SchemeEvaluation(
                               commercial: Option[String] = None,
                               digitalAndTechnology: Option[String] = None,
                               business: Option[String] = None,
                               projectDelivery: Option[String] = None,
                               finance: Option[String] = None
                             )

  case class ApplicationPreferencesWithTestResults(userId: String, applicationId: String, location1: Option[String],
                                                   location1Scheme1: Option[String], location1Scheme2: Option[String],
                                                   location2: Option[String], location2Scheme1: Option[String],
                                                   location2Scheme2: Option[String], alternativeLocation: Option[String],
                                                   alternativeScheme: Option[String],
                                                   personalDetails: PersonalInfo,
                                                   scores: CandidateScoresSummary,
                                                   passmarks: SchemeEvaluation)

  case class AssessmentResultsReport(
                                      appPreferences: ApplicationPreferences,
                                      questionnaire: QuestionnaireReportItem,
                                      candidateScores: CandidateScoresAndFeedback
                                    )

  case class AssessmentCentreCandidatesReport(
                                               application: ApplicationPreferencesWithTestResults,
                                               phoneAndEmail: PhoneAndEmail
                                             )

  case class ApplicationResponse(applicationId: String, applicationStatus: String, userId: String, progressResponse: ProgressResponse,
                                 civilServiceExperienceDetails: Option[CivilServiceExperienceDetails])

  case class PassMarkSettingsCreateResponse(passMarkSettingsVersion: String, passMarkSettingsCreateDate: DateTime)

  //  questionnaire
  case class Answer(answer: Option[String], otherDetails: Option[String], unknown: Option[Boolean])

  case class Question(question: String, answer: Answer)

  case class Questionnaire(questions: List[Question])

  case class PreviewRequest(flag: Boolean)

  case class AdjustmentManagement(adjustments: Option[List[String]], otherAdjustments: Option[String],
                                  timeNeeded: Option[Int], timeNeededNum: Option[Int])

  case class SearchCandidate(firstOrPreferredName: Option[String], lastName: Option[String], dateOfBirth: Option[LocalDate], postCode: Option[PostCode])

  case class Candidate(userId: String, applicationId: Option[String], email: Option[String], firstName: Option[String], lastName: Option[String],
                       preferredName: Option[String], dateOfBirth: Option[LocalDate], address: Option[Address], postCode: Option[PostCode],
                       country: Option[String])

  case class ApplicationAssessment(applicationId: String, venue: String, date: LocalDate, session: String, slot: Int, confirmed: Boolean) {
    val assessmentDateTime = {
      // TODO This should be configurable in the future, but hardcoding it in the fasttrack service is the lesser of the evils at the moment
      // FSET-471 was an emergency last minute fix
      if (venue == "Manchester" || venue == "London (Berkeley House)") {
        if (session == "AM") {
          date.toLocalDateTime(new LocalTime(9, 0)).toDateTime
        } else {
          date.toLocalDateTime(new LocalTime(13, 0)).toDateTime
        }
      } else {
        if (session == "AM") {
          date.toLocalDateTime(new LocalTime(8, 30)).toDateTime
        } else {
          date.toLocalDateTime(new LocalTime(12, 30)).toDateTime
        }
      }
    }

    // If a candidate is allocated at DD/MM/YYYY, the deadline for the candidate to confirm is 10 days.
    // Because we don't store the time it means we need to set DD-11/MM/YYY, and remember that there is
    // an implicit time 23:59:59 after which the allocation expires.
    // After DD-11/MM/YYYY the allocation is expired.
    // For Example:
    // - The candidate is scheduled on 25/05/2016.
    // - It means the deadline is 14/05/2016 23:59:59
    def expireDate: LocalDate = date.minusDays(11)
  }

  case class AssessmentCentrePassMarkSettingsResponse(
                                                       schemes: List[AssessmentCentrePassMarkScheme],
                                                       info: Option[AssessmentCentrePassMarkInfo]
                                                     )

  object Implicits {
    implicit val addressFormat = Json.format[Address]
    implicit val applicationAddedFormat = Json.format[ApplicationResponse]
    implicit val passMarkSettingsCreateResponseFormat = Json.format[PassMarkSettingsCreateResponse]
    implicit val personalDetailsAddedFormat = Json.format[PersonalDetailsAdded]
    implicit val createApplicationRequestFormats: Format[CreateApplicationRequest] = Json.format[CreateApplicationRequest]

    implicit val answerFormats = Json.format[Answer]
    implicit val questionFormats = Json.format[Question]
    implicit val questionnaireFormats = Json.format[Questionnaire]
    implicit val previewFormats = Json.format[PreviewRequest]

    implicit val tooManyEntriesFormat = Json.format[TooManyEntries]
    implicit val noResultsReturnedFormat = Json.format[NoResultsReturned]

    implicit val searchCandidateFormat = Json.format[SearchCandidate]
    implicit val candidateFormat = Json.format[Candidate]
    implicit val reportFormat = Json.format[Report]
    implicit val preferencesWithContactDetailsFormat = Json.format[PreferencesWithContactDetails]
    implicit val adjustmentManagementFormat = Json.format[AdjustmentManagement]

    implicit def fromCommandToPersistedQuestion(q: Question): PersistedQuestion =
      PersistedQuestion(q.question, PersistedAnswer(q.answer.answer, q.answer.otherDetails, q.answer.unknown))

    implicit val onlineTestDetailsFormat = Json.format[OnlineTestDetails]
    implicit val onlineTestFormat = Json.format[OnlineTest]
    implicit val onlineTestStatusFormats = Json.format[OnlineTestStatus]
    implicit val userIdWrapperFormats = Json.format[UserIdWrapper]

    implicit val assessmentCentreAllocationReportFormat = Json.format[AssessmentCentreAllocationReport]
    implicit val candidateAwaitingAllocationFormat = Json.format[CandidateAwaitingAllocation]

    implicit val applicationAssessmentFormat = Json.format[ApplicationAssessment]
    implicit val phoneAndEmailFormat = Json.format[PhoneAndEmail]
    implicit val reportWithPersonalDetailsFormat = Json.format[ReportWithPersonalDetails]
    implicit val assessmentCentrePassMarkSettingsResponseFormat = Json.format[AssessmentCentrePassMarkSettingsResponse]
    implicit val passMarkEvaluationSchemes = Json.format[OnlineTestPassmarkEvaluationSchemes]
    implicit val applicationPreferencesFormat = Json.format[ApplicationPreferences]
    implicit val assessmentResultsReportFormat = Json.format[AssessmentResultsReport]
    implicit val personalInfoFormat = Json.format[PersonalInfo]
    implicit val schemeEvaluation = Json.format[SchemeEvaluation]
    implicit val candidateScoresSummaryFormat = Json.format[CandidateScoresSummary]
    implicit val applicationPreferencesWithTestResults = Json.format[ApplicationPreferencesWithTestResults]
    implicit val assessmentCentreCandidatesReportFormat = Json.format[AssessmentCentreCandidatesReport]
  }

}
