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

package model.testdata.candidate

import connectors.AuthProviderClient.UserRole
import model.ApplicationStatus.ApplicationStatus
import model.CivilServantAndInternshipType.CivilServantAndInternshipType
import model.ProgressStatuses.ProgressStatus
import model._
import model.command.testdata.CreateCandidateRequest._
import model.testdata.CreateAdminData.AssessorData
import model.testdata.CreateTestData
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import services.testdata.faker.{ DataFaker, DataFakerRandom }

object CreateCandidateData extends DataFakerRandom {

  case class AssistanceDetails(
    hasDisability: String = "No",
    disabilityImpact: String = "No",
    disabilityCategories: List[String] = List("Prefer Not to Say"),
    hasDisabilityDescription: String = "",
    setGis: Boolean = false,
    onlineAdjustments: Boolean = false,
    onlineAdjustmentsDescription: String = "",
    assessmentCentreAdjustments: Boolean = false,
    assessmentCentreAdjustmentsDescription: String = "",
    phoneAdjustments: Boolean = false,
    phoneAdjustmentsDescription: String = ""
  )

  object AssistanceDetails {
    def apply(o: AssistanceDetailsRequest): AssistanceDetails = {
      val default = AssistanceDetails()
      AssistanceDetails(
        hasDisability = o.hasDisability.getOrElse(default.hasDisability),
        disabilityImpact = o.disabilityImpact.getOrElse(default.disabilityImpact),
        disabilityCategories = o.disabilityCategories.getOrElse(default.disabilityCategories),
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
    parentalCompanySize: Option[String] = Some(Random.sizeParentsEmployer)
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
    firstName: String = Random.firstname(1),
    lastName: String = Random.lastname(1),
    preferredName: Option[String] = None,
    dob: LocalDate = new LocalDate(1981, Random.monthNumber, 21),
    postCode: Option[String] = None,
    country: Option[String] = None,
    edipCompleted: Option[Boolean] = None,
    otherInternshipCompleted: Option[Boolean] = None,
    role: Option[UserRole] = None
  ) {
    def getPreferredName: String = preferredName.getOrElse(s"Pref$firstName")
  }

  object PersonalData {
    def apply(o: PersonalDataRequest, dataFaker: DataFaker, generatorId: Int): PersonalData = {
      val default = PersonalData()
      val fname = o.firstName.getOrElse(dataFaker.Random.firstname(generatorId))
      val emailPrefix = o.emailPrefix.map(e => if (generatorId > 1) {
        s"$e-$generatorId"
      } else {
        e
      })

      PersonalData(
        emailPrefix = emailPrefix.getOrElse(s"tesf${dataFaker.Random.number()}-$generatorId"),
        firstName = fname,
        lastName = o.lastName.getOrElse(dataFaker.Random.lastname(generatorId)),
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

  case class AdjustmentsData(
    adjustments: Option[List[String]] = None,
    adjustmentsConfirmed: Option[Boolean] = None,
    etray: Option[AdjustmentDetailData] = None,
    video: Option[AdjustmentDetailData] = None
  )

  object AdjustmentsData {
    def apply(o: AdjustmentsRequest): AdjustmentsData = {
      AdjustmentsData(adjustments = o.adjustments, adjustmentsConfirmed = o.adjustmentsConfirmed,
        etray = o.etray.map(AdjustmentDetailData(_)), video = o.video.map(AdjustmentDetailData(_))
      )
    }
  }

  case class AdjustmentDetailData(
    timeNeeded: Option[Int] = None,
    percentage: Option[Int] = None,
    otherInfo: Option[String] = None,
    invigilatedInfo: Option[String] = None
  )

  object AdjustmentDetailData {
    def apply(o: AdjustmentDetailRequest): AdjustmentDetailData = {
      AdjustmentDetailData(o.timeNeeded, o.percentage, otherInfo = o.otherInfo, invigilatedInfo = o.invigilatedInfo)
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
    civilServantAndInternshipTypes: List[CivilServantAndInternshipType] = Nil,
    fastPassCertificateNumber: Option[String] = None,
    hasDegree: Boolean = Random.bool,
    region: Option[String] = None,
    phase1TestData: Option[Phase1TestData] = None,
    phase2TestData: Option[Phase2TestData] = None,
    phase3TestData: Option[Phase3TestData] = None,
    fsbTestGroupData: Option[FsbTestGroupDataRequest] = None,
    adjustmentInformation: Option[AdjustmentsData] = None,
    assessorDetails: Option[AssessorData] = None
  ) extends CreateTestData

  object CreateCandidateData {
    def apply(psiUrlFromConfig: String, request: CreateCandidateRequest, dataFaker: DataFaker)(generatorId: Int): CreateCandidateData = {

      val statusData = StatusData(request.statusData)

      val isCivilServant = request.isCivilServant.getOrElse(dataFaker.Random.bool)
      val hasFastPass = request.hasFastPass.getOrElse(false)
      val civilServantAndInternshipTypes = if (hasFastPass) {
        request.civilServantAndInternshipTypes.map(internshipTypes =>
          internshipTypes.map(internshipType => CivilServantAndInternshipType.withName(internshipType)))
          .getOrElse(List(CivilServantAndInternshipType.SDIP))
      } else {
        Nil
      }

      val fastPassCertificateNumber = if (hasFastPass) Some(dataFaker.Random.number().toString) else None

      val progressStatusMaybe = request.statusData.progressStatus.map(ProgressStatuses.nameToProgressStatus)
        .orElse(Some(translateApplicationStatusToProgressStatus(request.statusData.applicationStatus)))

      val schemeTypes = progressStatusMaybe.map(progressStatus => {
        if (ProgressStatuses.ProgressStatusOrder.isEqualOrAfter(progressStatus, ProgressStatuses.SCHEME_PREFERENCES).getOrElse(false)) {
          request.statusData.applicationRoute match {
            case Some("Sdip") => List(SchemeId("Sdip"))
            case Some("Edip") => List(SchemeId("Edip"))
            case _ => request.schemeTypes.getOrElse(List(SchemeId("Commercial"), SchemeId("Finance")))
          }
        } else {
          Nil
        }
      }).getOrElse(Nil)

      CreateCandidateData(
        statusData = statusData,
        personalData = request.personalData.map(PersonalData(_, dataFaker, generatorId)).getOrElse(PersonalData()),
        diversityDetails = request.diversityDetails.map(DiversityDetails(_)).getOrElse(DiversityDetails()),
        assistanceDetails = request.assistanceDetails.map(AssistanceDetails.apply).getOrElse(AssistanceDetails()),
        psiUrl = psiUrlFromConfig,
        schemeTypes = Some(schemeTypes),
        isCivilServant = isCivilServant,
        hasFastPass = hasFastPass,
        civilServantAndInternshipTypes = civilServantAndInternshipTypes,
        fastPassCertificateNumber = fastPassCertificateNumber,
        hasDegree = request.hasDegree.getOrElse(dataFaker.Random.bool),
        region = request.region,
        phase1TestData = Phase1TestData.build(request, schemeTypes),
        phase2TestData = Phase2TestData(request, schemeTypes),
        phase3TestData = Phase3TestData(request, schemeTypes),
        fsbTestGroupData = request.fsbTestGroupData,
        adjustmentInformation = request.adjustmentInformation.map(AdjustmentsData(_))
      )
    }

    private def translateApplicationStatusToProgressStatus(applicationStatus: String) = {
      applicationStatus match {
        case "REGISTERED" => ProgressStatuses.CREATED
        case "IN_PROGRESS_PERSONAL_DETAILS" => ProgressStatuses.PERSONAL_DETAILS
        case "IN_PROGRESS_SCHEME_PREFERENCES" => ProgressStatuses.SCHEME_PREFERENCES
        case "IN_PROGRESS_ASSISTANCE_DETAILS" => ProgressStatuses.ASSISTANCE_DETAILS
        case "IN_PROGRESS_QUESTIONNAIRE" => ProgressStatuses.QUESTIONNAIRE_OCCUPATION
        case "IN_PROGRESS_PREVIEW" => ProgressStatuses.PREVIEW
        case _ => ProgressStatuses.nameToProgressStatus(applicationStatus)
      }
    }
  }
}
