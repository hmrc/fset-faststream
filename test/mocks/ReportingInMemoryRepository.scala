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

package mocks

import model.PersistedObjects.{ DiversityReport, DiversityReportRow }
import org.joda.time.DateTime
import repositories.ReportingRepository

import scala.concurrent.Future

object ReportingInMemoryRepository extends ReportingRepository {
  override def update(location: String, timeStamp: DateTime, data: DiversityReportRow): Future[Unit] = Future.successful(Unit)

  override def findLatest(): Future[Option[DiversityReport]] = Future.successful(None)

  override def finalizeReportStatus(timeStamp: DateTime): Future[Unit] = Future.successful(Unit)
}
