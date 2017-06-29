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
import model.Scheme
import net.jcazevedo.moultingyaml._
import net.jcazevedo.moultingyaml.DefaultYamlProtocol._
import play.api.Play
import resource._

import scala.concurrent.Future
import scala.io.Source


object SchemeConfigProtocol extends DefaultYamlProtocol {
  implicit val schemeFormat = yamlFormat3((a: String, b: String ,c: String) => Scheme(a,b,c))
}

trait SchemeRepositoryImpl {

  import play.api.Play.current

  private lazy val rawConfig = {
    val input = managed(Play.application.resourceAsStream(MicroserviceAppConfig.schemeConfig.yamlFilePath).get)
    input.acquireAndGet(stream => Source.fromInputStream(stream).mkString)
  }

  lazy val schemes = Future {
    import SchemeConfigProtocol._

    rawConfig.parseYaml.convertTo[List[Scheme]]
  }
}

object SchemeYamlRepository extends SchemeRepositoryImpl
