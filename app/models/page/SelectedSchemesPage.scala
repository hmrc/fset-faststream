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

package models.page

import connectors.exchange.referencedata.Scheme

case class SelectedSchemesPage(allSchemes: Seq[Scheme]) {

  val visibleSchemes = allSchemes.filterNot(scheme => Seq("Sdip", "Edip").contains(scheme.id.value))

  def getValidSchemesByPriority(formData: Map[String, String]) = {
    val selectedSchemes = getSchemesByPriority(formData)
    val invalidSchemes = getInvalidSchemes(selectedSchemes)
    selectedSchemes.filterNot(schemeId => invalidSchemes.contains(schemeId))
  }

  val getInvalidSchemes = (selectedSchemes: List[String]) => selectedSchemes.diff(allSchemes.map(_.id.value))

  def getSchemesByPriority(formData: Map[String, String]) = {
    val validSchemeParamsFunc = (name: String, value: String) => name.startsWith("scheme_") && value.nonEmpty
    val extractPriorityNumberFunc: String => Int = _.split("_").last.toInt

    formData.filter { case (name, scheme) => validSchemeParamsFunc(name, scheme) }
      .collect { case (name, scheme) => extractPriorityNumberFunc(name) -> scheme }
      .toList
      .sortBy { case (priority, _) => priority }
      .map { case (_, scheme) => scheme }
      .distinct
  }
}
