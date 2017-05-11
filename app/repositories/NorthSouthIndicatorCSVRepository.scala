/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package repositories

import com.github.ghik.silencer.silent
import model.report.CandidateProgressReportItem
import model.{ ApplicationRoute, FSACIndicator }
import play.api.Play
import resource._

import scala.io.Source

object NorthSouthIndicatorCSVRepository extends NorthSouthIndicatorCSVRepository {
  private val CsvFileName = "North_South_indicator_lookup_for_FSAC.csv"
  override def expectedNumberOfHeaders = 3

  import play.api.Play.current

  override private[repositories] val fsacIndicators: Map[String, FSACIndicator] =  {

    @silent val input = managed(Play.application.resourceAsStream(CsvFileName).get)
    input.acquireAndGet { inputStream =>
      val rawData = Source.fromInputStream(inputStream).getLines.map(parseLine).toList
      val headers = rawData.head
      val values = rawData.tail

      def toMap(m: Map[String, FSACIndicator], line: Array[String]): Map[String, FSACIndicator] = {
        require(headers.length == line.length,
          s"Number of columns must be equal to number of headers. Incorrect line: ${line.mkString("|")}")
        m + ((line(0), FSACIndicator(line(1), line(2))))
      }

      values.foldLeft(Map.empty[String, FSACIndicator])((acc, line) => toMap(acc, line))
    }
  }

  override def calculateFsacIndicator(postcode: Option[String], outsideUk: Boolean): Option[String] = {
    (postcode, outsideUk) match {
      case (None, false) => None
      case (None, true) => Some(DefaultIndicator)
      case (aPostcode, false) => getFsacIndicator(aPostcode)
      case _ => Some(DefaultIndicator)
    }
  }

  override def calculateFsacIndicatorForReports(postcode: Option[String], candidate: CandidateProgressReportItem): Option[String] = {
    if(candidate.applicationRoute != ApplicationRoute.Faststream) { None }
    else if (candidate.progress.contains("registered")) { None }
    else if (postcode.isEmpty) { Some(DefaultIndicator) }
    else { getFsacIndicator(postcode) }
  }

  private def getFsacIndicator(postcode: Option[String]): Option[String] = {
    postcode.flatMap(pc => {
      val key = pc.takeWhile(!_.isDigit).toUpperCase
      fsacIndicators.get(key).fold[Option[String]](Some(DefaultIndicator))(indicator => Some(indicator.assessmentCentre))
    })
  }

}

trait NorthSouthIndicatorCSVRepository extends CsvHelper {
  val DefaultIndicator = "London"
  private[repositories] val fsacIndicators: Map[String, FSACIndicator]
  def calculateFsacIndicator(postcode: Option[String], outsideUk: Boolean): Option[String]
  def calculateFsacIndicatorForReports(postcode: Option[String], candidate: CandidateProgressReportItem): Option[String]
}
