/*
 * Copyright 2021 HM Revenue & Customs
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
import com.google.inject.name.Named
import common.Joda._
import connectors.{AuthProviderClient, ExchangeObjects}

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import model.ApplicationRoute.{ApplicationRoute, Edip, Faststream, Sdip, SdipFaststream}
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Green
import model.Exceptions.{NotFoundException, UnexpectedException}
import model._
import model.assessmentscores.AssessmentScoresExercise
import model.command.{CandidateDetailsReportItem, CsvExtract}
import model.persisted.eventschedules.Event
import model.persisted.{ApplicationForOnlineTestPassMarkReport, ContactDetailsWithId}
import model.report._
import play.api.Logging
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.streams.IterateeStreams
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import repositories.application._
import repositories.contactdetails.ContactDetailsRepository
import repositories.events.EventsRepository
import repositories.fsb.FsbRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.sift.ApplicationSiftRepository
import repositories._
import services.evaluation.AssessmentScoreCalculator
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.collection.breakOut
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// scalastyle:off number.of.methods file.size.limit
@Singleton
class ReportingController @Inject() (cc: ControllerComponents,
                                     reportingRepository: ReportingRepository,
                                     assessorRepository: AssessorRepository,
                                     eventsRepository: EventsRepository,
                                     assessorAllocationRepository: AssessorAllocationRepository,
                                     contactDetailsRepository: ContactDetailsRepository,
                                     questionnaireRepository: QuestionnaireRepository,
                                     prevYearCandidatesDetailsRepository: PreviousYearCandidatesDetailsRepository,
                                     @Named("ReviewerAssessmentScoresRepo") assessmentScoresRepository: AssessmentScoresRepository,
                                     mediaRepository: MediaRepository,
                                     applicationSiftRepository: ApplicationSiftRepository,
                                     schemeRepo: SchemeRepository,
                                     authProviderClient: AuthProviderClient,
                                     candidateAllocationRepo: CandidateAllocationRepository,
                                     fsbRepository: FsbRepository,
                                     applicationRepository: GeneralApplicationRepository,
                                     personalDetailsRepository: PersonalDetailsRepository
)(implicit mat: Materializer) extends BackendController(cc) with Logging {

  def fsacScores(): Action[AnyContent] = Action.async { implicit request =>
    def removeFeedback(assessmentScoresExercise: AssessmentScoresExercise) =
      assessmentScoresExercise.copy(seeingTheBigPictureFeedback = None, makingEffectiveDecisionsFeedback = None,
        communicatingAndInfluencingFeedback = None, workingTogetherDevelopingSelfAndOthersFeedback = None)

    val reports = for {
      fsacResults <- assessmentScoresRepository.findAll
    } yield {
      fsacResults.map{ data =>
        FsacScoresReportItem(
          data.applicationId.toString(),
          data.analysisExercise.map( removeFeedback ),
          data.groupExercise.map( removeFeedback ),
          data.leadershipExercise.map( removeFeedback )
        )
      }
    }

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def internshipReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    def buildInternshipReportItems(applications: List[ApplicationForInternshipReport],
                                   contactDetailsMap: Map[String, ContactDetailsWithId]
                                  ): List[InternshipReportItem] = {
      applications.map { application =>
        val contactDetails = contactDetailsMap.getOrElse(application.userId,
          throw new IllegalStateException(s"No contact details found for user Id = ${application.userId}")
        )
        InternshipReportItem(application, contactDetails)
      }
    }

    for {
      applications <- reportingRepository.applicationsForInternshipReport(frameworkId)
      contactDetails <- contactDetailsRepository.findByUserIds(applications.map(_.userId)).map(cdList => contactDetailsToMap(cdList))
    } yield {
      Ok(Json.toJson(buildInternshipReportItems(applications, contactDetails)))
    }
  }

  private def contactDetailsToMap(contactDetailsList: List[ContactDetailsWithId]) = contactDetailsList.map(cd => cd.userId -> cd).toMap

  def analyticalSchemesReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>

    def buildAnalyticalSchemesReportItems(applications: List[ApplicationForAnalyticalSchemesReport],
                                          contactDetailsMap: Map[String, ContactDetailsWithId]): List[AnalyticalSchemesReportItem] = {
      applications.map { application =>
        val contactDetails = contactDetailsMap.getOrElse(application.userId,
          throw new IllegalStateException(s"No contact details found for user Id = ${application.userId}")
        )
        AnalyticalSchemesReportItem(application, contactDetails)
      }
    }

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

  def streamPreviousYearFaststreamPresubmittedCandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(ApplicationStatus.CREATED, ApplicationStatus.IN_PROGRESS,
        ApplicationStatus.SUBMITTED, ApplicationStatus.WITHDRAWN,
        ApplicationStatus.ELIGIBLE_FOR_JOB_OFFER
      )
    )
  }

  def streamPreviousYearFaststreamP2P3CandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(
        ApplicationStatus.PHASE2_TESTS, ApplicationStatus.PHASE2_TESTS_PASSED,
        ApplicationStatus.PHASE2_TESTS_FAILED, ApplicationStatus.PHASE3_TESTS,
        ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER, ApplicationStatus.PHASE3_TESTS_PASSED,
        ApplicationStatus.PHASE3_TESTS_FAILED, ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED
      )
    )
  }

  def streamPreviousYearFaststreamP2CandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(
        ApplicationStatus.PHASE2_TESTS, ApplicationStatus.PHASE2_TESTS_PASSED,
        ApplicationStatus.PHASE2_TESTS_FAILED
      )
    )
  }

  def streamPreviousYearFaststreamP3CandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(
        ApplicationStatus.PHASE3_TESTS,
        ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER, ApplicationStatus.PHASE3_TESTS_PASSED,
        ApplicationStatus.PHASE3_TESTS_FAILED, ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED
      )
    )
  }

  def streamPreviousYearFaststreamSIFTCandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(ApplicationStatus.SIFT, ApplicationStatus.FAILED_AT_SIFT)
    )
  }

  def streamPreviousYearFaststreamFSACCandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(ApplicationStatus.ASSESSMENT_CENTRE)
    )
  }

  def streamPreviousYearFaststreamFSBCandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(ApplicationStatus.FSB)
    )
  }
/*
  def streamPreviousYearFaststreamP1FailedCandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      (c: Candidate) => c.applicationStatus.get == ApplicationStatus.PHASE1_TESTS_FAILED.toString
    )
  }

  def streamPreviousYearFaststreamP1NotFailedCandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      (c: Candidate) => c.applicationStatus.get != ApplicationStatus.PHASE1_TESTS_FAILED.toString
    )
  }*/

  def streamPreviousYearFaststreamP1NotFailedCandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS, ApplicationStatus.PHASE1_TESTS_PASSED, ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED)
    )
  }

  def streamPreviousYearFaststreamP1FailedCandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS_FAILED)
    )
  }

  def streamPreviousYearFaststreamP1FailedCandidatesDetailsPart1Report: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS_FAILED),
      part = 1
    )
  }

  def streamPreviousYearFaststreamP1FailedCandidatesDetailsPart2Report: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS_FAILED),
      part = 2
    )
  }

  def streamPreviousYearFaststreamP1FailedCandidatesDetailsPart3Report: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS_FAILED),
      part = 3
    )
  }

  def streamPreviousYearFaststreamP1FailedCandidatesDetailsPart4Report: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS_FAILED),
      part = 4
    )
  }

  def streamPreviousYearNonFaststreamCandidatesDetailsReport: Action[AnyContent] = {
    streamPreviousYearCandidatesDetailsReport(
      Seq(SdipFaststream, Sdip, Edip),
      _ => true
    )
  }

  private def streamPreviousYearCandidatesDetailsReport(
    applicationRoutes: Seq[ApplicationRoute],
    applicationStatuses: Seq[ApplicationStatus]
    ): Action[AnyContent] = Action.async { implicit request =>
      logRpt(s"started report for appRoutes: $applicationRoutes, appStatuses: $applicationStatuses")
      prevYearCandidatesDetailsRepository.findApplicationsFor(applicationRoutes, applicationStatuses).flatMap { candidates =>
        logRpt(s"fetched ${candidates.size} candidates")
        val appIds = candidates.map(_.applicationId)
        val userIds = candidates.map(_.userId)

        enrichPreviousYearCandidateDetails(appIds, userIds) {
          (numOfSchemes, contactDetails, questionnaireDetails, mediaDetails, eventsDetails,
           siftAnswers, assessorAssessmentScores, reviewerAssessmentScores) => {
            val header = buildHeaders(numOfSchemes)
            logRpt(s"started fetching the application details stream for ${appIds.size} candidates")
            val candidatesStream = prevYearCandidatesDetailsRepository.applicationDetailsStream(numOfSchemes, appIds).map {
              app =>
                createCandidateInfoBackUpRecord(
                  app, contactDetails, questionnaireDetails, mediaDetails,
                  eventsDetails, siftAnswers, assessorAssessmentScores, reviewerAssessmentScores
                ) + "\n"
            }
            logRpt(s"stopped fetching the application details stream for ${appIds.size} candidates")
            logRpt(s"finished loading data for appRoutes: $applicationRoutes, appStatuses: $applicationStatuses. " +
              s"Now sending the chunked response.")
            Ok.chunked(header ++ candidatesStream)
          }
        }
      }
  }

  private def streamDataAnalystReport(applicationRoutes: Seq[ApplicationRoute],
                                      applicationStatuses: Seq[ApplicationStatus]
                                     ): Action[AnyContent] = Action.async { implicit request =>
    logDataAnalystRpt(s"started report for appRoutes: $applicationRoutes, appStatuses: $applicationStatuses")
    prevYearCandidatesDetailsRepository.findApplicationsFor(applicationRoutes, applicationStatuses).flatMap { candidates =>
      logDataAnalystRpt(s"fetched ${candidates.size} candidates")
      val appIds = candidates.map(_.applicationId)
      val userIds = candidates.map(_.userId)
      commonEnrichDataAnalystReport(appIds, userIds, applicationRoutes, applicationStatuses)
    }
  }

  private def streamPreviousYearCandidatesDetailsReport(
    applicationRoutes: Seq[ApplicationRoute],
    applicationStatuses: Seq[ApplicationStatus],
    part: Int
  ): Action[AnyContent] = Action.async { implicit request =>
    logRpt(s"started report for appRoutes: $applicationRoutes, appStatuses: $applicationStatuses, part: $part")
    prevYearCandidatesDetailsRepository.findApplicationsFor(applicationRoutes, applicationStatuses, part).flatMap { candidates =>
      val appIds = candidates.map(_.applicationId)
      val userIds = candidates.map(_.userId)

      enrichPreviousYearCandidateDetails(appIds, userIds) {
        (numOfSchemes, contactDetails, questionnaireDetails, mediaDetails, eventsDetails,
        siftAnswers, assessorAssessmentScores, reviewerAssessmentScores) => {
          val header = buildHeaders(numOfSchemes)
          logRpt(s"started fetching the application details stream for ${appIds.size} candidates")
          val candidatesStream = prevYearCandidatesDetailsRepository.applicationDetailsStream(numOfSchemes, appIds).map {
            app =>
              val ret = createCandidateInfoBackUpRecord(
                app, contactDetails, questionnaireDetails, mediaDetails,
                eventsDetails, siftAnswers, assessorAssessmentScores, reviewerAssessmentScores
              ) + "\n"
              ret
          }
          logRpt(s"stopped fetching the application details stream for ${appIds.size} candidates")
          logRpt(s"finished loading data for appRoutes: $applicationRoutes, appStatuses: $applicationStatuses, part: $part. " +
            s"Now sending the chunked response.")
          Ok.chunked(header ++ candidatesStream)
        }
      }
    }
  }

  private def streamDataAnalystReport(applicationRoutes: Seq[ApplicationRoute],
                                      applicationStatuses: Seq[ApplicationStatus],
                                      part: Int
                                     ): Action[AnyContent] = Action.async { implicit request =>
    logDataAnalystRpt(s"started report for appRoutes: $applicationRoutes, appStatuses: $applicationStatuses, part: $part")
    prevYearCandidatesDetailsRepository.findApplicationsFor(applicationRoutes, applicationStatuses, part).flatMap { candidates =>
      logDataAnalystRpt(s"fetched ${candidates.size} candidates")
      val appIds = candidates.map(_.applicationId)
      val userIds = candidates.map(_.userId)
      commonEnrichDataAnalystReport(appIds, userIds, applicationRoutes, applicationStatuses)
    }
  }

  private def streamPreviousYearCandidatesDetailsReport(
    applicationRoutes: Seq[ApplicationRoute],
    filter: CandidateIds => Boolean): Action[AnyContent] = Action.async { implicit request =>
    logRpt(s"started report for appRoutes: $applicationRoutes")
      prevYearCandidatesDetailsRepository.findApplicationsFor(applicationRoutes).flatMap { candidates =>
        val appIds = candidates.collect { case c if filter(c) => c.applicationId }
        val userIds = candidates.collect { case c if filter(c) => c.userId }

        enrichPreviousYearCandidateDetails(appIds, userIds) {
          (numOfSchemes, contactDetails, questionnaireDetails, mediaDetails, eventsDetails,
           siftAnswers, assessorAssessmentScores, reviewerAssessmentScores) => {
            val header = buildHeaders(numOfSchemes)
            var counter = 0
            logRpt(s"started fetching the application details stream for ${appIds.size} candidates")
            val candidatesStream = prevYearCandidatesDetailsRepository.applicationDetailsStream(numOfSchemes, appIds).map {
              app =>
                val ret = createCandidateInfoBackUpRecord(
                  app, contactDetails, questionnaireDetails, mediaDetails,
                  eventsDetails, siftAnswers, assessorAssessmentScores, reviewerAssessmentScores
                ) + "\n"
                counter += 1
                ret
            }
            logRpt(s"stopped fetching the application details stream for ${appIds.size} candidates")
            logRpt(s"finished loading data for appRoutes: $applicationRoutes. Now sending the chunked response.")
            Ok.chunked(header ++ candidatesStream)
          }
        }
      }
  }

  private def streamDataAnalystReport(applicationRoutes: Seq[ApplicationRoute],
                                      filter: CandidateIds => Boolean): Action[AnyContent] = Action.async { implicit request =>
    logDataAnalystRpt(s"started report for appRoutes: $applicationRoutes")
    prevYearCandidatesDetailsRepository.findApplicationsFor(applicationRoutes).flatMap { candidates =>
      logDataAnalystRpt(s"fetched ${candidates.size} candidates")
      val appIds = candidates.collect { case c if filter(c) => c.applicationId }
      val userIds = candidates.collect { case c if filter(c) => c.userId }
      commonEnrichDataAnalystReport(appIds, userIds, applicationRoutes, Nil)
    }
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

  //TODO: remove
  /*
  private def buildHeadersLegacy(numOfSchemes: Int): Enumerator[String] = {
    Enumerator(
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
  }*/

  private def buildHeaders(numOfSchemes: Int): Source[String, _] = {
    Source.single(
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
  }

  type ReportStreamBlockType = (Int, CsvExtract[String], CsvExtract[String], CsvExtract[String], CsvExtract[String],
    CsvExtract[String],CsvExtract[String], CsvExtract[String]) => Result

  private def logRpt(msg: String)= logger.warn(s"streamPreviousYearCandidatesDetailsReport: $msg")

  private def enrichPreviousYearCandidateDetails(applicationIds: Seq[String], userIds: Seq[String] = Nil)(block: ReportStreamBlockType) = {
    logRpt(s"started fetching enriched data for ${applicationIds.size} candidates")
    for {
      contactDetails <- prevYearCandidatesDetailsRepository.findContactDetails(userIds)
      _ = logRpt(s"enriching data - contactDetails size = ${contactDetails.records.size}")
      questionnaireDetails <- prevYearCandidatesDetailsRepository.findQuestionnaireDetails(applicationIds)
      _ = logRpt(s"enriching data - questionnaireDetails size = ${questionnaireDetails.records.size}")
      mediaDetails <- prevYearCandidatesDetailsRepository.findMediaDetails(userIds)
      _ = logRpt(s"enriching data - mediaDetails size = ${mediaDetails.records.size}")
      eventsDetails <- prevYearCandidatesDetailsRepository.findEventsDetails(applicationIds)
      _ = logRpt(s"enriching data - eventsDetails size = ${eventsDetails.records.size}")
      siftAnswers <- prevYearCandidatesDetailsRepository.findSiftAnswers(applicationIds)
      _ = logRpt(s"enriching data - siftAnswers size = ${siftAnswers.records.size}")
      assessorAssessmentScores <- prevYearCandidatesDetailsRepository.findAssessorAssessmentScores(applicationIds)
      _ = logRpt(s"enriching data - assessorAssessmentScores size = ${assessorAssessmentScores.records.size}")
      reviewerAssessmentScores <- prevYearCandidatesDetailsRepository.findReviewerAssessmentScores(applicationIds)
      _ = logRpt(s"enriching data - reviewerAssessmentScores size = ${reviewerAssessmentScores.records.size}")
    } yield {
      logRpt(s"finished fetching enriched data for ${applicationIds.size} candidates")
      block(maxSchemes, contactDetails, questionnaireDetails, mediaDetails, eventsDetails, siftAnswers,
        assessorAssessmentScores, reviewerAssessmentScores)
    }
  }

  // +1 to handle SdipFastStream candidates who automatically get the Sdip schemes in addition to the 4 selectable schemes
  private def maxSchemes = schemeRepo.maxNumberOfSelectableSchemes + 1

  //====
  // Pt1 Includes data from the following collections: application, contact-details and media
  // Pt2 Includes data from the following collections: application, questionnaire and sift-answers
  def streamDataAnalystFaststreamPresubmittedCandidatesDetailsReport: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(ApplicationStatus.CREATED, ApplicationStatus.IN_PROGRESS,
        ApplicationStatus.SUBMITTED, ApplicationStatus.WITHDRAWN,
        ApplicationStatus.ELIGIBLE_FOR_JOB_OFFER
      )
    )
  }

  def streamDataAnalystFaststreamP1NotFailedCandidatesDetailsReport: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS, ApplicationStatus.PHASE1_TESTS_PASSED, ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED)
    )
  }

  def streamDataAnalystFaststreamP1FailedCandidatesDetailsReport: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS_FAILED)
    )
  }

  def streamDataAnalystFaststreamP1FailedCandidatesDetailsPart1Report: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS_FAILED),
      part = 1
    )
  }

  def streamDataAnalystFaststreamP1FailedCandidatesDetailsPart2Report: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS_FAILED),
      part = 2
    )
  }

  def streamDataAnalystFaststreamP1FailedCandidatesDetailsPart3Report: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS_FAILED),
      part = 3
    )
  }

  def streamDataAnalystFaststreamP1FailedCandidatesDetailsPart4Report: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(ApplicationStatus.PHASE1_TESTS_FAILED),
      part = 4
    )
  }

  def streamDataAnalystFaststreamP2P3CandidatesDetailsReport: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(
        ApplicationStatus.PHASE2_TESTS, ApplicationStatus.PHASE2_TESTS_PASSED,
        ApplicationStatus.PHASE2_TESTS_FAILED, ApplicationStatus.PHASE3_TESTS,
        ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER, ApplicationStatus.PHASE3_TESTS_PASSED,
        ApplicationStatus.PHASE3_TESTS_FAILED, ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED
      )
    )
  }

  def streamDataAnalystFaststreamP2CandidatesDetailsReport: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(
        ApplicationStatus.PHASE2_TESTS, ApplicationStatus.PHASE2_TESTS_PASSED,
        ApplicationStatus.PHASE2_TESTS_FAILED
      )
    )
  }

  def streamDataAnalystFaststreamP3CandidatesDetailsReport: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(
        ApplicationStatus.PHASE3_TESTS,
        ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER, ApplicationStatus.PHASE3_TESTS_PASSED,
        ApplicationStatus.PHASE3_TESTS_FAILED, ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED
      )
    )
  }

  def streamDataAnalystFaststreamSIFTCandidatesDetailsReport: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(ApplicationStatus.SIFT, ApplicationStatus.FAILED_AT_SIFT)
    )
  }

  def streamDataAnalystFaststreamFSACCandidatesDetailsReport: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(ApplicationStatus.ASSESSMENT_CENTRE)
    )
  }

  def streamDataAnalystFaststreamFSBCandidatesDetailsReport: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(Faststream),
      Seq(ApplicationStatus.FSB)
    )
  }

  def streamDataAnalystNonFaststreamCandidatesDetailsReport: Action[AnyContent] = {
    streamDataAnalystReport(
      Seq(SdipFaststream, Sdip, Edip),
      _ => true
    )
  }

  private def logDataAnalystRpt(msg: String)= logger.warn(s"streamDataAnalystReport: $msg")

  private def commonEnrichDataAnalystReport(appIds: Seq[String], userIds: Seq[String],
                                            applicationRoutes: Seq[ApplicationRoute],
                                            applicationStatuses: Seq[ApplicationStatus]): Future[Result] =
    enrichDataAnalystReport(appIds, userIds) {
      (numOfSchemes, contactDetails, mediaDetails, questionnaireDetails, siftDetails) =>

        logDataAnalystRpt(s"started fetching the application details stream for ${appIds.size} candidates")
        val applicationDetailsStream = prevYearCandidatesDetailsRepository.dataAnalystApplicationDetailsStream(numOfSchemes, appIds).map { app =>
          createDataAnalystRecord(app, contactDetails, mediaDetails, questionnaireDetails, siftDetails) + "\n"
        }
        logDataAnalystRpt(s"stopped fetching the application details stream for ${appIds.size} candidates")

        val header = Source.single(
          (prevYearCandidatesDetailsRepository.dataAnalystApplicationDetailsHeader(numOfSchemes) ::
            prevYearCandidatesDetailsRepository.dataAnalystContactDetailsHeader ::
            prevYearCandidatesDetailsRepository.mediaHeader ::
            prevYearCandidatesDetailsRepository.questionnaireDetailsHeader ::
            prevYearCandidatesDetailsRepository.dataAnalystSiftAnswersHeader ::
            Nil).mkString(",") + "\n"
        )
        val msg = s"finished loading the data for appRoutes: $applicationRoutes, appStatuses: $applicationStatuses. " +
          s"Now sending the chunked response."
        logDataAnalystRpt(msg)
        Ok.chunked(header ++ applicationDetailsStream)
    }

  type DataAnalystReportBlockType = (Int, CsvExtract[String], CsvExtract[String], CsvExtract[String], CsvExtract[String]) => Result

  // We include all the columns from both parts 1 and 2 of data analyst report
  private def enrichDataAnalystReport(applicationIds: Seq[String], userIds: Seq[String] = Nil)(
    block: DataAnalystReportBlockType): Future[Result] = {
    logDataAnalystRpt(s"started fetching the enriched data for the ${applicationIds.size} candidates")
    for {
      // pt1 data analyst report
      contactDetails <- prevYearCandidatesDetailsRepository.findDataAnalystContactDetails(userIds)
      _ = logDataAnalystRpt(s"enriching data - contactDetails size = ${contactDetails.records.size}")
      mediaDetails <- prevYearCandidatesDetailsRepository.findMediaDetails(userIds)
      _ = logDataAnalystRpt(s"enriching data - mediaDetails size = ${mediaDetails.records.size}")
      // pt2 data analyst report
      questionnaireDetails <- prevYearCandidatesDetailsRepository.findDataAnalystQuestionnaireDetails(applicationIds)
      _ = logDataAnalystRpt(s"enriching data - questionnaireDetails size = ${questionnaireDetails.records.size}")
      siftAnswers <- prevYearCandidatesDetailsRepository.findDataAnalystSiftAnswers(applicationIds)
      _ = logDataAnalystRpt(s"enriching data - siftAnswers size = ${siftAnswers.records.size}")
    } yield {
      logDataAnalystRpt(s"finished fetching the enriched data for the ${applicationIds.size} candidates")
      block(maxSchemes, contactDetails, mediaDetails, questionnaireDetails, siftAnswers)
    }
  }

  private def createDataAnalystRecord(candidateDetails: CandidateDetailsReportItem,
                                        contactDetails: CsvExtract[String],
                                        mediaDetails: CsvExtract[String],
                                        questionnaireDetails: CsvExtract[String],
                                        siftDetails: CsvExtract[String]
                                       ) = {
    (candidateDetails.csvRecord ::
      contactDetails.records.getOrElse(candidateDetails.userId, contactDetails.emptyRecord) ::
      mediaDetails.records.getOrElse(candidateDetails.userId, mediaDetails.emptyRecord) ::
      questionnaireDetails.records.getOrElse(candidateDetails.appId, questionnaireDetails.emptyRecord) ::
      siftDetails.records.getOrElse(candidateDetails.appId, siftDetails.emptyRecord) ::
      Nil).mkString(",")
  }
  //====

  // Includes data from the following collections: application, contact-details and media
  def streamDataAnalystReportPt1: Action[AnyContent] = Action.async { implicit request =>
    enrichDataAnalystReportPt1(
      (numOfSchemes, contactDetails, mediaDetails) => {

        val applicationDetailsStream = prevYearCandidatesDetailsRepository.dataAnalystApplicationDetailsStreamPt1(numOfSchemes).map { app =>
          createDataAnalystRecordPt1(app, contactDetails, mediaDetails) + "\n"
        }

        val header = Source.single(
          (prevYearCandidatesDetailsRepository.dataAnalystApplicationDetailsHeader(numOfSchemes) ::
            prevYearCandidatesDetailsRepository.dataAnalystContactDetailsHeader ::
            prevYearCandidatesDetailsRepository.mediaHeader ::
            Nil).mkString(",") + "\n"
        )
        Ok.chunked(header ++ applicationDetailsStream)
      }
    )
  }

  private def enrichDataAnalystReportPt1(block: (Int, CsvExtract[String], CsvExtract[String]) => Result)= {
    for {
      contactDetails <- prevYearCandidatesDetailsRepository.findDataAnalystContactDetails
      mediaDetails <- prevYearCandidatesDetailsRepository.findMediaDetails
    } yield {
      block(maxSchemes, contactDetails, mediaDetails)
    }
  }

  private def createDataAnalystRecordPt1(candidateDetails: CandidateDetailsReportItem,
                                         contactDetails: CsvExtract[String],
                                         mediaDetails: CsvExtract[String]
                                        ) = {
    (candidateDetails.csvRecord ::
      contactDetails.records.getOrElse(candidateDetails.userId, contactDetails.emptyRecord) ::
      mediaDetails.records.getOrElse(candidateDetails.userId, mediaDetails.emptyRecord) ::
      Nil).mkString(",")
  }

  // Includes data from the following collections: application, questionnaire and sift-answers
  def streamDataAnalystReportPt2: Action[AnyContent] = Action.async { implicit request =>
    enrichDataAnalystReportPt2(
      (questionnaireDetails, siftDetails) => {

        val applicationDetailsStream = prevYearCandidatesDetailsRepository.dataAnalystApplicationDetailsStreamPt2.map { app =>
          createDataAnalystRecordPt2(app, questionnaireDetails, siftDetails) + "\n"
        }

        val header = Source.single(
          ("ApplicationId" ::
            prevYearCandidatesDetailsRepository.questionnaireDetailsHeader ::
            prevYearCandidatesDetailsRepository.dataAnalystSiftAnswersHeader ::
            Nil).mkString(",") + "\n"
        )
        Ok.chunked(header ++ applicationDetailsStream)
      }
    )
  }

  private def enrichDataAnalystReportPt2(block: (CsvExtract[String], CsvExtract[String]) => Result)= {
    for {
      questionnaireDetails <- prevYearCandidatesDetailsRepository.findDataAnalystQuestionnaireDetails
      siftDetails <- prevYearCandidatesDetailsRepository.findDataAnalystSiftAnswers
    } yield {
      block(questionnaireDetails, siftDetails)
    }
  }

  private def createDataAnalystRecordPt2(candidateDetails: CandidateDetailsReportItem,
                                         questionnaireDetails: CsvExtract[String],
                                         siftDetails: CsvExtract[String]
                                        ) = {
    (candidateDetails.csvRecord ::
      questionnaireDetails.records.getOrElse(candidateDetails.appId, questionnaireDetails.emptyRecord) ::
      siftDetails.records.getOrElse(candidateDetails.appId, siftDetails.emptyRecord) ::
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

  def fastPassAwaitingAcceptanceReport(): Action[AnyContent] = Action.async { implicit request =>
    val reports =
      for {
        data <- reportingRepository.fastPassAwaitingAcceptanceReport
      } yield {
        data.map {
          case (appId, cert) => FastPassAwaitingAcceptanceReportItem(appId, cert)
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

          contactDetailsRepository.findByUserIds(applications.map(_.userId)).flatMap { contactDetails =>
            val contactDetailsMap = contactDetailsToMap(contactDetails)

            personalDetailsRepository.findByIds(applications.map(_.applicationId)).map { appPersonalDetailsTuple =>
              applications.map { application =>
                val user = authDetails.find(_.userId == application.userId)
                  .getOrElse(throw new NotFoundException(s"Unable to find auth details for user ${application.userId}"))
                val (_, pd) = appPersonalDetailsTuple.find(_._1 == application.applicationId)
                  .getOrElse(throw UnexpectedException(s"Invalid applicationId ${application.applicationId}"))
                val phoneNumberOpt = contactDetailsMap.get(application.userId).flatMap ( _.phone )

                PreSubmittedReportItem(user, pd.map(_.preferredName), phoneNumberOpt, application)
              }
            }
          }
        }
      })
    }

    reportItemsFut.map(items => Ok(Json.toJson(items.flatten.toList)))
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

  def onlineActiveTestsCountReport: Action[AnyContent] = Action.async { implicit request =>
    reportingRepository.onlineActiveTestCountReport.map { apps =>
      Ok(Json.toJson(apps))
    }
  }

  private def onlineTestPassMarkReportCommon(applications: List[ApplicationForOnlineTestPassMarkReport]):
  Future[List[OnlineTestPassMarkReportItem]] = {

    for {
      siftResults <- applicationSiftRepository.findAllResults
      fsacResults <- assessmentScoresRepository.findAll
      appIds = applications.map(_.applicationId)
      questionnaires <- questionnaireRepository.findForOnlineTestPassMarkReport(appIds)
      fsbScoresAndFeedback <- fsbRepository.findScoresAndFeedback(appIds)
    } yield {
      for {
        application <- applications
        appId = UniqueIdentifier(application.applicationId)
        fsac = fsacResults.find(_.applicationId == appId)
        overallFsacScoreOpt = fsac.map(res => AssessmentScoreCalculator.countAverage(res).overallScore)
        sift = siftResults.find(_.applicationId == application.applicationId)
        q <- questionnaires.get(application.applicationId)
        fsb <- fsbScoresAndFeedback.get(application.applicationId)
      } yield
        OnlineTestPassMarkReportItem(
          ApplicationForOnlineTestPassMarkReportItem(application, fsac, overallFsacScoreOpt, sift, fsb), q
        )
    }
  }

  def onlineTestPassMarkReportFsPhase1Failed(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports = (for {
      applications <- reportingRepository.onlineTestPassMarkReportFsPhase1Failed
    } yield {
      onlineTestPassMarkReportCommon(applications)
    }).flatMap(identity)

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def onlineTestPassMarkReportFsNotPhase1Failed(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports = (for {
      applications <- reportingRepository.onlineTestPassMarkReportFsNotPhase1Failed
    } yield {
      onlineTestPassMarkReportCommon(applications)
    }).flatMap(identity)

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def onlineTestPassMarkReportNonFs(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports = (for {
      applications <- reportingRepository.onlineTestPassMarkReportNonFs
    } yield {
      onlineTestPassMarkReportCommon(applications)
    }).flatMap(identity)

    reports.map { list =>
      Ok(Json.toJson(list))
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
//scalastyle:on
