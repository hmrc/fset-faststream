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

import sbt._

object FrontendBuild extends Build with MicroService {
  import scala.util.Properties.envOrElse

  val appName = "csr-fasttrack-frontend"
  val appVersion = envOrElse("CSR_FASTTRACK_FRONTEND_VERSION", "999-SNAPSHOT")

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object Versions {

  val ficus         = "1.1.2"
  val cacheClient   = "5.3.0"
  val frontend      = "6.4.0"
  val playConfig    = "2.0.1"
  val playHealth    = "1.1.0"
  val urlBuilder    = "1.0.0"
  val httpclient    = "4.3.4"
  val jsonLogger    = "2.1.1"
  val scalatest     = "2.2.2"
  val pegdown       = "1.4.2"
  val jsoup         = "1.7.3"
  val wiremock      = "1.57"
  val hmrctest      = "1.4.0"
  val scalatestplus = "1.2.0"
  val silhouette    = "2.0.1"
}

private object AppDependencies {

  import Versions._

  val compile = Seq(
    "net.ceedubs"               %% "ficus"                    % ficus,
    "uk.gov.hmrc"               %% "http-caching-client"      % cacheClient,
    "uk.gov.hmrc"               %% "frontend-bootstrap"       % frontend,
    "uk.gov.hmrc"               %% "play-config"              % playConfig,
    "uk.gov.hmrc"               %% "play-json-logger"         % jsonLogger,
    "uk.gov.hmrc"               %% "play-health"              % playHealth,
    "uk.gov.hmrc"               %% "url-builder"              % urlBuilder,
    "org.apache.httpcomponents" %  "httpclient"               % httpclient,
    "com.mohiva"                %% "play-silhouette"          % silhouette
  )

  val test = Seq(
    "org.scalatest"             %% "scalatest"                % scalatest     % "test",
    "org.scalatestplus"         %% "play"                     % scalatestplus % "test",
    "org.pegdown"               %  "pegdown"                  % pegdown       % "test",
    "org.jsoup"                 %  "jsoup"                    % jsoup         % "test",
    "com.github.tomakehurst"    %  "wiremock"                 % wiremock      % "test",
    "uk.gov.hmrc"               %% "hmrctest"                 % hmrctest      % "test",
    "com.mohiva"                %% "play-silhouette-testkit"  % silhouette    % "test"
  )

  def apply() = compile ++ test
}
