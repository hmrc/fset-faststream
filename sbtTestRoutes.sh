#!/bin/bash

sbt -jvm-debug 7284 -J-Dplay.http.router=testOnlyDoNotUseInAppConf.Routes -Dhttp.port=9284 -Dplay.filters.headers.contentSecurityPolicy='www.google-analytics.com'
