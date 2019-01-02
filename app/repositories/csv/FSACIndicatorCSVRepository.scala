/*
 * Copyright 2019 HM Revenue & Customs
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

package repositories.csv

import model.report.CandidateProgressReportItem
import model.{ ApplicationRoute, FSACIndicator }
import resource._
import com.github.ghik.silencer.silent
import play.api.Play

import scala.io.Source

object FSACIndicatorCSVRepository extends FSACIndicatorCSVRepository {
  private val CsvFileName = "FSAC_indicator_lookup_by_postcode.csv"
  override def expectedNumberOfHeaders = 3

  import play.api.Play.current

  override private[repositories] val indicators: Map[String, FSACIndicator] =  {
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

  override def find(postcode: Option[String], outsideUk: Boolean): Option[FSACIndicator] = {
    (postcode, outsideUk) match {
      case (None, false) => None
      case (None, true) => Some(DefaultIndicator)
      case (aPostcode, false) => find(aPostcode)
      case _ => Some(DefaultIndicator)
    }
  }

  private def find(postcode: Option[String]): Option[FSACIndicator] = {
    postcode.flatMap(postCodeVal => {
      val postCodeUpperCase = postCodeVal.takeWhile(!_.isDigit).toUpperCase
      indicators.get(postCodeUpperCase).fold[Option[FSACIndicator]](Some(DefaultIndicator))(indicator => Some(indicator))
    })
  }

  override def getAssessmentCentres: Seq[String] =
    indicators.values.groupBy( fsacIndicator => fsacIndicator.assessmentCentre).keys.toSeq
}

trait FSACIndicatorCSVRepository extends CsvHelper {
  val FSACIndicatorVersion = "1"
  val DefaultIndicator = FSACIndicator("Newcastle", "Newcastle")

  private[repositories] val indicators: Map[String, FSACIndicator]

  def find(postcode: Option[String], outsideUk: Boolean): Option[FSACIndicator]

  def getAssessmentCentres: Seq[String]
}
