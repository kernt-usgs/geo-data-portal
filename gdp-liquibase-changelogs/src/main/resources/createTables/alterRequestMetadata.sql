--liquibase formatted sql

--changeset slarson:7alterRequestMetadata
ALTER TABLE request_metadata ALTER COLUMN user_agent TYPE TEXT;
