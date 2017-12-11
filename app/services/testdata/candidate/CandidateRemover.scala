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

package services.testdata.candidate

import connectors.AuthProviderClient
import play.api.mvc.RequestHeader
import repositories.MongoDbConnection
import repositories.testdata.{ ApplicationRemovalMongoRepository, ApplicationRemovalRepository }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

trait CandidateRemover {
  val appRemovalRepo: ApplicationRemovalRepository
  val authClient: AuthProviderClient

  def remove(applicationStatus: Option[String])(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Int]
}

object CandidateRemover extends CandidateRemover {

  private implicit val connection = {
    MongoDbConnection.mongoConnector.db
  }

  override val appRemovalRepo = new ApplicationRemovalMongoRepository()
  override val authClient: AuthProviderClient = AuthProviderClient

  def remove(applicationStatus: Option[String])(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Int] = {
    for {
      userIds <- appRemovalRepo.remove(applicationStatus)
      _ <- Future.sequence(userIds.map(uid => authClient.removeUser(uid)))
    } yield userIds.size
  }
}
