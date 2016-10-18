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

package services.onlinetesting

import connectors.CSREmailClient
import model.persisted.ExpiringOnlineTest
import model.ProgressStatuses.PHASE1_TESTS_EXPIRED
import play.api.Logger
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase1TestRepository
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

@deprecated("remaining methods to be moved in phase1/2 services")
trait OnlineTestExpiryService {

}

class OnlineTestExpiryServiceImpl(
  appRepository: GeneralApplicationRepository,
  otRepository: Phase1TestRepository,
  cdRepository: ContactDetailsRepository,
  emailClient: CSREmailClient,
  auditService: AuditService,
  newHeaderCarrier: => HeaderCarrier
)(implicit executor: ExecutionContext) extends OnlineTestExpiryService {

  private implicit def headerCarrier = newHeaderCarrier


}

object OnlineTestExpiryService extends OnlineTestExpiryServiceImpl(
applicationRepository, phase1TestRepository, contactDetailsRepository, CSREmailClient, AuditService, HeaderCarrier()
)