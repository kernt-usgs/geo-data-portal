package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import gov.usgs.cida.gdp.wps.algorithm.GDPAlgorithmUtil;
import gov.usgs.cida.gdp.wps.analytics.DataFetchInfo;

import java.util.Date;

import org.geotools.feature.FeatureCollection;
import org.n52.wps.server.observerpattern.ISubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.nc2.dt.GridDatatype;

/**
 * Calculate the size of data fetched from source.  This is logged to compare to
 * the download size for how much bandwidth is saved in summarizing the data.
 *
 * @author jiwalker
 *
 */
public class FetchSizeAlgorithmHeuristic extends AlgorithmHeuristic {

	private static final Logger log = LoggerFactory.getLogger(FetchSizeAlgorithmHeuristic.class);

	private ISubject algorithm;
	private FeatureCollection<?, ?> featureCollection;
	private Date dateTimeStart;
	private Date dateTimeEnd;
	private boolean requireFullCoverage;
	
	private long totalDataPulled;
	private int variableCount;
	private DataFetchInfo info;
	
	public FetchSizeAlgorithmHeuristic(ISubject algorithm, FeatureCollection<?, ?> featureCollection,
			Date dateTimeStart, Date dateTimeEnd, boolean requireFullCoverage) {
		this.algorithm = algorithm;
		this.featureCollection = featureCollection;
		this.dateTimeStart = dateTimeStart;
		this.dateTimeEnd = dateTimeEnd;
		this.requireFullCoverage = requireFullCoverage;
		
		this.totalDataPulled = 0L;
		this.variableCount = 0;
	}

	/**
	 * Throws exception if heuristic fails.
	 * @param gridDatatype 
	 */
	@Override
	public void traverseStart(GridDatatype gridDatatype) {
		GDPAlgorithmUtil.DataCube dataCube = GDPAlgorithmUtil.calculateDataCube(
				gridDatatype, featureCollection, dateTimeStart, dateTimeEnd, requireFullCoverage);
		totalDataPulled += dataCube.totalSize;
		variableCount++;
		int gridCells = dataCube.xLength * dataCube.xLength;
		// stubbing in bouning rect for now
		// TODO get real bounding rect
		info = new DataFetchInfo(totalDataPulled, gridCells, dataCube.tLength, dataCube.dataTypeSize, variableCount, "");
	}
	
	@Override
	public void traverseEnd() {
		algorithm.update(info);
	}
}
