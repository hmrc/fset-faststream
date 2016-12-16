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

package controllers.testdata

import java.io.File

import com.typesafe.config.ConfigFactory
import connectors.AuthProviderClient
import connectors.testdata.ExchangeObjects.Implicits._
import model.Exceptions.EmailTakenException
import model.command.testdata.GeneratorConfig
import model.exchange.testdata._
import model._
import model.persisted.PassmarkEvaluation
import play.api.Play
import play.api.libs.json.{ JsObject, JsString, Json }
import play.api.mvc.{ Action, RequestHeader }
import services.testdata._
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

  def clearDatabase() = Action.async { implicit request =>
    TestDataGeneratorService.clearDatabase().map { _ =>
      Ok(Json.parse("""{"message": "success"}"""))
    }
  }

  // scalastyle:off method.length
  def requestExample = Action { implicit request =>
    val example = CreateCandidateInStatusRequest(
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
      assistanceDetails = Some(AssistanceDetailsRequest(
        hasDisability = Some("false"),
        hasDisabilityDescription = Some(Random.hasDisabilityDescription),
        setGis = Some(false),
        onlineAdjustments = Some(false),
        onlineAdjustmentsDescription = Some(Random.onlineAdjustmentsDescription),
        assessmentCentreAdjustments = Some(false),
        assessmentCentreAdjustmentsDescription = Some(Random.assessmentCentreAdjustmentDescription)
      )),
      schemeTypes = Some(List(SchemeType.Commercial, SchemeType.European, SchemeType.DigitalAndTechnology)),
      isCivilServant = Some(Random.bool),
      hasDegree = Some(Random.bool),
      region = Some("region"),
      loc1scheme1EvaluationResult = Some("loc1 scheme1 result1"),
      loc1scheme2EvaluationResult = Some("loc1 scheme2 result2"),
      confirmedAllocation = Some(Random.bool),
      phase1TestData = Some(Phase1TestDataRequest(
        start = Some("2340-01-01"),
        expiry = Some("2340-01-29"),
        completion = Some("2340-01-16"),
        tscore = Some("80")
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
        completion = Some("2340-01-16")
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

  def createAdminUsers(numberToGenerate: Int, emailPrefix: Option[String], role: String) = Action.async { implicit request =>
    try {
      TestDataGeneratorService.createAdminUsers(numberToGenerate, emailPrefix, AuthProviderClient.getRole(role)).map { candidates =>
        Ok(Json.toJson(candidates))
      }
    } catch {
      case _: EmailTakenException => Future.successful(Conflict(JsObject(List(("message",
          JsString("Email has been already taken. Try with another one by changing the emailPrefix parameter"))))))
    }
  }

  val secretsFileCubiksUrlKey = "microservice.services.cubiks-gateway.testdata.url"
  lazy val cubiksUrlFromConfig = Play.current.configuration.getString(secretsFileCubiksUrlKey)
    .getOrElse(fetchSecretConfigKeyFromFile("cubiks.url"))

  private def fetchSecretConfigKeyFromFile(key: String): String = {
    val path = System.getProperty("user.home") + "/.csr/.secrets"
    val testConfig = ConfigFactory.parseFile(new File(path))
    if (testConfig.isEmpty) {
      throw new IllegalArgumentException(s"No key found at '$secretsFileCubiksUrlKey' and .secrets file does not exist.")
    } else {
      testConfig.getString(s"testdata.$key")
    }
  }

  def createCandidatesInStatusPOST(numberToGenerate: Int) = Action.async(parse.json) { implicit request =>
    withJsonBody[CreateCandidateInStatusRequest] { body =>
      createCandidateInStatus(GeneratorConfig.apply(cubiksUrlFromConfig, body), numberToGenerate)
    }
  }

  private def createCandidateInStatus(config: (Int) => GeneratorConfig, numberToGenerate: Int)
    (implicit hc: HeaderCarrier, rh: RequestHeader) = {
    try {
      TestDataGeneratorService.createCandidatesInSpecificStatus(
        numberToGenerate, StatusGeneratorFactory.getGenerator,
        config
      ).map { candidates =>
        Ok(Json.toJson(candidates))
      }
    } catch {
      case _: EmailTakenException => Future.successful(Conflict(JsObject(List(("message",
          JsString("Email has been already taken. Try with another one by changing the emailPrefix parameter"))))))
    }
  }
}
