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

package services.testdata

import model.PersistedObjects.{ PersistedAnswer, PersistedQuestion }
import model.persisted.{ AssistanceDetails }
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object InProgressQuestionnaireStatusGenerator extends InProgressQuestionnaireStatusGenerator {
  override val previousStatusGenerator = InProgressAssistanceDetailsStatusGenerator
  override val appRepository = applicationRepository
  override val qRepository = questionnaireRepository
}

trait InProgressQuestionnaireStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val qRepository: QuestionnaireRepository

  // scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {

    def getAllQuestionnaireQuestions = List(
      PersistedQuestion("What is your gender identity?", PersistedAnswer(Some(Random.gender), None, None)),
      PersistedQuestion("What is your sexual orientation?", PersistedAnswer(Some(Random.sexualOrientation), None, None)),
      PersistedQuestion("What is your ethnic group?", PersistedAnswer(Some(Random.ethnicGroup), None, None)),
      PersistedQuestion(
        "Between the ages of 11 to 16, in which school did you spend most of your education?",
        PersistedAnswer(Some(Random.age11to16School), None, None)
      ),
      PersistedQuestion(
        "Between the ages of 16 to 18, in which school did you spend most of your education?",
        PersistedAnswer(Some(Random.age16to18School), None, None)
      ),
      PersistedQuestion("What was your home postcode when you were 14?", PersistedAnswer(Some(Random.homePostcode), None, None)),
      PersistedQuestion(
        "During your school years, were you at any time eligible for free school meals?",
        PersistedAnswer(Some(Random.yesNo), None, None)
      ),
      PersistedQuestion(
        "Did any of your parent(s) or guardian(s) complete a university degree course or equivalent?",
        PersistedAnswer(Some(Random.yesNo), None, None)
      ),
      PersistedQuestion(
        "Which type of occupation did they have?",
        PersistedAnswer(Some(Random.parentsOccupation), None, None)
      ),
      PersistedQuestion(
        "Did they work as an employee or were they self-employed?",
        PersistedAnswer(Random.employeeOrSelf, None, None)
      ),
      PersistedQuestion(
        "Which size would best describe their place of work?",
        PersistedAnswer(Some(Random.sizeOfPlaceOfWork), None, None)
      ),
      PersistedQuestion(
        "Did they supervise any other employees?",
        PersistedAnswer(Some(Random.yesNo), None, None)
      )
    )

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- qRepository.addQuestions(candidateInPreviousStatus.applicationId.get, getAllQuestionnaireQuestions)
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "start_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "education_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "diversity_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "occupation_questionnaire")
    } yield {
      candidateInPreviousStatus
    }
  }
  // scalastyle:on method.length
}
