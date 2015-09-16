#!/usr/bin/env bash
grails war --profile=web
docker build --rm -t lulumialu/rest .
docker push lulumialu/rest
say "complete"