--liquibase formatted sql

--changeset jiwalker:3createTableOutputDefinition
CREATE TABLE output_definition (
	ID VARCHAR(100) NOT NULL PRIMARY KEY,
	REQUEST_ID VARCHAR(100),
	MIME_TYPE VARCHAR(100),
	OUTPUT_IDENTIFIER VARCHAR(200))
--rollback drop table output_definition;