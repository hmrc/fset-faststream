/*
 * Copyright 2023 HM Revenue & Customs
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

package model.assessmentscores

import play.api.libs.json.{Json, OFormat}

// Used to be MakingEffectiveDecisionsScores
case class ThinksScores(
                         b17thinksAnalytically: Option[Double] = None, // exercise 1
                         b18widerContext: Option[Double] = None, // exercise 1
                         b1widerContext: Option[Double] = None, // exercise 2
                         b2patternsAndInterrelationships: Option[Double] = None // exercise 2
                       ) {
  override def toString: String =
    s"b17thinksAnalytically=$b17thinksAnalytically," +
      s"b18widerContext=$b18widerContext," +
      s"b1widerContext=$b1widerContext," +
      s"b2patternsAndInterrelationships=$b2patternsAndInterrelationships"
}

object ThinksScores {
  implicit val thinksScoresFormat: OFormat[ThinksScores] = Json.format[ThinksScores]
}
