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

package services.testdata.adminusers

import model.exchange.testdata.AssessorResponse
import model.exchange.testdata.{ AssessorData, CreateAdminUserStatusData }
import play.api.mvc.RequestHeader
import services.assessoravailability.AssessorService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

object AssessorCreatedStatusGenerator extends AssessorCreatedStatusGenerator {
  override val previousStatusGenerator: AdminUserBaseGenerator = AdminCreatedStatusGenerator
  override val assessorService = AssessorService
}

trait AssessorCreatedStatusGenerator extends AdminUserConstructiveGenerator {

  import scala.concurrent.ExecutionContext.Implicits.global

  val assessorService: AssessorService


  def generate(generationId: Int, createData: CreateAdminUserStatusData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    previousStatusGenerator.generate(generationId, createData).flatMap { userInPrevStatus =>
      createData.assessor match {
        case Some(assessor) =>
          createAssessor(userInPrevStatus.userId, assessor).map { assessorStored =>
            userInPrevStatus.copy(assessor =
              Some(AssessorResponse.apply((assessorStored))))
          }
        case None => Future.successful(userInPrevStatus)
      }
    }
  }

  def createAssessor(userId: String, assessor: AssessorData)(
    implicit hc: HeaderCarrier): Future[model.exchange.Assessor] = {
    val assessorE = model.exchange.Assessor(userId, assessor.skills, assessor.civilServant)
    assessorService.saveAssessor(userId, assessorE).map(_ => assessorE)
  }

}
