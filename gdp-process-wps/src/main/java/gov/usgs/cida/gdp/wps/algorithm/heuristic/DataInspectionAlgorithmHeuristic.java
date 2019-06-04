package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Polygon;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridCellGeometry;
import gov.usgs.cida.gdp.wps.algorithm.GDPAlgorithmUtil;
import gov.usgs.cida.gdp.wps.analytics.DataFetchInfo;

import java.util.Date;
import java.util.logging.Level;

import org.geotools.feature.FeatureCollection;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.referencing.operation.DefaultMathTransformFactory;
import org.n52.wps.server.observerpattern.ISubject;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.nc2.dt.GridCoordSystem;

import ucar.nc2.dt.GridDatatype;

/**
 * Calculate the size of data fetched from source.  This is logged to compare to
 * the download size for how much bandwidth is saved in summarizing the data.
 *
 * @author jiwalker
 *
 */
public class DataInspectionAlgorithmHeuristic extends AlgorithmHeuristic {

	private static final Logger log = LoggerFactory.getLogger(DataInspectionAlgorithmHeuristic.class);
	
	private ISubject algorithm;
	private FeatureCollection<?, ?> featureCollection;
	private Date dateTimeStart;
	private Date dateTimeEnd;
	private boolean requireFullCoverage;
	
	private long totalDataPulled;
	private int variableCount;
	private String footprint;
	private DataFetchInfo info;
	
	public DataInspectionAlgorithmHeuristic(ISubject algorithm, FeatureCollection<?, ?> featureCollection,
			Date dateTimeStart, Date dateTimeEnd, boolean requireFullCoverage) {
		this.algorithm = algorithm;
		this.featureCollection = featureCollection;
		this.dateTimeStart = dateTimeStart;
		this.dateTimeEnd = dateTimeEnd;
		this.requireFullCoverage = requireFullCoverage;
		
		this.totalDataPulled = 0L;
		this.variableCount = 0;
		this.footprint = null;
	}

	/**
	 * Throws exception if heuristic fails.
	 * @param gridDatatype 
	 */
	@Override
	public void traverseStart(GridDatatype gridDatatype) {
		GDPAlgorithmUtil.DataCube dataCube = GDPAlgorithmUtil.calculateDataCube(
				gridDatatype, featureCollection, dateTimeStart, dateTimeEnd, requireFullCoverage);
		totalDataPulled += dataCube.getTotalSize();
		variableCount++;
		int gridCells = dataCube.getxLength() * dataCube.getyLength();
		
		if (footprint == null) { // somewhat expensive, only do once
			footprint = calculateLatLonFootprint(gridDatatype);
		}
		
		info = new DataFetchInfo(totalDataPulled, gridCells, dataCube.gettLength(), dataCube.getDataTypeSize(), variableCount, footprint);
	}
	
	@Override
	public void traverseEnd() {
		algorithm.update(info);
	}
	
	private String calculateLatLonFootprint(GridDatatype gridDatatype) {
		StringBuilder builder = new StringBuilder();
		
		
		GridCoordSystem coordSys = gridDatatype.getCoordinateSystem();
		GridCellGeometry geometry = new GridCellGeometry(coordSys);
		CoordinateReferenceSystem crs = geometry.getGridCRS();
		try {
			MathTransform transform = CRS.findMathTransform(crs, DefaultGeographicCRS.WGS84, true);
							
			// I believe this goes lowerleft, lowerright, upperright, upperleft
			Geometry[] geoms = new Geometry[4];
			geoms[0] = geometry.getCellGeometry(0, 0);
			geoms[1] = geometry.getCellGeometry(geometry.getCellCountX()-1, 0);
			geoms[2] = geometry.getCellGeometry(geometry.getCellCountX()-1, geometry.getCellCountY()-1);
			geoms[3] = geometry.getCellGeometry(0, geometry.getCellCountY()-1);

			Coordinate[] latlon = new Coordinate[4];
			for (int i=0; i < 4; i++) {
				if (geoms[i] instanceof Polygon) {
					Polygon poly = (Polygon)geoms[i];
					Coordinate[] coordinates = poly.getCoordinates();
					Coordinate corner = coordinates[i];
					latlon[i] = JTS.transform(corner, null, transform);
				}
			}
			
			builder.append("POLYGON((")
					.append(latlon[0].x).append(" ").append(latlon[0].y)
					.append(", ")
					.append(latlon[1].x).append(" ").append(latlon[1].y)
					.append(", ")
					.append(latlon[2].x).append(" ").append(latlon[2].y)
					.append(", ")
					.append(latlon[3].x).append(" ").append(latlon[3].y)
					.append(", ")
					.append(latlon[0].x).append(" ").append(latlon[0].y)
					.append("))");
		} catch (FactoryException | TransformException ex) {
			log.warn("Encountered unusual CRS " + crs.toWKT(), ex);
		}
		return builder.toString();
	}
}
