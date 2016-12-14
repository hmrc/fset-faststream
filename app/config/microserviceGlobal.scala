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
import play.api.{ Application, Configuration, Logger, Play }
import scheduler.allocation.ConfirmAttendanceReminderJob
import scheduler.assessment._
import scheduler.fixer.FixerJob
import scheduler.onlinetesting._
import scheduler.parity.ParityExportJob
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.config.{ AppName, ControllerConfig }
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.scheduling.{ RunningOfScheduledJobs, ScheduledJob }

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs: Config = Play.current.configuration.underlying.as[Config]("controllers")
}

object MicroserviceAuditFilter extends AuditFilter with AppName {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) =
    false // Disable implicit _inbound_ auditing.
}

object MicroserviceLoggingFilter extends LoggingFilter {
  override def controllerNeedsLogging(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

trait Scheduler extends RunningOfScheduledJobs {
  val appConfig: MicroserviceAppConfig
  import appConfig._

  private def maybeInitScheduler(name: String, config: ScheduledJobConfigurable, scheduler: => ScheduledJob): Option[ScheduledJob] = {
    if (config.enabled) Some(scheduler) else {
      Logger.warn(s"$name job is disabled")
      None
    }
  }

  lazy val scheduledJobs: List[ScheduledJob] = List(
    maybeInitScheduler("Send phase 1 invitation", sendPhase1InvitationJobConfig, SendPhase1InvitationJob),
    maybeInitScheduler("Send phase 2 invitation", sendPhase2InvitationJobConfig, SendPhase2InvitationJob),
    maybeInitScheduler("Send phase 3 invitation", sendPhase3InvitationJobConfig, SendPhase3InvitationJob),
    maybeInitScheduler("Expire Phase1 test", expirePhase1TestJobConfig, ExpirePhase1TestJob),
    maybeInitScheduler("Expire Phase2 test", expirePhase2TestJobConfig, ExpirePhase2TestJob),
    maybeInitScheduler("Expire Phase3 test", expirePhase3TestJobConfig, ExpirePhase3TestJob),
    maybeInitScheduler("First Phase1 reminder for expiring test", firstPhase1ReminderJobConfig, FirstPhase1ReminderExpiringTestJob),
    maybeInitScheduler("Second Phase1 reminder for expiring test", secondPhase1ReminderJobConfig, SecondPhase1ReminderExpiringTestJob),
    maybeInitScheduler("First Phase2 reminder for expiring test", firstPhase2ReminderJobConfig, FirstPhase2ReminderExpiringTestJob),
    maybeInitScheduler("Second Phase2 reminder for expiring test", secondPhase2ReminderJobConfig, SecondPhase2ReminderExpiringTestJob),
    maybeInitScheduler("First Phase3 reminder for expiring test", firstPhase3ReminderJobConfig, FirstPhase3ReminderExpiringTestJob),
    maybeInitScheduler("Second Phase3 reminder for expiring test", secondPhase3ReminderJobConfig, SecondPhase3ReminderExpiringTestJob),
    maybeInitScheduler("Failed Phase1 online test", failedPhase1TestJobConfig, FailedPhase1TestJob),
    maybeInitScheduler("Failed Phase2 online test", failedPhase2TestJobConfig, FailedPhase2TestJob),
    maybeInitScheduler("Failed Phase3 online test", failedPhase3TestJobConfig, FailedPhase3TestJob),
    maybeInitScheduler("Success Phase1 online test", successPhase1TestJobConfig, SuccessPhase1TestJob),
    maybeInitScheduler("Success Phase3 online test", successPhase3TestJobConfig, SuccessPhase3TestJob),
    maybeInitScheduler("Retrieve phase1 results", retrievePhase1ResultsJobConfig, RetrievePhase1ResultsJob),
    maybeInitScheduler("Retrieve phase2 results", retrievePhase2ResultsJobConfig, RetrievePhase2ResultsJob),
    maybeInitScheduler("Evaluate phase1 result", evaluatePhase1ResultJobConfig, EvaluatePhase1ResultJob),
    maybeInitScheduler("Evaluate phase2 result", evaluatePhase2ResultJobConfig, EvaluatePhase2ResultJob),
    maybeInitScheduler("Evaluate phase3 result", evaluatePhase3ResultJobConfig, EvaluatePhase3ResultJob),
    maybeInitScheduler("Confirm attendance reminder", confirmAttendanceReminderJobConfig, ConfirmAttendanceReminderJob),
    maybeInitScheduler("Evaluate assessment score", evaluateAssessmentScoreJobConfig, EvaluateAssessmentScoreJob),
    maybeInitScheduler("Notify assessment centre pass/fail", notifyAssessmentCentrePassedOrFailedJobConfig,
      NotifyAssessmentCentrePassedOrFailedJob),
    maybeInitScheduler("Fixer", fixerJobConfig, FixerJob),
    maybeInitScheduler("Parity export", parityExportJobConfig, ParityExportJob)
  ).flatten
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with Scheduler {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig("microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None

  override val appConfig: MicroserviceAppConfig = MicroserviceAppConfig
}

