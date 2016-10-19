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

package connectors.testdata

import model.Commands.ApplicationAssessment
import model.persisted.{ContactDetails, PersonalDetails}
import model.SelectedSchemes
import play.api.libs.json.Json

object ExchangeObjects {

  case class DataGenerationResponse(generationId: Int,
                                    userId: String,
                                    applicationId: Option[String],
                                    email: String,
                                    firstName: String,
                                    lastName: String,
                                    preferredName: String,
                                    mediaReferrer: Option[String] = None,
                                    personalDetails: Option[PersonalDetails] = None,
                                    isCivilServant: Option[Boolean] = None,
                                    contactDetails: Option[ContactDetails] = None,
                                    phase1TestGroup: Option[TestGroupResponse] = None,
                                    phase2TestGroup: Option[TestGroupResponse] = None,
                                    applicationAssessment: Option[ApplicationAssessment] = None,
                                    schemePreferences: Option[SelectedSchemes] = None,
                                    phase3TestUrl: Option[String] = None
                                   )

  case class TestGroupResponse(tests: List[CubiksTestResponse])

  case class CubiksTestResponse(cubiksUserId: Int, token: String, testUrl: String)

  object Implicits {

    import model.Commands.Implicits._

    implicit val phase1TestResponseFormat = Json.format[CubiksTestResponse]
    implicit val phase1TestGroupResponseFormat = Json.format[TestGroupResponse]
    implicit val dataGenerationResponseFormat = Json.format[DataGenerationResponse]
  }

}
