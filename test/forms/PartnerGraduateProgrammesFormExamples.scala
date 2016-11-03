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

package forms

object PartnerGraduateProgrammesFormExamples {
  val InterestedNotAllForm = PartnerGraduateProgrammesForm.Data("Yes",
    Some(List("Entrepreneur First", "Frontline", "Think Ahead")))
  val NotInterestedForm = PartnerGraduateProgrammesForm.Data("No", None)
  val InterestedButNoProgrammeSelectedForm = PartnerGraduateProgrammesForm.Data("Yes", None)

  val InterestedNotAllMap = Map[String, String](
    "interested" -> "Yes",
    "partnerGraduateProgrammes[]" -> "Entrepreneur First",
    "partnerGraduateProgrammes[]" -> "Frontline",
    "partnerGraduateProgrammes[]" -> "Think Ahead"
  )

  val NoInterestedMap = Map[String, String](
    "interested" -> "No"
  )

  val InterestedButNoProgrammeSelectedMap = Map[String, String](
    "interested" -> "Yes"
  )

  val InterestedNotAllFormUrlEncodedBody = Seq(
    "interested" -> "Yes",
    "partnerGraduateProgrammes[]" -> "Entrepreneur First",
    "partnerGraduateProgrammes[]" -> "Frontline",
    "partnerGraduateProgrammes[]" -> "Think Ahead"
  )

  val NoInterestedFormUrlEncodedBody = Seq(
    "interested" -> "No"
  )

  val InterestedButNoProgrammeSelectedFormUrlEncodedBody = Seq(
    "interested" -> "Yes"
  )
}
