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

import javax.inject.{ Inject, Singleton }
import model._
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.candidate.CreateCandidateData.{ CreateCandidateData, PersonalData }
import play.api.mvc.RequestHeader
import services.personaldetails.PersonalDetailsService
import services.testdata.faker.DataFaker
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class InProgressPersonalDetailsStatusGenerator @Inject() (val previousStatusGenerator: CreatedStatusGenerator,
                                                          pdService: PersonalDetailsService,
                                                          dataFaker: DataFaker) extends ConstructiveGenerator {

  //scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    def getPersonalDetails(candidateInformation: CreateCandidateResponse) = {
      def getEdipCompleted = {
        if (generatorConfig.statusData.applicationRoute == ApplicationRoute.Sdip) {
          Some(generatorConfig.personalData.edipCompleted.getOrElse(dataFaker.Random.bool))
        }
        else {
          None
        }
      }

      def getOtherInternshipCompleted = {
        if (generatorConfig.statusData.applicationRoute == ApplicationRoute.Sdip) {
          Some(generatorConfig.personalData.otherInternshipCompleted.getOrElse(dataFaker.Random.bool))
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
            civilServantAndInternshipTypes = Some(generatorConfig.civilServantAndInternshipTypes),
            fastPassReceived = Some(generatorConfig.hasFastPass), fastPassAccepted = None,
            certificateNumber = generatorConfig.fastPassCertificateNumber
          )
        } else {
          CivilServiceExperienceDetails(applicable = false)
        }
      }

      val edipCompleted = getEdipCompleted
      val otherInternshipCompleted = getOtherInternshipCompleted
      model.command.GeneralDetails(
        candidateInformation.firstName,
        candidateInformation.lastName,
        generatorConfig.personalData.getPreferredName,
        candidateInformation.email,
        generatorConfig.personalData.dob,
        outsideUk = getOutsideUK(generatorConfig.personalData),
        Address("123, Fake street"),
        if (generatorConfig.personalData.country.isEmpty) {
          generatorConfig.personalData.postCode.orElse(Some(dataFaker.postCode))
        } else {
          None
        },
        fsacIndicator = None,
        generatorConfig.personalData.country,
        "07770 774 914",
        Some(getCivilServiceExperienceDetails(candidateInformation)),
        edipCompleted,
        edipCompleted.flatMap { completed => if (completed) { Some("2020") } else { None } },
        otherInternshipCompleted,
        otherInternshipCompleted.flatMap { completed => if (completed) { Some("Other internship name") } else { None } },
        otherInternshipCompleted.flatMap { completed => if (completed) { Some("2020") } else { None } },
        updateApplicationStatus = Some(true)
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
