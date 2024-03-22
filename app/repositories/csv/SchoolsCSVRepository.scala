/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.inject.ImplementedBy
import javax.inject.{ Inject, Singleton }
import model.School
import play.api.Application
import resource._

import scala.concurrent.Future
import scala.io.Source

@ImplementedBy(classOf[SchoolsCSVRepository])
trait SchoolsRepository {
  def schools: Future[List[School]]
}

@Singleton
class SchoolsCSVRepository @Inject() (application: Application) extends SchoolsRepository with CsvHelper {
  override def expectedNumberOfHeaders = 10

  private lazy val schoolsCached = Future.successful {

    val input = managed(application.environment.resourceAsStream("UK_schools_data_v2.csv").get)
    input.acquireAndGet { inputStream =>
      val rawData = Source.fromInputStream(inputStream).getLines().map(parseLine).toList
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

  def schools: Future[List[School]] = schoolsCached
}
