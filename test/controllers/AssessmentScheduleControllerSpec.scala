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

package controllers

import java.io.UnsupportedEncodingException
import java.net.URLEncoder

import config._
import connectors.EmailClient
import model.Address
import model.AssessmentScheduleCommands.Implicits._
import model.AssessmentScheduleCommands.{ApplicationForAssessmentAllocation, ApplicationForAssessmentAllocationResult}
import model.Commands._
import model.command._
import model.Exceptions.NotFoundException
import model.PersistedObjects.{ContactDetails, PersonalDetails}
import org.joda.time.{DateTime, LocalDate}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsString, JsValue, Json}
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.AssessmentCentreLocation._
import repositories.application.{GeneralApplicationRepository, OnlineTestRepository, PersonalDetailsRepository}
import repositories.{AssessmentCentreLocation, _}
import services.applicationassessment.ApplicationAssessmentService
import testkit.MockitoSugar

import scala.concurrent.Future

class AssessmentScheduleControllerSpec extends PlaySpec with Results
  with MockitoSugar with ScalaFutures {
  val mockAssessmentCentreRepository = mock[AssessmentCentreRepository]

  "Get Assessment Schedule" should {
    "return a valid schedule" in new TestFixture {
      val result = assessmentScheduleControllerWithSampleSchedule.getAssessmentSchedule()(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      val expectedJson =
        s"""
           | {
           |    "locations": [
           |        {
           |            "name": "Narnia",
           |            "venues": [
           |                {
           |                    "name": "Test Venue 1",
           |                    "usedCapacityDates": [
           |                        {
           |                            "amUsedCapacity": {
           |                                "hasUnconfirmedAttendees": true,
           |                                "usedCapacity": 2
           |                            },
           |                            "date": "2015-04-25",
           |                            "pmUsedCapacity": {
           |                                "hasUnconfirmedAttendees": true,
           |                                "usedCapacity": 1
           |                            }
           |                        }
           |                    ]
           |                },
           |                {
           |                    "name": "Test Venue 2",
           |                    "usedCapacityDates": [
           |                        {
           |                            "amUsedCapacity": {
           |                                "hasUnconfirmedAttendees": false,
           |                                "usedCapacity": 0
           |                            },
           |                            "date": "2015-04-27",
           |                            "pmUsedCapacity": {
           |                                "hasUnconfirmedAttendees": false,
           |                                "usedCapacity": 0
           |                            }
           |                        }
           |                    ]
           |                }
           |            ]
           |        }
           |    ]
           |}
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }
  }

  "get application for assessment allocation" should {
    "return a List of Application for Assessment allocation if there are relevant applications in the repo" in new TestFixture {
      val response = assessmentScheduleControllerWithSampleSchedule.getApplicationForAssessmentAllocation("London", 0, 5)(FakeRequest())
      status(response) must be(200)

      val json = contentAsJson(response).as[JsValue]

      val result = contentAsJson(response).as[ApplicationForAssessmentAllocationResult]
      result.result must have size 2
    }
    "return an empty List of Application for Assessment allocation if there are no relevant applications in the repo" in new TestFixture {
      val response = assessmentScheduleControllerWithSampleSchedule.getApplicationForAssessmentAllocation("Manchester", 0, 5)(FakeRequest())
      status(response) must be(200)

      val result = contentAsJson(response).as[ApplicationForAssessmentAllocationResult]
      result.result must have size 0
    }
  }

  "get a day's schedule for a venue" should {
    "show the candidate schedule for a venue when there is only one candidate in one session" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-04-25"

      val result = assessmentScheduleControllerWithOneVenueDateCandidate.getVenueDayCandidateSchedule(venue, date)(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      // FYI: Nulls are expected "empty slot" padding.
      val expectedJson =
        s"""
           |{
           |    "amSession": [
           |            {
           |                "applicationId": "appid-1",
           |                "confirmed": true,
           |                "firstName": "Bob",
           |                "lastName": "Marley",
           |                "userId": "userid-1"
           |            },
           |            null
           |    ],
           |    "pmSession": [
           |            null,
           |            null,
           |            null
           |    ]
           |
           |}
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "show the candidate schedule for a venue when there are no candidates assigned to any sessions" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-04-26"

      val result = assessmentScheduleControllerWithNoVenueDateCandidates.getVenueDayCandidateSchedule(venue, date)(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      // FYI: Nulls are expected "empty slot" padding.
      val expectedJson =
        s"""
           |{
           |    "amSession": [
           |            null,
           |            null,
           |            null
           |    ],
           |    "pmSession": [
           |            null,
           |            null,
           |            null,
           |            null,
           |            null,
           |            null
           |     ]
           |
           |}
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "throw an illegal argument exception if a bad date string is passed" in new TestFixture {
      intercept[IllegalArgumentException] {
        val venue = "Copa (Cabana)"
        val date = "INVALID_DATE"

        val result = assessmentScheduleControllerWithSampleSchedule.getVenueDayCandidateSchedule(venue, date)(FakeRequest())

        status(result) must be(OK)
      }
    }

    "throw an unsupported encoding exception if a venue string we cannot understand is passed" in new TestFixture {
      intercept[UnsupportedEncodingException] {
        val venue = URLEncoder.encode("a����", "latin-1")
        val date = "2015-06-01"

        val result = assessmentScheduleControllerWithSampleSchedule.getVenueDayCandidateSchedule(venue, date)(FakeRequest())

        status(result) must be(OK)
      }
    }
  }

  "get a day's schedule for a venue with details" should {
    "show the candidate schedule for a venue when there is only one candidate in one session" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-04-25"

      val result = assessmentScheduleControllerWithOneVenueDateCandidate.getVenueDayCandidateScheduleWithDetails(venue, date)(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      val expectedJson =
        s"""
           |[{
           |    "firstName": "Bob",
           |    "lastName": "Marley",
           |    "preferredName": "preferredName1",
           |    "email": "email1@mailinator.com",
           |    "phone": "11111111",
           |    "venue": "Copa (Cabana)",
           |    "session": "AM",
           |    "date": "2015-04-25",
           |    "status": "Confirmed"
           |}]
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "show the candidate schedule for a venue when there are several candidates in AM and PM sessions" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-04-25"

      val result = assessmentScheduleControllerWithOneVenueOneDateOneCandidateInPMOneInAMSession.getVenueDayCandidateScheduleWithDetails(
        venue,
        date
      )(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      val expectedJson =
        s"""
           |[{
           |    "firstName": "Bob",
           |    "lastName": "Marley",
           |    "preferredName": "preferredName1",
           |    "email": "email1@mailinator.com",
           |    "phone": "11111111",
           |    "venue": "Copa (Cabana)",
           |    "session": "AM",
           |    "date": "2015-04-25",
           |    "status": "Confirmed"
           |},
           |{
           |    "firstName": "Michael",
           |    "lastName": "Jackson",
           |    "preferredName": "preferredName2",
           |    "email": "email2@mailinator.com",
           |    "phone": "22222222",
           |    "venue": "Copa (Cabana)",
           |    "session": "PM",
           |    "date": "2015-04-25",
           |    "status": "Confirmed"
           |}
           |]
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "show the candidate schedule for a venue when there are no candidates" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-07-30"

      val result = assessmentScheduleControllerWithNoVenueDateCandidates.getVenueDayCandidateScheduleWithDetails(venue, date)(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      // FYI: Nulls are expected "empty slot" padding.
      val expectedJson = "[]"

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "show the candidate schedule for a venue when there is one non withdrawn and one withdrawn candidate" in new TestFixture {
      val venue = "Copa (Cabana)"
      val date = "2015-04-25"

      val result = assessmentScheduleControllerWithOneVenueOneDateOneNormalCandidateAndWithdrawnCandidate.
        getVenueDayCandidateScheduleWithDetails(venue, date)(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(200)

      val expectedJson =
        s"""
           |[
           |{
           |    "firstName": "Michael",
           |    "lastName": "Jackson",
           |    "preferredName": "preferredName2",
           |    "email": "email2@mailinator.com",
           |    "phone": "22222222",
           |    "venue": "Copa (Cabana)",
           |    "session": "PM",
           |    "date": "2015-04-25",
           |    "status": "Confirmed"
           |}
           |]
         """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

  }

  "delete an Application Assessment" should {
    "return a deletion success response when an application id exists" in new TestFixture {
      val result = assessmentScheduleControllerWithValidDeletes.deleteApplicationAssessment("applicationid-1")(FakeRequest())

      status(result) must be(OK)
    }
    "return a not found response when an application id does not exist" in new TestFixture {
      val result = assessmentScheduleControllerWithNoDeletes.deleteApplicationAssessment("non-existent-app")(FakeRequest())

      status(result) must be(NOT_FOUND)
    }
    "return unauthorised if the application status is assessment scores accepted" in new TestFixture {
      val result = assessmentScheduleControllerWithUnauthDeletes.deleteApplicationAssessment("someid-1")(FakeRequest())

      status(result) must be(CONFLICT)
    }
  }

  "getting an allocation status" should {
    "return location name, venue description, morning date of assessment and expiry" in new TestFixture {
      val result = assessmentScheduleControllerWithSampleSchedule.allocationStatus("1")(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(OK)

      val jsonTime = new DateTime(2015, 4, 25, 8, 30).getMillis
      val expectedJson =
        s"""
          | {
          |   "location": "Narnia",
          |   "venueDescription": "Test Street 1",
          |   "attendanceDateTime": $jsonTime,
          |   "expirationDate": "2015-04-23"
          | }
        """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "return location name, venue description, afternoon date of assessment and expiry" in new TestFixture {
      val result = assessmentScheduleControllerWithSampleSchedule.allocationStatus("2")(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(OK)

      val jsonTime = new DateTime(2015, 4, 25, 12, 30).getMillis
      val expectedJson =
        s"""
           | {
           |   "location": "Narnia",
           |   "venueDescription": "Test Street 2",
           |   "attendanceDateTime": $jsonTime,
           |   "expirationDate": "2015-04-23"
           | }
        """.stripMargin

      jsonResponse must equal(Json.parse(expectedJson))
    }

    "return NotFound when an invalid application is passed" in new TestFixture {
      val result = assessmentScheduleControllerWithSampleSchedule.allocationStatus("Non Existent Application")(FakeRequest())

      status(result) must be(NOT_FOUND)
    }
  }

  "converting a location to an assessment centre location" should {
    "return a location when the assessment centre location is valid" in new TestFixture {
      val result = assessmentScheduleControllerWithNoVenueDateCandidates.locationToAssessmentCentreLocation("Reading")(FakeRequest())
      val jsonResponse = contentAsJson(result)

      status(result) must be(OK)

      val expectedJson = """{"name":"London"}"""

      jsonResponse must equal(Json.parse(expectedJson))
    }
  }

  "get assessment centre capacities" should {
    "do not return past dates" in new TestFixture {
      val locations = List(AssessmentCentreLocation("London", List(AssessmentCentreVenue("London venue", "",
        List(
          AssessmentCentreVenueCapacityDate(LocalDate.now.minusDays(1), 1, 1),
          AssessmentCentreVenueCapacityDate(LocalDate.now, 2, 2),
          AssessmentCentreVenueCapacityDate(LocalDate.now.minusDays(1), 3, 3),
          AssessmentCentreVenueCapacityDate(LocalDate.now.plusDays(1), 4, 4),
          AssessmentCentreVenueCapacityDate(LocalDate.now.plusDays(2), 5, 5)
        )))))
      when(mockAssessmentCentreRepository.assessmentCentreCapacities).thenReturn(Future.successful(locations))
      val controller = makeAssessmentScheduleController {}

      val result = controller.getAssessmentCentreCapacities("London venue")(FakeRequest())
      status(result) must be(OK)

      val venue = contentAsJson(result).as[AssessmentCentreVenue]
      venue.capacityDates.map(_.amCapacity) must contain only (2, 4, 5)
    }

    "return empty list of slots if all dates are in the past" in new TestFixture {
      val locations = List(AssessmentCentreLocation("London", List(AssessmentCentreVenue("London venue", "",
        List(
          AssessmentCentreVenueCapacityDate(LocalDate.now.minusDays(1), 1, 1),
          AssessmentCentreVenueCapacityDate(LocalDate.now.minusDays(2), 2, 2),
          AssessmentCentreVenueCapacityDate(LocalDate.now.minusDays(3), 2, 2)
        )))))
      when(mockAssessmentCentreRepository.assessmentCentreCapacities).thenReturn(Future.successful(locations))
      val controller = makeAssessmentScheduleController {}

      val result = controller.getAssessmentCentreCapacities("London venue")(FakeRequest())
      status(result) must be(OK)

      val venue = contentAsJson(result).as[AssessmentCentreVenue]
      venue.capacityDates must be(empty)

    }

    "return a list of assessment centres " in new TestFixture {
      val locations = List(AssessmentCentreLocation(
        "London",
        List(
          AssessmentCentreVenue("London venue", "", List()),
          AssessmentCentreVenue("London venue 2", "", List()),
          AssessmentCentreVenue("London venue 3", "", List())
        )
      ))
      when(mockAssessmentCentreRepository.assessmentCentreCapacities).thenReturn(Future.successful(locations))
      val controller = makeAssessmentScheduleController {}

      val result = controller.assessmentCentres(FakeRequest())

      status(result) must be(OK)

      val centres: JsValue = contentAsJson(result)

      centres.as[JsArray].value.size must be(3)
      centres.as[JsArray].value.map(_.as[JsString].value) must be(Seq("London venue", "London venue 2", "London venue 3"))

    }
  }

  trait TestFixture extends TestFixtureBase {

    val mockApplicationAssessmentRepository = mock[ApplicationAssessmentRepository]
    val mockApplicationRepository = mock[GeneralApplicationRepository]
    val mockOnlineTestRepository = mock[OnlineTestRepository]
    val mockPersonalDetailsRepository = mock[PersonalDetailsRepository]
    val mockContactDetailsRepository = mock[ContactDetailsRepository]
    val mockEmailClient = mock[EmailClient]
    val mockApplicationAssessmentService = mock[ApplicationAssessmentService]

    def makeAssessmentScheduleController(op: => Unit) = {
      op
      new AssessmentScheduleController {
        override val aaRepository = mockApplicationAssessmentRepository
        override val acRepository = mockAssessmentCentreRepository
        override val aRepository = mockApplicationRepository
        override val otRepository = mockOnlineTestRepository
        override val auditService = mockAuditService
        override val pdRepository = mockPersonalDetailsRepository
        override val cdRepository = mockContactDetailsRepository
        override val emailClient = mockEmailClient
        override val aaService = mockApplicationAssessmentService
      }
    }

    lazy val assessmentScheduleControllerWithSampleSchedule = makeAssessmentScheduleController {
      applicationAssessmentRepoWithSomeAssessments
      assessmentCentreRepoWithSomeLocations
      applicationRepoWithSomeApplications
      assessmentCentreRepoWithSomeLocationsAndAssessmentCentres
    }

    lazy val assessmentScheduleControllerWithOneVenueDateCandidate = makeAssessmentScheduleController {
      assessmentCentreRepoWithOneDate
      applicationRepositoryWithOneVenueDateCandidate
      applicationAssessmentRepoWithOneVenueDateCandidate
      contactDetailsRepository
      personalDetailsRepository
    }

    lazy val assessmentScheduleControllerWithOneVenueOneDateOneCandidateInPMOneInAMSession = makeAssessmentScheduleController {
      assessmentCentreRepoWithOneDate
      applicationRepositoryWithOneVenueOneDateOneCandidateInPMOneInAMSession
      applicationAssessmentRepoWithOneVenueOneDateOneCandidateInPMOneInAMSession
      contactDetailsRepository
      personalDetailsRepository
    }

    lazy val assessmentScheduleControllerWithOneVenueOneDateOneNormalCandidateAndWithdrawnCandidate = makeAssessmentScheduleController {
      assessmentCentreRepoWithOneDate
      applicationRepositoryWithOneVenueOneDateOneNormalCandidateAndWithdrawnCandidate
      applicationAssessmentRepoWithOneVenueOneDateOneCandidateInPMOneInAMSession
      contactDetailsRepository
      personalDetailsRepository
    }

    lazy val assessmentScheduleControllerWithNoVenueDateCandidates = makeAssessmentScheduleController {
      assessmentCentreRepoWithNoDates
      applicationRepositoryWithNoVenueDateCandidates
      applicationAssessmentRepoWithNoVenueDateCandidates
    }

    lazy val assessmentScheduleControllerWithValidDeletes = makeAssessmentScheduleController {

      applicationAssessmentRepositoryThatCanDelete
      onlineTestRepositoryThatCanRemoveAllocationStatus
    }

    lazy val assessmentScheduleControllerWithNoDeletes = makeAssessmentScheduleController {
      applicationAssessmentRepositoryThatCannotDelete
      onlineTestRepositoryThatCanRemoveAllocationStatus
    }

    lazy val assessmentScheduleControllerWithUnauthDeletes = makeAssessmentScheduleController {
      applicationAssessmentRepositoryThatCannotDeleteUnauthorised
      onlineTestRepositoryThatCanRemoveAllocationStatus
    }

    // scalastyle:off
    def applicationAssessmentRepoWithSomeAssessments = {

      when(mockApplicationAssessmentRepository.applicationAssessment(any())).thenReturn(Future.successful(None))

      when(mockApplicationAssessmentRepository.applicationAssessment(eqTo("1"))).thenReturn(Future.successful(
        Some(ApplicationAssessment(
          "1",
          "Test Venue 1",
          DateTime.parse("2015-04-25").toLocalDate,
          "AM",
          1,
          confirmed = false
        ))
      ))

      when(mockApplicationAssessmentRepository.applicationAssessment(eqTo("2"))).thenReturn(Future.successful(
        Some(ApplicationAssessment(
          "2",
          "Test Venue 2",
          DateTime.parse("2015-04-25").toLocalDate,
          "PM",
          1,
          confirmed = false
        ))
      ))

      when(mockApplicationAssessmentRepository.applicationAssessments).thenReturn(Future.successful(
        List(
          ApplicationAssessment(
            "1",
            "Test Venue 1",
            DateTime.parse("2015-04-25").toLocalDate,
            "AM",
            1,
            confirmed = false
          ),
          ApplicationAssessment(
            "2",
            "Test Venue 1",
            DateTime.parse("2015-04-25").toLocalDate,
            "AM",
            2,
            confirmed = false
          ),
          ApplicationAssessment(
            "2",
            "Test Venue 1",
            DateTime.parse("2015-04-25").toLocalDate,
            "PM",
            1,
            confirmed = false
          )
        )
      ))
    }
    // scalastyle:on

    def assessmentCentreRepoWithSomeLocations = {
      when(mockAssessmentCentreRepository.assessmentCentreCapacities).thenReturn(Future.successful(
        List(
          AssessmentCentreLocation(
            "Narnia",
            List(
              AssessmentCentreVenue(
                "Test Venue 1",
                "Test Street 1",
                List(
                  AssessmentCentreVenueCapacityDate(
                    DateTime.parse("2015-04-25").toLocalDate,
                    2,
                    3
                  )
                )
              ),
              AssessmentCentreVenue(
                "Test Venue 2",
                "Test Street 2",
                List(
                  AssessmentCentreVenueCapacityDate(
                    DateTime.parse("2015-04-27").toLocalDate,
                    1,
                    6
                  )
                )
              )
            )
          )
        )
      ))
    }

    def applicationRepoWithSomeApplications = {
      when(mockApplicationRepository.findApplicationsForAssessmentAllocation(
        eqTo(List("Reading")),
        eqTo(0), eqTo(5)
      )).thenReturn(Future.successful(
        ApplicationForAssessmentAllocationResult(
          List(
            ApplicationForAssessmentAllocation(
              "firstName1", "lastName1", "userId1", "applicationId1", "No", DateTime.now
            ),
            ApplicationForAssessmentAllocation(
              "firstName2", "lastName2", "userId2", "applicationId2", "Yes", DateTime.now.minusDays(2)
            )
          ), 2
        )
      ))

      when(mockApplicationRepository.findApplicationsForAssessmentAllocation(
        eqTo(List("Manchester")),
        eqTo(0), eqTo(5)
      )).thenReturn(Future.successful(
        ApplicationForAssessmentAllocationResult(List.empty, 0)
      ))

      when(mockApplicationRepository.allocationExpireDateByApplicationId(any())).thenReturn(Future.successful(
        Some(LocalDate.parse("2015-04-23"))
      ))
    }

    def assessmentCentreRepoWithSomeLocationsAndAssessmentCentres = {
      when(mockAssessmentCentreRepository.locationsAndAssessmentCentreMapping).thenReturn(Future.successful(
        Map[String, String]("Reading" -> "London", "Manchester" -> "Manchester / Salford")
      ))
    }

    def assessmentCentreRepoWithOneDate = {
      when(mockAssessmentCentreRepository.assessmentCentreCapacityDate(any(), any())).thenReturn(Future.successful(
        AssessmentCentreVenueCapacityDate(
          LocalDate.parse("2015-04-25"),
          2,
          3
        )
      ))
    }

    def applicationRepositoryWithOneVenueDateCandidate = {
      when(mockApplicationRepository.find(any())).thenReturn(Future.successful(
        List(
          Candidate(
            "userid-1",
            Some("appid-1"),
            None,
            Some("Bob"),
            Some("Marley"),
            None,
            None,
            None,
            None
          )
        )
      ))
      when(mockApplicationRepository.findProgress(any())).thenReturn(Future.successful(
        ProgressResponse("appid-1", true, true, true, true, true, List(), true, false,
          OnlineTestProgressResponse(true, true, true, false, false, false, false, true, true))
      ))
    }

    def applicationRepositoryWithOneVenueOneDateOneCandidateInPMOneInAMSession = {
      when(mockApplicationRepository.find(any())).thenReturn(Future.successful(
        List(
          Candidate(
            "userid-1",
            Some("appid-1"),
            None,
            Some("Bob"),
            Some("Marley"),
            None,
            None,
            None,
            None
          ),
          Candidate(
            "userid-2",
            Some("appid-2"),
            None,
            Some("Michael"),
            Some("Jackson"),
            None,
            None,
            None,
            None
          )
        )
      ))
      when(mockApplicationRepository.findProgress(eqTo("appid-1"))).thenReturn(Future.successful(
        ProgressResponse("appid-1", true, true, true, true, true, List(), true, false,
          OnlineTestProgressResponse(true, true, true, false, false, false, false, true, true, false), false)
      ))
      when(mockApplicationRepository.findProgress(eqTo("appid-2"))).thenReturn(Future.successful(
        ProgressResponse("appid-2", true, true, true, true, true, List(), true, false,
          OnlineTestProgressResponse(true, true, true, false, false, false, false, true, true, false), false)
      ))
    }

    def applicationRepositoryWithOneVenueOneDateOneNormalCandidateAndWithdrawnCandidate = {
      when(mockApplicationRepository.find(any())).thenReturn(Future.successful(
        List(
          Candidate(
            "userid-2",
            Some("appid-2"),
            None,
            Some("Michael"),
            Some("Jackson"),
            None,
            None,
            None,
            None
          )
        )
      ))
      when(mockApplicationRepository.findProgress(eqTo("appid-1"))).thenReturn(Future.successful(
        ProgressResponse("appid-1", true, true, true, true, true, List(), true, withdrawn = true,
          OnlineTestProgressResponse(true, true, true, false, false, false, false, true, true, false), false)
      ))
      when(mockApplicationRepository.findProgress(eqTo("appid-2"))).thenReturn(Future.successful(
        ProgressResponse("appid-2", true, true, true, true, true, List(), true, false,
          OnlineTestProgressResponse(true, true, true, false, false, false, false, true, true, false), false)
      ))
    }

    def applicationAssessmentRepoWithOneVenueDateCandidate = {
      when(mockApplicationAssessmentRepository.applicationAssessments(any(), any())).thenReturn(Future.successful(
        List(
          ApplicationAssessment(
            "appid-1",
            "Test Venue 1",
            LocalDate.parse("2015-04-25"),
            "AM",
            1,
            confirmed = true
          )
        )
      ))
    }

    def applicationAssessmentRepoWithOneVenueOneDateOneCandidateInPMOneInAMSession = {
      when(mockApplicationAssessmentRepository.applicationAssessments(any(), any())).thenReturn(Future.successful(
        List(
          ApplicationAssessment(
            "appid-1",
            "Test Venue 1",
            LocalDate.parse("2015-04-25"),
            "AM",
            1,
            confirmed = true
          ),
          ApplicationAssessment(
            "appid-2",
            "Test Venue 1",
            LocalDate.parse("2015-04-25"),
            "PM",
            1,
            confirmed = true
          )
        )
      ))
    }

    def assessmentCentreRepoWithNoDates = {
      when(mockAssessmentCentreRepository.assessmentCentreCapacityDate(any(), any())).thenReturn(Future.successful(
        AssessmentCentreVenueCapacityDate(
          LocalDate.parse("2015-04-25"),
          3,
          6
        )
      ))

      when(mockAssessmentCentreRepository.locationsAndAssessmentCentreMapping).thenReturn(Future.successful(
        Map[String, String]("Reading" -> "London", "Manchester" -> "Manchester / Salford")
      ))
    }

    def applicationRepositoryWithNoVenueDateCandidates = {
      when(mockApplicationRepository.find(any())).thenReturn(Future.successful(
        List()
      ))
    }

    def applicationAssessmentRepoWithNoVenueDateCandidates = {
      when(mockApplicationAssessmentRepository.applicationAssessments(any(), any())).thenReturn(Future.successful(
        List()
      ))
    }

    def applicationAssessmentRepositoryThatCanDelete = {
      when(mockApplicationAssessmentService.removeFromApplicationAssessmentSlot(any())).thenReturn(
        Future.successful(())
      )
      when(mockApplicationRepository.findProgress(any())).thenReturn(
        Future.successful(ProgressResponse(""))
      )
    }
    def applicationAssessmentRepositoryThatCannotDeleteUnauthorised = {
      when(mockApplicationRepository.findProgress(any())).thenReturn(
        Future.successful(ProgressResponse("", assessmentScores = AssessmentScores(accepted = true)))
      )
      when(mockApplicationAssessmentService.removeFromApplicationAssessmentSlot(any())).thenReturn(Future.successful(()))
    }

    def applicationAssessmentRepositoryThatCannotDelete = {
      when(mockApplicationAssessmentService.removeFromApplicationAssessmentSlot(any())).thenReturn(
        Future.failed(new NotFoundException("Non existent app"))
      )
      when(mockApplicationRepository.findProgress(any())).thenReturn(
        Future.successful(ProgressResponse(""))
      )
    }

    def onlineTestRepositoryThatCanRemoveAllocationStatus = {
      when(mockOnlineTestRepository.removeCandidateAllocationStatus(any())).thenReturn(
        Future.successful(())
      )
    }

    def contactDetailsRepository = {
      when(mockContactDetailsRepository.find(eqTo("userid-1"))).thenReturn(
        Future.successful(ContactDetails(Address("address1"), "postCode1", "email1@mailinator.com", Some("11111111")))
      )
      when(mockContactDetailsRepository.find(eqTo("userid-2"))).thenReturn(
        Future.successful(ContactDetails(Address("address2"), "postCode2", "email2@mailinator.com", Some("22222222")))
      )
    }

    def personalDetailsRepository = {
      when(mockPersonalDetailsRepository.find(eqTo("appid-1"))).thenReturn(
        Future.successful(PersonalDetails("firstName1", "lastName1", "preferredName1", LocalDate.parse("2016-05-26"), true, true))
      )
      when(mockPersonalDetailsRepository.find(eqTo("appid-2"))).thenReturn(
        Future.successful(PersonalDetails("firstName2", "lastName2", "preferredName2", LocalDate.parse("2016-05-26"), true, true))
      )
    }
  }
}
