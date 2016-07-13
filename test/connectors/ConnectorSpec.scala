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

package connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import uk.gov.hmrc.play.test.UnitSpec

trait ConnectorSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with BeforeAndAfterAll {

  //override implicit val defaultTimeout = FiniteDuration(100, TimeUnit.SECONDS)

  private val WIREMOCK_PORT = 11111
  private val stubHost = "localhost"

  protected val wiremockBaseUrl: String = s"http://localhost:$WIREMOCK_PORT"
  private val wireMockServer = new WireMockServer(wireMockConfig().port(WIREMOCK_PORT))

  override def beforeAll() = {
    wireMockServer.stop()
    wireMockServer.start()
    WireMock.configureFor(stubHost, WIREMOCK_PORT)
  }

  override def afterAll() = {
    wireMockServer.stop()
  }

  override def beforeEach() = {
    WireMock.reset()
  }
}
