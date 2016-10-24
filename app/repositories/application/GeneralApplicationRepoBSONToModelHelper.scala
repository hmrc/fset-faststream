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

package repositories.application

import model.ApplicationStatus.{apply => _, _}
import model.AssessmentScheduleCommands.ApplicationForAssessmentAllocation
import model.CivilServiceExperienceType.{CivilServiceExperienceType, apply => _, _}
import model.Commands.{Candidate, _}
import model.InternshipType.{InternshipType, apply => _}
import model.OnlineTestCommands.TestResult
import model.SchemeType._
import model.command.ProgressResponse
import model.persisted.{ApplicationForNotification, Phase1TestProfile}
import model.report._
import model.{CivilServiceExperienceType, InternshipType}
import org.joda.time.{DateTime, LocalDate}
import reactivemongo.bson.{BSONDocument, _}
import repositories._


object GeneralApplicationRepoBSONToModelHelper extends GeneralApplicationRepoBSONToModelHelper

trait GeneralApplicationRepoBSONToModelHelper {

  def toReportWithPersonalDetails(findProgress: (BSONDocument, String) => ProgressResponse)
                                 (doc: BSONDocument): ReportWithPersonalDetails = {
    val fr = doc.getAs[BSONDocument]("framework-preferences")
    val fr1 = fr.flatMap(_.getAs[BSONDocument]("firstLocation"))
    val fr2 = fr.flatMap(_.getAs[BSONDocument]("secondLocation"))

    def frLocation(root: Option[BSONDocument]) = extract("location")(root)
    def frScheme1(root: Option[BSONDocument]) = extract("firstFramework")(root)
    def frScheme2(root: Option[BSONDocument]) = extract("secondFramework")(root)

    val personalDetails = doc.getAs[BSONDocument]("personal-details")
    val aLevel = personalDetails.flatMap(_.getAs[Boolean]("aLevel").map(booleanTranslator))
    val stemLevel = personalDetails.flatMap(_.getAs[Boolean]("stemLevel").map(booleanTranslator))
    val firstName = personalDetails.flatMap(_.getAs[String]("firstName"))
    val lastName = personalDetails.flatMap(_.getAs[String]("lastName"))
    val preferredName = personalDetails.flatMap(_.getAs[String]("preferredName"))
    val dateOfBirth = personalDetails.flatMap(_.getAs[String]("dateOfBirth"))

    val fpAlternatives = fr.flatMap(_.getAs[BSONDocument]("alternatives"))
    val location = fpAlternatives.flatMap(_.getAs[Boolean]("location").map(booleanTranslator))
    val framework = fpAlternatives.flatMap(_.getAs[Boolean]("framework").map(booleanTranslator))

    val ad = doc.getAs[BSONDocument]("assistance-details")
    val needsAssistance = ad.flatMap(_.getAs[String]("needsAssistance"))
    val needsAdjustment = ad.flatMap(_.getAs[String]("needsAdjustment"))
    val guaranteedInterview = ad.flatMap(_.getAs[String]("guaranteedInterview"))

    val applicationId = doc.getAs[String]("applicationId").getOrElse("")
    val userId = doc.getAs[String]("userId").getOrElse("")
    val progress: ProgressResponse = findProgress(doc, applicationId)

    val onlineTests = doc.getAs[BSONDocument]("online-tests")
    val cubiksUserId = onlineTests.flatMap(_.getAs[Int]("cubiksUserId"))

    ReportWithPersonalDetails(
      applicationId, userId, Some(ProgressStatusesReportLabels.progressStatusNameInReports(progress)),
      frLocation(fr1), frScheme1(fr1), frScheme2(fr1),
      frLocation(fr2), frScheme1(fr2), frScheme2(fr2), aLevel,
      stemLevel, location, framework, needsAssistance, needsAdjustment, guaranteedInterview, firstName, lastName,
      preferredName, dateOfBirth, cubiksUserId
    )
  }

  def toCandidateProgressReport(findProgress: (BSONDocument, String) => ProgressResponse)
                               (doc: BSONDocument): CandidateProgressReport = {
    val schemesDoc = doc.getAs[BSONDocument]("scheme-preferences")
    val schemes = schemesDoc.flatMap(_.getAs[List[SchemeType]]("schemes"))

    val adDoc = doc.getAs[BSONDocument]("assistance-details")
    val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
    val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).map(booleanTranslator)
    val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)
    val gis = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview")).map(booleanTranslator)

    val fpDoc = doc.getAs[BSONDocument]("civil-service-experience-details")
    val civilServiceExperienceType = (fpType: CivilServiceExperienceType) => fpDoc.map(
      _.getAs[CivilServiceExperienceType]("civilServiceExperienceType").contains(fpType)
    )
    val internshipTypes = (internshipType: InternshipType) =>
      fpDoc.map(_.getAs[List[InternshipType]]("internshipTypes").getOrElse(List.empty[InternshipType]).contains(internshipType))

    val civilServant = civilServiceExperienceType(CivilServiceExperienceType.CivilServant).map(booleanTranslator)
    val fastTrack = civilServiceExperienceType(CivilServiceExperienceType.CivilServantViaFastTrack).map(booleanTranslator)
    val edip = internshipTypes(InternshipType.EDIP).map(booleanTranslator)
    val sdipPrevious = internshipTypes(InternshipType.SDIPPreviousYear).map(booleanTranslator)
    val sdip = internshipTypes(InternshipType.SDIPCurrentYear).map(booleanTranslator)
    val fastPassCertificate = fpDoc.map(_.getAs[String]("certificateNumber").getOrElse("No"))

    val applicationId = doc.getAs[String]("applicationId").getOrElse("")
    val progress: ProgressResponse = findProgress(doc, applicationId)

    CandidateProgressReport(applicationId, Some(ProgressStatusesReportLabels.progressStatusNameInReports(progress)),
      schemes.getOrElse(List.empty[SchemeType]), disability, onlineAdjustments,
      assessmentCentreAdjustments, gis, civilServant, fastTrack, edip, sdipPrevious, sdip, fastPassCertificate)
  }

  def toCandidate(doc: BSONDocument): Candidate = {
    val userId = doc.getAs[String]("userId").getOrElse("")
    val applicationId = doc.getAs[String]("applicationId")

    val psRoot = doc.getAs[BSONDocument]("personal-details")
    val firstName = psRoot.flatMap(_.getAs[String]("firstName"))
    val lastName = psRoot.flatMap(_.getAs[String]("lastName"))
    val preferredName = psRoot.flatMap(_.getAs[String]("preferredName"))
    val dateOfBirth = psRoot.flatMap(_.getAs[LocalDate]("dateOfBirth"))

    Candidate(userId, applicationId, None, firstName, lastName, preferredName, dateOfBirth, None, None, None)
  }

  def toCivilServiceExperienceDetailsReportItem(optDoc: Option[BSONDocument]): Option[CivilServiceExperienceDetailsReportItem] = {
    optDoc.map { doc =>
      val civilServiceExperienceType = (fpType: CivilServiceExperienceType) =>
        doc.getAs[CivilServiceExperienceType]("civilServiceExperienceType").contains(fpType)
      val internshipTypes = (internshipType: InternshipType) =>
        doc.getAs[List[InternshipType]]("internshipTypes").getOrElse(List.empty[InternshipType]).contains(internshipType)
      val civilServant = booleanTranslator(civilServiceExperienceType(CivilServiceExperienceType.CivilServant))
      val fastTrack = booleanTranslator(civilServiceExperienceType(CivilServiceExperienceType.CivilServantViaFastTrack))
      val edip = booleanTranslator(internshipTypes(InternshipType.EDIP))
      val sdipPrevious = booleanTranslator(internshipTypes(InternshipType.SDIPPreviousYear))
      val sdip = booleanTranslator(internshipTypes(InternshipType.SDIPCurrentYear))
      val fastPassCertificate = doc.getAs[String]("certificateNumber").getOrElse("No")
      CivilServiceExperienceDetailsReportItem(Some(civilServant), Some(fastTrack), Some(edip), Some(sdipPrevious),
        Some(sdip), Some(fastPassCertificate))
    }
  }

  def toApplicationForDiversityReportItem(findProgress: (BSONDocument, String) => ProgressResponse)
                                         (doc: BSONDocument): ApplicationForDiversityReportItem = {
    val schemesDoc = doc.getAs[BSONDocument]("scheme-preferences")
    val schemes = schemesDoc.flatMap(_.getAs[List[SchemeType]]("schemes"))

    val adDoc = doc.getAs[BSONDocument]("assistance-details")
    val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
    val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).map(booleanTranslator)
    val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)
    val gis = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview"))

    val civilServiceExperienceDoc = doc.getAs[BSONDocument]("civil-service-experience-details")
    val civilServiceExperience = toCivilServiceExperienceDetailsReportItem(civilServiceExperienceDoc)

    val applicationId = doc.getAs[String]("applicationId").getOrElse("")
    val userId = doc.getAs[String]("userId").getOrElse("")
    val progress: ProgressResponse = findProgress(doc, applicationId)

    ApplicationForDiversityReportItem(applicationId, userId, Some(ProgressStatusesReportLabels.progressStatusNameInReports(progress)),
      schemes.getOrElse(List.empty), disability, gis, onlineAdjustments,
      assessmentCentreAdjustments, civilServiceExperience)
  }

  def toApplicationForOnlineTestPassMarkReportItem(doc: BSONDocument): ApplicationForOnlineTestPassMarkReportItem = {
    import config.MicroserviceAppConfig._

    val applicationId = doc.getAs[String]("applicationId").getOrElse("")

    val schemesDoc = doc.getAs[BSONDocument]("scheme-preferences")
    val schemes = schemesDoc.flatMap(_.getAs[List[SchemeType]]("schemes"))

    val adDoc = doc.getAs[BSONDocument]("assistance-details")
    val gis = adDoc.flatMap(_.getAs[Boolean]("guaranteedInterview"))
    val disability = adDoc.flatMap(_.getAs[String]("hasDisability"))
    val onlineAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).map(booleanTranslator)
    val assessmentCentreAdjustments = adDoc.flatMap(_.getAs[Boolean]("needsSupportAtVenue")).map(booleanTranslator)

    val testGroupsDoc = doc.getAs[BSONDocument]("testGroups")
    val phase1Doc = testGroupsDoc.flatMap(_.getAs[BSONDocument]("PHASE1"))

    val phase1TestProfile = Phase1TestProfile.bsonHandler.read(phase1Doc.get)

    val situationalScheduleId = cubiksGatewayConfig.phase1Tests.scheduleIds("sjq")
    val behaviouralScheduleId = cubiksGatewayConfig.phase1Tests.scheduleIds("bq")

    def getTestResult(phase1TestProfile: Phase1TestProfile, scheduleId: Int) = {
      phase1TestProfile.activeTests.find(_.scheduleId == scheduleId).flatMap { phase1Test =>
        phase1Test.testResult.map { tr =>
          TestResult(status = tr.status, norm = tr.norm, tScore = tr.tScore, raw = tr.raw, percentile = tr.percentile, sten = tr.sten)
        }
      }
    }
    val behaviouralTestResult = getTestResult(phase1TestProfile, behaviouralScheduleId)
    val situationalTestResult = getTestResult(phase1TestProfile, situationalScheduleId)

    ApplicationForOnlineTestPassMarkReportItem(
      applicationId,
      schemes.getOrElse(List.empty[SchemeType]),
      disability,
      gis,
      onlineAdjustments,
      assessmentCentreAdjustments,
      PassMarkReportTestResults(behaviouralTestResult, situationalTestResult))
  }

  def toApplicationsForAssessmentAllocation(doc: BSONDocument): ApplicationForAssessmentAllocation = {
    val userId = doc.getAs[String]("userId").get
    val applicationId = doc.getAs[String]("applicationId").get
    val personalDetails = doc.getAs[BSONDocument]("personal-details").get
    val firstName = personalDetails.getAs[String]("firstName").get
    val lastName = personalDetails.getAs[String]("lastName").get
    val assistanceDetails = doc.getAs[BSONDocument]("assistance-details").get
    val needsSupportAtVenue = assistanceDetails.getAs[Boolean]("needsSupportAtVenue").flatMap(b => Some(booleanTranslator(b))).get
    val onlineTestDetails = doc.getAs[BSONDocument]("online-tests").get
    val invitationDate = onlineTestDetails.getAs[DateTime]("invitationDate").get
    ApplicationForAssessmentAllocation(firstName, lastName, userId, applicationId, needsSupportAtVenue, invitationDate)
  }

  def toApplicationForNotification(doc: BSONDocument): ApplicationForNotification = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ApplicationForNotification(applicationId, userId, preferredName, applicationStatus)
  }

  private def booleanTranslator(bool: Boolean) = bool match {
    case true => "Yes"
    case false => "No"
  }

  private def extract(key: String)(root: Option[BSONDocument]) = root.flatMap(_.getAs[String](key))

}
