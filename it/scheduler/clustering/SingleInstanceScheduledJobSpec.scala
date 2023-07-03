package scheduler.clustering

import config.ScheduledJobConfig
import repositories.CollectionNames
import scheduler.BasicJobConfig
import testkit.MongoRepositorySpec

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

class SingleInstanceScheduledJobSpec extends MongoRepositorySpec {
  val collectionName: String = CollectionNames.LOCKS
  "SingeInstanceScheduledJob isRunning" should {
    "be true when executing" in {
      val promise = Promise[Unit]()
      val aLongTime = Duration(100, SECONDS)

      val job = new SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
        def config = ???
        override val lockId = "test lock id"
        override val forceLockReleaseAfter: FiniteDuration = aLongTime
        override def name = "Test Lock"

        override def initialDelay = Duration(200, MILLISECONDS)
        override def interval: FiniteDuration = aLongTime

        override val mongoComponent = mongo

        override implicit val ec: ExecutionContext = global

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
