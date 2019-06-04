package org.n52.wps.server.database.domain;

import java.util.UUID;

import net.opengis.wps.x100.DocumentOutputDefinitionType;

public class WpsOutputDefinition {
	
	private final String id;
	private final String wpsRequestId;
	private final String outputIdentifer;
	private final String mimeType;

	public WpsOutputDefinition(String inWpsRequestId, DocumentOutputDefinitionType outputDefinitionType) {
		id = UUID.randomUUID().toString();
		wpsRequestId = inWpsRequestId;
		outputIdentifer = outputDefinitionType.getIdentifier().getStringValue();
		this.mimeType = outputDefinitionType.getMimeType();
	}
	
	public String getId() {
		return id;
	}
	
	public String getWpsRequestId() {
		return wpsRequestId;
	}
	
	public String getOutputIdentifier() {
		return outputIdentifer;
	}
	
	public String getMimeType() {
		return mimeType;
	}
}
