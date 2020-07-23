/*
 * Copyright 2020 HM Revenue & Customs
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
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.schemepreferences.SchemePreferencesRepository
import services.testdata.faker.DataFaker
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class InProgressSchemePreferencesStatusGenerator @Inject() (val previousStatusGenerator: InProgressPersonalDetailsStatusGenerator,
                                                            spRepository: SchemePreferencesRepository,
                                                            dataFaker: DataFaker
                                                           ) extends ConstructiveGenerator {

  // scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    def getSchemePreferences: Future[SelectedSchemes] = {
      Future.successful(
        generatorConfig.schemeTypes.map { schemeTypesList =>
          generatorConfig.statusData.applicationRoute match {
            case ApplicationRoute.Edip => SelectedSchemes(List(model.SchemeId("Edip")), orderAgreed = true, eligible = true)
            case ApplicationRoute.Sdip => SelectedSchemes(List(SchemeId("Sdip")), orderAgreed = true, eligible = true)
            case ApplicationRoute.SdipFaststream => SelectedSchemes(schemeTypesList :+ model.SchemeId("Sdip"),
              orderAgreed = true, eligible = true)
            case _ => SelectedSchemes(schemeTypesList, orderAgreed = true, eligible = true)
          }

        }.getOrElse {
          generatorConfig.statusData.applicationRoute match {
            case ApplicationRoute.Edip => SelectedSchemes(List(model.SchemeId("Edip")), orderAgreed = true, eligible = true)
            case ApplicationRoute.Sdip => SelectedSchemes(List(model.SchemeId("Sdip")), orderAgreed = true, eligible = true)
            case ApplicationRoute.SdipFaststream => SelectedSchemes(List(model.SchemeId("Sdip"), model.SchemeId("Commercial"),
              model.SchemeId("DigitalAndTechnology"), model.SchemeId("Finance")), orderAgreed = true, eligible = true)
            case _ => SelectedSchemes(dataFaker.schemeTypes.map(_.id), orderAgreed = true, eligible = true)
          }
        }
      )
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      schemePreferences <- getSchemePreferences
      _ <- spRepository.save(candidateInPreviousStatus.applicationId.get, schemePreferences)
    } yield {
      candidateInPreviousStatus.copy(
        schemePreferences = Some(schemePreferences)
      )
    }
  }
  // scalastyle:on method.length
}
