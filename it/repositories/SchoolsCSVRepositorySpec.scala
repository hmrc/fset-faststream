package repositories

import testkit.{ ShortTimeout, UnitWithAppSpec }

class SchoolsCSVRepositorySpec extends UnitWithAppSpec with ShortTimeout {

  "Schools CSV Repository" should {
    "parse file with expected number of schools" in {
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
