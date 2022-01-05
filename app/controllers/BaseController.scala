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

package controllers

import config.FrontendAppConfig
import play.api.Logging
import play.api.mvc.MessagesControllerComponents
import security.SecureActions
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

/**
 * should be extended by all controllers
 */
abstract class BaseController(
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents)
  extends FrontendController(mcc) with SecureActions with Logging {

  implicit val feedbackUrl = config.feedbackUrl
  implicit val trackingConsentConfig = config.trackingConsentConfig
}
