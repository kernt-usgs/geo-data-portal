package gov.usgs.cida.gdp.wps.analytics;

import java.io.IOException;
import java.io.InputStream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.After;
import org.junit.Test;
import org.junit.Before;
import org.mockito.internal.util.io.IOUtil;
import org.xml.sax.SAXException;

/**
 *
 * @author jiwalker
 */
public class ClientInfoTest {
	
	private InputStream is;
	
	@Before
	public void setup() {
		is = ClientInfoTest.class.getClassLoader().getResourceAsStream("geoip.xml");
	}
	
	@After
	public void teardown() {
		IOUtil.closeQuietly(is);
	}

	@Test
	public void testSomeMethod() throws SAXException, IOException {
		String location = ClientInfo.GeoIP.parse(is);
		assertThat(location, is(equalTo("POINT(-82.9752 40.0853)")));
	}
	
}
