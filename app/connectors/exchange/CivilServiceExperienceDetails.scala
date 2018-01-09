/*
 * Copyright 2018 HM Revenue & Customs
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
import forms.FastPassForm
import play.api.libs.json.Json

import language.implicitConversions

case class CivilServiceExperienceDetails(applicable: Boolean,
                                         civilServiceExperienceType: Option[String] = None,
                                         internshipTypes: Option[Seq[String]] = None,
                                         fastPassReceived: Option[Boolean] = None,
                                         fastPassAccepted: Option[Boolean] = None,
                                         certificateNumber: Option[String] = None) {
  def isCivilServant = civilServiceExperienceType.exists {
    fpt => fpt == FastPassForm.CivilServant || fpt == FastPassForm.CivilServantViaFastTrack
  }
}

object CivilServiceExperienceDetails {

  implicit val civilServiceExperienceDetailsFormat = Json.format[CivilServiceExperienceDetails]

  implicit def toData(optExchange: Option[CivilServiceExperienceDetails]): Option[Data] = optExchange.map(exchange => Data(
    exchange.applicable.toString,
    exchange.civilServiceExperienceType,
    exchange.internshipTypes,
    exchange.fastPassReceived,
    exchange.certificateNumber
  ))

  implicit def toExchange(optData: Option[Data]): Option[CivilServiceExperienceDetails] = {
    optData.map( data =>
      CivilServiceExperienceDetails(
        data.applicable.toBoolean,
        data.civilServiceExperienceType,
        data.internshipTypes,
        data.fastPassReceived,
        None,
        data.certificateNumber
      )
    )
  }
}
