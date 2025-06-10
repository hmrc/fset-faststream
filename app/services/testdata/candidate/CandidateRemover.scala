/*
 * Copyright 2023 HM Revenue & Customs
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

import connectors.AuthProviderClientTDG

import javax.inject.{Inject, Singleton}
import play.api.mvc.RequestHeader
import repositories.testdata.ApplicationRemovalRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CandidateRemover @Inject()(appRemovalRepo: ApplicationRemovalRepository,
                                  authClient: AuthProviderClientTDG)(implicit ec: ExecutionContext) {

  def remove(applicationStatus: Option[String])(implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[Int] = {
    for {
      userIds <- appRemovalRepo.remove(applicationStatus)
      _ <- Future.sequence(userIds.map(uid => authClient.removeUser(uid)))
    } yield userIds.size
  }
}
