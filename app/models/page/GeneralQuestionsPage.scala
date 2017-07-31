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

package models.page

import connectors.exchange.sift.GeneralQuestionsAnswers
import forms.sift.GeneralQuestionsForm
import play.api.data.Form

case class GeneralQuestionsPage(
  form : Form[GeneralQuestionsForm.Data]
)

object GeneralQuestionsPage {

  def apply(answers: Option[GeneralQuestionsAnswers]): GeneralQuestionsPage = GeneralQuestionsPage(
    answers.map { a =>
      GeneralQuestionsForm().form.fill(GeneralQuestionsForm.Data(
        multiplePassports = a.multiplePassports,
        secondPassportCountry = a.secondPassportCountry,
        passportCountry = Option(a.passportCountry).filter(_.trim.nonEmpty),
        hasUndergradDegree = a.undergradDegree.isDefined,
        undergradDegree = a.undergradDegree,
        hasPostgradDegree = a.postgradDegree.isDefined,
        postgradDegree = a.postgradDegree
      ))
    }.getOrElse(GeneralQuestionsForm().form)
  )


}
