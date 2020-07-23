package repositories.csv

import testkit.{ ShortTimeout, UnitWithAppSpec }

class SchoolsCSVRepositorySpec extends UnitWithAppSpec with ShortTimeout {

  "Schools CSV Repository" should {
    "parse file with expected number of schools" in {
      val result = new SchoolsCSVRepository(app).schools.futureValue
      result.size mustBe 6882
    }
  }
}
