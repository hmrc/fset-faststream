package connectors.exchange

import org.joda.time.{DateTime, Period}
import org.joda.time.format.{DateTimeFormatterBuilder, PeriodFormatterBuilder}

abstract class CubiksTestProfile extends TestProfile[CubiksTest] {
  def hasNotResultReadyToDownloadForAllTestsYet =  activeTests.exists(!_.resultsReadyToDownload)
}

trait TestProfile[T <: Test] {
  def expirationDate: DateTime
  def tests: List[T]
  def activeTests = tests filter (_.usedForResults)
  def hasNotStartedYet = activeTests.forall(_.startedDateTime.isEmpty)
  def hasNotCompletedYet =  activeTests.exists(_.completedDateTime.isEmpty)

  def getDuration: String = {
    val now = DateTime.now
    val date = this.expirationDate

    val periodFormat = new PeriodFormatterBuilder().
      printZeroAlways().
      appendDays().
      appendSuffix(" day ", " days ").
      appendSeparator(" and ").
      appendHours().
      appendSuffix(" hour ", " hours ").
      toFormatter

    val period = new Period(now, date)
    periodFormat print period
  }

    def getExpireDateTime: String = {

    val dateTimeFormat = new DateTimeFormatterBuilder().
      appendClockhourOfHalfday(1).
      appendLiteral(":").
      appendMinuteOfHour(2).
      appendHalfdayOfDayText().
      appendLiteral(" on ").
      appendDayOfMonth(1).
      appendLiteral(" ").
      appendMonthOfYearText().
      appendLiteral(" ").
      appendYear(4, 4).
      toFormatter

    dateTimeFormat.print(expirationDate)
  }

  def getExpireDate: String = {

    val dateTimeFormat = new DateTimeFormatterBuilder().
      appendDayOfMonth(1).
      appendLiteral(" ").
      appendMonthOfYearText().
      appendLiteral(" ").
      appendYear(4, 4).
      toFormatter

    dateTimeFormat.print(expirationDate)
  }
}

trait Test {
  def usedForResults: Boolean
  def token: String
  def startedDateTime: Option[DateTime]
  def completedDateTime: Option[DateTime]
}
