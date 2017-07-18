# Civil Service Fast Stream Service 

[![Build Status](https://travis-ci.org/hmrc/fset-faststream.svg?branch=master)](https://travis-ci.org/hmrc/fset-faststream)

### Summary
This repository provides a service containing business logic and storage to support the Fast Stream project

### Requirements
This service is written in Scala and Play, so needs at least a [JRE] to run.

### Testing
To run it locally
	
	sbt -Dhttp.port=9282 run
	

If you go to `http://localhost:9000/fset-fast-stream/signin` you can see the landing page

### Secrets File

Create a file at ~/.csr/.secrets containing:

    testdata {
        cubiks {
            url = "http://secret/path/to/cubiks"
        }
    }

Get the correct path to cubiks for local development environments from another maintainer

### Secrets File

Create a file at ~/.csr/.secrets containing:

    testdata {
        cubiks {
            url = "http://secret/path/to/cubiks"
        }
    }

Get the correct path to cubiks for local development environments from a colleague

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
