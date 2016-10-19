/*
 * Copyright 2016 HM Revenue & Customs
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

import model.School
import play.api.Play
import resource._

import scala.concurrent.Future
import scala.io.Source

trait SchoolsRepository {
  def schools: Future[List[School]]
}

object SchoolsCSVRepository extends SchoolsRepository {
  private val SchoolsCSVPath = "UK_schools_data_v2.csv"
  private val ExpectedNumberOfHeaders = 10

  import play.api.Play.current

  private lazy val schoolsCached = Future.successful {

    val input = managed(Play.application.resourceAsStream("UK_schools_data_v2.csv").get)
    input.acquireAndGet { inputStream =>
      val rawData = Source.fromInputStream(inputStream).getLines.map(parseLine).toList
      val headers = rawData.head
      val values = rawData.tail
      val schools = values map { columns =>
        require(headers.length == columns.length,
          s"Number of columns must be equal to number of headers. Incorrect line: ${columns.mkString("|")}")

        def tryGet(col: Int) = if (columns(col).isEmpty) None else Some(columns(col))

        School(columns(0), columns(1), columns(2), tryGet(3), tryGet(4), tryGet(5), tryGet(6), tryGet(7), tryGet(8), tryGet(9))
      }
      schools
    }
  }

  private[repositories] def parseLine(line: String): Array[String] = {
    if (line.split(",").length != ExpectedNumberOfHeaders) {
      val sanitized = line.split("\"")
      require(sanitized.length == 3, "Line cannot have more than one value with quotation")
      require(!sanitized(1).contains("|"), "| is forbidden character")

      val parsedLine = sanitized(0) + sanitized(1).replaceAll(",", "|") + sanitized(2)
      val fixedColumns = parsedLine.split(",")
      require(fixedColumns.length == ExpectedNumberOfHeaders,
        "Line with commas as values need to have space after each coma which is not a delimiter")

      fixedColumns.map(_.replaceAll("\\|", ","))
    } else {
      line.split(",")
    }
  }

  def schools: Future[List[School]] = schoolsCached

}
