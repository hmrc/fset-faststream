package repositories.onlinetesting

import factories.ITDateTimeFactoryMock
import model.ProgressStatuses.ProgressStatus
import model.persisted.phase3tests.Phase3TestGroup
import model.persisted.{ Phase1TestProfile, Phase2TestGroup }
import reactivemongo.api.commands.{ UpdateWriteResult, WriteResult }
import reactivemongo.bson.{ BSONArray, BSONDocument }
import repositories.application.GeneralApplicationMongoRepository
import services.GBTimeZoneService
import testkit.MongoRepositorySpec
import reactivemongo.play.json.ImplicitBSONHandlers

import scala.concurrent.Future
import config.MicroserviceAppConfig._
import model.SchemeId
import repositories.CollectionNames

trait ApplicationDataFixture {
  this: MongoRepositorySpec =>

  def helperRepo = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, testIntegrationGatewayConfig, eventsConfig)

  def phase1TestRepo = new Phase1TestMongoRepository(ITDateTimeFactoryMock)

  def phase2TestRepo = new Phase2TestMongoRepository(ITDateTimeFactoryMock)

  def phase3TestRepo = new Phase3TestMongoRepository(ITDateTimeFactoryMock)

  import ImplicitBSONHandlers._

  override val collectionName = CollectionNames.APPLICATION

  def updateApplication(doc: BSONDocument, appId: String): Future[UpdateWriteResult] =
    phase1TestRepo.collection.update(BSONDocument("applicationId" -> appId), doc)

  def createApplication(appId: String, userId: String, frameworkId: String, appStatus: String,
                        needsSupportForOnlineAssessment: Boolean, adjustmentsConfirmed: Boolean, timeExtensionAdjustments: Boolean,
                        fastPassApplicable: Boolean = false): WriteResult = {

    helperRepo.collection.insert(BSONDocument(
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name",
        "lastName" -> "Test Last Name"),
      "civil-service-experience-details.applicable" -> fastPassApplicable,
      "assistance-details" -> createAssistanceDetails(needsSupportForOnlineAssessment, adjustmentsConfirmed, timeExtensionAdjustments)
    )).futureValue
  }

  def createAssistanceDetails(needsSupportForOnlineAssessment: Boolean,
                              adjustmentsConfirmed: Boolean,
                              timeExtensionAdjustments: Boolean): BSONDocument = {
    if (needsSupportForOnlineAssessment) {
      if (adjustmentsConfirmed) {
        if (timeExtensionAdjustments) {
          BSONDocument(
            "needsSupportForOnlineAssessment" -> "Yes",
            "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
            "adjustmentsConfirmed" -> true,
            "verbalTimeAdjustmentPercentage" -> 9,
            "numericalTimeAdjustmentPercentage" -> 11
          )
        } else {
          BSONDocument(
            "needsSupportForOnlineAssessment" -> "Yes",
            "typeOfAdjustments" -> BSONArray("room alone"),
            "adjustmentsConfirmed" -> true
          )
        }
      } else {
        BSONDocument(
          "needsSupportForOnlineAssessment" -> "Yes",
          "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
          "adjustmentsConfirmed" -> false
        )
      }
    } else {
      BSONDocument(
        "needsSupportForOnlineAssessment" -> "No"
      )
    }
  }

  def createOnlineTestApplication(appId: String, applicationStatus: String, xmlReportSavedOpt: Option[Boolean] = None,
                                  alreadyEvaluatedAgainstPassmarkVersionOpt: Option[String] = None): String = {
    val result = (xmlReportSavedOpt, alreadyEvaluatedAgainstPassmarkVersionOpt) match {
      case (None, None) =>
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
  // scalastyle:off method.length
  def createApplicationWithAllFields(userId: String,
    appId: String,
    testAccountId: String,
    frameworkId: String = "frameworkId",
    appStatus: String,
    needsSupportForOnlineAssessment: Boolean = false,
    needsSupportAtVenue: Boolean = false,
    adjustmentsConfirmed: Boolean = false,
    timeExtensionAdjustments: Boolean = false,
    fastPassApplicable: Boolean = false,
    fastPassReceived: Boolean = false,
    fastPassAccepted: Option[Boolean] = None,
    isGis: Boolean = false,
    additionalProgressStatuses: List[(ProgressStatus, Boolean)] = List.empty,
    phase1TestProfile: Option[Phase1TestProfile] = None,
    phase2TestGroup: Option[Phase2TestGroup] = None,
    phase3TestGroup: Option[Phase3TestGroup] = None,
    typeOfEtrayOnlineAdjustments: List[String] = List("etrayTimeExtension", "etrayOther"),
    applicationRoute: String = "Faststream"
  ): Future[WriteResult] = {
    val doc = BSONDocument(
      "applicationId" -> appId,
      "testAccountId" -> testAccountId,
      "applicationStatus" -> appStatus,
      "userId" -> userId,
      "applicationRoute" -> applicationRoute,
      "frameworkId" -> frameworkId,
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
        "fastPassReceived" -> fastPassReceived,
        "fastPassAccepted" -> fastPassAccepted
      ),
      "assistance-details" -> createAssistanceDetails(needsSupportForOnlineAssessment, adjustmentsConfirmed, timeExtensionAdjustments,
        needsSupportAtVenue, isGis, typeOfEtrayOnlineAdjustments),
      "issue" -> "this candidate has changed the email",
      "progress-status" -> progressStatus(additionalProgressStatuses),
      "scheme-preferences" -> schemes,
      "testGroups" -> testGroups(phase1TestProfile, phase2TestGroup, phase3TestGroup)
    )

    helperRepo.collection.insert(doc)
  }
  // scalastyle:on method.length
  // scalastyle:on parameter.number

  val Commercial = SchemeId("Commercial")
  val Edip = SchemeId("Edip")
  val Finance = SchemeId("Finance")
  private def schemes: BSONDocument = BSONDocument("schemes" -> List(Commercial, Edip, Finance))

  private def testGroups(p1: Option[Phase1TestProfile], p2: Option[Phase2TestGroup], p3: Option[Phase3TestGroup]): BSONDocument = {
    BSONDocument("PHASE1" -> p1.map(Phase1TestProfile.bsonHandler.write),
      "PHASE2" -> p2.map(Phase2TestGroup.bsonHandler.write),
      "PHASE3" -> p3.map(Phase3TestGroup.bsonHandler.write)
    )
  }

  def progressStatus(args: List[(ProgressStatus, Boolean)] = List.empty): BSONDocument = {
    val baseDoc = BSONDocument(
      "personal-details" -> true,
      "in_progress" -> true,
      "scheme-preferences" -> true,
      "assistance-details" -> true,
      "questionnaire" -> questionnaire(),
      "preview" -> true,
      "submitted" -> true
    )

    args.foldLeft(baseDoc)((acc, v) => acc.++(v._1.toString -> v._2))
  }

  private def questionnaire() = {
    BSONDocument(
      "start_questionnaire" -> true,
      "diversity_questionnaire" -> true,
      "education_questionnaire" -> true,
      "occupation_questionnaire" -> true
    )
  }

  private def createAssistanceDetails(needsSupportForOnlineAssessment: Boolean, adjustmentsConfirmed: Boolean,
                                      timeExtensionAdjustments: Boolean, needsSupportAtVenue: Boolean = false, isGis: Boolean = false,
                                      typeOfAdjustments: List[String] = List("etrayTimeExtension", "etrayOther")) = {
    if (needsSupportForOnlineAssessment) {
      if (adjustmentsConfirmed) {
        if (timeExtensionAdjustments) {
          BSONDocument(
            "hasDisability" -> "No",
            "needsSupportForOnlineAssessment" -> true,
            "needsSupportAtVenue" -> needsSupportAtVenue,
            "typeOfAdjustments" -> BSONArray(typeOfAdjustments),
            "adjustmentsConfirmed" -> true,
            "etray" -> BSONDocument(
              "timeNeeded" -> 20,
              "otherInfo" -> "other online adjustments"
            ),
            "guaranteedInterview" -> isGis
          )
        } else {
          BSONDocument(
            "needsSupportForOnlineAssessment" -> true,
            "needsSupportAtVenue" -> needsSupportAtVenue,
            "typeOfAdjustments" -> BSONArray(typeOfAdjustments),
            "adjustmentsConfirmed" -> true,
            "guaranteedInterview" -> isGis
          )
        }
      } else {
        BSONDocument(
          "needsSupportForOnlineAssessment" -> true,
          "needsSupportAtVenue" -> needsSupportAtVenue,
          "typeOfAdjustments" -> BSONArray(typeOfAdjustments),
          "adjustmentsConfirmed" -> false,
          "guaranteedInterview" -> isGis
        )
      }
    } else {
      BSONDocument(
        "needsSupportForOnlineAssessment" -> false,
        "needsSupportAtVenue" -> needsSupportAtVenue,
        "guaranteedInterview" -> isGis,
        "adjustmentsConfirmed" -> adjustmentsConfirmed
      )
    }
  }

  val testCandidate = Map(
    "firstName" -> "George",
    "lastName" -> "Jetson",
    "preferredName" -> "Georgy",
    "dateOfBirth" -> "1986-05-01"
  )

  def insertApplication(appId: String, userId: String): WriteResult = {
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
