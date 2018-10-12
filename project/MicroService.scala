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

import de.heikoseeberger.sbtheader.{ AutomateHeaderPlugin, HeaderPlugin }
import play.routes.compiler.StaticRoutesGenerator
import play.sbt.routes.RoutesKeys._
import sbt.Keys._
import sbt.Tests.{ Group, SubProcess }
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

trait MicroService {

  import uk.gov.hmrc._
  import DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings, targetJvm}
  import TestPhases._
  import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
  import play.sbt.routes.RoutesKeys.{ routesImport, routesGenerator }

  import scalariform.formatter.preferences._

  val appName: String
  val appDependencies : Seq[ModuleID]

  lazy val plugins : Seq[Plugins] = Seq(SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  lazy val playSettings : Seq[Setting[_]] = Seq.empty

  lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins : _*)
    .settings(majorVersion := 1)
    .settings(playSettings : _*)
    .settings(scalaSettings: _*)
    .settings(publishingSettings)
    .settings(defaultSettings(): _*)
    .settings(
      routesGenerator := StaticRoutesGenerator,
      routesImport += "controllers.Binders._",
      targetJvm := "jvm-1.8",
      scalaVersion := "2.11.11",
      libraryDependencies ++= appDependencies,
      parallelExecution in Test := false,
      fork in Test := false,
      retrieveManaged := true,
      scalacOptions += "-feature",
      // Currently don't enable warning in value discard in tests until ScalaTest 3
      scalacOptions in(Compile, compile) += "-Ywarn-value-discard",
      scalacOptions in(Compile, compile) += "-Xlint:-missing-interpolator,_",
      scalacOptions in(Compile, compile) += "-Ywarn-unused")
    .settings(sources in (Compile, doc) := Seq.empty)
    .settings(HeaderPlugin.settingsFor(IntegrationTest))
    .configs(IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.testSettings ++ AutomateHeaderPlugin.automateFor(IntegrationTest)) : _*)
    .settings(inConfig(IntegrationTest)(Defaults.itSettings))
    // Disable Scalastyle & Scalariform temporarily, as it is currently intermittently failing when building
//    .settings(scalariformSettings: _*)
//    .settings(ScalariformKeys.preferences := ScalariformKeys.preferences.value
//      .setPreference(FormatXml, false)
//      .setPreference(DoubleIndentClassDeclaration, false)
//      .setPreference(DanglingCloseParenthesis, Preserve))
//     // Exclude the test data generation files as they cause stackoverflows in Scalariform due to their size
//     .settings(excludeFilter in ScalariformKeys.format := ((excludeFilter in ScalariformKeys.format).value ||
//        "Firstnames.scala" ||
//       "Lastnames.scala"))
//    .settings(compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value,
//      (compile in Compile) <<= (compile in Compile) dependsOn compileScalastyle)
    .settings(
      Keys.fork in IntegrationTest := false,
      unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest)(base => Seq(
        base / "it", base / "test/model", base / "test/testkit"
      )).value,
      addTestReportOption(IntegrationTest, "int-test-reports"),
      testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
      parallelExecution in IntegrationTest := false)
    .settings(
      resolvers := Seq(
        Resolver.bintrayRepo("hmrc", "releases"),
        Resolver.typesafeRepo("releases"),
        Resolver.jcenterRepo
      )
    )
    .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
}

private object TestPhases {

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
    tests map {
      test => Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions =
        Seq("-Dtest.name=" + test.name,
          "-Dmongodb.uri=mongodb://localhost:27017/test-fset-faststream?rm.nbChannelsPerNode=2&writeConcernJ=false",
          "-Dmongodb.failoverStrategy.retries=10",
          "-Dmongodb.failoverStrategy.delay.function=fibonacci",
          "-Dmongodb.failoverStrategy.delay.factor=1")
      )))
    }
}
