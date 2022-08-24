package repositories.onlinetesting

import factories.ITDateTimeFactoryMock
import model.ProgressStatuses.ProgressStatus
import model.SchemeId
import model.persisted.phase3tests.Phase3TestGroup
import model.persisted.{Phase1TestProfile, Phase2TestGroup}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import uk.gov.hmrc.mongo.play.json.Codecs
import repositories.CollectionNames
import repositories.application.GeneralApplicationMongoRepository
import testkit.MongoRepositorySpec

trait ApplicationDataFixture {
  this: MongoRepositorySpec =>

  override val collectionName: String = CollectionNames.APPLICATION

  def helperRepo = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  def phase1TestRepo = new Phase1TestMongoRepository(ITDateTimeFactoryMock, mongo)

  def phase2TestRepo = new Phase2TestMongoRepository(ITDateTimeFactoryMock, mongo)

  def phase3TestRepo = new Phase3TestMongoRepository(ITDateTimeFactoryMock, mongo)

  def applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)

//  import ImplicitBSONHandlers._

//  def updateApplication(doc: BSONDocument, appId: String): Future[UpdateWriteResult] =
//    phase1TestRepo.collection.update(ordered = false).one(BSONDocument("applicationId" -> appId), doc)
  def updateApplication(doc: Document, appId: String) =
    phase1TestRepo.collection.updateOne(filter = Document("applicationId" -> appId), update = doc).toFuture()

  /*
    def createApplication(appId: String, userId: String, frameworkId: String, appStatus: String,
                          needsSupportForOnlineAssessment: Boolean, adjustmentsConfirmed: Boolean, timeExtensionAdjustments: Boolean,
                          fastPassApplicable: Boolean = false): WriteResult = {

      helperRepo.collection.insert(ordered = false).one(BSONDocument(
        "userId" -> userId,
        "frameworkId" -> frameworkId,
        "applicationId" -> appId,
        "applicationStatus" -> appStatus,
        "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name",
          "lastName" -> "Test Last Name"),
        "civil-service-experience-details.applicable" -> fastPassApplicable,
        "assistance-details" -> createAssistanceDetails(needsSupportForOnlineAssessment, adjustmentsConfirmed, timeExtensionAdjustments)
      )).futureValue
    }*/

/*
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
  }*/

  def createOnlineTestApplication(appId: String, applicationStatus: String, xmlReportSavedOpt: Option[Boolean] = None,
                                  alreadyEvaluatedAgainstPassmarkVersionOpt: Option[String] = None): String = {
    val result = (xmlReportSavedOpt, alreadyEvaluatedAgainstPassmarkVersionOpt) match {
      case (None, None) =>
//        helperRepo.collection.insert(ordered = false).one(BSONDocument(
//          "applicationId" -> appId,
//          "applicationStatus" -> applicationStatus
//        ))
      case (Some(xmlReportSaved), None) =>
//        helperRepo.collection.insert(ordered = false).one(BSONDocument(
//          "applicationId" -> appId,
//          "applicationStatus" -> applicationStatus,
//          "online-tests" -> BSONDocument("xmlReportSaved" -> xmlReportSaved)
//        ))
      case (None, Some(alreadyEvaluatedAgainstPassmarkVersion)) =>
//        helperRepo.collection.insert(ordered = false).one(BSONDocument(
//          "applicationId" -> appId,
//          "applicationStatus" -> applicationStatus,
//          "passmarkEvaluation" -> BSONDocument("passmarkVersion" -> alreadyEvaluatedAgainstPassmarkVersion)
//        ))
      case (Some(xmlReportSaved), Some(alreadyEvaluatedAgainstPassmarkVersion)) =>
//        helperRepo.collection.insert(ordered = false).one(BSONDocument(
//          "applicationId" -> appId,
//          "applicationStatus" -> applicationStatus,
//          "online-tests" -> BSONDocument("xmlReportSaved" -> xmlReportSaved),
//          "passmarkEvaluation" -> BSONDocument("passmarkVersion" -> alreadyEvaluatedAgainstPassmarkVersion)
//        ))
    }

//    result.futureValue
    appId
  }

/*
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

    helperRepo.collection.insert(ordered = false).one(doc)
  }
  // scalastyle:on method.length
  // scalastyle:on parameter.number
 */

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
                                    ) = {

    def civilServiceExperienceDetails(fastPassApplicable: Boolean, fastPassReceived: Boolean, fastPassAcceptedOpt: Option[Boolean]) = {
      Document("applicable" -> fastPassApplicable, "fastPassReceived" -> fastPassReceived) ++
        fastPassAcceptedOpt.map (value => Document("fastPassAccepted" -> value)).getOrElse(Document.empty)
    }

    val doc = Document(
      "applicationId" -> appId,
      "testAccountId" -> testAccountId,
      "applicationStatus" -> appStatus,
      "userId" -> userId,
      "applicationRoute" -> applicationRoute,
      "frameworkId" -> frameworkId,
      "personal-details" -> Document(
        "firstName" -> s"${testCandidate("firstName")}",
        "lastName" -> s"${testCandidate("lastName")}",
        "preferredName" -> s"${testCandidate("preferredName")}",
        "dateOfBirth" -> s"${testCandidate("dateOfBirth")}"//,
//        "aLevel" -> true, //TODO: mongo no longer needed
//        "stemLevel" -> true
      ),
      "civil-service-experience-details" -> civilServiceExperienceDetails(fastPassApplicable, fastPassReceived, fastPassAccepted),
      "assistance-details" -> createAssistanceDetails(needsSupportForOnlineAssessment, adjustmentsConfirmed, timeExtensionAdjustments,
        needsSupportAtVenue, isGis, typeOfEtrayOnlineAdjustments),
      "issue" -> "this candidate has changed the email",
      "progress-status" -> progressStatus(additionalProgressStatuses),
      "scheme-preferences" -> schemes,
      "testGroups" -> testGroups(phase1TestProfile, phase2TestGroup, phase3TestGroup)
    )
    applicationCollection.insertOne(doc).toFuture()
  }
  // scalastyle:on method.length
  // scalastyle:on parameter.number

  val Commercial = SchemeId("Commercial")
  val Edip = SchemeId("Edip")
  val Finance = SchemeId("Finance")
  private def schemes: Document = Document("schemes" -> Codecs.toBson(List(Commercial, Edip, Finance)))

/*
  private def testGroups(p1: Option[Phase1TestProfile], p2: Option[Phase2TestGroup], p3: Option[Phase3TestGroup]): BSONDocument = {
    BSONDocument("PHASE1" -> p1.map(Phase1TestProfile.bsonHandler.write),
      "PHASE2" -> p2.map(Phase2TestGroup.bsonHandler.write),
      "PHASE3" -> p3.map(Phase3TestGroup.bsonHandler.write)
    )
  }*/

  private def testGroups(p1: Option[Phase1TestProfile], p2: Option[Phase2TestGroup], p3: Option[Phase3TestGroup]): Document = {
    // This impl for the scala mongodb driver needs to handle optional values otherwise nulls will be stored
    p1.map(p => Document("PHASE1" -> p.toBson)).getOrElse(Document.empty) ++
    p2.map(p => Document("PHASE2" -> p.toBson)).getOrElse(Document.empty) ++
    p3.map(p => Document("PHASE3" -> p.toBson)).getOrElse(Document.empty)
  }

  def progressStatus(args: List[(ProgressStatus, Boolean)] = List.empty): Document = {
    val baseDoc = Document(
      "personal-details" -> true,
      "in_progress" -> true,
      "scheme-preferences" -> true,
      "assistance-details" -> true,
      "questionnaire" -> questionnaire,
      "preview" -> true,
      "submitted" -> true
    )

    args.foldLeft(baseDoc)((acc, v) => acc.++( Document(v._1.toString -> v._2) ))
  }

  private def questionnaire = {
    Document(
      "start_questionnaire" -> true,
      "diversity_questionnaire" -> true,
      "education_questionnaire" -> true,
      "occupation_questionnaire" -> true
    )
  }

  //scalastyle:off
  private def createAssistanceDetails(needsSupportForOnlineAssessment: Boolean, adjustmentsConfirmed: Boolean,
                                      timeExtensionAdjustments: Boolean, needsSupportAtVenue: Boolean = false, isGis: Boolean = false,
                                      typeOfAdjustments: List[String] = List("etrayTimeExtension", "etrayOther")) = {
    if (needsSupportForOnlineAssessment) {
      if (adjustmentsConfirmed) {
        if (timeExtensionAdjustments) {
          Document(
            "hasDisability" -> "No",
            "needsSupportForOnlineAssessment" -> true,
            "needsSupportAtVenue" -> needsSupportAtVenue,
            "typeOfAdjustments" -> BsonArray(typeOfAdjustments),
            "adjustmentsConfirmed" -> true,
            "etray" -> Document(
              "timeNeeded" -> 20,
              "otherInfo" -> "other online adjustments"
            ),
            "guaranteedInterview" -> isGis
          )
        } else {
          Document(
            "needsSupportForOnlineAssessment" -> true,
            "needsSupportAtVenue" -> needsSupportAtVenue,
            "typeOfAdjustments" -> BsonArray(typeOfAdjustments),
            "adjustmentsConfirmed" -> true,
            "guaranteedInterview" -> isGis
          )
        }
      } else {
        Document(
          "needsSupportForOnlineAssessment" -> true,
          "needsSupportAtVenue" -> needsSupportAtVenue,
          "typeOfAdjustments" -> BsonArray(typeOfAdjustments),
          "adjustmentsConfirmed" -> false,
          "guaranteedInterview" -> isGis
        )
      }
    } else {
      Document(
        "needsSupportForOnlineAssessment" -> false,
        "needsSupportAtVenue" -> needsSupportAtVenue,
        "guaranteedInterview" -> isGis,
        "adjustmentsConfirmed" -> adjustmentsConfirmed
      )
    }
  }//scalastyle:on TODO: remove

  val testCandidate = Map(
    "firstName" -> "George",
    "lastName" -> "Jetson",
    "preferredName" -> "Georgy",
    "dateOfBirth" -> "1986-05-01"
  )

/*
  def insertApplication(appId: String, userId: String): WriteResult = {
    helperRepo.collection.insert(ordered = false).one(BSONDocument(
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
 */

  def insertApplication(appId: String, userId: String) = {
    applicationCollection.insertOne(Document(
      "applicationId" -> appId,
      "userId" -> userId,
      "personal-details" -> Document(
        "firstName" -> s"${testCandidate("firstName")}",
        "lastName" -> s"${testCandidate("lastName")}",
        "preferredName" -> s"${testCandidate("preferredName")}",
        "dateOfBirth" -> s"${testCandidate("dateOfBirth")}",
        "aLevel" -> true,
        "stemLevel" -> true
      )
    )).toFuture().futureValue
  }
}
