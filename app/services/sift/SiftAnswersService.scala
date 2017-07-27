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

package services.sift

import model.Exceptions.SiftAnswersNotFound
import model.SchemeId
import model.persisted.{ SchemeSpecificAnswer, SiftAnswers }
import repositories.sift.SiftAnswersRepository

import scala.concurrent.Future

object SiftAnswersService extends SiftAnswersService {
  val siftAnswersRepo: SiftAnswersRepository = repositories.siftAnswersRepository
}

trait SiftAnswersService {
  def siftAnswersRepo: SiftAnswersRepository

  def addSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId, answer: SchemeSpecificAnswer): Future[Unit] = {
    siftAnswersRepo.addSchemeSpecificAnswer(applicationId, schemeId, answer)
  }

  def findSiftAnswers(applicationId: String): Future[Option[SiftAnswers]] = {
    siftAnswersRepo.findSiftAnswers(applicationId)
  }

  def findSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId) : Future[Option[SchemeSpecificAnswer]] = {
    siftAnswersRepo.findSchemeSpecificAnswer(applicationId, schemeId)
  }
}