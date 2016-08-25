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

package connectors.exchange

import forms.FastPassForm._
import play.api.libs.json.Json
import language.implicitConversions

case class FastPassDetails(applicable: Boolean,
                           fastPassType: Option[String] = None,
                           internshipTypes: Option[Seq[String]] = None,
                           fastPassReceived: Option[Boolean] = None,
                           certificateNumber: Option[String] = None)

object FastPassDetails {

  implicit val fastPassDetailsFormat = Json.format[FastPassDetails]

  implicit def toData(exchange: FastPassDetails): Data = Data(
    exchange.applicable.toString,
    exchange.fastPassType,
    exchange.internshipTypes,
    exchange.fastPassReceived,
    exchange.certificateNumber
  )

  implicit def toExchange(data: Data): FastPassDetails = FastPassDetails(
    data.applicable.toBoolean,
    data.fastPassType,
    data.internshipTypes,
    data.fastPassReceived,
    data.certificateNumber
  )

}
