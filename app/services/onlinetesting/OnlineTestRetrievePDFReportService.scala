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
import connectors.CubiksGatewayClient
import connectors.ExchangeObjects._
import model.OnlineTestCommands._
import play.api.Logger
import play.libs.Akka
import repositories._
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

object OnlineTestRetrievePDFReportService extends OnlineTestRetrievePDFReportService {
  import config.MicroserviceAppConfig._

  val auditService = AuditService
  val appRepository = applicationRepository
  val otRepository = onlineTestRepository
  val otReportPDFRepository = onlineTestPDFReportRepository
  val cubiksGatewayClient = CubiksGatewayClient
  val gatewayConfig = cubiksGatewayConfig
}

trait OnlineTestRetrievePDFReportService {
  implicit def headerCarrier = new HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val auditService: AuditService
  val appRepository: GeneralApplicationRepository
  val otRepository: OnlineTestRepository
  val otReportPDFRepository: OnlineTestPDFReportRepository
  val cubiksGatewayClient: CubiksGatewayClient
  val gatewayConfig: CubiksGatewayConfig

  def norms = Seq(
    gatewayConfig.competenceAssessment,
    gatewayConfig.situationalAssessment,
    gatewayConfig.verbalAndNumericalAssessment
  ).map(a => ReportNorm(a.assessmentId, a.normId)).toList

  def nextApplicationReadyForPDFReportRetrieving(): Future[Option[OnlineTestApplicationWithCubiksUser]] = {
    otRepository.nextApplicationReadyForPDFReportRetrieving
  }

  def retrievePDFReport(application: OnlineTestApplicationWithCubiksUser, waitSecs: Option[Int]): Future[Unit] = {
    val request = OnlineTestApplicationForReportRetrieving(application.cubiksUserId, gatewayConfig.reportConfig.localeCode,
      gatewayConfig.reportConfig.pdfReportId, norms)

    cubiksGatewayClient.getReport(request) flatMap { reportAvailability =>
      val reportId = reportAvailability.reportId
      Logger.debug(s"PDFReportId retrieved from Cubiks: $reportId. Already available: ${reportAvailability.available}")

      // The 5 seconds delay here is because the Cubiks does not generate
      // reports till they are requested - Lazy generation.
      // After the getReportIdMRA we need to wait a few seconds to download the xml report
      akka.pattern.after(waitSecs.getOrElse(5) seconds, Akka.system.scheduler) {
        Logger.debug(s"Delayed downloading PDF report from Cubiks")
        cubiksGatewayClient.downloadPdfReport(reportId) flatMap { results: Array[Byte] =>
          otReportPDFRepository.save(application.applicationId, results).flatMap { _ =>
            otRepository.updatePDFReportSaved(application.applicationId) map { _ =>
              Logger.info(s"PDF Report has been saved for applicationId: ${application.applicationId}")
              audit("OnlineTestPDFReportSaved", application.userId)
            }
          }
        }
      }
    }
  }

  private def audit(event: String, userId: String, emailAddress: Option[String] = None): Unit = {
    // Only log user ID (not email).
    Logger.info(s"$event for user $userId")

    auditService.logEventNoRequest(
      event,
      Map("userId" -> userId) ++ emailAddress.map("email" -> _).toMap
    )
  }
}
