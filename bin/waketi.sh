#!/bin/sh
export LANG=ja_JP.UTF-8

processCheck=`ps auxw | egrep -v grep | grep "TwitterBot" | egrep -v "TwitterBot -s"`
if [ ${#processCheck} -gt 0 ]
then
	echo "skip at `date`" >> ~kaiba/program/waketi/kill.log
	kill -9 `ps auxw | egrep -v grep | grep "TwitterBot" | egrep -v "TwitterBot -s" | awk '{print $2}'`
fi

cd ~kaiba/program/waketi
java -Dfile.encoding=UTF-8 -Xmx400m -classpath ./waketi.jar:./lib/* com.pokosho.bot.twitter.TwitterBot $1

