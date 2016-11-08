package model

import model.OnlineTestApplicationExamples._
import org.scalatestplus.play.PlaySpec

class OnlineTestApplicationSpec extends PlaySpec {

  "Online Test" should {
    "be invigilated e-tray when needs online adjustments and invigilated e-tray are set" in {
      val test = OnlineTest.copy(
        needsOnlineAdjustments = true,
        eTrayAdjustments = Some(AdjustmentDetail(invigilatedInfo = Some("invigilated e-tray")))
      )
      test.isInvigilatedETray mustBe true
    }

    "be not invigilated e-tray when online adjustments are not set" in {
      val test = OnlineTest.copy(needsOnlineAdjustments = false)
      test.isInvigilatedETray mustBe false
    }

    "be not invigilated e-tray when invigilated e-tray info is not set" in {
      val test = OnlineTest.copy(needsOnlineAdjustments = true)
      test.isInvigilatedETray mustBe false
    }
  }
}
