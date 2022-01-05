/*
 * Copyright 2022 HM Revenue & Customs
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

object WithdrawApplicationFormExamples {
  val ValidForm = WithdrawApplicationForm.Data("Yes", Some("found another job"), None)

  val OtherReasonValidForm = WithdrawApplicationForm.Data("Yes", Some("Other (provide details)"), Some("more info"))

  val OtherReasonInvalidNoReasonForm = WithdrawApplicationForm.Data("Yes", None, None)

  val OtherReasonInvalidNoOtherReasonMoreInfoForm = WithdrawApplicationForm.Data("Yes", Some("Other (provide details)"), None)

  val ValidMap = Map[String, String](
    "wantToWithdraw" -> "Yes",
    "reason" -> "found another job")

  val OtherReasonValidMap = Map[String, String](
    "wantToWithdraw" -> "Yes",
    "reason" -> "Other (provide details)",
    "otherReason" -> "more info"
  )

  val OtherReasonInvalidNoReasonMap = Map[String, String](
    "wantToWithdraw" -> "Yes")

  val OtherReasonInvalidNoOtherReasonMoreInfoMap = Map[String, String](
    "wantToWithdraw" -> "Yes",
    "reason" -> "Other (provide details)")

  val ValidFormUrlEncodedBody = Seq(
    "wantToWithdraw" -> "Yes",
    "reason" -> "found another job")

  val OtherReasonValidFormUrlEncodedBody = Seq(
    "wantToWithdraw" -> "Yes",
    "reason" -> "Other (provide details)",
    "otherReason" -> "more info"
  )

  val OtherReasonInvalidNoReasonFormUrlEncodedBody = Seq(
    "wantToWithdraw" -> "Yes")

  val OtherReasonInvalidNoOtherReasonMoreInfoFormUrlEncodedBody = Seq(
    "wantToWithdraw" -> "Yes",
    "reason" -> "Other (provide details)")
}
