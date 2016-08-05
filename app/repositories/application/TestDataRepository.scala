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

import model.Commands._
import model.Address
import model.PersistedObjects
import model.PersistedObjects.ContactDetails
import org.joda.time.{ DateTime, LocalDate }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
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
  def createApplications(num: Int, onlyAwaitingAllocation: Boolean = false): Future[Unit]
}

trait TestDataContactDetailsRepository {
  def createContactDetails(num: Int): Future[Unit]
}

class TestDataContactDetailsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[ContactDetails, BSONObjectID]("contact-details", mongo,
    PersistedObjects.Implicits.contactDetailsFormats, ReactiveMongoFormats.objectIdFormats) with TestDataContactDetailsRepository {
  import Utils.chooseOne

  val postcodes = Seq("AB1 3CD", "BC1 2DE", "CD28 7EF", "DE23 8FG")

  override def createContactDetails(num: Int): Future[Unit] = Future.successful {
    for (i <- 0 until num) {
      createSingleContactDetail(i)
    }
  }

  private def createSingleContactDetail(id: Int): Future[Unit] = {

    val contactDetails = ContactDetails(Address("1st High Street"), chooseOne(postcodes), s"test_$id@test.com", Some("123456789"))
    val contactDetailsBson = BSONDocument("$set" -> BSONDocument(
      "contact-details" -> contactDetails
    ))

    collection.update(BSONDocument("userId" -> id.toString), contactDetailsBson, upsert = true) map (_ => ())
  }
}

class TestDataMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[ContactDetails, BSONObjectID]("application", mongo,
    PersistedObjects.Implicits.contactDetailsFormats, ReactiveMongoFormats.objectIdFormats) with TestDataRepository {
  import Utils.chooseOne

  val applicationStatuses = Seq("CREATED", "IN_PROGRESS", "SUBMITTED", "WITHDRAWN")
  val firstNames = Seq("John", "Chris", "James", "Paul")
  val lastNames = Seq("Clerk", "Smith", "Gulliver", "Swift")
  val preferredName = Seq("Superman", "Batman", "Spiderman", "Wolverine", "Hulk")
  val frameworks = Seq("Digital and technology", "Finance", "Project delivery", "Commercial", "Business")

  val locationsAndRegions = Seq("Southend" -> "East", "Nottingham" -> "East-midlands", "London" -> "London", "Darlington" -> "North-east",
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

  override def createApplications(num: Int, onlyAwaitingAllocation: Boolean = false): Future[Unit] =
    Future.sequence(
      (0 until num).map { i => createSingleApplication(i, onlyAwaitingAllocation) }
    ).map(_ => ())

  private def createSingleApplication(id: Int, onlyAwaitingAllocation: Boolean = false): Future[Unit] = {

    val document = buildSingleApplication(id, onlyAwaitingAllocation)

    collection.update(BSONDocument("userId" -> id.toString), document, upsert = true) map (_ => ())
  }

  private def createProgress(
                              personalDetails: Option[BSONDocument],
                              frameworks: Option[BSONDocument],
                              assistance: Option[BSONDocument],
                              isSubmitted: Option[Boolean],
                              isWithdrawn: Option[Boolean]
                            ) = {
    var progress = BSONDocument()

    progress = personalDetails.map(_ => progress ++ ("personal-details" -> true)).getOrElse(progress)
    progress = frameworks.map(_ => progress ++ ("frameworks-location" -> true)).getOrElse(progress)
    progress = assistance.map(_ => progress ++ ("assistance-details" -> true)).getOrElse(progress)
    progress = isSubmitted.map(_ => progress ++ ("submitted" -> true)).getOrElse(progress)
    progress = isWithdrawn.map(_ => progress ++ ("withdrawn" -> true)).getOrElse(progress)

    progress
  }

  private def buildSingleApplication(id: Int, onlyAwaitingAllocation: Boolean = false) = {
    val personalDetails = createPersonalDetails(id, onlyAwaitingAllocation)
    val frameworks = createLocations(id, onlyAwaitingAllocation)
    val assistance = createAssistance(id, onlyAwaitingAllocation)
    val onlineTests = createOnlineTests(id, onlyAwaitingAllocation)
    val submitted = isSubmitted(id)(personalDetails, frameworks, assistance)
    val withdrawn = isWithdrawn(id)(personalDetails, frameworks, assistance)

    val progress = createProgress(personalDetails, frameworks, assistance, submitted, withdrawn)

    val applicationStatus = if (onlyAwaitingAllocation) "AWAITING_ALLOCATION" else chooseOne(applicationStatuses)
    var document = BSONDocument(
      "applicationId" -> id.toString,
      "userId" -> id.toString,
      "frameworkId" -> "FastTrack-2015",
      "applicationStatus" -> applicationStatus
    )
    document = buildDocument(document)(personalDetails.map(d => "personal-details" -> d))
    document = buildDocument(document)(frameworks.map(d => "framework-preferences" -> d))
    document = buildDocument(document)(assistance.map(d => "assistance-details" -> d))
    document = buildDocument(document)(onlineTests.map(d => "online-tests" -> d))
    document = document ++ ("progress-status" -> progress)

    document

  }

  private def buildDocument(document: BSONDocument)(f: Option[(String, BSONDocument)]) = {
    f.map(d => document ++ d).getOrElse(document)
  }

  private def createAssistance(id: Int, buildAlways: Boolean = false) = id match {
    case x if x % 7 == 0 && !buildAlways => None
    case _ =>
      Some(BSONDocument(
        "needsAssistance" -> "No",
        "needsAdjustment" -> "Yes",
        "guaranteedInterview" -> "Yes",
        "typeOfAdjustments" -> BSONArray("Time extension", "Braille test paper", "Stand up and move around", "Other"),
        "otherAdjustments" -> "Other adjustments test text"
      ))
  }

  private def createPersonalDetails(id: Int, buildAlways: Boolean = false) = id match {
    case x if x % 5 == 0 && !buildAlways => None
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

  private def createLocations(id: Int, buildAlways: Boolean = false) = id match {
    case x if x % 11 == 0 && !buildAlways => None
    case _ =>
      val firstLocationRegion = chooseOne(locationsAndRegions)
      val secondLocationRegion = chooseOne(locationsAndRegions)
      Some(BSONDocument(
        "firstLocation" -> BSONDocument(
          "region" -> firstLocationRegion._2, "location" -> firstLocationRegion._1,
          "firstFramework" -> chooseOne(frameworks), "secondFramework" -> chooseOne(frameworks)
        ), // Could be identical.
        "secondLocation" -> BSONDocument(
          "region" -> secondLocationRegion._2, "location" -> secondLocationRegion._1,
          "firstFramework" -> chooseOne(frameworks), "secondFramework" -> chooseOne(frameworks)
        ), // Could be identical.
        "secondLocationIntended" -> true,
        "alternatives" -> BSONDocument("location" -> true, "framework" -> true)
      ))
  }

  private def createOnlineTests(id: Int, buildAlways: Boolean = false) = id match {
    case x if x % 12 == 0 && !buildAlways => None
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

  private def isSubmitted(id: Int)(ps: Option[BSONDocument], fl: Option[BSONDocument], as: Option[BSONDocument]) = (ps, fl, as) match {
    case (Some(_), Some(_), Some(_)) if id % 2 == 0 => Some(true)
    case _ => None
  }

  private def isWithdrawn(id: Int)(ps: Option[BSONDocument], fl: Option[BSONDocument], as: Option[BSONDocument]) = (ps, fl, as) match {
    case (Some(_), Some(_), Some(_)) if id % 3 == 0 => Some(true)
    case _ => None
  }

}
