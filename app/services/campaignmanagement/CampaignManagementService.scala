/*
 * Copyright 2018 HM Revenue & Customs
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

package services.campaignmanagement

import java.util.UUID

import factories.UUIDFactory
import model.exchange.campaignmanagement.{ AfterDeadlineSignupCode, AfterDeadlineSignupCodeUnused }
import model.persisted.CampaignManagementAfterDeadlineCode
import org.joda.time.DateTime
import repositories.campaignManagementAfterDeadlineSignupCodeRepository
import repositories.campaignmanagement.CampaignManagementAfterDeadlineSignupCodeRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object CampaignManagementService extends CampaignManagementService{
  val afterDeadlineCodeRepository: CampaignManagementAfterDeadlineSignupCodeRepository = campaignManagementAfterDeadlineSignupCodeRepository
  val uuidFactory = UUIDFactory
}

trait CampaignManagementService {
  val afterDeadlineCodeRepository: CampaignManagementAfterDeadlineSignupCodeRepository
  val uuidFactory: UUIDFactory

  def afterDeadlineSignupCodeUnusedAndValid(code: String): Future[AfterDeadlineSignupCodeUnused] = {
    afterDeadlineCodeRepository.findUnusedValidCode(code).map(storedCodeOpt =>
      AfterDeadlineSignupCodeUnused(storedCodeOpt.isDefined, storedCodeOpt.map(_.expires))
    )
  }

  def markSignupCodeAsUsed(code: String, applicationId: String): Future[Unit] = {
    afterDeadlineCodeRepository.markSignupCodeAsUsed(code, applicationId).map(_ => ())
  }

  def generateAfterDeadlineSignupCode(createdByUserId: String, expiryInHours: Int): Future[AfterDeadlineSignupCode] = {
    val newCode = CampaignManagementAfterDeadlineCode(
        uuidFactory.generateUUID(),
      createdByUserId,
      DateTime.now().plusHours(expiryInHours)
    )

    afterDeadlineCodeRepository.save(newCode).map { _ =>
      AfterDeadlineSignupCode(newCode.code)
    }
  }
}
