package repositories.onlinetesting

import factories.DateTimeFactory
import model.ProgressStatuses.{ DIVERSITY_QUESTIONNAIRE_COMPLETED, EDUCATION_QUESTIONS_COMPLETED, IN_PROGRESS_ASSISTANCE_DETAILS_COMPLETED, IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES_COMPLETED, OCCUPATION_QUESTIONS_COMPLETED, PERSONAL_DETAILS_COMPLETED, PREVIEW, ProgressStatus, SCHEME_PREFERENCES_COMPLETED, START_DIVERSITY_QUESTIONNAIRE_COMPLETED, SUBMITTED }
import model.persisted.Phase1TestProfile
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONArray, BSONDocument}
import repositories.application.{GeneralApplicationMongoRepository, GeneralApplicationRepoBSONToModelHelper}
import services.GBTimeZoneService
import testkit.MongoRepositorySpec
import reactivemongo.json.ImplicitBSONHandlers

import scala.concurrent.Future
import config.MicroserviceAppConfig.cubiksGatewayConfig

trait ApplicationDataFixture extends MongoRepositorySpec {
  def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService, cubiksGatewayConfig, GeneralApplicationRepoBSONToModelHelper)
  def phase1TestRepo = new Phase1TestMongoRepository(DateTimeFactory)
  def phase2TestRepo = new Phase2TestMongoRepository(DateTimeFactory)
  def phase3TestRepo = new Phase3TestMongoRepository(DateTimeFactory)

  import ImplicitBSONHandlers._

  override val collectionName = "application"

  def updateApplication(doc: BSONDocument, appId: String) =
    phase1TestRepo.collection.update(BSONDocument("applicationId" -> appId), doc)

  def createApplication(appId: String, userId: String, frameworkId: String, appStatus: String,
    needsAdjustment: Boolean, adjustmentsConfirmed: Boolean, timeExtensionAdjustments: Boolean,
    fastPassApplicable: Boolean = false) = {

    helperRepo.collection.insert(BSONDocument(
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name",
                                          "lastName" -> "Test Last Name"),
      "civil-service-experience-details.applicable" -> fastPassApplicable,
      "assistance-details" -> createAssistanceDetails(needsAdjustment, adjustmentsConfirmed, timeExtensionAdjustments)
    )).futureValue
  }

  def createAssistanceDetails(needsAdjustment: Boolean, adjustmentsConfirmed: Boolean, timeExtensionAdjustments:Boolean) = {
    if (needsAdjustment) {
      if (adjustmentsConfirmed) {
        if (timeExtensionAdjustments) {
          BSONDocument(
            "needsAdjustment" -> "Yes",
            "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
            "adjustments-confirmed" -> true,
            "verbalTimeAdjustmentPercentage" -> 9,
            "numericalTimeAdjustmentPercentage" -> 11
          )
        } else {
          BSONDocument(
            "needsAdjustment" -> "Yes",
            "typeOfAdjustments" -> BSONArray("room alone"),
            "adjustments-confirmed" -> true
          )
        }
      } else {
        BSONDocument(
          "needsAdjustment" -> "Yes",
          "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
          "adjustments-confirmed" -> false
        )
      }
    } else {
      BSONDocument(
        "needsAdjustment" -> "No"
      )
    }
  }

  def createOnlineTestApplication(appId: String, applicationStatus: String, xmlReportSavedOpt: Option[Boolean] = None,
    alreadyEvaluatedAgainstPassmarkVersionOpt: Option[String] = None): String = {
    val result = (xmlReportSavedOpt, alreadyEvaluatedAgainstPassmarkVersionOpt) match {
      case (None, None ) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus
        ))
      case (Some(xmlReportSaved), None) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus,
          "online-tests" -> BSONDocument("xmlReportSaved" -> xmlReportSaved)
        ))
      case (None, Some(alreadyEvaluatedAgainstPassmarkVersion)) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus,
          "passmarkEvaluation" -> BSONDocument("passmarkVersion" -> alreadyEvaluatedAgainstPassmarkVersion)
        ))
      case (Some(xmlReportSaved), Some(alreadyEvaluatedAgainstPassmarkVersion)) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "applicationStatus" -> applicationStatus,
          "online-tests" -> BSONDocument("xmlReportSaved" -> xmlReportSaved),
          "passmarkEvaluation" -> BSONDocument("passmarkVersion" -> alreadyEvaluatedAgainstPassmarkVersion)
        ))
    }

    result.futureValue

    appId
  }

  // scalastyle:off parameter.number
  def createApplicationWithAllFields(userId: String, appId: String, frameworkId: String = "frameworkId",
    appStatus: String, needsAdjustment: Boolean = false, adjustmentsConfirmed: Boolean = false,
    timeExtensionAdjustments: Boolean = false, fastPassApplicable: Boolean = false,
    fastPassReceived: Boolean = false, isGis: Boolean = false,
    additionalProgressStatuses: List[(ProgressStatus, Boolean)] = List.empty,
    phase1TestProfile: Option[Phase1TestProfile] = None
  ): Future[WriteResult] = {
    val doc = BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "framework-preferences" -> BSONDocument(
        "firstLocation" -> BSONDocument(
          "region" -> "Region1",
          "location" -> "Location1",
          "firstFramework" -> "Commercial",
          "secondFramework" -> "Digital and technology"
        ),
        "secondLocation" -> BSONDocument(
          "location" -> "Location2",
          "firstFramework" -> "Business",
          "secondFramework" -> "Finance"
        ),
        "alternatives" -> BSONDocument(
          "location" -> true,
          "framework" -> true
        )
      ),
      "personal-details" -> BSONDocument(
        "firstName" -> s"${testCandidate("firstName")}",
        "lastName" -> s"${testCandidate("lastName")}",
        "preferredName" -> s"${testCandidate("preferredName")}",
        "dateOfBirth" -> s"${testCandidate("dateOfBirth")}",
        "aLevel" -> true,
        "stemLevel" -> true
      ),
      "civil-service-experience-details" -> BSONDocument(
        "applicable" -> fastPassApplicable,
        "fastPassReceived" -> fastPassReceived
      ),
      "assistance-details" -> createAssistanceDetails(needsAdjustment, adjustmentsConfirmed, timeExtensionAdjustments, isGis),
      "issue" -> "this candidate has changed the email",
      "progress-status" -> progressStatus(additionalProgressStatuses),
      "testGroups" -> phase1TestGroup(phase1TestProfile)
    )

    helperRepo.collection.insert(doc)
  }
  // scalastyle:on parameter.number

  private def phase1TestGroup(o: Option[Phase1TestProfile]): BSONDocument = {
    BSONDocument("PHASE1" -> o.map(Phase1TestProfile.bsonHandler.write))
  }

  def progressStatus(args: List[(ProgressStatus, Boolean)] = List.empty): BSONDocument = {
    val baseDoc = BSONDocument(
      PERSONAL_DETAILS_COMPLETED.key -> true,
      SCHEME_PREFERENCES_COMPLETED.key -> true,
      IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES_COMPLETED.key -> true,
      IN_PROGRESS_ASSISTANCE_DETAILS_COMPLETED.key -> true,
      "questionnaire" -> questionnaire(),
      PREVIEW.key -> true,
      SUBMITTED.key -> true
    )

    args.foldLeft(baseDoc)((acc, v) => acc.++(v._1.toString -> v._2))
  }

  private def questionnaire() = {
    BSONDocument(
      START_DIVERSITY_QUESTIONNAIRE_COMPLETED.key -> true,
      DIVERSITY_QUESTIONNAIRE_COMPLETED.key -> true,
      EDUCATION_QUESTIONS_COMPLETED.key -> true,
      OCCUPATION_QUESTIONS_COMPLETED.key -> true
    )
  }

  private def createAssistanceDetails(needsSupportForOnlineAssessment: Boolean, adjustmentsConfirmed: Boolean,
    timeExtensionAdjustments:Boolean, isGis: Boolean = false) = {
    if (needsSupportForOnlineAssessment) {
      if (adjustmentsConfirmed) {
        if (timeExtensionAdjustments) {
          BSONDocument(
            "needsSupportForOnlineAssessment" -> needsSupportForOnlineAssessment,
            "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
            "adjustments-confirmed" -> true,
            "verbalTimeAdjustmentPercentage" -> 9,
            "numericalTimeAdjustmentPercentage" -> 11,
            "guaranteedInterview" -> isGis
          )
        } else {
          BSONDocument(
            "needsSupportForOnlineAssessment" -> needsSupportForOnlineAssessment,
            "typeOfAdjustments" -> BSONArray("room alone"),
            "adjustments-confirmed" -> true,
            "guaranteedInterview" -> isGis
          )
        }
      } else {
        BSONDocument(
          "needsSupportForOnlineAssessment" -> needsSupportForOnlineAssessment,
          "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
          "adjustments-confirmed" -> false,
          "guaranteedInterview" -> isGis
        )
      }
    } else {
      BSONDocument(
        "needsSupportForOnlineAssessment" -> needsSupportForOnlineAssessment,
        "guaranteedInterview" -> isGis
      )
    }
  }

  val testCandidate = Map(
    "firstName" -> "George",
    "lastName" -> "Jetson",
    "preferredName" -> "Georgy",
    "dateOfBirth" -> "1986-05-01"
  )

  def insertApplication(appId: String, userId: String) = {
    helperRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> userId,
      "personal-details" -> BSONDocument(
        "firstName" -> s"${testCandidate("firstName")}",
        "lastName" -> s"${testCandidate("lastName")}",
        "preferredName" -> s"${testCandidate("preferredName")}",
        "dateOfBirth" -> s"${testCandidate("dateOfBirth")}",
        "aLevel" -> true,
        "stemLevel" -> true
      ))).futureValue
  }
}
