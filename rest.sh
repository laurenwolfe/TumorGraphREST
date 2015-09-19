#!/usr/bin/env bash
grails war --profile=web
docker build -t lulumialu/rest .
docker run -d -p 8080:8080
#docker push lulumialu/rest
#say "complete"
