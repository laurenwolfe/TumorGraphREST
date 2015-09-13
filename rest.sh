#!/usr/bin/env bash
grails war --profile=web
docker build -t lulumialu/rest .
docker push lulumialu/rest
say "complete"