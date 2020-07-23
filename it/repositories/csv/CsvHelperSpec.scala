package repositories.csv

import testkit.{ ShortTimeout, UnitWithAppSpec }

class CsvHelperSpec extends UnitWithAppSpec with ShortTimeout {

  "Parse line" should {
    "return 10 columns" in {
      val result = TestableCsvHelper.parseLine("0,1,2,3,4,5,6,7,8,9")
      result mustBe Array("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    }

    "parse line with quotation and comma correctly" in {
      val result = TestableCsvHelper.parseLine("0,\"1-1, 1-2\",2,3,4,5,6,7,8,9")
      result mustBe Array("0", "1-1, 1-2", "2", "3", "4", "5", "6", "7", "8", "9")
    }

    "throw an exception when there is more than one column with quotation" in {
      intercept[IllegalArgumentException] {
        TestableCsvHelper.parseLine("0,\"1-1, 1-2\",2,3,4,\"5-1 5-2\",6,7,8,9")
      }
    }

    "throw an exception when there is more column than 10" in {
      intercept[IllegalArgumentException] {
        TestableCsvHelper.parseLine("0,1,2,3,4,5,6,7,8,9,10")
      }
    }

    "throw an exception when there is illegal character around quotation: |" in {
      intercept[IllegalArgumentException] {
        TestableCsvHelper.parseLine("0,1,2,3,\"4 ,| \",5,6,7,8,9")
      }
    }
  }
}

object TestableCsvHelper extends CsvHelper {
  override def expectedNumberOfHeaders = 10
}
