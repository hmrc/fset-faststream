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

package model.command.testdata

import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Result
import model.ProgressStatuses
import model.{ ApplicationRoute, ApplicationStatus }
import model.EvaluationResults.Result
import model.ProgressStatuses.ProgressStatus
import model.exchange.testdata._
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
import org.joda.time.format.DateTimeFormat
import services.testdata.faker.DataFaker.Random

case class AssistanceDetails(
  hasDisability: String = Random.yesNoPreferNotToSay,
  hasDisabilityDescription: String = Random.hasDisabilityDescription,
  setGis: Boolean = Random.bool,
  onlineAdjustments: Boolean = Random.bool,
  onlineAdjustmentsDescription: String = Random.onlineAdjustmentsDescription,
  assessmentCentreAdjustments: Boolean = Random.bool,
  assessmentCentreAdjustmentsDescription: String = Random.assessmentCentreAdjustmentDescription
)

object AssistanceDetails {
  def apply(o: model.exchange.testdata.AssistanceDetailsRequest): AssistanceDetails = {
    val default = AssistanceDetails()
    AssistanceDetails(
      hasDisability = o.hasDisability.getOrElse(default.hasDisability),
      hasDisabilityDescription = o.hasDisabilityDescription.getOrElse(default.hasDisabilityDescription),
      setGis = o.setGis.getOrElse(default.setGis),
      onlineAdjustments = o.onlineAdjustments.getOrElse(default.onlineAdjustments),
      onlineAdjustmentsDescription = o.onlineAdjustmentsDescription.getOrElse(default.onlineAdjustmentsDescription),
      assessmentCentreAdjustments = o.assessmentCentreAdjustments.getOrElse(default.assessmentCentreAdjustments),
      assessmentCentreAdjustmentsDescription = o.assessmentCentreAdjustmentsDescription.getOrElse(default.assessmentCentreAdjustmentsDescription)
    )
  }
}

trait TestDates {
  def start: Option[DateTime]
  def expiry: Option[DateTime]
  def completion: Option[DateTime]

  def randomDateBeforeNow: DateTime = {
    DateTime.now(DateTimeZone.UTC).minusHours(scala.util.Random.nextInt(120))
  }

  def randomDateAroundNow: DateTime = {
   DateTime.now(DateTimeZone.UTC).plusHours(scala.util.Random.nextInt(240)).minusHours(scala.util.Random.nextInt(240))
  }
}

trait TestResult {
  def tscore: Option[Double]
}

case class Phase1TestData(
  start: DateTime = DateTime.now ,
  expiry: DateTime = DateTime.now,
  completion: DateTime = DateTime.now,
  tscore: Double = scala.util.Random.nextDouble
) extends TestDates with TestResult

object Phase1TestData {
  def apply(o: model.exchange.testdata.Phase1TestDataRequest): Phase1TestData = {
    Phase1TestData(
      start = o.start.map(DateTime.parse),
      expiry = o.start.map(DateTime.parse),
      completion = o.start.map(DateTime.parse),
      tscore = o.tscore.map(_.toDouble)
    )
  }
}
case class Phase2TestData(
  start: Option[DateTime] = None,
  expiry: Option[DateTime] = None,
  completion: Option[DateTime] = None,
  tscore: Option[Double] = None
) extends TestDates with TestResult

object Phase2TestData {
  def apply(o: model.exchange.testdata.Phase2TestDataRequest): Phase2TestData = {
    Phase2TestData(
      start = o.start.map(DateTime.parse),
      expiry = o.start.map(DateTime.parse),
      completion = o.start.map(DateTime.parse),
      tscore = o.tscore.map(_.toDouble)
    )
  }
}

case class PersonalData(
  emailPrefix: Option[String] = None,
  firstName: Option[String] = None,
  lastName: Option[String] = None,
  preferredName: Option[String] = None,
  dob: Option[LocalDate] = None,
  postCode: Option[String] = None,
  country: Option[String] = None
)

object PersonalData {
  def apply(o: model.exchange.testdata.PersonalDataRequest): PersonalData = {
    PersonalData(
      emailPrefix = o.emailPrefix,
      firstName = o.firstName,
      lastName = o.lastName,
      preferredName = o.preferedName,
      dob = o.dateOfBirth.map(x => LocalDate.parse(x, DateTimeFormat.forPattern("yyyy-MM-dd"))),
      postCode = o.postCode,
      country = o.country
    )
  }
}

case class StatusData(
  applicationStatus: ApplicationStatus = ApplicationStatus.REGISTERED,
  previousApplicationStatus: Option[ApplicationStatus] = None,
  progressStatus: Option[ProgressStatus] = None,
  applicationRoute: ApplicationRoute.ApplicationRoute = ApplicationRoute.Faststream
)

object StatusData {
  def apply(o: model.exchange.testdata.StatusDataRequest): StatusData = {
    StatusData(applicationStatus = o.applicationRoute.map(ApplicationStatus.withName).getOrElse(ApplicationStatus.REGISTERED),
      previousApplicationStatus = o.previousApplicationStatus.map(ApplicationStatus.withName),
      progressStatus = o.progressStatus.map(ProgressStatuses.nameToProgressStatus),
      applicationRoute = o.applicationRoute.map(ApplicationRoute.withName).getOrElse(ApplicationRoute.Faststream)
    )
  }
}


case class GeneratorConfig(statusData: StatusData,
  numberToGenerate: Int = 1,
  personalData: Option[PersonalData] = None,
  assistanceDetails: Option[AssistanceDetails] = None,
  cubiksUrl: String,
  isCivilServant: Option[Boolean] = None,
  hasDegree: Option[Boolean] = None,
  region: Option[String] = None,
  loc1scheme1Passmark: Option[Result] = None,
  loc1scheme2Passmark: Option[Result] = None,
  confirmedAllocation: Boolean = true,
  phase1TestData: Option[Phase1TestData] = None,
  phase2TestData: Option[Phase2TestData] = None
)

object GeneratorConfig {
  def apply(o: model.exchange.testdata.CreateCandidateInStatusRequest, cubiksUrlFromConfig: String): GeneratorConfig = {

    val statusData = StatusData(o.statusData)

    GeneratorConfig(
      statusData = statusData,
      numberToGenerate = o.numberToGenerate,
      personalData = o.personalData.map(PersonalData.apply),
      assistanceDetails = o.assistanceDetails.map(AssistanceDetails.apply),
      cubiksUrl = cubiksUrlFromConfig,
      isCivilServant = o.isCivilServant,
      hasDegree = o.hasDegree,
      region = o.region,
      loc1scheme1Passmark = o.loc1scheme1EvaluationResult.map(Result.apply),
      loc1scheme2Passmark = o.loc1scheme2EvaluationResult.map(Result.apply),
      confirmedAllocation = statusData.applicationStatus match {
        case ApplicationStatus.ALLOCATION_UNCONFIRMED => false
        case ApplicationStatus.ALLOCATION_CONFIRMED => true
        case _ => o.confirmedAllocation.getOrElse(false)
      },
      phase1TestData = o.phase1TestData.map(Phase1TestData.apply),
      phase2TestData = o.phase2TestData.map(Phase2TestData.apply)
    )
  }
}
