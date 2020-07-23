#!/bin/bash

sbt -J-Dplay.http.router=testOnlyDoNotUseInAppConf.Routes -Dhttp.port=8101 -J-Xmx1G
