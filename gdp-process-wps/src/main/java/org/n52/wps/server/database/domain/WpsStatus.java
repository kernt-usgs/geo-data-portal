package org.n52.wps.server.database.domain;

import net.opengis.wps.x100.ProcessFailedType;
import net.opengis.wps.x100.ProcessStartedType;
import net.opengis.wps.x100.StatusType;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public enum WpsStatus {
	ACCEPTED,
	STARTED,
	PAUSED,
	SUCCEEDED,
	FAILED,
	UNKNOWN;
	
	public static WpsStatus lookup(StatusType type) {
		WpsStatus ret;
		if (type.getProcessAccepted() != null) {
			ret = ACCEPTED;
		} else if (type.getProcessStarted() != null) {
			ret = STARTED;
		} else if (type.getProcessPaused() != null) {
			ret = PAUSED;
		} else if (type.getProcessSucceeded() != null) {
			ret = SUCCEEDED;
		} else if (type.getProcessFailed() != null) {
			ret = FAILED;
		} else {
			ret = UNKNOWN;
		}
		return ret;
	}
}
