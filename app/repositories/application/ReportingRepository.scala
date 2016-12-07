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

import model.ApplicationStatus._
import model.Commands._
import model.command._
import model.persisted._
import model.report.{ AdjustmentReportItem, CandidateDeferralReportItem, CandidateProgressReportItem, ProgressStatusesReportLabels }
import model.{ ApplicationStatus, _ }
import org.joda.time.LocalDate
import play.api.libs.json.Format
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONDocumentReader, _ }
import repositories._
import services.TimeZoneService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait ReportingRepository {
  def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]]

  def candidateProgressReport(frameworkId: String): Future[List[CandidateProgressReportItem]]

  def diversityReport(frameworkId: String): Future[List[ApplicationForDiversityReport]]

  def onlineTestPassMarkReport(frameworkId: String): Future[List[ApplicationForOnlineTestPassMarkReport]]

  def candidateProgressReportNotWithdrawn(frameworkId: String): Future[List[CandidateProgressReportItem]]

  def overallReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]]

  def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]]

  def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]]

  def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]]

  def candidateDeferralReport(frameworkId: String): Future[List[CandidateDeferralReportItem]]

}

class ReportingMongoRepository(timeZoneService: TimeZoneService)(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID]("application", mongo,
    Commands.Implicits.createApplicationRequestFormats, ReactiveMongoFormats.objectIdFormats) with ReportingRepository with RandomSelection with
    CommonBSONDocuments with ReportingRepoBSONReader with ReactiveRepositoryHelpers {

  override def candidateProgressReportNotWithdrawn(frameworkId: String): Future[List[CandidateProgressReportItem]] =
    candidateProgressReport(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "WITHDRAWN"))
    )))

  override def overallReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]] =
    overallReportWithPersonalDetails(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "WITHDRAWN"))
    )))

  override def candidateProgressReport(frameworkId: String): Future[List[CandidateProgressReportItem]] =
    candidateProgressReport(BSONDocument("frameworkId" -> frameworkId))

  private def candidateProgressReport(query: BSONDocument): Future[List[CandidateProgressReportItem]] = {
    val projection = BSONDocument(
      "userId" -> "1",
      "applicationRoute" -> "1",
      "personal-details.edipCompleted" -> "1",
      "scheme-preferences.schemes" -> "1",
      "assistance-details" -> "1",
      "civil-service-experience-details" -> "1",
      "applicationId" -> "1",
      "progress-status" -> "2"
    )
    reportQueryWithProjectionsBSON[CandidateProgressReportItem](query, projection)
  }

  override def candidateDeferralReport(frameworkId: String): Future[List[CandidateDeferralReportItem]] = {
    val query = BSONDocument(
      "$and" -> BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument("partner-graduate-programmes.interested" -> true)
    ))

    val projection = BSONDocument("userId" -> true, "personal-details" -> true, "partner-graduate-programmes" -> true)

    reportQueryWithProjections[BSONDocument](query, projection).map { docs =>
      docs.flatMap { doc =>
        for {
          userId <- doc.getAs[String]("userId")
          personalDetails <- doc.getAs[model.persisted.PersonalDetails]("personal-details")
          programmes <- doc.getAs[BSONDocument]("partner-graduate-programmes").map { p =>
            p.getAs[List[String]]("partnerGraduateProgrammes").getOrElse(Nil)
          }
        } yield {
          CandidateDeferralReportItem(
            userId,
            personalDetails.firstName,
            personalDetails.lastName,
            personalDetails.preferredName,
            programmes
          )
        }
      }
    }
  }

  override def diversityReport(frameworkId: String): Future[List[ApplicationForDiversityReport]] = {
    val query = BSONDocument("frameworkId" -> frameworkId)
    val projection = BSONDocument(
      "userId" -> "1",
      "applicationRoute" -> "1",
      "scheme-preferences.schemes" -> "1",
      "assistance-details" -> "1",
      "civil-service-experience-details" -> "1",
      "applicationId" -> "1",
      "progress-status" -> "2"
    )
    reportQueryWithProjectionsBSON[ApplicationForDiversityReport](query, projection)
  }

  override def onlineTestPassMarkReport(frameworkId: String): Future[List[ApplicationForOnlineTestPassMarkReport]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true)
    ))

    val projection = BSONDocument(
      "userId" -> "1",
      "applicationId" -> "1",
      "applicationRoute" -> "1",
      "scheme-preferences.schemes" -> "1",
      "assistance-details" -> "1",
      "testGroups.PHASE1" -> "1",
      "testGroups.PHASE2" -> "1",
      "testGroups.PHASE3.tests.callbacks.reviewed" -> 1,
      "progress-status" -> "1"
    )

    reportQueryWithProjectionsBSON[ApplicationForOnlineTestPassMarkReport](query, projection)
  }


  // scalstyle:on method.length
  private def overallReportWithPersonalDetails(query: BSONDocument): Future[List[ReportWithPersonalDetails]] = {
    val projection = BSONDocument(
      "userId" -> "1",
      "framework-preferences.alternatives.location" -> "1",
      "framework-preferences.alternatives.framework" -> "1",
      "framework-preferences.firstLocation.location" -> "1",
      "framework-preferences.secondLocation.location" -> "1",
      "framework-preferences.firstLocation.firstFramework" -> "1",
      "framework-preferences.secondLocation.firstFramework" -> "1",
      "framework-preferences.firstLocation.secondFramework" -> "1",
      "framework-preferences.secondLocation.secondFramework" -> "1",
      "personal-details.aLevel" -> "1",
      "personal-details.dateOfBirth" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "personal-details.stemLevel" -> "1",
      "online-tests.cubiksUserId" -> "1",
      "assistance-details.needsAssistance" -> "1",
      "assistance-details.needsAdjustment" -> "1",
      "assistance-details.guaranteedInterview" -> "1",
      "applicationId" -> "1",
      "progress-status" -> "2"
    )

    reportQueryWithProjectionsBSON[ReportWithPersonalDetails](query, projection)
  }

  //scalastyle:off method.length
  override def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]] = {
    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.CREATED)),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.IN_PROGRESS)),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.WITHDRAWN)),
        BSONDocument("$and" ->
          BSONArray(
            BSONDocument("$or" ->
              BSONArray(
                BSONDocument("applicationRoute" -> "Faststream"),
                BSONDocument("applicationRoute" -> BSONDocument("$exists" -> false))
              )
            ),
            BSONDocument("$or" ->
              BSONArray(
                BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> true),
                BSONDocument("assistance-details.needsSupportAtVenue" -> true),
                BSONDocument("assistance-details.guaranteedInterview" -> true),
                BSONDocument("assistance-details.adjustmentsConfirmed" -> true)
              )
            )
          )
        )
      )
    )

    val projection = BSONDocument(
      "userId" -> "1",
      "applicationStatus" -> "1",
      "applicationId" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "assistance-details.hasDisability" -> "1",
      "assistance-details.needsSupportAtVenueDescription" -> "1",
      "assistance-details.needsSupportForOnlineAssessmentDescription" -> "1",
      "assistance-details.guaranteedInterview" -> "1",
      "assistance-details.hasDisabilityDescription" -> "1",
      "assistance-details.typeOfAdjustments" -> "1",
      "assistance-details.etray" -> "1",
      "assistance-details.video" -> "1",
      "assistance-details.adjustmentsConfirmed" -> "1",
      "assistance-details.adjustmentsComment" -> "1"
    )

    reportQueryWithProjections[BSONDocument](query, projection).map { list =>
      list.map { document =>

        val personalDetails = document.getAs[BSONDocument]("personal-details")
        val userId = document.getAs[String]("userId").getOrElse("")
        val applicationId = document.getAs[String]("applicationId")
        val applicationStatus = document.getAs[String]("applicationStatus")
        val firstName = extract("firstName")(personalDetails)
        val lastName = extract("lastName")(personalDetails)
        val preferredName = extract("preferredName")(personalDetails)

        val assistance = document.getAs[BSONDocument]("assistance-details")
        val gis = assistance.flatMap(_.getAs[Boolean]("guaranteedInterview")).flatMap(b => Some(booleanTranslator(b)))
        val needsSupportForOnlineAssessmentDescription = extract("needsSupportForOnlineAssessmentDescription")(assistance)
        val needsSupportAtVenueDescription = extract("needsSupportAtVenueDescription")(assistance)
        val hasDisability = extract("hasDisability")(assistance)
        val hasDisabilityDescription = extract("hasDisabilityDescription")(assistance)
        val adjustmentsConfirmed = assistance.flatMap(_.getAs[Boolean]("adjustmentsConfirmed"))
        val adjustmentsComment = extract("adjustmentsComment")(assistance)
        val etray = assistance.flatMap(_.getAs[AdjustmentDetail]("etray"))
        val video = assistance.flatMap(_.getAs[AdjustmentDetail]("video"))
        val typeOfAdjustments = assistance.flatMap(_.getAs[List[String]]("typeOfAdjustments"))

        val adjustments = adjustmentsConfirmed.flatMap { ac =>
          if (ac) Some(Adjustments(typeOfAdjustments, adjustmentsConfirmed, etray, video)) else None
        }

        AdjustmentReportItem(
          userId,
          applicationId,
          firstName,
          lastName,
          preferredName,
          None,
          None,
          gis,
          applicationStatus,
          needsSupportForOnlineAssessmentDescription,
          needsSupportAtVenueDescription,
          hasDisability,
          hasDisabilityDescription,
          adjustments,
          adjustmentsComment)
      }
    }
  }

  //scalastyle:on method.length

  override def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]] = {
    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument("applicationStatus" -> "AWAITING_ALLOCATION")
      ))

    val projection = BSONDocument(
      "userId" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "personal-details.dateOfBirth" -> "1",
      "framework-preferences.firstLocation.location" -> "1",
      "assistance-details.typeOfAdjustments" -> "1",
      "assistance-details.otherAdjustments" -> "1"
    )

    reportQueryWithProjections[BSONDocument](query, projection).map { list =>
      list.map { document =>

        val userId = document.getAs[String]("userId").get
        val personalDetails = document.getAs[BSONDocument]("personal-details").get
        val firstName = personalDetails.getAs[String]("firstName").get
        val lastName = personalDetails.getAs[String]("lastName").get
        val preferredName = personalDetails.getAs[String]("preferredName").get
        val dateOfBirth = personalDetails.getAs[LocalDate]("dateOfBirth").get
        val frameworkPreferences = document.getAs[BSONDocument]("framework-preferences").get
        val firstLocationDoc = frameworkPreferences.getAs[BSONDocument]("firstLocation").get
        val firstLocation = firstLocationDoc.getAs[String]("location").get

        val assistance = document.getAs[BSONDocument]("assistance-details")
        val typesOfAdjustments = assistance.flatMap(_.getAs[List[String]]("typeOfAdjustments"))

        val otherAdjustments = extract("otherAdjustments")(assistance)
        val adjustments = typesOfAdjustments.getOrElse(Nil) ::: otherAdjustments.toList
        val finalTOA = if (adjustments.isEmpty) None else Some(adjustments.mkString("|"))

        CandidateAwaitingAllocation(userId, firstName, lastName, preferredName, firstLocation, finalTOA, dateOfBirth)
      }
    }
  }

  override def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]] = {
    val query = BSONDocument("frameworkId" -> frameworkId)

    val projection = BSONDocument(
      "applicationId" -> "1",
      "personal-details.preferredName" -> "1",
      "userId" -> "1",
      "framework-preferences" -> "1",
      "progress-status" -> "2"
    )

    val seed = Future.successful(List.empty[(String, Boolean, PreferencesWithContactDetails)])
    reportQueryWithProjections[BSONDocument](query, projection).flatMap { lst =>
      lst.foldLeft(seed) { (applicationsFuture, document) =>
        applicationsFuture.map { applications =>
          val timeCreated = isoTimeToPrettyDateTime(getDocumentId(document).time)
          val applicationId = document.getAs[String]("applicationId").get
          val personalDetails = document.getAs[BSONDocument]("personal-details")
          val preferredName = extract("preferredName")(personalDetails)
          val userId = document.getAs[String]("userId").get
          val frameworkPreferences = document.getAs[Preferences]("framework-preferences")

          val location1 = frameworkPreferences.map(_.firstLocation.location)
          val location1Scheme1 = frameworkPreferences.map(_.firstLocation.firstFramework)
          val location1Scheme2 = frameworkPreferences.flatMap(_.firstLocation.secondFramework)

          val location2 = frameworkPreferences.flatMap(_.secondLocation.map(_.location))
          val location2Scheme1 = frameworkPreferences.flatMap(_.secondLocation.map(_.firstFramework))
          val location2Scheme2 = frameworkPreferences.flatMap(_.secondLocation.flatMap(_.secondFramework))

          val p = toProgressResponse(applicationId).read(document)

          val preferences = PreferencesWithContactDetails(None, None, preferredName, None, None,
            location1, location1Scheme1, location1Scheme2,
            location2, location2Scheme1, location2Scheme2,
            Some(ProgressStatusesReportLabels.progressStatusNameInReports(p)), Some(timeCreated))

          (userId, isNonSubmittedStatus(p), preferences) +: applications
        }
      }
    }
  }

  override def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]] = {
    val query = BSONDocument("frameworkId" -> frameworkId)
    val projection = BSONDocument(
      "applicationId" -> "1",
      "userId" -> "1"
    )

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map {
      _.map { doc =>
        val userId = doc.getAs[String]("userId").getOrElse("")
        val applicationId = doc.getAs[String]("applicationId").getOrElse("")
        PersonalDetailsAdded(applicationId, userId)
      }
    }
  }

  private[application] def isNonSubmittedStatus(progress: ProgressResponse): Boolean = {
    val isNotSubmitted = !progress.submitted
    val isNotWithdrawn = !progress.withdrawn
    isNotWithdrawn && isNotSubmitted
  }

  private def getDocumentId(document: BSONDocument): BSONObjectID =
    document.get("_id").get match {
      case id: BSONObjectID => id
      case id: BSONString => BSONObjectID(id.value)
    }

  private def isoTimeToPrettyDateTime(utcMillis: Long): String =
    timeZoneService.localize(utcMillis).toString("yyyy-MM-dd HH:mm:ss")

  private def reportQueryWithProjections[A](
                                             query: BSONDocument,
                                             prj: BSONDocument,
                                             upTo: Int = Int.MaxValue,
                                             stopOnError: Boolean = true
                                           )(implicit reader: Format[A]): Future[List[A]] =
    collection.find(query).projection(prj).cursor[A](ReadPreference.nearest).collect[List](upTo, stopOnError)

  private def extract(key: String)(root: Option[BSONDocument]) = root.flatMap(_.getAs[String](key))

  private def reportQueryWithProjectionsBSON[A](
                                                 query: BSONDocument,
                                                 prj: BSONDocument,
                                                 upTo: Int = Int.MaxValue,
                                                 stopOnError: Boolean = true
                                               )(implicit reader: BSONDocumentReader[A]): Future[List[A]] =
    bsonCollection.find(query).projection(prj)
      .cursor[A](ReadPreference.nearest)
      .collect[List](Int.MaxValue, true)
}
