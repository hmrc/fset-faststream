/*
 * Copyright 2021 HM Revenue & Customs
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

package config

import akka.stream.Materializer
import filters.{FaststreamAllowlistFilter, FaststreamAuditFilter}
import play.api.Logging
import play.api.http.{DefaultHttpFilters, EnabledFilters}

import javax.inject.Inject

class DevFaststreamFilters @Inject() (
  defaultFilters: EnabledFilters,
  auditFilter: FaststreamAuditFilter)(
  implicit
  val materializer: Materializer)
  extends DefaultHttpFilters(
    defaultFilters.filters.updated(defaultFilters.filters.indexWhere(_.toString.contains("AuditFilter")), auditFilter): _*) with Logging {
  logger.info("Allow list filter is NOT enabled")
}

class ProductionFaststreamFilters @Inject() (
                                              defaultFilters: EnabledFilters,
                                              auditFilter: FaststreamAuditFilter,
                                              allowlistFilter: FaststreamAllowlistFilter)(
  implicit
  val materializer: Materializer)
  extends DefaultHttpFilters((Seq(allowlistFilter) ++
    defaultFilters.filters.updated(defaultFilters.filters.indexWhere(_.toString.contains("AuditFilter")), auditFilter)
  ): _*) with Logging {
  logger.info("Allow list filter is enabled")
}
