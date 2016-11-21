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

import play.PlayImport._
import sbt._

object MicroServiceBuild extends Build with MicroService {
  override val appName = "fset-faststream"
  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object Versions {

  val microserviceBootstrapVersion  = "4.4.0"
  val ficus                         = "1.1.2"
  val playHealthVersion             = "1.1.0"
  val playConfigVersion             = "2.1.0"
  val hmrcScheduler                 = "3.0.0"
  val hmrcTestVersion               = "1.8.0"
  val playReactivemongoVersion      = "4.8.0"
  val playJsonLogger                = "2.1.1"
  val guice                         = "4.0.0"

  val scalatest                     = "2.2.6"
  val pegdown                       = "1.6.0"
  val mockito                       = "2.2.17"
  val scalatestplus                 = "1.2.0"
  val specs2                        = "3.6.5"
}


private object AppDependencies {
  import Versions._
  import play.core.PlayVersion

  val compile = Seq(
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "play-health" % playHealthVersion,
    "uk.gov.hmrc" %% "play-config" % playConfigVersion,
    "uk.gov.hmrc" %% "play-json-logger" % playJsonLogger,
    "uk.gov.hmrc" %% "play-scheduling" % hmrcScheduler,
    "org.webjars" %% "webjars-play" % "2.3.0",
    "org.webjars" % "bootstrap" % "3.1.1",
    "org.webjars" % "jquery" % "1.11.0",
    "net.codingwell" %% "scala-guice" % guice,
    "net.ceedubs" %% "ficus" % ficus,
    "org.yaml" % "snakeyaml" % "1.16",
    "com.jsuereth" %% "scala-arm" % "1.4",
    "de.leanovate.play-mockws" %% "play-mockws" % "2.3.2" % "test",
    filters,
    cache,
    ws
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
      "org.mockito" % "mockito-core" % mockito % scope,
      "org.scalatestplus" %% "play" % scalatestplus % scope,
      "org.scalatest" %% "scalatest" % scalatest % scope,
      "org.pegdown" % "pegdown" % pegdown % scope,
      "com.typesafe.play" %% "play-test" % PlayVersion.current % scope excludeAll (
        ExclusionRule(organization = "org.specs2")
        ),
      "org.specs2" %% "specs2-core" % specs2 % scope
    )
  }

  object Test extends TestDependencies("test")
  object IntegrationTest extends TestDependencies("it")

  def apply() = compile ++ Test.test ++ IntegrationTest.test

}

