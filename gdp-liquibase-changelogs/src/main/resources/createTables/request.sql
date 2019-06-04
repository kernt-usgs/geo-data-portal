--liquibase formatted sql

--This is for the gdp schema

--changeset jiwalker:1createTableRequest
CREATE TABLE request (
	REQUEST_ID VARCHAR(100) NOT NULL PRIMARY KEY,
	WPS_ALGORITHM_IDENTIFIER VARCHAR(200),
	REQUEST_XML TEXT)
--rollback drop table request;