--liquibase formatted sql

--changeset jiwalker:2createTableInput
CREATE TABLE input (
	ID VARCHAR(100) NOT NULL PRIMARY KEY,
	REQUEST_ID VARCHAR(100),
	INPUT_IDENTIFIER VARCHAR(200),
	INPUT_VALUE VARCHAR(500))
--rollback drop table input;