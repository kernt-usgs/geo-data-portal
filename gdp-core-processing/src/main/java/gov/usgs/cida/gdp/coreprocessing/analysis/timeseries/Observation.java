package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import org.joda.time.DateTime;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class Observation {

	public ObservationMetadata getMetadata() {
		return metadata;
	}
	
	public static final String POINT_ELEMENT = "point";
	public static final String TIME_ELEMENT = "time";
	public static final String VALUE_ELEMENT = "value";

	private boolean ready;
	private ObservationMetadata metadata;
	private DateTime time;
	private String value;
	
	public Observation() {
		this.ready = false;
	}
	
	protected boolean isReady() {
		return ready;
	}
	
	protected void setReady(boolean ready) {
		this.ready = ready;
	}
	
	public ObservationMetadata metadata() {
		return this.metadata;
	}
	
	public Observation metadata(ObservationMetadata metadata) {
		this.metadata = metadata;
		return this;
	}
	
	public DateTime time() {
		return this.time;
	}
	
	public Observation time(DateTime time) {
		this.time = time;
		return this;
	}
	
	public String value() {
		return this.value;
	}
	
	public Observation value(String value) {
		this.value = value;
		return this;
	}
	
	
	/* Making into a bean for BeanUtils, should have never gotten too fancy */
	public void setMetadata(ObservationMetadata metadata) {
		this.metadata = metadata;
	}

	public DateTime getTime() {
		return time;
	}

	public void setTime(DateTime time) {
		this.time = time;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
