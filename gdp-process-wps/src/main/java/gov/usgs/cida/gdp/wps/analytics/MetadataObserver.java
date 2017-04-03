package gov.usgs.cida.gdp.wps.analytics;

import org.n52.wps.server.observerpattern.IObserver;
import org.n52.wps.server.observerpattern.ISubject;

import java.util.UUID;

/**
 *
 * @author jiwalker
 */
public class MetadataObserver implements IObserver {

	private final UUID requestId;
	
	public MetadataObserver(UUID requestId) {
		this.requestId = requestId;
	}
	
	@Override
	public void update(ISubject is) {
		MetadataLoggingWorker worker = new MetadataLoggingWorker(requestId, is.getState());
		worker.poolJob();
	}
	
}
