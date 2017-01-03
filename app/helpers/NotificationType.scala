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

package helpers

import play.api.i18n.Messages

import scala.language.implicitConversions

/**
 * see flashMessage.scala.html
 */

sealed abstract class NotificationType(val key: String)

object NotificationType extends Enumeration {

  // Shows a success banner
  object Success extends NotificationType("success")

  // Shows a banner used for errors
  object Danger extends NotificationType("danger")

  // Shows a warning banner used for optional errors (if any) or important messages to the user e.g. deadline for applications is near
  object Warning extends NotificationType("warning")

  // shows a info banner, for general information
  object Info extends NotificationType("info")

  val all = Seq(Success, Danger, Warning, Info)

  def success(msg: String, args: Any*): (NotificationType, String) = Success -> Messages(msg, args: _*)
  def danger(msg: String, args: Any*): (NotificationType, String) = Danger -> Messages(msg, args: _*)
  def warning(msg: String, args: Any*): (NotificationType, String) = Warning -> Messages(msg, args: _*)
  def info(msg: String, args: Any*): (NotificationType, String) = Info -> Messages(msg, args: _*)

  /**
   * Implicit conversion helps when using the Notification Type with play's Flash session, as it doesn't understand
   * the Notification Type, but expects (String, String). Using the implicit conversion means that we can be type-safe
   * right until we call the play flash methods
   * @param value
   * @return
   */
  implicit def notificationToFlash(value: (NotificationType, String)): (String, String) = (value._1.key, value._2)
}
