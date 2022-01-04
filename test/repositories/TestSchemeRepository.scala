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

package repositories
import model.{ FsbType, Scheme, SchemeId }

/*
 Convenience class to be extended in tests. An example can be found in ApplicationServiceSpec
 */
class TestSchemeRepository extends SchemeRepository {
  override def schemes: Seq[Scheme] = ???
  override def getSchemeForFsb(fsb: String): Scheme = ???
  override def faststreamSchemes: Seq[Scheme] = ???
  override def getSchemesForIds(ids: Seq[SchemeId]): Seq[Scheme] = ???
  override def getSchemeForId(id: SchemeId): Option[Scheme] = ???
  override def fsbSchemeIds: Seq[SchemeId] = ???
  override def siftableSchemeIds: Seq[SchemeId] = ???
  override def siftableAndEvaluationRequiredSchemeIds: Seq[SchemeId] = ???
  override def noSiftEvaluationRequiredSchemeIds: Seq[SchemeId] = ???
  override def nonSiftableSchemeIds: Seq[SchemeId] = ???
  override def numericTestSiftRequirementSchemeIds: Seq[SchemeId] = ???
  override def formMustBeFilledInSchemeIds: Seq[SchemeId] = ???
  override def getFsbTypes: Seq[FsbType] = ???
}
