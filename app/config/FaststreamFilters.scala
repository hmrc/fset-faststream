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
import filters.{CookiePolicyFilter, FaststreamAuditFilter, FaststreamWhitelistFilter}
import play.api.Logging
import play.api.http.DefaultHttpFilters
import uk.gov.hmrc.play.bootstrap.frontend.filters.FrontendFilters

import javax.inject.Inject

class FaststreamFilters @Inject() (
  defaultFilters: FrontendFilters,
  auditFilter: FaststreamAuditFilter,
  cookiePolicyFilter: CookiePolicyFilter)(
  implicit
  val materializer: Materializer)
  extends DefaultHttpFilters(
    defaultFilters.filters.updated(defaultFilters.filters.indexWhere(_.toString.contains("AuditFilter")), auditFilter)
      :+ cookiePolicyFilter: _*) with Logging {
  logger.info("White list filter NOT enabled")
}

class ProductionFaststreamFilters @Inject() (
  defaultFilters: FrontendFilters,
  auditFilter: FaststreamAuditFilter,
  whilelistFilter: FaststreamWhitelistFilter,
  cookiePolicyFilter: CookiePolicyFilter)(
  implicit
  val materializer: Materializer)
  extends DefaultHttpFilters((Seq(whilelistFilter) ++
    defaultFilters.filters.updated(defaultFilters.filters.indexWhere(_.toString.contains("AuditFilter")), auditFilter)
    ) :+ cookiePolicyFilter: _*) with Logging {
  logger.info("White list filter enabled")
}
