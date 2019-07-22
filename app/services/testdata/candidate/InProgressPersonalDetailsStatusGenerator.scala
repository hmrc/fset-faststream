/*
 * Copyright 2019 HM Revenue & Customs
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

import model._
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.CreateCandidateData.{CreateCandidateData, PersonalData}
import play.api.mvc.RequestHeader
import services.personaldetails.PersonalDetailsService
import services.testdata.faker.DataFaker.Random
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object InProgressPersonalDetailsStatusGenerator extends InProgressPersonalDetailsStatusGenerator {
  override val previousStatusGenerator = CreatedStatusGenerator
  override val pdService = PersonalDetailsService
}

trait InProgressPersonalDetailsStatusGenerator extends ConstructiveGenerator {
  val pdService: PersonalDetailsService

  //scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    def getPersonalDetails(candidateInformation: CreateCandidateResponse) = {
      def getEdipCompleted = {
        if (generatorConfig.statusData.applicationRoute == ApplicationRoute.Sdip) {
          Some(generatorConfig.personalData.edipCompleted.getOrElse(Random.bool))
        }
        else {
          None
        }
      }

      def getOutsideUK(personalData: PersonalData) = {
        personalData.country.isDefined
      }

      def getCivilServiceExperienceDetails(candidateInformation: CreateCandidateResponse) = {
        if (generatorConfig.isCivilServant) {
          CivilServiceExperienceDetails(applicable = true,
            civilServiceExperienceType = if (generatorConfig.isCivilServant) Some(CivilServiceExperienceType.CivilServant) else None,
            internshipTypes = Some(generatorConfig.internshipTypes),
            fastPassReceived = Some(generatorConfig.hasFastPass), fastPassAccepted = Some(false),
            certificateNumber = generatorConfig.fastPassCertificateNumber
          )
        } else {
          CivilServiceExperienceDetails(applicable = false)
        }
      }

      model.command.GeneralDetails(
        candidateInformation.firstName,
        candidateInformation.lastName,
        generatorConfig.personalData.getPreferredName,
        candidateInformation.email,
        generatorConfig.personalData.dob,
        outsideUk = getOutsideUK(generatorConfig.personalData),
        Address("123, Fake street"),
        if (generatorConfig.personalData.country.isEmpty) {
          generatorConfig.personalData.postCode.orElse(Some(Random.postCode))
        } else {
          None
        },
        None,
        generatorConfig.personalData.country,
        "07770 774 914",
        Some(getCivilServiceExperienceDetails(candidateInformation)),
        getEdipCompleted,
        Some(true)
      )
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      pdRequest = getPersonalDetails(candidateInPreviousStatus)
      _ <- pdService.update(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.userId, pdRequest)
      pd <- pdService.find(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.userId)
    } yield {
      candidateInPreviousStatus.copy(personalDetails = Some(pd))
    }
  }
  //scalastyle:off method.length
}
