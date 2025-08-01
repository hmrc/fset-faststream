/*
 * Copyright 2023 HM Revenue & Customs
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

package model

import java.io.Serializable

import model.persisted.{ AssistanceDetails, PersonalDetails }
import repositories.FrameworkRepository.Region
// scalastyle:off cyclomatic.complexity
case class ApplicationValidator(pd: PersonalDetails, ad: AssistanceDetails, sl: Option[Preferences], availableRegions: List[Region]) {

  def validate: Boolean = validateGeneralDetails && validateAssistanceDetails && validateSchemes

  def validateGeneralDetails: Boolean =
    !(pd.firstName.isEmpty || pd.lastName.isEmpty || pd.preferredName.isEmpty)

  def validateAssistanceDetails: Boolean = {

    def ifNeeds(value: Option[Boolean], f: AssistanceDetails => Boolean) = value match {
      case Some(true) => f(ad)
      case _ => true // True indicates validation is valid
    }

    def hasVenueAdjustmentDescription(ad: AssistanceDetails): Boolean = ad.needsSupportAtVenueDescription match {
      case Some(x) => x.nonEmpty
      case _ => false
    }

    ifNeeds(ad.needsSupportAtVenue, hasVenueAdjustmentDescription)
  }

  def validateSchemes: Boolean = {

    def preferenceToPair(locationPreference: LocationPreference) = locationPreference.secondFramework match {
      case Some(framework) => List(
        (locationPreference.region, locationPreference.location, locationPreference.firstFramework),
        (locationPreference.region, locationPreference.location, framework)
      )
      case None => List((locationPreference.region, locationPreference.location, locationPreference.firstFramework))
    }

    val validPairs: List[(String, String, String)] = for {
      region <- availableRegions
      location <- region.locations
      framework <- location.frameworks
    } yield {
      (region.name, location.name, framework.name)
    }

    val allPreferencesToPairs: List[Serializable] = sl.map { preference =>
      preferenceToPair(preference.firstLocation) ++ preference.secondLocation.map(pref => preferenceToPair(pref)).getOrElse(List())
    }.getOrElse(List())

    allPreferencesToPairs.forall { p =>
      validPairs.contains(p)
    }

  }
  // scalastyle:on cyclomatic.complexity
}
