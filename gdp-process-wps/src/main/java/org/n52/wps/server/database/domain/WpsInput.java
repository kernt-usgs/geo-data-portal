package org.n52.wps.server.database.domain;

import java.util.UUID;

import net.opengis.wps.x100.InputType;

public class WpsInput {

	private final String id;
	private final String wpsRequestId;
	private final String inputId;
	private final String value;

	public WpsInput(String inWpsRequestId, InputType inputType) {
		id = UUID.randomUUID().toString();
		wpsRequestId = inWpsRequestId;
		inputId = inputType.getIdentifier().getStringValue();
		value = inputType.getData().getLiteralData().getStringValue();
	}
	
	public String getId() {
		return id;
	}
	
	public String getInputId() {
		return inputId;
	}
	
	public String getWpsRequestId() {
		return wpsRequestId;
	}
	
	public String getValue() {
		return value;
	}

}
