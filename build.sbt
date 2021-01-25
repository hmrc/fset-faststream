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
import play.sbt.PlayImport._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion
import play.sbt.routes.RoutesKeys.routesGenerator

import uk.gov.hmrc._
import DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings, targetJvm}
import TestPhases._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

import scalariform.formatter.preferences._

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
  .settings(publishingSettings ++ (publishArtifact in(Compile, packageDoc) := false))
  .settings(defaultSettings(): _*)
  .settings(
    targetJvm := "jvm-1.8",
    scalaVersion := "2.12.11",
    routesImport += "controllers.Binders._",
    libraryDependencies ++= appDependencies,
    parallelExecution in Test := false,
    fork in Test := true,
    javaOptions in Test += "-Dlogger.resource=logback-test.xml",
    javaOptions in Test += "-Dmicroservice.services.user-management.url.host=http://localhost:11111",
    retrieveManaged := true,
    scalacOptions += "-feature",
    // Currently don't enable warning in value discard in tests until ScalaTest 3
    scalacOptions in (Compile, compile) += "-Ywarn-value-discard"//,
    //routesGenerator := StaticRoutesGenerator
  )
  .settings(sources in (Compile, doc) := Seq.empty)
  .configs(IntegrationTest)
  .settings(pipelineStages := Seq(digest, gzip))
  .settings(inConfig(IntegrationTest)(sbt.Defaults.testSettings) : _*)
  // Disable scalastyle awaiting release of version > 1.8.0 to fix parameter formatting for implicit parameters
  //    .settings(scalariformSettings: _*)
  //    .settings(ScalariformKeys.preferences := ScalariformKeys.preferences.value
  //      .setPreference(FormatXml, false)
  //      .setPreference(DoubleIndentClassDeclaration, false)
  //      .setPreference(DanglingCloseParenthesis, Preserve)
  //      .setPreference(AlignParameters, false)
  //      .setPreference(SpacesAroundMultiImports, false))
  .settings(compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value,
    (compile in Compile) <<= (compile in Compile) dependsOn compileScalastyle)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest <<= (baseDirectory in IntegrationTest)(base => Seq(base / "it")),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false)
  // Silhouette transitive dependencies require that the Atlassian repository be first in the resolver list
  .settings(resolvers := ("Atlassian Releases" at "https://maven.atlassian.com/public/") +: resolvers.value)
  .settings(resolvers ++= Seq(Resolver.bintrayRepo("hmrc", "releases"), Resolver.jcenterRepo))
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
