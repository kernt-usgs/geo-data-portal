--liquibase formatted sql

--changeset slarson:7queueThrottleTableStatus
CREATE TABLE throttle_queue (
	ID serial NOT NULL PRIMARY KEY,
	REQUEST_ID VARCHAR(100),
	STATUS VARCHAR(50),
	ENQUEUED TIMESTAMP without time zone,
	DEQUEUED TIMESTAMP without time zone
	)
--rollback drop table throttle_queue;