package repositories.csv

import model.FSACIndicator
import testkit.{ ShortTimeout, UnitWithAppSpec }

class FSACIndicatorCSVRepositorySpec extends UnitWithAppSpec with ShortTimeout {

  "North South Indicator Repository" should {
    "parse file with expected number of post code areas" in new TestFixture {
      val result = repository.indicators
      result.size mustBe 124
    }
  }

  "calculateFsacIndicator" should {
    "return no indicator when in UK but no postcode" in new TestFixture {
      val result = repository.find(None, outsideUk = false)
      result mustBe None
    }

    "return default indicator when outside UK and no postcode" in new TestFixture {
      val result = repository.find(None, outsideUk = true)
      result mustBe Some(repository.DefaultIndicator)
    }

    "return default indicator when in UK and no postcode match is found" in new TestFixture {
      val result = repository.find(Some("BOGUS3"), outsideUk = false)
      result mustBe Some(repository.DefaultIndicator)
    }

    "return default indicator when in UK for an empty postcode " in new TestFixture {
      val result = repository.find(Some(""), outsideUk = false)
      result mustBe Some(repository.DefaultIndicator)
    }

    "ignore postcode if outside UK and return the default indicator" in new TestFixture {
      val result = repository.find(Some("OX1 4DB"), outsideUk = true)
      result mustBe Some(repository.DefaultIndicator)
    }

    "return London for Oxford postcode" in new TestFixture {
      val result = repository.find(Some("OX1 4DB"), outsideUk = false)
      result mustBe Some(FSACIndicator("Oxford", "London"))
    }

    "return London for Edinburgh postcode due to covid impact needing virtual FSACs and all postcodes mapped to London" in new TestFixture {
      val result = repository.find(Some("EH1 3EG"), outsideUk = false)
      result mustBe Some(FSACIndicator("Edinburgh", "London"))
    }

    "return London even when postcode is lowercase" in new TestFixture {
      val result = repository.find(Some("ec1v 3eg"), outsideUk = false)
      result mustBe Some(FSACIndicator("East Central london", "London"))
    }
  }

  trait TestFixture {
    val repository = new FSACIndicatorCSVRepositoryImpl(app)
  }
}
