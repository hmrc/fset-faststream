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

package config

import akka.actor.ActorSystem
import com.google.inject.ImplementedBy
import com.google.inject.name.Named
import com.typesafe.config.Config
import javax.inject.{ Inject, Singleton }
import play.api.Configuration
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.ws._

@ImplementedBy(classOf[HttpVerbs])
trait WSHttpT extends HttpGet with HttpPut with HttpPost with HttpDelete with HttpPatch with WSHttp

@Singleton
class HttpVerbs @Inject() (@Named("appName") val appName: String, val auditConnector: AuditConnector, val wsClient: WSClient,
                           val actorSystem: ActorSystem, config: Configuration)
  extends WSHttpT with HttpAuditing {
  override val hooks = Seq(AuditingHook)
  override def configuration: Config = config.underlying
}
