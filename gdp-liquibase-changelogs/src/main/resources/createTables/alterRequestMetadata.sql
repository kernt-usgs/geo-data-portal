--liquibase formatted sql

--changeset slarson:7alterRequestMetadata
ALTER TABLE request_metadata ALTER COLUMN user_agent TYPE TEXT;

-- changeset jiwalker:8addSizeInfo
ALTER TABLE request_metadata ADD COLUMN jobid TEXT,
	ADD COLUMN timesteps INTEGER,
	ADD COLUMN gridcells BIGINT,
	ADD COLUMN varcount INTEGER,
	ADD COLUMN cellsize_bytes INTEGER,
	ADD COLUMN bounding_rect TEXT,
	ADD COLUMN data_retrieved BIGINT,
	ADD COLUMN data_returned BIGINT;

-- changeset jiwalker:9addIdConstraint
ALTER TABLE request_metadata ADD CONSTRAINT unique_request_constraint UNIQUE (request_id); 