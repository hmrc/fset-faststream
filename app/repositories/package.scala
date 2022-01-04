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
import model.CivilServantAndInternshipType.CivilServantAndInternshipType
import model.EvaluationResults._
import model.FlagCandidatePersistedObject.FlagCandidate
import model.OnlineTestCommands.OnlineTestApplication
import model.assessmentscores._
import model.command.WithdrawApplication
import model.persisted.{ AssistanceDetails, ContactDetails, QuestionnaireAnswer }
import model.{ AdjustmentDetail, ApplicationRoute, CivilServantAndInternshipType }
import org.joda.time.{ DateTime, DateTimeZone, LocalDate, LocalTime }
import play.api.libs.json._
import reactivemongo.bson._

import scala.language.postfixOps

package object repositories {

  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
    def read(bdtime: BSONDateTime) = new DateTime(bdtime.value, DateTimeZone.UTC)
    def write(jdtime: DateTime) = BSONDateTime(jdtime.getMillis)
  }

  implicit object BSONLocalDateHandler extends BSONHandler[BSONString, LocalDate] {
    def read(time: BSONString) = LocalDate.parse(time.value)
    def write(jdtime: LocalDate) = BSONString(jdtime.toString("yyyy-MM-dd"))
  }

  implicit object BSONLocalTimeHandler extends BSONHandler[BSONString, LocalTime] {
    def read(time: BSONString) = LocalTime.parse(time.value)
    def write(jdtime: LocalTime) = BSONString(jdtime.toString("HH:mm"))
  }

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
  }

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
  }

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
  }

  implicit object OFormatHelper {
    def oFormat[T](implicit format:Format[T]) : OFormat[T] = {
      val oFormat: OFormat[T] = new OFormat[T](){
        override def writes(o: T): JsObject = format.writes(o).as[JsObject]
        override def reads(json: JsValue): JsResult[T] = format.reads(json)
      }
      oFormat
    }
  }

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

  def bsonDocToOnlineTestApplication(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val applicationStatus = doc.getAs[String]("applicationStatus").get
    val userId = doc.getAs[String]("userId").get
    val testAccountId = doc.getAs[String]("testAccountId").get

    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    val lastName = personalDetailsRoot.getAs[String]("lastName").get

    val assistanceDetailsRoot = doc.getAs[BSONDocument]("assistance-details").get
    val guaranteedInterview = assistanceDetailsRoot.getAs[Boolean]("guaranteedInterview").getOrElse(false)
    val needsAdjustmentForOnlineTests = assistanceDetailsRoot.getAs[Boolean]("needsSupportForOnlineAssessment").getOrElse(false)
    val needsAdjustmentsAtVenue = assistanceDetailsRoot.getAs[Boolean]("needsSupportAtVenue").getOrElse(false)

    val etrayAdjustments = assistanceDetailsRoot.getAs[AdjustmentDetail]("etray")
    val videoInterviewAdjustments = assistanceDetailsRoot.getAs[AdjustmentDetail]("video")

    OnlineTestApplication(
      applicationId, applicationStatus, userId, testAccountId, guaranteedInterview,
      needsAdjustmentForOnlineTests, needsAdjustmentsAtVenue, preferredName, lastName,
      etrayAdjustments, videoInterviewAdjustments
    )
  }

  //scalastyle:off method.length cyclomatic.complexity
  def getCivilServiceExperienceDetails(applicationRoute: ApplicationRoute,
                                       doc: BSONDocument
                                   ): CombinedCivilServiceExperienceDetails = {

    def booleanTranslator(bool: Boolean) = if (bool) "Yes" else "No"

    val csedDocOpt = doc.getAs[BSONDocument]("civil-service-experience-details")
    val pdDocOpt = doc.getAs[BSONDocument]("personal-details")

    val civilServantAndInternshipType = (civilServantAndInternshipType: CivilServantAndInternshipType) =>
      csedDocOpt.flatMap(_.getAs[List[CivilServantAndInternshipType]]("civilServantAndInternshipTypes"))
        .getOrElse(List.empty[CivilServantAndInternshipType]).contains(civilServantAndInternshipType)

    val civilServant = booleanTranslator(civilServantAndInternshipType(CivilServantAndInternshipType.CivilServant))
    val edipCompleted = booleanTranslator(civilServantAndInternshipType(CivilServantAndInternshipType.EDIP))
    val sdipCompleted = booleanTranslator(civilServantAndInternshipType(CivilServantAndInternshipType.SDIP))
    val otherInternshipCompleted = booleanTranslator(civilServantAndInternshipType(CivilServantAndInternshipType.OtherInternship))

    val edipYearOpt = csedDocOpt.flatMap(_.getAs[String]("edipYear"))
    val sdipYearOpt = csedDocOpt.flatMap(_.getAs[String]("sdipYear"))
    val otherInternshipNameOpt = csedDocOpt.flatMap(_.getAs[String]("otherInternshipName"))
    val otherInternshipYearOpt = csedDocOpt.flatMap(_.getAs[String]("otherInternshipYear"))
    val fastPassCertificate = csedDocOpt.flatMap(_.getAs[String]("certificateNumber")).getOrElse("No")

    val pdEdipCompletedOpt = pdDocOpt.flatMap(_.getAs[Boolean]("edipCompleted"))
    val pdEdipYearOpt = pdDocOpt.flatMap(_.getAs[String]("edipYear"))
    val pdOtherInternshipCompletedOpt = pdDocOpt.flatMap(_.getAs[Boolean]("otherInternshipCompleted"))
    val pdOtherInternshipNameOpt = pdDocOpt.flatMap(_.getAs[String]("otherInternshipName"))
    val pdOtherInternshipYearOpt = pdDocOpt.flatMap(_.getAs[String]("otherInternshipYear"))

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
