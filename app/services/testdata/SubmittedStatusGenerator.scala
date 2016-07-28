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

import connectors.testdata.ExchangeObjects.DataGenerationResponse
import model.Commands.AssistanceDetailsExchange
import model.PersistedObjects.{ContactDetails, PersistedAnswer, PersistedQuestion, PersonalDetails}
import model.{Address, Alternatives, LocationPreference, Preferences}
import org.joda.time.LocalDate
import repositories._
import repositories.application.{AssistanceDetailsRepository, GeneralApplicationRepository, PersonalDetailsRepository}
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SubmittedStatusGenerator extends SubmittedStatusGenerator {
  override val previousStatusGenerator = CreatedStatusGenerator
  override val appRepository = applicationRepository
  override val pdRepository = personalDetailsRepository
  override val adRepository = assistanceRepository
  override val cdRepository = contactDetailsRepository
  override val fpRepository = frameworkPreferenceRepository
  override val qRepository = questionnaireRepository

}

trait SubmittedStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val pdRepository: PersonalDetailsRepository
  val adRepository: AssistanceDetailsRepository
  val cdRepository: ContactDetailsRepository
  val fpRepository: FrameworkPreferenceRepository
  val qRepository: QuestionnaireRepository

  // scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    def getPersonalDetails(candidateInformation: DataGenerationResponse) = {
      PersonalDetails(
        candidateInformation.firstName,
        candidateInformation.lastName,
        "Pref" + candidateInformation.firstName,
        new LocalDate(2015, 5, 21),
        Random.bool,
        Random.bool
      )
    }

    def getAssistanceDetails(gis: Boolean) = {
      if (gis) {
        AssistanceDetailsExchange(
          "yes", Some(List("Wheelchair")), Some("Wheelchair required"), Some("yes"),
          Some("yes"), Some(List()), None, None, None, None, None, None
        )
      } else {
        AssistanceDetailsExchange(
          "no", Some(List()), None, None, Some("no"), Some(List()), None, None, None, None, None, None
        )
      }
    }

    def getContactDetails(candidateInformation: DataGenerationResponse) = {
      ContactDetails(
        Address("123, Fake street"),
        "AB1 2CD",
        candidateInformation.email,
        Some("07770 774 914")
      )
    }

    def getFrameworkPrefs: Future[Preferences] = {
      for {
        randomRegion <- Random.region
        region = generatorConfig.region.getOrElse(randomRegion)
        firstLocation <- Random.location(region)
        secondLocation <- Random.location(region, List(firstLocation))
      } yield {
        Preferences(
          LocationPreference(region, firstLocation, "Commercial", None),
          None,
          None,
          Some(Alternatives(
            Random.bool,
            Random.bool
          ))
        )
      }
    }

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
      _ <- pdRepository.update(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.userId,
        getPersonalDetails(candidateInPreviousStatus))
      _ <- adRepository.update(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.userId,
        getAssistanceDetails(generatorConfig.setGis))
      _ <- cdRepository.update(candidateInPreviousStatus.userId, getContactDetails(candidateInPreviousStatus))
      frameworkPrefs <- getFrameworkPrefs
      _ <- fpRepository.savePreferences(candidateInPreviousStatus.applicationId.get, frameworkPrefs)
      _ <- qRepository.addQuestions(candidateInPreviousStatus.applicationId.get, getAllQuestionnaireQuestions)
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "start_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "education_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "diversity_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "occupation_questionnaire")
      submit <- appRepository.submit(candidateInPreviousStatus.applicationId.get)
    } yield {
      candidateInPreviousStatus.copy(
        preferences = Some(frameworkPrefs)
      )
    }
  }
  // scalastyle:on method.length
}
