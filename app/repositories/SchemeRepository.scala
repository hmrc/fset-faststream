/*
 * Copyright 2017 HM Revenue & Customs
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
import model.{ Degree, Scheme, SchemeId, SiftRequirement }
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

  implicit val schemeFormat = yamlFormat7((
    id: String, code: String, name: String, civilServantEligible: Boolean, degree: Option[Degree],
    siftRequirement: Option[SiftRequirement.Value], evaluationRequired: Boolean
  ) => Scheme(SchemeId(id),code,name, civilServantEligible, degree, siftRequirement, evaluationRequired))
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

  def getSchemesForIds(ids: Seq[SchemeId]): Seq[Scheme] = ids.flatMap { id => getSchemeForId(id) }

  def getSchemeForId(id: SchemeId): Option[Scheme] = schemes.find(_.id == id)

  def siftableSchemeIds: Seq[SchemeId] = schemes.collect { case s if s.siftRequirement.isDefined => s.id}
}

object SchemeYamlRepository extends SchemeRepository
