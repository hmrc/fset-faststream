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

import model._
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.locationpreferences.LocationPreferencesRepository
import repositories.schemepreferences.SchemePreferencesRepository
import services.testdata.faker.DataFaker
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InProgressLocationPreferencesStatusGenerator @Inject()(val previousStatusGenerator: InProgressSchemePreferencesStatusGenerator,
                                                             lpRepository: LocationPreferencesRepository,
                                                             dataFaker: DataFaker
                                                           )(implicit ec: ExecutionContext) extends ConstructiveGenerator with Schemes {

  // scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext) = {
    def getLocationPreferences: Future[SelectedLocations] = {
      val interests = List("Interest 1") //TODO: change this to actual interests once we know what they are
      Future.successful(
        generatorConfig.locationPreferences.map { locationPreferencesList =>
          generatorConfig.statusData.applicationRoute match {
            case ApplicationRoute.Sdip => SelectedLocations(locationPreferencesList, interests)
            case _ => SelectedLocations(Nil, Nil)
          }

        }.getOrElse {
          // No location preferences specified in the request so create some random ones
          generatorConfig.statusData.applicationRoute match {
            case ApplicationRoute.Sdip => SelectedLocations(dataFaker.locationPreferences.map(_.id), interests)
            case _ => SelectedLocations(Nil, Nil)
          }
        }
      )
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      locationPreferences <- getLocationPreferences
      _ <- lpRepository.save(candidateInPreviousStatus.applicationId.get, locationPreferences)
    } yield {
      candidateInPreviousStatus.copy(
        locationPreferences = Some(locationPreferences)
      )
    }
  }
}
