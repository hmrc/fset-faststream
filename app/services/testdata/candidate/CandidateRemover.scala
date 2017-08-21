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
import repositories.application.GeneralApplicationRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait CandidateRemover {
  val appRepo: GeneralApplicationRepository
  val authClient: AuthProviderClient

  def remove(applicationStatus: Option[String])(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Int]
}

object CandidateRemover extends CandidateRemover {
  override val appRepo = repositories.applicationRepository
  override val authClient: AuthProviderClient = AuthProviderClient

  def remove(applicationStatus: Option[String])(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Int] = {
    for {
      userIds <- appRepo.remove(applicationStatus)
      _ <- userIds.map(uid => authClient.removeUser(uid))
    } yield userIds.size
  }
}
