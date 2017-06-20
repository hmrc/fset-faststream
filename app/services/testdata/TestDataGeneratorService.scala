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
import connectors.AuthProviderClient._
import model.exchange.testdata.CreateAdminUserInStatusResponse.CreateAdminUserInStatusResponse
import model.exchange.testdata.CreateCandidateInStatusResponse.CreateCandidateInStatusResponse
import model.exchange.testdata.CreateEventResponse.CreateEventResponse
import model.testdata.CreateCandidateInStatusData.CreateCandidateInStatusData
import model.testdata.CreateAdminUserInStatusData.CreateAdminUserInStatusData
import model.exchange.testdata.CreateTestDataResponse
import model.testdata.CreateEventData.CreateEventData
import model.testdata.CreateTestData
import play.api.Play.current
import play.api.mvc.RequestHeader
import play.modules.reactivemongo.MongoDbConnection
import services.testdata.admin.AdminUserBaseGenerator
import services.testdata.candidate.{ BaseGenerator, RegisteredStatusGenerator }
import services.testdata.event.EventGenerator
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.immutable.ParRange
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

object TestDataGeneratorService extends TestDataGeneratorService {
}

trait TestDataGeneratorService extends MongoDbConnection {

  def clearDatabase(generateDefaultUsers: Boolean)(implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      _ <- db().drop()
      _ <- AuthProviderClient.removeAllUsers()
      _ <- generateUsers() if generateDefaultUsers
    } yield ()
  }

  def generateUsers()(implicit hc: HeaderCarrier): Future[Unit] = {
    for {
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
    } yield { () }
  }

  def createAdminUsers(numberToGenerate: Int, emailPrefix: Option[String],
                       role: UserRole)(implicit hc: HeaderCarrier): Future[List[CreateCandidateInStatusResponse]] = {
    Future.successful {
      val parNumbers = getParNumbers(numberToGenerate)

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
                                       generatorForStatus: (CreateCandidateInStatusData) => BaseGenerator,
                                       configGenerator: (Int) => CreateCandidateInStatusData
                                      )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[CreateCandidateInStatusResponse]] = {
    Future.successful {
      val parNumbers = getParNumbers(numberToGenerate)

      // one wasted generation of config
      val config = configGenerator(parNumbers.head)
      val generator = generatorForStatus(config)

      runInParallel(parNumbers, configGenerator, generator.generate)
    }
  }

  def createAdminUserInSpecificStatus(numberToGenerate: Int,
                                      generatorForStatus: (CreateAdminUserInStatusData) => AdminUserBaseGenerator,
                                      createData: (Int) => CreateAdminUserInStatusData
                                      )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[CreateAdminUserInStatusResponse]] = {
    Future.successful {
      val parNumbers = getParNumbers(numberToGenerate)

      // one wasted generation of config
      val config = createData(parNumbers.head)
      val generator = generatorForStatus(config)

      runInParallel(parNumbers, createData, generator.generate)
    }
  }

  def createEvent(numberToGenerate: Int, createData: (Int) => CreateEventData)(
    implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[CreateEventResponse]] = {
    Future.successful {
      val parNumbers = getParNumbers(numberToGenerate)

      // one wasted generation of data
      val data = createData(parNumbers.head)

      runInParallel(parNumbers, createData, EventGenerator.generate)
    }
  }

  private def getParNumbers(numberToGenerate: Int): ParRange = {
    val parNumbers = (1 to numberToGenerate).par
    parNumbers.tasksupport = new ForkJoinTaskSupport(
      new scala.concurrent.forkjoin.ForkJoinPool(2)
    )
    parNumbers
  }


  private def runInParallel[D <: CreateTestData, R <: CreateTestDataResponse](parNumbers: ParRange,
                                                                              createData: (Int => D),
                                                                              block: ((Int, D) => Future[R]))
  : List[R] = {
    parNumbers.map { candidateGenerationId =>
      Await.result(
        block(candidateGenerationId, createData(candidateGenerationId)),
        10 seconds
      )
    }.toList
  }

}
