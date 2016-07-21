package forms

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec

class GeneralDetailsFormSpec extends PlaySpec {
  implicit val now = LocalDate.now

  import GeneralDetailsForm.{form => personalDetailsForm}

  "Personal Details form" should {
    "be invalid for missing mandatory fields" in {
      val form = personalDetailsForm.bind(Map[String, String]())
      form.hasErrors must be(true)
      form.hasGlobalErrors must be(false)
      form.errors.map(_.message) must be(
        List("error.firstName",
          "error.lastName",
          "error.preferredName",
          "error.required",
          "error.required",
          "error.required",
          "error.address.required",
          "error.required",
          "error.phone.required",
          "aleveld.required",
          "alevel.required"))
    }
  }
}
