#!/bin/bash

sbt -mem 2048 -jvm-debug 7284 -J-Dplay.http.router=testOnlyDoNotUseInAppConf.Routes -Dhttp.port=9284
