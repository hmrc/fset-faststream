/*
 * Copyright 2019 HM Revenue & Customs
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

package services.onlinetesting

import model.Exceptions.NotFoundException

object Exceptions {
  case class TestExtensionException(message: String) extends Exception(message)
  case class ReportIdNotDefinedException(message: String) extends Exception(message)
  case class NoActiveTestException(m: String) extends Exception(m)
  case class CannotResetPhase2Tests() extends NotFoundException
  case class ResetLimitExceededException() extends Exception
  case class TestRegistrationException(m: String) extends Exception(m)
  case class TestCancellationException(m: String) extends Exception(m)
}
