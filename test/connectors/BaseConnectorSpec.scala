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

import config.CSRHttp
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers.any
import play.api.libs.ws.WSClient
import play.api.{ConfigLoader, Configuration, Environment}
import testkit.BaseSpec

trait BaseConnectorSpec extends BaseSpec {
  trait BaseConnectorTestFixture {
    val mockConfiguration = mock[Configuration]
    when(mockConfiguration.getOptional(any[String])(any[ConfigLoader[String]])).thenReturn(None)

    val mockEnvironment = mock[Environment]
    val mockHttp = mock[CSRHttp]
    val mockApplicationClient = mock[ApplicationClient]
    val mockWs = mock[WSClient]
  }
}
