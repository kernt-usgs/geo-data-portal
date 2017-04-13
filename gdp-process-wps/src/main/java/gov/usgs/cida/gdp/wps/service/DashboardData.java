package gov.usgs.cida.gdp.wps.service;

import gov.usgs.cida.gdp.wps.analytics.ClientInfo;
import gov.usgs.cida.gdp.wps.analytics.DataFetchInfo;
import gov.usgs.cida.gdp.wps.analytics.OutputInfo;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
* @author abramhall
*/
public class DashboardData {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm";
    private static final long MILLIS_PER_DAY = 1000 * 60 * 60 * 24;
    private static final long MILLIS_PER_HOUR = 1000 * 60 * 60;
    private static final long MILLIS_PER_MIN = 1000 * 60;
    
	private String requestId = null;
	private String requestLink = null;
    private String identifier = null;
    private String status = null;
    private Integer percentComplete = null;
    private String creationTime = null;
    private String elapsedTime = null;
    private String output = null;
    private String errorMessage = null;
    private ClientInfo clientInfo = null;
    private DataFetchInfo dataFetchInfo = null;
    private OutputInfo outputInfo = null;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPercentComplete() {
        return percentComplete;
    }

    public void setPercentComplete(Integer percentComplete) {
        this.percentComplete = percentComplete;
    }

    public String getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        cal.setTimeInMillis(creationTime);
        this.creationTime = sdf.format(cal.getTime());
    }

    public String getElapsedTime() {
        return elapsedTime;
    }

    public void setElapsedTime(long elapsedTime) {
        this.elapsedTime = convertMilliTimeToHumanReadable(elapsedTime);
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage =  errorMessage;
    }

	/**
	 * @return the requestId
	 */
	public String getRequestId() {
		return requestId;
	}

	/**
	 * @param requestId the requestId to set
	 */
	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	/**
	 * @return the requestLink
	 */
	public String getRequestLink() {
		return requestLink;
	}

	/**
	 * @param requestLink the requestLink to set
	 */
	public void setRequestLink(String requestLink) {
		this.requestLink = requestLink;
	}

	public ClientInfo getClientInfo() {
		return clientInfo;
	}

	public void setClientInfo(ClientInfo clientInfo) {
		this.clientInfo = clientInfo;
	}

	public DataFetchInfo getDataFetchInfo() {
		return dataFetchInfo;
	}

	public void setDataFetchInfo(DataFetchInfo dataFetchInfo) {
		this.dataFetchInfo = dataFetchInfo;
	}

	public OutputInfo getOutputInfo() {
		return outputInfo;
	}

	public void setOutputInfo(OutputInfo outputInfo) {
		this.outputInfo = outputInfo;
	}
	
    /**
    * @param time in milliseconds
    * @return human readable string of time elapsed in terms of hours, minutes, and seconds (fractional seconds truncated)
    */
    private String convertMilliTimeToHumanReadable(long time) {
        StringBuilder returnString = new StringBuilder();
        long days = time / MILLIS_PER_DAY;
        if (days > 0) {
            time = time - days * MILLIS_PER_DAY;
            returnString.append(days).append("d ");
        }
        long hours = time / MILLIS_PER_HOUR;
        if (hours > 0) {
            time = time - hours * MILLIS_PER_HOUR;
            returnString.append(hours).append("h ");
        }
        long minutes = time / MILLIS_PER_MIN;
        if (minutes > 0) {
            time = time - minutes * MILLIS_PER_MIN;
            returnString.append(minutes).append("m ");
        }
        long seconds = time / 1000;
        returnString.append(seconds).append("s");
        return returnString.toString();
    }

}