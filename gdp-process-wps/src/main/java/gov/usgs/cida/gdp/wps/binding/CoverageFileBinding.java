package gov.usgs.cida.gdp.wps.binding;

import java.io.File;
import org.apache.commons.io.FileUtils;
import org.n52.wps.io.data.IComplexData;

public class CoverageFileBinding implements IComplexData {

	private static final long serialVersionUID = 9203397138194901693L;

	protected final File file;

	public CoverageFileBinding(File file) {
		this.file = file;
	}

	@Override
	public File getPayload() {
		return file;
	}

	@Override
	public Class getSupportedClass() {
		return File.class;
	}

	@Override
	public void dispose() {
		FileUtils.deleteQuietly(file);
	}
}
