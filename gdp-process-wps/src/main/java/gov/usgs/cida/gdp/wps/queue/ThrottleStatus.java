package gov.usgs.cida.gdp.wps.queue;

/**
 *
 * @author smlarson
 */

public enum ThrottleStatus {
	ACCEPTED,
        ENQUEUE,
        WAITING,
        STARTED,
	PROCESSED,
        UNKNOWN
}
