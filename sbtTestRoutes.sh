#!/bin/bash

sbt -jvm-debug 7101 -J-Dapplication.router=testOnlyDoNotUseInAppConf.Routes -Dhttp.port=8101
