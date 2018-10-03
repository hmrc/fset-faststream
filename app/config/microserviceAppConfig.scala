/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.{ Logger, Play }
import play.api.Play.{ configuration, current }
import play.api.libs.json.Json
import uk.gov.hmrc.play.config.ServicesConfig

case class FrameworksConfig(yamlFilePath: String)

case class SchemeConfig(yamlFilePath: String)

case class EventsConfig(scheduleFilePath: String, fsacGuideUrl: String, daysBeforeInvitationReminder: Int)

case class EventSubtypeConfig(yamlFilePath: String)

case class AuthConfig(serviceName: String)

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
  numericalTests: NumericalTestsConfig,
  reportConfig: ReportConfig,
  candidateAppUrl: String,
  emailDomain: String
)

case class Phase1TestsConfig(expiryTimeInDays: Int,
                             scheduleIds: Map[String, Int],
                             standard: List[String],
                             gis: List[String])

case class Phase2Schedule(scheduleId: Int, assessmentId: Int)

case class Phase2TestsConfig(expiryTimeInDays: Int,
                             expiryTimeInDaysForInvigilatedETray: Int,
                             schedules: Map[String, Phase2Schedule],
                             alwaysChooseSchedule: Option[String]) {
  require(schedules.contains("daro"), "Daro schedule must be present as it is used for the invigilated e-tray applications")
  alwaysChooseSchedule.foreach { s =>
    require(schedules.contains(s), s"The alwaysChooseSchedule value: $s is not in the schedule names: ${schedules.keys}")
    Logger.info(s"The alwaysChooseSchedule key is configured so phase2 invitation job will always invite candidates to take $s Etray")
  }

  def scheduleNameByScheduleId(scheduleId: Int): String = {
    val scheduleNameOpt = schedules.find { case (_, s) =>
      s.scheduleId == scheduleId
    }
    val (scheduleName, _) = scheduleNameOpt.getOrElse(throw new IllegalArgumentException(s"Schedule id cannot be found: $scheduleId"))
    scheduleName
  }

  def scheduleForInvigilatedETray = schedules("daro")
}

case class NumericalTestSchedule(scheduleId: Int, assessmentId: Int)
case class NumericalTestsConfig(schedules: Map[String, NumericalTestSchedule])

case object NumericalTestsConfig {
  val numericalTestScheduleName = "numericalTest"
}

trait CubiksGatewayAssessment {
  val assessmentId: Int
}

case class CubiksGatewayStandardAssessment(assessmentId: Int) extends CubiksGatewayAssessment

case class ReportConfig(xmlReportId: Int, pdfReportId: Int, localeCode: String, suppressValidation: Boolean = false)

case class LaunchpadGatewayConfig(url: String, phase3Tests: Phase3TestsConfig)

case class Phase3TestsConfig(timeToExpireInDays: Int,
                             invigilatedTimeToExpireInDays: Int,
                             candidateCompletionRedirectUrl: String,
                             interviewsByAdjustmentPercentage: Map[String, Int],
                             evaluationWaitTimeAfterResultsReceivedInHours: Int,
                             verifyAllScoresArePresent: Boolean)

case class LocationsAndVenuesConfig(yamlFilePath: String)

case class AssessmentEvaluationMinimumCompetencyLevel(enabled: Boolean, minimumCompetencyLevelScore: Option[Double]) {
  require(!enabled || minimumCompetencyLevelScore.isDefined)
}

object AssessmentEvaluationMinimumCompetencyLevel {
  implicit val AssessmentEvaluationMinimumCompetencyLevelFormats = Json.format[AssessmentEvaluationMinimumCompetencyLevel]
}

object MicroserviceAppConfig extends MicroserviceAppConfig

trait MicroserviceAppConfig extends ServicesConfig {
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  @silent lazy val app = play.api.Play.current
  @silent lazy val underlyingConfiguration = configuration.underlying

  lazy val appName = app.configuration.getString("appName").get

  lazy val emailConfig = underlyingConfiguration.as[EmailConfig]("microservice.services.email")
  lazy val authConfig = underlyingConfiguration.as[AuthConfig](s"microservice.services.auth")
  lazy val frameworksConfig = underlyingConfiguration.as[FrameworksConfig]("microservice.frameworks")
  lazy val schemeConfig = underlyingConfiguration.as[SchemeConfig]("microservice.schemes")
  lazy val eventsConfig = underlyingConfiguration.as[EventsConfig]("microservice.events")
  lazy val userManagementConfig = underlyingConfiguration.as[UserManagementConfig]("microservice.services.user-management")
  lazy val cubiksGatewayConfig = underlyingConfiguration.as[CubiksGatewayConfig]("microservice.services.cubiks-gateway")
  lazy val launchpadGatewayConfig = underlyingConfiguration.as[LaunchpadGatewayConfig]("microservice.services.launchpad-gateway")
  lazy val disableSdipFaststreamForSift = underlyingConfiguration.as[Boolean]("microservice.services.disableSdipFaststreamForSift")
  lazy val maxNumberOfDocuments = underlyingConfiguration.as[Int]("maxNumberOfDocuments")

  lazy val locationsAndVenuesConfig =
    underlyingConfiguration.as[LocationsAndVenuesConfig]("scheduling.online-testing.locations-and-venues")

  val AllLocations = Location("All")
  val AllVenues = Venue("ALL_VENUES", "All venues")


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
