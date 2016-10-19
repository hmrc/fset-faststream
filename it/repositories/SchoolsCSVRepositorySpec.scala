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

  "Parse line" should {
    "return 10 columns" in {
      val result = SchoolsCSVRepository.parseLine("0,1,2,3,4,5,6,7,8,9")
      result mustBe Array("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    }

    "parse line with quotation and comma correctly" in {
      val result = SchoolsCSVRepository.parseLine("0,\"1-1, 1-2\",2,3,4,5,6,7,8,9")
      result mustBe Array("0", "1-1, 1-2", "2", "3", "4", "5", "6", "7", "8", "9")
    }

    "throw an exception when there is more than one column with quotation" in {
      intercept[IllegalArgumentException] {
        SchoolsCSVRepository.parseLine("0,\"1-1, 1-2\",2,3,4,\"5-1 5-2\",6,7,8,9")
      }
    }

    "throw an exception when there is more column than 10" in {
      intercept[IllegalArgumentException] {
        SchoolsCSVRepository.parseLine("0,1,2,3,4,5,6,7,8,9,10")
      }
    }

    "throw an exception when there is illegal character around quotation: |" in {
      intercept[IllegalArgumentException] {
        SchoolsCSVRepository.parseLine("0,1,2,3,\"4 ,| \",5,6,7,8,9")
      }
    }

  }

}
