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

package models.view

object CampaignReferrers {
  val list = List(
    ("GOV.UK or Civil Service Jobs", false),
    ("Recruitment website", false),
    ("Social Media (Facebook, Twitter or Instagram)", false),
    ("Fast Stream website (including scheme sites)", false),
    ("News article or online search (Google)", false),
    ("Friend in the Fast Stream", false),
    ("Friend or family in the Civil Service", false),
    ("Friend or family outside of the Civil Service", false),
    ("Careers fair (University or graduate)", false),
    ("University careers service (or jobs flyers)", false),
    ("University event (Guest lecture or skills session)", false),
    ("Other", true)
  )
}
