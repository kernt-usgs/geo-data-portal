# Geo Data Portal

The USGS Geo Data Portal (GDP) project provides scientists and environmental resource managers access to downscaled climate projections and other data resources that are otherwise difficult to access and manipulate. 

### Project Components:
* gdp-52n-wps-tests  
  * Tests for 52N WPS dependency  
* gdp-common-utilities  
  * Utilities shared across many components  
* gdp-core-processing  
  * Code used by processing algorithms  
* gdp-csw-client  
  * Client code for use with Catalog Service for the Web servers  
* gdp-data-access  
  * Code used by web processing algorithms for data access  
* gdp-derivative-processing  
  * Code to calculate climate derivatives (deprecated)  
* gdp-derivative-ui  
  * Derivative portal webapp code.  
* gdp-process-wps  
  * 'Process' web processing service web application code.  
* gdp-proxy  
  * A specialized proxy that will generically proxy OGC web services.  
* gdp-ui  
  * A Geo Data Portal web processing service javascript client web app (deprecated)  
* gdp-ui-landing  
  * A metadata catalog client to feed datasets to the javascript client (deprecated)  
* gdp-utility-wps  
  * 'Utility' web processing service web application code. 

### Building
```bash
git clone git@github.com:dblodgett-usgs/geo-data-portal.git
cd geo-data-portal
mvn install
```

### Running
Running the geo data portal requires some significant configuration. 

#### gdp-process-wps

A context.xml line:  
```xml
<Environment name="gdp.path.wps_config"	type="java.lang.String" value="{{ gdp_path_wps_config }}" override="false" />
```  
   needs to point the wps\_config.xml file. This file deploys in webapps/gdp-process-wps/conf/wps\_config.xml. This file can be used to configure the web processing service to use a postgres database rather than flat file database.
If a postgres database is available it can be used for process status and other framework functions instead of the file system. The database snippet for the wps\_config.xml looks like:
```xml
<Database>
	<Property active="true" name="databaseClass">org.n52.wps.server.database.PostgresDatabase</Property>
	<Property active="true" name="jndiName">gdp</Property>
        <Property active="true" name="saveResultsToDb">false</Property>
        <Property name="wipe.enabled" active="true">false</Property>
        <Property name="wipe.period" active="true">PT1H</Property>
        <Property name="wipe.threshold" active="true">P7D</Property>
</Database>
```
   in which case you will also need a context.xml element like:
```xml
<Resource name="jdbc/gdp" auth="Container" type="javax.sql.DataSource" driverClassName="org.postgresql.Driver" 
	url="jdbc:postgresql://127.0.0.1:5432/{{ postgres_database.name }}" 
	username="{{ postgres_database.user }}"
	password="{{ postgres_database.password }}" 
	removeAbandoned="true"
	removeAbandonedTimeout="30" 
	logAbandoned="true"
	maxActive="20" maxIdle="10" maxWait="-1" />
```
The default location for process results to be stored is in the Tomcat/temp/GDP folder. This can be changed with a context.xml element like:
```xml
<Environment name="gdp.path.workspace"			type="java.lang.String" value="{{ gdp_path_workspace }}"		override="false" />
```

##### Sample Request
This request can be posted to the deployed war at a url like: http://localhost:8080/gdp-process-wps/WebProcessingService to verify that it is working. Other requests can be built with one of the geo data portal clients at: http://cida.usgs.gov/gdp/ or https://github.com/USGS-CIDA/pyGDP or https://github.com/USGS-R/geoknife
```xml
<?xml version="1.0" encoding="UTF-8"?>
<wps:Execute xmlns:wps="http://www.opengis.net/wps/1.0.0" xmlns:ows="http://www.opengis.net/ows/1.1" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" service="WPS" version="1.0.0" xsi:schemaLocation="http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsExecute_request.xsd">
	<ows:Identifier>gov.usgs.cida.gdp.wps.algorithm.FeatureWeightedGridStatisticsAlgorithm</ows:Identifier>
	<wps:DataInputs>
		<wps:Input>
			<ows:Identifier>FEATURE_ATTRIBUTE_NAME</ows:Identifier>
			<wps:Data>
				<wps:LiteralData>STATE</wps:LiteralData>
			</wps:Data>
		</wps:Input>
		<wps:Input>
			<ows:Identifier>DATASET_URI</ows:Identifier>
			<wps:Data>
				<wps:LiteralData>dods://cida.usgs.gov/thredds/dodsC/prism_v2</wps:LiteralData>
			</wps:Data>
		</wps:Input>
		<wps:Input>
			<ows:Identifier>DATASET_ID</ows:Identifier>
			<wps:Data>
				<wps:LiteralData>ppt</wps:LiteralData>
			</wps:Data>
		</wps:Input>
		<wps:Input>
			<ows:Identifier>TIME_START</ows:Identifier>
			<wps:Data>
				<wps:LiteralData>1895-01-01T00:00:00.000Z</wps:LiteralData>
			</wps:Data>
		</wps:Input>
		<wps:Input>
			<ows:Identifier>TIME_END</ows:Identifier>
			<wps:Data>
				<wps:LiteralData>2013-12-01T00:00:00.000Z</wps:LiteralData>
			</wps:Data>
		</wps:Input>
		<wps:Input>
			<ows:Identifier>DELIMITER</ows:Identifier>
			<wps:Data>
				<wps:LiteralData>COMMA</wps:LiteralData>
			</wps:Data>
		</wps:Input>
		<wps:Input>
			<ows:Identifier>REQUIRE_FULL_COVERAGE</ows:Identifier>
			<wps:Data>
				<wps:LiteralData>true</wps:LiteralData>
			</wps:Data>
		</wps:Input>
		<wps:Input>
			<ows:Identifier>GROUP_BY</ows:Identifier>
			<wps:Data>
				<wps:LiteralData>STATISTIC</wps:LiteralData>
			</wps:Data>
		</wps:Input>
		<wps:Input>
			<ows:Identifier>SUMMARIZE_TIMESTEP</ows:Identifier>
			<wps:Data>
				<wps:LiteralData>false</wps:LiteralData>
			</wps:Data>
		</wps:Input>
		<wps:Input>
			<ows:Identifier>STATISTICS</ows:Identifier>
			<wps:Data>
				<wps:LiteralData>MEAN</wps:LiteralData>
			</wps:Data>
		</wps:Input>
		<wps:Input>
			<ows:Identifier>SUMMARIZE_FEATURE_ATTRIBUTE</ows:Identifier>
			<wps:Data>
				<wps:LiteralData>false</wps:LiteralData>
			</wps:Data>
		</wps:Input>
		<wps:Input>
			<ows:Identifier>FEATURE_COLLECTION</ows:Identifier>
			<wps:Reference xlink:href="http://cida.usgs.gov/gdp/geoserver/wfs">
				<wps:Body>
					<wfs:GetFeature xmlns:wfs="http://www.opengis.net/wfs" xmlns:ogc="http://www.opengis.net/ogc" xmlns:gml="http://www.opengis.net/gml" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" service="WFS" version="1.1.0" outputFormat="text/xml; subtype=gml/3.1.1" xsi:schemaLocation="http://www.opengis.net/wfs ../wfs/1.1.0/WFS.xsd">
						<wfs:Query typeName="sample:CONUS_states">
							<wfs:PropertyName>the_geom</wfs:PropertyName>
							<wfs:PropertyName>STATE</wfs:PropertyName>
							<ogc:Filter>
								<ogc:GmlObjectId gml:id="CONUS_states.459"/>
								<ogc:GmlObjectId gml:id="CONUS_states.472"/>
								<ogc:GmlObjectId gml:id="CONUS_states.477"/>
								<ogc:GmlObjectId gml:id="CONUS_states.479"/>
								<ogc:GmlObjectId gml:id="CONUS_states.481"/>
								<ogc:GmlObjectId gml:id="CONUS_states.486"/>
							</ogc:Filter>
						</wfs:Query>
					</wfs:GetFeature>
				</wps:Body>
			</wps:Reference>
		</wps:Input>
	</wps:DataInputs>
	<wps:ResponseForm>
		<wps:ResponseDocument storeExecuteResponse="true" status="true">
			<wps:Output asReference="true" mimeType="text/csv">
				<ows:Identifier>OUTPUT</ows:Identifier>
			</wps:Output>
		</wps:ResponseDocument>
	</wps:ResponseForm>
</wps:Execute>
```
This request can be easily sent utilizing curl:
```
curl -d @example_req.xml http://localhost:8080/gdp-process-wps/WebProcessingService
```
Assuming that the above sample request specified was saved in a file called *example_req.xml*.


  [
    ![CC0](http://i.creativecommons.org/p/zero/1.0/88x31.png)
  ](http://creativecommons.org/publicdomain/zero/1.0/)

  To the extent possible under law,
  [
    <span property="dct:title">The Center For Integrated Data Analytics</span>](http://cida.usgs.gov/)
  has waived all copyright and related or neighboring rights to
  <span property="dct:title">The Geo Data Portal</span>.
This work is published from:
<span property="vcard:Country" datatype="dct:ISO3166"
      content="US" about="http://cida.usgs.gov/">
  United States</span>.
