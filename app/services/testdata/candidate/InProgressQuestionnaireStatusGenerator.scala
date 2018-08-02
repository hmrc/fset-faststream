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

package services.testdata.candidate

import model.exchange.testdata.CreateCandidateResponse
import model.persisted.{ QuestionnaireAnswer, QuestionnaireQuestion }
import model.testdata.CreateCandidateData.{ CreateCandidateData, DiversityDetails }
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.{ GeneralApplicationMongoRepository, GeneralApplicationRepository }
import services.testdata.faker.DataFaker._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object InProgressQuestionnaireStatusGenerator extends InProgressQuestionnaireStatusGenerator {
  override val previousStatusGenerator = InProgressAssistanceDetailsStatusGenerator
  override val appRepository: GeneralApplicationMongoRepository = applicationRepository
  override val qRepository: QuestionnaireMongoRepository = questionnaireRepository
}

trait InProgressQuestionnaireStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val qRepository: QuestionnaireRepository

  // scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse.CreateCandidateResponse] = {

    val didYouLiveInUkBetween14and18Answer = Random.yesNo

    def getWhatWasYourHomePostCodeWhenYouWere14 = {
      if (didYouLiveInUkBetween14and18Answer == "Yes") {
        Some(QuestionnaireQuestion("What was your home postcode when you were 14?",
          QuestionnaireAnswer(Some(Random.homePostcode), None, None)))
      } else {
        None
      }
    }

    def getSchoolName14to16Answer = {
      if (didYouLiveInUkBetween14and18Answer == "Yes") {
        Some(QuestionnaireQuestion("Aged 14 to 16 what was the name of your school?",
          QuestionnaireAnswer(Some(Random.age14to16School), None, None)))
      } else {
        None
      }
    }

    def getSchoolType14to16Answer = if (didYouLiveInUkBetween14and18Answer == "Yes") {
      Some(QuestionnaireQuestion("What type of school was this?",
        QuestionnaireAnswer(Some(Random.schoolType14to16), None, None))
      )
    } else { None }

    def getSchoolName16to18Answer = {
      if (didYouLiveInUkBetween14and18Answer == "Yes") {
        Some(QuestionnaireQuestion("Aged 16 to 18 what was the name of your school?",
          QuestionnaireAnswer(Some(Random.age16to18School), None, None)))
      } else {
        None
      }
    }

    def getFreeSchoolMealsAnswer = {
      if (didYouLiveInUkBetween14and18Answer == "Yes") {
        Some(QuestionnaireQuestion("Were you at any time eligible for free school meals?",
          QuestionnaireAnswer(Some(Random.yesNoPreferNotToSay), None, None)))
      } else {
        None
      }
    }

    def getHaveDegreeAnswer = {
      if (generatorConfig.isCivilServant) {
        Some(QuestionnaireQuestion("Do you have a degree?",
          QuestionnaireAnswer(Some(if (generatorConfig.hasDegree) { "Yes" } else { "No" }), None, None))
        )
      } else { None }
    }

    def getUniversityAnswer = {
      if (generatorConfig.hasDegree) {
        Some(QuestionnaireQuestion("What is the name of the university you received your degree from?",
          QuestionnaireAnswer(Some(generatorConfig.diversityDetails.universityAttended), None, None)))
      } else {
        None
      }
    }

    def getUniversityDegreeCategoryAnswer = {
      if (generatorConfig.hasDegree) {
        Some(QuestionnaireQuestion("Which category best describes your degree?",
          QuestionnaireAnswer(Some(Random.degreeCategory._2), None, None)))
      } else {
        None
      }
    }

    def getEmployedOrSelfEmployed(parentsOccupation: String) = {
      if (parentsOccupation == "Employed") {
        Some(QuestionnaireQuestion("Did they work as an employee or were they self-employed?",
          QuestionnaireAnswer(Some(Random.employeeOrSelf), None, None)))
      } else {
        None
      }
    }

    def getSizeParentsEmployer(occupation: Option[String]) = occupation.flatMap { occ =>
      if (occ == "Employed") {
        Some(QuestionnaireQuestion("Which size would best describe their place of work?",
          QuestionnaireAnswer(Some(Random.sizeOfPlaceOfWork), None, None)))
      } else { None }
    }

    def getSuperviseEmployees(parentsOccupation: Option[String]) = parentsOccupation.flatMap { occ =>
      if (occ == "Employed") {
        Some(QuestionnaireQuestion("Did they supervise employees?",
          QuestionnaireAnswer(Some(Random.yesNoPreferNotToSay), None, None)))
      } else { None }
    }

    def getAllQuestionnaireQuestions(dd: DiversityDetails) = List(
      Some(QuestionnaireQuestion("I understand this won't affect my application", QuestionnaireAnswer(Some(Random.yesNo), None, None))),
      Some(QuestionnaireQuestion("What is your gender identity?", QuestionnaireAnswer(Some(dd.genderIdentity), None, None))),
      Some(QuestionnaireQuestion("What is your sexual orientation?", QuestionnaireAnswer(Some(dd.sexualOrientation), None, None))),
      Some(QuestionnaireQuestion("What is your ethnic group?", QuestionnaireAnswer(Some(dd.ethnicity), None, None))),
      Some(QuestionnaireQuestion("Did you live in the UK between the ages of 14 and 18?", QuestionnaireAnswer(
        Some(didYouLiveInUkBetween14and18Answer), None, None))
      ),
      getWhatWasYourHomePostCodeWhenYouWere14,
      getSchoolName14to16Answer,
      getSchoolType14to16Answer,
      getSchoolName16to18Answer,
      getFreeSchoolMealsAnswer,
      getHaveDegreeAnswer,
      getUniversityAnswer,
      getUniversityDegreeCategoryAnswer,
      Some(QuestionnaireQuestion("Do you consider yourself to come from a lower socio-economic background?",
        QuestionnaireAnswer(Some(Random.yesNoPreferNotToSay), None, None))
      ),
      Some(QuestionnaireQuestion("Do you have a parent or guardian that completed a university degree course, or qualifications " +
        "below degree level, by the time you were 18?",
        QuestionnaireAnswer(Some(Random.parentsDegree), None, None))
      ),
      Some(QuestionnaireQuestion("When you were 14, what kind of work did your highest-earning parent or guardian do?",
        QuestionnaireAnswer(dd.parentalEmployment, None, None))),
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
