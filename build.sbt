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

import play.sbt.PlayImport.PlayKeys.playDefaultPort
import play.sbt.routes.RoutesKeys.*
import sbt.Keys.*
import sbt.*
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings, targetJvm}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

val appName = "fset-faststream"
val appDependencies : Seq[ModuleID] = AppDependencies()

lazy val playSettings : Seq[Setting[?]] = Seq.empty

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / majorVersion := 1

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(playSettings *)
  .settings(scalaSettings *)
  .settings(defaultSettings() *)
  .settings(playDefaultPort := 8101)
  .settings(
    routesImport += "controllers.Binders._",
    targetJvm := "jvm-1.8",
    libraryDependencies ++= appDependencies,

    Test / parallelExecution := false,
    Test / fork := false,
    retrieveManaged := true,
    scalacOptions += "-feature",
    // Currently don't enable warning in value discard in tests until ScalaTest 3
    Compile / compile / scalacOptions += "-Ywarn-value-discard",
    Compile / compile / scalacOptions += "-Xlint:-missing-interpolator,_",
    Compile / compile / scalacOptions += "-Ywarn-unused"
  )
  // Even though log4j does not appear in the dependency graph, sbt still downloads it into the Coursier cache
  // when we compile. It is version log4j-1.2.17.jar, which contains the security vulnerabilities so as a workaround
  // we exclude any log4j library here
  .settings(excludeDependencies += "log4j" % "log4j")
  .settings(Compile / doc / sources := Seq.empty)
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

  // Temporarily remove due to the following error on jenkins:
  //  [error] java.lang.StackOverflowError
  //  [error]    at scalariform.utils.Utils$.$anonfun$groupBy$1(Utils.scala:38)
  //  .settings(compileScalastyle := scalastyle.in(Compile).toTask("").value,
  //    (compile in Compile) := ((compile in Compile) dependsOn compileScalastyle).value
  //  )

  .settings(resolvers ++= Seq(Resolver.jcenterRepo))
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)

lazy val it = (project in file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(
      Test / javaOptions += "-Dconfig.resource=application.it.conf"
  )
  .settings(DefaultBuildSettings.itSettings(true))
  .settings(DefaultBuildSettings.addTestReportOption(Test, "int-test-reports"))
