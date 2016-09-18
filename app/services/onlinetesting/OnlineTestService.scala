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
import common.FutureEx
import config.CubiksGatewayConfig
import connectors.ExchangeObjects._
import connectors.{ CSREmailClient, CubiksGatewayClient, EmailClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands._
import model.PersistedObjects.CandidateTestReport
import model.exchange.Phase1TestProfileWithNames
import org.joda.time.DateTime
import play.api.Logger
import play.libs.Akka
import repositories._
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

object OnlineTestService extends OnlineTestService {
  import config.MicroserviceAppConfig._
  val appRepository = applicationRepository
  val cdRepository = contactDetailsRepository
  val otRepository = onlineTestRepository
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

  def getPhase1TestProfile(userId: String): Future[Option[Phase1TestProfileWithNames]] = {
    appRepository.findCandidateByUserId(userId).flatMap {
      case Some(candidate) if candidate.applicationId.isDefined =>
        for {
          phase1 <- otRepository.getPhase1TestProfile(candidate.applicationId.get)
        } yield {
          phase1 map { p =>
            val sjqTests = p.activeTests filter (_.scheduleId == sjq)
            val bqTests = p.activeTests filter (_.scheduleId == bq)
            require(sjqTests.length <= 1)
            require(bqTests.length <= 1)

            Phase1TestProfileWithNames(p.expirationDate, Map()
              ++ (if (sjqTests.nonEmpty) Map("sjq" -> sjqTests.head) else Map())
              ++ (if (bqTests.nonEmpty) Map("bq" -> bqTests.head) else Map())
            )
          }
        }
      case None => Future.successful(None)
    }
  }

  private def sjq = gatewayConfig.onlineTestConfig.scheduleIds("sjq")

  private def bq = gatewayConfig.onlineTestConfig.scheduleIds("bq")

  def registerAndInviteForTestGroup(application: OnlineTestApplication): Future[Unit] = {
    registerAndInviteForTestGroup(application, getScheduleNamesForApplication(application))
  }

  def registerAndInviteForTestGroup(application: OnlineTestApplication, scheduleNames: List[String]): Future[Unit] = {
    val (invitationDate, expirationDate) = calcOnlineTestDates

    def mapValue[T]( f: Future[T] ): Future[Try[T]] = {
      val prom = Promise[Try[T]]()
      f onComplete prom.success
      prom.future
    }

    // TODO work out a better way to do this
    // The problem is that the standard future sequence returns at the point when the first future has failed
    // but doesn't actually wait until all futures are complete. This can be problematic for tests which assert
    // the something has or hasn't worked. It is also a bit nasty in production where processing can still be
    // going on in the background.
    // The approach to fixing it here is to generate futures that return Try[A] and then all futures will be
    // traversed. Afterward, we look at the results and clear up the mess
    val registerAndInvite = FutureEx.traverseToTry(scheduleNames){ sn =>
      val scheduleId = scheduleIdByName(sn)
      registerAndInviteApplicant(application, scheduleId, invitationDate, expirationDate)
    }

    val registerAndInviteProcess = registerAndInvite.flatMap { phase1TestsRegs =>
      phase1TestsRegs.collect { case Failure(e) => throw e }
      val successfullyRegisteredTests = phase1TestsRegs.collect { case Success(t) => t }.toList
      markAsInvited(application)(Phase1TestProfile(expirationDate = expirationDate, tests = successfullyRegisteredTests))
    }

    for {
      _ <- registerAndInviteProcess
      emailAddress <- candidateEmailAddress(application)
      _ <- emailInviteToApplicant(application, emailAddress, invitationDate, expirationDate)
    } yield audit("OnlineTestInvitationProcessComplete", application.userId)
  }

  private def registerAndInviteApplicant(application: OnlineTestApplication, scheduleId: Int,
                                         invitationDate: DateTime, expirationDate: DateTime): Future[Phase1Test] = {
    val authToken = tokenFactory.generateUUID()

    for {
      userId <- registerApplicant(application, authToken)
      invitation <- inviteApplicant(application, authToken, userId, scheduleId)
      _ <- trRepository.remove(application.applicationId)
    } yield {
      Phase1Test(scheduleId = scheduleId,
        usedForResults = true,
        cubiksUserId = invitation.userId,
        token = authToken,
        invitationDate = invitationDate,
        participantScheduleId = invitation.participantScheduleId,
        testUrl = invitation.authenticateUrl
      )
    }
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
              // TODO FAST STREAM FIX ME
              Future.successful(Unit)
              //trRepository.saveOnlineTestReport(cr).flatMap { _ =>
              //  otRepository.updateXMLReportSaved(application.applicationId) map { _ =>
              //    Logger.info(s"Report has been saved for applicationId: ${application.applicationId}")
              //    audit("OnlineTestXmlReportSaved", application.userId)
              //  }
              //}
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

  def registerApplicant(application: OnlineTestApplication, token: String): Future[Int] = {
    val preferredName = CubiksSanitizer.sanitizeFreeText(application.preferredName)
    val registerApplicant = RegisterApplicant(preferredName, "", token + "@" + gatewayConfig.emailDomain)
    cubiksGatewayClient.registerApplicant(registerApplicant).map { registration =>
      audit("UserRegisteredForOnlineTest", application.userId)
      registration.userId
    }
  }

  private def inviteApplicant(application: OnlineTestApplication, authToken: String, userId: Int, scheduleId: Int): Future[Invitation] = {

    val inviteApplicant = buildInviteApplication(application, authToken, userId, scheduleId)
    cubiksGatewayClient.inviteApplicant(inviteApplicant).map { invitation =>
      audit("UserInvitedToOnlineTest", application.userId)
      invitation
    }
  }

  private def emailInviteToApplicant(application: OnlineTestApplication, emailAddress: String,
                                     invitationDate: DateTime, expirationDate: DateTime): Future[Unit] = {
    val preferredName = application.preferredName
    emailClient.sendOnlineTestInvitation(emailAddress, preferredName, expirationDate).map { _ =>
      audit("OnlineTestInvitationEmailSent", application.userId, Some(emailAddress))
    }
  }

  private def markAsInvited(application: OnlineTestApplication)
                           (newOnlineTestProfile: Phase1TestProfile): Future[Unit] = for {
    currentOnlineTestProfile <- otRepository.getPhase1TestProfile(application.applicationId)
    updatedOnlineTestProfile = merge(currentOnlineTestProfile, newOnlineTestProfile)
    _ <- otRepository.insertPhase1TestProfile(application.applicationId, updatedOnlineTestProfile)
  } yield {
    audit("OnlineTestInvited", application.userId)
  }

  private def merge(currentProfile: Option[Phase1TestProfile], newProfile: Phase1TestProfile): Phase1TestProfile = currentProfile match {
    case None =>
      newProfile
    case Some(profile) =>
      val scheduleIdsToArchive = newProfile.tests.map(_.scheduleId)
      val existingTests = profile.tests.map(t =>
        if (scheduleIdsToArchive.contains(t.scheduleId)) {
          t.copy(usedForResults = false)
        } else {
          t
        }
      )
      Phase1TestProfile(newProfile.expirationDate, existingTests ++ newProfile.tests)
  }

  private def candidateEmailAddress(application: OnlineTestApplication): Future[String] =
    cdRepository.find(application.userId).map(_.email)

  private def calcOnlineTestDates: (DateTime, DateTime) = {
    val invitationDate = onlineTestInvitationDateFactory.nowLocalTimeZone
    val expirationDate = invitationDate.plusDays(gatewayConfig.onlineTestConfig.expiryTimeInDays)
    (invitationDate, expirationDate)
  }

  private def audit(event: String, userId: String, emailAddress: Option[String] = None): Unit = {
    Logger.info(s"$event for user $userId")

    auditService.logEventNoRequest(
      event,
      Map("userId" -> userId) ++ emailAddress.map("email" -> _).toMap
    )
  }

  private def getScheduleNamesForApplication(application: OnlineTestApplication) = {
    if (application.guaranteedInterview) {
      gatewayConfig.onlineTestConfig.gis
    } else {
      gatewayConfig.onlineTestConfig.standard
    }
  }

  private def scheduleIdByName(name: String): Int = {
    gatewayConfig.onlineTestConfig.scheduleIds.getOrElse(name, throw new IllegalArgumentException(s"Incorrect test name: $name"))
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
    val scheduleCompletionUrl = gatewayConfig.candidateAppUrl + "/fset-fast-stream/online-tests/complete/" + token
    if (application.guaranteedInterview) {
      InviteApplicant(scheduleId, userId, scheduleCompletionUrl, resultsURL = None, timeAdjustments = None)
    } else {
      val timeAdjustments = getTimeAdjustments(application)
      InviteApplicant(scheduleId, userId, scheduleCompletionUrl, resultsURL = None, timeAdjustments)
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
