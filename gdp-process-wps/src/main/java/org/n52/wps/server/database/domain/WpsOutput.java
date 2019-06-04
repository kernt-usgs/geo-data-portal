package org.n52.wps.server.database.domain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import net.opengis.wps.x100.OutputDataType;

import org.apache.commons.io.IOUtils;

public class WpsOutput {

	private final String id;
	private final String outputId;
	private final String wpsResponseId;
	private final String mimeType;
	private final boolean isReference;

	private long responseLength = 0;
	private String location = null;
	private String content = null;

	public WpsOutput(String inWpsResponseId, OutputDataType processOutput, boolean inIsReference) {
		id = UUID.randomUUID().toString();
		outputId = inWpsResponseId + "" + processOutput.getIdentifier();
		wpsResponseId = inWpsResponseId;
		mimeType = null;
		isReference = inIsReference;
	}

	public WpsOutput(String inWpsResponseId, String inOutputId, String inMimeType) {
		id = UUID.randomUUID().toString();
		outputId = inOutputId;
		wpsResponseId = inWpsResponseId;
		mimeType = inMimeType;
		isReference = true;
	}

	public WpsOutput(String id, String outputId, String responseId, String inlineResponse, String mimeType, long responseLength, String location) {
		this.id = id;
		this.outputId = outputId;
		this.wpsResponseId = responseId;
		this.content = inlineResponse;
		this.mimeType = mimeType;
		this.responseLength = responseLength;
		this.location = location;
		this.isReference = (this.content == null);
	}

	public String getId() {
		return id;
	}

	public String getLocation() {
		return location;
	}

	public String getMimeType() {
		return mimeType;
	}

	public long getResponseLength() {
		return responseLength;
	}

	public String getWpsResponseId() {
		return wpsResponseId;
	}

	public void setInline(InputStream stream) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			IOUtils.copy(stream, bos);
			byte[] bytes = bos.toByteArray();
			responseLength = bytes.length;
			content = new String(bytes);
		} catch (IOException e) {
			throw new RuntimeException("unable to copy output to string writer", e);
		}
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public void setResponseLength(long responseLength) {
		this.responseLength = responseLength;
	}

	public String getContent() {
		return content;
	}

	public boolean isReference() {
		return isReference;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getOutputId() {
		return outputId;
	}
}
