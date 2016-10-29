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

package mocks.application

import mocks.InMemoryStorage
import model.Exceptions.AssistanceDetailsNotFound
import model.persisted.AssistanceDetails
import repositories.assistancedetails.AssistanceDetailsRepository

import scala.concurrent.Future

object AssistanceDetailsInMemoryRepository extends AssistanceDetailsRepository with InMemoryStorage[AssistanceDetails] {
  // Seed with test data.
  inMemoryRepo +=
    "111-111" ->
    AssistanceDetails("No", None, None, false, None, Some(false), None)

  override def notFound(applicationId: String) = throw new AssistanceDetailsNotFound(applicationId)
}
