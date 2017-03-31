package gov.usgs.cida.gdp.wps.queue;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jiwalker
 */
public class QueueServletListener implements ServletContextListener {
	
	private static final Logger log = LoggerFactory.getLogger(QueueServletListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // might as well initialize manager
        ExecuteRequestManager.getInstance();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ExecuteRequestManager.getInstance().shutdown();
    }
	
}
