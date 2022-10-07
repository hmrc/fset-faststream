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

package repositories.application

import config.{MicroserviceAppConfig, PsiTestIds}
import connectors.launchpadgateway.exchangeobjects.in.reviewed.{ReviewSectionQuestionRequest, ReviewedCallbackRequest}
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.{apply => _}
import model.CivilServantAndInternshipType.CivilServantAndInternshipType
import model.OnlineTestCommands.PsiTestResult
import model.Phase.Phase
import model._
import model.command._
import model.persisted._
import model.persisted.sift.SiftTestGroup
import model.report._
import org.slf4j.{Logger, LoggerFactory}
import repositories.{ BaseBSONReader, CommonBSONDocuments }
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{BsonDocument, BsonValue}
import repositories._
import uk.gov.hmrc.mongo.play.json.Codecs
import scala.util.Try

trait ReportingRepoBSONReader extends CommonBSONDocuments with BaseBSONReader {

  //scalastyle:off method.length
  private[application] def toCandidateProgressReportItem(doc: Document) = {
    val schemesDocOpt = doc.get("scheme-preferences").map(_.asDocument())
    val schemes: Option[List[SchemeId]] = schemesDocOpt.map(doc => Codecs.fromBson[List[SchemeId]](doc.get("schemes")))

    val adDocOpt = doc.get("assistance-details").map(_.asDocument())
    val disability = adDocOpt.flatMap( doc => Try(doc.get("hasDisability").asString().getValue).toOption )
    val onlineAdjustments = adDocOpt.flatMap( doc =>
      Try(doc.get("needsSupportForOnlineAssessment").asBoolean().getValue).map(booleanTranslator).toOption
    )
    val assessmentCentreAdjustments = adDocOpt.flatMap( doc =>
      Try(doc.get("needsSupportAtVenue").asBoolean().getValue).map(booleanTranslator).toOption
    )
    val phoneAdjustments = adDocOpt.flatMap( doc =>
      Try(doc.get("needsSupportForPhoneInterview").asBoolean().getValue).map(booleanTranslator).toOption
    )

    val gis = adDocOpt.flatMap( doc => Try(doc.get("guaranteedInterview").asBoolean().getValue).map(booleanTranslator).toOption )
    val csedDocOpt = doc.get("civil-service-experience-details").map(_.asDocument())

    val containsCivilServantAndInternshipType = (internshipType: CivilServantAndInternshipType) =>
      csedDocOpt.flatMap(doc => Try(Codecs.fromBson[List[CivilServantAndInternshipType]](doc.get("civilServantAndInternshipTypes"))).toOption)
        .map ( _.contains(internshipType) )

    val csedCivilServant = containsCivilServantAndInternshipType(CivilServantAndInternshipType.CivilServant).map(booleanTranslator)
    val csedEdipCompleted = containsCivilServantAndInternshipType(CivilServantAndInternshipType.EDIP).map(booleanTranslator)
    val csedSdip = containsCivilServantAndInternshipType(CivilServantAndInternshipType.SDIP).map(booleanTranslator)
    val csedOtherInternshipCompleted = containsCivilServantAndInternshipType(CivilServantAndInternshipType.OtherInternship)
      .map(booleanTranslator)

    val fastPassCertificate = csedDocOpt.map{ doc => Try(doc.get("certificateNumber").asString().getValue).toOption.getOrElse("No") }

    val pdDocOpt = doc.get("personal-details").map(_.asDocument())
    val edipCompleted = pdDocOpt.flatMap( doc => Try(doc.get("edipCompleted").asBoolean().getValue).toOption)
    val otherInternshipCompleted = pdDocOpt.flatMap{ doc =>
      Try(doc.get("otherInternshipCompleted").asBoolean().getValue).toOption
    }.map(booleanTranslator)

    val applicationId = extractAppIdOpt(doc).getOrElse("")
    val userId = extractUserId(doc)
    val applicationRoute = extractApplicationRoute(doc)

    val progress: ProgressResponse = toProgressResponse(applicationId)(doc) // Defined in CommonBSONDocuments

    val edipReportColumn = applicationRoute match {
      case ApplicationRoute.Faststream => csedEdipCompleted
      case ApplicationRoute.SdipFaststream => edipCompleted.map(booleanTranslator)
      case ApplicationRoute.Edip => None
      case ApplicationRoute.Sdip => edipCompleted.map(booleanTranslator)
      case _ => None
    }

    val otherInternshipColumn = applicationRoute match {
      case ApplicationRoute.Faststream => csedOtherInternshipCompleted
      case _ => otherInternshipCompleted
    }

    val fsacIndicatorDocOpt = doc.get("fsac-indicator").map(_.asDocument())
    val assessmentCentre = fsacIndicatorDocOpt.map(_.get("assessmentCentre").asString().getValue)

    CandidateProgressReportItem(userId, applicationId, Some(ProgressStatusesReportLabels.progressStatusNameInReports(progress)),
      schemes.getOrElse(Nil), disability, onlineAdjustments, assessmentCentreAdjustments, phoneAdjustments, gis, csedCivilServant,
      edipReportColumn, csedSdip, otherInternshipColumn, fastPassCertificate, assessmentCentre, applicationRoute)
  } //scalastyle:on

  private[application] def toApplicationForInternshipReport(doc: Document) = {
    ??? // TODO: mongo i think this can be deleted
    /*
        val applicationId = extractAppId(doc)
        val route = doc.getAs[ApplicationRoute.ApplicationRoute]("applicationRoute")
          .getOrElse(throw new Exception(s"Application route not set for $applicationId"))
        val userId = extractUserId(doc)
        val progressResponse = toProgressResponse(applicationId)(doc)

        val psDocOpt = subDocRoot("personal-details")(doc)
        val firstName = extract("firstName")(psDocOpt)
        val lastName = extract("lastName")(psDocOpt)
        val preferredName = extract("preferredName")(psDocOpt)

        val adDocOpt = subDocRoot("assistance-details")(doc)
        val guaranteedInterviewScheme = extractBoolean("guaranteedInterview")(adDocOpt)

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
        */
  }

  private[application] def toApplicationForAnalyticalSchemesReport(doc: Document) = {
    val userId = extractUserId(doc)

    val psDocOpt = subDocRoot("personal-details")(doc)
    val firstName = extract("firstName")(psDocOpt)
    val lastName = extract("lastName")(psDocOpt)

    val schemes = extractSchemes(doc)
    val firstSchemePreferenceOpt = schemes.map(_.head.toString)

    val adDocOpt = subDocRoot("assistance-details")(doc)
    val guaranteedInterviewScheme = extractBoolean("guaranteedInterview")(adDocOpt)

    val applicationId = extractAppId(doc)
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
      firstSchemePreference = firstSchemePreferenceOpt,
      guaranteedInterviewScheme = guaranteedInterviewScheme,
      behaviouralTScore = behaviouralTScore,
      situationalTScore = situationalTScore,
      etrayTScore = etrayTScore,
      overallVideoScore = overallVideoScore
    )
  }

  private[application] def toApplicationForDiversityReport(doc: Document) = {
    val applicationId = extractAppId(doc)
    val userId = extractUserId(doc)
    val applicationRoute = extractApplicationRoute(doc)

    val onlineAdjustmentsKey = if(applicationRoute == ApplicationRoute.Edip) { "needsSupportForPhoneInterview" }
      else if (applicationRoute == ApplicationRoute.Sdip) { "needsSupportForPhoneInterview" }
      else { "needsSupportForOnlineAssessment" }

    val adDocOpt = subDocRoot("assistance-details")(doc)
    val onlineAdjustments = extractBoolean(onlineAdjustmentsKey)(adDocOpt).map(booleanTranslator)
    val disability = extract("hasDisability")(adDocOpt)
    val assessmentCentreAdjustments = extractBoolean("needsSupportAtVenue")(adDocOpt).map(booleanTranslator)
    val gis = extractBoolean("guaranteedInterview")(adDocOpt)

    val civilServiceExperience = toCivilServiceExperienceDetailsReportItem(applicationRoute, doc)
    val progress: ProgressResponse = toProgressResponse(applicationId)(doc) // Defined in CommonBSONDocuments

    val schemesDocOpt = subDocRoot("scheme-preferences")(doc)
    val schemes = schemesDocOpt.map( doc => Codecs.fromBson[List[SchemeId]](doc.get("schemes")) )
    val curSchemeStatus = extractCurrentSchemeStatus(doc, schemes)

    ApplicationForDiversityReport(applicationId, userId, applicationRoute,
      Some(ProgressStatusesReportLabels.progressStatusNameInReports(progress)),
      schemes.getOrElse(List.empty), disability, gis, onlineAdjustments,
      assessmentCentreAdjustments, civilServiceExperience, curSchemeStatus
    )
  }

  private[application] def toApplicationForOnlineTestPassMarkReport(doc: Document) = {
    val userId = extractUserId(doc)
    val applicationId = extractAppId(doc)
    val applicationRoute = extractApplicationRoute(doc)
    val schemes = extractSchemes(doc)

    val adDoc = subDocRoot("assistance-details")(doc)
    val gis = extractBoolean("guaranteedInterview")(adDoc)
    val disability = extract("hasDisability")(adDoc)
    val onlineAdjustments = extractBoolean("needsSupportForOnlineAssessment")(adDoc).map(booleanTranslator)
    val assessmentCentreAdjustments = extractBoolean("needsSupportAtVenue")(adDoc).map(booleanTranslator)
    val curSchemeStatus = extractCurrentSchemeStatus(doc, schemes)

    val progress: ProgressResponse = toProgressResponse(applicationId)(doc)

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

  private[application] def toApplicationForNumericTestExtractReport(doc: Document) = {
    val userId = extractUserId(doc)
    val applicationId = extractAppId(doc)
    val applicationRoute = extractApplicationRoute(doc)
    val schemes = extractSchemes(doc)

    val personalDetails = Codecs.fromBson[PersonalDetails](doc.get("personal-details").getOrElse(
      throw new Exception(s"Error parsing personal details for $userId")
    ))

    val adDocOpt = subDocRoot("assistance-details")(doc)
    val gis = extractBoolean("guaranteedInterview")(adDocOpt)
    val disability = extract("hasDisability")(adDocOpt)
    val onlineAdjustments = extractBoolean("needsSupportForOnlineAssessment")(adDocOpt).map(booleanTranslator)
    val assessmentCentreAdjustments = extractBoolean("needsSupportAtVenue")(adDocOpt).map(booleanTranslator)

    val currentSchemeStatus = Codecs.fromBson[List[SchemeEvaluationResult]](doc.get("currentSchemeStatus").getOrElse(
      throw new Exception(s"Error parsing current scheme status for $userId")
    ))

    val progress: ProgressResponse = toProgressResponse(applicationId)(doc)

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

  private[application] def toApplicationForOnlineActiveTestCountReport(doc: Document) = {
    val userId = extractUserId(doc)
    val applicationId = extractAppId(doc)
    val testGroupsDocOpt = subDocRoot("testGroups")(doc)

    val testCounter = (phase: Phase, codec: BsonValue => PsiTestProfile) => (for {
      testGroupsDoc <- testGroupsDocOpt
      length <- subDocRoot(phase)(testGroupsDoc).map ( doc => codec(doc).activeTests.length )
    } yield length).getOrElse(0)

    val gis = booleanTranslator(extractBoolean("guaranteedInterview")(subDocRoot("assistance-details")(doc)).contains(true))
    val p1TestsCount = testCounter(Phase.PHASE1, Codecs.fromBson[Phase1TestProfile])
    val p2TestsCount = testCounter(Phase.PHASE2, Codecs.fromBson[Phase2TestGroup])

    ApplicationForOnlineActiveTestCountReport(userId,applicationId, gis, p1TestsCount, p2TestsCount)
  }

  private[application] def toCivilServiceExperienceDetailsReportItem(applicationRoute: ApplicationRoute,
                                                                     doc: Document
                                                                    ): Option[CivilServiceExperienceDetailsForDiversityReport] = {
    val civilServiceExperienceDetails = repositories.getCivilServiceExperienceDetails(applicationRoute, doc) // Defined in package object
    Some(CivilServiceExperienceDetailsForDiversityReport(civilServiceExperienceDetails))
  }

  private[application] def toTestResultsForOnlineTestPassMarkReportItem(appDoc: Document,
                                                                        applicationId: String): TestResultsForOnlineTestPassMarkReportItem = {

    val testGroupsDocOpt = subDocRoot("testGroups")(appDoc)
    val p1Tests = toPhase1TestResults(testGroupsDocOpt)
    val p2Tests = toPhase2TestResults(testGroupsDocOpt)
    val videoInterviewResults = toPhase3TestResults(testGroupsDocOpt)
    val siftTestResults = toSiftTestResults(applicationId, testGroupsDocOpt)

    TestResultsForOnlineTestPassMarkReportItem(
      p1Tests,
      p2Tests,
      videoInterviewResults,
      siftTestResults,
      fsac = None, overallFsacScore = None, sift = None, fsb = None)
  }

  // Just declaring that implementations needs to provide a MicroserviceAppConfig impl
  def appConfig: MicroserviceAppConfig

  private def toPhase1TestResults(testGroupsDocOpt: Option[BsonDocument]): Seq[Option[PsiTestResult]] = {
    testGroupsDocOpt.flatMap( doc => subDocRoot(Phase.PHASE1)(doc) ).map { phase1Doc =>
      val phase1TestProfile = Codecs.fromBson[Phase1TestProfile](phase1Doc)

      // Sort the tests in config based on their names eg. test1, test2, test3, test4
      val p1TestNamesSorted = appConfig.onlineTestsGatewayConfig.phase1Tests.tests.keys.toList.sorted
      val p1TestIds = p1TestNamesSorted.map(testName => appConfig.onlineTestsGatewayConfig.phase1Tests.tests(testName))

      toPhaseXTestResults(phase1TestProfile.activeTests, p1TestIds)
    }.getOrElse(Seq.fill(4)(None))
  }

  private[application] def toPhase2TestResults(testGroupsDocOpt: Option[BsonDocument]): Seq[Option[PsiTestResult]] = {
    testGroupsDocOpt.flatMap( doc => subDocRoot(Phase.PHASE2)(doc) ).map { phase2Doc =>
      val phase2TestProfile = Codecs.fromBson[Phase2TestGroup](phase2Doc)

      // Sort the tests in config based on their names eg. test1, test2
      val p2TestNamesSorted = appConfig.onlineTestsGatewayConfig.phase2Tests.tests.keys.toList.sorted
      val p2TestIds = p2TestNamesSorted.map(testName => appConfig.onlineTestsGatewayConfig.phase2Tests.tests(testName))

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

  private[application] def toPhase3TestResults(testGroupsDocOpt: Option[BsonDocument]): Option[VideoInterviewTestResult] = {
    val reviewedDocsOpt = testGroupsDocOpt.flatMap( doc => subDocRoot(Phase.PHASE3)(doc) )
      .flatMap { phase3Doc =>
        Try(phase3Doc.getArray("tests")).toOption
          .flatMap(doc => Try(doc.get(0).asDocument()).toOption)
          .flatMap(doc => Try(doc.get("callbacks").asDocument()).toOption)
          .flatMap(doc => Try(doc.get("reviewed")).toOption)
          .map(Codecs.fromBson[List[ReviewedCallbackRequest]])
      }

    val latestReviewedOpt = reviewedDocsOpt
      .flatMap(reviewedCallbackList => ReviewedCallbackRequest.getLatestReviewed(reviewedCallbackList))

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

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private[application] def toSiftTestResults(applicationId: String,
                                             testGroupsDocOpt: Option[BsonDocument]): Option[PsiTestResult] = {
    val siftDocOpt = testGroupsDocOpt.flatMap( doc => subDocRoot("SIFT_PHASE")(doc) )
    siftDocOpt.flatMap { siftDoc =>
      val siftTestProfile = Codecs.fromBson[SiftTestGroup](siftDoc)
      siftTestProfile.activeTests.size match {
        case 1 => siftTestProfile.activeTests.head.testResult.map { tr => PsiTestResult(status = "", tScore = tr.tScore, raw = tr.rawScore) }
        case 0 => None
        case s if s > 1 =>
          logger.error(s"There are $s active sift tests which is invalid for application id [$applicationId]")
          None
      }
    }
  }
}
