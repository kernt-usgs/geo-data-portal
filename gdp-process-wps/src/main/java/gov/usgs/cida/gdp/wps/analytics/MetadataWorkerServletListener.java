package gov.usgs.cida.gdp.wps.analytics;

import java.util.List;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jiwalker
 */
public class MetadataWorkerServletListener implements ServletContextListener {
	
	private static final Logger log = LoggerFactory.getLogger(MetadataWorkerServletListener.class);

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		// Metadata initialization happens on demand
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		List<MetadataLoggingWorker> notFinished = MetadataLoggingWorker.shutdownNow();
		for (MetadataLoggingWorker worker : notFinished) {
			log.debug("Unable to finish metadata log for: " + worker.toString());
		}
	}
	
}
