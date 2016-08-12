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

package services.onlinetesting

import _root_.services.AuditService
import config.CubiksGatewayConfig
import connectors.ExchangeObjects._
import connectors.{CSREmailClient, CubiksGatewayClient, EmailClient}
import controllers.OnlineTest
import factories.{DateTimeFactory, UUIDFactory}
import model.OnlineTestCommands._
import model.PersistedObjects.CandidateTestReport
import org.joda.time.DateTime
import play.api.Logger
import play.libs.Akka
import repositories._
import repositories.application.{GeneralApplicationRepository, OnlineTestRepository}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object OnlineTestService extends OnlineTestService {
  import config.MicroserviceAppConfig._
  val appRepository = applicationRepository
  val cdRepository = contactDetailsRepository
  val otRepository = onlineTestRepository
  val otprRepository = onlineTestPDFReportRepository
  val trRepository = testReportRepository
  val cubiksGatewayClient = CubiksGatewayClient
  val tokenFactory = UUIDFactory
  val onlineTestInvitationDateFactory = DateTimeFactory
  val emailClient = CSREmailClient
  val auditService = AuditService
  val gatewayConfig = cubiksGatewayConfig
}

trait OnlineTestService {
  implicit def headerCarrier = new HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val appRepository: GeneralApplicationRepository
  val cdRepository: ContactDetailsRepository
  val otRepository: OnlineTestRepository
  val otprRepository: OnlineTestPDFReportRepository
  val trRepository: TestReportRepository
  val cubiksGatewayClient: CubiksGatewayClient
  val emailClient: EmailClient
  val auditService: AuditService
  val tokenFactory: UUIDFactory
  val onlineTestInvitationDateFactory: DateTimeFactory
  val gatewayConfig: CubiksGatewayConfig

  def norms = Seq(
    gatewayConfig.competenceAssessment,
    gatewayConfig.situationalAssessment,
    gatewayConfig.verbalAndNumericalAssessment
  ).map(a => ReportNorm(a.assessmentId, a.normId)).toList

  def nextApplicationReadyForOnlineTesting() = {
    otRepository.nextApplicationReadyForOnlineTesting
  }

  def getOnlineTest(userId: String): Future[OnlineTest] = {
    for {
      onlineTestDetails <- otRepository.getOnlineTestDetails(userId)
      candidate <- appRepository.findCandidateByUserId(userId)
      hasReport <- otprRepository.hasReport(candidate.get.applicationId.get)
    } yield {
      OnlineTest(
        onlineTestDetails.inviteDate,
        onlineTestDetails.expireDate,
        onlineTestDetails.onlineTestLink,
        onlineTestDetails.cubiksEmailAddress,
        onlineTestDetails.isOnlineTestEnabled,
        hasReport
      )
    }
  }

  def registerAndInviteApplicant(application: OnlineTestApplication): Future[Unit] = {
    val token = tokenFactory.generateUUID()

    val invitationProcess = for {
      userId <- registerApplicant(application, token)
      invitation <- inviteApplicant(application, token, userId)
      (invitationDate, expirationDate) = onlineTestDates
      _ <- trRepository.remove(application.applicationId)
      _ <- otprRepository.remove(application.applicationId)
      emailAddress <- candidateEmailAddress(application)
      _ <- emailInviteToApplicant(application, emailAddress, invitationDate)
    } yield OnlineTestProfile(
      invitation.userId,
      token,
      invitation.authenticateUrl,
      invitationDate,
      expirationDate,
      invitation.participantScheduleId
    )

    invitationProcess.flatMap(
      markAsCompleted(application)
    )
  }

  def nextApplicationReadyForReportRetrieving: Future[Option[OnlineTestApplicationWithCubiksUser]] = {
    otRepository.nextApplicationReadyForReportRetriving
  }

  def retrieveTestResult(application: OnlineTestApplicationWithCubiksUser, waitSecs: Option[Int]): Future[Unit] = {
    val request = OnlineTestApplicationForReportRetrieving(application.cubiksUserId, gatewayConfig.reportConfig.localeCode,
      gatewayConfig.reportConfig.xmlReportId, norms)

    cubiksGatewayClient.getReport(request) flatMap { reportAvailability =>
      val reportId = reportAvailability.reportId
      Logger.debug(s"ReportId retrieved from Cubiks: $reportId. Already available: ${reportAvailability.available}")

      // The 5 seconds delay here is because the Cubiks does not generate
      // reports till they are requested - Lazy generation.
      // After the getReportIdMRA we need to wait a few seconds to download the xml report
      akka.pattern.after(waitSecs.getOrElse(5) seconds, Akka.system.scheduler) {
        Logger.debug(s"Delayed downloading XML report from Cubiks")

        appRepository.gisByApplication(application.applicationId).flatMap { gis =>
          Logger.debug(s"Retrieved GIS for user ${application.userId}: application ${application.userId}: GIS: $gis")
          cubiksGatewayClient.downloadXmlReport(reportId) flatMap { results: Map[String, TestResult] =>
            val cr = toCandidateTestReport(application.applicationId, results)
            if (gatewayConfig.reportConfig.suppressValidation || cr.isValid(gis)) {

              trRepository.saveOnlineTestReport(cr).flatMap { _ =>
                otRepository.updateXMLReportSaved(application.applicationId) map { _ =>
                  Logger.info(s"Report has been saved for applicationId: ${application.applicationId}")
                  audit("OnlineTestXmlReportSaved", application.userId)
                }
              }
            } else {
              val cubiksUserId = application.cubiksUserId
              val applicationId = application.applicationId

              val msg = s"Cubiks report $reportId does not have a valid report for " +
                s"Cubiks User ID:$cubiksUserId and Application ID:$applicationId"

              Logger.error(msg)
              throw new IllegalStateException(msg)
            }
          }
        }
      }
    }
  }

  private def registerApplicant(application: OnlineTestApplication, token: String): Future[Int] = {
    val preferredName = CubiksSanitizer.sanitizeFreeText(application.preferredName)
    val registerApplicant = RegisterApplicant(preferredName, "", token + "@" + gatewayConfig.emailDomain)
    cubiksGatewayClient.registerApplicant(registerApplicant).map { registration =>
      audit("UserRegisteredForOnlineTest", application.userId)
      registration.userId
    }
  }

  private def inviteApplicant(application: OnlineTestApplication, token: String, userId: Int): Future[Invitation] = {
    val scheduleId = getScheduleIdForApplication(application)
    val inviteApplicant = buildInviteApplication(application, token, userId, scheduleId)
    cubiksGatewayClient.inviteApplicant(inviteApplicant).map { invitation =>
      audit("UserInvitedToOnlineTest", application.userId)
      invitation
    }
  }

  private def emailInviteToApplicant(application: OnlineTestApplication, emailAddress: String, invitationDate: DateTime): Future[Unit] = {
    val expirationDate = calculateExpireDate(invitationDate)
    val preferredName = application.preferredName
    emailClient.sendOnlineTestInvitation(emailAddress, preferredName, expirationDate).map { _ =>
      audit("OnlineTestInvitationEmailSent", application.userId, Some(emailAddress))
    }
  }

  private def markAsCompleted(application: OnlineTestApplication)(onlineTestProfile: OnlineTestProfile): Future[Unit] =
    otRepository.storeOnlineTestProfileAndUpdateStatusToInvite(application.applicationId, onlineTestProfile).map { _ =>
      audit("OnlineTestInvitationProcessComplete", application.userId)
    }

  private def candidateEmailAddress(application: OnlineTestApplication): Future[String] =
    cdRepository.find(application.userId).map(_.email)

  private def onlineTestDates: (DateTime, DateTime) = {
    val invitationDate = onlineTestInvitationDateFactory.nowLocalTimeZone
    val expirationDate = calculateExpireDate(invitationDate)
    (invitationDate, expirationDate)
  }

  private def audit(event: String, userId: String, emailAddress: Option[String] = None): Unit = {
    // Only log user ID (not email).
    Logger.info(s"$event for user $userId")

    auditService.logEventNoRequest(
      event,
      Map("userId" -> userId) ++ emailAddress.map("email" -> _).toMap
    )
  }

  private def calculateExpireDate(invitationDate: DateTime) = invitationDate.plusDays(7)

  private[services] def getScheduleIdForApplication(application: OnlineTestApplication) = {
    if (application.guaranteedInterview) {
      gatewayConfig.scheduleIds.gis
    } else {
      gatewayConfig.scheduleIds.standard
    }
  }

  private[services] def getTimeAdjustments(application: OnlineTestApplication): Option[TimeAdjustments] = {
    if (application.timeAdjustments.isEmpty) {
      None
    } else {
      val config = gatewayConfig.verbalAndNumericalAssessment
      Some(TimeAdjustments(
        config.assessmentId,
        config.verbalSectionId,
        config.numericalSectionId,
        getAdjustedTime(
          config.verbalTimeInMinutesMinimum,
          config.verbalTimeInMinutesMaximum,
          application.timeAdjustments.get.verbalTimeAdjustmentPercentage
        ),
        getAdjustedTime(
          config.numericalTimeInMinutesMinimum,
          config.numericalTimeInMinutesMaximum,
          application.timeAdjustments.get.numericalTimeAdjustmentPercentage
        )
      ))
    }
  }

  private[services] def getAdjustedTime(minimum: Int, maximum: Int, percentageToIncrease: Int) = {
    val adjustedValue = math.ceil(minimum.toDouble * (1 + percentageToIncrease / 100.0))
    math.min(adjustedValue, maximum).toInt
  }

  private[services] def buildInviteApplication(application: OnlineTestApplication, token: String, userId: Int, scheduleId: Int) = {
    val onlineTestCompletedUrl = gatewayConfig.candidateAppUrl + "/fset-fast-stream/online-tests/complete/" + token
    if (application.guaranteedInterview) {
      InviteApplicant(scheduleId, userId, onlineTestCompletedUrl, None, None)
    } else {
      val timeAdjustments = getTimeAdjustments(application)
      InviteApplicant(scheduleId, userId, onlineTestCompletedUrl, None, timeAdjustments)
    }
  }

  private def toCandidateTestReport(appId: String, tests: Map[String, TestResult]) = {
    val VerbalTestName = "Logiks Verbal and Numerical - Verbal"
    val NumericalTestName = "Logiks Verbal and Numerical - Numerical"
    val CompetencyTestName = "Cubiks Factors"
    val SituationalTestName = "Civil Service Fast Track Apprentice SJQ"

    CandidateTestReport(
      appId, "XML",
      tests.get(CompetencyTestName),
      tests.get(NumericalTestName),
      tests.get(VerbalTestName),
      tests.get(SituationalTestName)
    )
  }
}
