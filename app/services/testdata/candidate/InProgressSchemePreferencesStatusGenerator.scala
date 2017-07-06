/*
 * Copyright 2017 HM Revenue & Customs
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
import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories._
import repositories.schemepreferences.SchemePreferencesRepository
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object InProgressSchemePreferencesStatusGenerator extends InProgressSchemePreferencesStatusGenerator {
  override val previousStatusGenerator = InProgressPersonalDetailsStatusGenerator
  override val spRepository = schemePreferencesRepository
}

trait InProgressSchemePreferencesStatusGenerator extends ConstructiveGenerator {
  val spRepository: SchemePreferencesRepository

  // scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    def getSchemePreferences: Future[SelectedSchemes] = {
      Future.successful(
        generatorConfig.schemeTypes.map { schemeTypesList =>
          generatorConfig.statusData.applicationRoute match {
            case ApplicationRoute.SdipFaststream => SelectedSchemes(
              model.SchemeType.Sdip :: schemeTypesList,
              orderAgreed = true, eligible = true
            )
            case _ => SelectedSchemes(schemeTypesList, orderAgreed = true, eligible = true)
          }

        }.getOrElse {
          generatorConfig.statusData.applicationRoute match {
            case ApplicationRoute.Edip => SelectedSchemes(List(model.SchemeType.Edip), orderAgreed = true, eligible = true)
            case ApplicationRoute.Sdip => SelectedSchemes(List(model.SchemeType.Sdip), orderAgreed = true, eligible = true)
            case ApplicationRoute.SdipFaststream => SelectedSchemes(List(model.SchemeType.Sdip, model.SchemeType.Commercial,
              model.SchemeType.DigitalAndTechnology, model.SchemeType.Finance), orderAgreed = true, eligible = true)
            case _ => SelectedSchemes(Random.schemeTypes, orderAgreed = true, eligible = true)
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
