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

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import play.api.Play.{ configuration, current }
import play.api.libs.json.Json
import uk.gov.hmrc.play.config.{ RunMode, ServicesConfig }

case class FrameworksConfig(yamlFilePath: String)

case class EmailConfig(url: String)

case class UserManagementConfig(url: String)

trait ScheduledJobConfigurable {
  val enabled: Boolean
  val lockId: Option[String]
  val initialDelaySecs: Option[Int]
  val intervalSecs: Option[Int]
}

case class ScheduledJobConfig(
  enabled: Boolean,
  lockId: Option[String],
  initialDelaySecs: Option[Int],
  intervalSecs: Option[Int]
) extends ScheduledJobConfigurable

case class WaitingScheduledJobConfig(
  enabled: Boolean,
  lockId: Option[String],
  initialDelaySecs: Option[Int],
  intervalSecs: Option[Int],
  waitSecs: Option[Int]
) extends ScheduledJobConfigurable

case class CubiksGatewayConfig(url: String,
  phase1Tests: Phase1TestsConfig,
  competenceAssessment: CubiksGatewayStandardAssessment,
  situationalAssessment: CubiksGatewayStandardAssessment,
  reportConfig: ReportConfig,
  candidateAppUrl: String,
  emailDomain: String
)

case class Phase1TestsConfig(expiryTimeInDays: Int,
                                  scheduleIds: Map[String, Int],
                                  standard: List[String],
                                  gis: List[String])

trait CubiksGatewayAssessment {
  val assessmentId: Int
  val normId: Int
}

case class CubiksGatewayStandardAssessment(assessmentId: Int, normId: Int) extends CubiksGatewayAssessment

case class ReportConfig(xmlReportId: Int, pdfReportId: Int, localeCode: String, suppressValidation: Boolean = false)

case class DiversityMonitoringJobConfig(enabled: Boolean, lockId: Option[String], initialDelaySecs: Option[Int],
  intervalSecs: Option[Int], forceStopActorsSecs: Option[Int])

case class AssessmentCentresLocationsConfig(yamlFilePath: String)
case class AssessmentCentresConfig(yamlFilePath: String)

case class AssessmentEvaluationMinimumCompetencyLevel(enabled: Boolean, minimumCompetencyLevelScore: Option[Double],
  motivationalFitMinimumCompetencyLevelScore: Option[Double]) {
  require(!enabled || (minimumCompetencyLevelScore.isDefined && motivationalFitMinimumCompetencyLevelScore.isDefined))
}

object AssessmentEvaluationMinimumCompetencyLevel {
  implicit val AssessmentEvaluationMinimumCompetencyLevelFormats = Json.format[AssessmentEvaluationMinimumCompetencyLevel]
}

object MicroserviceAppConfig extends ServicesConfig with RunMode {
  lazy val emailConfig = configuration.underlying.as[EmailConfig]("microservice.services.email")
  lazy val frameworksConfig = configuration.underlying.as[FrameworksConfig]("microservice.frameworks")
  lazy val userManagementConfig = configuration.underlying.as[UserManagementConfig]("microservice.services.user-management")
  lazy val cubiksGatewayConfig = configuration.underlying.as[CubiksGatewayConfig]("microservice.services.cubiks-gateway")
  lazy val maxNumberOfDocuments = configuration.underlying.as[Int]("maxNumberOfDocuments")
  lazy val sendInvitationJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.send-invitation-job")
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.send-invitation-job")

  lazy val firstReminderJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.first-reminder-expiring-test-job")
  lazy val secondReminderJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.second-reminder-expiring-test-job")

  lazy val expireOnlineTestJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.expiry-job")
  lazy val failedOnlineTestJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.failed-test-job")
  lazy val diversityMonitoringJobConfig =
    configuration.underlying.as[DiversityMonitoringJobConfig]("scheduling.diversity-monitoring-job")
  lazy val retrieveResultsJobConfig =
    configuration.underlying.as[WaitingScheduledJobConfig]("scheduling.online-testing.retrieve-results-job")
  lazy val evaluatePhase1ResultJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.evaluate-phase1-result-job")
  lazy val confirmAttendanceReminderJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.confirm-attendance-reminder-job")
  lazy val evaluateAssessmentScoreJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.evaluate-assessment-score-job")
  lazy val notifyAssessmentCentrePassedOrFailedJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.notify-assessment-centre-passed-or-failed-job")

  lazy val assessmentCentresLocationsConfig =
    configuration.underlying.as[AssessmentCentresLocationsConfig]("scheduling.online-testing.assessment-centres-locations")
  lazy val assessmentCentresConfig =
    configuration.underlying.as[AssessmentCentresConfig]("scheduling.online-testing.assessment-centres")
  lazy val assessmentEvaluationMinimumCompetencyLevelConfig =
    configuration.underlying
      .as[AssessmentEvaluationMinimumCompetencyLevel]("microservice.services.assessment-evaluation.minimum-competency-level")

}