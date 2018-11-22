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

package controllers

import akka.stream.scaladsl.Source
import connectors.{ AuthProviderClient, ExchangeObjects }
import model.EvaluationResults.Green
import model.Exceptions.{ NotFoundException, UnexpectedException }
import model.command.{ CandidateDetailsReportItem, CsvExtract }
import model.persisted.ContactDetailsWithId
import model.persisted.eventschedules.Event
import model.report._
import model.{ ApplicationStatus, SiftRequirement, UniqueIdentifier }
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.libs.streams.Streams
import play.api.mvc.{ Action, AnyContent, Result }
import repositories.application._
import repositories.contactdetails.ContactDetailsMongoRepository
import repositories.csv.FSACIndicatorCSVRepository
import repositories.events.EventsRepository
import repositories.fsb.FsbRepository
import repositories.sift.ApplicationSiftRepository
import repositories.{ QuestionnaireRepository, _ }
import services.evaluation.AssessmentScoreCalculator
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.collection.breakOut
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import common.Joda._

object ReportingController extends ReportingController {
  val reportingRepository: ReportingMongoRepository = repositories.reportingRepository
  val assessorRepository: AssessorRepository = repositories.assessorRepository
  val eventsRepository: EventsRepository = repositories.eventsRepository
  val assessorAllocationRepository: AssessorAllocationRepository = repositories.assessorAllocationRepository
  val contactDetailsRepository: ContactDetailsMongoRepository = repositories.faststreamContactDetailsRepository
  val questionnaireRepository: QuestionnaireMongoRepository = repositories.questionnaireRepository
  val prevYearCandidatesDetailsRepository: PreviousYearCandidatesDetailsMongoRepository = repositories.previousYearCandidatesDetailsRepository
  val assessmentScoresRepository: AssessmentScoresMongoRepository = repositories.reviewerAssessmentScoresRepository
  val mediaRepository: MediaMongoRepository = repositories.mediaRepository
  val applicationSiftRepository = repositories.applicationSiftRepository
  val fsacIndicatorCSVRepository: FSACIndicatorCSVRepository = repositories.fsacIndicatorCSVRepository
  val schemeRepo: SchemeRepository = SchemeYamlRepository
  val authProviderClient: AuthProviderClient = AuthProviderClient
  val candidateAllocationRepo = repositories.candidateAllocationRepository
  val fsbRepository = repositories.fsbRepository
  val applicationRepository: GeneralApplicationRepository = repositories.applicationRepository
}

trait ReportingController extends BaseController {

  val reportingRepository: ReportingRepository
  val assessorRepository: AssessorRepository
  val eventsRepository: EventsRepository
  val assessorAllocationRepository: AssessorAllocationRepository
  val contactDetailsRepository: contactdetails.ContactDetailsRepository
  val questionnaireRepository: QuestionnaireRepository
  val prevYearCandidatesDetailsRepository: PreviousYearCandidatesDetailsRepository
  val assessmentScoresRepository: AssessmentScoresRepository
  val mediaRepository: MediaRepository
  val applicationSiftRepository: ApplicationSiftRepository
  val fsacIndicatorCSVRepository: FSACIndicatorCSVRepository
  val schemeRepo: SchemeRepository
  val authProviderClient: AuthProviderClient
  val candidateAllocationRepo: CandidateAllocationRepository
  val fsbRepository: FsbRepository
  val applicationRepository: GeneralApplicationRepository

  def internshipReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      applications <- reportingRepository.applicationsForInternshipReport(frameworkId)
      contactDetails <- contactDetailsRepository.findByUserIds(applications.map(_.userId)).map(cdList => contactDetailsToMap(cdList))
    } yield {
      Ok(Json.toJson(buildInternshipReportItems(applications, contactDetails)))
    }
  }

  private def contactDetailsToMap(contactDetailsList: List[ContactDetailsWithId]) = contactDetailsList.map(cd => cd.userId -> cd).toMap

  private def buildInternshipReportItems(applications: List[ApplicationForInternshipReport],
    contactDetailsMap: Map[String, ContactDetailsWithId]
  ): List[InternshipReportItem] = {
    applications.map { application =>
      val contactDetails = contactDetailsMap.getOrElse(application.userId,
        throw new IllegalStateException(s"No contact details found for user Id = ${application.userId}")
      )
      InternshipReportItem(application, contactDetails)
    }
  }

  def analyticalSchemesReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val applicationsFut = reportingRepository.applicationsForAnalyticalSchemesReport(frameworkId)
    val reportFut = for {
      applications <- applicationsFut
      contactDetails <- contactDetailsRepository.findByUserIds(applications.map(_.userId)).map(cdList => contactDetailsToMap(cdList))
    } yield {
      buildAnalyticalSchemesReportItems(applications, contactDetails)
    }
    reportFut.map { report =>
      Ok(Json.toJson(report))
    }
  }

  def streamPreviousYearCandidatesDetailsReport: Action[AnyContent] = Action.async { implicit request =>
    def log(msg: String)= play.api.Logger.warn(s"streamPreviousYearCandidatesDetailsReport: $msg")
    enrichPreviousYearCandidateDetails {
      (numOfSchemes, contactDetails, questionnaireDetails, mediaDetails, eventsDetails,
       siftAnswers, assessorAssessmentScores, reviewerAssessmentScores) =>
      {
        val header = Enumerator(
          (prevYearCandidatesDetailsRepository.applicationDetailsHeader(numOfSchemes) ::
            prevYearCandidatesDetailsRepository.contactDetailsHeader ::
            prevYearCandidatesDetailsRepository.questionnaireDetailsHeader ::
            prevYearCandidatesDetailsRepository.mediaHeader ::
            prevYearCandidatesDetailsRepository.eventsDetailsHeader ::
            prevYearCandidatesDetailsRepository.siftAnswersHeader ::
            prevYearCandidatesDetailsRepository.assessmentScoresHeaders("Assessor") ::
            prevYearCandidatesDetailsRepository.assessmentScoresHeaders("Reviewer") ::
            Nil).mkString(",") + "\n"
        )
        var counter = 0
        val candidatesStream = prevYearCandidatesDetailsRepository.applicationDetailsStream(numOfSchemes).map { app =>
          val ret = createCandidateInfoBackUpRecord(
            app,
            contactDetails,
            questionnaireDetails,
            mediaDetails,
            eventsDetails,
            siftAnswers,
            assessorAssessmentScores,
            reviewerAssessmentScores
          ) + "\n"
          counter += 1
          ret
        }
        Ok.chunked(Source.fromPublisher(Streams.enumeratorToPublisher(header.andThen(candidatesStream))))
      }
    }
  }

  private def enrichPreviousYearCandidateDetails(
    block: (Int, CsvExtract[String], CsvExtract[String],
      CsvExtract[String], CsvExtract[String],
      CsvExtract[String], CsvExtract[String],
      CsvExtract[String]) => Result
  ) = {
    def log(msg: String)= play.api.Logger.warn(s"streamPreviousYearCandidatesDetailsReport: $msg")
    log(s"started enriching data at ${org.joda.time.DateTime.now}")
    val data = for {
      contactDetails <- prevYearCandidatesDetailsRepository.findContactDetails()
      _ = log(s"enriching data - contactDetails = ${contactDetails.header}, size = ${contactDetails.records.size}")
      questionnaireDetails <- prevYearCandidatesDetailsRepository.findQuestionnaireDetails()
      _ = log(s"enriching data - questionnaireDetails = ${questionnaireDetails.header}, size = ${questionnaireDetails.records.size}")
      mediaDetails <- prevYearCandidatesDetailsRepository.findMediaDetails()
      _ = log(s"enriching data - mediaDetails = ${mediaDetails.header}, size = ${mediaDetails.records.size}")
      eventsDetails <- prevYearCandidatesDetailsRepository.findEventsDetails()
      _ = log(s"enriching data - eventsDetails = ${eventsDetails.header}, size = ${eventsDetails.records.size}")
      siftAnswers <- prevYearCandidatesDetailsRepository.findSiftAnswers()
      _ = log(s"enriching data - siftAnswers = ${siftAnswers.header}, size = ${siftAnswers.records.size}")
      assessorAssessmentScores <- prevYearCandidatesDetailsRepository.findAssessorAssessmentScores()
      _ = log(s"enriching data - assessorAssessmentScores = ${assessorAssessmentScores.header}, size = ${assessorAssessmentScores.records.size}")
      reviewerAssessmentScores <- prevYearCandidatesDetailsRepository.findReviewerAssessmentScores()
      _ = log(s"enriching data - reviewerAssessmentScores = ${reviewerAssessmentScores.header}, size = ${reviewerAssessmentScores.records.size}")
    } yield {
      val res = block(schemeRepo.schemes.size, contactDetails, questionnaireDetails, mediaDetails, eventsDetails, siftAnswers,
        assessorAssessmentScores, reviewerAssessmentScores)
      log(s"result = $res")
      log(s"finished enriching data at ${org.joda.time.DateTime.now} ")
      res
    }
    data
  }

  //scalastyle:off
  def streamOnlineTestPassMarkReportWip: Action[AnyContent] = Action.async { implicit request =>
    def log(msg: String)= play.api.Logger.warn(s"onlineTestPassMarkStreamedReportWip: $msg")
    // this calls the method with the implementation of the function passed as an argument
    enrichOnlineTestPassMark {
//      (numOfSchemes, contactDetails, questionnaireDetails, mediaDetails, eventsDetails,
//       siftAnswers, assessorAssessmentScores, reviewerAssessmentScores) =>
      (numOfSchemes, questionnaireDetails) =>
      {

        val preferencesHeader = "Preference 1,Preference 2,Preference 3,Preference 4,Preference 5,Preference 6,Preference 7,Preference 8," +
          "Preference 9,Preference 10,Preference 11,Preference 12,Preference 13,Preference 14,Preference 15,Preference 16,Preference 17"
        val header = Enumerator(
          ("applicationId,userId,Candidate Progress" ::
            preferencesHeader ::
            "Disability,GIS,Adjustments" ::
            "Behavioural T-score,Behavioural Percentile,Behavioural Raw,Behavioural STEN" ::
            "Situational T-score,Situational Percentile,Situational Raw,Situational STEN" ::
            "e-Tray T-score,e-Tray Raw" ::
            "Gender,Ethnicity,University,Oxbridge,Russell Group,SE 1-5,SES" ::
            Nil
          ).mkString(",") + "\n"

/*
          (prevYearCandidatesDetailsRepository.applicationDetailsHeader(numOfSchemes) ::
            prevYearCandidatesDetailsRepository.contactDetailsHeader ::
            prevYearCandidatesDetailsRepository.questionnaireDetailsHeader ::
            prevYearCandidatesDetailsRepository.mediaHeader ::
            prevYearCandidatesDetailsRepository.eventsDetailsHeader ::
            prevYearCandidatesDetailsRepository.siftAnswersHeader ::
            prevYearCandidatesDetailsRepository.assessmentScoresHeaders("Assessor") ::
            prevYearCandidatesDetailsRepository.assessmentScoresHeaders("Reviewer") ::
            Nil).mkString(",") + "\n"
*/
        )
//        var counter = 0
        val candidatesStream = prevYearCandidatesDetailsRepository.applicationDetailsStreamWip(numOfSchemes).map { app =>
          val ret = createPassMarkRecord(
            app,
//            contactDetails,
            questionnaireDetails//,
//            mediaDetails,
//            eventsDetails,
//            siftAnswers,
//            assessorAssessmentScores,
//            reviewerAssessmentScores
          ) + "\n"
//          counter += 1
          ret
        }
        Ok.chunked(Source.fromPublisher(Streams.enumeratorToPublisher(header.andThen(candidatesStream))))
      }
    }
  }
  //scalastyle:on

  private def createPassMarkRecord(
                                    candidateDetails: CandidateDetailsReportItem,
//                                    contactDetails: CsvExtract[String],
                                    questionnaireDetails: CsvExtract[String]//,
//                                    mediaDetails: CsvExtract[String],
//                                    eventsDetails: CsvExtract[String],
//                                    siftAnswersDetails: CsvExtract[String],
//                                    assessorAssessmentScoresDetails: CsvExtract[String],
//                                    reviewerAssessmentScoresDetails: CsvExtract[String]
                                  ) = {
    (candidateDetails.csvRecord ::
//      contactDetails.records.getOrElse(candidateDetails.userId, contactDetails.emptyRecord) ::
      questionnaireDetails.records.getOrElse(candidateDetails.appId, questionnaireDetails.emptyRecord) ::
//      mediaDetails.records.getOrElse(candidateDetails.userId, mediaDetails.emptyRecord) ::
//      eventsDetails.records.getOrElse(candidateDetails.appId, eventsDetails.emptyRecord) ::
//      siftAnswersDetails.records.getOrElse(candidateDetails.appId, siftAnswersDetails.emptyRecord) ::
//      assessorAssessmentScoresDetails.records.getOrElse(candidateDetails.appId, assessorAssessmentScoresDetails.emptyRecord) ::
//      reviewerAssessmentScoresDetails.records.getOrElse(candidateDetails.appId, reviewerAssessmentScoresDetails.emptyRecord) ::
      Nil).mkString(",")
  }


  // this method fetches all of the additional data (not from application collection, which is fetched separately)
  // it is called with an empty tuple, which has place holders for all the additional data
  private def enrichOnlineTestPassMark(
                                                  block: (Int, CsvExtract[String]) => Result
                                                ) = {
    def log(msg: String)= play.api.Logger.warn(s"enrichOnlineTestPassMark: $msg")
    log(s"started enriching data at ${org.joda.time.DateTime.now}")
    val data = for {
      questionnaireDetails <- prevYearCandidatesDetailsRepository.findQuestionnaireDetailsWip()
      _ = log(s"enriching data - questionnaireDetails = ${questionnaireDetails.header}, size = ${questionnaireDetails.records.size}")
    } yield {
      val res = block(schemeRepo.schemes.size, questionnaireDetails)
      log(s"result = $res")
      log(s"finished enriching data at ${org.joda.time.DateTime.now} ")
      res
    }
    data
  }

  private def createCandidateInfoBackUpRecord(
    candidateDetails: CandidateDetailsReportItem,
    contactDetails: CsvExtract[String],
    questionnaireDetails: CsvExtract[String],
    mediaDetails: CsvExtract[String],
    eventsDetails: CsvExtract[String],
    siftAnswersDetails: CsvExtract[String],
    assessorAssessmentScoresDetails: CsvExtract[String],
    reviewerAssessmentScoresDetails: CsvExtract[String]
  ) = {
    (candidateDetails.csvRecord ::
      contactDetails.records.getOrElse(candidateDetails.userId, contactDetails.emptyRecord) ::
      questionnaireDetails.records.getOrElse(candidateDetails.appId, questionnaireDetails.emptyRecord) ::
      mediaDetails.records.getOrElse(candidateDetails.userId, mediaDetails.emptyRecord) ::
      eventsDetails.records.getOrElse(candidateDetails.appId, eventsDetails.emptyRecord) ::
      siftAnswersDetails.records.getOrElse(candidateDetails.appId, siftAnswersDetails.emptyRecord) ::
      assessorAssessmentScoresDetails.records.getOrElse(candidateDetails.appId, assessorAssessmentScoresDetails.emptyRecord) ::
      reviewerAssessmentScoresDetails.records.getOrElse(candidateDetails.appId, reviewerAssessmentScoresDetails.emptyRecord) ::
      Nil).mkString(",")
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")

  // scalastyle:off method.length
  def assessorAllocationReport: Action[AnyContent] = Action.async { implicit request =>

    val sortedEventsFut = eventsRepository.findAll().map(_.sortBy(_.date))

    val reportRows = for {
      allAssessors <- assessorRepository.findAll()
      allAssessorsIds = allAssessors.map(_.userId)
      allAssessorsPersonalInfo <- authProviderClient.findByUserIds(allAssessorsIds)
        .map(
          _.map(x => x.userId -> x)(breakOut): Map[String, ExchangeObjects.Candidate]
        )
      allAssessorsAuthInfo <- authProviderClient.findAuthInfoByUserIds(allAssessorsIds)
        .map(
          _.map(x => x.userId -> x)(breakOut): Map[String, ExchangeObjects.UserAuthInfo]
        )
      sortedEvents <- sortedEventsFut
      assessorAllocations <- assessorAllocationRepository.findAll().map(_.groupBy(_.id))
    } yield for {
      theAssessor <- allAssessors
      theAssessorPersonalInfo = allAssessorsPersonalInfo(theAssessor.userId)
      theAssessorAuthInfo = allAssessorsAuthInfo(theAssessor.userId)
      theAssessorAllocations = assessorAllocations.getOrElse(theAssessor.userId, Nil)
    } yield {

      val roleByDate = sortedEvents.map { event =>
        theAssessorAllocations.find(_.eventId == event.id).map(allocation =>
          s"${allocation.allocatedAs.toString} (${allocation.status.toString})"
        ).orElse {
          val availabilities = theAssessor.availability.filter(_.date == event.date)
          if (availabilities.nonEmpty && theAssessor.skills.intersect(event.skillRequirements.keys.toSeq).nonEmpty) {
            Some(availabilities.map(_.location.name).mkString(", "))
          } else {
            None
          }
        }
      }

      val assessorInfo = List(Some(s"${theAssessorPersonalInfo.userId}"),
        Some(s"${theAssessorPersonalInfo.firstName} ${theAssessorPersonalInfo.lastName}"),
        Some(theAssessorPersonalInfo.roles.mkString(", ")),
        Some(if(theAssessorAuthInfo.disabled) "YES" else "NO"),
        Some(theAssessor.skills.mkString(", ")),
        Some(theAssessor.sifterSchemes.map(_.toString).mkString(", ")),
        Some(theAssessorPersonalInfo.email), theAssessorPersonalInfo.phone,
        Some(if (theAssessor.civilServant) { "Internal" } else { "External" }))

      makeRow(assessorInfo ++ roleByDate: _*)
    }

    sortedEventsFut.flatMap { events =>
      val orderedDates = events.map(event => s""""${event.date} (${event.eventType.toString}, ${event.location.name})"""").mkString(",")
      val headers = List(
        s"Assessor ID,Name,Role,Deactivated,Skills,Sift schemes,Email,Phone,Internal/External,$orderedDates"
      )

      reportRows.map { rows =>
        Ok(Json.toJson(headers ++ rows))
      }
    }
  }
  // scalastyle:on

  private def buildAnalyticalSchemesReportItems(applications: List[ApplicationForAnalyticalSchemesReport],
    contactDetailsMap: Map[String, ContactDetailsWithId]): List[AnalyticalSchemesReportItem] = {
    applications.map { application =>
      val contactDetails = contactDetailsMap.getOrElse(application.userId,
        throw new IllegalStateException(s"No contact details found for user Id = ${application.userId}")
      )
      AnalyticalSchemesReportItem(application, contactDetails)
    }
  }

  def adjustmentReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports =
      for {
        applications <- reportingRepository.adjustmentReport(frameworkId)
        allCandidates <- contactDetailsRepository.findAll
        candidates = allCandidates.groupBy(_.userId).mapValues(_.head)
      } yield {
        applications.map { application =>
          candidates
            .get(application.userId)
            .fold(application)(cd =>
              application.copy(email = Some(cd.email), telephone = cd.phone))
        }
      }

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def candidateProgressReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val candidatesFut: Future[List[CandidateProgressReportItem]] = reportingRepository.candidateProgressReport(frameworkId)

    for {
      candidates <- candidatesFut
    } yield Ok(Json.toJson(candidates))
  }

  def preSubmittedCandidatesReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reportItemsFut = reportingRepository.preSubmittedApplications(frameworkId).flatMap { allApplications =>
      val batchedApplications = allApplications.grouped(500)
      Future.sequence(batchedApplications.map { applications =>
        authProviderClient.findByUserIds(applications.map(_.userId)).flatMap { authDetails =>
          personalDetailsRepository.findByIds(applications.map(_.applicationId)).map { appPersonalDetailsTuple =>
            applications.map { application =>
              val user = authDetails.find(_.userId == application.userId)
                .getOrElse(throw new NotFoundException(s"Unable to find auth details for user ${application.userId}"))
              val (_, pd) = appPersonalDetailsTuple.find(_._1 == application.applicationId)
                .getOrElse(throw UnexpectedException(s"Invalid applicationId ${application.applicationId}"))
              PreSubmittedReportItem(user, pd.map(_.preferredName), application)
            }
          }
        }
      })
    }

    reportItemsFut.map(items => Ok(Json.toJson(items.flatten.toList)))
  }

  def candidateDeferralReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      eventualCandidates <- reportingRepository.candidateDeferralReport(frameworkId)
      eventualContactDetails <- contactDetailsRepository.findAll
      contactDetailsByUserId = eventualContactDetails.groupBy(_.userId).mapValues(_.head)
    } yield {
      val data = eventualCandidates.map { candidate =>
        contactDetailsByUserId.get(candidate.userId).map { cd =>
          CandidateDeferralReportItem(
            candidateName = s"${candidate.firstName} ${candidate.lastName}",
            preferredName = candidate.preferredName,
            email = cd.email,
            address = cd.address,
            postCode = cd.postCode,
            telephone = cd.phone,
            programmes = candidate.partnerProgrammes
          )
        }
      }

      Ok(Json.toJson(data))
    }
  }

  def diversityReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports = for {
      applications <- reportingRepository.diversityReport(frameworkId)
      questionnaires <- questionnaireRepository.findAllForDiversityReport
      medias <- mediaRepository.findAll()
    } yield {
      applications.map { application =>
        DiversityReportItem(
          ApplicationForDiversityReportItem.create(application),
          questionnaires.get(application.applicationId),
          medias.get(application.userId).map { m => MediaReportItem(m.media) })
      }
    }
    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def successfulCandidatesReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports = for {
      successfulApplications <- reportingRepository.successfulCandidatesReport
      appsByUserId <- reportingRepository.diversityReport(frameworkId).map(_.groupBy(_.userId).mapValues(_.head))
      userIds = successfulApplications.map(_.userId)
      appIds = successfulApplications.map(_.applicationId)
      questionnairesByAppId <- questionnaireRepository.findQuestionsByIds(appIds)
      mediasByUserId <- mediaRepository.findAll()
      candidatesContactDetails <- contactDetailsRepository.findByUserIds(userIds)
      applicationsForOnlineTest <- reportingRepository.onlineTestPassMarkReportByIds(appIds)
      siftResults <- applicationSiftRepository.findAllResultsByIds(appIds)
      fsacResults <- assessmentScoresRepository.findAllByIds(appIds)
      fsbResults <- fsbRepository.findByApplicationIds(successfulApplications.map(_.applicationId), None)
    } yield {
      Future.sequence(successfulApplications.map { successfulCandidatePartialItem =>
        val userId = successfulCandidatePartialItem.userId
        val application = appsByUserId(userId)
        val appId = application.applicationId
        val contactDetails = candidatesContactDetails.find(_.userId == userId)
        val diversityReportItem = DiversityReportItem(
          ApplicationForDiversityReportItem.create(application),
          questionnairesByAppId.get(appId),
          mediasByUserId.get(userId).map(m => MediaReportItem(m.media))
        )

        val onlineTestResults = applicationsForOnlineTest.find(_.userId == userId)
        val siftResult = siftResults.find(_.applicationId == appId)
        val fsacResult = fsacResults.find(_.applicationId.toString() == appId)
        val overallFsacScoreOpt = fsacResult.map(res => AssessmentScoreCalculator.countAverage(res).overallScore)
        val fsbResult = Option(FsbReportItem(appId, fsbResults.find(_.applicationId == appId).map(_.results)))

        applicationRepository.getCurrentSchemeStatus(appId).map { currentSchemeStatus =>
          SuccessfulCandidateReportItem(
            successfulCandidatePartialItem,
            contactDetails,
            diversityReportItem,
            onlineTestResults,
            siftResult,
            overallFsacScoreOpt,
            fsbResult,
            currentSchemeStatus
          )
        }
      })
    }

    reports.flatMap(identity).map { list =>
      Ok(Json.toJson(list))
    }
  }

  def onlineTestPassMarkReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    def log(msg: String)= play.api.Logger.warn(s"onlineTestPassMarkReport: $msg")

    log(s"started fetching data at ${org.joda.time.DateTime.now}")
    val reports =
      for {
        applications <- reportingRepository.onlineTestPassMarkReport
        _ = log(s"applications fetched = ${applications.size}")
        siftResults <- applicationSiftRepository.findAllResults
        _ = log(s"sift results fetched = ${siftResults.size}")
        fsacResults <- assessmentScoresRepository.findAll
        _ = log(s"fsac results fetched = ${fsacResults.size}")
        appIds = applications.map(_.applicationId)
        questionnaires <- questionnaireRepository.findForOnlineTestPassMarkReport(appIds)
        _ = log(s"questionnaires fetched = ${questionnaires.size}")
        fsbScoresAndFeedback <- fsbRepository.findScoresAndFeedback(appIds)
        _ = log(s"fsb scores and feedback fetched = ${fsbScoresAndFeedback.size}")
      } yield {
        val data = for {
          application <- applications
          appId = UniqueIdentifier(application.applicationId)
          fsac = fsacResults.find(_.applicationId == appId)
          overallFsacScoreOpt = fsac.map(res => AssessmentScoreCalculator.countAverage(res).overallScore)
          sift = siftResults.find(_.applicationId == application.applicationId)
          q <- questionnaires.get(application.applicationId)
          fsb <- fsbScoresAndFeedback.get(application.applicationId)
        } yield OnlineTestPassMarkReportItem(
          ApplicationForOnlineTestPassMarkReportItem.create(application, fsac, overallFsacScoreOpt, sift, fsb), q
        )
        log(s"finished fetching data at ${org.joda.time.DateTime.now}")
        data
      }

    reports.map { list =>
      log(s"Json conversion started at ${org.joda.time.DateTime.now}")
      val jsonData = Json.toJson(list)
      log(s"Json conversion finished at ${org.joda.time.DateTime.now}")
      Ok(jsonData)
    }
  }

  def numericTestExtractReport(): Action[AnyContent] = Action.async { implicit request =>

    val numericTestSchemeIds = schemeRepo.schemes.collect {
      case scheme if scheme.siftEvaluationRequired && scheme.siftRequirement.contains(SiftRequirement.NUMERIC_TEST) => scheme.id
    }

    val reports =
      for {
        applications <- reportingRepository.numericTestExtractReport.map(_.filter { app =>
          val successfulSchemesSoFarIds = app.currentSchemeStatus.collect {
            case evalResult if evalResult.result == Green.toString => evalResult.schemeId
          }
          successfulSchemesSoFarIds.exists(numericTestSchemeIds.contains)
        })
        contactDetails <- contactDetailsRepository.findByUserIds(applications.map(_.userId))
          .map(
            _.map(x => x.userId -> x)(breakOut): Map[String, ContactDetailsWithId]
          )
        questionnaires <- questionnaireRepository.findForOnlineTestPassMarkReport(applications.map(_.applicationId))
      } yield for {
        a <- applications
        c <- contactDetails.get(a.userId)
        q <- questionnaires.get(a.applicationId)
      } yield NumericTestExtractReportItem(a, c, q)

      reports.map(list => Ok(Json.toJson(list)))
  }

  def candidateAcceptanceReport(): Action[AnyContent] = Action.async { implicit request =>

    val headers = Seq("Candidate email, allocation date, event date, event type, event description, location, venue")
    candidateAllocationRepo.allAllocationUnconfirmed.flatMap { allAllocations =>
      for {
        candidates <- applicationRepository.find(allAllocations.map(_.id))
        candidateAllocations = allAllocations.filterNot { alloc =>
          val status = candidates.find(c => c.applicationId.get == alloc.id)
            .getOrElse(throw UnexpectedException(s"Unable to find application ${alloc.id}"))
            .applicationStatus.getOrElse(throw UnexpectedException(s"Application ${alloc.id} has no application status"))
          status == ApplicationStatus.WITHDRAWN.toString
        }
        events <- eventsRepository.getEventsById(candidateAllocations.map(_.eventId))
        contactDetails <- contactDetailsRepository.findByUserIds(candidates.map(_.userId))
      } yield {
        val eventMap: Map[String, Event] = events.map(e => e.id -> e)(breakOut)
        val cdMap: Map[String, ContactDetailsWithId] =
          candidates.map(c => c.applicationId.get -> contactDetails.find(_.userId == c.userId).get)(breakOut)

        val report = headers ++ candidateAllocations.map { allocation =>
          val e = eventMap(allocation.eventId)
          makeRow(List(Some(cdMap(allocation.id).email), Some(allocation.createdAt.toString), Some(e.date.toString), Some(e.eventType.toString),
            Some(e.description), Some(e.location.name), Some(e.venue.name)):_*
          )
        }
        Ok(Json.toJson(report))
      }
    }
  }
}
