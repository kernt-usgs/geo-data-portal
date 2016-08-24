package gov.usgs.cida.gdp.wps.queue;

/**
 *
 * @author smlarson
 */

public enum ThrottleStatus {
	ACCEPTED,
        PREENQUEUE,
        ENQUEUE,
        WAITING,
        STARTED,
	PROCESSED,
        UNKNOWN
}
