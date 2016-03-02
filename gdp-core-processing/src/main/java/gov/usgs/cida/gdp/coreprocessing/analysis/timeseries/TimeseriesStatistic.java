package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import gov.usgs.cida.gdp.coreprocessing.analysis.Statistic;
import gov.usgs.cida.gdp.coreprocessing.analysis.statistics.IStatistics1D;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public enum TimeseriesStatistic implements Statistic {
	// Introducing this for when we are just performing pass-thrus
	VALUE("%f", "%s") {
		@Override
		public Number getValue(IStatistics1D wsa) {
			return (wsa.getCount() == 1) ? (float) wsa.getMean() : Float.NaN;
		}
	};
	
	private final String valueFormat;
	private final String unitFormat;

	TimeseriesStatistic(String format) {
		this(format, null);
	}

	TimeseriesStatistic(String format, String unitFormat) {
		this.valueFormat = format;
		this.unitFormat = unitFormat;
	}

	@Override
	public String getValueFormat() {
		return valueFormat;
	}

	@Override
	public String getUnitFormat() {
		return unitFormat;
	}

	@Override
	public boolean getNeedsUnits() {
		return unitFormat != null && unitFormat.length() > 0;
	}
}
