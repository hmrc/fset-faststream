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

package repositories.application

import config.MicroserviceAppConfig._
import connectors.launchpadgateway.exchangeobjects.in.reviewed.{ ReviewSectionQuestionRequest, ReviewedCallbackRequest }
import model.ApplicationStatus.{ apply => _ }
import model.CivilServiceExperienceType.{ CivilServiceExperienceType, apply => _, _ }
import model.Commands._
import model.InternshipType.{ InternshipType, apply => _ }
import model.OnlineTestCommands.TestResult
import model.SchemeType._
import model.command._
import model.persisted._
import model.report._
import model.{ CivilServiceExperienceType, InternshipType, Phase }
import play.api.Logger
import reactivemongo.bson.{ BSONDocument, _ }
import repositories.{ BSONHelpers, BaseBSONReader }

trait ReportingRepoBSONReader extends BaseBSONReader {

  implicit val toReportWithPersonalDetails = bsonReader {
    (doc: BSONDocument) => {
      val fr = doc.getAs[BSONDocument]("framework-preferences")
      val fr1 = fr.flatMap(_.getAs[BSONDocument]("firstLocation"))
      val fr2 = fr.flatMap(_.getAs[BSONDocument]("secondLocation"))

      def frLocation(root: Option[BSONDocument]) = extract("location")(root)
      def frScheme1(root: Option[BSONDocument]) = extract("firstFramework")(root)
      def frScheme2(root: Option[BSONDocument]) = extract("secondFramework")(root)

      val personalDetails = doc.getAs[BSONDocument]("personal-details")
      val aLevel = personalDetails.flatMap(_.getAs[Boolean]("aLevel").map(booleanTranslator))
      val stemLevel = personalDetails.flatMap(_.getAs[Boolean]("stemLevel").map(booleanTranslator))
      val firstName = personalDetails.flatMap(_.getAs[String]("firstName"))
      val lastName = personalDetails.flatMap(_.getAs[String]("lastName"))
      val preferredName = personalDetails.flatMap(_.getAs[String]("preferredName"))
      val dateOfBirth = personalDetails.flatMap(_.getAs[String]("dateOfBirth"))

      val fpAlternatives = fr.flatMap(_.getAs[BSONDocument]("alternatives"))
      val location = fpAlternatives.flatMap(_.getAs[Boolean]("location").map(booleanTranslator))
      val framework = fpAlternatives.flatMap(_.getAs[Boolean]("framework").map(booleanTranslator))

      val ad = doc.getAs[BSONDocument]("assistance-details")
      val needsAssistance = ad.flatMap(_.getAs[String]("needsAssistance"))
      val needsAdjustment = ad.flatMap(_.getAs[String]("needsAdjustment"))
      val guaranteedInterview = ad.flatMap(_.getAs[String]("guaranteedInterview"))

      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val userId = doc.getAs[String]("userId").getOrElse("")
      val progress: ProgressResponse = toProgressResponse(applicationId).read(doc)

      val onlineTests = doc.getAs[BSONDocument]("online-tests")
      val cubiksUserId = onlineTests.flatMap(_.getAs[Int]("cubiksUserId"))

      ReportWithPersonalDetails(
        applicationId, userId, Some(ProgressStatusesReportLabels.progressStatusNameInReports(progress)),
        frLocation(fr1), frScheme1(fr1), frScheme2(fr1),
        frLocation(fr2), frScheme1(fr2), frScheme2(fr2), aLevel,
        stemLevel, location, framework, needsAssistance, needsAdjustment, guaranteedInterview, firstName, lastName,
        preferredName, dateOfBirth, cubiksUserId
      )
    }
  }

  implicit val toCandidateProgressReportItem = bsonReader {
    (doc: BSONDocument) => {
      val schemesDoc = doc.getAs[BSONDocument]("scheme-preferences")
      val schemes = schemesDoc.flatMap(_.getAs[List[SchemeType]]("schemes"))

      val adDoc = doc.getAs[BSONDocument]("assistance-details")
      val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
      val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).map(booleanTranslator)
      val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)
      val gis = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview")).map(booleanTranslator)

      val fpDoc = doc.getAs[BSONDocument]("civil-service-experience-details")
      val civilServiceExperienceType = (fpType: CivilServiceExperienceType) => fpDoc.map(
        _.getAs[CivilServiceExperienceType]("civilServiceExperienceType").contains(fpType)
      )
      val internshipTypes = (internshipType: InternshipType) =>
        fpDoc.map(_.getAs[List[InternshipType]]("internshipTypes").getOrElse(List.empty[InternshipType]).contains(internshipType))

      val civilServant = civilServiceExperienceType(CivilServiceExperienceType.CivilServant).map(booleanTranslator)
      val fastTrack = civilServiceExperienceType(CivilServiceExperienceType.CivilServantViaFastTrack).map(booleanTranslator)
      val edip = internshipTypes(InternshipType.EDIP).map(booleanTranslator)
      val sdipPrevious = internshipTypes(InternshipType.SDIPPreviousYear).map(booleanTranslator)
      val sdip = internshipTypes(InternshipType.SDIPCurrentYear).map(booleanTranslator)
      val fastPassCertificate = fpDoc.map(_.getAs[String]("certificateNumber").getOrElse("No"))

      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val progress: ProgressResponse = toProgressResponse(applicationId).read(doc)

      CandidateProgressReportItem(applicationId, Some(ProgressStatusesReportLabels.progressStatusNameInReports(progress)),
        schemes.getOrElse(Nil), disability, onlineAdjustments, assessmentCentreAdjustments, gis, civilServant,
        fastTrack, edip, sdipPrevious, sdip, fastPassCertificate)
    }
  }

  implicit val toApplicationForDiversityReport = bsonReader {
    (doc: BSONDocument) => {
      val schemesDoc = doc.getAs[BSONDocument]("scheme-preferences")
      val schemes = schemesDoc.flatMap(_.getAs[List[SchemeType]]("schemes"))

      val adDoc = doc.getAs[BSONDocument]("assistance-details")
      val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
      val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).map(booleanTranslator)
      val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)
      val gis = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview"))

      val civilServiceExperienceDoc = doc.getAs[BSONDocument]("civil-service-experience-details")
      val civilServiceExperience = toCivilServiceExperienceDetailsReportItem(civilServiceExperienceDoc)

      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val userId = doc.getAs[String]("userId").getOrElse("")
      val progress: ProgressResponse = toProgressResponse(applicationId).read(doc)

      ApplicationForDiversityReport(applicationId, userId, Some(ProgressStatusesReportLabels.progressStatusNameInReports(progress)),
        schemes.getOrElse(List.empty), disability, gis, onlineAdjustments,
        assessmentCentreAdjustments, civilServiceExperience)
    }
  }

  implicit val toApplicationForOnlineTestPassMarkReport = bsonReader {
    (doc: BSONDocument) => {
      val applicationId = doc.getAs[String]("applicationId").getOrElse("")

      val schemesDoc = doc.getAs[BSONDocument]("scheme-preferences")
      val schemes = schemesDoc.flatMap(_.getAs[List[SchemeType]]("schemes"))

      val adDoc = doc.getAs[BSONDocument]("assistance-details")
      val gis = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview"))
      val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
      val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).map(booleanTranslator)
      val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)

      val progress: ProgressResponse = toProgressResponse(applicationId).read(doc)

      ApplicationForOnlineTestPassMarkReport(
        applicationId,
        ProgressStatusesReportLabels.progressStatusNameInReports(progress),
        schemes.getOrElse(Nil),
        disability,
        gis,
        onlineAdjustments,
        assessmentCentreAdjustments,
        toTestResultsForOnlineTestPassMarkReportItem(doc, applicationId))
    }
  }

  private[application] def toCivilServiceExperienceDetailsReportItem(optDoc: Option[BSONDocument]):
  Option[CivilServiceExperienceDetailsForDiversityReport] = {
    optDoc.map { doc =>
      val civilServiceExperienceType = (fpType: CivilServiceExperienceType) =>
        doc.getAs[CivilServiceExperienceType]("civilServiceExperienceType").contains(fpType)
      val internshipTypes = (internshipType: InternshipType) =>
        doc.getAs[List[InternshipType]]("internshipTypes").getOrElse(List.empty[InternshipType]).contains(internshipType)
      val civilServant = booleanTranslator(civilServiceExperienceType(CivilServiceExperienceType.CivilServant))
      val fastTrack = booleanTranslator(civilServiceExperienceType(CivilServiceExperienceType.CivilServantViaFastTrack))
      val edip = booleanTranslator(internshipTypes(InternshipType.EDIP))
      val sdipPrevious = booleanTranslator(internshipTypes(InternshipType.SDIPPreviousYear))
      val sdip = booleanTranslator(internshipTypes(InternshipType.SDIPCurrentYear))
      val fastPassCertificate = doc.getAs[String]("certificateNumber").getOrElse("No")
      CivilServiceExperienceDetailsForDiversityReport(Some(civilServant), Some(fastTrack), Some(edip), Some(sdipPrevious),
        Some(sdip), Some(fastPassCertificate))
    }
  }

  private[application] def toTestResultsForOnlineTestPassMarkReportItem(doc: BSONDocument, applicationId: String):
  TestResultsForOnlineTestPassMarkReportItem = {

    val testGroupsDoc = doc.getAs[BSONDocument]("testGroups")
    val (behaviouralTestResult, situationalTestResult) = toPhase1TestResults(testGroupsDoc)
    val etrayTestResult = toPhase2TestResults(applicationId, testGroupsDoc)
    val videoInterviewResults = toPhase3TestResults(testGroupsDoc)

    TestResultsForOnlineTestPassMarkReportItem(behaviouralTestResult, situationalTestResult, etrayTestResult, videoInterviewResults)
  }

  private[application] def toPhase1TestResults(testGroupsDoc: Option[BSONDocument]) = {
    val phase1Doc = testGroupsDoc.flatMap(_.getAs[BSONDocument](Phase.PHASE1))
    val phase1TestProfile = Phase1TestProfile.bsonHandler.read(phase1Doc.get)

    val situationalScheduleId = cubiksGatewayConfig.phase1Tests.scheduleIds("sjq")
    val behaviouralScheduleId = cubiksGatewayConfig.phase1Tests.scheduleIds("bq")

    def getTestResult(phase1TestProfile: Phase1TestProfile, scheduleId: Int) = {
      phase1TestProfile.activeTests.find(_.scheduleId == scheduleId).flatMap { phase1Test =>
        phase1Test.testResult.map { tr => toTestResult(tr) }
      }
    }
    (getTestResult(phase1TestProfile, behaviouralScheduleId), getTestResult(phase1TestProfile, situationalScheduleId))
  }

  private[application] def toPhase2TestResults(applicationId: String, testGroupsDoc: Option[BSONDocument]) = {
    val phase2DocOpt = testGroupsDoc.flatMap(_.getAs[BSONDocument](Phase.PHASE2))
    phase2DocOpt.flatMap { phase2Doc =>
      val phase2TestProfile = Phase2TestGroup.bsonHandler.read(phase2Doc)
      phase2TestProfile.activeTests.size match {
        case 1 => phase2TestProfile.activeTests.head.testResult.map { tr => toTestResult(tr) }
        case 0 => None
        case s if s > 1 =>
          Logger.error(s"There are $s active tests which is invalid for application id [$applicationId]")
          None
      }
    }
  }

  private[application] def toPhase3TestResults(testGroupsDoc: Option[BSONDocument]): Option[VideoInterviewTestResult] = {
    val reviewedDocOpt = testGroupsDoc.flatMap(_.getAs[BSONDocument](Phase.PHASE3))
      .flatMap(_.getAs[BSONArray]("tests")).flatMap(_.getAs[BSONDocument](0))
      .flatMap(_.getAs[BSONDocument]("callbacks")).flatMap(_.getAs[List[BSONDocument]]("reviewed"))

    val reviewed = reviewedDocOpt.map (_.map(ReviewedCallbackRequest.bsonHandler.read(_)))
    val latestReviewedOpt = reviewed.map ( _.sortWith { (r1, r2) => r1.received.isAfter(r2.received)}).flatMap(_.headOption)

    latestReviewedOpt.map { latestReviewed =>
      VideoInterviewTestResult(
        toVideoInterviewQuestionTestResult(latestReviewed.latestReviewer.question1),
        toVideoInterviewQuestionTestResult(latestReviewed.latestReviewer.question2),
        toVideoInterviewQuestionTestResult(latestReviewed.latestReviewer.question3),
        toVideoInterviewQuestionTestResult(latestReviewed.latestReviewer.question4),
        toVideoInterviewQuestionTestResult(latestReviewed.latestReviewer.question5),
        toVideoInterviewQuestionTestResult(latestReviewed.latestReviewer.question6),
        toVideoInterviewQuestionTestResult(latestReviewed.latestReviewer.question7),
        toVideoInterviewQuestionTestResult(latestReviewed.latestReviewer.question8),
        latestReviewed.calculateTotalScore
      )
    }
  }

  private[this] def toVideoInterviewQuestionTestResult(question: ReviewSectionQuestionRequest) = {
    VideoInterviewQuestionTestResult(
      question.reviewCriteria1.score,
      question.reviewCriteria2.score)
  }

  private[this] def toTestResult(tr: model.persisted.TestResult) = {
    TestResult(status = tr.status, norm = tr.norm, tScore = tr.tScore, raw = tr.raw, percentile = tr.percentile, sten = tr.sten)
  }

  private def extract(key: String)(root: Option[BSONDocument]): Option[String] = root.flatMap(_.getAs[String](key))
}
