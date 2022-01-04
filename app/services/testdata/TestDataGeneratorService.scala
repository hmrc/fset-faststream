/*
 * Copyright 2022 HM Revenue & Customs
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
import javax.inject.{ Inject, Singleton }
import model.exchange.testdata.CreateAdminResponse.CreateAdminResponse
import model.exchange.testdata.CreateAssessorAllocationResponse
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.exchange.testdata.CreateEventResponse
import model.exchange.testdata.{ CreateCandidateAllocationResponse, CreateTestDataResponse }
import model.testdata.CreateAdminData.CreateAdminData
import model.testdata.CreateAssessorAllocationData
import model.testdata.CreateEventData
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import model.testdata.{ CreateCandidateAllocationData, CreateTestData }
import play.api.Logging
import play.api.mvc.RequestHeader
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.collection.JSONCollection
import services.testdata.admin.AdminUserBaseGenerator
import services.testdata.allocation.{ AssessorAllocationGenerator, CandidateAllocationGenerator }
import services.testdata.candidate._
import services.testdata.event.EventGenerator
import services.testdata.faker.DataFaker
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.immutable.ParRange
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

@Singleton
class TestDataGeneratorService @Inject() (authProviderClient: AuthProviderClient,
                                          registeredStatusGenerator: RegisteredStatusGenerator,
                                          candidateRemover: CandidateRemover,
                                          candidateAllocationGenerator: CandidateAllocationGenerator,
                                          assessorAllocationGenerator: AssessorAllocationGenerator,
                                          mongoComponent: ReactiveMongoComponent,
                                          dataFaker: DataFaker,
                                          eventGenerator: EventGenerator) extends Logging {

  def clearDatabase(generateDefaultUsers: Boolean)(implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      _ <- cleanupDb()
      _ <- authProviderClient.removeAllUsers()
      _ <- generateUsers() if generateDefaultUsers
    } yield ()
  }

  def clearCandidates(applicationStatus: Option[String])(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Int] = {
    candidateRemover.remove(applicationStatus)
  }

  def cleanupDb(): Future[Unit] = {
    mongoComponent.mongoConnector.db().collectionNames.map { names =>
      names.foreach { name =>
        import reactivemongo.play.json.ImplicitBSONHandlers._
        mongoComponent.mongoConnector.db().collection[JSONCollection](name).delete().one(BSONDocument.empty)
        logger.info(s"removed data from collection: $name")
      }
    }
  }

  private def generateUsers()(implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      _ <- registeredStatusGenerator.createUser(
        1,
        "test_super_admin_1@mailinator.com", "CSR Test", "Super Admin", Some("TestSuperAdmin"),
        List(AuthProviderClient.SuperAdminRole)
      )
      _ <- registeredStatusGenerator.createUser(
        1,
        "test_service_manager_1@mailinator.com", "CSR Test", "Tech Admin", Some("TestServiceManager"),
        List(AuthProviderClient.TechnicalAdminRole)
      )
      _ <- registeredStatusGenerator.createUser(
        1,
        "test_techadmin@mailinator.com", "CSR Test", "Tech Admin", Some("TestServiceManager"),
        List(AuthProviderClient.TechnicalAdminRole)
      )
      _ <- registeredStatusGenerator.createUser(
        1,
        "test_service_admin@mailinator.com", "CSR Test", "Service Admin", Some("TestServiceManager"),
        List(AuthProviderClient.ServiceAdminRole)
      )
      _ <- registeredStatusGenerator.createUser(
        1,
        "test_assessor@mailinator.com", "CSR Test", "Assessor", Some("TestServiceManager"),
        List(AuthProviderClient.AssessorRole)
      )
      _ <- registeredStatusGenerator.createUser(
        1,
        "test_qac@mailinator.com", "CSR Test", "QAC", Some("TestServiceManager"),
        List(AuthProviderClient.AssessorRole)
      )
      _ <- registeredStatusGenerator.createUser(
        1,
        "test_service_admin_assessor@mailinator.com", "CSR Test", "Admin & Assessor", Some("TestServiceManagerAssessor"),
        List(AuthProviderClient.ServiceAdminRole, AuthProviderClient.AssessorRole)
      )
    } yield { () }
  }

  def createAdminUsers(numberToGenerate: Int, emailPrefix: Option[String],
                       roles: List[UserRole])(implicit hc: HeaderCarrier): Future[List[CreateCandidateResponse]] = {
    Future.successful {
      val parNumbers = getParNumbers(numberToGenerate)

      parNumbers.map { candidateGenerationId =>
        val fut = registeredStatusGenerator.createUser(
          candidateGenerationId,
          s"test_service_manager_${emailPrefix.getOrElse(dataFaker.Random.number(Some(10000)))}a$candidateGenerationId@mailinator.com",
          "CSR Test",
          "Service Manager",
          Some("TestServiceManager"),
          roles
        )
        Await.result(fut, 10 seconds)
      }.toList
    }
  }

  def createCandidates(numberToGenerate: Int,
                       generatorForStatus: CreateCandidateData => BaseGenerator,
                       configGenerator: Int => CreateCandidateData
                      )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[CreateCandidateResponse]] = {
    Future.successful {
      val parNumbers = getParNumbers(numberToGenerate)

      // one wasted generation of config
      val config = configGenerator(parNumbers.head)
      val generator = generatorForStatus(config)

      runInParallel(parNumbers, configGenerator, generator.generate)
    }
  }

  def createAdmins(numberToGenerate: Int,
                   generatorForStatus: CreateAdminData => AdminUserBaseGenerator,
                   createData: Int => CreateAdminData
                  )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[CreateAdminResponse]] = {
    Future.successful {
      val parNumbers = getParNumbers(numberToGenerate)

      // one wasted generation of config
      val config = createData(parNumbers.head)
      val generator = generatorForStatus(config)

      runInParallel(parNumbers, createData, generator.generate)
    }
  }

  def createEvents(numberToGenerate: Int, createDatas: List[Int => CreateEventData])(
    implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[CreateEventResponse]] = {
    val listOfFutures = createDatas.map { createData =>
      createEvent(numberToGenerate, createData)
    }
    Future.sequence(listOfFutures).map(_.flatten)
  }

  def createEvent(numberToGenerate: Int, createData: Int => CreateEventData)(
    implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[CreateEventResponse]] = {
    Future.successful {
      val parNumbers = getParNumbers(numberToGenerate)

      // one wasted generation of data
      val _ = createData(parNumbers.head)

      runInParallel(parNumbers, createData, eventGenerator.generate)
    }
  }

  def createAssessorAllocation(numberToGenerate: Int, createData: Int => CreateAssessorAllocationData)(
    implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[CreateAssessorAllocationResponse]] = {
    Future.successful {
      val parNumbers = getParNumbers(numberToGenerate)

      // one wasted generation of data
      val _ = createData(parNumbers.head)

      runInParallel(parNumbers, createData, assessorAllocationGenerator.generate)
    }
  }

  def createCandidateAllocation(numberToGenerate: Int, createData: Int => CreateCandidateAllocationData)
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[CreateCandidateAllocationResponse]] = {
    Future.successful {
      val parNumbers = getParNumbers(numberToGenerate)
      createData(parNumbers.head)
      runInParallel(parNumbers, createData, candidateAllocationGenerator.generate)
    }
  }

  def createAssessorAllocations(numberToGenerate: Int, createData: List[Int => CreateAssessorAllocationData])(
    implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[CreateAssessorAllocationResponse]] = {
    val listOfFutures = createData.map { createData =>
      createAssessorAllocation(numberToGenerate, createData)
    }
    Future.sequence(listOfFutures).map(_.flatten)
  }

  def createCandidateAllocations(numberToGenerate: Int, data: List[Int => CreateCandidateAllocationData])
                                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[CreateCandidateAllocationResponse]] = {
    val listOfFutures = data.map { element =>
      createCandidateAllocation(numberToGenerate, element)
    }
    Future.sequence(listOfFutures).map(_.flatten)
  }

  private def getParNumbers(numberToGenerate: Int): ParRange = {
    val parNumbers = (1 to numberToGenerate).par
    parNumbers.tasksupport = new ForkJoinTaskSupport(
      new scala.concurrent.forkjoin.ForkJoinPool(2)
    )
    parNumbers
  }

  private def runInParallel[D <: CreateTestData, R <: CreateTestDataResponse](parNumbers: ParRange,
                                                                              createData: Int => D,
                                                                              block: (Int, D) => Future[R]): List[R] = {
    parNumbers.map { candidateGenerationId =>
      Await.result(
        block(candidateGenerationId, createData(candidateGenerationId)),
        10 seconds
      )
    }.toList
  }
}
