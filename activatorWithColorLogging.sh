#!/bin/bash

activator shell -Dlogger.file=conf/application-dev-logger.xml -Dlog4j.configurationFile=file:conf/application-dev-logger-log4j2.xml
