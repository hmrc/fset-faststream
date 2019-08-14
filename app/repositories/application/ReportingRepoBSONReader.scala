/*
 * Copyright 2019 HM Revenue & Customs
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
import _root_.config.PsiTestIds
import connectors.launchpadgateway.exchangeobjects.in.reviewed.ReviewSectionQuestionRequest
import model._
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.{apply => _}
import model.CivilServiceExperienceType.{CivilServiceExperienceType, apply => _}
import model.Commands._
import model.InternshipType.{ InternshipType, apply => _ }
import model.OnlineTestCommands.{ PsiTestResult, TestResult }
import model.command._
import model.persisted._
import model.persisted.phase3tests.LaunchpadTestCallbacks
import model.persisted.sift.SiftTestGroup2
import model.report._
import play.api.Logger
import reactivemongo.bson.{ BSONDocument, _ }
import repositories.{ BaseBSONReader, CommonBSONDocuments }

trait ReportingRepoBSONReader extends CommonBSONDocuments with BaseBSONReader {

  implicit val toCandidateProgressReportItem: BSONDocumentReader[CandidateProgressReportItem] = bsonReader {
    (doc: BSONDocument) => {
      val schemesDoc = doc.getAs[BSONDocument]("scheme-preferences")
      val schemes = schemesDoc.flatMap(_.getAs[List[SchemeId]]("schemes"))

      val adDoc = doc.getAs[BSONDocument]("assistance-details")
      val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
      val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).map(booleanTranslator)
      val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)
      val phoneAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForPhoneInterview")).map(booleanTranslator)
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

      val pdDoc = doc.getAs[BSONDocument]("personal-details")
      val edipCompleted = pdDoc.flatMap(_.getAs[Boolean]("edipCompleted"))

      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val userId = doc.getAs[String]("userId").getOrElse("")
      val applicationRoute = doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
      val progress: ProgressResponse = toProgressResponse(applicationId).read(doc)

      val edipReportColumn = applicationRoute match {
        case ApplicationRoute.Faststream => edip
        case ApplicationRoute.SdipFaststream => edip
        case ApplicationRoute.Edip => None
        case ApplicationRoute.Sdip => edipCompleted.map(eC => if (eC) "Yes" else "No")
        case _ => None
      }

      val fsacIndicatorDoc = doc.getAs[BSONDocument]("fsac-indicator")
      val assessmentCentre = fsacIndicatorDoc.flatMap(_.getAs[String]("assessmentCentre"))

      CandidateProgressReportItem(userId, applicationId, Some(ProgressStatusesReportLabels.progressStatusNameInReports(progress)),
        schemes.getOrElse(Nil), disability, onlineAdjustments, assessmentCentreAdjustments, phoneAdjustments, gis, civilServant,
        fastTrack, edipReportColumn, sdipPrevious, sdip, fastPassCertificate, assessmentCentre, applicationRoute)
    }
  }

  implicit val toApplicationForInternshipReport: BSONDocumentReader[ApplicationForInternshipReport] = bsonReader {
    (doc: BSONDocument) => {
      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val route = doc.getAs[ApplicationRoute.ApplicationRoute]("applicationRoute")
        .getOrElse(throw new Exception(s"Application route not set for $applicationId"))
      val userId = doc.getAs[String]("userId").getOrElse("")
      val progressResponse = toProgressResponse(applicationId).read(doc)

      val psDoc = doc.getAs[BSONDocument]("personal-details")
      val firstName = psDoc.flatMap(_.getAs[String]("firstName"))
      val lastName = psDoc.flatMap(_.getAs[String]("lastName"))
      val preferredName = psDoc.flatMap(_.getAs[String]("preferredName"))

      val adDoc = doc.getAs[BSONDocument]("assistance-details")
      val guaranteedInterviewScheme = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview"))

      val testResults: TestResultsForOnlineTestPassMarkReportItem =
        toTestResultsForOnlineTestPassMarkReportItem(doc, applicationId)
      // TODO: Fix this when updating this report. We now hve list of tests
      val behaviouralTScore = None // testResults.behavioural.flatMap(_.tScore)
      val situationalTScore = None //testResults.situational.flatMap(_.tScore)

      // FDH to only return the statuses relevant to SDIP for an SdipFaststream candidate.
      val modifiedProgressResponse = progressResponse.copy(phase2ProgressResponse = Phase2ProgressResponse(),
        phase3ProgressResponse = Phase3ProgressResponse(),
        // if they've failed SDIP then we don't care if they've been exported for Faststream
        exported = if (progressResponse.phase1ProgressResponse.sdipFSFailed) false else progressResponse.exported,
        updateExported = if (progressResponse.phase1ProgressResponse.sdipFSFailed) false else progressResponse.updateExported
      )

      ApplicationForInternshipReport(
        applicationRoute = route,
        userId = userId,
        progressStatus = Some(ProgressStatusesReportLabels.progressStatusNameInReports(modifiedProgressResponse)),
        firstName = firstName,
        lastName = lastName,
        preferredName = preferredName,
        guaranteedInterviewScheme = guaranteedInterviewScheme,
        behaviouralTScore = behaviouralTScore,
        situationalTScore = situationalTScore
      )
    }
  }

  implicit val toApplicationForAnalyticalSchemesReport: BSONDocumentReader[ApplicationForAnalyticalSchemesReport] = bsonReader {
    (doc: BSONDocument) => {
      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val userId = doc.getAs[String]("userId").getOrElse("")

      val psDoc = doc.getAs[BSONDocument]("personal-details")
      val firstName = psDoc.flatMap(_.getAs[String]("firstName"))
      val lastName = psDoc.flatMap(_.getAs[String]("lastName"))

      val spDoc = doc.getAs[BSONDocument]("scheme-preferences")
      val schemes = spDoc.flatMap(_.getAs[List[SchemeId]]("schemes"))
      val firstSchemePreference = schemes.map(_.head.toString)

      val adDoc = doc.getAs[BSONDocument]("assistance-details")
      val guaranteedInterviewScheme = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview"))

      val testResults: TestResultsForOnlineTestPassMarkReportItem =
        toTestResultsForOnlineTestPassMarkReportItem(doc, applicationId)
      //TODO: Fix this when fixing this report
      val behaviouralTScore = None //testResults.behavioural.flatMap(_.tScore)
      val situationalTScore = None //testResults.situational.flatMap(_.tScore)
      val etrayTScore = None // testResults.etray.flatMap(_.tScore)
      val overallVideoScore = testResults.videoInterview.map(_.overallTotal)

      ApplicationForAnalyticalSchemesReport(
        userId = userId,
        firstName = firstName,
        lastName = lastName,
        firstSchemePreference = firstSchemePreference,
        guaranteedInterviewScheme = guaranteedInterviewScheme,
        behaviouralTScore = behaviouralTScore,
        situationalTScore = situationalTScore,
        etrayTScore = etrayTScore,
        overallVideoScore = overallVideoScore
      )
    }
  }

  implicit val toApplicationForDiversityReport: BSONDocumentReader[ApplicationForDiversityReport] = bsonReader {
    (doc: BSONDocument) => {
      val applicationRoute = doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
      val onlineAdjustmentsKey = if(applicationRoute == ApplicationRoute.Edip) { "needsSupportForPhoneInterview" }
        else if (applicationRoute == ApplicationRoute.Sdip) { "needsSupportForPhoneInterview" }
        else { "needsSupportForOnlineAssessment" }
      val schemesDoc = doc.getAs[BSONDocument]("scheme-preferences")
      val schemes = schemesDoc.flatMap(_.getAs[List[SchemeId]]("schemes"))

      val adDoc = doc.getAs[BSONDocument]("assistance-details")
      val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
      val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean](onlineAdjustmentsKey)).map(booleanTranslator)
      val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)
      val gis = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview"))

      val civilServiceExperienceDoc = doc.getAs[BSONDocument]("civil-service-experience-details")
      val civilServiceExperience = toCivilServiceExperienceDetailsReportItem(civilServiceExperienceDoc)

      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val userId = doc.getAs[String]("userId").getOrElse("")
      val progress: ProgressResponse = toProgressResponse(applicationId).read(doc)

      val curSchemeStatus = doc.getAs[List[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(
        schemes.getOrElse(List.empty).map(s => new SchemeEvaluationResult(s, EvaluationResults.Green.toString)))

      ApplicationForDiversityReport(applicationId, userId, applicationRoute,
        Some(ProgressStatusesReportLabels.progressStatusNameInReports(progress)),
        schemes.getOrElse(List.empty), disability, gis, onlineAdjustments,
        assessmentCentreAdjustments, civilServiceExperience, curSchemeStatus)
    }
  }

  implicit val toApplicationForOnlineTestPassMarkReport: BSONDocumentReader[ApplicationForOnlineTestPassMarkReport] = bsonReader {
    (doc: BSONDocument) => {
      val userId = doc.getAs[String]("userId").getOrElse("")
      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val applicationRoute = doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
      val schemesDoc = doc.getAs[BSONDocument]("scheme-preferences")
      val schemes = schemesDoc.flatMap(_.getAs[List[SchemeId]]("schemes"))

      val adDoc = doc.getAs[BSONDocument]("assistance-details")
      val gis = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview"))
      val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
      val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).map(booleanTranslator)
      val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)
      val curSchemeStatus = doc.getAs[List[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(
        schemes.getOrElse(List.empty).map(s => new SchemeEvaluationResult(s, EvaluationResults.Green.toString)))

      val progress: ProgressResponse = toProgressResponse(applicationId).read(doc)

      ApplicationForOnlineTestPassMarkReport(
        userId,
        applicationId,
        ProgressStatusesReportLabels.progressStatusNameInReports(progress),
        applicationRoute,
        schemes.getOrElse(Nil),
        disability,
        gis,
        onlineAdjustments,
        assessmentCentreAdjustments,
        toTestResultsForOnlineTestPassMarkReportItem(doc, applicationId),
        curSchemeStatus)
    }
  }

  implicit val toApplicationForNumericTestExtractReport: BSONDocumentReader[ApplicationForNumericTestExtractReport] = bsonReader {
    (doc: BSONDocument) => {
      val userId = doc.getAs[String]("userId").getOrElse("")
      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val applicationRoute = doc.getAs[ApplicationRoute]("applicationRoute").getOrElse(ApplicationRoute.Faststream)
      val schemesDoc = doc.getAs[BSONDocument]("scheme-preferences")
      val schemes = schemesDoc.flatMap(_.getAs[List[SchemeId]]("schemes"))

      val personalDetails = doc.getAs[PersonalDetails]("personal-details").getOrElse(
        throw new Exception(s"Error parsing personal details for $userId")
      )

      val adDoc = doc.getAs[BSONDocument]("assistance-details")
      val gis = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview"))
      val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
      val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).map(booleanTranslator)
      val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)

      val currentSchemeStatus = doc.getAs[List[SchemeEvaluationResult]]("currentSchemeStatus").getOrElse(
        throw new Exception(s"Error parsing current scheme status for $userId")
      )

      val progress: ProgressResponse = toProgressResponse(applicationId).read(doc)

      ApplicationForNumericTestExtractReport(
        userId,
        applicationId,
        applicationRoute,
        personalDetails.firstName,
        personalDetails.lastName,
        personalDetails.preferredName,
        ProgressStatusesReportLabels.progressStatusNameInReports(progress),
        schemes.getOrElse(Nil),
        disability,
        gis,
        onlineAdjustments,
        assessmentCentreAdjustments,
        toTestResultsForOnlineTestPassMarkReportItem(doc, applicationId),
        currentSchemeStatus
      )
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

  private[application] def toTestResultsForOnlineTestPassMarkReportItem(
    appDoc: BSONDocument, applicationId: String): TestResultsForOnlineTestPassMarkReportItem = {

    val testGroupsDoc = appDoc.getAs[BSONDocument]("testGroups")
    val p1Tests = toPhase1TestResults(testGroupsDoc)
    val p2Tests = toPhase2TestResults(testGroupsDoc)
    val videoInterviewResults = toPhase3TestResults(testGroupsDoc)
    val siftTestResults = toSiftTestResults(applicationId, testGroupsDoc)

    TestResultsForOnlineTestPassMarkReportItem(
      p1Tests,
      p2Tests,
      videoInterviewResults,
      siftTestResults,
      None, None, None, None)
  }

  private[application] def toPhase1TestResults(testGroupsDoc: Option[BSONDocument]): Seq[Option[PsiTestResult]] = {
    testGroupsDoc.flatMap(_.getAs[BSONDocument](Phase.PHASE1)).map { phase1Doc =>
      val phase1TestProfile = Phase1TestProfile2.bsonHandler.read(phase1Doc)
      val p1TestIds = testIntegrationGatewayConfig.phase1Tests.tests.valuesIterator.toSeq
      toPhaseXTestResults(phase1TestProfile.activeTests, p1TestIds)
    }.getOrElse(Seq.fill(4)(None))
  }

  private[application] def toPhase2TestResults(testGroupsDoc: Option[BSONDocument]): Seq[Option[PsiTestResult]] = {
    testGroupsDoc.flatMap(_.getAs[BSONDocument](Phase.PHASE2)).map { phase2Doc =>
      val phase2TestProfile = Phase2TestGroup2.bsonHandler.read(phase2Doc)
      val p2TestIds = testIntegrationGatewayConfig.phase2Tests.tests.valuesIterator.toSeq
      toPhaseXTestResults(phase2TestProfile.activeTests, p2TestIds)
    }.getOrElse(Seq.fill(2)(None))
  }

  private[application] def toPhaseXTestResults(activeTests: Seq[PsiTest],
                                               allTestIds: Seq[PsiTestIds]): Seq[Option[PsiTestResult]] = {
      def getTestResult(inventoryId: String): Option[PsiTestResult] = {
        activeTests.find(_.inventoryId == inventoryId).flatMap { psiTest =>
          // TODO: What is status?
          psiTest.testResult.map { tr => PsiTestResult(status = "", tScore = tr.tScore, raw = tr.rawScore) }
        }
      }
    allTestIds.map{ testIds => getTestResult(testIds.inventoryId) }
  }

  private[application] def toPhase3TestResults(testGroupsDoc: Option[BSONDocument]): Option[VideoInterviewTestResult] = {
    val callsbacksDocOpt = testGroupsDoc.flatMap(_.getAs[BSONDocument](Phase.PHASE3))
      .flatMap(_.getAs[BSONArray]("tests")).flatMap(_.getAs[BSONDocument](0))
      .flatMap(_.getAs[BSONDocument]("callbacks"))

    val callsbacksOpt = callsbacksDocOpt.map(LaunchpadTestCallbacks.bsonHandler.read)
    val latestReviewedOpt = callsbacksOpt.flatMap(_.getLatestReviewed)

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
        latestReviewed.calculateTotalScore()
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

  private[application] def toSiftTestResults(applicationId: String,
                                             testGroupsDoc: Option[BSONDocument]): Option[PsiTestResult] = {
    val siftDocOpt = testGroupsDoc.flatMap(_.getAs[BSONDocument]("SIFT_PHASE"))
    siftDocOpt.flatMap { siftDoc =>
      val siftTestProfile = SiftTestGroup2.bsonHandler.read(siftDoc)
      siftTestProfile.activeTests.size match {
        case 1 => siftTestProfile.activeTests.head.testResult.map { tr => PsiTestResult("", tr.tScore, tr.rawScore) }
        case 0 => None
        case s if s > 1 =>
          Logger.error(s"There are $s active sift tests which is invalid for application id [$applicationId]")
          None
      }
    }
  }
}
