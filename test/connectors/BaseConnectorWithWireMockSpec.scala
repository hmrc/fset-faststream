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

package connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{ Application, Configuration, Environment }

trait BaseConnectorWithWireMockSpec extends BaseSpec with BeforeAndAfterEach with BeforeAndAfterAll with GuiceOneServerPerSuite {

  protected def wireMockPort: Int = 11111
  private val stubHost = "localhost"

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(Map(
      "microservice.services.launchpad.api.baseUrl" -> s"http://localhost:$wireMockPort"
    ))
    .build

  protected val wiremockBaseUrl: String = s"http://localhost:$wireMockPort"
  protected val wireMockServer = new WireMockServer(wireMockConfig().port(wireMockPort))

  override def beforeAll(): Unit = {
    wireMockServer.stop()
    wireMockServer.start()
    WireMock.configureFor(stubHost, wireMockPort)
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
  }

  override def beforeEach(): Unit = {
    WireMock.reset()
  }

  trait BaseConnectorTestFixture {
    val mockConfiguration = mock[Configuration]
    val mockEnvironment = mock[Environment]
  }
}
