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

package config

import akka.actor.ActorSystem
import com.google.inject.ImplementedBy
import com.google.inject.name.Named
import com.typesafe.config.Config
import javax.inject.{ Inject, Singleton }
import play.api.{ Configuration, Environment, Play }
import play.api.Play.current
import play.api.libs.ws.{ WS, WSClient }
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.LoadAuditingConfig
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.http.ws._
/*
trait WSHttp extends HttpGet with WSGet with HttpPut with WSPut with HttpPost with WSPost with HttpDelete with WSDelete with AppName {
  // Disable implicit _outbound_ auditing.
  override val hooks = NoneRequired
  lazy val playWS: WSClient = WS.client
  override def configuration: Option[Config] = Option(Play.current.configuration.underlying)
  override def appNameConfiguration = Play.current.configuration
  override def actorSystem: ActorSystem = Play.current.actorSystem
}

object WSHttp extends WSHttp
*/

@ImplementedBy(classOf[HttpVerbs])
trait WSHttpT extends HttpGet with HttpPut with HttpPost with HttpDelete with HttpPatch with WSHttp

@Singleton
class HttpVerbs @Inject() (@Named("appName") val appName: String, val auditConnector: AuditConnector,
                           val actorSystem: ActorSystem, config: Configuration)
  extends WSHttpT with HttpAuditing {
  override val hooks = Seq(AuditingHook)
  lazy val playWS: WSClient = wsClient
  override def configuration: Option[Config] = Option(config.underlying)
}

//TODO:fix remove
//@Singleton
//class MicroserviceAuditConnector @Inject() (configuration: Configuration, env: Environment) extends AuditConnector {
//  override lazy val auditingConfig = LoadAuditingConfig(configuration, env.mode, "auditing")
//}


