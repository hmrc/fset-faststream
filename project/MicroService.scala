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

import sbt.Tests.{Group, SubProcess}
import sbt._

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
