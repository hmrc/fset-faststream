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

case class CreateCandidateInStatus(applicationStatus: String,
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
                                   setGis: Option[Boolean],
                                   isCivilServant: Option[Boolean],
                                   hasDegree: Option[Boolean],
                                   region: Option[String],
                                   loc1scheme1EvaluationResult: Option[String],
                                   loc1scheme2EvaluationResult: Option[String],
                                   confirmedAllocation: Option[Boolean],
                                   phase1StartTime: Option[String],
                                   phase1ExpiryTime: Option[String],
                                   tscore: Option[Double])

object CreateCandidateInStatus {
  implicit val createCandidateInStatusFormat = Json.format[CreateCandidateInStatus]
}