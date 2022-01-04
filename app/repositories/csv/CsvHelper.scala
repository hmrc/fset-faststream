/*
 * Copyright 2022 HM Revenue & Customs
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

trait CsvHelper {

  def expectedNumberOfHeaders: Int

  private[repositories] def parseLine(line: String): Array[String] = {
    if (line.split(",").length != expectedNumberOfHeaders) {
      val sanitized = line.split("\"")
      require(sanitized.length == 3, "Line cannot have more than one value with quotation")
      require(!sanitized(1).contains("|"), "| is forbidden character")

      val parsedLine = sanitized(0) + sanitized(1).replaceAll(",", "|") + sanitized(2)
      val fixedColumns = parsedLine.split(",")
      require(fixedColumns.length == expectedNumberOfHeaders,
        "Line with commas as values need to have space after each coma which is not a delimiter")

      fixedColumns.map(_.replaceAll("\\|", ","))
    } else {
      line.split(",")
    }
  }
}
