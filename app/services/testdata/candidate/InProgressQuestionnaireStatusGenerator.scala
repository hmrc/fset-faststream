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

package services.testdata.candidate

import javax.inject.{Inject, Singleton}
import model.exchange.testdata.CreateCandidateResponse
import model.persisted.{QuestionnaireAnswer, QuestionnaireQuestion}
import model.testdata.candidate.CreateCandidateData.{CreateCandidateData, DiversityDetails}
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.{DiversityQuestionsText, GeneralApplicationRepository}
import services.testdata.faker.DataFaker
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InProgressQuestionnaireStatusGenerator @Inject() (val previousStatusGenerator: InProgressAssistanceDetailsStatusGenerator,
                                                        appRepository: GeneralApplicationRepository,
                                                        qRepository: QuestionnaireRepository,
                                                        dataFaker: DataFaker
                                                       )(
  implicit ec: ExecutionContext) extends ConstructiveGenerator with DiversityQuestionsText {

  // scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[CreateCandidateResponse.CreateCandidateResponse] = {

    val didYouLiveInUkBetween14and18Answer = dataFaker.yesNo

    def getWhatWasYourHomePostCodeWhenYouWere14 = {
      if (didYouLiveInUkBetween14and18Answer == "Yes") {
        Some(QuestionnaireQuestion(postcodeAtAge14,
          QuestionnaireAnswer(Some(dataFaker.homePostcode), otherDetails = None, unknown = None)))
      } else {
        None
      }
    }

    def getSchoolName14to16Answer = {
      if (didYouLiveInUkBetween14and18Answer == "Yes") {
        Some(QuestionnaireQuestion(schoolNameAged14to16,
          QuestionnaireAnswer(Some(dataFaker.age14to16School), otherDetails = None, unknown = None)))
      } else {
        None
      }
    }

    def getSchoolType14to16Answer = if (didYouLiveInUkBetween14and18Answer == "Yes") {
      Some(QuestionnaireQuestion(schoolTypeAged14to16,
        QuestionnaireAnswer(Some(dataFaker.schoolType14to16), otherDetails = None, unknown = None))
      )
    } else { None }

    def getSchoolName16to18Answer = {
      if (didYouLiveInUkBetween14and18Answer == "Yes") {
        Some(QuestionnaireQuestion(schoolNameAged16to18,
          QuestionnaireAnswer(Some(dataFaker.age16to18School), otherDetails = None, unknown = None)))
      } else {
        None
      }
    }

    def getFreeSchoolMealsAnswer = {
      if (didYouLiveInUkBetween14and18Answer == "Yes") {
        Some(QuestionnaireQuestion(eligibleForFreeSchoolMeals,
          QuestionnaireAnswer(Some(dataFaker.yesNoPreferNotToSay), otherDetails = None, unknown = None)))
      } else {
        None
      }
    }

    def getHaveDegreeAnswer = {
      if (generatorConfig.isCivilServant) {
        Some(QuestionnaireQuestion(doYouHaveADegree,
          QuestionnaireAnswer(Some(if (generatorConfig.hasDegree) { "Yes" } else { "No" }), otherDetails = None, unknown = None))
        )
      } else { None }
    }

    def getUniversityAnswer = {
      if (generatorConfig.hasDegree) {
        Some(QuestionnaireQuestion(universityName,
          QuestionnaireAnswer(Some(generatorConfig.diversityDetails.universityAttended), otherDetails = None, unknown = None)))
      } else {
        None
      }
    }

    def getUniversityDegreeCategoryAnswer = {
      if (generatorConfig.hasDegree) {
        Some(QuestionnaireQuestion(categoryOfDegree,
          QuestionnaireAnswer(Some(dataFaker.degreeCategory._2), otherDetails = None, unknown = None)))
      } else {
        None
      }
    }

    def getUniversityDegreeTypeAnswer = {
      if (generatorConfig.hasDegree) {
        Some(QuestionnaireQuestion(degreeType,
          QuestionnaireAnswer(answer = Some(dataFaker.degreeType), otherDetails = None, unknown = None)))
      } else {
        None
      }
    }

    def getHasPostgradDegreeAnswer = {
      if (generatorConfig.hasDegree) {
        // Always set to "No" in TDG
        Some(QuestionnaireQuestion(postgradDoYouHaveADegree, QuestionnaireAnswer(Some("No"), otherDetails = None, unknown = None)))
      } else {
        None
      }
    }

    def getEmployedOrSelfEmployed(parentsOccupation: String) = {
      if (parentsOccupation == "Employed") {
        Some(QuestionnaireQuestion(employeeOrSelfEmployed,
          QuestionnaireAnswer(Some(dataFaker.employeeOrSelf), otherDetails = None, unknown = None)))
      } else {
        None
      }
    }

    def getSizeParentsEmployer(occupation: Option[String]) = occupation.flatMap { occ =>
      if (occ == "Employed") {
        Some(QuestionnaireQuestion(sizeOfPlaceOfWork,
          QuestionnaireAnswer(Some(dataFaker.sizeOfPlaceOfWork), otherDetails = None, unknown = None)))
      } else { None }
    }

    def getSuperviseEmployees(parentsOccupation: Option[String]) = parentsOccupation.flatMap { occ =>
      if (occ == "Employed") {
        Some(QuestionnaireQuestion(superviseEmployees,
          QuestionnaireAnswer(Some(dataFaker.yesNoPreferNotToSay), otherDetails = None, unknown = None)))
      } else { None }
    }

    def getAllQuestionnaireQuestions(dd: DiversityDetails) = List(
      Some(QuestionnaireQuestion("I understand this won't affect my application",
        QuestionnaireAnswer(Some(dataFaker.yesNo), None, unknown = None))),
      Some(QuestionnaireQuestion(sex, QuestionnaireAnswer(Some(dd.sex), otherDetails = None, unknown = None))),
      Some(QuestionnaireQuestion(sexualOrientation, QuestionnaireAnswer(Some(dd.sexualOrientation), otherDetails = None, unknown = None))),
      Some(QuestionnaireQuestion(ethnicGroup, QuestionnaireAnswer(Some(dd.ethnicity), otherDetails = None, unknown = None))),
      Some(QuestionnaireQuestion(liveInUkAged14to18, QuestionnaireAnswer(
        Some(didYouLiveInUkBetween14and18Answer), otherDetails = None, unknown = None))
      ),
      getWhatWasYourHomePostCodeWhenYouWere14,
      getSchoolName14to16Answer,
      getSchoolType14to16Answer,
      getSchoolName16to18Answer,
      getFreeSchoolMealsAnswer,
      getHaveDegreeAnswer,
      getUniversityAnswer,
      getUniversityDegreeCategoryAnswer,
      getUniversityDegreeTypeAnswer,
      getHasPostgradDegreeAnswer,
      Some(QuestionnaireQuestion(lowerSocioEconomicBackground,
        QuestionnaireAnswer(Some(dataFaker.yesNoPreferNotToSay), otherDetails = None, unknown = None))
      ),
      Some(QuestionnaireQuestion(parentOrGuardianQualificationsAtAge18,
        QuestionnaireAnswer(Some(dataFaker.parentsDegree), otherDetails = None, unknown = None))
      ),
      Some(QuestionnaireQuestion(highestEarningParentOrGuardianTypeOfWorkAtAge14,
        QuestionnaireAnswer(dd.parentalEmployment, otherDetails = None, unknown = None))),
      getEmployedOrSelfEmployed(dd.parentalEmployedOrSelfEmployed),
      getSizeParentsEmployer(dd.parentalEmployment),
      getSuperviseEmployees(dd.parentalEmployment)
    ).filter(_.isDefined).map { someItem => someItem.get }

    val questions = getAllQuestionnaireQuestions(generatorConfig.diversityDetails)

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- qRepository.addQuestions(candidateInPreviousStatus.applicationId.get, questions)
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "start_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "education_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "diversity_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "occupation_questionnaire")
    } yield {
      candidateInPreviousStatus.copy(diversityDetails = Some(questions))
    }
  }
  // scalastyle:on method.length
}
