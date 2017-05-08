package gov.usgs.cida.gdp.wps.algorithm;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Test;
import static org.junit.Assert.*;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import ucar.ma2.InvalidRangeException;

/**
 *
 * @author jiwalker
 */
public class GDPAlgorithmUtilTest {
	
	public GDPAlgorithmUtilTest() {
	}

	@Test
	public void testDataCube_size() throws InvalidRangeException, TransformException, FactoryException {
		// first is under 2 billion, works normally
		GDPAlgorithmUtil.DataCube dataCubeSmall = new GDPAlgorithmUtil.DataCube(2, 3, 5, 7, null, null, null);
		// next is over 2 billion, with just 2 dimensions
		GDPAlgorithmUtil.DataCube dataCubeLarge = new GDPAlgorithmUtil.DataCube(1000000000, 3, 1, 1, null, null, null);
		// next is over 2 billion only when all dimensions combined
		GDPAlgorithmUtil.DataCube dataCubeHuge = new GDPAlgorithmUtil.DataCube(1000, 1000, 1000, 4, null, null, null);
		assertThat(dataCubeSmall.totalSize, is(equalTo(210l)));
		assertThat(dataCubeLarge.totalSize, is(equalTo(3000000000l)));
		assertThat(dataCubeHuge.totalSize, is(equalTo(4000000000l)));
	}
	
}
