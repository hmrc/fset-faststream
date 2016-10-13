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

  private lazy val sendInvitationJob: Option[ScheduledJob] =
    if (sendInvitationJobConfigValues.enabled) Some(SendInvitationJob) else {
      Logger.warn("Send invitation job is disabled")
      None
    }

  private lazy val firstReminderExpiringTestJob: Option[ScheduledJob] =
    if (firstReminderJobConfigValues.enabled) Some(FirstReminderExpiringTestJob) else {
      Logger.warn("First reminder for expiring test job is disabled")
      None
    }

  private lazy val secondReminderExpiringTestJob: Option[ScheduledJob] =
    if (secondReminderJobConfigValues.enabled) Some(SecondReminderExpiringTestJob) else {
      Logger.warn("Second reminder for expiring test job is disabled")
      None
    }

  private lazy val expireOnlineTestJob: Option[ScheduledJob] =
    if (expireOnlineTestJobConfigValues.enabled) Some(ExpireOnlineTestJob) else {
      Logger.warn("Expire online test job is disabled")
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

  private[config] def sendInvitationJobConfigValues = sendInvitationJobConfig
  private[config] def expireOnlineTestJobConfigValues = expireOnlineTestJobConfig
  private[config] def firstReminderJobConfigValues = firstReminderJobConfig
  private[config] def secondReminderJobConfigValues = secondReminderJobConfig
  private[config] def failedOnlineTestJobConfigValues = failedOnlineTestJobConfig
  private[config] def retrieveResultsJobConfigValues = retrieveResultsJobConfig
  private[config] def evaluatePhase1ResultJobConfigValues = evaluatePhase1ResultJobConfig
  private[config] def confirmAttendanceReminderJobConfigValues = confirmAttendanceReminderJobConfig
  private[config] def evaluateAssessmentScoreJobConfigValues = evaluateAssessmentScoreJobConfig
  private[config] def notifyAssessmentCentrePassedOrFailedJobConfigValues = notifyAssessmentCentrePassedOrFailedJobConfig

  lazy val scheduledJobs = List(sendInvitationJob, firstReminderExpiringTestJob, secondReminderExpiringTestJob,
    expireOnlineTestJob, failedOnlineTestJob, retrieveResultsJob, evaluatePhase1ResultJob,
    confirmAttendanceReminderJob, evaluateAssessmentScoreJob, notifyAssessmentCentrePassedOrFailedJob).flatten
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with Scheduler {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application) = app.configuration.getConfig("microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None
}
