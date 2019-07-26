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

package model.testdata.candidate

import connectors.AuthProviderClient.UserRole
import model.ApplicationStatus.ApplicationStatus
import model.InternshipType.InternshipType
import model.ProgressStatuses.ProgressStatus
import model._
import model.command.testdata.CreateCandidateRequest._
import model.testdata.CreateAdminData.AssessorData
import model.testdata.CreateTestData
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
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
    psiUrl: String,
    schemeTypes: Option[List[SchemeId]] = None,
    isCivilServant: Boolean = false,
    hasFastPass: Boolean = false,
    internshipTypes: List[InternshipType] = Nil,
    fastPassCertificateNumber: Option[String] = None,
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
    def apply(psiUrlFromConfig: String, o: CreateCandidateRequest)(generatorId: Int): CreateCandidateData = {

      val statusData = StatusData(o.statusData)

      val isCivilServant = o.isCivilServant.getOrElse(Random.bool)
      val hasFastPass = o.hasFastPass.getOrElse(if (isCivilServant) {
        Random.bool
      } else {
        false
      })
      val internshipTypes = if (hasFastPass) {
        o.internshipTypes.map(internshipTypes =>
          internshipTypes.map(internshipType => InternshipType.withName(internshipType)))
          .getOrElse(List(InternshipType.SDIPCurrentYear))
      } else {
        Nil
      }
      val fastPassCertificateNumber = if (hasFastPass) Some(Random.number().toString) else None

      val progressStatusMaybe = o.statusData.progressStatus.map(ProgressStatuses.nameToProgressStatus)
        .orElse(Some(translateApplicationStatusToProgressStatus(o.statusData.applicationStatus)))

      val schemeTypes = progressStatusMaybe.map(progressStatus => {
        if (ProgressStatuses.ProgressStatusOrder.isEqualOrAfter(progressStatus, ProgressStatuses.SCHEME_PREFERENCES).getOrElse(false)) {
          o.schemeTypes.getOrElse(List(SchemeId("Commercial"), SchemeId("Finance")))
        } else {
          Nil
        }
      }).getOrElse(Nil)

      CreateCandidateData(
        statusData = statusData,
        personalData = o.personalData.map(PersonalData(_, generatorId)).getOrElse(PersonalData()),
        diversityDetails = o.diversityDetails.map(DiversityDetails(_)).getOrElse(DiversityDetails()),
        assistanceDetails = o.assistanceDetails.map(AssistanceDetails.apply).getOrElse(AssistanceDetails()),
        psiUrl = psiUrlFromConfig,
        schemeTypes = Some(schemeTypes),
        isCivilServant = isCivilServant,
        hasFastPass = hasFastPass,
        internshipTypes = internshipTypes,
        fastPassCertificateNumber = fastPassCertificateNumber,
        hasDegree = o.hasDegree.getOrElse(Random.bool),
        region = o.region,
        phase1TestData = Phase1TestData.build(o, schemeTypes),
        phase2TestData = Phase2TestData(o, schemeTypes),
        phase3TestData = Phase3TestData(o, schemeTypes),
        fsbTestGroupData = o.fsbTestGroupData,
        adjustmentInformation = o.adjustmentInformation
      )
    }

    private def translateApplicationStatusToProgressStatus(applicationStatus: String) = {
      applicationStatus match {
        case "REGISTERED" => ProgressStatuses.CREATED
        case "IN_PROGRESS_PERSONAL_DETAILS" => ProgressStatuses.PERSONAL_DETAILS
        case "IN_PROGRESS_SCHEME_PREFERENCES" => ProgressStatuses.SCHEME_PREFERENCES
        case "IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES" => ProgressStatuses.PARTNER_GRADUATE_PROGRAMMES
        case "IN_PROGRESS_ASSISTANCE_DETAILS" => ProgressStatuses.ASSISTANCE_DETAILS
        case "IN_PROGRESS_PREVIEW" => ProgressStatuses.PREVIEW
        case _ => ProgressStatuses.nameToProgressStatus(applicationStatus)
      }
    }
  }

}
