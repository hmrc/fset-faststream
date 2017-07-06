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

package config

import java.io.File

import com.github.ghik.silencer.silent
import com.typesafe.config.ConfigFactory
import model.persisted.eventschedules.{ Location, Venue }
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader
import play.api.Play
import play.api.Play.{ configuration, current }
import play.api.libs.json.Json
import uk.gov.hmrc.play.config.{ RunMode, ServicesConfig }

case class FrameworksConfig(yamlFilePath: String)

case class SchemeConfig(yamlFilePath: String)

case class AuthConfig(host: String, port: Int, serviceName: String)

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

case class CubiksGatewayConfig(
  url: String,
  phase1Tests: Phase1TestsConfig,
  phase2Tests: Phase2TestsConfig,
  competenceAssessment: CubiksGatewayStandardAssessment,
  situationalAssessment: CubiksGatewayStandardAssessment,
  reportConfig: ReportConfig,
  candidateAppUrl: String,
  emailDomain: String
)

case class Phase1TestsConfig(
  expiryTimeInDays: Int,
  scheduleIds: Map[String, Int],
  standard: List[String],
  gis: List[String]
)

case class Phase2Schedule(scheduleId: Int, assessmentId: Int, normId: Int)

case class Phase2TestsConfig(
  expiryTimeInDays: Int,
    expiryTimeInDaysForInvigilatedETray: Int,
    schedules: Map[String, Phase2Schedule]
) {
  require(schedules.contains("daro"), "Daro schedule must be present as it is used for the invigilated e-tray applications")

  def scheduleNameByScheduleId(scheduleId: Int): String = {
    val scheduleNameOpt = schedules.find {
      case (_, s) =>
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

case class Phase3TestsConfig(
  timeToExpireInDays: Int,
  invigilatedTimeToExpireInDays: Int,
  candidateCompletionRedirectUrl: String,
  interviewsByAdjustmentPercentage: Map[String, Int],
  evaluationWaitTimeAfterResultsReceivedInHours: Int,
  verifyAllScoresArePresent: Boolean
)

case class LocationsAndVenuesConfig(yamlFilePath: String)

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
  @silent lazy val app = play.api.Play.current
  @silent lazy val underlyingConfiguration = configuration.underlying

  lazy val appName = app.configuration.getString("appName").get

  lazy val emailConfig = underlyingConfiguration.as[EmailConfig]("microservice.services.email")
  lazy val authConfig = underlyingConfiguration.as[AuthConfig](s"microservice.services.auth")
  lazy val frameworksConfig = underlyingConfiguration.as[FrameworksConfig]("microservice.frameworks")
  lazy val schemeConfig = underlyingConfiguration.as[SchemeConfig]("microservice.schemes")
  lazy val userManagementConfig = underlyingConfiguration.as[UserManagementConfig]("microservice.services.user-management")
  lazy val cubiksGatewayConfig = underlyingConfiguration.as[CubiksGatewayConfig]("microservice.services.cubiks-gateway")
  lazy val launchpadGatewayConfig = underlyingConfiguration.as[LaunchpadGatewayConfig]("microservice.services.launchpad-gateway")
  lazy val parityGatewayConfig = underlyingConfiguration.as[ParityGatewayConfig]("microservice.services.parity-gateway")
  lazy val maxNumberOfDocuments = underlyingConfiguration.as[Int]("maxNumberOfDocuments")

  lazy val locationsAndVenuesConfig =
    underlyingConfiguration.as[LocationsAndVenuesConfig]("scheduling.online-testing.locations-and-venues")

  val AllLocations = Location("All")
  val AllVenues = Venue("All", "All venues")

  lazy val assessmentEvaluationMinimumCompetencyLevelConfig =
    underlyingConfiguration
      .as[AssessmentEvaluationMinimumCompetencyLevel]("microservice.services.assessment-evaluation.minimum-competency-level")

  lazy val fixerJobConfig =
    underlyingConfiguration.as[ScheduledJobConfig]("scheduling.online-testing.fixer-job")

  lazy val parityExportJobConfig =
    underlyingConfiguration.as[ScheduledJobConfig]("scheduling.parity-export-job")

  private val secretsFileCubiksUrlKey = "microservice.services.cubiks-gateway.testdata.url"
  lazy val testDataGeneratorCubiksSecret = app.configuration.getString(secretsFileCubiksUrlKey).
    getOrElse(fetchSecretConfigKeyFromFile("cubiks.url"))

  private def fetchSecretConfigKeyFromFile(key: String): String = {
    val path = System.getProperty("user.home") + "/.csr/.secrets"
    val testConfig = ConfigFactory.parseFile(new File(path))
    if (testConfig.isEmpty) {
      throw new IllegalArgumentException(s"No key found at '$secretsFileCubiksUrlKey' and .secrets file does not exist.")
    } else {
      testConfig.getString(s"testdata.$key")
    }
  }
}
