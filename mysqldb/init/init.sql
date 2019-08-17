CREATE USER 'maxwell'@'%' IDENTIFIED BY 'XXXXXX';
GRANT ALL ON maxwell.* TO 'maxwell'@'%';
GRANT SELECT, REPLICATION CLIENT, REPLICATION SLAVE ON *.* TO 'maxwell'@'%';

CREATE DATABASE test;
USE test;
CREATE TABLE testtable (
    `id` varchar(128),
    `age` varchar(128)
);