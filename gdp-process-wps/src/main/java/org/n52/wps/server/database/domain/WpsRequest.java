package org.n52.wps.server.database.domain;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.opengis.wps.x100.DataInputsType;
import net.opengis.wps.x100.DocumentOutputDefinitionType;
import net.opengis.wps.x100.ExecuteDocument;
import net.opengis.wps.x100.ExecuteDocument.Execute;
import net.opengis.wps.x100.InputType;
import net.opengis.wps.x100.ResponseDocumentType;

import com.google.common.collect.ImmutableList;

public class WpsRequest {
	
	private final String id;
	private final String wpsAlgoIdentifer;
	private final List<WpsInput> wpsInputs;
	private final List<WpsOutputDefinition> wpsRequestedOutputs;
	private final ExecuteDocument executeDoc;
	
	public WpsRequest(String requestId, InputStream stream) {
		this(requestId, constructExecuteFromStream(stream));
	}
	

	public WpsRequest(String requestId, ExecuteDocument inExecuteDoc) {
		id = requestId;
		executeDoc = inExecuteDoc;
		Execute execute = inExecuteDoc.getExecute();
		wpsAlgoIdentifer = execute.getIdentifier().getStringValue();
		wpsInputs = constructInputs(execute.getDataInputs());
		wpsRequestedOutputs = constructOutputs(execute.getResponseForm().getResponseDocument());
	}

	private List<WpsOutputDefinition> constructOutputs(ResponseDocumentType responseDocumentType) {
		ArrayList<WpsOutputDefinition> ret = new ArrayList<>();
		if (responseDocumentType != null) {
			for (DocumentOutputDefinitionType outputDefinitionType : responseDocumentType.getOutputArray()) {
				if (!outputDefinitionType.getIdentifier().isNil()) {
					ret.add(new WpsOutputDefinition(id, outputDefinitionType));
				}
			}
		}
		return ImmutableList.copyOf(ret.toArray(new WpsOutputDefinition[0]));
	}
	
	private static ExecuteDocument constructExecuteFromStream(InputStream stream) {
		ExecuteDocument executeDoc;
		try {
			executeDoc = ExecuteDocument.Factory.parse(stream);
		} catch (Exception e) {
			throw new RuntimeException("issue constructing ExecuteDocument from xml request", e);
		}
		return executeDoc;
	}

	private List<WpsInput> constructInputs(DataInputsType dataInputs) {
		ArrayList<WpsInput> ret = new ArrayList<WpsInput>();
		if (dataInputs != null) {
			for (InputType inputType : dataInputs.getInputArray()) {
				if (inputType.getData() != null && inputType.getData().getLiteralData() != null) {
					ret.add(new WpsInput(id, inputType));
				}
			}
		}
		return ImmutableList.copyOf(ret.toArray(new WpsInput[0]));
	}

	public String getId() {
		return id;
	}
	
	public String getWpsAlgoIdentifer() {
		return wpsAlgoIdentifer;
	}
	
	public List<WpsInput> getWpsInputs() {
		return wpsInputs;
	}
	
	public List<WpsOutputDefinition> getWpsRequestedOutputs() {
		return wpsRequestedOutputs;
	}
	
	public ExecuteDocument getExecuteDoc() {
		return executeDoc;
	}

}
