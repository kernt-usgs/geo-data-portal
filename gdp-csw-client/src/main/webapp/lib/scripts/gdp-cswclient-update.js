/**
 * An update to the stock CSW client. Creates functions specific to the functionality
 * if the Geo Data Portal.
 * 
 * Author: Ivan Suftin (isuftin@usgs.gov)
 */

// JSLint cleanup
/*jslint sloppy : true */
/*global window */
/*global document */
/*global CSWClient: true */
/*global trim: true */
/*global Sarissa */
/*global XMLHttpRequest */
/*global XSLTProcessor */
/*global XMLSerializer */
/*global ActiveXObject */
/*global DOMParser */

// We need to override the constructor for this object. In order to do that, 
// I am going to borrow the prototype for this object as it's been set up by the
// stock CSW script
var oldCSWProto = CSWClient.prototype;

// I now update the CSWClient object with the new constructor
CSWClient = function(cswhost, host) {
    this.cswhost = cswhost;
    this.use_proxy = false;
    this.proxy = host;
    this.getrecords_xsl = this.loadDocument("lib/xsl/getrecords.xsl");
    this.getrecordbyid_xsl = this.loadDocument("lib/xsl/getrecordbyid.xsl");
    this.defaults_xml = this.loadDocument("lib/xml/defaults.xml");
    this.capabilitiesMap = {};
    this.context = null;
    this.sbEndpoint = null;
    this.sbConstraintFeature = false;
    this.sbConstraintCoverage = false;
    this.currentSBFeatureSearch = null;
    this.lowerCorner = '-180 -90';
    this.upperCorner = '180 90';
	
	this.defaultschema;
	try {
		this.defaultschema = this.defaults_xml.selectSingleNode("/defaults/outputschema/text()").nodeValue;
	} catch (ex) {
		this.defaultschema = this.defaults_xml.evaluate("/defaults/maxrecords/text()", this.defaults_xml).iterateNext().data;
	}
};

// Now that the object has a new constructor, I go ahead and slap on the old
// prototype on it
CSWClient.prototype = oldCSWProto;

// Delete the variable to clean up
oldCSWProto = undefined;

/**
 * Updates the stock CSWClient function by not hardcoding the proxy url 
 * 
 * @param {type} request
 * @returns {unresolved}
 * @see CSWClient.sendCSWRequest
 */
CSWClient.prototype.sendCSWRequest = function(request) {
    var xml = Sarissa.getDomDocument(),
            xmlhttp = new XMLHttpRequest(),
            cswProxy = this.proxy + this.cswhost;

    xml.async = false;

    if (this.use_proxy) {
        xmlhttp.open("POST", cswProxy, false);
    } else {
        xmlhttp.open("POST", this.cswhost, false);
    }
    xmlhttp.setRequestHeader("Content-type", "application/xml");
    xmlhttp.setRequestHeader("Content-length", request.length);
    xmlhttp.setRequestHeader("Connection", "close");
    xmlhttp.send(request);

    xml = xmlhttp.responseXML;
    return xml;
};

/**
 * Instead of passing off the response to the handleCSWResponse function,
 * this override passes the response back to the calling function to be handled
 * upstream
 * 
 * @param {type} id
 * @returns {unresolved}
 */
CSWClient.prototype.getRecordById = function(id) {
    var schema = this.defaultschema,
            processor = new XSLTProcessor(),
            request_xml,
            request;

    if (document.theForm.schema !== null) {
        schema = document.theForm.schema.value;
    }

    this.setXpathValue(this.defaults_xml, "/defaults/outputschema", String(schema));
    this.setXpathValue(this.defaults_xml, "/defaults/id", String(id));

    processor.importStylesheet(this.getrecordbyid_xsl);

    request_xml = processor.transformToDocument(this.defaults_xml);
    request = new XMLSerializer().serializeToString(request_xml);

    return this.sendCSWRequest(request);
};

/**
 * Changes a few things about stock handling of the CSWResponse.
 * Updated to include handling for a new metadata element in html.
 * Stops short of popping up the element and leaves that to upstream functions.
 * 
 * @param {type} request
 * @param {type} xml
 * @returns {undefined}
 */
CSWClient.prototype.handleCSWResponse = function(request, xml, displaymode) {
    var stylesheet = "lib/xsl/prettyxml.xsl",
            xslt,
            processor = new XSLTProcessor(),
            serializer = new XMLSerializer(),
            XmlDom,
            output,
            displaymode = displaymode || document.theForm.displaymode.value,
            outputDiv = document.getElementById("csw-output");

    if (request === "getrecords" && displaymode !== "xml") {
        stylesheet = "lib/xsl/csw-results.xsl";
    } else if (request === "getrecordbyid" && displaymode !== "xml") {
        stylesheet = "lib/xsl/csw-metadata.xsl";
    }

    if (request === "getrecordbyid") {
        outputDiv = document.getElementById("metadata");
    }

    xslt = this.loadDocument(stylesheet);
    processor.importStylesheet(xslt);
    XmlDom = processor.transformToDocument(xml);
    output = serializer.serializeToString(XmlDom.documentElement);
    outputDiv.innerHTML = output;
    return output;
};

/**
 * Includes a lot of ScienceBase specific overrides 
 * 
 * @param {type} start
 * @returns {unresolved}
 */
CSWClient.prototype.getRecords = function(start) {
    // force outputSchema  always  to csw:Record for GetRecords requests xsl for 
    // this only handles dublin core, others are in GetRecordById xsl fixed this
    var results = "<results>",
            schema = document.theForm.schema.value,
            sortby = document.theForm.sortby.value,
            queryable = document.theForm.queryable.value,
            operator = document.theForm.operator.value,
            query = trim(document.theForm.query.value),
            processor = new XSLTProcessor(),
            importNode,
            request_xml,
            results_xml,
            request,
            csw_response;

    if (typeof start === "undefined") {
        start = 1;
    }

    if (this.cswhost === this.sbEndpoint) {
        if (this.context === 'wfs') {
            this.sbConstraintFeature = true;
            this.sbConstraintCoverage = false;
        } else {
            this.sbConstraintFeature = false;
            this.sbConstraintCoverage = true;
        }
    } else {
        this.sbConstraintFeature = false;
        this.sbConstraintCoverage = false;
    }

    if (typeof document.theForm.cswhosts !== "undefined") {
        this.setCSWHost(document.theForm.cswhosts.value);
    }

    /*because geonetwork does not follow the specs*/
    if (this.cswhost.indexOf('geonetwork') !== -1 && queryable === "anytext") {
        queryable = "any";
    }

    if (operator === "contains" && query !== "") {
        query = "%" + query + "%";
    }

    this.setXpathValue(this.defaults_xml, "/defaults/outputschema", String(schema));
    this.setXpathValue(this.defaults_xml, "/defaults/propertyname", String(queryable));
    this.setXpathValue(this.defaults_xml, "/defaults/literal", String(query));

    // http://internal.cida.usgs.gov/jira/browse/GDP-507
    // If we're doing a ScienceBase search for Area of Interest, the search was throwing in some constrained bounding box.
    // Instead, we will not set the bounding box. The CSW Client will set -180, -90, 180, 90 as the bounding box.
    if (this.cswhost !== this.sbEndpoint && this.context !== 'wfs') {
        this.setXpathValue(this.defaults_xml, "/defaults/bboxlc", String(this.lowerCorner));
        this.setXpathValue(this.defaults_xml, "/defaults/bboxuc", String(this.upperCorner));
    }

    this.setXpathValue(this.defaults_xml, "/defaults/startposition", String(start));
    this.setXpathValue(this.defaults_xml, "/defaults/sortby", String(sortby));

    if (this.sbConstraintFeature) {
        this.setXpathValue(this.defaults_xml, "/defaults/scienceBaseFeature", 'true');
        this.setXpathValue(this.defaults_xml, "/defaults/scienceBaseCoverage", 'false');
        results = "<results scienceBaseFeature=\"true\">";
    } else if (this.sbConstraintCoverage) {
        this.setXpathValue(this.defaults_xml, "/defaults/scienceBaseCoverage", 'true');
        this.setXpathValue(this.defaults_xml, "/defaults/scienceBaseFeature", 'false');
        results = "<results scienceBaseCoverage=\"true\">";
    } else {
        this.setXpathValue(this.defaults_xml, "/defaults/scienceBaseCoverage", 'false');
        this.setXpathValue(this.defaults_xml, "/defaults/scienceBaseFeature", 'false');
    }

    processor.importStylesheet(this.getrecords_xsl);

    request_xml = processor.transformToDocument(this.defaults_xml);
    request = new XMLSerializer().serializeToString(request_xml);
    csw_response = this.sendCSWRequest(request);

    results += "<request start=\"" + start + "\"" + " maxrecords=\"";
	
	try {
		results += this.defaults_xml.selectSingleNode("/defaults/maxrecords/text()").nodeValue;
	} catch (ex) {
		results += this.defaults_xml.evaluate("/defaults/maxrecords/text()", this.defaults_xml).iterateNext().data;
	}
	
    results += "\"/></results>";

    if (window.ActiveXObject) {
        // IE
        results_xml = new ActiveXObject('Msxml2.DOMDocument.6.0');
        results_xml.loadXML(results);
    } else {
        results_xml = (new DOMParser()).parseFromString(results, "text/xml");
    }

    importNode = results_xml.importNode(csw_response.documentElement, true);
    results_xml.documentElement.appendChild(importNode);
    this.handleCSWResponse("getrecords", results_xml);
    return results_xml;
};

/**
 * Uses an ID to fetch and display metadata
 * 
 * @param {type} id
 * @returns {unresolved}
 */
CSWClient.prototype.popupMetadataById = function(id) {
    var csw_response = this.getRecordById(id);
    this.handleCSWResponse("getrecordbyid", csw_response);
    return csw_response;
};

/**
 * This exists due to being triggered by in-line javascript dynamically created
 * in HTML popup windows
 * 
 * @param {type} context
 * @returns {undefined}
 */
CSWClient.prototype.setContext = function(context) {
    this.context = context;
};