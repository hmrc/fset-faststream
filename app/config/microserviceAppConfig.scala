/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.persisted.eventschedules.{Location, Venue}
import net.ceedubs.ficus.Ficus._
import play.api.{ Configuration, Environment }

//scalastyle:off number.of.types

case class FrameworksConfig(yamlFilePath: String)

case class SchemeConfig(yamlFilePath: String)

case class EventsConfig(scheduleFilePath: String, fsacGuideUrl: String, daysBeforeInvitationReminder: Int, maxNumberOfCandidates: Int)

case class EventSubtypeConfig(yamlFilePath: String)

case class AuthConfig(serviceName: String)

case class EmailConfig(enabled: Boolean, url: String)

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
  implicit lazy val reader =
    net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader[ScheduledJobConfig]
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
  implicit lazy val reader =
    net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader[WaitingScheduledJobConfig]
}

case class OnlineTestsGatewayConfig(url: String,
                                    phase1Tests: Phase1TestsConfig,
                                    phase2Tests: Phase2TestsConfig,
                                    numericalTests: NumericalTestsConfig,
                                    reportConfig: ReportConfig,
                                    candidateAppUrl: String,
                                    emailDomain: String
)

case class PsiTestIds(inventoryId: String, assessmentId: String, reportId: String, normId: String) {
  override def toString = s"inventoryId:$inventoryId,assessmentId:$assessmentId,reportId:$reportId,normId:$normId"
}

case class Phase1TestsConfig(expiryTimeInDays: Int,
                             gracePeriodInSecs: Int,
                             testRegistrationDelayInSecs: Int,
                             tests: Map[String, PsiTestIds],
                             standard: List[String],
                             gis: List[String])

case class Phase2Schedule(scheduleId: Int, assessmentId: Int)

case class Phase2TestsConfig(expiryTimeInDays: Int,
                             expiryTimeInDaysForInvigilatedETray: Int,
                             gracePeriodInSecs: Int,
                             testRegistrationDelayInSecs: Int,
                             tests: Map[String, PsiTestIds],
                             standard: List[String])

case class NumericalTestsConfig(gracePeriodInSecs: Int, tests: Map[String, PsiTestIds], standard: List[String])

case class ReportConfig(xmlReportId: Int, pdfReportId: Int, localeCode: String, suppressValidation: Boolean = false)

case class LaunchpadGatewayConfig(url: String, phase3Tests: Phase3TestsConfig)

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

case class LocationsAndVenuesConfig(yamlFilePath: String)

@Singleton
class MicroserviceAppConfig @Inject() (val config: Configuration, val environment: Environment) {
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  lazy val underlyingConfiguration = config.underlying

  lazy val emailConfig = underlyingConfiguration.as[EmailConfig]("microservice.services.email")
  lazy val authConfig = underlyingConfiguration.as[AuthConfig](s"microservice.services.auth")
  lazy val frameworksConfig = underlyingConfiguration.as[FrameworksConfig]("microservice.frameworks")
  lazy val schemeConfig = underlyingConfiguration.as[SchemeConfig]("microservice.schemes")
  lazy val eventsConfig = underlyingConfiguration.as[EventsConfig]("microservice.events")
  lazy val userManagementConfig = underlyingConfiguration.as[UserManagementConfig]("microservice.services.user-management")
  lazy val onlineTestsGatewayConfig = underlyingConfiguration.as[OnlineTestsGatewayConfig]("microservice.services.test-integration-gateway")
  lazy val launchpadGatewayConfig = underlyingConfiguration.as[LaunchpadGatewayConfig]("microservice.services.launchpad-gateway")
  lazy val disableSdipFaststreamForSift = underlyingConfiguration.as[Boolean]("microservice.services.disableSdipFaststreamForSift")
  lazy val maxNumberOfDocuments = underlyingConfiguration.as[Int]("maxNumberOfDocuments")

  lazy val locationsAndVenuesConfig =
    underlyingConfiguration.as[LocationsAndVenuesConfig]("scheduling.online-testing.locations-and-venues")

  val AllLocations = Location("All")
  val AllVenues = Venue("ALL_VENUES", "All venues")

  lazy val fixerJobConfig =
    underlyingConfiguration.as[ScheduledJobConfig]("scheduling.online-testing.fixer-job")

  lazy val parityExportJobConfig =
    underlyingConfiguration.as[ScheduledJobConfig]("scheduling.parity-export-job")
}
//scalastyle:on
