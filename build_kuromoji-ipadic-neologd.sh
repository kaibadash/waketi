#!/bin/sh
mkdir -p ./WEB-INF/lib/
wget https://raw.githubusercontent.com/kazuhira-r/kuromoji-with-mecab-neologd-buildscript/master/build-atilika-kuromoji-with-mecab-ipadic-neologd.sh
chmod a+x build-atilika-kuromoji-with-mecab-ipadic-neologd.sh
./build-atilika-kuromoji-with-mecab-ipadic-neologd.sh
cp kuromoji-ipadic-neologd-*.jar WEB-INF/lib

