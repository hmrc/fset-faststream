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

package controllers

import services.fsacscores.FSACScoresService
import uk.gov.hmrc.play.microservice.controller.BaseController

object FSACScoresController extends FSACScoresController {
  val fsacScoresService: FSACScoresService = FSACScoresService
  /*val eventsService: EventsService = EventsService
  val locationsAndVenuesRepository: LocationsWithVenuesRepository = LocationsWithVenuesInMemoryRepository
  val assessorAllocationService: AssessorAllocationService = AssessorAllocationService*/
}

trait FSACScoresController extends BaseController {
  val fsacScoresService: FSACScoresService
  /*
  def eventsService: EventsService

  def locationsAndVenuesRepository: LocationsWithVenuesRepository

  def assessorAllocationService: AssessorAllocationService
*/
}
