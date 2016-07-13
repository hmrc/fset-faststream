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

package model

import java.io.Serializable

import model.Commands.AssistanceDetailsExchange
import model.PersistedObjects.PersonalDetails
import repositories.FrameworkRepository.Region
// scalastyle:off cyclomatic.complexity
case class ApplicationValidator(gd: PersonalDetails, ad: AssistanceDetailsExchange, sl: Option[Preferences], availableRegions: List[Region]) {

  def validate: Boolean = validateGeneralDetails && validateAssistanceDetails && validateSchemes

  def validateGeneralDetails: Boolean =
    !(gd.firstName.isEmpty || gd.lastName.isEmpty || gd.preferredName.isEmpty)

  def validateAssistanceDetails: Boolean = {

    def ifNeeds(value: Option[String])(f: AssistanceDetailsExchange => Boolean) = value match {
      case Some("Yes") => f(ad)
      case _ => true
    }

    val ifNeedsAssistance = ifNeeds(Some(ad.needsAssistance)) _
    val ifNeedsAdjustment = ifNeeds(ad.needsAdjustment) _

    def hasAtLeastOneDisability(ad: AssistanceDetailsExchange): Boolean = ad.typeOfdisability match {
      case Some(x) => x.nonEmpty
      case _ => false

    }

    def hasAtLeastOneAdjustment(ad: AssistanceDetailsExchange): Boolean = ad.typeOfAdjustments match {
      case Some(x) => x.nonEmpty
      case _ => false
    }

    def hasDecidedGuaranteedInterview(ad: AssistanceDetailsExchange): Boolean = ad.guaranteedInterview match {
      case Some(_) => true
      case _ => false
    }

    ifNeedsAssistance(hasAtLeastOneDisability) && ifNeedsAdjustment(hasAtLeastOneAdjustment) &&
      ifNeedsAssistance(hasDecidedGuaranteedInterview)

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
