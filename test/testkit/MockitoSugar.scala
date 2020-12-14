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

package testkit

import org.mockito.Mockito
import org.mockito.stubbing.Stubber

import scala.concurrent.Future

trait MockitoSugar extends org.scalatestplus.mockito.MockitoSugar {
  // Unfortunately we cannot use the `when().thenReturn` format for spies.
  def doReturnAsync() = Mockito.doReturn(Future.successful(()), Nil:_*)
  def doReturnAsync[A](value: A) = Mockito.doReturn(Future.successful(value), Nil:_*)
  def doThrowAsync(): Stubber = doThrowAsync(new Exception("Unexpected error"))
  def doThrowAsync(exception: Throwable): Stubber = Mockito.doReturn(Future.failed(exception), Nil:_*)
}
