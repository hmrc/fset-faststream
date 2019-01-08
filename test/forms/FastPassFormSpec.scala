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

package forms

import forms.FastPassForm.{ form => fastPassForm, _ }
import testkit.UnitWithAppSpec

class FastPassFormSpec extends UnitWithAppSpec {

  "FastPass form" should {
    "be valid when fast pass is not applicable" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "false"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("false")
    }

    "be valid when fast pass is applicable and fast pass type is CivilServant" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "CivilServant"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("true", Some("CivilServant"))
    }

    "be valid when fast pass is applicable, fast pass type is DiversityInternship and internship type is " in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "DiversityInternship",
        "internshipTypes[0]" -> "EDIP"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("true", Some("DiversityInternship"), Some(Seq("EDIP")))
    }

    "be valid when fast pass is applicable, fast pass type is DiversityInternship, internship type is SDIPCurrentYear and " +
      "fast pass is not received" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "DiversityInternship",
        "civilServiceExperienceDetails.internshipTypes[0]" -> "SDIPCurrentYear",
        "civilServiceExperienceDetails.fastPassReceived" -> "false"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("true", Some("DiversityInternship"), Some(Seq("SDIPCurrentYear")), Some(false))
    }

    "be valid when fast pass is applicable, fast pass type is DiversityInternship, internship type is SDIPCurrentYear, " +
      "fast pass is received and has a valid certificate number" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "DiversityInternship",
        "civilServiceExperienceDetails.internshipTypes[0]" -> "SDIPCurrentYear",
        "civilServiceExperienceDetails.fastPassReceived" -> "true",
        "civilServiceExperienceDetails.certificateNumber" -> "1234567"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("true", Some("DiversityInternship"), Some(Seq("SDIPCurrentYear")), Some(true), Some("1234567"))
    }
  }

  "FastPass form" should {
    "be valid and must discard fast pass type" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "false",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "CivilServant"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("false")
    }

    "be valid and must discard internship type" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "CivilServant",
        "civilServiceExperienceDetails.internshipTypes[0]" -> "EDIP"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("true", Some("CivilServant"))
    }

    "be valid and must discard certificate number" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "DiversityInternship",
        "civilServiceExperienceDetails.internshipTypes[0]" -> "SDIPCurrentYear",
        "civilServiceExperienceDetails.fastPassReceived" -> "false",
        "civilServiceExperienceDetails.certificateNumber" -> "1234567"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("true", Some("DiversityInternship"), Some(Seq("SDIPCurrentYear")), Some(false))
    }

    "be valid for sdip faststream candidate who specifies applicable and civil service experience only" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "DiversityInternship",
        "applicationRoute" -> "SdipFaststream"
         ))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data(applicable = "true", civilServiceExperienceType = Some("DiversityInternship"),
        internshipTypes = None, fastPassReceived = None, certificateNumber = None)
    }
  }

  "FastPass form" should {
    "be invalid when fast pass is applicable and fast pass type is not supplied" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when fast pass is applicable, fast pass type is DiversityInternship and internship type is not supplied" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "DiversityInternship"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when fast pass is applicable, fast pass type is DiversityInternship, internship type is SDIPCurrentYear " +
      "and fast pass received is not supplied" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "DiversityInternship",
        "civilServiceExperienceDetails.internshipTypes[0]" -> "SDIPCurrentYear"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when fast pass is applicable, fast pass type is DiversityInternship, internship type is SDIPCurrentYear, " +
      "fast pass is received and certificate number is not supplied" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "DiversityInternship",
        "civilServiceExperienceDetails.internshipTypes[0]" -> "SDIPCurrentYear",
        "civilServiceExperienceDetails.fastPassReceived" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid for sdip faststream candidate if civil service experience is not specified" in {
      val form = fastPassForm.bind(Map("civilServiceExperienceDetails.applicable" -> "true",
        "applicationRoute" -> "SdipFaststream"
         ))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }
  }

  "cleanup fast pass params" should {
    "remove other fields when fast pass is not applicable" in {
      val data = Map("civilServiceExperienceDetails.applicable" -> "false",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "DiversityInternship",
        "civilServiceExperienceDetails.internshipTypes[0]" -> "SDIPCurrentYear")
      data.cleanupFastPassFields mustBe Map("civilServiceExperienceDetails.applicable" -> "false")
    }

    "remove internship types when fast pass type is CivilServant" in {
      val data = Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "CivilServant",
        "civilServiceExperienceDetails.internshipTypes[0]" -> "SDIPCurrentYear")
      data.cleanupFastPassFields must contain theSameElementsAs
        Map("civilServiceExperienceDetails.applicable" -> "true", "civilServiceExperienceDetails.civilServiceExperienceType" -> "CivilServant")
    }

    "remove certificate number when fast pass is not received" in {
      val data = Map("civilServiceExperienceDetails.applicable" -> "true",
        "civilServiceExperienceDetails.civilServiceExperienceType" -> "DiversityInternship",
        "civilServiceExperienceDetails.internshipTypes[0]" -> "SDIPCurrentYear", "fastPassReceived" -> "false", "certificateNumber" -> "1234567")
      data.cleanupFastPassFields must contain theSameElementsAs
        Map("civilServiceExperienceDetails.applicable" -> "true",
          "civilServiceExperienceDetails.civilServiceExperienceType" -> "DiversityInternship",
          "civilServiceExperienceDetails.internshipTypes[0]" -> "SDIPCurrentYear", "fastPassReceived" -> "false")
    }
  }
}
