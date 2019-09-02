#!/bin/bash

sbt -jvm-debug 7281 -J-Dapplication.router=testOnlyDoNotUseInAppConf.Routes -Dhttp.port=9284 -Dplay.filters.headers.contentSecurityPolicy='www.google-analytics.com'
