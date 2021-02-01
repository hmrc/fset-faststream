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
import sbt._

//scalastyle:off line.size.limit
object AppDependencies {
  import play.core.PlayVersion

  val compile = Seq(
    "uk.gov.hmrc"       %% "simple-reactivemongo"             % "7.30.0-play-27",
    "uk.gov.hmrc"       %% "bootstrap-backend-play-27"        % "2.24.0",
    // Needed to get an Enumerator of documents from ReactiveMongo. Note the version specified is the version of the ReactiveMongo driver
    // which matches the current version used in the HMRC simple-reactivemongo lib
    // ReactiveMongo version used is here: https://github.com/hmrc/simple-reactivemongo/blob/master/project/LibraryDependencies.scala
    "org.reactivemongo" %% "reactivemongo-iteratees"          % "0.18.8",
    "com.typesafe.play" %% "play-iteratees"                   % "2.6.1",
    "com.typesafe.play" %% "play-iteratees-reactive-streams"  % "2.6.1",
    "com.typesafe.play" %% "play-json-joda"                   % "2.6.10",
    "uk.gov.hmrc"       %% "play-scheduling-play-27"          % "7.10.0",
    "org.webjars"       %% "webjars-play"                     % "2.7.3",
    "org.webjars"       %  "bootstrap"                        % "3.1.1",
    "org.webjars"       %  "jquery"                           % "1.11.0",
    // If you move to 1.5.0 it breaks AssessmentCentreServiceIntSpec ficus deserialization
    "com.iheart"        %% "ficus"                            % "1.4.7",
    compilerPlugin("com.github.ghik" %% "silencer-plugin" % "0.5"),
    "com.github.ghik"   %% "silencer-lib"                     % "0.5",
    "org.yaml"          %  "snakeyaml"                        % "1.16",
    "com.jsuereth"      %% "scala-arm"                        % "2.0",
    "net.jcazevedo"     %% "moultingyaml"                     % "0.4.0",
    filters,
    ws
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "org.pegdown"               %  "pegdown"                      % "1.6.0"             % scope,
      "de.leanovate.play-mockws"  %% "play-mockws"                  % "2.7.1"             % sbt.Test,
      "org.mockito"               %  "mockito-core"                 % "3.0.0"             % scope,
      "org.scalatestplus.play"    %% "scalatestplus-play"           % "4.0.3"             % scope,
      "com.typesafe.play"         %% "play-test"                    % PlayVersion.current % scope excludeAll ExclusionRule(organization = "org.specs2"),
      "org.scalamock"             %% "scalamock-scalatest-support"  % "3.5.0"             % scope,
      "org.scalacheck"            %% "scalacheck"                   % "1.13.4"            % sbt.Test
    )
  }

  object Test extends TestDependencies("test")
  object IntegrationTest extends TestDependencies("it")

  def apply(): Seq[ModuleID] = compile ++ Test.test ++ IntegrationTest.test
}
