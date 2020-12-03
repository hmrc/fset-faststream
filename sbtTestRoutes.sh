#!/bin/bash

sbt -J-Dapplication.router=testOnlyDoNotUseInAppConf.Routes -Dhttp.port=8101 -J-Xmx1G
