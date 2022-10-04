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

package repositories.application

import connectors.ExchangeObjects

import javax.inject.{Inject, Singleton}
import model.{ApplicationStatus, _}
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.ProgressStatuses.ProgressStatus
import model.persisted.ContactDetails
import org.joda.time.{DateTime, LocalDate}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.UpdateOptions
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import repositories._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

object Utils {
  def chooseOne[T](values: Seq[T]): T =
    values(Random.nextInt(values.size))
}

trait TestDataRepository {
  def createApplications(num: Int): Future[Unit]
}

trait TestDataContactDetailsRepository {
  def createContactDetails(num: Int): Future[Unit]
}

@Singleton
class TestDataContactDetailsMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[ContactDetails](
    collectionName = CollectionNames.CONTACT_DETAILS,
    mongoComponent = mongo,
    domainFormat = ContactDetails.contactDetailsFormat,
    indexes = Nil
  ) with TestDataContactDetailsRepository with ReactiveRepositoryHelpers {

  import Utils.chooseOne

  val postcodes = Seq("AB1 3CD", "BC1 2DE", "CD28 7EF", "DE23 8FG")

  override def createContactDetails(num: Int): Future[Unit] = Future.successful {
    for (i <- 0 until num) {
      createSingleContactDetail(i)
    }
  }

  private def createSingleContactDetail(id: Int): Future[Unit] = {
    val contactDetails = ContactDetails(outsideUk = false, Address("1st High Street"), Some(chooseOne(postcodes)), None,
      s"test_$id@test.com", "123456789")
    val contactDetailsBson = Document("$set" -> Document(
      "contact-details" -> Codecs.toBson(contactDetails)
    ))

    val validator = singleUpsertValidator(id.toString, actionDesc = "creating contact test data")
    collection.updateOne(Document("userId" -> id.toString), contactDetailsBson, UpdateOptions().upsert(true)).toFuture() map validator
  }
}

@Singleton
class TestDataMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[ContactDetails](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = ContactDetails.contactDetailsFormat,
    indexes = Nil
  ) with TestDataRepository with ReactiveRepositoryHelpers {

  import Utils.chooseOne

  // Use this collection when using hand written bson documents
  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(CollectionNames.APPLICATION)

  val applicationStatuses = Seq("CREATED", "IN_PROGRESS", "SUBMITTED", "WITHDRAWN")
  val firstNames = Seq("John", "Chris", "James", "Paul")
  val lastNames = Seq("Clerk", "Smith", "Gulliver", "Swift")
  val preferredName = Seq("Superman", "Batman", "Spiderman", "Wolverine", "Hulk")
  val frameworks = Seq("Digital and technology", "Finance", "Project delivery", "Commercial", "Business")

  val allLocationsAndRegions = Seq("Southend" -> "East", "Nottingham" -> "East-midlands", "London" -> "London", "Darlington" -> "North-east",
    "newcastle" -> "North-east", "Blackpool" -> "North-west", "Stockport" -> "North-west", "Warrington" -> "North-west",
    "Manchester / Salford" -> "North-west", "Liverpool / Bootle / Netherton" -> "North-west", "Bathgate" -> "Scotland",
    "East Kilbride" -> "Scotland", "Edinburgh" -> "Scotland", "Glasgow" -> "Scotland", "Airdrie / Motherwell / Hamilton" -> "Scotland",
    "Springburn / Newlands / Govan" -> "Scotland", "Hastings" -> "South-east", "Reading" -> "South-east", "Worthing" -> "South-east",
    "Bristol" -> "South-west", "St Austell" -> "South-west", "Torquay" -> "South-west", "Cardiff" -> "Wales", "Birmingham" -> "West-midlands",
    "Coventry" -> "West-midlands", "Telford" -> "West-midlands", "Bradford" -> "Yorkshire-humberside",
    "Shipley" -> "Yorkshire-humberside", "Leeds / Sheffield" -> "Yorkshire-humberside")
  val dateOfBirth = Seq(LocalDate.parse("1980-12-12"), LocalDate.parse("1981-12-12"),
    LocalDate.parse("1982-12-12"), LocalDate.parse("1983-12-12"), LocalDate.parse("1984-12-12"),
    LocalDate.parse("1985-12-12"), LocalDate.parse("1987-12-12"), LocalDate.parse("1987-12-12")
  )

  override def createApplications(num: Int): Future[Unit] =
    Future.sequence(
      (0 until num).map { i => createSingleApplication(i) }
    ).map(_ => ())

  // scalastyle:off parameter.number method.length
  def createApplicationWithAllFields(userId: String, appId: String, testAccountId: String, frameworkId: String,
                                     appStatus: ApplicationStatus = ApplicationStatus.IN_PROGRESS, hasDisability: String = "Yes",
                                     needsSupportForOnlineAssessment: Boolean = false,
                                     needsSupportAtVenue: Boolean = false, guaranteedInterview: Boolean = false, lastName: Option[String] = None,
                                     firstName: Option[String] = None, preferredName: Option[String] = None,
                                     additionalProgressStatuses: List[(ProgressStatus, Boolean)] = Nil,
                                     additionalDoc: Document = Document.empty,
                                     applicationRoute: Option[ApplicationRoute] = None
                                    ) = {
    val document = Document(
      "applicationId" -> appId,
      "testAccountId" -> testAccountId,
      "applicationStatus" -> appStatus.toBson,
      "applicationRoute" -> Codecs.toBson(applicationRoute.getOrElse(ApplicationRoute.Faststream)),
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "scheme-preferences" -> Document(
        "schemes" -> BsonArray(SchemeId("DiplomaticAndDevelopment").toBson, SchemeId("GovernmentOperationalResearchService").toBson)
      ),
      "personal-details" -> Document(
        "firstName" -> firstName.getOrElse(s"${testCandidate("firstName")}"),
        "lastName" -> lastName.getOrElse(s"${testCandidate("lastName")}"),
        "preferredName" -> preferredName.getOrElse(s"${testCandidate("preferredName")}"),
        "dateOfBirth" -> s"${testCandidate("dateOfBirth")}"
      ),
      "civil-service-experience-details" -> Document(
        "applicable" -> true,
        "civilServantAndInternshipTypes" -> BsonArray(
          CivilServantAndInternshipType.CivilServant.toBson,
          CivilServantAndInternshipType.EDIP.toBson,
          CivilServantAndInternshipType.SDIP.toBson,
          CivilServantAndInternshipType.OtherInternship.toBson
        ),
        "edipYear" -> "2018",
        "sdipYear" -> "2019",
        "otherInternshipName" -> "other",
        "otherInternshipYear" -> "2020",
        "fastPassReceived" -> true,
        "certificateNumber" -> "1234567"
      ),
      "assistance-details" -> Document(
        "hasDisability" -> "Yes",
        "needsSupportForOnlineAssessment" -> needsSupportForOnlineAssessment,
        "needsSupportAtVenue" -> needsSupportAtVenue,
        "guaranteedInterview" -> guaranteedInterview
      ),
      "issue" -> "this candidate has changed the email",
      "progress-status" -> progressStatus(additionalProgressStatuses),
      "progress-status-timestamp" -> Document(
        "IN_PROGRESS" -> dateTimeToBson(DateTime.now()),
        "SUBMITTED" -> dateTimeToBson(DateTime.now())
      )
    ) ++ additionalDoc
    applicationCollection.insertOne(document).toFuture()
  }
  // scalastyle:on parameter.number method.length

  def progressStatus(args: List[(ProgressStatus, Boolean)] = List.empty): Document = {
    val baseDoc = Document(
      "personal-details" -> true,
      "in_progress" -> true,
      "scheme-preferences" -> true,
      "assistance-details" -> true,
      "questionnaire" -> Document(
        "start_questionnaire" -> true,
        "diversity_questionnaire" -> true,
        "education_questionnaire" -> true,
        "occupation_questionnaire" -> true
      ),
      "preview" -> true,
      "submitted" -> true
    )

    args.foldLeft(baseDoc)((acc, v) => acc ++ Document(v._1.toString -> v._2))
  }

  val testCandidate = Map(
    "firstName" -> "George",
    "lastName" -> "Jetson",
    "preferredName" -> "Georgy",
    "dateOfBirth" -> "1986-05-01"
  )

  def createMinimumApplication(userId: String, appId: String, frameworkId: String) = {
    applicationCollection.insertOne(Document(
      "applicationId" -> appId,
      "userId" -> userId,
      "frameworkId" -> frameworkId
    )).toFuture()
  }


  private def createSingleApplication(id: Int): Future[Unit] = {
    val update = Document("$set" -> buildSingleApplication(id))

    val validator = singleUpsertValidator(id.toString, actionDesc = "creating application test data")

    collection.updateOne(Document("userId" -> id.toString), update, UpdateOptions().upsert(true)).toFuture() map validator
  }

  private def createProgress(
                              personalDetails: Option[Document],
                              assistance: Option[Document],
                              isSubmitted: Option[Boolean],
                              isWithdrawn: Option[Boolean]
                            ) = {
    var progress = Document.empty

    progress = personalDetails.map(_ => progress ++ Document("personal-details" -> true)).getOrElse(progress)
    progress = assistance.map(_ => progress ++ Document("assistance-details" -> true)).getOrElse(progress)
    progress = isSubmitted.map(_ => progress ++ Document("submitted" -> true)).getOrElse(progress)
    progress = isWithdrawn.map(_ => progress ++ Document("withdrawn" -> true)).getOrElse(progress)

    progress
  }

  private def buildSingleApplication(id: Int): Document = {
    val personalDetails = createPersonalDetails(id)
    val assistance = createAssistance(id)
    val submitted = isSubmitted(id)(personalDetails, assistance)
    val withdrawn = isWithdrawn(id)(personalDetails, assistance)

    val progress = createProgress(personalDetails, assistance, submitted, withdrawn)

    val applicationStatus = chooseOne(applicationStatuses)

    var document = Document(
      "applicationId" -> id.toString,
      "userId" -> id.toString,
      "frameworkId" -> ExchangeObjects.frameworkId,
      "applicationStatus" -> applicationStatus
    )
    document = buildDocument(document)(personalDetails.map(d => "personal-details" -> d))
    document = buildDocument(document)(assistance.map(d => "assistance-details" -> d))
    document = document ++ Document("progress-status" -> progress)

    document
  }

  private def buildDocument(document: Document)(f: Option[(String, Document)]) = {
    f.map(pair => document ++ Document(pair._1 -> pair._2)).getOrElse(document)
  }

  private def createAssistance(id: Int) = id match {
    case x if x % 7 == 0 => None
    case _ =>
      Some(Document(
        "hasDisability" -> "Yes",
        "needsSupportForOnlineAssessment" -> true,
        "needsSupportAtVenue" -> true,
        "needsSupportAtVenueDescription" -> "A nice cup of coffee",
        "needsSupportForOnlineAssessmentDescription" -> "Extra time",
        "guaranteedInterview" -> true,
        "hasDisabilityDescription" -> "Wooden leg"
      ))
  }

  private def createPersonalDetails(id: Int) = id match {
    case x if x % 5 == 0 => None
    case _ =>
      import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._
      Some(Document(
        "firstName" -> chooseOne[String](firstNames),
        "lastName" -> chooseOne[String](lastNames),
        "preferredName" -> chooseOne[String](preferredName),
        "dateOfBirth" -> Codecs.toBson(chooseOne[LocalDate](dateOfBirth))
      ))
  }

  private def isSubmitted(id: Int)(ps: Option[Document], as: Option[Document]) = (ps, as) match {
    case (Some(_), Some(_)) if id % 2 == 0 => Some(true)
    case _ => None
  }

  private def isWithdrawn(id: Int)(ps: Option[Document], as: Option[Document]) = (ps, as) match {
    case (Some(_), Some(_)) if id % 3 == 0 => Some(true)
    case _ => None
  }
}
