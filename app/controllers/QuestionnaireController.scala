/*
 * Copyright 2022 HM Revenue & Customs
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

import model.questionnaire.Question._
import model.questionnaire.Questionnaire
import play.api.mvc.ControllerComponents
import repositories._
import repositories.application.GeneralApplicationRepository
import services.AuditService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class QuestionnaireController @Inject() (cc: ControllerComponents,
                                         qRepository: QuestionnaireRepository,
                                         appRepository: GeneralApplicationRepository,
                                         auditService: AuditService
                                        ) extends BackendController(cc) {

  def addSection(applicationId: String, sectionKey: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[Questionnaire] { questionnaire =>
      for {
        _ <- qRepository.addQuestions(applicationId, questionnaire.questions.map(fromCommandToPersistedQuestion))
        _ <- appRepository.updateQuestionnaireStatus(applicationId, sectionKey)
      } yield {
        auditService.logEvent("QuestionnaireSectionSaved", Map("section" -> sectionKey))
        Accepted
      }
    }
  }
}
