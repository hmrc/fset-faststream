package model

import org.scalatestplus.play.PlaySpec
import services.testdata.ApplicationStatusOnlyForTest

class ProgressStatusesSpec extends PlaySpec {

  // scalastyle:off
  "Progress statuses" should {
    "be assigned to all application statuses" in {
      object JustForTest extends Enumeration with ApplicationStatusOnlyForTest {
        type ApplicationStatus = Value
      }

      val excludedApplicationStatuses = JustForTest.values.map(_.toString).toSeq
      val allAppStatusesAssignedToProgressStatuses: Seq[String] = ProgressStatuses.allStatuses.map(_.applicationStatus.toString).sorted
      val allAppStatuses: Seq[String] = ApplicationStatus.values.map(_.toString).toSeq diff excludedApplicationStatuses

      allAppStatuses.foreach { appStatus =>
        allAppStatusesAssignedToProgressStatuses must contain(appStatus)
      }

      allAppStatuses.size mustBe allAppStatuses.size
    }
  }
}
