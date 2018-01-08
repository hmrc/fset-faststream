/*
 * Copyright 2018 HM Revenue & Customs
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

package model.report

object DiversityReportItemExamples {
  val AllFields1 = DiversityReportItem(
    ApplicationForDiversityReportItemExamples.Example1,
    Some(QuestionnaireReportItemExamples.NoParentOccupation1),
    Some(MediaReportItemExamples.Example1)
  )
  val AllFields2 = DiversityReportItem(
    ApplicationForDiversityReportItemExamples.Example2,
    Some(QuestionnaireReportItemExamples.NoParentOccupation2),
    Some(MediaReportItemExamples.Example2)
  )

  val OnlyApplication1 =DiversityReportItem(
    ApplicationForDiversityReportItemExamples.Example1,
    None,
    None)
  val OnlyApplication2 =DiversityReportItem(
    ApplicationForDiversityReportItemExamples.Example2,
    None,
    None)

  val OnlyApplicationAndQuestionnaire1 =DiversityReportItem(
    ApplicationForDiversityReportItemExamples.Example1,
    Some(QuestionnaireReportItemExamples.NoParentOccupation1),
    None)
  val OnlyApplicationAndQuestionnaire2 =DiversityReportItem(
    ApplicationForDiversityReportItemExamples.Example2,
    Some(QuestionnaireReportItemExamples.NoParentOccupation2),
    None)

  val OnlyApplicationAndMedia1 =DiversityReportItem(
    ApplicationForDiversityReportItemExamples.Example1,
    None,
    Some(MediaReportItemExamples.Example1))
  val OnlyApplicationAndMedia2 =DiversityReportItem(
    ApplicationForDiversityReportItemExamples.Example2,
    None,
    Some(MediaReportItemExamples.Example2))
}
