package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import gov.usgs.cida.gdp.coreprocessing.analysis.grid.Statistics1DWriter;
import gov.usgs.cida.gdp.coreprocessing.analysis.statistics.Statistics1D;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class FeatureTimeseriesStatiticsVisitor extends StationTimeseriesVisitor {

	private static final Logger log = LoggerFactory.getLogger(FeatureTimeseriesStatiticsVisitor.class);
	
	public final static String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	public final static String TIMEZONE = "UTC";
	
	private List<Object> featureNames;
	private Statistics1DWriter writer;
	private SimpleDateFormat dateFormat;
	
	private TimeseriesDataset dataset;
	private DateTime currentTimestep;
	private String tLabel;
	private Map<String, Statistics1D> stationValueMap;
	
	public FeatureTimeseriesStatiticsVisitor(List<Object> featureNames, Statistics1DWriter writer) {
		this.featureNames = featureNames;
		this.writer = writer;
		
		dateFormat = new SimpleDateFormat(DATE_FORMAT);
		dateFormat.setTimeZone(TimeZone.getTimeZone(TIMEZONE));
	}
	
	@Override
	public void traverseStart(TimeseriesDataset dataset) {
		try {
			this.dataset = dataset;
			writer.writeHeader(Statistics1DWriter.buildRowLabel(Statistics1DWriter.TIMESTEPS_LABEL, null));
		} catch (IOException ex) {
			log.trace("Couldn't write header",ex);
		}
	}
	
	@Override
	public void traverseEnd() {
		dataset = null;
		currentTimestep = null;
		tLabel = null;
		stationValueMap = null;
	}
	
	@Override
	public void timeStart(DateTime timestep) {
		super.timeStart(timestep);
		currentTimestep = timestep;
		tLabel = dateFormat.format(timestep.toDate());
	}
	

	
	@Override
	public void stationsStart() {
		super.stationsStart();
		stationValueMap = new LinkedHashMap<>();
		for (Object station : featureNames) {
			String stationName = station.toString();
			stationValueMap.put(stationName, new Statistics1D());
		}
	}
	
	@Override
	public void stationsEnd() {
		try {
			writer.writeRow(
					Statistics1DWriter.buildRowLabel(tLabel, null),
					stationValueMap.values(),
					null);
		} catch (IOException ex) {
			log.trace("Couldn't write row");
		}
	}

	@Override
	public void processStations() {
		super.processStations();
		if (null == currentTimestep || null == dataset) {
			throw new IllegalStateException("Visitor used in wrong sequence");
		}
		for (Object station : featureNames) {
			String stationName = station.toString();
			String value = dataset.getValue(stationName, currentTimestep);
			if (value != null) {
				stationValueMap.get(stationName).accumulate(Double.parseDouble(value));
			}
		}
	}

}
