waketi
======

A japanese chat bot named Waketi.

https://pokosho.com/b/waketi

## Development

### Create a database and tables

```shell script
docker-compose up -d
mysql -uroot -proot -h127.0.0.1 < script/create_table.sql
```

### Edit bot.properties

```shell script
cp conf/bot.properties.sample conf/bot.properties
vi conf/bot.properties
```

### Build

```shell script
./gradlew shadowJar
# Fill version number
java -Dfile.encoding=UTF-8 -Xmx400m -jar build/libs/waketi-*-all.jar
```
