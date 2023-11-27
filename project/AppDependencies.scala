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

  val compile = Seq(
    "uk.gov.hmrc.mongo"             %% "hmrc-mongo-play-28"         % "1.5.0",
    "uk.gov.hmrc"                   %% "bootstrap-backend-play-28"  % "7.23.0",
    "com.typesafe.play"             %% "play-json-joda"             % "2.9.2",
    // If you move to 1.5.0 it breaks AssessmentCentreServiceIntSpec ficus deserialization
    "com.iheart"                    %% "ficus"                      % "1.4.7",
// Removed when we changed scala version 2.12.11 -> 2.12.15
//    compilerPlugin("com.github.ghik" %% "silencer-plugin" % "0.5"),
//    "com.github.ghik"   %% "silencer-lib"                     % "0.5",
    "org.yaml"                      %  "snakeyaml"                  % "1.16",
    "com.michaelpollmeier"          %% "scala-arm"                  % "2.1",
    "net.jcazevedo"                 %% "moultingyaml"               % "0.4.2",
    // Works with MireMock up to version 2.31.0
    "com.fasterxml.jackson.module"  %% "jackson-module-scala"       % "2.12.2",
    "org.scala-lang.modules"        %% "scala-parallel-collections" % "1.0.0",
    filters,
    ws
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "de.leanovate.play-mockws"  %% "play-mockws"                  % "2.7.1"     % sbt.Test,
      "org.mockito"               %  "mockito-core"                 % "3.0.0"     % scope,
      "org.scalatestplus.play"    %% "scalatestplus-play"           % "5.1.0"     % scope,
      // Gives you access to MockitoSugar as it is no longer available in scalatestplus-play
      "org.scalatestplus"         %% "mockito-3-4"                  % "3.2.8.0"   % scope,
      "com.vladsch.flexmark"      %  "flexmark-all"                 % "0.36.8"    % scope,
      "org.scalatest"             %% "scalatest"                    % "3.1.0"     % scope,
      "org.scalamock"             %% "scalamock"                    % "5.1.0"     % scope,
      "org.scalacheck"            %% "scalacheck"                   % "1.17.0"    % sbt.Test,
      "com.github.tomakehurst"    %  "wiremock-jre8"                % "2.31.0"    % sbt.Test
    )
  }

  object Test extends TestDependencies("test")
  object IntegrationTest extends TestDependencies("it")

  def apply(): Seq[ModuleID] = compile ++ Test.test ++ IntegrationTest.test
}
