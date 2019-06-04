--liquibase formatted sql

--changeset jiwalker:4createTableResponse
CREATE TABLE response (
	ID VARCHAR(100) NOT NULL PRIMARY KEY,
	REQUEST_ID VARCHAR(100),
	WPS_ALGORITHM_IDENTIFIER VARCHAR(200),
	STATUS VARCHAR(50),
	PERCENT_COMPLETE INTEGER,
	CREATION_TIME TIMESTAMP with time zone,
	START_TIME TIMESTAMP with time zone,
	END_TIME TIMESTAMP with time zone,
	EXCEPTION_TEXT TEXT)
--rollback drop table response;