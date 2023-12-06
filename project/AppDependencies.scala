/*
 * Copyright 2016 HM Revenue & Customs
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

import play.sbt.PlayImport._
import sbt.Keys.scalaBinaryVersion
import sbt._

//scalastyle:off line.size.limit
object AppDependencies {

  val bootstrapVersion = "8.1.0"

  val compile = Seq(
    "uk.gov.hmrc.mongo"             %% "hmrc-mongo-play-28"         % "1.6.0",
    "uk.gov.hmrc"                   %% "bootstrap-backend-play-28"  % bootstrapVersion,
    "com.typesafe.play"             %% "play-json-joda"             % "2.9.4",
    // If you move to 1.5.0 it breaks AssessmentCentreServiceIntSpec ficus deserialization
    "com.iheart"                    %% "ficus"                      % "1.4.7",
    "org.yaml"                      %  "snakeyaml"                  % "2.0",
    "net.jcazevedo"                 %% "moultingyaml"               % "0.4.2",
    "com.michaelpollmeier"          %% "scala-arm"                  % "2.1",
    "org.scala-lang.modules"        %% "scala-parallel-collections" % "1.0.4",
    filters,
    ws
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "uk.gov.hmrc"               %% "bootstrap-test-play-28" % bootstrapVersion  % scope,
      "org.mockito"               %  "mockito-core"           % "3.0.0"           % scope,
      // Gives you access to MockitoSugar
      "org.scalatestplus"         %% "mockito-3-4"            % "3.2.8.0"         % scope,
      "org.scalatest"             %% "scalatest"              % "3.1.0"           % scope,
      "org.scalamock"             %% "scalamock"              % "5.1.0"           % scope,
    )
  }

  object Test extends TestDependencies("test")
  object IntegrationTest extends TestDependencies("it")

  def apply(): Seq[ModuleID] = compile ++ Test.test ++ IntegrationTest.test
}
