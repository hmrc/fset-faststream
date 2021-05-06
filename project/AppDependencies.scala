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

object AppDependencies {

  object Versions {
    val playVersion       = "4.2.0"
    val silhouetteVersion = "6.1.1"
  }

  import Versions._

  val compile = Seq(
    "com.iheart"                %% "ficus"                                    % "1.5.0",
    "uk.gov.hmrc"               %% "http-caching-client"                      % "9.1.0-play-26",
    "uk.gov.hmrc"               %% "bootstrap-frontend-play-28"               % playVersion,
    "com.typesafe.play"         %% "play-json-joda"                           % "2.6.10",
    "uk.gov.hmrc"               %% "url-builder"                              % "3.4.0-play-27",
    "org.apache.httpcomponents" %  "httpclient"                               % "4.5.3",
    "org.apache.httpcomponents" %  "httpcore"                                 % "4.4.5",
    "com.mohiva"                %% "play-silhouette"                          % silhouetteVersion,
    "com.mohiva"                %% "play-silhouette-password-bcrypt"          % silhouetteVersion,
    "com.mohiva"                %% "play-silhouette-crypto-jca"               % silhouetteVersion,
    "com.mohiva"                %% "play-silhouette-persistence"              % silhouetteVersion,
    "net.codingwell"            %% "scala-guice"                              % "4.1.0",
    "com.github.nscala-time"    %% "nscala-time"                              % "2.24.0",
    ws
  )

  val test = Seq(
    "org.scalatestplus.play"    %% "scalatestplus-play"           % "5.1.0"           % sbt.Test,
    // Gives you access to MockitoSugar as it is no longer available in scalatestplus-play
    "org.scalatestplus"         %% "mockito-3-4"                  % "3.2.8.0"         % Test,
    "com.vladsch.flexmark"      %  "flexmark-all"                 % "0.36.8"          % Test,
    "org.mockito"               %  "mockito-core"                 % "3.9.0"           % sbt.Test,
    "org.jsoup"                 %  "jsoup"                        % "1.7.3"           % sbt.Test,
    "com.github.tomakehurst"    %  "wiremock"                     % "1.57"            % sbt.Test,
    "com.mohiva"                %% "play-silhouette-testkit"      % silhouetteVersion % sbt.Test,
    "uk.gov.hmrc"               %% "bootstrap-test-play-28"       % playVersion       % sbt.Test
  )

  def apply() = compile ++ test
}
