package repositories

import model.AssessorNewEventsJobInfo
import org.joda.time.DateTime
import testkit.MongoRepositorySpec

class AssessorsEventsSummaryJobsRepositorySpec extends MongoRepositorySpec {
  override val collectionName: String = CollectionNames.ASSESSOR_EVENTS_SUMMARY_JOBS

  lazy val repository = repositories.assessorsEventsSummaryJobsRepository

  "AssessorsEventsSummaryJobs" should {
    "save only the last run job" in {
      val now = DateTime.now
      val futureTime = now.plusMonths(1)
      val dateTimes = Seq(now, now.plusDays(1), now.plusDays(2), now.plusDays(3), futureTime)

      dateTimes.foreach { dateTime =>
        repository.save(AssessorNewEventsJobInfo(dateTime)).futureValue mustBe unit
      }

      repository.lastRun.futureValue mustBe Option(AssessorNewEventsJobInfo(futureTime))
    }
  }
}
