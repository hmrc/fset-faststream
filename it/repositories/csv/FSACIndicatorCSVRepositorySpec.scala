package repositories.csv

import model.FSACIndicator
import model.report.CandidateProgressReportItem
import model.{ ApplicationRoute, SchemeId }
import testkit.{ ShortTimeout, UnitWithAppSpec }

class FSACIndicatorCSVRepositorySpec extends UnitWithAppSpec with ShortTimeout {

  import TestFixture._

  "North South Indicator Repository" should {
    "parse file with expected number of post code areas" in {
      val result = FSACIndicatorCSVRepository.indicators
      result.size mustBe 124
    }
  }

  "calculateFsacIndicator" should {
    "return no indicator when in UK but no postcode" in {
      val result = FSACIndicatorCSVRepository.find(None, outsideUk = false)
      result mustBe None
    }
    "return default indicator when outside UK and no postcode" in {
      val result = FSACIndicatorCSVRepository.find(None, outsideUk = true)
      result mustBe Some(FSACIndicatorCSVRepository.DefaultIndicator)
    }
    "return default indicator when in UK and no postcode match is found" in {
      val result = FSACIndicatorCSVRepository.find(Some("BOGUS3"), outsideUk = false)
      result mustBe Some(FSACIndicatorCSVRepository.DefaultIndicator)
    }
    "return default indicator when in UK for an empty postcode " in {
      val result = FSACIndicatorCSVRepository.find(Some(""), outsideUk = false)
      result mustBe Some(FSACIndicatorCSVRepository.DefaultIndicator)
    }
    "ignore postcode if outside UK and return the default indicator" in {
      val result = FSACIndicatorCSVRepository.find(Some("OX1 4DB"), outsideUk = true)
      result mustBe Some(FSACIndicatorCSVRepository.DefaultIndicator)
    }
    "return London for Oxford postcode" in {
      val result = FSACIndicatorCSVRepository.find(Some("OX1 4DB"), outsideUk = false)
      result mustBe Some(FSACIndicator("Oxford", "London"))
    }
    "return London for Edinburgh postcode due to covid impact needing virtual FSACs and all postcodes mapped to London" in {
      val result = FSACIndicatorCSVRepository.find(Some("EH1 3EG"), outsideUk = false)
      result mustBe Some(FSACIndicator("Edinburgh", "London"))
    }
    "return London even when postcode is lowercase" in {
      val result = FSACIndicatorCSVRepository.find(Some("ec1v 3eg"), outsideUk = false)
      result mustBe Some(FSACIndicator("East Central london", "London"))
    }
  }
}

object TestFixture {

  val CandidateProgressReportItemFaststreamBase = CandidateProgressReportItem("user123", "app123", Some("submitted"),
    List(SchemeId("DiplomaticService"), SchemeId("GovernmentOperationalResearchService")), disability = Some("Yes"),
    onlineAdjustments = Some("No"), assessmentCentreAdjustments = Some("No"), phoneAdjustments = None, gis = Some("No"),
    civilServant = Some("No"), edip = Some("No"), sdip = Some("No"), otherInternship = Some("No"), fastPassCertificate = Some("1234567"),
    assessmentCentre = None, ApplicationRoute.Faststream)
  val CandidateProgressReportItemFaststream = CandidateProgressReportItemFaststreamBase.copy(applicationRoute = ApplicationRoute.Faststream)
  val CandidateProgressReportItemEdip = CandidateProgressReportItemFaststreamBase.copy(applicationRoute = ApplicationRoute.Edip)
  val CandidateProgressReportItemRegistered = CandidateProgressReportItemFaststreamBase.copy(progress = Some("registered"))
}
