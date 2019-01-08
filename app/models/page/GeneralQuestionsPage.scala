/*
 * Copyright 2019 HM Revenue & Customs
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
import forms.sift.{ GeneralQuestionsForm, PostGradDegreeInfoForm, UndergradDegreeInfoForm }
import play.api.data.Form

case class GeneralQuestionsPage(
  form : Form[GeneralQuestionsForm.Data]
)

object GeneralQuestionsPage {

  def apply(answers: Option[GeneralQuestionsAnswers]): GeneralQuestionsPage = GeneralQuestionsPage(
    answers.map { a =>
      GeneralQuestionsForm().form.fill(GeneralQuestionsForm.Data(
        multipleNationalities = Some(a.multipleNationalities),
        secondNationality= a.secondNationality,
        nationality = Option(a.nationality).filter(_.trim.nonEmpty),
        hasUndergradDegree = Some(a.undergradDegree.isDefined),
        undergradDegree = a.undergradDegree map(
          ud => UndergradDegreeInfoForm.Data(
            ud.name, Option(ud.classification).filter(_.trim.nonEmpty), Option(ud.graduationYear).filter(_.trim.nonEmpty), ud.moduleDetails)),
        hasPostgradDegree = Some(a.postgradDegree.isDefined),
        postgradDegree = a.postgradDegree map(
          pd => PostGradDegreeInfoForm.Data(
            pd.name, Option(pd.graduationYear).filter(_.trim.nonEmpty), pd.otherDetails, pd.projectDetails))
      ))
    }.getOrElse(GeneralQuestionsForm().form)
  )


}
