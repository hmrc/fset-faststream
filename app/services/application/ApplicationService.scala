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

package services.application

import model.Commands.WithdrawApplicationRequest
import model.Exceptions.NotFoundException
import repositories._
import repositories.application.GeneralApplicationRepository
import services.AuditService
import services.applicationassessment.ApplicationAssessmentService

import scala.concurrent.ExecutionContext

object ApplicationService extends ApplicationService {
  val appRepository = applicationRepository
  val appAssessService = ApplicationAssessmentService
  val auditService = AuditService
}

trait ApplicationService {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val appRepository: GeneralApplicationRepository
  val appAssessService: ApplicationAssessmentService
  val auditService: AuditService

  def withdraw(applicationId: String, withdrawRequest: WithdrawApplicationRequest) = {
    appRepository.withdraw(applicationId, withdrawRequest).flatMap { result =>
      auditService.logEventNoRequest(
        "ApplicationWithdrawn",
        Map("applicationId" -> applicationId, "withdrawRequest" -> withdrawRequest.toString)
      )
      appAssessService.deleteApplicationAssessment(applicationId).recover {
        case ex: NotFoundException => {}
      }
    }
  }
}
