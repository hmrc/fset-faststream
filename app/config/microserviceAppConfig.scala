/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.persisted.eventschedules.{Location, Venue}
import play.api.{ConfigLoader, Configuration, Environment}

import java.util.Base64

//scalastyle:off number.of.types

case class FrameworksConfig(yamlFilePath: String)

object FrameworksConfig {
  implicit val configLoader: ConfigLoader[FrameworksConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    FrameworksConfig(config.get[String]("yamlFilePath"))
  }
}

case class SchemeConfig(yamlFilePath: String, candidateFrontendUrl: String)

object SchemeConfig {
  def apply(yamlFilePath: String, candidateFrontendUrl: String) = {
    val base64DecodedUrl = new String(Base64.getDecoder.decode(candidateFrontendUrl), "UTF-8")
    new SchemeConfig(yamlFilePath, base64DecodedUrl)
  }

  implicit val configLoader: ConfigLoader[SchemeConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    SchemeConfig(config.get[String]("yamlFilePath"), config.get[String]("candidateFrontendUrl"))
  }
}

case class EventsConfig(scheduleFilePath: String, fsacGuideUrl: String, daysBeforeInvitationReminder: Int, maxNumberOfCandidates: Int)

object EventsConfig {
  implicit val configLoader: ConfigLoader[EventsConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    EventsConfig(
      config.get[String]("scheduleFilePath"),
      config.get[String]("fsacGuideUrl"),
      config.get[Int]("daysBeforeInvitationReminder"),
      config.get[Int]("maxNumberOfCandidates")
    )
  }
}

case class EventSubtypeConfig(yamlFilePath: String)

case class AuthConfig(serviceName: String)

object AuthConfig {
  implicit val configLoader: ConfigLoader[AuthConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    AuthConfig(config.get[String]("serviceName"))
  }
}

case class EmailConfig(enabled: Boolean, url: String)

object EmailConfig {
  implicit val configLoader: ConfigLoader[EmailConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    EmailConfig(config.get[Boolean]("enabled"), config.get[String]("url"))
  }
}

case class UserManagementConfig(url: String)

object UserManagementConfig {
  implicit val configLoader: ConfigLoader[UserManagementConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    UserManagementConfig(config.get[String]("url"))
  }
}

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
  implicit val configLoader: ConfigLoader[ScheduledJobConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    ScheduledJobConfig(
      config.get[Boolean]("enabled"),
      config.getOptional[String]("lockId"),
      config.getOptional[Int]("initialDelaySecs"),
      config.getOptional[Int]("intervalSecs"),
      config.getOptional[Int]("batchSize")
    )
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
  implicit val configLoader: ConfigLoader[WaitingScheduledJobConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    WaitingScheduledJobConfig(
      config.get[Boolean]("enabled"),
      config.getOptional[String]("lockId"),
      config.getOptional[Int]("initialDelaySecs"),
      config.getOptional[Int]("intervalSecs"),
      config.getOptional[Int]("waitSecs"),
      config.getOptional[Int]("batchSize")
    )
  }
}

case class OnlineTestsGatewayConfig(url: String,
                                    phase1Tests: Phase1TestsConfig,
                                    phase2Tests: Phase2TestsConfig,
                                    numericalTests: NumericalTestsConfig,
                                    reportConfig: ReportConfig,
                                    candidateAppUrl: String,
                                    emailDomain: String
)

object OnlineTestsGatewayConfig {
  implicit val configLoader: ConfigLoader[OnlineTestsGatewayConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    OnlineTestsGatewayConfig(
      config.get[String]("url"),
      config.get[Phase1TestsConfig]("phase1Tests"),
      config.get[Phase2TestsConfig]("phase2Tests"),
      config.get[NumericalTestsConfig]("numericalTests"),
      config.get[ReportConfig]("reportConfig"),
      config.get[String]("candidateAppUrl"),
      config.get[String]("emailDomain")
    )
  }
}

case class PsiTestIds(inventoryId: String, assessmentId: String, reportId: String, normId: String) {
  override def toString = s"inventoryId:$inventoryId,assessmentId:$assessmentId,reportId:$reportId,normId:$normId"
}

object PsiTestIds {
  implicit val configLoader: ConfigLoader[PsiTestIds] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    PsiTestIds(
      config.get[String]("inventoryId"),
      config.get[String]("assessmentId"),
      config.get[String]("reportId"),
      config.get[String]("normId")
    )
  }
}

case class Phase1TestsConfig(expiryTimeInDays: Int,
                             gracePeriodInSecs: Int,
                             testRegistrationDelayInSecs: Int,
                             tests: Map[String, PsiTestIds],
                             standard: List[String],
                             gis: List[String])

object Phase1TestsConfig {
  implicit val configLoader: ConfigLoader[Phase1TestsConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    Phase1TestsConfig(
      config.get[Int]("expiryTimeInDays"),
      config.get[Int]("gracePeriodInSecs"),
      config.get[Int]("testRegistrationDelayInSecs"),
      config.get[Map[String, PsiTestIds]]("tests"),
      config.get[Seq[String]]("standard").toList,
      config.get[Seq[String]]("gis").toList
    )
  }
}

case class Phase2Schedule(scheduleId: Int, assessmentId: Int)

case class Phase2TestsConfig(expiryTimeInDays: Int,
                             expiryTimeInDaysForInvigilatedETray: Int,
                             gracePeriodInSecs: Int,
                             testRegistrationDelayInSecs: Int,
                             tests: Map[String, PsiTestIds],
                             standard: List[String])

object Phase2TestsConfig {
  implicit val configLoader: ConfigLoader[Phase2TestsConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    Phase2TestsConfig(
      config.get[Int]("expiryTimeInDays"),
      config.get[Int]("expiryTimeInDaysForInvigilatedETray"),
      config.get[Int]("gracePeriodInSecs"),
      config.get[Int]("testRegistrationDelayInSecs"),
      config.get[Map[String, PsiTestIds]]("tests"),
      config.get[Seq[String]]("standard").toList
    )
  }
}

case class NumericalTestsConfig(gracePeriodInSecs: Int, tests: Map[String, PsiTestIds], standard: List[String])

object NumericalTestsConfig {
  implicit val configLoader: ConfigLoader[NumericalTestsConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    NumericalTestsConfig(
      config.get[Int]("gracePeriodInSecs"),
      config.get[Map[String, PsiTestIds]]("tests"),
      config.get[Seq[String]]("standard").toList
    )
  }
}

case class ReportConfig(xmlReportId: Int, pdfReportId: Int, localeCode: String, suppressValidation: Boolean = false)

object ReportConfig {
  implicit val configLoader: ConfigLoader[ReportConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    ReportConfig(
      config.get[Int]("xmlReportId"),
      config.get[Int]("pdfReportId"),
      config.get[String]("localeCode"),
      config.get[Boolean]("suppressValidation")
    )
  }
}

case class Phase3TestsConfig(timeToExpireInDays: Int,
                             invigilatedTimeToExpireInDays: Int,
                             gracePeriodInSecs: Int,
                             candidateCompletionRedirectUrl: String,
                             interviewsByAdjustmentPercentage: Map[String, Int],
                             evaluationWaitTimeAfterResultsReceivedInHours: Int,
                             verifyAllScoresArePresent: Boolean) {
  override def toString: String = s"timeToExpireInDays=$timeToExpireInDays," +
    s"invigilatedTimeToExpireInDays=$invigilatedTimeToExpireInDays," +
    s"gracePeriodInSecs=$gracePeriodInSecs," +
    s"candidateCompletionRedirectUrl=$candidateCompletionRedirectUrl," +
    s"interviewsByAdjustmentPercentage=$interviewsByAdjustmentPercentage," +
    s"evaluationWaitTimeAfterResultsReceivedInHours=$evaluationWaitTimeAfterResultsReceivedInHours," +
    s"verifyAllScoresArePresent=$verifyAllScoresArePresent"
}

object Phase3TestsConfig {
  implicit val configLoader: ConfigLoader[Phase3TestsConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    Phase3TestsConfig(
      config.get[Int]("timeToExpireInDays"),
      config.get[Int]("invigilatedTimeToExpireInDays"),
      config.get[Int]("gracePeriodInSecs"),
      config.get[String]("candidateCompletionRedirectUrl"),
      config.get[Map[String, Int]]("interviewsByAdjustmentPercentage"),
      config.get[Int]("evaluationWaitTimeAfterResultsReceivedInHours"),
      config.get[Boolean]("verifyAllScoresArePresent")
    )
  }
}

case class LaunchpadGatewayConfig(url: String, phase3Tests: Phase3TestsConfig)

object LaunchpadGatewayConfig {
  implicit val configLoader: ConfigLoader[LaunchpadGatewayConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    LaunchpadGatewayConfig(
      config.get[String]("url"),
      config.get[Phase3TestsConfig]("phase3Tests")
    )
  }
}

case class LocationsAndVenuesConfig(yamlFilePath: String)

object LocationsAndVenuesConfig {
  implicit val configLoader: ConfigLoader[LocationsAndVenuesConfig] = (rootConfig: Config, path: String) => {
    val config = Configuration(rootConfig).get[Configuration](path)
    LocationsAndVenuesConfig(config.get[String]("yamlFilePath"))
  }
}

@Singleton
class MicroserviceAppConfig @Inject() (val config: Configuration, val environment: Environment) {
  lazy val emailConfig = config.get[EmailConfig]("microservice.services.email")
  lazy val authConfig = config.get[AuthConfig](s"microservice.services.auth")
  lazy val frameworksConfig = config.get[FrameworksConfig]("microservice.frameworks")
  lazy val schemeConfig = config.get[SchemeConfig]("microservice.schemes")
  lazy val eventsConfig = config.get[EventsConfig]("microservice.events")
  lazy val userManagementConfig = config.get[UserManagementConfig]("microservice.services.user-management")
  lazy val onlineTestsGatewayConfig = config.get[OnlineTestsGatewayConfig]("microservice.services.test-integration-gateway")
  lazy val launchpadGatewayConfig = config.get[LaunchpadGatewayConfig]("microservice.services.launchpad-gateway")
  lazy val disableSdipFaststreamForSift = config.get[Boolean]("microservice.services.disableSdipFaststreamForSift")
  lazy val maxNumberOfDocuments = config.get[Int]("maxNumberOfDocuments")

  lazy val locationsAndVenuesConfig =
    config.get[LocationsAndVenuesConfig]("scheduling.online-testing.locations-and-venues")

  val AllLocations = Location("All")
  val AllVenues = Venue("ALL_VENUES", "All venues")
}
//scalastyle:on
