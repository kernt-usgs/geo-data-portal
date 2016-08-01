--liquibase formatted sql

--changeset slarson:1insertToggleData
Insert into throttle_queue_toggle (enabled, toggle_type) values (false, 'THROTTLE');
Insert into throttle_queue_toggle (enabled, toggle_type) values (false, 'REDUNDANT_REQUEST_CHECK');