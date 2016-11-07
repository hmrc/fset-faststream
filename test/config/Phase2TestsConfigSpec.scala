package config

import org.scalatestplus.play.PlaySpec
import Phase2ScheduleExamples._

class Phase2TestsConfigSpec extends PlaySpec {

  "Schedule name by schedule id" should {
    val config = Phase2TestsConfig(10, 20, Map("daro" -> DaroShedule))

    "return schedule name by schedule id" in {
      val name = config.scheduleNameByScheduleId(DaroShedule.scheduleId)
      name mustBe "daro"
    }

    "throw an exception when no schedule id" in {
      an[IllegalArgumentException] must be thrownBy {
        config.scheduleNameByScheduleId(WardShedule.scheduleId)
      }
    }
  }

  "schedule for invigilated e-tray" should {
    "return daro" in {
      val config = Phase2TestsConfig(10, 20, Map("irad" -> IradShedule, "daro" -> DaroShedule))
      config.scheduleForInvigilatedETray mustBe DaroShedule
    }

    "throw an exception when there is no daro schedule" in {
      an[IllegalArgumentException] must be thrownBy {
        Phase2TestsConfig(10, 20, Map("irad" -> IradShedule))
      }
    }
  }
}
