var GDP = GDP || {};

// JSLint fixes
/*global $ */
/*global window */
/*global document */
/*jslint plusplus: true */

/* All of these values come from GDP-UI map.js as standard for this app */
var _NUM_ZOOM_LEVELS = 18;
var _MAX_RESOLUTION = 1.40625/2;
var _MAX_EXTENT = [-20037508.34, -20037508.34,20037508.34, 20037508.34];

GDP.UI = function (args) {
	"use strict";
	args = args || {};

	// Initialization
	this.init = function (args) {
		args = args || {};

		// Attach arrays of algorithm names to their respective buttons
		$('#btn-choice-algorithm-areal').data({
			algorithms: [
				"gov.usgs.cida.gdp.wps.algorithm.FeatureWeightedGridStatisticsAlgorithm",
				"gov.usgs.cida.gdp.wps.algorithm.FeatureGridStatisticsAlgorithm",
				"gov.usgs.cida.gdp.wps.algorithm.FeatureCategoricalGridCoverageAlgorithm"
			]
		});
		$('#btn-choice-algorithm-subset').data({
			algorithms: [
				"gov.usgs.cida.gdp.wps.algorithm.FeatureCoverageOPeNDAPIntersectionAlgorithm",
				"gov.usgs.cida.gdp.wps.algorithm.FeatureCoverageIntersectionAlgorithm"
			]
		});

		// Fix the header of the application by removing unused elements in the 
		// right container
		$('#ccsa-area').children().slice(0, 2).remove();

		var me = this,
		/**
		 *	Sciencebase doesn't have a GetDomain call, so had to hard-code the algorithm list in an array
		 *	and loop through that.  Precedence was set when the algorithms were hard-coded to their buttons
		 */
			updateOfferingMaps = function () {
			    var dvIdx,
				value,
				domainValues = [
					"gov.usgs.cida.gdp.wps.algorithm.FeatureWeightedGridStatisticsAlgorithm",
					"gov.usgs.cida.gdp.wps.algorithm.FeatureGridStatisticsAlgorithm",
					"gov.usgs.cida.gdp.wps.algorithm.FeatureCategoricalGridCoverageAlgorithm",
					"gov.usgs.cida.gdp.wps.algorithm.FeatureCoverageOPeNDAPIntersectionAlgorithm",
					"gov.usgs.cida.gdp.wps.algorithm.FeatureCoverageIntersectionAlgorithm"
					];

			    for (dvIdx = 0; dvIdx < domainValues.length; dvIdx++) {
				value = domainValues[dvIdx];
				GDP.CONFIG.offeringMaps.wpsToCsw[value] = {};
				}
			    GDP.CONFIG.cswClient.getRecordsByKeywordsFromServer({
				scope: me,
				keywords: [Object.keys(GDP.CONFIG.offeringMaps.wpsToCsw)],
				callbacks: {
				    success: [
					function (cswGetRecRespObj) {
						var records = cswGetRecRespObj.records,
						    wpsToCsw = GDP.CONFIG.offeringMaps.wpsToCsw,
						    cswToWps = GDP.CONFIG.offeringMaps.cswToWps,
						    rIdx,
						    record,
						    algIdx,
						    algName,
						    ident,
						    url,
						    algorithm,
						    algorithmArray;

			    			for (rIdx = 0; rIdx < records.length; rIdx++) {
							record = records[rIdx];
							ident = record.fileIdentifier.CharacterString.value;
							url = '';
							if (!GDP.CONFIG.offeringMaps.cswIdentToRecord[ident]) {
								GDP.CONFIG.offeringMaps.cswIdentToRecord[ident] = record;
							}
							
							algorithmArray = GDP.CONFIG.cswClient.getAlgorithmArrayFromRecord({
							    record: record
							});

							for (algIdx = 0; algIdx < algorithmArray.length; algIdx++) {
							    algorithm = algorithmArray[algIdx];
							    if (!wpsToCsw[algorithm][ident]) {
								wpsToCsw[algorithm][ident] = record;
							    }
							}

							if (!cswToWps[ident]) {
							    cswToWps[ident] = [];
							}
						}

						for (algName in wpsToCsw) {
						    if (wpsToCsw.hasOwnProperty(algName)) {
							for (ident in wpsToCsw[algName]) {
							    if (wpsToCsw[algName].hasOwnProperty(ident)) {
								if (!cswToWps[ident]) {
								    cswToWps[ident] = [];
								}
								cswToWps[ident].push(algName);
							    }
							}
						    }
						}
						this.initializationCompleted();
					}
				    ],
				    error: [
					function (error) {
					    GDP.CONFIG.ui.errorEncountered({
						data: error,
						recoverable: false
					    });
					}
				    ]
				}
			    });
			},
			/**
			 * Handles clicking of either datasets buttons or processing buttons
			 * @type type
			 */
			buttonSelected = function (event) {
				var me = this,
					button = event.target,
					buttonId = button.id,
					keywords = [],
					dbIdx = 0,
					dId,
					depressedButtons = $('#row-choice-start label.active input'), // [:(]
					depressedButtonIds = [event.target.id], // [:(].killMe
					records,
					btnDatasetAllId = 'btn-choice-dataset-all',
					btnDatasetClimateId = 'btn-choice-dataset-climate',
					btnDatasetLandscapeId = 'btn-choice-dataset-landscape',
					btnAlgorithmArealId = 'btn-choice-algorithm-areal',
					btnAlgorithmSubsetId = 'btn-choice-algorithm-subset',
					arealAlgs = $('#' + btnAlgorithmArealId).data('algorithms'),
					subsetAlgs = $('#' + btnAlgorithmSubsetId).data('algorithms');

				for (dbIdx; dbIdx < depressedButtons.length; dbIdx++) {
					dId = depressedButtons[dbIdx].id;
					if (depressedButtonIds.indexOf(dId) === -1) {
						depressedButtonIds.push(dId);
					}
				}

				if (buttonId === btnDatasetAllId) {
					records = GDP.CONFIG.offeringMaps.cswIdentToRecord;
				} else if (buttonId === btnDatasetClimateId) {
					keywords = [['*climate*']];
					if (depressedButtonIds.indexOf(btnAlgorithmArealId) !== -1) {
						keywords.push(arealAlgs);
					}
					if (depressedButtonIds.indexOf(btnAlgorithmSubsetId) !== -1) {
						keywords.push(subsetAlgs);
					}
				} else if (buttonId === btnDatasetLandscapeId) {
					keywords = [['*landscape*']];
					if (depressedButtonIds.indexOf(btnAlgorithmArealId) !== -1) {
						keywords.push(arealAlgs);
					}
					if (depressedButtonIds.indexOf(btnAlgorithmSubsetId) !== -1) {
						keywords.push(subsetAlgs);
					}
				} else if (buttonId === btnAlgorithmArealId) {
					if (depressedButtonIds.indexOf(btnDatasetClimateId) === -1 &&
						depressedButtonIds.indexOf(btnDatasetLandscapeId) === -1) {
						records = GDP.CONFIG.wpsClient.getRecordsByAlgorithmArray({
							algorithms: arealAlgs
						});
					} else {
						keywords.push(arealAlgs);
						if (depressedButtonIds.indexOf(btnDatasetClimateId) !== -1) {
							keywords.push(['*climate*']);
						} else if (depressedButtonIds.indexOf(btnDatasetLandscapeId) !== -1) {
							keywords.push(['*landscape*']);
						}
					}
				} else if (buttonId === btnAlgorithmSubsetId) {
					if (depressedButtonIds.indexOf(btnDatasetClimateId) === -1 &&
						depressedButtonIds.indexOf(btnDatasetLandscapeId) === -1) {
						records = GDP.CONFIG.wpsClient.getRecordsByAlgorithmArray({
							algorithms: subsetAlgs
						});
					} else {
						keywords.push(subsetAlgs);
						if (depressedButtonIds.indexOf(btnDatasetClimateId) !== -1) {
							keywords.push(['*climate*']);
						} else if (depressedButtonIds.indexOf(btnDatasetLandscapeId) !== -1) {
							keywords.push(['*landscape*']);
						}
					}
				}

				if (records) {
					me.updateCswDropdown({
						offerings: records
					});
				} else {
					GDP.CONFIG.cswClient.getRecordsByKeywordsFromServer({
						scope: me,
						keywords: keywords,
						callbacks: {
							success: [
								function (cswGetRecRespObj) {
									var records;

									records = GDP.CONFIG.cswClient.getCswIdentToRecordMapFromRecordsArray({
										records: cswGetRecRespObj.records
									});

									if (Object.keys(cswGetRecRespObj).length > 0) {
										me.updateCswDropdown({
											offerings: records
										});
									} else {
										$.bootstrapGrowl('Could not find any records to match your criteria',
											{
												type: 'info',
												'allow_dismiss': false
											});
									}
								}
							],
							error: [
								function (error) {
									GDP.CONFIG.ui.errorEncountered({
										data: error,
										recoverable: false
									});
								}
							]
						}
					});
				}
			};

		this.removeOverlay = function () {
			$('#overlay').fadeOut(800,
				function () {
					$('#overlay').remove();
				});
		};

		/**
		 * Handles both dataset selection dropdowns changing
		 * 
		 * @param {type} event
		 * @returns {undefined}
		 */
		this.cswDropdownChanged = function (event) {
			/**
			 * Whenever a dataset button is selected we need to make sure that
			 * the dataset map preview is reset (if it exists)
			 * 
			 * Since this is a variable declared in a different file.  Best
			 * practice is to make sure its actually defined before messing with
			 * it so we dont throw unrecoverable exceptions
			 */
			if((typeof datasetMapPreview != 'undefined') && (datasetMapPreview != null)) {
				/**
				 * We need to remove the olMap class completely from the map div
				 * prior to destroying it so that the height of the div goes back
				 * to what we originally wanted.
				 */
				$('#dataset-map-preview').removeClass('olMap');
				datasetMapPreview.destroy();
				
				/**
				 * Explicitly set this to null so that we dont depend on the openlayers
				 * destroy() method doing what it needs to do and we can rely on
				 * javascript garbage collection.
				 */ 
				datasetMapPreview = null;
			}
			
			
			var eventTarget = event.target,
				value = eventTarget.value,
				ident,
				isDatasetChosen,
				subOptions,
				subOptionsChild,
				isChildSelect = $(eventTarget).attr('id') === 'form-control-select-csw-child',
				$childSelectRow = $('#row-csw-select-child'),
				$childSelectControl = $('#form-control-select-csw-child'),
				$chosenOption = $(eventTarget).find('option:selected'),
				proceedRow = $('#row-proceed'),
				proceedRowPlaceholder = $('#row-proceed-placeholder'),
				contentContainer = $('#p-csw-information-content'),
				titleContainer = $('#p-csw-information-title'),
				getDescriptionObject = function (value, $target) {
					var ident = value.split(';')[1],
						record = GDP.CONFIG.offeringMaps.cswIdentToRecord[ident],
						title = GDP.CONFIG.cswClient.getTitleFromRecord({
							record: record
						}),
						subtitle = $target.find('option[value="' + value + '"]').html(),
						content = GDP.CONFIG.cswClient.getAbstractFromRecord({
							record: record
						}),
						geoInfo = GDP.CONFIG.cswClient.getGeographicElementFromRecord({
							record: record
						});

					if (subtitle !== title) {
						title += '<br />' + subtitle;
					}

					return {
						title: title,
						content: content,
						geoInfo: geoInfo
					};
				},
				/**
				 * UPDATE THIS METHOD (updateContent) to show and display a map with the info
				 * for this selection.
				 */
				updateContent = function (descriptionObject, ident, titleContainer, contentContainer) {
					titleContainer.html(descriptionObject.title);
					contentContainer.html(window.replaceURLWithHTMLLinks(descriptionObject.content));
					contentContainer.append('&nbsp;&nbsp;&nbsp;&nbsp;',
						$('<a />')
						.attr({
							'id': 'view-full-info-link',
							'ident': ident
						})
						.html('View Full Record')
						);
					
					/** Now lets create a map with the info we have and place it into the
					 *  dataset-map-preview div.
					 *  
					 *  Openlayers Bounds is constructed as:
					 *  
					 *  	OpenLayers.Bounds(left, bottom, right, top)
					 */
					var geoInfo = descriptionObject.geoInfo;
					var boundingBox = [geoInfo.left.value, geoInfo.bottom.value, geoInfo.right.value, geoInfo.top.value];
					
					if(datasetMapPreview != null) {
						datasetMapPreview.destroy();
						datasetMapPreview = null;
					}
					
					datasetMapPreview = new OpenLayers.Map('dataset-map-preview', {
						numZoomLevels: _NUM_ZOOM_LEVELS,
						maxResolution: _MAX_RESOLUTION,
					    maxExtent: new OpenLayers.Bounds(_MAX_EXTENT)
					});
					
					var layerOb = {
				            id: "shaded_relief",
				            name: "Shaded Relief",
				            url: "http://server.arcgisonline.com/ArcGIS/rest/services/ESRI_StreetMap_World_2D/MapServer/tile/${z}/${y}/${x}",
				            params: {
				            	isBaseLayer: "true",
				            	layers: "0",
				            	projection: "EPSG:4326",
				            	transitionEffect: "resize"},
				            OLparams: {} // parameters passed to OpenLayers, such as opacity
				        };
					
		            var layer = new OpenLayers.Layer.XYZ(
		            		layerOb.name,
		                    layerOb.url,
		                    layerOb.params,
		                    layerOb.OLparams
		                    );
		            layer.setVisibility(true);
		            datasetMapPreview.addLayer(layer);
		            
		            datasetMapPreview.zoomTo(3);
		            
		            var centerLon = parseFloat(geoInfo.left.value) + (parseFloat(geoInfo.right.value) - parseFloat(geoInfo.left.value));
		            var centerLat = parseFloat(geoInfo.bottom.value) + (parseFloat(geoInfo.top.value) - parseFloat(geoInfo.bottom.value));
		            var center = new OpenLayers.LonLat(centerLon, centerLat);
		            datasetMapPreview.setCenter(center);

		            /**
		             * Now create our bounding box of where this data comes from
		             */
					var vectorLayer = new OpenLayers.Layer.Vector("Vector Layer");
					
					var proj = new OpenLayers.Projection("EPSG:4326");
					var style_green = {strokeColor: "#ff3300",strokeOpacity: 1,strokeWidth: 2,fillColor: "#FF9966",fillOpacity: 0.1};
					var p1 = new OpenLayers.Geometry.Point(geoInfo.left.value,geoInfo.top.value)
					p1.transform(proj, datasetMapPreview.getProjectionObject());
					var p2 = new OpenLayers.Geometry.Point(geoInfo.right.value,geoInfo.top.value)
					p2.transform(proj, datasetMapPreview.getProjectionObject());
					var p3 = new OpenLayers.Geometry.Point(geoInfo.left.value,geoInfo.bottom.value)
					p3.transform(proj, datasetMapPreview.getProjectionObject());
					var p4 = new OpenLayers.Geometry.Point(geoInfo.right.value,geoInfo.bottom.value)
					p4.transform(proj, datasetMapPreview.getProjectionObject());
					var points = [];
					points.push(p1);
					points.push(p2);
					points.push(p4);
					points.push(p3);
					
					var poly = new OpenLayers.Geometry.LinearRing(points);
					var polygonFeature = new OpenLayers.Feature.Vector(poly, null, style_green);
					vectorLayer.addFeatures([polygonFeature]);
					
					datasetMapPreview.addLayer(vectorLayer);
					
					/**
					 * Lets force its visibility to ON
					 */
					vectorLayer.setVisibility(true);
					
					/**
					 * Now, using the bounding box we got for this dataset lets zoom the map to it as well
					 */
					datasetMapPreview.zoomToExtent(new OpenLayers.Bounds(geoInfo.left.value, geoInfo.bottom.value, geoInfo.right.value, geoInfo.top.value));
				},
				bindInfoLink = function () {
					$('#view-full-info-link').on('click', function () {
						var ident = this.attributes.ident.value;
						GDP.CONFIG.cswClient.createFullRecordView({
							identifier: ident
						});

						// The excat client we are using is specific to the GDP so it has
						// GDP functionality attached to some of the hrefs. We need to 
						// extract the links that the javascript are bound to and make 
						// the href regular hrefs
						$('.meta-value a[href*="javascript"]').each(function (i, o) {
							var hrefAttr = o.attributes.href.value,
								firstIndex = hrefAttr.indexOf("'") + 1,
								lastIndex = hrefAttr.lastIndexOf("'"),
								rootHref = hrefAttr.substring(firstIndex, lastIndex);

							$(o).attr({
								'href': rootHref,
								'target': '_datasetTab'
							});
						});

						// Create HTML links in the full record view
						$('.captioneddiv .meta td.meta-value').each(function (i, o) {
							$(o).html(window.replaceURLWithHTMLLinks($(o).html()));
						});

						$('#full-record-modal').modal('show');
					});
				};

			proceedRow.fadeOut();
			proceedRowPlaceholder.fadeIn();
			titleContainer.html('');
			contentContainer.html('');

			if (!value) {
				if (!isChildSelect) {
					$childSelectRow.fadeOut();
				}
			} else {
				ident = value.split(';')[1];

				if (!isChildSelect) {
					$childSelectRow.fadeOut();
					if ($chosenOption.hasClass('opt-haschildren')) {
						// User chose an option which has children
						subOptions = $chosenOption.data('suboptions');
						$childSelectControl.empty();
						var emptyOption = $('<option />').attr({
							'value': '',
							'disabled': true,
							'selected': true
						}).html(GDP.CONFIG.constants.DATASET_SELECT);

						for (subOptionsChild in subOptions) {
							if (subOptions.hasOwnProperty(subOptionsChild) && subOptionsChild !== 'ident') {
								$childSelectControl.append(
									$('<option>').
									attr({
										value: subOptionsChild + ';' + subOptions.ident
									}).
									html(subOptions[subOptionsChild].title)
									);
							}
						}
						var sel = $childSelectControl;
						var opts_list = sel.find('option');
						opts_list.sort(function(a, b) {
						    return $(a).text() > $(b).text(); 
						});
						$childSelectControl.append(emptyOption);
						$childSelectControl.append(opts_list);
						$childSelectRow.fadeIn();
					} else {
						// Chosen option has no children
						var descriptionObject = getDescriptionObject(value, $(eventTarget));
						updateContent(descriptionObject, ident, titleContainer, contentContainer);
					}
				} else {
					var descriptionObject = getDescriptionObject(value, $(eventTarget));
					updateContent(descriptionObject, ident, titleContainer, contentContainer);
				}

				isDatasetChosen = $('.btn-group label.active input').attr('id').indexOf('dataset') !== -1;

				if ($chosenOption.attr('value') && me.isProcessingButtonSelected()) {
					proceedRowPlaceholder.fadeOut();
					proceedRow.fadeIn();
					me.bindProceedButton();
				}
				
				bindInfoLink();
			}
		};

		this.updateCswDropdown = function (args) {
			args = args || {};
			var offerings = args.offerings || GDP.CONFIG.offeringMaps.cswToWps,
				$datasetDropDown = $('#form-control-select-csw'),
				$childSelectRow = $('#row-csw-select-child'),
				$datasetDropDownChild = $('#form-control-select-csw-child'),
				$cswGroupRow = $('#row-csw-group'),
				ident,
				option,
				emptyOption = $('<option />').attr({
				'value': '',
				'disabled': true,
				'selected': true
				}).html(GDP.CONFIG.constants.DATASET_SELECT),
				currentlySelectedOption = $datasetDropDown.val(),
				currentlySelectedChildOption = $datasetDropDownChild.val();

			$datasetDropDown.empty();
			$childSelectRow.fadeOut();
			$datasetDropDownChild.empty();

			for (ident in offerings) {
				if (offerings.hasOwnProperty(ident)) {
					option = GDP.CONFIG.cswClient.createOptionFromRecord({
						record: GDP.CONFIG.offeringMaps.cswIdentToRecord[ident]
					});
					$datasetDropDown.append(option);
				}
			}
			var sel = $datasetDropDown;
			var opts_list = sel.find('option');
			opts_list.sort(function(a, b) {
			    return $(a).text() > $(b).text() ? 1 : -1; 
			});
			
			$datasetDropDown.append(emptyOption);
			$datasetDropDown.append(opts_list);
			$datasetDropDown.val(currentlySelectedOption);
			$datasetDropDown.trigger('change');
			if (currentlySelectedChildOption && $datasetDropDownChild.children().length > 0) {
				$datasetDropDownChild.val(currentlySelectedChildOption);
				$datasetDropDownChild.trigger('change');
			}

			if ($cswGroupRow.css('display') === 'none') {
				$cswGroupRow.fadeIn();
			}

			$datasetDropDown.off('change', this.cswDropdownChanged);
			$datasetDropDown.on('change', this.cswDropdownChanged);
			$datasetDropDownChild.off('change', this.cswDropdownChanged);
			$datasetDropDownChild.on('change', this.cswDropdownChanged);
		};

		this.errorEncountered = function (args) {
			args = args || {};

			var data = args.data,
				recoverable = args.recoverable;

			if (recoverable) {
				$.bootstrapGrowl(data,
					{
						type: 'info',
						'allow_dismiss': false
					});
			} else {
				$('#unrecoverable-error-modal .modal-body').append(
					$('<div />').attr({
					'id': 'unrecoverable-error-modal-modal-body-content'
				}).html(data + '<br /><br />Please try to reload the application or contact the system administrator for support with this error.')
					);
				$('#unrecoverable-error-modal').modal('show');
			}
		};

		this.bindProceedButton = function () {
			$('#btn-proceed').off();

			$('#btn-proceed').on('click', function () {
				var csw,
					cswIdent,
					cswUrl,
					win,
					url,
					pKey,
					aIdx,
					algorithm,
					algorithms = [],
					buttonAlgoritms,
					recordAlgorithms,
					formContainer,
					form,
					useCache,
					status,
					incomingParams = GDP.CONFIG.incomingParams,
					$primaryDatasetDropdownOption = $('#form-control-select-csw option:selected'),
					$secondaryDatasetDropdownOption = $('#form-control-select-csw-child option:selected'),
					// If the first dropdown has no value, it means there's a second dataset open and that should have the value
					datasetDropdownValue = $primaryDatasetDropdownOption.attr('value') || $secondaryDatasetDropdownOption.attr('value'),
					algorithmButtonId = $('#col-choice-start-algorithm .btn-group label.active input').attr('id');


				if (datasetDropdownValue) {
					cswUrl = datasetDropdownValue.split(';')[0];
					cswIdent = datasetDropdownValue.split(';')[1];

					recordAlgorithms = GDP.CONFIG.cswClient.getAlgorithmArrayFromRecord({
						record: GDP.CONFIG.offeringMaps.cswIdentToRecord[cswIdent]
					});

					// Try to figure out which algorithsm from the selected 
					// algorithm button go with the selected dataset and only 
					// pass in the union of those algorithms over to the GDP
					buttonAlgoritms = $('#' + algorithmButtonId).data('algorithms');
					for (aIdx = 0; aIdx < buttonAlgoritms.length; aIdx++) {
						algorithm = buttonAlgoritms[aIdx];
						if (recordAlgorithms.indexOf(buttonAlgoritms[aIdx]) !== -1) {
							algorithms.push(buttonAlgoritms[aIdx]);
						}
					}
					algorithms = algorithms.join(',');

					csw = encodeURIComponent(cswUrl);
					status = GDP.CONFIG.cswClient.getStatusFromRecord({
						'record': GDP.CONFIG.offeringMaps.cswIdentToRecord[cswIdent]
					});
					useCache = status === 'completed';
					if (GDP.CONFIG.incomingMethod === 'GET') {
						url = GDP.CONFIG.hosts.gdp + '?dataset=' + csw + '&algorithm=' + algorithms + '&useCache=' + useCache;

						for (pKey in incomingParams) {
							if (incomingParams.hasOwnProperty(pKey) && pKey) {
								url += '&' + pKey + '=' + incomingParams[pKey];
							}
						}

						win = window.open(url);
						win.focus();
					} else if (GDP.CONFIG.incomingMethod === 'POST') {
						$('#gdp-redir-div').remove();
						formContainer = $('<div />').attr({
							'id': 'gdp-redir-div',
							'style': 'display:none;'
						});

						form = $('<form />').attr({
							'action': GDP.CONFIG.hosts.gdp,
							'method': 'POST',
							'name': 'gdp-redirect-post-form'
						});

						for (pKey in incomingParams) {
							if (incomingParams.hasOwnProperty(pKey) && pKey) {
								form.append($('<input />').attr({
									'type': 'hidden',
									'name': pKey,
									'value': incomingParams[pKey]
								}));
							}
						}

						form.append($('<input />').attr({
							'type': 'hidden',
							'name': 'algorithm',
							'value': algorithms
						}));

						form.append($('<input />').attr({
							'type': 'hidden',
							'name': 'dataset',
							'value': csw
						}));

						form.append($('<input />').attr({
							'type': 'hidden',
							'name': 'useCache',
							'value': useCache
						}));

						formContainer.append(form);
						$('body').append(formContainer);
						document.forms['gdp-redirect-post-form'].submit();
					}
				}

			});
		};

		this.isProcessingButtonSelected = function () {
			return $('#col-choice-start-algorithm label.active').length > 0 ? true : false;
		};

		this.initializationCompleted = function () {
			$('#row-choice-start input').on('change', $.proxy(buttonSelected, this));
			this.removeOverlay();
		};

		GDP.CONFIG.cswClient.requestGetCapabilities({
			callbacks: {
				success: [
					function () {
						if (GDP.CONFIG.wpsClient.capabilitiesDocument) {
							updateOfferingMaps();
						}
					}
				],
				error: [
					function (error) {
						GDP.CONFIG.ui.errorEncountered({
							data: error,
							recoverable: false
						});
					}
				]
			}
		});

		GDP.CONFIG.wpsClient.requestGetCapabilities({
			callbacks: {
				success: [
					function (capabilities) {
						if (GDP.CONFIG.cswClient.capabilitiesDocument) {
							updateOfferingMaps();
						}
					}
				],
				error: [
					function (error) {
						GDP.CONFIG.ui.errorEncountered({
							data: error,
							recoverable: false
						});
					}
				]
			}
		});
		
		// Check is incoming request has caller parameters. If so, update the view 
		var $incomingCallerDiv = $('#row-incoming-caller-info');
		var incomingParams = GDP.CONFIG.incomingParams;
		if (incomingParams['caller'] && incomingParams['item_id']) {
			// JIRA GDP-830 - This JIRA seems specific to sciencebase and yet this
			// code is pretty generic.  I am going to make a change to this just
			// for sciencebase as that is what is requested.
			if(incomingParams['caller'].toLowerCase() == 'sciencebase' ) {
				// 
				// https://www.sciencebase.gov/catalog/gdp/callGdp?item_id=54296bf0e4b0ad29004c2fbb&owsurl=https%3A%2F%2Fwww.sciencebase.gov%2FcatalogMaps%2Fmapping%2Fows%2F54296bf0e4b0ad29004c2fbb&wmsurl=https%3A%2F%2Fwww.sciencebase.gov%2FcatalogMaps%2Fmapping%2Fows%2F54296bf0e4b0ad29004c2fbb&wfsurl=https%3A%2F%2Fwww.sciencebase.gov%2FcatalogMaps%2Fmapping%2Fows%2F54296bf0e4b0ad29004c2fbb&wcsurl=
				// 
				//
				// We need to build the sciencebase url since its not included in the
				// request params.  Params passed in via ScienceBase look like:
				// 				caller: "sciencebase"
				//		 		development: "false"
				//		 		feature_wfs: "https://www.sciencebase.gov/catalogMaps/mapping/ows/54296bf0e4b0ad29004c2fbb"
				//		 		feature_wms: "https://www.sciencebase.gov/catalogMaps/mapping/ows/54296bf0e4b0ad29004c2fbb"
				//		 		item_id: "54296bf0e4b0ad29004c2fbb"
				//		 		ows: "https://www.sciencebase.gov/catalogMaps/mapping/ows/54296bf0e4b0ad29004c2fbb"
				//		 		redirect_url: "https://www.sciencebase.gov/catalog/gdp/landing/54296bf0e4b0ad29004c2fbb"
				//
				//		URL to sciencebase looks like:
				//				https://www.sciencebase.gov/catalog/item/54296bf0e4b0ad29004c2fbb
				//
				// So first thing is to get the request host
				var parser = document.createElement('a');
			    parser.href = incomingParams['redirect_url'];  
			    
			    var host = parser.hostname;
			    var proto = parser.protocol;
			    var url = proto + "//" + host + "/catalog/item/" + incomingParams['item_id'];
			    
			    var callerMsg = '<a href="' + url + '" target="_blank">Areas of interest from ScienceBase already selected.</a>';
			    $incomingCallerDiv.append(callerMsg);
			} else {
				var callerMsg = 'Areas of interest ' + incomingParams['item_id'] +  ' from ' + incomingParams['caller'] + ' already selected.';
				$incomingCallerDiv.append(callerMsg);
			}
		} else {
			$incomingCallerDiv.remove();
		}
	};

	this.init();

	return {
		isProcessingButtonSelected: this.isProcessingButtonSelected,
		bindProceedButton: this.bindProceedButton,
		updateCswDropdown: this.updateCswDropdown,
		cswDropdownUpdated: this.cswDropdownChanged,
		errorEncountered: this.errorEncountered,
		removeOverlay: this.removeOverlay
	};
};
