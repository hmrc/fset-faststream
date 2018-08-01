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

package models.view.questionnaire

object ParentQualifications {
  // The first data item in the tuple specifies what the generated id will be for the widget
  // eg. id="parentsDegree-degree", id="parentsDegree-below-degree" etc
  // The second data item is the text to display next to the field.
  // The third data item specifies whether selecting the field should trigger displaying another control
  val seq = Seq(
    ("degree", "Degree level qualification", false),
    ("below-degree", "Qualifications below degree level", false),
    ("none", "No formal qualifications", false),
    ("unknown", "I don't know/prefer not to say", false)
  )
}
