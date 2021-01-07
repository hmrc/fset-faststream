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

package mocks

import scala.collection.mutable
import scala.concurrent.Future

trait InMemoryStorage[A] {

  def notFound(applicationId: String): A

  def update(applicationId: String, userId: String, value: A): Future[Unit] = {
    inMemoryRepo += applicationId -> value
    Future.successful(())
  }

  def update(applicationId: String, value: A): Future[Unit] = {
    inMemoryRepo += applicationId -> value
    Future.successful(())
  }

  def find(applicationId: String): Future[A] = Future.successful {
    inMemoryRepo.get(applicationId) match {
      case Some(assistanceDetails) => assistanceDetails
      case None => notFound(applicationId)
    }
  }

  val inMemoryRepo = new mutable.HashMap[String, A]
}
