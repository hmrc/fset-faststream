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
import model.persisted.{ ContactDetails, PersonalDetails }
import org.joda.time.LocalDate
import repositories._
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.testdata.faker.DataFaker.Random
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object InProgressPersonalDetailsStatusGenerator extends InProgressPersonalDetailsStatusGenerator {
  override val previousStatusGenerator = CreatedStatusGenerator
  override val pdRepository = faststreamPersonalDetailsRepository
  override val cdRepository = faststreamContactDetailsRepository
  override val fpdRepository = civilServiceExperienceDetailsRepository
}

trait InProgressPersonalDetailsStatusGenerator extends ConstructiveGenerator {
  val pdRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository
  val fpdRepository: CivilServiceExperienceDetailsRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    def getPersonalDetails(candidateInformation: DataGenerationResponse) = {
      PersonalDetails(
        candidateInformation.firstName,
        candidateInformation.lastName,
        candidateInformation.preferredName,
        generatorConfig.dob.getOrElse(new LocalDate(1981, 5, 21))
      )
    }

    def getContactDetails(candidateInformation: DataGenerationResponse) = {
      ContactDetails(
        outsideUk = false,
        Address("123, Fake street"),
        if (generatorConfig.country.isEmpty) { generatorConfig.postCode.orElse(Some(Random.postCode)) } else { None },
        generatorConfig.country,
        candidateInformation.email,
        "07770 774 914"
      )
    }

    def getCivilServiceExperienceDetails(candidateInformation: DataGenerationResponse) = {
      if (generatorConfig.isCivilServant.contains(true)) {
        CivilServiceExperienceDetails(true, Some(CivilServiceExperienceType.CivilServant), None, None, None)
      } else {
        CivilServiceExperienceDetails(applicable = false)
      }
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      pd = getPersonalDetails(candidateInPreviousStatus)
      cd = getContactDetails(candidateInPreviousStatus)
      fpd = getCivilServiceExperienceDetails(candidateInPreviousStatus)
      _ <- pdRepository.update(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.userId,
        pd, List(model.ApplicationStatus.CREATED), model.ApplicationStatus.IN_PROGRESS)
      _ <- cdRepository.update(candidateInPreviousStatus.userId, cd)
      _ <- fpdRepository.update(candidateInPreviousStatus.applicationId.get, fpd)
    } yield {
      candidateInPreviousStatus.copy(personalDetails = Some(pd), contactDetails = Some(cd))
    }
  }
}