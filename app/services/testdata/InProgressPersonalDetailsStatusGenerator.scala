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
import model._
import model.persisted.{ContactDetails, PersonalDetails }
import org.joda.time.LocalDate
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object InProgressPersonalDetailsStatusGenerator extends InProgressPersonalDetailsStatusGenerator {
  override val previousStatusGenerator = CreatedStatusGenerator
  override val pdRepository = faststreamPersonalDetailsRepository
  override val cdRepository = faststreamContactDetailsRepository
}

trait InProgressPersonalDetailsStatusGenerator extends ConstructiveGenerator {
  val pdRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    def getPersonalDetails(candidateInformation: DataGenerationResponse) = {
      PersonalDetails(
        candidateInformation.firstName,
        candidateInformation.lastName,
        "Pref" + candidateInformation.firstName,
        new LocalDate(1981, 5, 21)
      )
    }

    def getContactDetails(candidateInformation: DataGenerationResponse) = {
      ContactDetails(
        false,
        Address("123, Fake street"),
        Some("AB1 2CD"),
        candidateInformation.email,
        "07770 774 914"
      )
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- pdRepository.update(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.userId,
        getPersonalDetails(candidateInPreviousStatus), List(model.ApplicationStatus.CREATED), model.ApplicationStatus.IN_PROGRESS)
      _ <- cdRepository.update(candidateInPreviousStatus.userId, getContactDetails(candidateInPreviousStatus))
    } yield {
      candidateInPreviousStatus
    }
  }
}
