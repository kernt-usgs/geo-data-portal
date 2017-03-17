package gov.usgs.cida.gdp.coreprocessing.analysis;

import java.io.IOException;
import java.util.Formatter;
import java.util.List;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;
import ucar.unidata.geoloc.ProjectionImpl;
import ucar.unidata.util.Parameter;

/**
 *
 * @author jiwalker
 */
public class CDMGridCoverageTest {
	
	@Test
	public void defaultNcmlProj() throws IOException {
		GridDataset grid = null;
		FeatureDataset fd = FeatureDatasetFactoryManager.open(
			FeatureType.GRID,
				CDMGridCoverageTest.class.getClassLoader().getResource("Sample_files/Trout_Lake_HRUs_coverage.ncml").toString(),
				null,
				new Formatter(System.err));
		if (fd instanceof GridDataset) {
			grid = (GridDataset)fd;
		} else {
			throw new RuntimeException("Dataset needs to be grid");
		}
		GridDatatype gdt = grid.findGridDatatype("tyx");
		GridCoordSystem gcs = gdt.getCoordinateSystem();
		ProjectionImpl projection = gcs.getProjection();
		List<Parameter> params = projection.getProjectionParameters();
		assertThat(params.size(), is(equalTo(1)));
		assertThat(params.get(0), is(equalTo(new Parameter("grid_mapping_name", "latitude_longitude"))));
		// Earth radius shouldn't be in parameters
	}
}
