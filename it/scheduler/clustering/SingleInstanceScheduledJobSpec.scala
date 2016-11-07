package scheduler.clustering

import testkit.MongoRepositorySpec

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

class SingleInstanceScheduledJobSpec extends MongoRepositorySpec {
  val collectionName = "locks"
  "SingeInstanceScheduledJob isRunning" should {
    "be true when executing" in {
      val promise = Promise[Unit]
      val aLongTime = Duration(100, SECONDS)

      val job = new SingleInstanceScheduledJob {
        val lockId = "test lock id"
        val forceLockReleaseAfter = aLongTime
        def name = "Test Lock"

        def initialDelay = Duration(200, MILLISECONDS)
        def interval = aLongTime

        implicit val ec: ExecutionContext = global

        def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
          isRunning.futureValue mustBe true
          promise.future
        }
      }

      job.isRunning.futureValue mustBe false
      promise.success(())
      job.isRunning.futureValue mustBe false
    }
  }
}
