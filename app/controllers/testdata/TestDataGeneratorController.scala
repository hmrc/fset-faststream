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

import com.typesafe.config.{Config, ConfigFactory}
import connectors.testdata.ExchangeObjects.Implicits._
import model.ApplicationStatuses
import model.EvaluationResults.Result
import model.ApplicationStatuses
import play.api.Play
import play.api.libs.json.Json
import play.api.mvc.Action
import services.testdata._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object TestDataGeneratorController extends TestDataGeneratorController

trait TestDataGeneratorController extends BaseController {
  def clearDatabase() = Action.async { implicit request =>
    TestDataGeneratorService.clearDatabase().map { _ =>
      Ok(Json.parse("""{"message": "success"}"""))
    }
  }

  def createAdminUsers(numberToGenerate: Int) = Action.async { implicit request =>
    TestDataGeneratorService.createAdminUsers(numberToGenerate).map { candidates =>
      Ok(Json.toJson(candidates))
    }
  }

  lazy val cubiksUrlFromConfig = Play.current.configuration.getString("cubiks.url").getOrElse(fetchSecretConfigKeyFromFile("cubiks.url"))

  private def fetchSecretConfigKeyFromFile(key: String): String = {
    val path = System.getProperty("user.home") + "/.csr/.secrets"
    val testConfig = ConfigFactory.parseFile(new File(path))
    testConfig.getString(s"testdata.$key")
  }

  // scalastyle:off parameter.number
  def createCandidatesInStatus(status: String, numberToGenerate: Int,
    emailPrefix: String,
    setGis: Boolean,
    region: Option[String],
    loc1scheme1EvaluationResult: Option[String],
    loc1scheme2EvaluationResult: Option[String],
    previousStatus: Option[String] = None,
    confirmedAllocation: Boolean) = Action.async { implicit request =>

    val initialConfig = GeneratorConfig(
      emailPrefix = emailPrefix,
      setGis = setGis,
      cubiksUrl = cubiksUrlFromConfig,
      region = region,
      loc1scheme1Passmark = loc1scheme1EvaluationResult.map(Result(_)),
      loc1scheme2Passmark = loc1scheme2EvaluationResult.map(Result(_)),
      previousStatus = previousStatus,
      confirmedAllocation = status match {
        case ApplicationStatuses.AllocationUnconfirmed => false
        case ApplicationStatuses.AllocationConfirmed => true
        case _ => confirmedAllocation
      }
    )
    // scalastyle:on

    TestDataGeneratorService.createCandidatesInSpecificStatus(numberToGenerate, StatusGeneratorFactory.getGenerator(status),
      initialConfig).map { candidates =>
      Ok(Json.toJson(candidates))
    }
  }
}
