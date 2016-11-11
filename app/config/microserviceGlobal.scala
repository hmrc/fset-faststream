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
import scheduler.fixer.FixerJob
import scheduler.onlinetesting._
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

  private lazy val firstPhase2ReminderExpiringTestJob: Option[ScheduledJob] =
    if (firstPhase2ReminderJobConfigValues.enabled) Some(FirstPhase2ReminderExpiringTestJob) else {
      Logger.warn("First Phase2 reminder for expiring test job is disabled")
      None
    }

  private lazy val secondPhase2ReminderExpiringTestJob: Option[ScheduledJob] =
    if (secondPhase2ReminderJobConfigValues.enabled) Some(SecondPhase2ReminderExpiringTestJob) else {
      Logger.warn("Second Phase2 reminder for expiring test job is disabled")
      None
    }

  private lazy val firstPhase3ReminderExpiringTestJob: Option[ScheduledJob] =
    if (firstPhase3ReminderJobConfigValues.enabled) Some(FirstPhase3ReminderExpiringTestJob) else {
      Logger.warn("First Phase3 reminder for expiring test job is disabled")
      None
    }

  private lazy val secondPhase3ReminderExpiringTestJob: Option[ScheduledJob] =
    if (secondPhase3ReminderJobConfigValues.enabled) Some(SecondPhase3ReminderExpiringTestJob) else {
      Logger.warn("Second Phase3 reminder for expiring test job is disabled")
      None
    }

  private lazy val expirePhase1TestJob: Option[ScheduledJob] =
    if (expirePhase1TestJobConfigValues.enabled) Some(ExpirePhase1TestJob) else {
      Logger.warn("Expire Phase1 test job is disabled")
      None
    }

  private lazy val expirePhase2TestJob: Option[ScheduledJob] =
    if (expirePhase2TestJobConfigValues.enabled) Some(ExpirePhase2TestJob) else {
      Logger.warn("Expire Phase2 test job is disabled")
      None
    }

  private lazy val failedPhase1TestJob: Option[ScheduledJob] =
    if (failedPhase1TestJobConfigValues.enabled) Some(FailedPhase1TestJob) else {
      Logger.warn("Failed Phase1 online test job is disabled")
      None
    }

  private lazy val failedPhase2TestJob: Option[ScheduledJob] =
    if (failedPhase2TestJobConfigValues.enabled) Some(FailedPhase2TestJob) else {
      Logger.warn("Failed Phase2 online test job is disabled")
      None
    }

  private lazy val retrievePhase1ResultsJob: Option[ScheduledJob] =
    if (retrievePhase1ResultsJobConfigValues.enabled) Some(RetrievePhase1ResultsJob) else {
      Logger.warn("Retrieve phase1 results job is disabled")
      None
    }

  private lazy val retrievePhase2ResultsJob: Option[ScheduledJob] =
    if (retrievePhase2ResultsJobConfigValues.enabled) Some(RetrievePhase2ResultsJob) else {
      Logger.warn("Retrieve phase2 results job is disabled")
      None
    }

  private lazy val evaluatePhase1ResultJob: Option[ScheduledJob] =
    if (evaluatePhase1ResultJobConfigValues.enabled) Some(EvaluatePhase1ResultJob) else {
      Logger.warn("evaluate phase1 result job is disabled")
      None
    }

  private lazy val evaluatePhase2ResultJob: Option[ScheduledJob] =
    if (evaluatePhase2ResultJobConfigValues.enabled) Some(EvaluatePhase2ResultJob) else {
      Logger.warn("evaluate phase2 result job is disabled")
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

  private lazy val fixerJob: Option[ScheduledJob] =
    if (fixerJobConfigValues.enabled) Some(FixerJob) else {
      Logger.warn("fixer job is disabled")
      None
    }

  private[config] def sendPhase1InvitationJobConfigValues = sendPhase1InvitationJobConfig
  private[config] def sendPhase2InvitationJobConfigValues = sendPhase2InvitationJobConfig
  private[config] def sendPhase3InvitationJobConfigValues = sendPhase3InvitationJobConfig
  private[config] def expirePhase1TestJobConfigValues = expirePhase1TestJobConfig
  private[config] def expirePhase2TestJobConfigValues = expirePhase2TestJobConfig
  private[config] def firstPhase1ReminderJobConfigValues = firstPhase1ReminderJobConfig
  private[config] def secondPhase1ReminderJobConfigValues = secondPhase1ReminderJobConfig
  private[config] def firstPhase2ReminderJobConfigValues = firstPhase2ReminderJobConfig
  private[config] def secondPhase2ReminderJobConfigValues = secondPhase2ReminderJobConfig
  private[config] def firstPhase3ReminderJobConfigValues = firstPhase3ReminderJobConfig
  private[config] def secondPhase3ReminderJobConfigValues = secondPhase3ReminderJobConfig
  private[config] def failedPhase1TestJobConfigValues = failedPhase1TestJobConfig
  private[config] def failedPhase2TestJobConfigValues = failedPhase2TestJobConfig
  private[config] def retrievePhase1ResultsJobConfigValues = retrievePhase1ResultsJobConfig
  private[config] def retrievePhase2ResultsJobConfigValues = retrievePhase2ResultsJobConfig
  private[config] def evaluatePhase1ResultJobConfigValues = evaluatePhase1ResultJobConfig
  private[config] def evaluatePhase2ResultJobConfigValues = evaluatePhase2ResultJobConfig
  private[config] def confirmAttendanceReminderJobConfigValues = confirmAttendanceReminderJobConfig
  private[config] def evaluateAssessmentScoreJobConfigValues = evaluateAssessmentScoreJobConfig
  private[config] def notifyAssessmentCentrePassedOrFailedJobConfigValues = notifyAssessmentCentrePassedOrFailedJobConfig
  private[config] def fixerJobConfigValues = fixerJobConfig

  lazy val scheduledJobs = List(sendPhase1InvitationJob, sendPhase2InvitationJob, sendPhase3InvitationJob,
    firstPhase1ReminderExpiringTestJob, secondPhase1ReminderExpiringTestJob, firstPhase2ReminderExpiringTestJob,
    secondPhase2ReminderExpiringTestJob, firstPhase3ReminderExpiringTestJob, secondPhase3ReminderExpiringTestJob,
    expirePhase1TestJob, expirePhase2TestJob, failedPhase1TestJob, failedPhase2TestJob, retrievePhase1ResultsJob,
    retrievePhase2ResultsJob, evaluatePhase1ResultJob, evaluatePhase2ResultJob, fixerJob, confirmAttendanceReminderJob,
    evaluateAssessmentScoreJob, notifyAssessmentCentrePassedOrFailedJob).flatten
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with Scheduler {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application) = app.configuration.getConfig("microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None
}

