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

import play.sbt.PlayImport.*
import sbt.*

//scalastyle:off line.size.limit
object AppDependencies {

  val bootstrapVersion = "10.2.0"

  val circe = Seq(
    "io.circe" %% "circe-yaml"  % "1.15.0"
  )

  val compile = Seq(
    "uk.gov.hmrc.mongo"             %% "hmrc-mongo-play-30"         % "2.9.0",
    "uk.gov.hmrc"                   %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "org.scala-lang.modules"        %% "scala-parallel-collections" % "1.2.0",
    filters,
    ws
  ) ++ circe

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "uk.gov.hmrc"               %% "bootstrap-test-play-30" % bootstrapVersion  % scope,
      "org.scalamock"             %% "scalamock"              % "7.3.0"           % scope
    )
  }

  object Test extends TestDependencies("test")

  def apply(): Seq[ModuleID] = compile ++ Test.test
}
