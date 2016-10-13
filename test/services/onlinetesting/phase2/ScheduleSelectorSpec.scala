package services.onlinetesting.phase2

import config.Phase2ScheduleExamples._
import config.{ Phase2Schedule, Phase2TestsConfig }
import org.scalatestplus.play.PlaySpec

class ScheduleSelectorSpec extends PlaySpec {

  "get random schedule" should {
    "throw an exception when no schedules are configured" in {
      val selector = createSelector(List())

      intercept[IllegalArgumentException] {
        selector.getRandomSchedule
      }
    }

    "return a schedule" in {
      val schedules = List(DaroShedule, IradShedule, WardShedule)
      val selector = createSelector(schedules)
      val randomSchedules = 1 to 1000 map (_ => selector.getRandomSchedule)

      randomSchedules.distinct must contain theSameElementsAs schedules
    }
  }

  private def createSelector(schedules: List[Phase2Schedule]): ScheduleSelector = new ScheduleSelector {
    def testConfig = Phase2TestsConfig(1, schedules)
  }

}
