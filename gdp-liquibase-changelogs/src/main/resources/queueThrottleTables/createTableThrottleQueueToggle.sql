--liquibase formatted sql

--changeset slarson:8createThrottleQueueToggle
CREATE TABLE throttle_queue_toggle (
	ID serial NOT NULL PRIMARY KEY,
        ENABLED BOOLEAN,
        TOGGLE_TYPE VARCHAR(50))
        
--rollback drop table throttle_queue;
