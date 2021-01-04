/*
 * Copyright 2021 HM Revenue & Customs
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

package models

object SchemeStatus {
  sealed trait Status
  case object Green extends Status
  case object Amber extends Status
  case object Red extends Status
  case object Withdrawn extends Status

  object Status {
    import scala.language.implicitConversions
    implicit def stringify(self: Status): String = self.toString

    def apply(s: String): Status = s match {
      case "Red" => Red
      case "Green" => Green
      case "Amber" => Amber
      case "Withdrawn" => Withdrawn
    }
  }
}

