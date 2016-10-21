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
import model.command.testdata.CreateCandidateInStatus
import org.joda.time.{DateTime, LocalDate}

case class GeneratorConfig(emailPrefix: String,
  setGis: Boolean = false,
  cubiksUrl: String,
  firstName: Option[String] = None,
  lastName: Option[String] = None,
  preferredName: Option[String] = None,
  isCivilServant: Option[Boolean] = None,
  hasDegree: Option[Boolean] = None,
  region: Option[String] = None,
  loc1scheme1Passmark: Option[Result] = None,
  loc1scheme2Passmark: Option[Result] = None,
  previousStatus: Option[String] = None,
  confirmedAllocation: Boolean = true,
  dob: Option[LocalDate] = None,
  postCode: Option[String] = None,
  country: Option[String] = None,
  phase1StartTime: Option[DateTime] = None,
  phase1ExpiryTime: Option[DateTime] = None,
  tscore: Option[Double] = None
)
