/*
 * Copyright 2017 HM Revenue & Customs
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

package controllers.testdata

import config.MicroserviceAppConfig
import connectors.AuthProviderClient
import factories.UUIDFactory
import model.Exceptions.EmailTakenException
import model._
import model.command.testdata.CreateAdminRequest.{ AssessorAvailabilityRequest, AssessorRequest, CreateAdminRequest }
import model.command.testdata.CreateAssessorAllocationRequest.CreateAssessorAllocationRequest
import model.command.testdata.CreateCandidateRequest.{ CreateCandidateRequest, _ }
import model.command.testdata.CreateEventRequest.CreateEventRequest
import model.exchange.AssessorSkill
import model.persisted.assessor.AssessorStatus
import model.persisted.eventschedules.{ EventType, Session, SkillType }
import model.testdata.CreateAdminData.CreateAdminData
import model.testdata.CreateAssessorAllocationData.CreateAssessorAllocationData
import model.testdata.CreateCandidateData.CreateCandidateData
import model.testdata.CreateEventData.CreateEventData
import org.joda.time.{ LocalDate, LocalTime }
import play.api.libs.json.{ JsObject, JsString, JsValue, Json }
import play.api.mvc.{ Action, AnyContent, RequestHeader }
import services.testdata._
import services.testdata.candidate.{ AdminStatusGeneratorFactory, CandidateStatusGeneratorFactory }
import services.testdata.faker.DataFaker.Random
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TestDataGeneratorController extends TestDataGeneratorController

trait TestDataGeneratorController extends BaseController {

  def ping = Action { implicit request =>
    Ok("OK")
  }

  def clearDatabase(generateDefaultUsers: Boolean) = Action.async { implicit request =>
    TestDataGeneratorService.clearDatabase(generateDefaultUsers).map { _ =>
      Ok(Json.parse("""{"message": "success"}"""))
    }
  }

  // scalastyle:off method.length
  def exampleCreateCandidate = Action { implicit request =>
    val example = CreateCandidateRequest(
      statusData = StatusDataRequest(
        applicationStatus = ApplicationStatus.SUBMITTED.toString,
        previousApplicationStatus = Some(ApplicationStatus.REGISTERED.toString),
        progressStatus = Some(ProgressStatuses.SUBMITTED.toString),
        applicationRoute = Some(ApplicationRoute.Faststream.toString)
      ),
      personalData = Some(PersonalDataRequest(
        emailPrefix = Some(s"testf${Random.number()}"),
        firstName = Some("Kathryn"),
        lastName = Some("Janeway"),
        preferredName = Some("Captain"),
        dateOfBirth = Some("2328-05-20"),
        postCode = Some("QQ1 1QQ"),
        country = Some("America")
      )),
      diversityDetails = Some(DiversityDetailsRequest(
        genderIdentity = Some("Female"),
        sexualOrientation = Some("Straight"),
        ethnicity = Some("White"),
        universityAttended = Some("W01-USW"),
        parentalEmployment = Some("Traditional professional"),
        parentalEmployedOrSelfEmployed = Some("Employed"),
        parentalCompanySize = Some("Small (1 to 24 employees)")
      )),
      assistanceDetails = Some(AssistanceDetailsRequest(
        hasDisability = Some("Yes"),
        hasDisabilityDescription = Some(Random.hasDisabilityDescription),
        setGis = Some(true),
        onlineAdjustments = Some(true),
        onlineAdjustmentsDescription = Some(Random.onlineAdjustmentsDescription),
        assessmentCentreAdjustments = Some(true),
        assessmentCentreAdjustmentsDescription = Some(Random.assessmentCentreAdjustmentDescription)
      )),
      schemeTypes = Some(List(SchemeId("Commercial"), SchemeId("European"), SchemeId("DigitalAndTechnology"))),
      isCivilServant = Some(Random.bool),
      hasFastPass = Some(true),
      hasDegree = Some(Random.bool),
      region = Some("region"),
      loc1scheme1EvaluationResult = Some("loc1 scheme1 result1"),
      loc1scheme2EvaluationResult = Some("loc1 scheme2 result2"),
      confirmedAllocation = Some(Random.bool),
      phase1TestData = Some(Phase1TestDataRequest(
        start = Some("2340-01-01"),
        expiry = Some("2340-01-29"),
        completion = Some("2340-01-16"),
        bqtscore = Some("80"),
        sjqtscore = Some("70")
      )),
      phase2TestData = Some(Phase2TestDataRequest(
        start = Some("2340-01-01"),
        expiry = Some("2340-01-29"),
        completion = Some("2340-01-16"),
        tscore = Some("80")
      )),
      phase3TestData = Some(Phase3TestDataRequest(
        start = Some("2340-01-01"),
        expiry = Some("2340-01-29"),
        completion = Some("2340-01-16"),
        score = Some(12.0),
        receivedBeforeInHours = Some(72),
        generateNullScoresForFewQuestions = Some(false)
      )),
      adjustmentInformation = Some(Adjustments(
        adjustments = Some(List("etrayInvigilated", "videoInvigilated")),
        adjustmentsConfirmed = Some(true),
        etray = Some(AdjustmentDetail(timeNeeded = Some(33), invigilatedInfo = Some("Some comments here")
          , otherInfo = Some("Some other comments here"))),
        video = Some(AdjustmentDetail(timeNeeded = Some(33), invigilatedInfo = Some("Some comments here")
          , otherInfo = Some("Some other comments here")))
      ))
    )

    Ok(Json.toJson(example))
  }

  // scalastyle:on method.length

  def exampleCreateAdmin = Action { implicit request =>
    val example = CreateAdminRequest(
      emailPrefix = Some("admin_user"),
      firstName = Some("Admin user 1"),
      lastName = Some("lastname"),
      preferredName = Some("Ad"),
      role = Some("assessor"),
      phone = Some("123456789"),
      assessor = Some(AssessorRequest(
        skills = Some(List("ASSESSOR", "QUALITY_ASSURANCE_COORDINATOR")),
        sifterSchemes = Some(List(SchemeId("GovernmentEconomicsService"), SchemeId("ProjectDelivery"), SchemeId("Sdip"))),
        civilServant = Some(true),
        availability = Some(List(
          AssessorAvailabilityRequest("London", LocalDate.now()),
          AssessorAvailabilityRequest("Newcastle", LocalDate.now())
        )),
        status = AssessorStatus.AVAILABILITIES_SUBMITTED
      ))
    )

    Ok(Json.toJson(example))
  }

  def exampleCreateEvent = Action { implicit request =>
    val example = CreateEventRequest(
      id = Some(UUIDFactory.generateUUID()),
      eventType = Some(EventType.FSAC),
      description = Some("PDFS FSB"),
      location = Some("London"),
      venue = Some("LONDON_FSAC"),
      date = Some(LocalDate.now),
      capacity = Some(32),
      minViableAttendees = Some(24),
      attendeeSafetyMargin = Some(30),
      startTime = Some(LocalTime.now()),
      endTime = Some(LocalTime.now().plusHours(1)),
      skillRequirements = Some(Map(SkillType.ASSESSOR.toString -> 4,
        "CHAIR" -> 1)),
      sessions = Some(List(
        Session(UniqueIdentifier.randomUniqueIdentifier.toString(), "Single", 36, 12, 4, LocalTime.now, LocalTime.now.plusHours(1))))
    )

    Ok(Json.toJson(example))
  }

  def exampleCreateEvents = Action { implicit request =>
    val example1 = CreateEventRequest(
      id = Some(UUIDFactory.generateUUID()),
      eventType = Some(EventType.FSAC),
      description = Some("PDFS FSB"),
      location = Some("London"),
      venue = Some("LONDON_FSAC"),
      date = Some(LocalDate.now),
      capacity = Some(32),
      minViableAttendees = Some(24),
      attendeeSafetyMargin = Some(30),
      startTime = Some(LocalTime.now()),
      endTime = Some(LocalTime.now()),
      skillRequirements = Some(Map(SkillType.ASSESSOR.toString -> 4,
        "CHAIR" -> 1)),
      sessions = Some(List(
        Session(UniqueIdentifier.randomUniqueIdentifier.toString(), "Single", 36, 12, 4, LocalTime.now, LocalTime.now.plusHours(1))))
    )
    val example2 = example1.copy(
      id = Some(UUIDFactory.generateUUID()),
      location = Some("Newcastle"),
      venue = Some("NEWCASTLE_FSAC")
      )

    Ok(Json.toJson(List(example1, example2)))
  }

  def exampleCreateAssessorAllocations: Action[AnyContent] = Action.async { implicit request =>
    val example1 = CreateAssessorAllocationRequest(
      "id2",
      "eventId2",
      Some(AllocationStatuses.UNCONFIRMED),
      AssessorSkill.AllSkillsWithLabels.tail.head.name.toString,
      Some("version1"))
    val example2 = CreateAssessorAllocationRequest(
      "id3",
      "eventId3",
      Some(AllocationStatuses.CONFIRMED),
      AssessorSkill.AllSkillsWithLabels.tail.tail.head.name.toString,
      Some("version1"))
    Future.successful(Ok(Json.toJson(List(example1, example2))))
  }

  def createAdmins(numberToGenerate: Int, emailPrefix: Option[String], role: String): Action[AnyContent] = Action.async { implicit request =>
    try {
      TestDataGeneratorService.createAdminUsers(numberToGenerate, emailPrefix, AuthProviderClient.getRole(role)).map { candidates =>
        Ok(Json.toJson(candidates))
      }
    } catch {
      case _: EmailTakenException => Future.successful(Conflict(JsObject(List(("message",
        JsString("Email has been already taken. Try with another one by changing the emailPrefix parameter"))))))
    }
  }

  def createAdminsPOST(numberToGenerate: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[CreateAdminRequest] { createRequest =>
      createAdmins(CreateAdminData.apply(createRequest), numberToGenerate)
    }
  }

  private lazy val cubiksUrlFromConfig: String = MicroserviceAppConfig.testDataGeneratorCubiksSecret

  def createCandidatesPOST(numberToGenerate: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[CreateCandidateRequest] { createRequest =>
      createCandidates(CreateCandidateData.apply(cubiksUrlFromConfig, createRequest), numberToGenerate)
    }
  }

  def createEventsPOST(numberToGenerate: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[List[CreateEventRequest]] { createRequests =>
      val createDatas: List[(Int) => CreateEventData] = createRequests.map { createRequest =>
        val createData: (Int) => CreateEventData = CreateEventData.apply(createRequest)
        createData
      }
      createEvents(createDatas, numberToGenerate)
    }
  }

  def createEventPOST(numberToGenerate: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[CreateEventRequest] { createRequest =>
      createEvent(CreateEventData.apply(createRequest), numberToGenerate)
    }
  }


  def createAssessorAllocationsPOST(numberToGenerate: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[List[CreateAssessorAllocationRequest]] { createRequests =>
      val createDatas: List[(Int) => CreateAssessorAllocationData] = createRequests.map { createRequest =>
        val createData: (Int) => CreateAssessorAllocationData = CreateAssessorAllocationData.apply(createRequest)
        createData
      }
      createAssessorAllocations(createDatas, numberToGenerate)
    }
  }

  private def createCandidates(config: (Int) => CreateCandidateData, numberToGenerate: Int)
                              (implicit hc: HeaderCarrier, rh: RequestHeader) = {
    try {
      TestDataGeneratorService.createCandidates(
        numberToGenerate, CandidateStatusGeneratorFactory.getGenerator,
        config
      ).map { candidates =>
        Ok(Json.toJson(candidates))
      }
    } catch {
      case _: EmailTakenException => Future.successful(Conflict(JsObject(List(("message",
        JsString("Email has been already taken. Try with another one by changing the emailPrefix parameter"))))))
      case ex: Throwable => Future.successful(Conflict(JsObject(List(("message",
        JsString(s"There was an exception creating the candidate. Message=[${ex.getMessage}]"))))))
    }
  }

  private def createAdmins(createData: (Int) => CreateAdminData, numberToGenerate: Int)
                          (implicit hc: HeaderCarrier, rh: RequestHeader) = {
    try {
      TestDataGeneratorService.createAdmins(
        numberToGenerate,
        AdminStatusGeneratorFactory.getGenerator,
        createData
      ).map { admins =>
        Ok(Json.toJson(admins))
      }
    } catch {
      case _: EmailTakenException => Future.successful(Conflict(JsObject(List(("message",
        JsString("Email has been already taken. Try with another one by changing the emailPrefix parameter"))))))
    }
  }

  private def createEvent(createData: (Int) => CreateEventData, numberToGenerate: Int)
                         (implicit hc: HeaderCarrier, rh: RequestHeader) = {
    try {
      TestDataGeneratorService.createEvent(
        numberToGenerate,
        createData
      ).map { events =>
        Ok(Json.toJson(events))
      }
    } catch {
      case ex: Throwable => Future.successful(Conflict(JsObject(List(("message",
        JsString(s"There was an exception creating the events: ${ex.getMessage}"))))))
    }
  }

  private def createEvents(createDatas: List[(Int) => CreateEventData], numberToGenerate: Int)
                          (implicit hc: HeaderCarrier, rh: RequestHeader) = {
    try {
      TestDataGeneratorService.createEvents(
        numberToGenerate,
        createDatas
      ).map { events =>
        Ok(Json.toJson(events))
      }
    } catch {
      case ex: Throwable => Future.successful(Conflict(JsObject(List(("message",
        JsString(s"There was an exception creating the events: ${ex.getMessage}"))))))
    }
  }

  private def createAssessorAllocations(createDatas: List[(Int) => CreateAssessorAllocationData], numberToGenerate: Int)
                          (implicit hc: HeaderCarrier, rh: RequestHeader) = {
    try {
      TestDataGeneratorService.createAssessorAllocations(
        numberToGenerate,
        createDatas
      ).map { assessorAllocations =>
        Ok(Json.toJson(assessorAllocations))
      }
    } catch {
      case ex: Throwable => Future.successful(Conflict(JsObject(List(("message",
        JsString(s"There was an exception creating the assessor allocations: ${ex.getMessage}"))))))
    }
  }
}
