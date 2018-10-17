/*
 * Copyright 2018 HM Revenue & Customs
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

import config.MicroserviceAppConfig
import model.Exceptions.SchemeNotFoundException
import model._
import net.jcazevedo.moultingyaml._
import play.api.Play
import resource._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source

object SchemeConfigProtocol extends DefaultYamlProtocol {
  implicit object SiftRequirementFormat extends YamlFormat[SiftRequirement.Value] {
    def read(value: YamlValue): SiftRequirement.Value = value match {
      case YamlString(siftReq) => SiftRequirement.withName(siftReq.toUpperCase.replaceAll("\\s|-", "_"))
    }

    def write(obj: SiftRequirement.Value): YamlValue = YamlString(obj.toString)
  }

  implicit val degreeFormat = yamlFormat2((a: String, b: Boolean) => Degree(a, b))

  implicit val schemeFormat = yamlFormat10((
    id: String, code: String, name: String, civilServantEligible: Boolean, degree: Option[Degree],
    siftRequirement: Option[SiftRequirement.Value], evaluationRequired: Boolean,
    fsbType: Option[String], schemeGuide: Option[String], schemeQuestion: Option[String]
  ) => Scheme(SchemeId(id),code,name, civilServantEligible, degree, siftRequirement, evaluationRequired,
    fsbType.map(t => FsbType(t)), schemeGuide, schemeQuestion))
}

trait SchemeRepository {

  import play.api.Play.current

  private lazy val rawConfig = {
    val input = managed(Play.application.resourceAsStream(MicroserviceAppConfig.schemeConfig.yamlFilePath).get)
    input.acquireAndGet(stream => Source.fromInputStream(stream).mkString)
  }

  lazy val schemes: Seq[Scheme] = {
    import SchemeConfigProtocol._
    rawConfig.parseYaml.convertTo[List[Scheme]]
  }

  private lazy val schemesByFsb: Map[String, Scheme] = schemes.flatMap(s => s.fsbType.map(ft => ft.key -> s)).toMap

  def getSchemeForFsb(fsb: String): Scheme = {
    schemesByFsb.getOrElse(fsb, throw SchemeNotFoundException(s"Cannot find scheme for FSB: $fsb"))
  }

  def faststreamSchemes: Seq[Scheme] = schemes.filterNot(s => s.isSdip || s.isEdip)

  def getSchemesForIds(ids: Seq[SchemeId]): Seq[Scheme] = ids.flatMap { id => getSchemeForId(id) }

  def getSchemeForId(id: SchemeId): Option[Scheme] = schemes.find(_.id == id)

  def fsbSchemeIds: Seq[SchemeId] = schemes.collect { case s if s.fsbType.isDefined => s.id }

  def siftableSchemeIds: Seq[SchemeId] = schemes.collect { case s if s.siftRequirement.isDefined => s.id }

  def siftableAndEvaluationRequiredSchemeIds: Seq[SchemeId] = schemes.collect {
    case s if s.siftRequirement.isDefined && s.siftEvaluationRequired => s.id
  }

  def noSiftEvaluationRequiredSchemeIds: Seq[SchemeId] = schemes.collect {
    case s if !s.siftEvaluationRequired => s.id
  }

  def nonSiftableSchemeIds: Seq[SchemeId] = schemes.collect { case s if s.siftRequirement.isEmpty => s.id }

  def numericTestSiftRequirementSchemeIds: Seq[SchemeId] = schemes.collect {
    case s if s.siftRequirement.contains(SiftRequirement.NUMERIC_TEST) && s.siftEvaluationRequired => s.id
  }

  def formMustBeFilledInSchemeIds: Seq[SchemeId] = schemes.collect {
    case s if s.siftRequirement.contains(SiftRequirement.FORM) => s.id
  }

  def getFsbTypes: Seq[FsbType] = schemes.flatMap(_.fsbType)
}

object SchemeYamlRepository extends SchemeRepository
