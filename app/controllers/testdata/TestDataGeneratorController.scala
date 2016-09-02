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
import controllers.testdata.TestDataGeneratorController.InvalidPostCodeFormatException
import model.EvaluationResults.Result
import model.ApplicationStatuses
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import play.api.Play
import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }
import play.api.libs.json.Json
import play.api.mvc.Action
import services.testdata._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TestDataGeneratorController extends TestDataGeneratorController {

  sealed case class InvalidPostCodeFormatException(message: String) extends Exception(message)

}

trait TestDataGeneratorController extends BaseController {

  def ping = Action.async { implicit request =>
    Future.successful(Ok("OK"))
  }

  def clearDatabase() = Action.async { implicit request =>
    TestDataGeneratorService.clearDatabase().map { _ =>
      Ok(Json.parse("""{"message": "success"}"""))
    }
  }

  def createAdminUsers(numberToGenerate: Int, emailPrefix: String, role: String) = Action.async { implicit request =>
        TestDataGeneratorService.createAdminUsers(numberToGenerate, emailPrefix, AuthProviderClient.getRole(role)).map { candidates =>
      Ok(Json.toJson(candidates))
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

  // scalastyle:off parameter.number
  def createCandidatesInStatus(status: String, numberToGenerate: Int,
                               emailPrefix: String,
                               setGis: Boolean,
                               firstName: Option[String],
                               lastName: Option[String],
                               preferredName: Option[String],
                               region: Option[String],
                               loc1scheme1EvaluationResult: Option[String],
                               loc1scheme2EvaluationResult: Option[String],
                               previousStatus: Option[String] = None,
                               confirmedAllocation: Boolean,
    dateOfBirth: Option[String] = None,
    postCode: Option[String]) = Action.async { implicit request =>

    val initialConfig = GeneratorConfig(
      emailPrefix = emailPrefix,
      setGis = setGis,
      cubiksUrl = cubiksUrlFromConfig,
      firstName = firstName,
      lastName = lastName,
      preferredName = preferredName,
      region = region,
      loc1scheme1Passmark = loc1scheme1EvaluationResult.map(Result(_)),
      loc1scheme2Passmark = loc1scheme2EvaluationResult.map(Result(_)),
      previousStatus = previousStatus,
      confirmedAllocation = status match {
        case ApplicationStatuses.AllocationUnconfirmed => false
        case ApplicationStatuses.AllocationConfirmed => true
        case _ => confirmedAllocation
      },
      dob = dateOfBirth.map(x => LocalDate.parse(x, DateTimeFormat.forPattern("yyyy-MM-dd"))),
      postCode = postCode.map ( pc => validatePostcode(pc) )
    )
    // scalastyle:on

    TestDataGeneratorService.createCandidatesInSpecificStatus(numberToGenerate, StatusGeneratorFactory.getGenerator(status),
      initialConfig).map { candidates =>
      Ok(Json.toJson(candidates))
    }
  }


  private def validatePostcode(postcode: String) = {
    // putting this on multiple lines won't make this regex any clearer
    // scalastyle:off line.size.limit
    val postcodePattern = """^(?i)(GIR 0AA)|((([A-Z][0-9][0-9]?)|(([A-Z][A-HJ-Y][0-9][0-9]?)|(([A-Z][0-9][A-Z])|([A-Z][A-HJ-Y][0-9]?[A-Z])))) ?[0-9][A-Z]{2})$""".r
    // scalastyle:on line.size.limit

    postcodePattern.pattern.matcher(postcode).matches match {
      case true => postcode
      case false if postcode.isEmpty => throw InvalidPostCodeFormatException(s"Postcode $postcode is empty")
      case false => throw InvalidPostCodeFormatException(s"Postcode $postcode is in an invalid format")
    }
  }
}
