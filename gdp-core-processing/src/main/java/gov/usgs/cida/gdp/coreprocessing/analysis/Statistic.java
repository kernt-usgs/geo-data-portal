package gov.usgs.cida.gdp.coreprocessing.analysis;

import gov.usgs.cida.gdp.coreprocessing.analysis.statistics.IStatistics1D;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public interface Statistic {
	public String getValueFormat();
	public String getUnitFormat();
	public boolean getNeedsUnits();
	public Number getValue(IStatistics1D wsa);
	public String name();
	
}
