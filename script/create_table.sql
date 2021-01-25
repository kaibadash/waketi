DROP DATABASE IF EXISTS waketi;
CREATE DATABASE waketi DEFAULT CHARACTER SET utf8;
USE waketi;

DROP TABLE IF EXISTS word;
CREATE TABLE word (
WORD_ID BIGINT AUTO_INCREMENT PRIMARY KEY ,
POS_ID BIGINT,
WORD TEXT,
WORD_COUNT INTEGER,
TIME INTEGER);

DROP TABLE IF EXISTS chain;
CREATE TABLE chain (
CHAIN_ID BIGINT AUTO_INCREMENT PRIMARY KEY ,
PREFIX01 BIGINT,
PREFIX02 BIGINT,
SUFFIX BIGINT DEFAULT NULL,
START BOOL,
FOREIGN KEY (PREFIX01) references word (WORD_ID),
FOREIGN KEY (PREFIX02) references word (WORD_ID),
FOREIGN KEY (SUFFIX) references word (WORD_ID),
INDEX INDEX_PREFIX01 (PREFIX01),
INDEX INDEX_PREFIX02 (PREFIX02),
INDEX INDEX_SUFFIX (SUFFIX)
);

DROP TABLE IF EXISTS reply;
CREATE TABLE reply (
TWEET_ID BIGINT PRIMARY KEY,
USER_ID BIGINT,
TIME INTEGER,
INDEX INDEX_USER_ID (USER_ID),
INDEX INDEX_USER_ID_TIME (USER_ID, TIME)
);

/* test */
SELECT c.prefix01, w1.word_id, w1.word,
c.prefix02, w2.word_id, w2.word,
c.suffix, w3.word_id, w3.word
 FROM chain c
 INNER JOIN word w1
 ON c.prefix01 = w1.word_id
 INNER JOIN word w2
 ON c.prefix02 = w2.word_id
 INNER JOIN word w3
 ON c.suffix = w3.word_id;
