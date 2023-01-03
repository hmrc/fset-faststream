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

package services.assistancedetails

import com.google.inject.ImplementedBy
import javax.inject.{ Inject, Singleton }
import model.exchange.AssistanceDetailsExchange
import model.persisted.AssistanceDetails
import repositories.assistancedetails.AssistanceDetailsRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[AssistanceDetailsServiceImpl])
trait AssistanceDetailsService {
  def update(applicationId: String, userId: String, updateAssistanceDetails: AssistanceDetailsExchange): Future[Unit]
  def find(applicationId: String, userId: String): Future[AssistanceDetailsExchange]
}

@Singleton
class AssistanceDetailsServiceImpl @Inject() (adRepository: AssistanceDetailsRepository) extends AssistanceDetailsService {
  override def update(applicationId: String, userId: String, updateAssistanceDetails: AssistanceDetailsExchange): Future[Unit] = {
    adRepository.update(applicationId, userId, AssistanceDetails(updateAssistanceDetails))
  }

  override def find(applicationId: String, userId: String): Future[AssistanceDetailsExchange] = {
    for {
      ad <- adRepository.find(applicationId)
    } yield AssistanceDetailsExchange(ad)
  }
}
