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

package mocks

import model.PersistedObjects.{ PersistedAnswer, PersistedQuestion }
import model.report.QuestionnaireReportItem
import repositories.QuestionnaireRepository

import scala.concurrent.Future

object QuestionnaireInMemoryRepository extends QuestionnaireRepository with InMemoryStorage[List[PersistedQuestion]] {

  override def addQuestions(applicationId: String, questions: List[PersistedQuestion]): Future[Unit] = {
    inMemoryRepo.put(applicationId, inMemoryRepo.getOrElse(applicationId, List()) ++ questions)
    Future.successful(())
  }

  override def notFound(applicationId: String) = throw QuestionnaireNotFound(applicationId)

  override def findQuestions(applicationId: String): Future[Map[String, PersistedAnswer]] =
    Future.successful(Map.empty[String, PersistedAnswer])

  override def findAllForDiversityReport: Future[Map[String, QuestionnaireReportItem]] =
    Future.successful(Map.empty[String, QuestionnaireReportItem])

  override def findForOnlineTestPassMarkReport: Future[Map[String, QuestionnaireReportItem]] =
    Future.successful(Map.empty[String, QuestionnaireReportItem])
}

case class QuestionnaireNotFound(applicationId: String) extends Exception
