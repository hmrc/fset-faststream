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

package services.testdata

import connectors.AuthProviderClient
import connectors.AuthProviderClient.UserRole
import connectors.testdata.ExchangeObjects.DataGenerationResponse
import play.api.Play.current
import play.api.mvc.RequestHeader
import play.modules.reactivemongo.ReactiveMongoPlugin
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

object TestDataGeneratorService extends TestDataGeneratorService {
}

trait TestDataGeneratorService {

  def clearDatabase()(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      dropMainDatabase <- ReactiveMongoPlugin.mongoConnector.db().drop()
      removeAllUsers <- AuthProviderClient.removeAllUsers()
      makeAdminUser1 <- RegisteredStatusGenerator.createUser(
        1,
        "test_service_manager_1@mailinator.com", "CSR Test", "Service Manager", "TestServiceManager", AuthProviderClient.TechnicalAdminRole
      )
    } yield {
      ()
    }
  }

  def createAdminUsers(numberToGenerate: Int, emailPrefix: String,
                       role: UserRole)(implicit hc: HeaderCarrier): Future[List[DataGenerationResponse]] = {
    Future.successful {
      val parNumbers = (1 to numberToGenerate).par
      parNumbers.tasksupport = new ForkJoinTaskSupport(
        new scala.concurrent.forkjoin.ForkJoinPool(2)
      )
      parNumbers.map { candidateGenerationId =>
        val fut = RegisteredStatusGenerator.createUser(
          candidateGenerationId,
          s"test_service_manager_$emailPrefix$candidateGenerationId@mailinator.com", "CSR Test", "Service Manager",
          "TestServiceManager", role
        )
        Await.result(fut, 10 seconds)
      }.toList
    }
  }

  def createCandidatesInSpecificStatus(numberToGenerate: Int,
    generatorForStatus: BaseGenerator,
    generatorConfig: GeneratorConfig
  )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[DataGenerationResponse]] = {
    Future.successful {
      val parNumbers = (1 to numberToGenerate).par
      parNumbers.tasksupport = new ForkJoinTaskSupport(
        new scala.concurrent.forkjoin.ForkJoinPool(2)
      )
      parNumbers.map {
        candidateGenerationId =>
          val fut = generatorForStatus.generate(candidateGenerationId, generatorConfig)
          Await.result(fut, 10 seconds)
      }.toList
    }
  }
}
