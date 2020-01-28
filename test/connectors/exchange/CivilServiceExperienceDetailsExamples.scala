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

package connectors.exchange

object CivilServiceExperienceDetailsExamples {
  val CivilServantExperience = CivilServiceExperienceDetails(true, Some("CivilServant"), None, Some(true), None, None)
  val CivilServantExperienceFastPassApproved = CivilServiceExperienceDetails(
    true, Some("CivilServant"), None, Some(true), Some(true), Some("1234567"))
  val CivilServantExperienceFastPassRejectd = CivilServiceExperienceDetails(
    true, Some("CivilServant"), None, Some(true), Some(false), Some("1234567"))
}
