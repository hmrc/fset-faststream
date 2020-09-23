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

package forms

import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import play.api.i18n.Messages
import testkit.BaseSpec

abstract class BaseFormSpec extends BaseSpec {
  implicit val mockMessages = mock[Messages]
  when(mockMessages.messages).thenReturn(mockMessages)
  when(mockMessages.apply(anyString(), any())).thenAnswer(new Answer[String]() {
    override def answer(invocationOnMock: InvocationOnMock): String = {
      invocationOnMock.getArgumentAt(0, classOf[String])
    }
  })
  when(mockMessages.apply(any[Seq[String]], any())).thenAnswer(new Answer[String]() {
    override def answer(invocationOnMock: InvocationOnMock): String = {
      invocationOnMock.getArgumentAt(0, classOf[String])
    }
  })
}
