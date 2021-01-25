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

### Build