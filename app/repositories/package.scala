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

import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.CivilServantAndInternshipType.CivilServantAndInternshipType
import model.OnlineTestCommands.OnlineTestApplication
import model._
import model.persisted.SchemeEvaluationResult
import org.joda.time.DateTime
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{BsonDocument, BsonValue}
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.Codecs

import scala.util.Try
//import reactivemongo.bson._

import scala.language.postfixOps

package object repositories {
  val insertIfNoRecordFound = true

/*
  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
    def read(bdtime: BSONDateTime) = new DateTime(bdtime.value, DateTimeZone.UTC)
    def write(jdtime: DateTime) = BSONDateTime(jdtime.getMillis)
  }*/

/*
  implicit object BSONLocalDateHandler extends BSONHandler[BSONString, LocalDate] {
    def read(time: BSONString) = LocalDate.parse(time.value)
    def write(jdtime: LocalDate) = BSONString(jdtime.toString("yyyy-MM-dd"))
  }*/

  /*
    val dobString = extract("dateOfBirth")(personalDetailsDocOpt).get
    val dob = LocalDate.parse(dobString)
   */

  // FSET specific version that differs from the HMRC MongoJodaFormats because we store the LocalDate as a Bson String
  // so we deal with that format here
//  private final val localDateReads: Reads[LocalDate] =
//    Reads.at[String](__).map( date => new LocalDate(date, DateTimeZone.UTC))

//  private final val localDateWrites: Writes[LocalDate] =
//    Writes.at[String](__).contramap[LocalDate](oo => oo.toString("yyyy-MM-dd"))

//  final implicit val localDateFormat: Format[LocalDate] =
//    Format(localDateReads, localDateWrites)

/*
  implicit object BSONLocalTimeHandler extends BSONHandler[BSONString, LocalTime] {
    def read(time: BSONString) = LocalTime.parse(time.value)
    def write(jdtime: LocalTime) = BSONString(jdtime.toString("HH:mm"))
  }*/

/*
  implicit object BSONMapStringIntHandler extends BSONHandler[BSONDocument, Map[String, Int]] {
    override def write(map: Map[String, Int]): BSONDocument = {
      val elements = map.toStream.map { case (key, value) =>
        key -> BSONInteger(value)
      }
      BSONDocument(elements)
    }

    override def read(bson: BSONDocument): Map[String, Int] = {
      val elements = bson.elements.map { tuple =>
        // assume that all values in the document are BSONDocuments
        tuple.name -> tuple.value.seeAsTry[Int].get
      }
      elements.toMap
    }
  }*/

/*
  implicit object BSONMapStringStringHandler extends BSONHandler[BSONDocument, Map[String, String]] {
    override def write(map: Map[String, String]): BSONDocument = {
      val elements = map.toStream.map { case (key, value ) =>
        key -> BSONString(value)
      }
      BSONDocument(elements)
    }

    override def read(bson: BSONDocument): Map[String, String] = {
      val elements = bson.elements.map { tuple =>
        // assume that all values in the document are BSONDocuments
        tuple.name -> tuple.value.seeAsTry[String].get
      }
      elements.toMap
    }
  }*/

/*
  implicit object BSONMapOfListOfLocalDateHandler extends BSONHandler[BSONDocument, Map[String, List[LocalDate]]] {
    import Producer._

    override def write(map: Map[String, List[LocalDate]]): BSONDocument = {
      val elements = map.map {
        case (key, value) =>
          element2Producer(key -> value)
      }.toSeq

      BSONDocument(elements:_*)
    }

    override def read(bson: BSONDocument): Map[String, List[LocalDate]] = {
      val elements = bson.elements.map { bsonElement =>
        bsonElement.name -> bsonElement.value.seeAsTry[List[LocalDate]].get
      }
      elements.toMap
    }
  }*/

  implicit object OFormatHelper {
    def oFormat[T](implicit format:Format[T]) : OFormat[T] = {
      val oFormat: OFormat[T] = new OFormat[T](){
        override def writes(o: T): JsObject = format.writes(o).as[JsObject]
        override def reads(json: JsValue): JsResult[T] = format.reads(json)
      }
      oFormat
    }
  }

/*
  implicit val withdrawHandler: BSONHandler[BSONDocument, WithdrawApplication] = Macros.handler[WithdrawApplication]
  implicit val cdHandler: BSONHandler[BSONDocument, ContactDetails] = Macros.handler[ContactDetails]
  implicit val assistanceDetailsHandler: BSONHandler[BSONDocument, AssistanceDetails] = Macros.handler[AssistanceDetails]
  implicit val answerHandler: BSONHandler[BSONDocument, QuestionnaireAnswer] = Macros.handler[QuestionnaireAnswer]
  implicit val workingTogetherDevelopingSelfAndOthersScoresHandler
  : BSONHandler[BSONDocument, WorkingTogetherDevelopingSelfAndOtherScores] =
    Macros.handler[WorkingTogetherDevelopingSelfAndOtherScores]
  implicit val makingEffectiveDecisionsScoresHandler: BSONHandler[BSONDocument, MakingEffectiveDecisionsScores] =
    Macros.handler[MakingEffectiveDecisionsScores]
  implicit val communicatingAndInfluencingScoresHandler: BSONHandler[BSONDocument, CommunicatingAndInfluencingScores] =
    Macros.handler[CommunicatingAndInfluencingScores]
  implicit val seeingTheBigPictureScoresHandler: BSONHandler[BSONDocument, SeeingTheBigPictureScores] =
    Macros.handler[SeeingTheBigPictureScores]
  implicit val competencyAverageResultHandler: BSONHandler[BSONDocument, CompetencyAverageResult] =
    Macros.handler[CompetencyAverageResult]
  implicit val flagCandidateHandler: BSONHandler[BSONDocument, FlagCandidate] = Macros.handler[FlagCandidate]
  implicit val adjustmentDetailHandler: BSONHandler[BSONDocument, AdjustmentDetail] = Macros.handler[AdjustmentDetail]
 */

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
    val needsAdjustmentForOnlineTests = Try(assistanceDetailsRoot.get("needsSupportForOnlineAssessment").asBoolean().getValue).getOrElse(false)
    val needsAdjustmentsAtVenue = Try(assistanceDetailsRoot.get("needsSupportAtVenue").asBoolean().getValue).getOrElse(false)
    val etrayAdjustments = Try(Codecs.fromBson[AdjustmentDetail](assistanceDetailsRoot.get("etray"))).toOption
    val videoInterviewAdjustments = Try(Codecs.fromBson[AdjustmentDetail](assistanceDetailsRoot.get("video"))).toOption

    OnlineTestApplication(
      applicationId, applicationStatus, userId, testAccountId, guaranteedInterview,
      needsAdjustmentForOnlineTests, needsAdjustmentsAtVenue, preferredName, lastName,
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

  def dateTimeToBson(dateTime: DateTime): BsonValue = {
    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits.jotDateTimeFormat // Needed to handle storing ISODate format
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
      Some(civilServant), edipCompletedColumnOpt, edipYearColumnOpt, Some(sdipCompleted), sdipYearOpt,
      otherInternshipColumnOpt, otherInternshipNameColumnOpt, otherInternshipYearColumnOpt, Some(fastPassCertificate)
    )
  } //scalastyle:on method.length cyclomatic.complexity
}
