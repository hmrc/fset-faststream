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

package model.command.testdata

import play.api.libs.json.Json


case class AssistanceDetailsRequest(hasDisability: Option[String] = None,
                                    hasDisabilityDescription: Option[String] = None,
                                    setGis: Option[Boolean] = None,
                                    onlineAdjustments: Option[Boolean] = None,
                                    onlineAdjustmentsDescription: Option[String] = None,
                                    assessmentCentreAdjustments: Option[Boolean] = None,
                                    assessmentCentreAdjustmentsDescription: Option[String] = None)

case class CreateCandidateInStatusRequest(applicationStatus: String,
                                          previousApplicationStatus: Option[String] = None,
                                          progressStatus: Option[String],
                                          numberToGenerate: Int = 1,
                                          emailPrefix: Option[String],
                                          firstName: Option[String],
                                          lastName: Option[String],
                                          preferredName: Option[String],
                                          dateOfBirth: Option[String],
                                          postCode: Option[String],
                                          country: Option[String],
                                          assistanceDetails: Option[AssistanceDetailsRequest],
                                          isCivilServant: Option[Boolean],
                                          hasDegree: Option[Boolean],
                                          region: Option[String],
                                          loc1scheme1EvaluationResult: Option[String],
                                          loc1scheme2EvaluationResult: Option[String],
                                          confirmedAllocation: Option[Boolean],
                                          phase1StartTime: Option[String],
                                          phase1ExpiryTime: Option[String],
                                          tscore: Option[Double])

object AssistanceDetailsRequest {
  implicit val assistanceDetailsRequestFormat = Json.format[AssistanceDetailsRequest]
}

object CreateCandidateInStatusRequest {
  implicit val createCandidateInStatusRequestFormat = Json.format[CreateCandidateInStatusRequest]
}
