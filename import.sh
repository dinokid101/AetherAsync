#!/bin/bash
#Usage: ./import.sh <version> -d [file]
# -d: download and install
# <version>: version number
# file: file path to runemate
file="Runemate.jar"
version="2.54.2.1"
mvn install:install-file -Dfile=$file -DgroupId=com.runemate -DartifactId=runemate -Dversion=$version -Dpackaging=jar -DgeneratePom=true