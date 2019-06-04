package org.n52.wps.server.database;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class FileReferenceInfo {

	private String fileLocation;
	private Long fileSize;

	public FileReferenceInfo(String fileLocation, Long fileSize) {
		this.fileLocation = fileLocation;
		this.fileSize = fileSize;
	}

	public String getFileLocation() {
		return fileLocation;
	}

	public Long getFileSize() {
		return fileSize;
	}

}
