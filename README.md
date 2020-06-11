# Civil Service Resourcing Fast Stream Web Application

[![Build Status](https://travis-ci.org/hmrc/fset-faststream-frontend.svg?branch=fset-558)](https://travis-ci.org/hmrc/fset-faststream-frontend)

### Summary
This repository contains the front-end for the Civil Service Resourcing Fast Stream programme.

### Requirements
This service is written in Scala and Play, so needs at least a [JRE] to run.

### Testing
To run using sbtTestRoutes file 

        ./sbtTestRoutes.sh

or using SBT with port number 

To run it locally
	
	sbt -Dhttp.port=9284 run
	

If you go to `http://localhost:9284/fset-fast-stream/signin` you can see the landing page

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
