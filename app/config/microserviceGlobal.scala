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

package config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.{ Application, Logger, Play }
import scheduler.allocation.ConfirmAttendanceReminderJob
import scheduler.assessment._
import scheduler.onlinetesting._
import scheduler.reporting.DiversityMonitoringJob
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.config.{ AppName, ControllerConfig }
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.scheduling.{ RunningOfScheduledJobs, ScheduledJob }

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object MicroserviceAuditFilter extends AuditFilter with AppName {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) =
    false // Disable implicit _inbound_ auditing.
}

object MicroserviceLoggingFilter extends LoggingFilter {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

trait Scheduler extends RunningOfScheduledJobs {
  import config.MicroserviceAppConfig._

  private lazy val sendPhase1InvitationJob: Option[ScheduledJob] =
    if (sendPhase1InvitationJobConfigValues.enabled) Some(SendPhase1InvitationJob) else {
      Logger.warn("Send phase 1 invitation job is disabled")
      None
    }

  private lazy val sendPhase2InvitationJob: Option[ScheduledJob] =
    if (sendPhase2InvitationJobConfigValues.enabled) Some(SendPhase2InvitationJob) else {
      Logger.warn("Send phase 2 invitation job is disabled")
      None
    }

  private lazy val sendPhase3InvitationJob: Option[ScheduledJob] =
    if (sendPhase3InvitationJobConfigValues.enabled) Some(SendPhase3InvitationJob) else {
      Logger.warn("Send phase 3 invitation job is disabled")
      None
    }

  private lazy val firstPhase1ReminderExpiringTestJob: Option[ScheduledJob] =
    if (firstPhase1ReminderJobConfigValues.enabled) Some(FirstPhase1ReminderExpiringTestJob) else {
      Logger.warn("First Phase1 reminder for expiring test job is disabled")
      None
    }

  private lazy val secondPhase1ReminderExpiringTestJob: Option[ScheduledJob] =
    if (secondPhase1ReminderJobConfigValues.enabled) Some(SecondPhase1ReminderExpiringTestJob) else {
      Logger.warn("Second Phase1 reminder for expiring test job is disabled")
      None
    }

  private lazy val expirePhase1TestJob: Option[ScheduledJob] =
    if (expirePhase1TestJobConfigValues.enabled) Some(ExpirePhase1TestJob) else {
      Logger.warn("Expire Phase1 test job is disabled")
      None
    }

  private lazy val failedOnlineTestJob: Option[ScheduledJob] =
    if (failedOnlineTestJobConfigValues.enabled) Some(FailedOnlineTestJob) else {
      Logger.warn("Failed online test job is disabled")
      None
    }

  private lazy val retrieveResultsJob: Option[ScheduledJob] =
    if (retrieveResultsJobConfigValues.enabled) Some(RetrieveResultsJob) else {
      Logger.warn("Retrieve results job is disabled")
      None
    }

  private lazy val evaluatePhase1ResultJob: Option[ScheduledJob] =
    if (evaluatePhase1ResultJobConfigValues.enabled) Some(EvaluatePhase1ResultJob) else {
      Logger.warn("evaluate phase1 result job is disabled")
      None
    }

  private lazy val diversityMonitoringJob: Option[ScheduledJob] =
    if (diversityMonitoringJobConfigValues.enabled) Some(DiversityMonitoringJob) else {
      Logger.warn("diversity monitoring job is disabled")
      None
    }

  private lazy val confirmAttendanceReminderJob: Option[ScheduledJob] =
    if (confirmAttendanceReminderJobConfigValues.enabled) Some(ConfirmAttendanceReminderJob) else {
      Logger.warn("confirm attendance reminder job is disabled")
      None
    }

  private lazy val evaluateAssessmentScoreJob: Option[ScheduledJob] =
    if (evaluateAssessmentScoreJobConfigValues.enabled) Some(EvaluateAssessmentScoreJob) else {
      Logger.warn("evaluate assessment score job is disabled")
      None
    }

  private lazy val notifyAssessmentCentrePassedOrFailedJob: Option[ScheduledJob] =
    if (notifyAssessmentCentrePassedOrFailedJobConfigValues.enabled) Some(NotifyAssessmentCentrePassedOrFailedJob) else {
      Logger.warn("notify assessment centre passsed or failed job is disabled")
      None
    }

  private[config] def sendPhase1InvitationJobConfigValues = sendPhase1InvitationJobConfig
  private[config] def sendPhase2InvitationJobConfigValues = sendPhase2InvitationJobConfig
  private[config] def sendPhase3InvitationJobConfigValues = sendPhase3InvitationJobConfig
  private[config] def expirePhase1TestJobConfigValues = expirePhase1TestJobConfig
  private[config] def firstPhase1ReminderJobConfigValues = firstPhase1ReminderJobConfig
  private[config] def secondPhase1ReminderJobConfigValues = secondPhase1ReminderJobConfig
  private[config] def failedOnlineTestJobConfigValues = failedOnlineTestJobConfig
  private[config] def retrieveResultsJobConfigValues = retrieveResultsJobConfig
  private[config] def evaluatePhase1ResultJobConfigValues = evaluatePhase1ResultJobConfig
  private[config] def diversityMonitoringJobConfigValues = diversityMonitoringJobConfig
  private[config] def confirmAttendanceReminderJobConfigValues = confirmAttendanceReminderJobConfig
  private[config] def evaluateAssessmentScoreJobConfigValues = evaluateAssessmentScoreJobConfig
  private[config] def notifyAssessmentCentrePassedOrFailedJobConfigValues = notifyAssessmentCentrePassedOrFailedJobConfig

  lazy val scheduledJobs = List(sendPhase1InvitationJob, sendPhase2InvitationJob, sendPhase3InvitationJob, firstPhase1ReminderExpiringTestJob,
    secondPhase1ReminderExpiringTestJob, expirePhase1TestJob, failedOnlineTestJob, retrieveResultsJob, evaluatePhase1ResultJob,
    diversityMonitoringJob, confirmAttendanceReminderJob, evaluateAssessmentScoreJob, notifyAssessmentCentrePassedOrFailedJob).flatten
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with Scheduler {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application) = app.configuration.getConfig("microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None
}
