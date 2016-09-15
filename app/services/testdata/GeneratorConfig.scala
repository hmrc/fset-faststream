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

import model.EvaluationResults.Result
import org.joda.time.{ DateTime, LocalDate }

case class GeneratorConfig(emailPrefix: String,
                           setGis: Boolean = false,
                           cubiksUrl: String,
                           firstName: Option[String],
                           lastName: Option[String],
                           preferredName: Option[String],
                           isCivilServant: Option[Boolean],
                           hasDegree: Option[Boolean],
                           region: Option[String],
                           loc1scheme1Passmark: Option[Result],
                           loc1scheme2Passmark: Option[Result],
                           previousStatus: Option[String],
                           confirmedAllocation: Boolean = true,
                           dob: Option[LocalDate],
                           postCode: Option[String],
                           country: Option[String],
                           phase1StartTime: Option[DateTime],
                           phase1ExpiryTime: Option[DateTime])
