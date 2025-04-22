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
import io.circe.yaml.parser
import io.circe.{Decoder, Error, HCursor, Json, ParsingFailure}
import model.Exceptions.SchemeNotFoundException
import model._
import play.api.Application

import javax.inject.{Inject, Singleton}
import scala.io.Source
import scala.util.Using

case class SchemeConfigProtocol(urlPrefix: String) {

  implicit val decoder: Decoder[Scheme] = (c: HCursor) => for {
    id <- c.downField("id").as[String]
    code <- c.downField("code").as[String]
    name <- c.downField("name").as[String]
    civilServantEligible <- c.downField("civilServantEligible").as[Boolean]

    degreeRequired <- c.downField("degree").downField("required").as[Option[String]]
    degreeSpecificRequirement <- c.downField("degree").downField("specificRequirement").as[Option[Boolean]]

    siftRequirementOpt <- c.downField("siftRequirement").as[Option[String]]
    siftEvaluationRequired <- c.downField("siftEvaluationRequired").as[Boolean]

    fsbTypeOpt <- c.downField("fsbType").as[Option[String]]

    schemeGuideOpt <- c.downField("schemeGuide").as[Option[String]]
    schemeQuestionOpt <- c.downField("schemeQuestion").as[Option[String]]
  } yield {

    val degreeOpt = (degreeRequired, degreeSpecificRequirement) match {
      case(Some(dr), Some(dsr)) => Some(Degree(dr, dsr))
      case _ => None
    }

    val siftRequirementEnumOpt = siftRequirementOpt.map { siftRequirement =>
      SiftRequirement.withName(siftRequirement.toUpperCase.replaceAll("\\s|-", "_"))
    }

    Scheme(
      SchemeId(id), code, name, civilServantEligible, degreeOpt,
      siftRequirementEnumOpt, siftEvaluationRequired,
      fsbTypeOpt.map(fsbType => FsbType(fsbType)),
      schemeGuideOpt.map( url => s"$urlPrefix$url" ),
      schemeQuestionOpt.map( url => s"$urlPrefix$url" )
    )
  }
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

  def schemeRequiresFsb(id: SchemeId): Boolean

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

  private lazy val yamlString = {
    Using.resource(application.environment.resourceAsStream(appConfig.schemeConfig.yamlFilePath).get) { inputStream =>
      Source.fromInputStream(inputStream).mkString
    }
  }

  override lazy val schemes: Seq[Scheme] = {
    val json: Either[ParsingFailure, Json] = parser.parse(yamlString)

    val schemeConfigProtocol = SchemeConfigProtocol(appConfig.schemeConfig.candidateFrontendUrl)

    import schemeConfigProtocol.decoder
    import cats.syntax.either._

    json
      .leftMap(err => err: Error)
      .flatMap(_.as[Seq[Scheme]])
      .valueOr(throw _)
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

  override def schemeRequiresFsb(id: SchemeId): Boolean = getSchemeForId(id).exists(_.fsbType.isDefined)

  override def getFsbTypes: Seq[FsbType] = schemes.flatMap(_.fsbType)

  override def isValidSchemeId(schemeId: SchemeId): Boolean = schemes.map(_.id).contains(schemeId)
}
