--liquibase formatted sql

--changeset slarson:7alterRequestMetadata
ALTER TABLE request_metadata ALTER COLUMN user_agent TYPE TEXT;

-- changeset jiwalker:8addSizeInfo
ALTER TABLE request_metadata ADD COLUMN jobid TEXT,
	ADD COLUMN timesteps INTEGER,
	ADD COLUMN gridcells BIGINT,
	ADD COLUMN varcount INTEGER,
	ADD COLUMN cellsize_bytes SMALLINT,
	ADD COLUMN bounding_rect TEXT;