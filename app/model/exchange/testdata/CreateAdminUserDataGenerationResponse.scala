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

package model.exchange.testdata

import play.api.libs.json.{ Json, OFormat }

case class CreateAdminUserDataGenerationResponse(generationId: Int, userId: String,
                                            preferedName: Option[String], email: String,
                                            firstName: String, lastName: String, phone: Option[String] = None,
                                            assessor: Option[AssessorData] = None) {

}



object CreateAdminUserDataGenerationResponse {
  implicit val createAdminUserDataGenerationResponseFormat: OFormat[CreateAdminUserDataGenerationResponse] =
    Json.format[CreateAdminUserDataGenerationResponse]
}
