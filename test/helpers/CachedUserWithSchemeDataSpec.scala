package helpers

import org.scalatest.{ MustMatchers, WordSpec }

class CachedUserWithSchemeDataSpec extends WordSpec with MustMatchers {

  "Successful schemes for display" should {
    "Display sift greens when candidate was sifted and has ambers at assessment centre" in new TestFixture {

    }

    "Display PHASE3 greens when candidate was not sifted and has ambers at assessment centre" in new TestFixture {

    }

    "Display all greens when candidate is fastpass (has no test results) and " +
      "was not sifted and has ambers at assessment centre" in new TestFixture {

    }

    "Display current scheme status greens when no ambers are present" in new TestFixture {

    }
  }

  "Failed schemes for display" should {
    "Display sift fails when candidate was sifted and has ambers at assessment centre" in new TestFixture {

    }

    "Display PHASE3 fails when candidate was not sifted and has ambers at assessment centre" in new TestFixture {

    }

    "Display no failures when candidate is fastpass (has no test results) and " +
      "was not sifted and has ambers at assessment centre" in new TestFixture {

    }

    "Display current scheme status fails when no ambers are present" in new TestFixture {

    }
  }

  "Withdrawn schemes" should {
    "Display withdrawn schemes from the current scheme status" in new TestFixture {

    }
  }

  "Successful schemes" should {
    "Display successful schemes from the current scheme status" in new TestFixture {

    }
  }

  "Schemes for sift forms" should {
    "Display schemes from the current scheme status greens that require forms" in new TestFixture {

    }
  }

  "Number of schemes for display" should {
    "Display counts for display success/failure schemes" in new TestFixture {

    }
  }

  "Has form requirement" should {
    "Return true if any current scheme status successful schemes need form sift" in new TestFixture {

    }
  }

  "Has numeric requirement" should {
    "Return true if any current scheme status successful schemes need numeric sift" in new TestFixture {

    }
  }

  trait TestFixture {
    val sut = CachedUserWithSchemeData(

    )

  }
}
