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

package models.page

case class TestResultsPage(
                            phase1DataOpt: Option[Phase1TestsPage2],
                            phase2DataOpt: Option[Phase2TestsPage],
                            phase3DataOpt: Option[Phase3TestsPage]
                          ) {
  def noDataFound = List(phase1DataOpt, phase2DataOpt, phase3DataOpt).forall( _.isEmpty )
}
