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

package services.testdata

import javax.inject.{Inject, Singleton}
import model.persisted.{QuestionnaireAnswer, QuestionnaireQuestion}
import model.testdata.candidate.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.{DiversityQuestionsText, GeneralApplicationRepository}
import services.testdata.candidate.{ConstructiveGenerator, InProgressAssistanceDetailsStatusGenerator}
import services.testdata.faker.DataFaker
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

//object InProgressQuestionnaireStatusGenerator extends InProgressQuestionnaireStatusGenerator {
//  override val previousStatusGenerator = InProgressAssistanceDetailsStatusGenerator
//  override val appRepository = applicationRepository
//  override val qRepository = questionnaireRepository
//}

@Singleton
class InProgressQuestionnaireStatusGenerator @Inject() (val previousStatusGenerator: InProgressAssistanceDetailsStatusGenerator,
                                                        appRepository: GeneralApplicationRepository,
                                                        qRepository: QuestionnaireRepository,
                                                        dataFaker: DataFaker
                                                       )(implicit ec : ExecutionContext) extends ConstructiveGenerator
  with DiversityQuestionsText {
//  val appRepository: GeneralApplicationRepository
//  val qRepository: QuestionnaireRepository

  private val didYouLiveInUkBetween14and18Answer = dataFaker.yesNo

  private def getWhatWasYourHomePostCodeWhenYouWere14 = {
    if (didYouLiveInUkBetween14and18Answer == "Yes") {
      Some(QuestionnaireQuestion(postcodeAtAge14,
        QuestionnaireAnswer(answer = Some(dataFaker.homePostcode), otherDetails = None, unknown = None)))
    } else {
      None
    }
  }

  private def getSchoolName14to16Answer = {
    if (didYouLiveInUkBetween14and18Answer == "Yes") {
      Some(QuestionnaireQuestion(schoolNameAged14to16,
        QuestionnaireAnswer(answer = Some(dataFaker.age14to16School), otherDetails = None, unknown = None)))
    } else {
      None
    }
  }

  private def getSchoolName16to18Answer = {
    if (didYouLiveInUkBetween14and18Answer == "Yes") {
      Some(QuestionnaireQuestion(schoolNameAged16to18,
        QuestionnaireAnswer(answer = Some(dataFaker.age16to18School), otherDetails = None, unknown = None)))
    } else {
      None
    }
  }

  private def getFreeSchoolMealsAnswer = {
    if (didYouLiveInUkBetween14and18Answer == "Yes") {
      Some(QuestionnaireQuestion(eligibleForFreeSchoolMeals,
        QuestionnaireAnswer(answer = Some(dataFaker.yesNoPreferNotToSay), otherDetails = None, unknown = None)))
    } else {
      None
    }
  }

  private def getHaveDegreeAnswer(generatorConfig: CreateCandidateData.CreateCandidateData) = {
    if (generatorConfig.isCivilServant) {
      Some(QuestionnaireQuestion(doYouHaveADegree,
        QuestionnaireAnswer(answer = Some(if (generatorConfig.hasDegree) { "Yes" } else { "No" }), otherDetails = None, unknown = None))
      )
    } else { None }
  }

  private def getUniversityAnswer(generatorConfig: CreateCandidateData.CreateCandidateData) = {
    if (generatorConfig.hasDegree) {
      Some(QuestionnaireQuestion(universityName,
        QuestionnaireAnswer(answer = Some(dataFaker.university._2), otherDetails = None, unknown = None)))
    } else {
      None
    }
  }

  private def getUniversityDegreeCategoryAnswer(generatorConfig: CreateCandidateData.CreateCandidateData) = {
    if (generatorConfig.hasDegree) {
      Some(QuestionnaireQuestion(categoryOfDegree,
        QuestionnaireAnswer(answer = Some(dataFaker.degreeCategory._2), otherDetails = None, unknown = None)))
    } else {
      None
    }
  }

  private def getUniversityDegreeTypeAnswer(generatorConfig: CreateCandidateData.CreateCandidateData) = {
    if (generatorConfig.hasDegree) {
      Some(QuestionnaireQuestion(degreeType,
        QuestionnaireAnswer(answer = Some(dataFaker.degreeType), otherDetails = None, unknown = None)))
    } else {
      None
    }
  }

  private def getParentsOccupation = dataFaker.parentsOccupation

  private def getParentsOccupationDetail(parentsOccupation: String) = {
    if (parentsOccupation == "Employed") {
      Some(QuestionnaireQuestion(highestEarningParentOrGuardianTypeOfWorkAtAge14,
        QuestionnaireAnswer(answer = Some(dataFaker.parentsOccupationDetails), otherDetails = None, unknown = None)))
    } else {
      Some(QuestionnaireQuestion(highestEarningParentOrGuardianTypeOfWorkAtAge14,
        QuestionnaireAnswer(answer = Some(parentsOccupation), otherDetails = None, unknown = None)))
    }
  }

  private def getEmployeedOrSelfEmployeed(parentsOccupation: String) = {
    if (parentsOccupation == "Employed") {
      Some(QuestionnaireQuestion(employeeOrSelfEmployed,
        QuestionnaireAnswer(answer = Some(dataFaker.employeeOrSelf), otherDetails = None, unknown = None)))
    } else {
      None
    }
  }

  private def getSizeParentsEmployeer(parentsOccupation: String) = {
    if (parentsOccupation == "Employed") {
      Some(QuestionnaireQuestion(sizeOfPlaceOfWork,
        QuestionnaireAnswer(answer = Some(dataFaker.sizeParentsEmployeer), otherDetails = None, unknown = None)))
    } else {
      None
    }
  }

  private def getSuperviseEmployees(parentsOccupation: String) = {
    if (parentsOccupation == "Employed") {
      Some(QuestionnaireQuestion(superviseEmployees,
        QuestionnaireAnswer(answer = Some(dataFaker.yesNoPreferNotToSay), otherDetails = None, unknown = None)))
    } else {
      None
    }
  }

  private def getAllQuestionnaireQuestions(parentsOccupation: String, generatorConfig: CreateCandidateData.CreateCandidateData) = List(
    Some(QuestionnaireQuestion("I understand this won't affect my application",
      QuestionnaireAnswer(answer = Some(dataFaker.yesNo), otherDetails = None, unknown = None))),
    Some(QuestionnaireQuestion(sex,
      QuestionnaireAnswer(answer = Some(dataFaker.sex), otherDetails = None, unknown = None))),
    Some(QuestionnaireQuestion(sexualOrientation,
      QuestionnaireAnswer(answer = Some(dataFaker.sexualOrientation), otherDetails = None, unknown = None))),
    Some(QuestionnaireQuestion(ethnicGroup,
      QuestionnaireAnswer(answer = Some(dataFaker.ethnicGroup), otherDetails = None, unknown = None))),
    Some(QuestionnaireQuestion(liveInUkAged14to18,
      QuestionnaireAnswer(answer = Some(didYouLiveInUkBetween14and18Answer), otherDetails = None, unknown = None))
    ),
    getWhatWasYourHomePostCodeWhenYouWere14,
    getSchoolName14to16Answer,
    getSchoolName16to18Answer,
    getFreeSchoolMealsAnswer,
    getHaveDegreeAnswer(generatorConfig),
    getUniversityAnswer(generatorConfig),
    getUniversityDegreeCategoryAnswer(generatorConfig),
    getUniversityDegreeTypeAnswer(generatorConfig),
    Some(QuestionnaireQuestion(lowerSocioEconomicBackground,
      QuestionnaireAnswer(Some(dataFaker.yesNoPreferNotToSay), otherDetails = None, unknown = None))
    ),
    Some(QuestionnaireQuestion(parentOrGuardianQualificationsAtAge18,
      QuestionnaireAnswer(answer = Some(dataFaker.parentsDegree), otherDetails = None, unknown = None))
    ),
    getParentsOccupationDetail(parentsOccupation),
    getEmployeedOrSelfEmployeed(parentsOccupation),
    getSizeParentsEmployeer(parentsOccupation),
    getSuperviseEmployees(parentsOccupation)
  ).filter(_.isDefined).map { someItem => someItem.get }

  def generate(generationId: Int, generatorConfig: CreateCandidateData.CreateCandidateData)(
    implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext) = {

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- qRepository.addQuestions(
        candidateInPreviousStatus.applicationId.get,
        getAllQuestionnaireQuestions(getParentsOccupation, generatorConfig)
      )
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "start_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "education_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "diversity_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "occupation_questionnaire")
    } yield {
      candidateInPreviousStatus
    }
  }
}
