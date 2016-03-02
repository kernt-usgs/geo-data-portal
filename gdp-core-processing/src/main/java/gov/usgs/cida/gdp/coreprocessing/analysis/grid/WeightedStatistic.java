package gov.usgs.cida.gdp.coreprocessing.analysis.grid;

import gov.usgs.cida.gdp.coreprocessing.analysis.Statistic;
import gov.usgs.cida.gdp.coreprocessing.analysis.statistics.IStatistics1D;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public enum WeightedStatistic implements Statistic {

	MEAN("%f", "%s") {
				@Override
				public Number getValue(IStatistics1D wsa) {
					return (float) wsa.getMean();
				}
			},
	MINIMUM("%f", "%s") {
				@Override
				public Number getValue(IStatistics1D wsa) {
					return (float) wsa.getMinimum();
				}
			},
	MAXIMUM("%f", "%s") {
				@Override
				public Number getValue(IStatistics1D wsa) {
					return (float) wsa.getMaximum();
				}
			},
	VARIANCE("%f", "%s^2") {
				@Override
				public Number getValue(IStatistics1D wsa) {
					return (float) wsa.getSampleVariance();
				}
			},
	STD_DEV("%f", "%s") {
				@Override
				public Number getValue(IStatistics1D wsa) {
					return (float) wsa.getSampleStandardDeviation();
				}
			},
	SUM("%f") {
				@Override
				public Number getValue(IStatistics1D wsa) {
					return (float) wsa.getSum();
				}
			},
	COUNT("%d") {
				@Override
				public Number getValue(IStatistics1D wsa) {
					return wsa.getCount();
				}
			};

	private final String valueFormat;
	private final String unitFormat;

	WeightedStatistic(String format) {
		this(format, null);
	}

	WeightedStatistic(String format, String unitFormat) {
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
