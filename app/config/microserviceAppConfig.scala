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
import net.ceedubs.ficus.readers.ValueReader
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
  val batchSize: Option[Int]
}

case class ScheduledJobConfig(
  enabled: Boolean,
  lockId: Option[String],
  initialDelaySecs: Option[Int],
  intervalSecs: Option[Int],
  batchSize: Option[Int] = None
) extends ScheduledJobConfigurable

object ScheduledJobConfig {
  implicit lazy val _reader: ValueReader[ScheduledJobConfig] = {
    net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader[ScheduledJobConfig]
  }
}

case class WaitingScheduledJobConfig(
  enabled: Boolean,
  lockId: Option[String],
  initialDelaySecs: Option[Int],
  intervalSecs: Option[Int],
  waitSecs: Option[Int],
  batchSize: Option[Int] = None
) extends ScheduledJobConfigurable

object WaitingScheduledJobConfig {
  implicit lazy val _reader: ValueReader[WaitingScheduledJobConfig] = {
    net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader[WaitingScheduledJobConfig]
  }
}

case class CubiksGatewayConfig(url: String,
  phase1Tests: Phase1TestsConfig,
  phase2Tests: Phase2TestsConfig,
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

case class Phase2Schedule(scheduleId: Int, assessmentId: Int, normId: Int)

case class Phase2TestsConfig(expiryTimeInDays: Int,
                             expiryTimeInDaysForInvigilatedETray: Int,
                             schedules: Map[String, Phase2Schedule]) {
  require(schedules.contains("daro"), "Daro schedule must be present as it is used for the invigilated e-tray applications")

  def scheduleNameByScheduleId(scheduleId: Int): String = {
    val scheduleNameOpt = schedules.find { case (_, s) =>
      s.scheduleId == scheduleId
    }
    val (scheduleName, _) = scheduleNameOpt.getOrElse(throw new IllegalArgumentException(s"Schedule id cannot be found: $scheduleId"))
    scheduleName
  }

  def scheduleForInvigilatedETray = schedules("daro")
}

trait CubiksGatewayAssessment {
  val assessmentId: Int
  val normId: Int
}

case class CubiksGatewayStandardAssessment(assessmentId: Int, normId: Int) extends CubiksGatewayAssessment

case class ReportConfig(xmlReportId: Int, pdfReportId: Int, localeCode: String, suppressValidation: Boolean = false)

case class LaunchpadGatewayConfig(url: String, phase3Tests: Phase3TestsConfig)

case class ParityGatewayConfig(url: String, upstreamAuthToken: String)

case class Phase3TestsConfig(timeToExpireInDays: Int,
                             invigilatedTimeToExpireInDays: Int,
                             candidateCompletionRedirectUrl: String,
                             interviewsByAdjustmentPercentage: Map[String, Int],
                             evaluationWaitTimeAfterResultsReceivedInHours: Int)

case class AssessmentCentresLocationsConfig(yamlFilePath: String)
case class AssessmentCentresConfig(yamlFilePath: String)

case class AssessmentEvaluationMinimumCompetencyLevel(enabled: Boolean, minimumCompetencyLevelScore: Option[Double],
  motivationalFitMinimumCompetencyLevelScore: Option[Double]) {
  require(!enabled || (minimumCompetencyLevelScore.isDefined && motivationalFitMinimumCompetencyLevelScore.isDefined))
}

object AssessmentEvaluationMinimumCompetencyLevel {
  implicit val AssessmentEvaluationMinimumCompetencyLevelFormats = Json.format[AssessmentEvaluationMinimumCompetencyLevel]
}

object MicroserviceAppConfig extends MicroserviceAppConfig

trait MicroserviceAppConfig extends ServicesConfig with RunMode {
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  lazy val emailConfig = configuration.underlying.as[EmailConfig]("microservice.services.email")
  lazy val frameworksConfig = configuration.underlying.as[FrameworksConfig]("microservice.frameworks")
  lazy val userManagementConfig = configuration.underlying.as[UserManagementConfig]("microservice.services.user-management")
  lazy val cubiksGatewayConfig = configuration.underlying.as[CubiksGatewayConfig]("microservice.services.cubiks-gateway")
  lazy val launchpadGatewayConfig = configuration.underlying.as[LaunchpadGatewayConfig]("microservice.services.launchpad-gateway")
  lazy val parityGatewayConfig = configuration.underlying.as[ParityGatewayConfig]("microservice.services.parity-gateway")
  lazy val maxNumberOfDocuments = configuration.underlying.as[Int]("maxNumberOfDocuments")

  lazy val assessmentCentresLocationsConfig =
    configuration.underlying.as[AssessmentCentresLocationsConfig]("scheduling.online-testing.assessment-centres-locations")
  lazy val assessmentCentresConfig =
    configuration.underlying.as[AssessmentCentresConfig]("scheduling.online-testing.assessment-centres")
  lazy val assessmentEvaluationMinimumCompetencyLevelConfig =
    configuration.underlying
      .as[AssessmentEvaluationMinimumCompetencyLevel]("microservice.services.assessment-evaluation.minimum-competency-level")

  lazy val fixerJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.fixer-job")

  lazy val parityExportJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.parity-export-job")
}
