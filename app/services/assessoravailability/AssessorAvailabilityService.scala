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

package services.assessoravailability

import model.Exceptions.AssessorAvailabilityNotFoundException
import repositories._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessorAvailabilityService extends AssessorAvailabilityService {
  val aaRepository: AssessorAvailabilityMongoRepository = assessorAvailabilityRepository
}

trait AssessorAvailabilityService {
  val aaRepository: AssessorAvailabilityRepository

  def save(userId: String, assessorAvailability: model.exchange.AssessorAvailability): Future[Unit] = {
    val assessorAvailabilityToPersist = model.persisted.AssessorAvailability(
      userId, assessorAvailability.availability)

    for {
      _ <- aaRepository.save(assessorAvailabilityToPersist)
    } yield {}
  }

  def find(userId: String): Future[model.exchange.AssessorAvailability] = {
    for {
      availabilityOpt <- aaRepository.find(userId)
    } yield {
      availabilityOpt.fold( throw AssessorAvailabilityNotFoundException(userId) ) {
        availability => model.exchange.AssessorAvailability(availability.userId, availability.availability)
      }
    }
  }
}
