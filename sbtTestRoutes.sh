#!/bin/bash

sbt -J-Dapplication.router=testOnlyDoNotUseInAppConf.Routes -Dhttp.port=9284 -Dplay.filters.headers.contentSecurityPolicy='www.google-analytics.com'
