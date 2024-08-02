/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.inject.ImplementedBy
import config.MicroserviceAppConfig
import model.Exceptions.SchemeNotFoundException
import model._
import net.jcazevedo.moultingyaml._
import play.api.Application
import resource._

import javax.inject.{Inject, Singleton}
import scala.io.Source

case class SchemeConfigProtocol(urlPrefix: String) extends DefaultYamlProtocol {
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
  ) => Scheme(SchemeId(id), code, name, civilServantEligible, degree, siftRequirement, evaluationRequired,
    fsbType.map(t => FsbType(t)), schemeGuide, schemeQuestion.map( url => s"$urlPrefix$url" ))
  )
}

@ImplementedBy(classOf[SchemeYamlRepository])
trait SchemeRepository {

  def schemes: Seq[Scheme]
  def getSchemeForFsb(fsb: String): Scheme
  def faststreamSchemes: Seq[Scheme]
  def getSchemesForIds(ids: Seq[SchemeId]): Seq[Scheme]
  def getSchemeForId(id: SchemeId): Option[Scheme]
  def fsbSchemeIds: Seq[SchemeId]
  def siftableSchemeIds: Seq[SchemeId]
  def siftableAndEvaluationRequiredSchemeIds: Seq[SchemeId]
  def noSiftEvaluationRequiredSchemeIds: Seq[SchemeId]
  def nonSiftableSchemeIds: Seq[SchemeId]
  def numericTestSiftRequirementSchemeIds: Seq[SchemeId]
  def formMustBeFilledInSchemeIds: Seq[SchemeId]
  def getFsbTypes: Seq[FsbType]
  def isValidSchemeId(schemeId: SchemeId): Boolean

  /**
    * Max number of schemes that a candidate can choose. Note that SdipFaststream max will be +1 eg. 5 because they automatically
    * get Sdip in addition to the 4 selectable schemes
    * @return the number of selectable schemes
    */
  def maxNumberOfSelectableSchemes = 4
}

@Singleton
class SchemeYamlRepository @Inject() (implicit application: Application, appConfig: MicroserviceAppConfig) extends SchemeRepository {

  private lazy val rawConfig = {
    val input = managed(application.environment.resourceAsStream(appConfig.schemeConfig.yamlFilePath).get)
    input.acquireAndGet(stream => Source.fromInputStream(stream).mkString)
  }

  override lazy val schemes: Seq[Scheme] = {
    val schemeConfigProtocol = SchemeConfigProtocol(appConfig.schemeConfig.candidateFrontendUrl)
    import schemeConfigProtocol._
    rawConfig.parseYaml.convertTo[List[Scheme]]
  }

  private lazy val schemesByFsb: Map[String, Scheme] = schemes.flatMap(s => s.fsbType.map(ft => ft.key -> s)).toMap

  override def getSchemeForFsb(fsb: String): Scheme = {
    schemesByFsb.getOrElse(fsb, throw SchemeNotFoundException(s"Cannot find scheme for FSB: $fsb"))
  }

  override def faststreamSchemes: Seq[Scheme] = schemes.filterNot(s => s.isSdip || s.isEdip)

  override def getSchemesForIds(ids: Seq[SchemeId]): Seq[Scheme] = ids.flatMap { id => getSchemeForId(id) }

  override def getSchemeForId(id: SchemeId): Option[Scheme] = schemes.find(_.id == id)

  override def fsbSchemeIds: Seq[SchemeId] = schemes.collect { case s if s.fsbType.isDefined => s.id }

  override def siftableSchemeIds: Seq[SchemeId] = schemes.collect { case s if s.siftRequirement.isDefined => s.id }

  override def siftableAndEvaluationRequiredSchemeIds: Seq[SchemeId] = schemes.collect {
    case s if s.siftRequirement.isDefined && s.siftEvaluationRequired => s.id
  }

  override def noSiftEvaluationRequiredSchemeIds: Seq[SchemeId] = schemes.collect {
    case s if !s.siftEvaluationRequired => s.id
  }

  override def nonSiftableSchemeIds: Seq[SchemeId] = schemes.collect { case s if s.siftRequirement.isEmpty => s.id }

  override def numericTestSiftRequirementSchemeIds: Seq[SchemeId] = schemes.collect {
    case s if s.siftRequirement.contains(SiftRequirement.NUMERIC_TEST) && s.siftEvaluationRequired => s.id
  }

  override def formMustBeFilledInSchemeIds: Seq[SchemeId] = schemes.collect {
    case s if s.siftRequirement.contains(SiftRequirement.FORM) => s.id
  }

  override def getFsbTypes: Seq[FsbType] = schemes.flatMap(_.fsbType)

  override def isValidSchemeId(schemeId: SchemeId): Boolean = schemes.map(_.id).contains(schemeId)
}
