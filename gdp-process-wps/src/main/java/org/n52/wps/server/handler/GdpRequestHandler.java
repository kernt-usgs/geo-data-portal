package org.n52.wps.server.handler;

import gov.usgs.cida.gdp.wps.analytics.UserAgentInfo;
import gov.usgs.cida.gdp.wps.queue.ExecuteRequestManager;
import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.WebProcessingService;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.n52.wps.server.request.CapabilitiesRequest;
import org.n52.wps.server.request.DescribeProcessRequest;
import org.n52.wps.server.request.ExecuteRequest;
import org.n52.wps.server.request.ExecuteRequestWrapper;
import org.n52.wps.server.request.Request;
import org.n52.wps.server.response.ExecuteResponse;
import org.n52.wps.server.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 *
 * @author smlarson
 */
public class GdpRequestHandler extends RequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GdpRequestHandler.class);
    private String userAgent;

    /**
     * Handles requests of type HTTP_POST (currently executeProcess). A Document
     * is used to represent the client input. This Document must first be parsed
     * from an InputStream.
     *
     * @param is The client input
     * @param os The OutputStream to write the response to.
     * @throws ExceptionReport
     */
    public GdpRequestHandler(InputStream is, OutputStream os)
            throws ExceptionReport {
        String nodeName, localName, nodeURI, version = null;
        Document doc;
        this.os = os;

        boolean isCapabilitiesNode = false;

        try {
            System.setProperty("javax.xml.parsers.DocumentBuilderFactory", "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl");

            DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
            fac.setNamespaceAware(true);

            // parse the InputStream to create a Document
            doc = fac.newDocumentBuilder().parse(is);

            // Get the first non-comment child.
            Node child = doc.getFirstChild();
            while (child.getNodeName().compareTo("#comment") == 0) {
                child = child.getNextSibling();
            }
            nodeName = child.getNodeName();
            localName = child.getLocalName();
            nodeURI = child.getNamespaceURI();
            Node versionNode = child.getAttributes().getNamedItem("version");

            /*
			 * check for service parameter. this has to be present for all requests
             */
            Node serviceNode = child.getAttributes().getNamedItem("service");

            if (serviceNode == null) {
                throw new ExceptionReport("Parameter <service> not specified.", ExceptionReport.MISSING_PARAMETER_VALUE, "service");
            } else if (!serviceNode.getNodeValue().equalsIgnoreCase("WPS")) {
                throw new ExceptionReport("Parameter <service> not specified.", ExceptionReport.INVALID_PARAMETER_VALUE, "service");
            }

            isCapabilitiesNode = nodeName.toLowerCase().contains("capabilities");
            if (versionNode == null && !isCapabilitiesNode) {
                throw new ExceptionReport("Parameter <version> not specified.", ExceptionReport.MISSING_PARAMETER_VALUE, "version");
            }
            //TODO: I think this can be removed, as capabilities requests do not have a version parameter (BenjaminPross)
            if (!isCapabilitiesNode) {
//				version = child.getFirstChild().getTextContent();//.getNextSibling().getFirstChild().getNextSibling().getFirstChild().getNodeValue();
                version = child.getAttributes().getNamedItem("version").getNodeValue();
            }
            /*
			 * check language, if not supported, return ExceptionReport
			 * Fix for https://bugzilla.52north.org/show_bug.cgi?id=905
             */
            Node languageNode = child.getAttributes().getNamedItem("language");
            if (languageNode != null) {
                String language = languageNode.getNodeValue();
                Request.checkLanguageSupported(language);
            }
        } catch (SAXException e) {
            throw new ExceptionReport(
                    "There went something wrong with parsing the POST data: "
                    + e.getMessage(),
                    ExceptionReport.NO_APPLICABLE_CODE, e);
        } catch (IOException e) {
            throw new ExceptionReport(
                    "There went something wrong with the network connection.",
                    ExceptionReport.NO_APPLICABLE_CODE, e);
        } catch (ParserConfigurationException e) {
            throw new ExceptionReport(
                    "There is a internal parser configuration error",
                    ExceptionReport.NO_APPLICABLE_CODE, e);
        }
        //Fix for Bug 904 https://bugzilla.52north.org/show_bug.cgi?id=904
        if (!isCapabilitiesNode && version == null) {
            throw new ExceptionReport("Parameter <version> not specified.", ExceptionReport.MISSING_PARAMETER_VALUE, "version");
        }
        if (!isCapabilitiesNode && !version.equals(Request.SUPPORTED_VERSION)) {
            throw new ExceptionReport("Version not supported.", ExceptionReport.INVALID_PARAMETER_VALUE, "version");
        }
        // get the request type
        if (nodeURI.equals(WebProcessingService.WPS_NAMESPACE) && localName.equals("Execute")) {
            req = new ExecuteRequestWrapper(doc); //#USGS override -added wrapper
            setResponseMimeType((ExecuteRequest) req);
        } else if (nodeURI.equals(WebProcessingService.WPS_NAMESPACE) && localName.equals("GetCapabilities")) {
            req = new CapabilitiesRequest(doc);
            this.responseMimeType = "text/xml";
        } else if (nodeURI.equals(WebProcessingService.WPS_NAMESPACE) && localName.equals("DescribeProcess")) {
            req = new DescribeProcessRequest(doc);
            this.responseMimeType = "text/xml";

        } else if (!localName.equals("Execute")) {
            throw new ExceptionReport("The requested Operation not supported or not applicable to the specification: "
                    + nodeName, ExceptionReport.OPERATION_NOT_SUPPORTED, localName);
        } else if (nodeURI.equals(WebProcessingService.WPS_NAMESPACE)) {
            throw new ExceptionReport("specified namespace is not supported: "
                    + nodeURI, ExceptionReport.INVALID_PARAMETER_VALUE);
        }
    }

    public GdpRequestHandler(Map<String, String[]> params, OutputStream os)
            throws ExceptionReport {
        super(params, os);
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * Handle a request after its type is determined. The request is scheduled
     * for execution. If the server has enough free resources, the client will
     * be served immediately. If time runs out, the client will be asked to come
     * back later with a reference to the result.
     *
     * Override: Previously, the wrapper added the handle agent logging. The
     * entire handle method is now overridden to: 1 - prevent redundant requests
     * from processing (ran or running) 2 - avoid taxing the same data source
     * the request requires for processing
     *
     * @throws ExceptionReport
     */
    @Override
    public void handle() throws ExceptionReport {
        Response resp = null;
        if (req == null) {
            throw new ExceptionReport("Internal Error", "");
        }

        if (req instanceof ExecuteRequestWrapper) {
            ExecuteRequestWrapper execReq = (ExecuteRequestWrapper) req; //#USGS override code

            LOGGER.debug("RequestId before updating to Accepted:" + execReq.getUniqueId());
            execReq.updateStatusAccepted();
            ExecuteRequestManager.getInstance().getThrottleQueue().putRequest(execReq); //inserts with ACCEPTED into the Throttle_Queue table. Does not actually add the request to the RequestQueue.

            UserAgentInfo uaInfo = new UserAgentInfo(userAgent);
            uaInfo.log(execReq.getUniqueId().toString());

            if (execReq.isStoreResponse()) {
                addToQueue(execReq, resp);
            } else {
                synchAddToQueue(execReq, resp);
            }

        } else {  // if ExecuteRequest
            // for GetCapabilities and DescribeProcess:
            resp = req.call();
            try {
                InputStream is = resp.getAsStream();
                IOUtils.copy(is, os);
                is.close();
            } catch (IOException e) {
                throw new ExceptionReport("Could not read from response stream.", ExceptionReport.NO_APPLICABLE_CODE);
            }
        }
    }

    private void addToQueue(ExecuteRequestWrapper execReq, Response resp) throws ExceptionReport {
        try {
            resp = new ExecuteResponse(execReq);
            InputStream is = resp.getAsStream();
            IOUtils.copy(is, os);
            is.close();
            //RequestManager.getInstance().getExecuteRequestQueue().put(execReq);  //#USGS pool.submit   - now done via timer task

            // retrieve status with timeout enabled
        } catch (RejectedExecutionException ree) {
            LOGGER.warn("exception handling ExecuteRequest.", ree);
            // server too busy?
            throw new ExceptionReport(
                    "The requested process was rejected. Maybe the server is flooded with requests.",
                    ExceptionReport.SERVER_BUSY);
        } catch (ExceptionReport | IOException e) {
            LOGGER.error("exception handling ExecuteRequest.", e);
            if (e instanceof ExceptionReport) {
                throw (ExceptionReport) e;
            }
            throw new ExceptionReport("Could not read from response stream.", ExceptionReport.NO_APPLICABLE_CODE);
        }
    }

    private void synchAddToQueue(ExecuteRequestWrapper execReq, Response resp) throws ExceptionReport {
        try {
            LOGGER.info("Adding to queue synchronously.");
            // add to queue even though the user is waiting ie synchronous <test> this may block the asynch requests
            resp = ExecuteRequestManager.getInstance().getExecuteRequestQueue().put(execReq); // #USGS pool.submit
        } catch (RejectedExecutionException ree) {
            LOGGER.warn("exception handling ExecuteRequest.", ree);
            // server too busy?
            throw new ExceptionReport(
                    "The requested process was rejected. Maybe the server is flooded with requests.",
                    ExceptionReport.SERVER_BUSY);
        } finally {
            if (resp == null) {
                LOGGER.warn("null response handling ExecuteRequest.");
                throw new ExceptionReport("Problem with handling threads in GDPRequestHandler", ExceptionReport.NO_APPLICABLE_CODE);
            }
            if (!execReq.isStoreResponse()) {
                InputStream is = resp.getAsStream();
                try {
                    IOUtils.copy(is, os);
                    is.close();
                } catch (IOException ex) {
                    //java.util.logging.Logger.getLogger(GdpRequestHandler.class.getName()).log(Level.SEVERE, null, ex);
                    LOGGER.error("Error getting stream from response in synchronous add to queue: " , ex);
                }

                LOGGER.info("Served ExecuteRequest.");
            }
        }
    }

}
