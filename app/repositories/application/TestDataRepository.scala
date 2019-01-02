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

package repositories.application

import connectors.ExchangeObjects
import model.{ ApplicationStatus, _ }
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.ProgressStatuses.ProgressStatus
import model.persisted.ContactDetails
import org.joda.time.{ DateTime, LocalDate }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

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

class TestDataContactDetailsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[ContactDetails, BSONObjectID](CollectionNames.CONTACT_DETAILS, mongo,
    ContactDetails.contactDetailsFormat, ReactiveMongoFormats.objectIdFormats) with
    TestDataContactDetailsRepository with ReactiveRepositoryHelpers {

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
    val contactDetailsBson = BSONDocument("$set" -> BSONDocument(
      "contact-details" -> contactDetails
    ))

    val validator = singleUpsertValidator(id.toString, actionDesc = "creating contact test data")
    collection.update(BSONDocument("userId" -> id.toString), contactDetailsBson, upsert = true) map validator
  }
}

class TestDataMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[ContactDetails, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ContactDetails.contactDetailsFormat, ReactiveMongoFormats.objectIdFormats) with TestDataRepository
    with ReactiveRepositoryHelpers {

  import Utils.chooseOne

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
    LocalDate.parse("1982-12-12"), LocalDate.parse("1983-12-12"), LocalDate.parse("1984-12-12"), LocalDate.parse("1985-12-12"),
    LocalDate.parse("1987-12-12"), LocalDate.parse("1987-12-12"))

  override def createApplications(num: Int): Future[Unit] =
    Future.sequence(
      (0 until num).map { i => createSingleApplication(i) }
    ).map(_ => ())

  // scalastyle:off parameter.number
  def createApplicationWithAllFields(userId: String, appId: String, frameworkId: String,
    appStatus: ApplicationStatus = ApplicationStatus.IN_PROGRESS, hasDisability: String = "Yes",
    needsSupportForOnlineAssessment: Boolean = false,
    needsSupportAtVenue: Boolean = false, guaranteedInterview: Boolean = false, lastName: Option[String] = None,
    firstName: Option[String] = None, preferredName: Option[String] = None,
    additionalProgressStatuses: List[(ProgressStatus, Boolean)] = Nil,
    additionalDoc: BSONDocument = BSONDocument.empty,
    applicationRoute: Option[ApplicationRoute] = None,
    partnerProgrammes: List[String] = Nil
  ) = {
    import repositories.BSONLocalDateHandler
    val document = BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "applicationRoute" -> applicationRoute,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "scheme-preferences" -> BSONDocument(
        "schemes" -> BSONArray(SchemeId("DiplomaticService"), SchemeId("GovernmentOperationalResearchService"))
      ),
      "personal-details" -> BSONDocument(
        "firstName" -> firstName.getOrElse(s"${testCandidate("firstName")}"),
        "lastName" -> lastName.getOrElse(s"${testCandidate("lastName")}"),
        "preferredName" -> preferredName.getOrElse(s"${testCandidate("preferredName")}"),
        "dateOfBirth" -> s"${testCandidate("dateOfBirth")}"
      ),
      "civil-service-experience-details" -> BSONDocument(
        "applicable" -> true,
        "fastPassReceived" -> true,
        "certificateNumber" -> "1234567",
        "civilServiceExperienceType" -> CivilServiceExperienceType.CivilServant,
        "internshipTypes" -> BSONArray(InternshipType.SDIPCurrentYear, InternshipType.EDIP)
      ),
      "assistance-details" -> BSONDocument(
        "hasDisability" -> "Yes",
        "needsSupportForOnlineAssessment" -> needsSupportForOnlineAssessment,
        "needsSupportAtVenue" -> needsSupportAtVenue,
        "guaranteedInterview" -> guaranteedInterview
      ),
      "partner-graduate-programmes" -> deferral(partnerProgrammes),
      "issue" -> "this candidate has changed the email",
      "progress-status" -> progressStatus(additionalProgressStatuses),
      "progress-status-dates" -> BSONDocument(
        "submitted" -> LocalDate.now()
      )
    ) ++ additionalDoc //.futureValue
    collection.insert(document)
  }
  // scalastyle:on parameter.number

  def deferral(args: List[String] = Nil): BSONDocument = args match {
    case Nil => BSONDocument()

    case _ :: _ => BSONDocument(
      "interested" -> true,
      "partnerGraduateProgrammes" -> args
    )
  }

  def progressStatus(args: List[(ProgressStatus, Boolean)] = List.empty): BSONDocument = {
    val baseDoc = BSONDocument(
      "personal-details" -> true,
      "in_progress" -> true,
      "scheme-preferences" -> true,
      "partner-graduate-programmes" -> true,
      "assistance-details" -> true,
      "questionnaire" -> BSONDocument(
        "start_questionnaire" -> true,
        "diversity_questionnaire" -> true,
        "education_questionnaire" -> true,
        "occupation_questionnaire" -> true
      ),
      "preview" -> true,
      "submitted" -> true
    )

    args.foldLeft(baseDoc)((acc, v) => acc ++ (v._1.toString -> v._2))
  }

  val testCandidate = Map(
    "firstName" -> "George",
    "lastName" -> "Jetson",
    "preferredName" -> "Georgy",
    "dateOfBirth" -> "1986-05-01"
  )

  def createMinimumApplication(userId: String, appId: String, frameworkId: String) = {
    collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> userId,
      "frameworkId" -> frameworkId
    )) //.futureValue
  }

  private def createSingleApplication(id: Int): Future[Unit] = {
    val document = buildSingleApplication(id)

    val validator = singleUpsertValidator(id.toString, actionDesc = "creating application test data")

    collection.update(BSONDocument("userId" -> id.toString), document, upsert = true) map validator
  }

  private def createProgress(
                              personalDetails: Option[BSONDocument],
                              assistance: Option[BSONDocument],
                              isSubmitted: Option[Boolean],
                              isWithdrawn: Option[Boolean]
                            ) = {
    var progress = BSONDocument.empty

    progress = personalDetails.map(_ => progress ++ ("personal-details" -> true)).getOrElse(progress)
    progress = assistance.map(_ => progress ++ ("assistance-details" -> true)).getOrElse(progress)
    progress = isSubmitted.map(_ => progress ++ ("submitted" -> true)).getOrElse(progress)
    progress = isWithdrawn.map(_ => progress ++ ("withdrawn" -> true)).getOrElse(progress)

    progress
  }

  private def buildSingleApplication(id: Int): BSONDocument = {
    val personalDetails = createPersonalDetails(id)
    val assistance = createAssistance(id)
    val onlineTests = createOnlineTests(id)
    val submitted = isSubmitted(id)(personalDetails, assistance)
    val withdrawn = isWithdrawn(id)(personalDetails, assistance)

    val progress = createProgress(personalDetails, assistance, submitted, withdrawn)

    val applicationStatus = chooseOne(applicationStatuses)

    var document = BSONDocument(
      "applicationId" -> id.toString,
      "userId" -> id.toString,
      "frameworkId" -> ExchangeObjects.frameworkId,
      "applicationStatus" -> applicationStatus
    )
    document = buildDocument(document)(personalDetails.map(d => "personal-details" -> d))
    document = buildDocument(document)(assistance.map(d => "assistance-details" -> d))
    document = buildDocument(document)(onlineTests.map(d => "online-tests" -> d))
    document = document ++ ("progress-status" -> progress)

    document
  }

  private def buildDocument(document: BSONDocument)(f: Option[(String, BSONDocument)]) = {
    f.map(d => document ++ d).getOrElse(document)
  }

  private def createAssistance(id: Int) = id match {
    case x if x % 7 == 0 => None
    case _ =>
      Some(BSONDocument(
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
      Some(BSONDocument(
        "firstName" -> chooseOne(firstNames),
        "lastName" -> chooseOne(lastNames),
        "preferredName" -> chooseOne(preferredName),
        "dateOfBirth" -> chooseOne(dateOfBirth),
        "aLevel" -> true,
        "stemLevel" -> true
      ))
  }

  private def createOnlineTests(id: Int) = id match {
    case x if x % 12 == 0 => None
    case _ =>
      Some(BSONDocument(
        "cubiksUserId" -> 117344,
        "token" -> "32cf213b-697e-414b-a954-7d92f3e3e682",
        "onlineTestUrl" -> "https://uat.cubiksonline.com/CubiksOnline/Standalone/PEnterFromExt.aspx?key=fc831fb6-1cb7-4c6d-9e9b".concat(
          "-1e508db76711&hash=A07B3B39025E6F34639E5CEA70A6F668402E4673"
        ),
        "invitationDate" -> DateTime.now().minusDays(5),
        "expirationDate" -> DateTime.now().plusDays(2),
        "participantScheduleId" -> 149245,
        "completionDate" -> DateTime.now()
      ))
  }

  private def isSubmitted(id: Int)(ps: Option[BSONDocument], as: Option[BSONDocument]) = (ps, as) match {
    case (Some(_), Some(_)) if id % 2 == 0 => Some(true)
    case _ => None
  }

  private def isWithdrawn(id: Int)(ps: Option[BSONDocument], as: Option[BSONDocument]) = (ps, as) match {
    case (Some(_), Some(_)) if id % 3 == 0 => Some(true)
    case _ => None
  }
}
