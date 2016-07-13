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

package forms

import forms.Mappings._
import models.frameworks.{ Preference, Region }
import play.api.data.Form
import play.api.data.Forms._

import scala.language.implicitConversions

object SchemeLocationPreferenceForm {

  val form = Form(
    mapping(
      "regionAndlocation" -> (nonEmptyTrimmedText("location.required", 128) verifying
        ("location.required", value => value.matches("[^;]+;[^;]+"))),
      "firstScheme" -> nonEmptyTrimmedText("firstScheme.required", 64),
      "secondScheme" -> optionalTrimmedText(64)
    )(Data.apply)(Data.unapply)
  )

  case class Data(
    regionAndlocation: String,
    firstScheme: String,
    secondScheme: Option[String]
  )

  implicit def fromPreferenceToFormData(pref: Preference): Data = Data(
    regionAndlocation = s"${pref.region};${pref.location}", firstScheme = pref.firstFramework, secondScheme = pref.secondFramework
  )

  implicit def fromFormDataToPreference(data: Data): Preference = {
    val tokens = data.regionAndlocation.split(";") // the data was validated by the form
    Preference(
      region = tokens.head,
      location = tokens.tail.head, firstFramework = data.firstScheme, secondFramework = data.secondScheme
    )
  }

  val locationErr = "location.unavailable"
  val firstErr = "firstScheme.unavailable"
  val secondDuplicateErr = "secondScheme.duplicate"
  val secondErr = "secondScheme.unavailable"

  def resetPreference(preference: Preference, errs: Seq[String]) = List(
    errs.contains(locationErr) -> { pref: Preference => pref.copy(location = "") },
    errs.contains(firstErr) -> { pref: Preference => pref.copy(firstFramework = "", secondFramework = None) },
    errs.contains(secondErr) -> { pref: Preference => pref.copy(secondFramework = None) }
  ).foldLeft(preference) {
      case (p, (true, f)) => f(p)
      case (p, _) => p
    }

  def validateSchemeLocation(preference: Preference, regions: List[Region]): Seq[String] = {
    regions.find(_.name == preference.region).flatMap { region =>
      region.locations.find(_.name == preference.location).map { loc =>
        import preference._
        if (Some(firstFramework) == secondFramework) {
          Seq(secondDuplicateErr)
        } else {
          (loc.frameworks.contains(firstFramework), secondFramework.map(loc.frameworks.contains(_))) match {
            case (true, Some(true)) => Seq()
            case (true, None) => Seq()
            case (true, Some(false)) => Seq(secondErr)
            case (false, Some(true)) => Seq(firstErr)
            case (false, Some(false)) => Seq(firstErr, secondErr)
            case (false, None) => Seq(firstErr)
          }
        }
      }
    }.getOrElse(Seq(locationErr))
  }

}
