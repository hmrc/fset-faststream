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

package services.testdata.admin

import com.google.inject.name.Named

import javax.inject.{Inject, Singleton}
import model.exchange.AssessorAvailabilities
import model.exchange.testdata.CreateAdminResponse.{AssessorResponse, CreateAdminResponse}
import model.persisted.assessor.AssessorStatus
import model.testdata.CreateAdminData.{AssessorData, CreateAdminData}
import play.api.mvc.RequestHeader
import services.assessor.AssessorService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

//object AssessorCreatedStatusGenerator extends AssessorCreatedStatusGenerator {
//  override val previousStatusGenerator: AdminUserBaseGenerator = AdminCreatedStatusGenerator
//  override val assessorService = AssessorService
//}

@Singleton
class AssessorCreatedStatusGenerator @Inject() (@Named("AdminCreatedStatusGenerator") val previousStatusGenerator: AdminUserBaseGenerator,
                                                assessorService: AssessorService)(
  implicit ec: ExecutionContext) extends AdminUserConstructiveGenerator {

//  override val previousStatusGenerator: AdminUserBaseGenerator = AdminCreatedStatusGenerator

//  val assessorService: AssessorService

  def generate(generationId: Int, createData: CreateAdminData)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateAdminResponse] = {
    previousStatusGenerator.generate(generationId, createData).flatMap { userInPreviousStatus =>
      createData.assessor.map { assessorData =>
        val userId = userInPreviousStatus.userId
        for {
          assessorPersisted <- createAssessor(userId, assessorData)
          availability = assessorData.availability.getOrElse(Set.empty)
          _ <- if (assessorData.status == AssessorStatus.AVAILABILITIES_SUBMITTED) {
            val aa = AssessorAvailabilities(assessorPersisted.userId, assessorPersisted.version, availability)
            assessorService.saveAvailability(aa)
          } else {
            Future.successful(())
          }
        } yield {
          userInPreviousStatus.copy(assessor = Some(AssessorResponse.apply(assessorPersisted).copy(availability = availability)))
        }
      }.getOrElse(Future.successful(userInPreviousStatus))
    }
  }

  def createAssessor(userId: String, assessor: AssessorData)(
    implicit hc: HeaderCarrier): Future[model.exchange.Assessor] = {
    val assessorE = model.exchange.Assessor(userId, None, assessor.skills, assessor.sifterSchemes, assessor.civilServant, assessor.status)
    assessorService.saveAssessor(userId, assessorE).map(_ => assessorE)
  }
}
