/*
 * Copyright 2020 HM Revenue & Customs
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

package services.stc.handler

import com.google.inject.{ ImplementedBy, Inject }
import javax.inject.Singleton
import model.stc.DataStoreEvent
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories.stc.StcEventRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

@ImplementedBy(classOf[DataStoreEventHandlerImpl])
trait DataStoreEventHandler extends StcEventHandler[DataStoreEvent] {
  def handle(event: DataStoreEvent)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]
}

@Singleton
class DataStoreEventHandlerImpl @Inject() (eventRepository: StcEventRepository) extends DataStoreEventHandler {
  //  val eventRepository: StcEventRepository2 = repositories.stcEventMongoRepository

  override def handle(event: DataStoreEvent)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    Logger.info(s"Data store event $event")
    eventRepository.create(event)
  }
}
