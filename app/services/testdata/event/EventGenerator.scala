/*
 * Copyright 2021 HM Revenue & Customs
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

package services.testdata.event

import javax.inject.{ Inject, Singleton }
import model.command.testdata.CreateEventRequest
import model.exchange.testdata.CreateEventResponse
import repositories.events._
import model.testdata.CreateEventData
import services.testdata.faker.DataFaker

import scala.concurrent.Future

//object EventGenerator extends EventGenerator {
//  override val eventsRepository = repositories.eventsRepository
//  override val locationsAndVenuesRepository = LocationsWithVenuesInMemoryRepository
//}

@Singleton
class EventGenerator @Inject() (eventsRepository: EventsRepository,
                                locationsAndVenuesRepository: LocationsWithVenuesRepository,
                                dataFaker: DataFaker
                               ) {
  import scala.concurrent.ExecutionContext.Implicits.global
  //  def eventsRepository: EventsRepository2
  //  def locationsAndVenuesRepository: LocationsWithVenuesRepository

  def allVenues = locationsAndVenuesRepository.venues.map(_.options)


  def generate(generationId: Int, createData: CreateEventData): Future[CreateEventResponse] = {
    val event = createData.toEvent
    eventsRepository.save(List(event)).map { _ => CreateEventResponse(generationId, createData) }
  }

  def createEvent(generationId: Int, request: CreateEventRequest) = {
    allVenues.flatMap { venues =>
      val data = CreateEventData.apply(request, venues, dataFaker)(generationId)
      generate(generationId, data)
    }
  }
}
