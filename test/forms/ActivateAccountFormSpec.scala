package forms

import org.scalatestplus.play.PlaySpec

class ActivateAccountFormSpec extends PlaySpec {

  import ActivateAccountForm.{ form => activateAccountForm }

  "Activate Account form" should {
    "be valid for token with 7 characters" in {
      val form = activateAccountForm.bind(Map("activation" -> "ABCDEFG"))
      form.hasErrors must be(false)
      form.hasGlobalErrors must be(false)
    }

    "be invalid for too short token" in {
      val form = activateAccountForm.bind(Map("activation" -> "A"))
      form.hasErrors must be(true)
      form.hasGlobalErrors must be(false)
      form.errors.map(_.message) must be(List("activation.wrong-format"))
    }

    "be invalid for too too long token" in {
      val form = activateAccountForm.bind(Map("activation" -> "ABCDEFGH"))
      form.hasErrors must be(true)
      form.hasGlobalErrors must be(false)
      form.errors.map(_.message) must be(List("error.maxLength", "activation.wrong-format"))
    }
  }

}
