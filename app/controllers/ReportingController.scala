/*
 * Copyright 2017 HM Revenue & Customs
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

import connectors.AuthProviderClient
import model.Commands._
import model.command.ProgressResponse
import model.persisted.ContactDetailsWithId
import model.report._
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, Request }
import repositories.application.{ ReportingMongoRepository, ReportingRepository }
import repositories.contactdetails.ContactDetailsMongoRepository
import repositories.csv.FSACIndicatorCSVRepository
import repositories.{ QuestionnaireRepository, _ }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ReportingController extends ReportingController {
  val reportingRepository: ReportingMongoRepository = repositories.reportingRepository
  val contactDetailsRepository: ContactDetailsMongoRepository = repositories.faststreamContactDetailsRepository
  val questionnaireRepository: QuestionnaireMongoRepository = repositories.questionnaireRepository
  val assessmentScoresRepository: ApplicationAssessmentScoresMongoRepository = repositories.applicationAssessmentScoresRepository
  val mediaRepository: MediaMongoRepository = repositories.mediaRepository
  val fsacIndicatorCSVRepository: FSACIndicatorCSVRepository = repositories.fsacIndicatorCSVRepository
  val authProviderClient = AuthProviderClient
}

trait ReportingController extends BaseController {

  import Implicits._

  val reportingRepository: ReportingRepository
  val contactDetailsRepository: contactdetails.ContactDetailsRepository
  val questionnaireRepository: QuestionnaireRepository
  val assessmentScoresRepository: ApplicationAssessmentScoresRepository
  val mediaRepository: MediaRepository
  val fsacIndicatorCSVRepository: FSACIndicatorCSVRepository
  val authProviderClient: AuthProviderClient

  def internshipReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      applications <- reportingRepository.applicationsForInternshipReport(frameworkId)
      contactDetails <- contactDetailsRepository.findByUserIds(applications.map(_.userId)).map(cdList => contactDetailsToMap(cdList))
    } yield {
      Ok(Json.toJson(buildInternshipReportItems(applications, contactDetails)))
    }
  }

  private def contactDetailsToMap(contactDetailsList: List[ContactDetailsWithId]) = contactDetailsList.map(cd => cd.userId -> cd).toMap

  private def buildInternshipReportItems(
    applications: List[ApplicationForInternshipReport],
    contactDetailsMap: Map[String, ContactDetailsWithId]
  ): List[InternshipReportItem] = {
    applications.map { application =>
      val contactDetails = contactDetailsMap.getOrElse(
        application.userId,
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

  private def buildAnalyticalSchemesReportItems(
    applications: List[ApplicationForAnalyticalSchemesReport],
    contactDetailsMap: Map[String, ContactDetailsWithId]
  ): List[AnalyticalSchemesReportItem] = {
    applications.map { application =>
      val contactDetails = contactDetailsMap.getOrElse(
        application.userId,
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
          medias.get(application.userId).map { m => MediaReportItem(m.media) }
        )
      }
    }
    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def timeToOfferReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports = for {
      applicationsForTimeToOffer <- reportingRepository.candidatesForTimeToOfferReport
      appsByUserId <- reportingRepository.diversityReport(frameworkId).map(_.groupBy(_.userId).mapValues(_.head))
      questionnairesByAppId <- questionnaireRepository.findAllForDiversityReport
      mediasByUserId <- mediaRepository.findAll()
      candidatesByUserId <- contactDetailsRepository.findAll.map(_.groupBy(_.userId).mapValues(_.head))
    } yield {
      applicationsForTimeToOffer.map { appTimeToOffer =>
        val userId = appTimeToOffer.userId
        val application = appsByUserId(userId)
        val appId = application.applicationId
        val contactDetails = candidatesByUserId.get(userId)
        val email = contactDetails.map(_.email)
        val diversityReportItem = DiversityReportItem(
          ApplicationForDiversityReportItem.create(application),
          questionnairesByAppId.get(appId),
          mediasByUserId.get(userId).map(m => MediaReportItem(m.media))
        )

        TimeToOfferItem(appTimeToOffer, email, diversityReportItem)
      }
    }
    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def onlineTestPassMarkReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports =
      for {
        applications <- reportingRepository.onlineTestPassMarkReport(frameworkId)
        questionnaires <- questionnaireRepository.findForOnlineTestPassMarkReport
      } yield {
        for {
          a <- applications
          q <- questionnaires.get(a.applicationId)
        } yield OnlineTestPassMarkReportItem(ApplicationForOnlineTestPassMarkReportItem.create(a), q)
      }
    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  // This method is not used at this moment
  def nonSubmittedAppsReport(frameworkId: String): Action[AnyContent] =
    preferencesAndContactReports(nonSubmittedOnly = true)(frameworkId)

  // This method is not used at this moment
  def createPreferencesAndContactReports(frameworkId: String): Action[AnyContent] =
    preferencesAndContactReports(nonSubmittedOnly = false)(frameworkId)

  // This method is not used at this moment
  def applicationAndUserIdsReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    reportingRepository.allApplicationAndUserIds(frameworkId).map { list =>
      Ok(Json.toJson(list))
    }
  }

  // This method is not used at this moment
  //scalastyle:off method.length
  private def preferencesAndContactReports(nonSubmittedOnly: Boolean)(frameworkId: String) = Action.async { implicit request =>
    def mergeApplications(
      users: Map[String, PreferencesWithContactDetails],
      contactDetails: List[ContactDetailsWithId],
      applications: List[(String, IsNonSubmitted, PreferencesWithContactDetails)]
    ) = {
      def getStatus(progress: Option[ProgressResponse]): String = progress match {
        case Some(p) => ProgressStatusesReportLabels.progressStatusNameInReports(p)
        case None => ProgressStatusesReportLabels.RegisteredProgress
      }

      val contactDetailsMap = contactDetails.groupBy(_.userId).mapValues(_.headOption)
      val applicationsMap = applications
        .groupBy { case (userId, _, _) => userId }
        .mapValues(_.headOption.map { case (_, _, app) => app })

      users.map {
        case (userId, user) =>
          val cd = contactDetailsMap.getOrElse(userId, None)
          val app = applicationsMap.getOrElse(userId, None)
          val noAppProgress: Option[String] = Some(getStatus(None))

          (userId, PreferencesWithContactDetails(
            user.firstName,
            user.lastName,
            user.preferredName,
            user.email,
            cd.flatMap(_.phone),
            app.flatMap(_.location1),
            app.flatMap(_.location1Scheme1),
            app.flatMap(_.location1Scheme2),
            app.flatMap(_.location2),
            app.flatMap(_.location2Scheme1),
            app.flatMap(_.location2Scheme2),
            app.fold(noAppProgress)(_.progress),
            app.flatMap(_.timeApplicationCreated)
          ))
      }
    }
    def getApplicationsNotToIncludeInReport(
      createdApplications: List[(String, IsNonSubmitted, PreferencesWithContactDetails)],
      nonSubmittedOnly: Boolean
    ) = {
      if (nonSubmittedOnly) {
        createdApplications.collect { case (userId, false, _) => userId }.toSet
      } else {
        Set.empty[String]
      }
    }

    def getAppsFromAuthProvider(candidateExclusionSet: Set[String])(implicit request: Request[AnyContent]) = {
      for {
        allCandidates <- authProviderClient.candidatesReport
      } yield {
        allCandidates.filterNot(c => candidateExclusionSet.contains(c.userId)).map(c =>
          c.userId -> PreferencesWithContactDetails(Some(c.firstName), Some(c.lastName), c.preferredName, Some(c.email),
            None, None, None, None, None, None, None, None, None)).toMap
      }
    }

    for {
      applications <- reportingRepository.applicationsReport(frameworkId)
      applicationsToExclude = getApplicationsNotToIncludeInReport(applications, nonSubmittedOnly)
      users <- getAppsFromAuthProvider(applicationsToExclude)
      contactDetails <- contactDetailsRepository.findAll
      reports = mergeApplications(users, contactDetails, applications)
    } yield {
      Ok(Json.toJson(reports.values))
    }
  }
  //scalastyle:on method.length
}
