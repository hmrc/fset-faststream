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

import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.gzip.Import._
import com.typesafe.sbt.web.Import._
import com.typesafe.sbt.web.SbtWeb
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import play.sbt.routes.RoutesKeys._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

import uk.gov.hmrc._
import DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings, targetJvm}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

val appName = "fset-faststream-frontend"
val appDependencies : Seq[ModuleID] = AppDependencies()

lazy val plugins : Seq[Plugins] = Seq.empty
lazy val playSettings : Seq[Setting[_]] = Seq(routesImport ++= Seq("binders.CustomBinders._", "models._"))

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

lazy val microservice = Project(appName, file("."))
  .enablePlugins(plugins : _*)
  .enablePlugins(Seq(play.sbt.PlayScala, SbtWeb, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins : _*)
  .settings(majorVersion := 0)
  .settings(playSettings : _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings ++ ((Compile / packageDoc / publishArtifact) := false))
  .settings(defaultSettings(): _*)
  .settings(
    targetJvm := "jvm-1.8",
    scalaVersion := "2.12.11",
    routesImport += "controllers.Binders._",
    libraryDependencies ++= appDependencies,
    Test / parallelExecution := false,
    Test / fork := true,
    Test / javaOptions += "-Dlogger.resource=logback-test.xml",
    Test / javaOptions += "-Dmicroservice.services.user-management.url.host=http://localhost:11111",
    retrieveManaged := true,
    scalacOptions += "-feature",
    // Currently don't enable warning in value discard in tests until ScalaTest 3
    Compile / compile / scalacOptions += "-Ywarn-value-discard"//,
  )
  .settings(Compile / doc / sources := Seq.empty)
  .configs(IntegrationTest)
  .settings(pipelineStages := Seq(digest, gzip))
  .settings(inConfig(IntegrationTest)(sbt.Defaults.testSettings) : _*)

  // Disable scalariform awaiting release of to fix parameter formatting for implicit parameters
//  .settings(ScalariformSettings())

  .settings(compileScalastyle := (Compile / scalastyle).toTask("").value,
    (Compile / compile) := ((Compile / compile) dependsOn compileScalastyle).value
  )
  .settings(
    IntegrationTest / Keys.fork := false,
    IntegrationTest / unmanagedSourceDirectories := Seq((IntegrationTest / baseDirectory).value / "it"),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    IntegrationTest / testGrouping := oneForkedJvmPerTest((IntegrationTest / definedTests).value),
    IntegrationTest / parallelExecution := false
  )
  // Silhouette transitive dependencies require that the Atlassian repository be first in the resolver list
  .settings(resolvers := ("Atlassian Releases" at "https://maven.atlassian.com/public/") +: resolvers.value)
  .settings(resolvers ++= Seq(Resolver.jcenterRepo))
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)

def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] =
  tests map { test =>
    val forkOptions = ForkOptions().withRunJVMOptions(Vector("-Dtest.name=" + test.name))
    Group(test.name, Seq(test), SubProcess(config = forkOptions))
  }
