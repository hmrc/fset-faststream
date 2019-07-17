/*
 * Copyright 2019 HM Revenue & Customs
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

package model.testdata

import connectors.AuthProviderClient.UserRole
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Result
import model.ProgressStatuses.ProgressStatus
import model.command.testdata.CreateCandidateRequest._
import model.persisted.PassmarkEvaluation
import model.testdata.CreateAdminData.AssessorData
import model._
import org.joda.time.format.DateTimeFormat
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
import services.testdata.faker.DataFaker.Random

object CreateCandidateData {

  case class AssistanceDetails(
                                hasDisability: String = Random.yesNoPreferNotToSay,
                                hasDisabilityDescription: String = Random.hasDisabilityDescription,
                                setGis: Boolean = false,
                                onlineAdjustments: Boolean = Random.bool,
                                onlineAdjustmentsDescription: String = Random.onlineAdjustmentsDescription,
                                assessmentCentreAdjustments: Boolean = Random.bool,
                                assessmentCentreAdjustmentsDescription: String = Random.assessmentCentreAdjustmentDescription,
                                phoneAdjustments: Boolean = Random.bool,
                                phoneAdjustmentsDescription: String = Random.phoneAdjustmentsDescription
                              )

  object AssistanceDetails {
    def apply(o: AssistanceDetailsRequest): AssistanceDetails = {
      val default = AssistanceDetails()
      AssistanceDetails(
        hasDisability = o.hasDisability.getOrElse(default.hasDisability),
        hasDisabilityDescription = o.hasDisabilityDescription.getOrElse(default.hasDisabilityDescription),
        setGis = o.setGis.getOrElse(default.setGis),
        onlineAdjustments = o.onlineAdjustments.getOrElse(default.onlineAdjustments),
        onlineAdjustmentsDescription = o.onlineAdjustmentsDescription.getOrElse(default.onlineAdjustmentsDescription),
        assessmentCentreAdjustments = o.assessmentCentreAdjustments.getOrElse(default.assessmentCentreAdjustments),
        assessmentCentreAdjustmentsDescription =
          o.assessmentCentreAdjustmentsDescription.getOrElse(default.assessmentCentreAdjustmentsDescription),
        phoneAdjustments = o.phoneAdjustments.getOrElse(default.phoneAdjustments),
        phoneAdjustmentsDescription = o.phoneAdjustmentsDescription.getOrElse(default.phoneAdjustmentsDescription)
      )
    }
  }

  case class DiversityDetails(
                               genderIdentity: String = Random.gender,
                               sexualOrientation: String = Random.sexualOrientation,
                               ethnicity: String = Random.ethnicGroup,
                               universityAttended: String = Random.university._2,
                               parentalEmployedOrSelfEmployed: String = Random.parentsOccupation,
                               parentalEmployment: Option[String] = Some(Random.parentsOccupationDetails),
                               parentalCompanySize: Option[String] = Some(Random.sizeParentsEmployeer)
                             )

  object DiversityDetails {
    def apply(o: DiversityDetailsRequest): DiversityDetails = {
      val default = DiversityDetails()
      DiversityDetails(
        genderIdentity = o.genderIdentity.getOrElse(default.genderIdentity),
        sexualOrientation = o.sexualOrientation.getOrElse(default.sexualOrientation),
        ethnicity = o.ethnicity.getOrElse(default.ethnicity),
        universityAttended = o.universityAttended.getOrElse(default.universityAttended),
        parentalEmployedOrSelfEmployed = o.parentalEmployedOrSelfEmployed.getOrElse(default.parentalEmployedOrSelfEmployed),
        parentalEmployment = o.parentalEmployment,
        parentalCompanySize = o.parentalCompanySize
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
                             start: Option[DateTime] = None,
                             expiry: Option[DateTime] = None,
                             completion: Option[DateTime] = None,
                             bqtscore: Option[Double] = None,
                             sjqtscore: Option[Double] = None,
                             passmarkEvaluation: Option[PassmarkEvaluation] = None
                           ) extends TestDates

  object Phase1TestData {
    def apply(testDataRequest: Phase1TestDataRequest): Phase1TestData = {
      Phase1TestData(
        start = testDataRequest.start.map(DateTime.parse),
        expiry = testDataRequest.expiry.map(DateTime.parse),
        completion = testDataRequest.completion.map(DateTime.parse),
        bqtscore = testDataRequest.bqtscore.map(_.toDouble),
        sjqtscore = testDataRequest.sjqtscore.map(_.toDouble),
        passmarkEvaluation = testDataRequest.passmarkEvaluation
      )
    }
  }

  case class Phase2TestData(
                             start: Option[DateTime] = None,
                             expiry: Option[DateTime] = None,
                             completion: Option[DateTime] = None,
                             tscore: Option[Double] = None,
                             passmarkEvaluation: Option[PassmarkEvaluation] = None
                           ) extends TestDates with TestResult

  object Phase2TestData {
    def apply(o: Phase2TestDataRequest): Phase2TestData = {
      Phase2TestData(
        start = o.start.map(DateTime.parse),
        expiry = o.expiry.map(DateTime.parse),
        completion = o.completion.map(DateTime.parse),
        tscore = o.tscore.map(_.toDouble),
        passmarkEvaluation = o.passmarkEvaluation
      )
    }
  }

  case class Phase3TestData(
                             start: Option[DateTime] = None,
                             expiry: Option[DateTime] = None,
                             completion: Option[DateTime] = None,
                             score: Option[Double] = None,
                             generateNullScoresForFewQuestions: Option[Boolean] = None,
                             receivedBeforeInHours: Option[Int] = None,
                             passmarkEvaluation: Option[PassmarkEvaluation] = None
                           )

  object Phase3TestData {
    def apply(o: Phase3TestDataRequest): Phase3TestData = {
      Phase3TestData(
        start = o.start.map(DateTime.parse),
        expiry = o.expiry.map(DateTime.parse),
        completion = o.completion.map(DateTime.parse),
        score = o.score,
        generateNullScoresForFewQuestions = o.generateNullScoresForFewQuestions,
        receivedBeforeInHours = o.receivedBeforeInHours,
        passmarkEvaluation = o.passmarkEvaluation
      )
    }
  }

  case class PersonalData(
                           emailPrefix: String = s"tesf${Random.number() - 1}",
                           firstName: String = Random.getFirstname(1),
                           lastName: String = Random.getLastname(1),
                           preferredName: Option[String] = None,
                           dob: LocalDate = new LocalDate(1981, 5, 21),
                           postCode: Option[String] = None,
                           country: Option[String] = None,
                           edipCompleted: Option[Boolean] = None,
                           role: Option[UserRole] = None
                         ) {
    def getPreferredName: String = preferredName.getOrElse(s"Pref$firstName")
  }

  object PersonalData {

    def apply(o: PersonalDataRequest, generatorId: Int): PersonalData = {
      val default = PersonalData()
      val fname = o.firstName.getOrElse(Random.getFirstname(generatorId))
      val emailPrefix = o.emailPrefix.map(e => if (generatorId > 1) {
        s"$e-$generatorId"
      } else {
        e
      })

      PersonalData(
        emailPrefix = emailPrefix.getOrElse(s"tesf${Random.number()}-$generatorId"),
        firstName = fname,
        lastName = o.lastName.getOrElse(Random.getLastname(generatorId)),
        preferredName = o.preferredName,
        dob = o.dateOfBirth.map(x => LocalDate.parse(x, DateTimeFormat.forPattern("yyyy-MM-dd"))).getOrElse(default.dob),
        postCode = o.postCode,
        country = o.country,
        edipCompleted = o.edipCompleted
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
    def apply(o: StatusDataRequest): StatusData = {
      StatusData(applicationStatus = ApplicationStatus.withName(o.applicationStatus),
        previousApplicationStatus = o.previousApplicationStatus.map(ApplicationStatus.withName),
        progressStatus = o.progressStatus.map(ProgressStatuses.nameToProgressStatus),
        applicationRoute = o.applicationRoute.map(ApplicationRoute.withName).getOrElse(ApplicationRoute.Faststream)
      )
    }
  }

  case class CreateCandidateData(statusData: StatusData,
                                 personalData: PersonalData = PersonalData(),
                                 diversityDetails: DiversityDetails = DiversityDetails(),
                                 assistanceDetails: AssistanceDetails = AssistanceDetails(),
                                 cubiksUrl: String,
                                 schemeTypes: Option[List[SchemeId]] = None,
                                 isCivilServant: Boolean = false,
                                 hasFastPass: Boolean = false,
                                 hasDegree: Boolean = Random.bool,
                                 region: Option[String] = None,
                                 phase1TestData: Option[Phase1TestData] = None,
                                 phase2TestData: Option[Phase2TestData] = None,
                                 phase3TestData: Option[Phase3TestData] = None,
                                 fsbTestGroupData: Option[FsbTestGroupDataRequest] = None,
                                 adjustmentInformation: Option[Adjustments] = None,
                                 assessorDetails: Option[AssessorData] = None
                                ) extends CreateTestData

  object CreateCandidateData {
    def apply(cubiksUrlFromConfig: String, o: CreateCandidateRequest)(generatorId: Int): CreateCandidateData = {

      val statusData = StatusData(o.statusData)

      CreateCandidateData(
        statusData = statusData,
        personalData = o.personalData.map(PersonalData(_, generatorId)).getOrElse(PersonalData()),
        diversityDetails = o.diversityDetails.map(DiversityDetails(_)).getOrElse(DiversityDetails()),
        assistanceDetails = o.assistanceDetails.map(AssistanceDetails.apply).getOrElse(AssistanceDetails()),
        cubiksUrl = cubiksUrlFromConfig,
        schemeTypes = o.schemeTypes,
        isCivilServant = o.isCivilServant.getOrElse(Random.bool),
        hasDegree = o.hasDegree.getOrElse(Random.bool),
        hasFastPass = o.hasFastPass.getOrElse(Random.bool),
        region = o.region,
        phase1TestData = o.phase1TestData.map(Phase1TestData.apply),
        phase2TestData = o.phase2TestData.map(Phase2TestData.apply),
        phase3TestData = o.phase3TestData.map(Phase3TestData.apply),
        fsbTestGroupData = o.fsbTestGroupData,
        adjustmentInformation = o.adjustmentInformation
      )
    }
  }

}
