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

import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.CivilServantAndInternshipType.CivilServantAndInternshipType
import model.OnlineTestCommands.OnlineTestApplication
import model._
import model.persisted.SchemeEvaluationResult
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{BsonDocument, BsonValue}
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.Codecs

import java.time.OffsetDateTime
import scala.util.Try
import scala.language.postfixOps

package object repositories {
  val insertIfNoRecordFound = true

  implicit object OFormatHelper {
    def oFormat[T](implicit format:Format[T]) : OFormat[T] = {
      val oFormat: OFormat[T] = new OFormat[T](){
        override def writes(o: T): JsObject = format.writes(o).as[JsObject]
        override def reads(json: JsValue): JsResult[T] = format.reads(json)
      }
      oFormat
    }
  }

  def bsonDocToOnlineTestApplication(doc: Document) = {
    val applicationId = getAppId(doc)
    val applicationStatus = Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)
    val userId = getUserId(doc)
    val testAccountId = doc.get("testAccountId").get.asString().getValue

    val personalDetailsRoot = doc.get("personal-details").map(_.asDocument()).get
    val preferredName = personalDetailsRoot.get("preferredName").asString().getValue
    val lastName = personalDetailsRoot.get("lastName").asString().getValue

    val assistanceDetailsRoot = doc.get("assistance-details").map(_.asDocument()).get
    val guaranteedInterview = Try(assistanceDetailsRoot.get("guaranteedInterview").asBoolean().getValue).getOrElse(false)
    val needsAdjustmentsAtVenue = Try(assistanceDetailsRoot.get("needsSupportAtVenue").asBoolean().getValue).getOrElse(false)
    val etrayAdjustments = Try(Codecs.fromBson[AdjustmentDetail](assistanceDetailsRoot.get("etray"))).toOption
    val videoInterviewAdjustments = Try(Codecs.fromBson[AdjustmentDetail](assistanceDetailsRoot.get("video"))).toOption

    OnlineTestApplication(
      applicationId, applicationStatus, userId, testAccountId, guaranteedInterview,
      needsAdjustmentsAtVenue, preferredName, lastName,
      etrayAdjustments, videoInterviewAdjustments
    )
  }

  def subDocRoot(key: String)(doc: Document): Option[BsonDocument] = doc.get(key).map(_.asDocument())

  def extract(key: String)(rootOpt: Option[BsonDocument]): Option[String] =
    rootOpt.flatMap ( doc => Try(doc.get(key).asString().getValue).toOption )

  def extractBoolean(key: String)(rootOpt: Option[BsonDocument]): Option[Boolean] =
    rootOpt.flatMap ( doc => Try(doc.get(key).asBoolean().getValue).toOption )

  def getAppId(doc: Document): String = doc.get("applicationId").get.asString().getValue
  def extractAppId(doc: Document): String = extractAppIdOpt(doc).getOrElse("")
  def extractAppIdOpt(doc: Document): Option[String] = doc.get("applicationId").map(_.asString().getValue)

  def getUserId(doc: Document): String = doc.get("userId").get.asString().getValue
  def extractUserId(doc: Document): String = extractUserIdOpt(doc).getOrElse("")
  def extractUserIdOpt(doc: Document): Option[String] = doc.get("userId").map(_.asString().getValue)

  def extractApplicationRoute(doc: Document): ApplicationRoute =
    Codecs.fromBson[ApplicationRoute](doc.get("applicationRoute").getOrElse(ApplicationRoute.Faststream.toBson))

  def extractApplicationStatus(doc: Document) =
    Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get)

  def extractSchemes(doc: Document) = {
    val schemesDocOpt = subDocRoot("scheme-preferences")(doc)
    schemesDocOpt.map( doc => Codecs.fromBson[List[SchemeId]](doc.get("schemes")) )
  }

  def extractCurrentSchemeStatus(doc: Document, schemes: Option[List[SchemeId]]) =
  // You have to pass a BsonValue to fromBson method NOT Option[BsonValue] so we need to provide a default if it is absent
    Codecs.fromBson[List[SchemeEvaluationResult]](doc.get("currentSchemeStatus")
      .getOrElse(Codecs.toBson(schemes.getOrElse(List.empty).map(s => new SchemeEvaluationResult(s, EvaluationResults.Green.toString)))))

  def offsetDateTimeToBson(dateTime: OffsetDateTime): BsonValue = {
    import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat // Needed to handle storing ISODate format
    Codecs.toBson(dateTime)
  }

  //scalastyle:off method.length cyclomatic.complexity
  def getCivilServiceExperienceDetails(applicationRoute: ApplicationRoute,
                                       doc: Document
                                      ): CombinedCivilServiceExperienceDetails = {

    def booleanTranslator(bool: Boolean) = if (bool) "Yes" else "No"

    val csedDocOpt = subDocRoot("civil-service-experience-details")(doc)
    val pdDocOpt = subDocRoot("personal-details")(doc)

    val containsCivilServantAndInternshipType = (internshipType: CivilServantAndInternshipType) =>
      csedDocOpt.flatMap(doc => Try(Codecs.fromBson[List[CivilServantAndInternshipType]](doc.get("civilServantAndInternshipTypes"))).toOption)
        .exists(_.contains(internshipType))

    val civilServant = booleanTranslator(containsCivilServantAndInternshipType(CivilServantAndInternshipType.CivilServant))
    val civilServantDepartmentOpt = extract("civilServantDepartment")(csedDocOpt)
    val edipCompleted = booleanTranslator(containsCivilServantAndInternshipType(CivilServantAndInternshipType.EDIP))
    val sdipCompleted = booleanTranslator(containsCivilServantAndInternshipType(CivilServantAndInternshipType.SDIP))
    val otherInternshipCompleted = booleanTranslator(containsCivilServantAndInternshipType(CivilServantAndInternshipType.OtherInternship))

    val edipYearOpt = extract("edipYear")(csedDocOpt)
    val sdipYearOpt = extract("sdipYear")(csedDocOpt)
    val otherInternshipNameOpt = extract("otherInternshipName")(csedDocOpt)
    val otherInternshipYearOpt = extract("otherInternshipYear")(csedDocOpt)
    val fastPassCertificate = extract("certificateNumber")(csedDocOpt).getOrElse("No")

    val pdEdipCompletedOpt = extractBoolean("edipCompleted")(pdDocOpt)
    val pdEdipYearOpt = extract("edipYear")(pdDocOpt)
    val pdOtherInternshipCompletedOpt = extractBoolean("otherInternshipCompleted")(pdDocOpt)
    val pdOtherInternshipNameOpt = extract("otherInternshipName")(pdDocOpt)
    val pdOtherInternshipYearOpt = extract("otherInternshipYear")(pdDocOpt)

    val edipCompletedColumnOpt = applicationRoute match {
      case ApplicationRoute.Faststream => Some(edipCompleted)
      case ApplicationRoute.SdipFaststream => pdEdipCompletedOpt.map(booleanTranslator)
      case ApplicationRoute.Edip => Some("No")
      case ApplicationRoute.Sdip => pdEdipCompletedOpt.map(booleanTranslator)
      case _ => None
    }

    val edipYearColumnOpt = applicationRoute match {
      case ApplicationRoute.Faststream => edipYearOpt
      case ApplicationRoute.SdipFaststream => pdEdipYearOpt
      case ApplicationRoute.Edip => None
      case ApplicationRoute.Sdip => pdEdipYearOpt
      case _ => None
    }

    val otherInternshipColumnOpt = applicationRoute match {
      case ApplicationRoute.Faststream => Some(otherInternshipCompleted)
      case _ => pdOtherInternshipCompletedOpt.map(booleanTranslator)
    }

    val otherInternshipNameColumnOpt = applicationRoute match {
      case ApplicationRoute.Faststream => otherInternshipNameOpt
      case _ => pdOtherInternshipNameOpt
    }

    val otherInternshipYearColumnOpt = applicationRoute match {
      case ApplicationRoute.Faststream => otherInternshipYearOpt
      case _ => pdOtherInternshipYearOpt
    }

    CombinedCivilServiceExperienceDetails(
      Some(civilServant), civilServantDepartmentOpt, edipCompletedColumnOpt, edipYearColumnOpt, Some(sdipCompleted), sdipYearOpt,
      otherInternshipColumnOpt, otherInternshipNameColumnOpt, otherInternshipYearColumnOpt, Some(fastPassCertificate)
    )
  } //scalastyle:on method.length cyclomatic.complexity
}
