package gov.usgs.cida.gdp.wps.analytics;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous calls to add extra process metadata to database.
 * 
 * There isn't a need to do this as a transaction, so spawn a thread to
 * deal with this separately, so process can continue.
 * 
 * @author jiwalker
 */
public class MetadataLoggingWorker implements Runnable {
	
	private static ExecutorService workerPool = null;
	
	private UUID requestId;
	private Object state;
	
	public MetadataLoggingWorker(UUID requestId, Object state) {
		initWorkerPool();
		this.requestId = requestId;
		this.state = state;
	}
	
	public synchronized static void initWorkerPool() {
		if (workerPool == null) {
			workerPool = Executors.newSingleThreadExecutor();
		}
	}
	
	/*
	 * The metadata isn't all that important, just shut it all down
	 */
	public static List<MetadataLoggingWorker> shutdownNow() {
		List<MetadataLoggingWorker> stopped = Lists.newArrayList();
		if (workerPool != null) {
			List<Runnable> runsStopped = workerPool.shutdownNow();
			for (Runnable run : runsStopped) {
				if (run instanceof MetadataLoggingWorker) {
					stopped.add((MetadataLoggingWorker)run);
				}
			}
		}
		return stopped;
	}
	
	public void poolJob() {
		workerPool.execute(this);
	}
	
	@Override
	public void run() {
		if (state instanceof IMetadataLogger) {
			((IMetadataLogger)state).log(requestId.toString());
		} else {
			// what other types will I have
		}
	}
	
}
