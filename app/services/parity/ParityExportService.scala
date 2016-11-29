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

package services.parity

import config.{ MicroserviceAppConfig, ParityGatewayConfig }
import play.api.Logger
import play.api.libs.json.{ JsObject, JsValue }
import services.events.{ EventService, EventSink }
import repositories._
import repositories.parity.ParityExportRepository
import repositories.parity.ParityExportRepository.ApplicationIdNotFoundException

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ParityExportService extends ParityExportService {
  val eventService = EventService
  val parityExRepository = parityExportRepository
  val cdRepository = faststreamContactDetailsRepository
  val parityGatewayConfig = MicroserviceAppConfig.parityGatewayConfig
}

trait ParityExportService extends EventSink {

  val parityExRepository: ParityExportRepository
  val parityGatewayConfig: ParityGatewayConfig
  val cdRepository: contactdetails.ContactDetailsRepository

  // Random apps in PHASE3_TESTS_PASSED_NOTIFIED
  def nextApplicationsForExport(batchSize: Int): Future[List[String]] = parityExRepository.nextApplicationsForExport(batchSize)

  // scalastyle:off method.length
  def exportApplication(applicationId: String): Future[Unit] = {

    try {
    (for {
      applicationDoc <- parityExRepository.getApplicationForExport(applicationId)
      userId = (applicationDoc \ "userId").as[String]
      _ = print("User ID = " + userId + "\n")
      contactDetailsDoc <- cdRepository.find(userId)
      // _ = print("Contact Details = " + contactDetailsDoc + "\n")
    } yield {
      Logger.debug("============ App = " + applicationDoc)

      /*
      Alternative style, not going to be used
      val export = JsObject(
        "application" -> JsObject(

        )
      )*/

      def optionalItem(key: String, valueOpt: Option[JsValue]): String = valueOpt.map { value =>
        s""""$key": $value,"""
      }.getOrElse("")

      val personalDetails = applicationDoc \ "personal-details"
      val civilServiceExperienceDetails = applicationDoc \ "civil-service-experience-details"

      val export2 =
        s"""
          | "token": ${parityGatewayConfig.upstreamAuthToken},
          | "application": {
          |
          |   "userId": "$userId",
          |   "applicationId": ${applicationDoc \ "applicationId"},
          |   "contact-details": {
          |     "outsideUk": ${contactDetailsDoc.outsideUk},
          |     "country": "${contactDetailsDoc.country}",
          |     "address": {
          |       "line1": "${contactDetailsDoc.address.line1}",
          |       "line2": "${contactDetailsDoc.address.line2}",
          |       "line3": "${contactDetailsDoc.address.line3}",
          |       "line4": "${contactDetailsDoc.address.line4}"
          |     },
          |     "email": "${contactDetailsDoc.email}",
          |     "phone": "${contactDetailsDoc.phone}",
          |     "postCode": "${contactDetailsDoc.postCode}",
          |     "frameworkId": ${applicationDoc \ "frameworkId"},
          |     "personal-details": {
          |       "firstName": ${personalDetails \ "firstName"},
          |       "lastName": ${personalDetails \ "lastName"},
          |       "preferredName": ${personalDetails \ "preferredName"},
          |       "dateOfBirth": ${personalDetails \ "dateOfBirth"}
          |     },
          |     "civil-service-experience-details": {
          |       "applicable": ${civilServiceExperienceDetails \ "applicable"},
          |       ${optionalItem("civilServiceExperienceType", (civilServiceExperienceDetails \ "civilServiceExperienceType").asOpt)}
          |       "internshipTypes": ${civilServiceExperienceDetails \ "internshipTypes"},
          |       "fastPassReceived": ${civilServiceExperienceDetails \ "fastPassReceived"}
          |     }
          |
          |   }
          | }
        """.stripMargin

      Logger.debug("=========== Exp = " + export2)
    }).recover {
      case _ => print("Error!!!!\n")
    }
    } catch {
      case _: Throwable => print("Exception!!!!\n"); Future.successful(())
    }

    // scalastyle:on method.length
  }
}
