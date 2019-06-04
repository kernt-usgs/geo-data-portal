package org.n52.wps.server.database.domain;

import com.google.common.base.Joiner;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.opengis.ows.x11.ExceptionType;

import net.opengis.wps.x100.ExecuteResponseDocument;
import net.opengis.wps.x100.ExecuteResponseDocument.ExecuteResponse.ProcessOutputs;
import net.opengis.wps.x100.OutputDataType;
import net.opengis.wps.x100.ProcessFailedType;
import net.opengis.wps.x100.ProcessStartedType;
import net.opengis.wps.x100.StatusType;

import org.joda.time.DateTime;

public class WpsResponse {
	
	private final String id;
	private final String wpsRequestId;
	private final String wpsAlgoIdentifer;
	private final WpsStatus status;
	private final Integer percentComplete;
	private final DateTime creationTime;
	private List<WpsOutput> outputs;
	private DateTime startTime;
	private DateTime endTime;
	private String exceptionText;

	public WpsResponse(String wpsRequestId, InputStream inputStream) {
		this(wpsRequestId, constructExecuteResponseFromStream(inputStream));
	}
	
	public WpsResponse(String inWpsRequestId, ExecuteResponseDocument executeResponseDoc) {
		id = UUID.randomUUID().toString();
		wpsRequestId = inWpsRequestId;
		wpsAlgoIdentifer = executeResponseDoc.getExecuteResponse().getProcess().getIdentifier().getStringValue();
		outputs = parseOutputs(id, executeResponseDoc.getExecuteResponse().getProcessOutputs());
		
		StatusType executeStatus = executeResponseDoc.getExecuteResponse().getStatus();
		status = WpsStatus.lookup(executeStatus);
		percentComplete = parsePercentComplete(executeStatus);
		creationTime = new DateTime(executeStatus.getCreationTime());
		
		ProcessFailedType processFailed = executeResponseDoc.getExecuteResponse().getStatus().getProcessFailed();
		exceptionText = extractExceptionText(processFailed);
	}
	
	public WpsResponse(String inId, String inWpsRequestId, String inWpsAlgoIdentifer, WpsStatus inStatus,
			Integer inPercentComplete, DateTime inCreationTime, String inExceptionText) {
		id = inId;
		wpsRequestId = inWpsRequestId;
		wpsAlgoIdentifer = inWpsAlgoIdentifer;
		status = inStatus;
		percentComplete = inPercentComplete;
		creationTime = inCreationTime;
		exceptionText = inExceptionText;
	}
	
	private static ExecuteResponseDocument constructExecuteResponseFromStream(InputStream inputStream) {
		try {
			return ExecuteResponseDocument.Factory.parse(inputStream);
		} catch (Exception e) {
			throw new RuntimeException("issue constructing ExecuteDocument from xml request", e);
		}
	}
	
	public String getId() {
		return id;
	}
	
	public String getWpsRequestId() {
		return wpsRequestId;
	}

	public String getWpsAlgoIdentifer() {
		return wpsAlgoIdentifer;
	}
	
	public List<WpsOutput> getOutputs() {
		return outputs;
	}
	
	public void setOutputs(List<WpsOutput> outputs) {
		this.outputs = outputs;
	}

	public WpsStatus getStatus() {
		return status;
	}

	public Integer getPercentComplete() {
		return percentComplete;
	}

	public DateTime getCreationTime() {
		return creationTime;
	}

	public DateTime getEndTime() {
		return endTime;
	}
	
	public DateTime getStartTime() {
		return startTime;
	}
	
	public void setStartTime(DateTime startTime) {
		this.startTime = startTime;
	}
	
	public void setEndTime(DateTime endTime) {
		this.endTime = endTime;
	}

	public String getExceptionText() {
		return exceptionText;
	}

	public void setExceptionText(String exceptionText) {
		this.exceptionText = exceptionText;
	}
	
	private List<WpsOutput> parseOutputs(String inWpsResponseId, ProcessOutputs processOutputs) {
		List<WpsOutput> ret = new ArrayList<>();
		if (processOutputs != null) {
			for (OutputDataType processOutput : processOutputs.getOutputArray()) {
				ret.add(new WpsOutput(inWpsResponseId, processOutput, processOutput.getReference() != null));
			}
		}
		return ret;
	}
	
	private Integer parsePercentComplete(StatusType type) {
		Integer percentage = 0;
		ProcessStartedType processPaused = type.getProcessPaused();
		ProcessStartedType processStarted = type.getProcessStarted();
		if (processPaused != null) {
			percentage = processPaused.getPercentCompleted();
		}
		if (processStarted != null) {
			percentage = processStarted.getPercentCompleted();
		}
		return percentage;
	}
	
	private String extractExceptionText(ProcessFailedType failedType) {
		StringBuilder exceptionBuilder = new StringBuilder();
		if (failedType != null) {
			ExceptionType[] exceptionArray = failedType.getExceptionReport().getExceptionArray();
			if (exceptionArray != null) {
				for (ExceptionType ex : exceptionArray) {
					String[] textArray = ex.getExceptionTextArray();
					String text = Joiner.on(System.lineSeparator()).join(textArray);
					exceptionBuilder.append(text);
				}
			}
		}
		return exceptionBuilder.toString();
	}
}
