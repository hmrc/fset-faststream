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
import play.sbt.PlayImport.ws

object FrontendBuild extends Build with MicroService {
  val appName = "fset-faststream-frontend"
  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object Versions {
  val silhouette    = "5.0.7"
}

private object AppDependencies {
  import Versions._

  val compile = Seq(
    "com.iheart"                %% "ficus"                                    % "1.2.6",
    "uk.gov.hmrc"               %% "http-caching-client"                      % "9.1.0-play-26",
    "uk.gov.hmrc"               %% "bootstrap-frontend-play-26"               % "2.24.0",
    "com.typesafe.play"         %% "play-json-joda"                           % "2.6.10",
    "uk.gov.hmrc"               %% "url-builder"                              % "2.1.0",
    "org.apache.httpcomponents" %  "httpclient"                               % "4.5.3",
    "org.apache.httpcomponents" %  "httpcore"                                 % "4.4.5",
    "com.mohiva"                %% "play-silhouette"                          % silhouette,
    "com.mohiva"                %% "play-silhouette-password-bcrypt"          % silhouette,
    "com.mohiva"                %% "play-silhouette-crypto-jca"               % silhouette,
    "com.mohiva"                %% "play-silhouette-persistence"              % silhouette,
    "net.codingwell"            %% "scala-guice"                              % "4.1.0",
    "com.github.nscala-time"    %% "nscala-time"                              % "2.24.0",
    ws
  )

  val test = Seq(
    "org.scalatestplus.play"    %% "scalatestplus-play"           % "3.1.3"       % "test",
    "org.mockito"               %  "mockito-all"                  % "1.10.19"     % "test",
    "org.pegdown"               %  "pegdown"                      % "1.4.2"       % "test",
    "org.jsoup"                 %  "jsoup"                        % "1.7.3"       % "test",
    "com.github.tomakehurst"    %  "wiremock"                     % "1.57"        % "test",
    "uk.gov.hmrc"               %% "hmrctest"                     % "3.0.0"       % "test",
    "com.mohiva"                %% "play-silhouette-testkit"      % silhouette    % "test",
    "uk.gov.hmrc"               %% "bootstrap-test-play-26"       % "2.24.0"
  )

  def apply() = compile ++ test
}
