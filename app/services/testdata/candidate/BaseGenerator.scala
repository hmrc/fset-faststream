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

package services.testdata.candidate

import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait BaseGenerator {
  def generate(generationId: Int, generatorConfig: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse]
}
