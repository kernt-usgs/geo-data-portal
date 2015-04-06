// JSLint fixes
/*jslint plusplus: true */
/*global $ */
/*global OpenLayers */
/*global Sarissa */
/*global CSWClient */

var GDP = GDP || {};
GDP.CSW = function (args) {
	"use strict";
	args = args || {};
	this.url = args.url;
	this.proxy = args.proxy;
	this.capabilitiesDocument = args.capabilitiesDocument;
	this.cswClient = new CSWClient(this.url);
	/**
	 * Create a CSW GetCapabilities request
	 * @param args {Object}
	 */
	var requestGetCapabilities = function (args) {
		args = args || {};

		var callbacks = args.callbacks || {
			success: [],
			error: []
		},
		url = args.url || this.url,
			scbInd,
			capabilitiesDocument = args.capabilitiesDocument || this.capabilitiesDocument,
			scope = args.scope || this,
			me = this;

		// Check to see if there's a capabilities document available in cache
		if (capabilitiesDocument) {
			for (scbInd = 0; scbInd < callbacks.success.length; scbInd++) {
				callbacks.success[scbInd].call(this, capabilitiesDocument);
			}
		} else {
			OpenLayers.Request.GET({
				url: url,
				params: {
					request: "GetCapabilities",
					service: "CSW",
					version: "2.0.2"
				},
				success: function (response) {
					var responseXML = response.responseXML;

					// Add the getCapabilities response to cache
					me.capabilitiesDocument = responseXML;

					if (callbacks.success && callbacks.success.length) {
						for (scbInd = 0; scbInd < callbacks.success.length; scbInd++) {
							callbacks.success[scbInd].call(scope, me.capabilitiesDocument);
						}
					}
				},
				failure: function (response) {
					if (callbacks.error) {
						for (scbInd = 0; scbInd < callbacks.error.length; scbInd++) {
							callbacks.error[scbInd].call(scope, response);
						}
					}
				}
			});
		}
	},
		getCapabilitiesKeywords = function (capablitiesXmlDoc) {
			if (!capablitiesXmlDoc && this.capabilitiesDocument) {
				capablitiesXmlDoc = this.capabilitiesDocument;
			} else if (!capablitiesXmlDoc && !this.capabilitiesDocument) {
				throw "missing capabilities xml document";
			}

			var oDomDoc = Sarissa.getDomDocument(),
				keywordNodes,
				keywords = [],
				kwInd;

			oDomDoc.setProperty("SelectionLanguage", "XPath");
			oDomDoc.setProperty("SelectionNamespaces",
				"xmlns:xhtml='http://www.w3.org/1999/xhtml' " +
				"xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
				"xmlns:ows='http://www.opengis.net/ows' " +
				"xmlns:csw='http://www.opengis.net/cat/csw/2.0.2'");
			keywordNodes = capablitiesXmlDoc.selectNodes('//ows:Keywords/ows:Keyword');

			for (kwInd = 0; kwInd < keywordNodes.length; kwInd++) {
				keywords.push(keywordNodes[kwInd].innerHTML);
			}

			return keywords;
		},
		getRecordsByKeywords = function (args) {
			args = args || {};
			var rId,
				keywords = args.keywords || [],
				record,
				records = [],
				recordSet = GDP.CONFIG.offeringMaps.cswIdentToRecord,
				keyword,
				recordKeyword,
				kwIdx,
				rkwIdx,
				recordKeywords;

			if (keywords && keywords.length) {
				for (rId in recordSet) {
					if (recordSet.hasOwnProperty(rId)) {
						record = recordSet[rId];
						recordKeywords = this.getKeywordsForRecord({
							recordId: rId
						});

						for (rkwIdx = 0; rkwIdx < recordKeywords.length && records.indexOf(record) === -1; rkwIdx++) {
							recordKeyword = String(recordKeywords[rkwIdx]).toLowerCase();
							for (kwIdx = 0; kwIdx < keywords.length && records.indexOf(record) === -1; kwIdx++) {
								keyword = String(keywords[kwIdx]).toLowerCase();
								if (recordKeyword.indexOf(keyword) !== -1) {
									records.push(record);
								}
							}
						}
					}
				}
			}

			return records;
		},
		/**
		 * Returns all keywords associated with a specific record
		 * @param {type} args
		 * @returns {Array|GDP.CSW.getKeywordsForRecord.keywords}
		 */
		getKeywordsForRecord = function (args) {
			args = args || {};
			var rId = args.recordId,
				keywords = [],
				keywordSet,
				kwsIdx,
				kaIdx,
				keywordArr,
				keyword,
				record;

			if (!rId) {
				throw "recordId cannot be empty";
			}

			record = GDP.CONFIG.offeringMaps.cswIdentToRecord[rId];

			if (record) {
				keywordSet = record.identificationInfo[0].descriptiveKeywords;
				for (kwsIdx = 0; kwsIdx < keywordSet.length; kwsIdx++) {
					keywordArr = keywordSet[kwsIdx].keyword;
					for (kaIdx = 0; kaIdx < keywordArr.length; kaIdx++) {
						keyword = keywordArr[kaIdx].CharacterString.value;
						keywords.push(keyword);
					}
				}
			}

			return keywords;
		},
		/**
		 * 
		 * @param {type} args
		 * @returns {undefined}
		 */
		getRecordsByKeywordsFromServer = function (args) {
			args = args || {};
			var keywords = args.keywords || [],
				keyword,
				callbacks = args.callbacks || {
					success: [],
					error: []
				},
			maxRecords = args.maxRecords || 1000,
				scope = args.scope || this,
				fInd,
				kwInd,
				cswGetRecFormat = new OpenLayers.Format.CSWGetRecords(),
				filters = [],
				filter,
				andFilter = new OpenLayers.Filter.Logical({
					type: OpenLayers.Filter.Logical.AND,
					filters: filters[fInd]
				}),
				orFilter,
				getRecRequest,
				getRecordsResponse = new OpenLayers.Protocol.Response({
					requestType: "read"
				});

			if (!keywords.length || !keywords[0].length) {
				filters.push(
					new OpenLayers.Filter.Comparison({
						type: OpenLayers.Filter.Comparison.LIKE,
						property: "Anytext",
						value: '*'
					})
					);
			} else {
				for (fInd = 0; fInd < keywords.length; fInd++) {
					var keywordArr = keywords[fInd];

					orFilter = new OpenLayers.Filter.Logical({
						type: OpenLayers.Filter.Logical.OR
					});

					for (kwInd = 0; kwInd < keywordArr.length; kwInd++) {
						keyword = keywordArr[kwInd];
						if (filters.length < fInd + 1) {
							filters.push([]);
						}

						filter = new OpenLayers.Filter.Comparison({
							type: OpenLayers.Filter.Comparison.LIKE,
							property: "Anytext",
							value: keyword,
							matchCase: false
						});

						orFilter.filters.push(filter);
					}

					if (orFilter && orFilter.filters.length) {
						andFilter.filters.push(orFilter);
					}
				}
			}

			getRecRequest = cswGetRecFormat.write({
				resultType: "results",
				maxRecords: String(maxRecords),
				outputSchema: "http://www.isotc211.org/2005/gmd",
				Query: {
					ElementSetName: {
						value: "full"
					},
					Constraint: {
						"version": "1.1.0",
						"Filter": andFilter
					}
				}
			});

			getRecordsResponse.priv = OpenLayers.Request.POST({
				url: this.url,
				data: getRecRequest,
				success: function (response) {
					var cswGetRecRespObj = cswGetRecFormat.read(response.responseXML || response.responseText),
						scbInd;

					if (cswGetRecRespObj.success !== false) {
						if (callbacks.success && callbacks.success.length) {
							for (scbInd = 0; scbInd < callbacks.success.length; scbInd++) {
								callbacks.success[scbInd].call(scope, cswGetRecRespObj);
							}
						}
					} else {
						GDP.CONFIG.ui.errorEncountered({
							data: 'Unfortunately the metadata catalog is ' +
								'experiencing technical difficulties. For more information, ' +
								'go to <a href="https://my.usgs.gov/confluence/display/GeoDataPortal/GDP+Home">' +
								'The Geo Data Portal Wiki</a> ',
							recoverable: false
						});
					}
				},
				failure: function (response) {
					var scbInd;
					if (callbacks.error) {
						for (scbInd = 0; scbInd < callbacks.error.length; scbInd++) {
							callbacks.error[scbInd].call(scope, response);
						}
					}
				}
			});
		},
		/**
		 * 
		 * @param {type} args
		 * @returns {undefined}
		 */
		getDomain = function (args) {
			args = args || {};

			var cswGetDomainFormat = new OpenLayers.Format.CSWGetDomain(),
				propertyName = args.propertyName || '',
				scope = args.scope || this,
				callbacks = args.callbacks || {
					success: [],
					error: []
				},
			getDomainReqData = cswGetDomainFormat.write({
					PropertyName: propertyName
				});

			OpenLayers.Request.POST({
				url: this.url,
				data: getDomainReqData,
				success: function (request) {
					var cswGetDomainResponseObject = cswGetDomainFormat.read(request.responseXML || request.responseText),
						scbInd;

					if (callbacks.success && callbacks.success.length) {
						for (scbInd = 0; scbInd < callbacks.success.length; scbInd++) {
							callbacks.success[scbInd].call(scope, cswGetDomainResponseObject);
						}
					}
				},
				failure: function (response) {
					var scbInd;
					if (callbacks.error) {
						for (scbInd = 0; scbInd < callbacks.error.length; scbInd++) {
							callbacks.error[scbInd].call(scope, response);
						}
					}
				}
			});
		},
		getAlgorithmArrayFromRecord = function (args) {
			args = args || {};
			if (!args.record) {
				throw "undefined record passed in";
			}
			var record = args.record,
				idInfoIdx,
				idInfoElement,
				dkIdx,
				kwArray,
				kwIdx,
				keyword,
				algorithmArray = [];

			if (record.hasOwnProperty('identificationInfo')) {
				for (idInfoIdx = 0; idInfoIdx < record.identificationInfo.length; idInfoIdx++) {
					idInfoElement = record.identificationInfo[idInfoIdx];
					if (idInfoElement.hasOwnProperty('descriptiveKeywords')) {
						for (dkIdx = 0; dkIdx < idInfoElement.descriptiveKeywords.length; dkIdx++) {
							kwArray = idInfoElement.descriptiveKeywords[dkIdx].keyword;
							for (kwIdx = 0; kwIdx < kwArray.length; kwIdx++) {
								keyword = kwArray[kwIdx].CharacterString.value;
								if (keyword.toLowerCase().indexOf('gov.usgs.cida.gdp.wps') !== -1) {
									algorithmArray.push(keyword);
								}
							}
						}
					}
				}
			}
			return algorithmArray;
		},
		getGeographicElementFromRecord = function (args) {
			args = args || {};
			if (!args.record) {
				throw "undefined record passed in";
			}
			var record = args.record,
				geographicElement = null,
				idInfoIdx,
				idInfoElement,
				idExtentIdx,
				idExtentElement;

			// record->identificationInfo[0]->extent[1]->geographicElement[0]->{eastBoundLongitude, northBoundLatitude, southBoundLatitude, westBoundLongitude}
			if (record.hasOwnProperty('identificationInfo')) {
				for (idInfoIdx = 0; idInfoIdx < record.identificationInfo.length && geographicElement == null; idInfoIdx++) {
					idInfoElement = record.identificationInfo[idInfoIdx];
					if (idInfoElement.hasOwnProperty('extent')) {
						for (idExtentIdx = 0; idExtentIdx < idInfoElement.extent.length && geographicElement == null; idExtentIdx++) {
							idExtentElement = idInfoElement.extent[idExtentIdx];
							if (idExtentElement.hasOwnProperty('geographicElement')) {
								geographicElement = idExtentElement.geographicElement[0];
							}
						}
					}
				}
			}
			
			/**
			 * 	Openlayers Bounds is constructed as:
			 * 
			 *  	OpenLayers.Bounds(left, bottom, right, top);
			 *  
			 *  So we will map the geographicElement values to this structure.
			 *  
			 *  	{
			 *  		eastBoundLongitude = right
			 *  		northBoundLatitude = top
			 *  		southBoundLatitude = bottom
			 *  		westBoundLongitude = left
			 *  	}
			 */
			var geoInfo = {left: 0, bottom: 0, right: 0, top: 0};
			if(geographicElement != null) {				
				if (geographicElement.hasOwnProperty('eastBoundLongitude')) {
					geoInfo.right = geographicElement.eastBoundLongitude.Decimal;				
				}
				
				if (geographicElement.hasOwnProperty('northBoundLatitude')) {
					geoInfo.top = geographicElement.northBoundLatitude.Decimal;				
				}
				
				if (geographicElement.hasOwnProperty('southBoundLatitude')) {
					geoInfo.bottom = geographicElement.southBoundLatitude.Decimal;				
				}
				
				if (geographicElement.hasOwnProperty('westBoundLongitude')) {
					geoInfo.left = geographicElement.westBoundLongitude.Decimal;				
				}
			}

			return geoInfo;
		},
		getTitleFromRecord = function (args) {
			args = args || {};
			if (!args.record) {
				throw "undefined record passed in";
			}
			var record = args.record,
				title = '',
				idInfoIdx,
				citation,
				idInfoElement;

			if (record.hasOwnProperty('identificationInfo')) {
				for (idInfoIdx = 0; idInfoIdx < record.identificationInfo.length && title === ''; idInfoIdx++) {
					idInfoElement = record.identificationInfo[idInfoIdx];
					if (idInfoElement.hasOwnProperty('citation')) {
						citation = idInfoElement.citation;
						title = citation.title.CharacterString.value;
					}
				}
			}

			return title;
		},
		getAbstractFromRecord = function (args) {
			args = args || {};
			if (!args.record) {
				throw "undefined record passed in";
			}
			var record = args.record,
				abstract = '',
				idInfoIdx,
				idInfoElement;

			if (record.hasOwnProperty('identificationInfo')) {
				for (idInfoIdx = 0; idInfoIdx < record.identificationInfo.length && abstract === ''; idInfoIdx++) {
					idInfoElement = record.identificationInfo[idInfoIdx];
					if (idInfoElement.hasOwnProperty('abstract')) {
						abstract = idInfoElement.abstract.CharacterString.value;
					}
				}
			}

			return abstract;
		},
		getUrlToIdentifierFromRecords = function (args) {
			args = args || {};
			if (!args.records) {
				throw "undefined records passed in";
			}
			var records = args.records,
				urlTocswIdentifier = {},
				rIdx,
				record,
				ident,
				url,
				toIdx,
				dtoIdx,
				idiIdx,
				distributor,
				distributionFormat,
				distributorTransferOptions,
				distributionTransferOption,
				distributionTransferOptionName,
				identificationInfos,
				identificationInfo,
				serviceIdentification,
				operationMetadataName,
				transferOptions,
				transferOption,
				transferOptionName;

			for (rIdx = 0; rIdx < records.length; rIdx++) {
				record = records[rIdx];
				ident = record.fileIdentifier.CharacterString.value;
				url = '';

				if (record.hasOwnProperty('identificationInfo')) {
					identificationInfos = record.identificationInfo;
					for (idiIdx = 0; idiIdx < identificationInfos.length; idiIdx++) {
						identificationInfo = identificationInfos[idiIdx];
						if (identificationInfo.hasOwnProperty('serviceIdentification')) {
							serviceIdentification = identificationInfo.serviceIdentification;
							operationMetadataName = serviceIdentification.operationMetadata.name.CharacterString.value.toLowerCase();
							if (operationMetadataName.indexOf('thredds') !== -1 ||
								operationMetadataName === 'opendap') {
								url = serviceIdentification.operationMetadata.linkage.URL;
								urlTocswIdentifier[url] = ident;
							}
						}
					}
				} else if (record.hasOwnProperty('distributionInfo')) {
					if (record.distributionInfo.hasOwnProperty('distributor')) {
						distributor = record.distributionInfo.distributor[0];
						distributionFormat = distributor.distributorFormat[0].name.CharacterString.value;
						if (distributionFormat.toLowerCase() === 'opendap') {
							distributorTransferOptions = distributor.distributorTransferOptions;
							for (dtoIdx = 0; dtoIdx < distributorTransferOptions.length; dtoIdx++) {
								distributionTransferOption = distributorTransferOptions[dtoIdx];
								distributionTransferOptionName = distributionTransferOption.onLine[0].name.CharacterString.value;
								if (distributionTransferOptionName.toLowerCase() === 'file information') {
									url = distributionTransferOption.onLine[0].linkage.URL;
									urlTocswIdentifier[url] = ident;
								}
							}
						}
					} else if (record.distributionInfo.hasOwnProperty('transferOptions')) {
						transferOptions = record.distributionInfo.transferOptions;
						for (toIdx = 0; toIdx < transferOptions.length; toIdx++) {
							transferOption = transferOptions[toIdx].onLine[0];
							transferOptionName = transferOption.name.CharacterString.value.toLowerCase();
							if (transferOptionName === 'opendap' ||
								transferOptionName.indexOf('wcs') !== -1) {
								url = transferOption.linkage.URL;
								urlTocswIdentifier[url] = ident;
							}
						}
					}
				}
			}
			return urlTocswIdentifier;
		},
		getEndpointFromRecord = function (args) {
			args = args || {};
			if (!args.record) {
				throw "undefined record passed in";
			}
			var record = args.record,
				distributionInfo,
				transferOption,
				transferOptions,
				url,
				protocol,
				toIndex,
				endpoint = '';

			if (record.hasOwnProperty('distributionInfo')) {
				distributionInfo = record.distributionInfo;
				if (distributionInfo.hasOwnProperty('transferOptions')) {
					transferOptions = distributionInfo.transferOptions;
					for (toIndex = 0; toIndex < transferOptions.length && endpoint === ''; toIndex++) {
						transferOption = transferOptions[toIndex];
						protocol = transferOption.onLine[0].name.CharacterString.value.toLowerCase();
						url = transferOption.onLine[0].linkage.URL;
						if (protocol === 'opendap' || url.toLowerCase().indexOf('wcs')) {
							endpoint = url;
						}
					}
				}
			}

			return endpoint;
		},
		getCswIdentToRecordMapFromRecordsArray = function (args) {
			args = args || {};

			if (!args.records) {
				throw "undefined record response object passed in";
			}

			var records = args.records,
				identToRecord = {},
				rIdx,
				record,
				ident;

			for (rIdx = 0; rIdx < records.length; rIdx++) {
				record = records[rIdx];
				ident = record.fileIdentifier.CharacterString.value;
				if (!identToRecord.hasOwnProperty(ident)) {
					identToRecord[ident] = record;
				}
			}

			return identToRecord;
		},
		getStatusFromRecord = function (args) {
			args = args || {};
			if (!args.record) {
				throw "undefined record passed in";
			}
			var record = args.record,
				status = '',
				statusObject,
				rIndex,
				codeList,
				identificationInfoArray,
				identificationInfoObject;

			if (record.identificationInfo) {
				identificationInfoArray = record.identificationInfo;
				for (rIndex = 0; rIndex < identificationInfoArray.length && status === ''; rIndex++) {
					identificationInfoObject = identificationInfoArray[rIndex];
					if (identificationInfoObject.status) {
						statusObject = identificationInfoObject.status[0];
						if (statusObject.codeList) {
							codeList = statusObject.codeList;
							if (codeList.toLowerCase().indexOf('progresscode') > -1) {
								status = statusObject.codeListValue;
							}
						}
					}
				}
			}

			return status;
		},
		createFullRecordView = function (args) {
			args = args || {};
			if (!args.identifier) {
				throw "undefined identifier passed in";
			}
			var identifier = args.identifier,
				cswResponse,
				getrecordXML = '<csw:GetRecordById xmlns:csw="http://www.opengis.net/cat/csw/2.0.2" ' +
				'xmlns:xlink="http://www.w3.org/1999/xlink" service="CSW" version="2.0.2" ' +
				'outputFormat="application/xml" outputSchema="http://www.isotc211.org/2005/gmd">' +
				'<csw:Id>' + identifier + '</csw:Id><csw:ElementSetName>full</csw:ElementSetName></csw:GetRecordById>';
			cswResponse = this.client.sendCSWRequest(getrecordXML);
			this.client.handleCSWResponse("getrecordbyid", cswResponse, "html");
		},
		createOptionFromRecord = function (args) {
			args = args || {};
			if (!args.record) {
				throw "undefined record passed in";
			}
			var record = args.record,
				ident,
				distributionInfo,
				identificationInfos,
				identificationInfo,
				serviceIdentification,
				operationMetadata,
				operationMetadataName,
				transferOptions,
				transferOption,
				transferOptionName,
				parentTitle,
				opt,
				$option,
				optionsCount = 0,
				options = {},
				url,
				title,
				idiIdx,
				toIdx;

			ident = record.fileIdentifier.CharacterString.value;

			// If there's only one identificationInfo, that means that the identificationInfo array has
			// no urls and only record identification stuff (name, title, etc)
			if (record.hasOwnProperty('identificationInfo') && record.identificationInfo.length > 1) {
				identificationInfos = record.identificationInfo;
				parentTitle = identificationInfos[0].citation.title.CharacterString.value;
				for (idiIdx = 0; idiIdx < identificationInfos.length; idiIdx++) {
					identificationInfo = identificationInfos[idiIdx];
					if (identificationInfo.hasOwnProperty('serviceIdentification')) {
						serviceIdentification = identificationInfo.serviceIdentification;
						operationMetadataName = serviceIdentification.operationMetadata.name.CharacterString.value;
						operationMetadata = serviceIdentification.operationMetadata;
						url = operationMetadata.linkage.URL;
						title = serviceIdentification.citation.title.CharacterString.value;

						if ((operationMetadataName.toLowerCase() === 'opendap') ||
							(operationMetadataName.toLowerCase().indexOf('wcs') !== -1 && optionsCount === 0)) {
							options[url] = {
								name: operationMetadataName,
								title: title
							};
							optionsCount++;
						}
					}
				}
			}

			// We didn't get a value out of identificationInfo. Try distributionsInfo
			if (Object.keys(options).length === 0 && record.hasOwnProperty('distributionInfo')) {
				distributionInfo = record.distributionInfo;
				// try tranferOptions
				if (distributionInfo.hasOwnProperty('transferOptions')) {
					transferOptions = record.distributionInfo.transferOptions;
					for (toIdx = 0; toIdx < transferOptions.length; toIdx++) {
						transferOption = transferOptions[toIdx];
						transferOptionName = transferOption.onLine[0].name.CharacterString.value;
						url = transferOption.onLine[0].linkage.URL;
						operationMetadataName = transferOption.onLine[0].name.CharacterString.value;
						title = record.identificationInfo[0].citation.title.CharacterString.value;
						if (transferOptionName.toLowerCase() === 'opendap' ||
							(transferOptionName.toLowerCase().indexOf('wcs') !== -1 && optionsCount === 0)) {
							options[url] = {
								name: operationMetadataName,
								title: title
							};
							optionsCount++;
						}
					}
				}
			}

			if (optionsCount === 1) {
				for (opt in options) {
					if (options.hasOwnProperty(opt)) {
						$option = $('<option>').
							attr({
								value: opt + ';' + ident
							}).
							addClass('top-lvl-opt').
							html(options[opt].title);
					}
				}
			} else if (optionsCount > 1) {
				$option = $('<option>').
					addClass('top-lvl-opt opt-haschildren').
					html(parentTitle + '&nbsp;&darr;');
				
				// This options object is used if this option is selected in order to create a secondary dropdown
				// list using this options object
				options.ident = ident;
				$option.data('suboptions', options);
			}

			return $option;

		};

	return {
		requestGetCapabilities: requestGetCapabilities,
		getCapabilitiesKeywords: getCapabilitiesKeywords,
		getRecordsByKeywordsFromServer: getRecordsByKeywordsFromServer,
		getRecordsByKeywords: getRecordsByKeywords,
		getDomain: getDomain,
		getAlgorithmArrayFromRecord: getAlgorithmArrayFromRecord,
		getGeographicElementFromRecord: getGeographicElementFromRecord,
		getTitleFromRecord: getTitleFromRecord,
		getAbstractFromRecord: getAbstractFromRecord,
		getEndpointFromRecord: getEndpointFromRecord,
		getUrlToIdentifierFromRecords: getUrlToIdentifierFromRecords,
		createOptionFromRecord: createOptionFromRecord,
		getCswIdentToRecordMapFromRecordsArray: getCswIdentToRecordMapFromRecordsArray,
		createFullRecordView: createFullRecordView,
		getKeywordsForRecord: getKeywordsForRecord,
		getStatusFromRecord: getStatusFromRecord,
		url: this.url,
		proxy: this.proxy,
		client: this.cswClient,
		capabilitiesDocument: this.capabilitiesDocument
	};
};