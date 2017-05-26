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

package services.testdata

import connectors.AuthProviderClient
import connectors.AuthProviderClient.UserRole
import connectors.testdata.ExchangeObjects.DataGenerationResponse
import model.command.testdata.GeneratorConfig
import play.api.Play.current
import play.api.mvc.RequestHeader
import play.modules.reactivemongo.MongoDbConnection
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

object TestDataGeneratorService extends TestDataGeneratorService {
}

trait TestDataGeneratorService extends MongoDbConnection {

  def clearDatabase()(implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      _ <- db().drop()
      _ <- AuthProviderClient.removeAllUsers()
      _ <- RegisteredStatusGenerator.createUser(
        1,
        "test_service_manager_1@mailinator.com", "CSR Test", "Tech Admin", Some("TestServiceManager"), AuthProviderClient.TechnicalAdminRole
      )
      _ <- RegisteredStatusGenerator.createUser(
        1,
        "test_techadmin@mailinator.com", "CSR Test", "Tech Admin", Some("TestServiceManager"), AuthProviderClient.TechnicalAdminRole
      )
      _ <- RegisteredStatusGenerator.createUser(
        1,
        "test_service_admin@mailinator.com", "CSR Test", "Service Admin", Some("TestServiceManager"), AuthProviderClient.ServiceAdminRole
      )
      _ <- RegisteredStatusGenerator.createUser(
        1,
        "test_assessor@mailinator.com", "CSR Test", "Assessor", Some("TestServiceManager"), AuthProviderClient.AssessorRole
      )
      _ <- RegisteredStatusGenerator.createUser(
        1,
        "test_qac@mailinator.com", "CSR Test", "QAC", Some("TestServiceManager"), AuthProviderClient.QacRole
      )
    } yield {
      ()
    }
  }

  def createAdminUsers(numberToGenerate: Int, emailPrefix: Option[String],
                       role: UserRole)(implicit hc: HeaderCarrier): Future[List[DataGenerationResponse]] = {
    Future.successful {
      val parNumbers = (1 to numberToGenerate).par
      parNumbers.tasksupport = new ForkJoinTaskSupport(
        new scala.concurrent.forkjoin.ForkJoinPool(2)
      )
      parNumbers.map { candidateGenerationId =>
        val fut = RegisteredStatusGenerator.createUser(
          candidateGenerationId,
          s"test_service_manager_${emailPrefix.getOrElse(Random.number(Some(10000)))}a$candidateGenerationId@mailinator.com",
          "CSR Test",
          "Service Manager",
          Some("TestServiceManager"),
          role
        )
        Await.result(fut, 10 seconds)
      }.toList
    }
  }

  def createCandidatesInSpecificStatus(numberToGenerate: Int,
                                       generatorForStatus: (GeneratorConfig) => BaseGenerator,
                                       configGenerator: (Int) => GeneratorConfig
                                      )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[DataGenerationResponse]] = {
    Future.successful {

      val parNumbers = (1 to numberToGenerate).par
      parNumbers.tasksupport = new ForkJoinTaskSupport(
        new scala.concurrent.forkjoin.ForkJoinPool(2)
      )

      // one wasted generation of config
      val config = configGenerator(parNumbers.head)
      val generator = generatorForStatus(config)

      parNumbers.map { candidateGenerationId =>
        Await.result(
          generator.generate(candidateGenerationId, configGenerator(candidateGenerationId)),
          10 seconds
        )
      }.toList

    }
  }
}
