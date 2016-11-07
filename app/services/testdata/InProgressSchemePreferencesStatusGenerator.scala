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
import model.PersistedObjects.{ PersistedAnswer, PersistedQuestion }
import model._
import model.command.testdata.GeneratorConfig
import model.persisted.{ AssistanceDetails, ContactDetails, PersonalDetails }
import org.joda.time.LocalDate
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
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
  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    def getSchemePreferences: Future[SelectedSchemes] = {
       Future.successful(
         if (generatorConfig.applicationRoute == ApplicationRoute.Edip) {
           SelectedSchemes(List(model.SchemeType.Edip), true, true)
         } else {
           SelectedSchemes(Random.schemeTypes, true, true)
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
