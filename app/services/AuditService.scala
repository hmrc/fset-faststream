/*
 * Copyright 2020 HM Revenue & Customs
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

package services

import com.google.inject.Inject
import com.google.inject.name.Named
import javax.inject.Singleton
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.EventKeys
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{ Audit, DataEvent, EventTypes }

@Singleton
class AuditService @Inject() (@Named("appName") val appName: String, auditConnector: AuditConnector) {
  private[services] val auditFacade: Audit = new Audit(appName, auditConnector)

  def logEvent(eventName: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Unit =
    auditFacade.sendDataEvent(
      DataEvent(appName, EventTypes.Succeeded, tags = hc.toAuditTags(eventName, rh.path))
    )

  def logEvent(eventName: String, detail: Map[String, String])(implicit hc: HeaderCarrier, rh: RequestHeader): Unit =
    auditFacade.sendDataEvent(
      DataEvent(appName, EventTypes.Succeeded, tags = hc.toAuditTags(eventName, rh.path), detail = detail)
    )

  def logEventNoRequest(eventName: String, detail: Map[String, String]): Unit =
    auditFacade.sendDataEvent(
      DataEvent(appName, EventTypes.Succeeded, tags = Map(EventKeys.TransactionName -> eventName), detail = detail)
    )
}
