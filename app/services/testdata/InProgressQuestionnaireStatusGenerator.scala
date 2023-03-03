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
import repositories.application.GeneralApplicationRepository
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
                                                       )(implicit ec : ExecutionContext) extends ConstructiveGenerator {
//  val appRepository: GeneralApplicationRepository
//  val qRepository: QuestionnaireRepository

  private val didYouLiveInUkBetween14and18Answer = dataFaker.yesNo

  private def getWhatWasYourHomePostCodeWhenYouWere14 = {
    if (didYouLiveInUkBetween14and18Answer == "Yes") {
      Some(QuestionnaireQuestion("What was your home postcode when you were 14?",
        QuestionnaireAnswer(Some(dataFaker.homePostcode), None, None)))
    } else {
      None
    }
  }

  private def getSchoolName14to16Answer = {
    if (didYouLiveInUkBetween14and18Answer == "Yes") {
      Some(QuestionnaireQuestion("Aged 14 to 16 what was the name of your school?",
        QuestionnaireAnswer(Some(dataFaker.age14to16School), None, None)))
    } else {
      None
    }
  }

  private def getSchoolName16to18Answer = {
    if (didYouLiveInUkBetween14and18Answer == "Yes") {
      Some(QuestionnaireQuestion("Aged 16 to 18 what was the name of your school or college?",
        QuestionnaireAnswer(Some(dataFaker.age16to18School), None, None)))
    } else {
      None
    }
  }

  private def getFreeSchoolMealsAnswer = {
    if (didYouLiveInUkBetween14and18Answer == "Yes") {
      Some(QuestionnaireQuestion("Were you at any time eligible for free school meals?",
        QuestionnaireAnswer(Some(dataFaker.yesNoPreferNotToSay), None, None)))
    } else {
      None
    }
  }

  private def getHaveDegreeAnswer(generatorConfig: CreateCandidateData.CreateCandidateData) = {
    if (generatorConfig.isCivilServant) {
      Some(QuestionnaireQuestion("Do you have a degree?",
        QuestionnaireAnswer(Some(if (generatorConfig.hasDegree) { "Yes" } else { "No" }), None, None))
      )
    } else { None }
  }

  private def getUniversityAnswer(generatorConfig: CreateCandidateData.CreateCandidateData) = {
    if (generatorConfig.hasDegree) {
      Some(QuestionnaireQuestion("What is the name of the university you received your degree from?",
        QuestionnaireAnswer(Some(dataFaker.university._2), None, None)))
    } else {
      None
    }
  }

  private def getUniversityDegreeCategoryAnswer(generatorConfig: CreateCandidateData.CreateCandidateData) = {
    if (generatorConfig.hasDegree) {
      Some(QuestionnaireQuestion("Which category best describes your degree?",
        QuestionnaireAnswer(Some(dataFaker.degreeCategory._2), None, None)))
    } else {
      None
    }
  }

  private def getParentsOccupation = dataFaker.parentsOccupation

  private def getParentsOccupationDetail(parentsOccupation: String) = {
    if (parentsOccupation == "Employed") {
      Some(QuestionnaireQuestion("When you were 14, what kind of work did your highest-earning parent or guardian do?",
        QuestionnaireAnswer(Some(dataFaker.parentsOccupationDetails), None, None)))
    } else {
      Some(QuestionnaireQuestion("When you were 14, what kind of work did your highest-earning parent or guardian do?",
        QuestionnaireAnswer(Some(parentsOccupation), None, None)))
    }
  }

  private def getEmployeedOrSelfEmployeed(parentsOccupation: String) = {
    if (parentsOccupation == "Employed") {
      Some(QuestionnaireQuestion("Did they work as an employee or were they self-employed?",
        QuestionnaireAnswer(Some(dataFaker.employeeOrSelf), None, None)))
    } else {
      None
    }
  }

  private def getSizeParentsEmployeer(parentsOccupation: String) = {
    if (parentsOccupation == "Employed") {
      Some(QuestionnaireQuestion("Which size would best describe their place of work?",
        QuestionnaireAnswer(Some(dataFaker.sizeParentsEmployeer), None, None)))
    } else {
      None
    }
  }

  private def getSuperviseEmployees(parentsOccupation: String) = {
    if (parentsOccupation == "Employed") {
      Some(QuestionnaireQuestion("Did they supervise employees?",
        QuestionnaireAnswer(Some(dataFaker.yesNoPreferNotToSay), None, None)))
    } else {
      None
    }
  }

  private def getAllQuestionnaireQuestions(parentsOccupation: String, generatorConfig: CreateCandidateData.CreateCandidateData) = List(
    Some(QuestionnaireQuestion("I understand this won't affect my application", QuestionnaireAnswer(Some(dataFaker.yesNo), None, None))),
    Some(QuestionnaireQuestion("What is your gender identity?", QuestionnaireAnswer(Some(dataFaker.gender), None, None))),
    Some(QuestionnaireQuestion("What is your sexual orientation?", QuestionnaireAnswer(Some(dataFaker.sexualOrientation), None, None))),
    Some(QuestionnaireQuestion("What is your ethnic group?", QuestionnaireAnswer(Some(dataFaker.ethnicGroup), None, None))),
    Some(QuestionnaireQuestion("Did you live in the UK between the ages of 14 and 18?", QuestionnaireAnswer(
      Some(didYouLiveInUkBetween14and18Answer), None, None))
    ),
    getWhatWasYourHomePostCodeWhenYouWere14,
    getSchoolName14to16Answer,
    getSchoolName16to18Answer,
    getFreeSchoolMealsAnswer,
    getHaveDegreeAnswer(generatorConfig),
    getUniversityAnswer(generatorConfig),
    getUniversityDegreeCategoryAnswer(generatorConfig),
    Some(QuestionnaireQuestion("Do you consider yourself to come from a lower socio-economic background?",
      QuestionnaireAnswer(Some(dataFaker.yesNoPreferNotToSay), None, None))
    ),
    Some(QuestionnaireQuestion("Do you have a parent or guardian that completed a university degree course, or qualifications " +
      "below degree level, by the time you were 18?",
      QuestionnaireAnswer(Some(dataFaker.parentsDegree), None, None))
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
