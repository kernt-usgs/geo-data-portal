--liquibase formatted sql

--changeset slarson:1insertToggleData
INSERT INTO throttle_queue_toggle (enabled, toggle_type) VALUES (false, 'THROTTLE');
INSERT INTO throttle_queue_toggle (enabled, toggle_type) VALUES (false, 'REDUNDANT_REQUEST_CHECK');