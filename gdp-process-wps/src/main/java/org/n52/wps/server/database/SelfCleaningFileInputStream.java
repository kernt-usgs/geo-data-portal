package org.n52.wps.server.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * After calling close on this InputStream, will attempt to delete the
 * underlying file
 *
 * @author isuftin
 */
public class SelfCleaningFileInputStream extends FileInputStream {

	private static final Logger LOGGER = LoggerFactory.getLogger(SelfCleaningFileInputStream.class);
	private File file;

	public SelfCleaningFileInputStream(File file) throws FileNotFoundException {
		super(file);
	}

	@Override
	public void close() throws IOException {
		super.close();
		String path = file.getAbsolutePath();
		if (!file.exists()) {
			LOGGER.debug("File at {} does not exist", path);
		} else if (!file.isFile()) {
			LOGGER.debug("File at {} is not a file", path);
		} else if (!file.canWrite()) {
			LOGGER.debug("File at {} can not be written to", path);
		} else {
			FileUtils.deleteQuietly(file);
		}
	}
}
