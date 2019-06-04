--liquibase formatted sql

--changeset jiwalker:6createTableRequestMetadata
CREATE TABLE request_metadata (
	ID VARCHAR(100) NOT NULL PRIMARY KEY,
	REQUEST_ID VARCHAR(100),
	USER_AGENT VARCHAR(100))
--rollback drop table request_metadata;