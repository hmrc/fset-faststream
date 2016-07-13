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

package mocks

import model.Commands.PassMarkReportQuestionnaireData
import model.PersistedObjects.PersistedQuestion
import repositories.QuestionnaireRepository

import scala.concurrent.Future

object QuestionnaireInMemoryRepository extends QuestionnaireRepository with InMemoryStorage[List[PersistedQuestion]] {

  override def addQuestions(applicationId: String, questions: List[PersistedQuestion]): Future[Unit] = {
    inMemoryRepo.put(applicationId, inMemoryRepo.getOrElse(applicationId, List()) ++ questions)
    Future.successful(())
  }

  override def notFound(applicationId: String) = throw new QuestionnaireNotFound(applicationId)

  override def findQuestions(applicationId: String): Future[Map[String, String]] = Future.successful(Map.empty[String, String])

  override def passMarkReport: Future[Map[String, PassMarkReportQuestionnaireData]] =
    Future.successful(Map.empty[String, PassMarkReportQuestionnaireData])
}

case class QuestionnaireNotFound(applicationId: String) extends Exception
