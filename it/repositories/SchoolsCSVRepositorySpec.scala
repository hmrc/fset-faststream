package repositories

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.test.WithApplication
import testkit.ShortTimeout

class SchoolsCSVRepositorySpec extends PlaySpec with ScalaFutures with ShortTimeout {

  "Schools CSV Repository" should {
    "parse file with expected number of schools" in new WithApplication {
      val result = SchoolsCSVRepository.schools.futureValue
      result.size mustBe 6882
    }
  }

}
