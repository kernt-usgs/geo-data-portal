Ext.ns("GDP");

GDP.Plotter = Ext.extend(Ext.Panel, {
    sosStore : [],
	leafStore : undefined,
    plotterData : [],
    scenarioGcmJSON : {},
	origScenarioGcmJSON : {},
    yLabels : [],
    plotterYMin : 10000000,
    plotterYMax : 0,
    plotterDiv : undefined,
    legendDiv : undefined,
    height : undefined,
    legendWidth : undefined,
    controller : undefined,
    plotterTitle : undefined,
    titleTipText : undefined,
    graph : undefined,
    visibility : undefined,
    errorBarsOn : undefined,
    toolbar : undefined,
    errorDisplayed : undefined,
    constructor : function (config) {
        config = config || {};
        this.plotterDiv = config.plotterDiv || 'dygraph-content';
        this.legendDiv = config.legendDiv || 'dygraph-legend';
        this.legendWidth = config.legendWidth || 250;
        this.height = config.height || 200;
        this.plotterTitle = config.title || '';
        this.controller = config.controller;
        this.titleTipText = config.titleTipText;
        this.visibility = config.visibility || [];
        this.errorBarsOn = config.errorBars || true;
        this.errorDisplayed = false;
        this.toolbar = new Ext.Toolbar({
            itemId : 'plotterToolbar',
            ref : '../plotterToolbar',
            items : '&nbsp; '
        });

        var contentPanel = new Ext.Panel({
            contentEl : this.plotterDiv,
            itemId : 'contentPanel',
            ref : '../contentPanel',
            layout : 'fit',
            region : 'center',
            autoShow : true
        });

        var legendPanel = new Ext.Panel({
            itemId : 'legendPanel',
            ref : '../legendPanel',
            contentEl : this.legendDiv,
            layout : 'fit',
            region : 'east',
            autoShow : true
        });
        config = Ext.apply({
            items : [contentPanel, legendPanel],
            layout : 'border',
            autoShow : true,
            tbar : this.toolbar,
            bufferResize : true,
            split: true
        }, config);

        GDP.Plotter.superclass.constructor.call(this, config);
        this.controller.on('updateplotter', function (args) {
            LOG.debug('Plotter:updateplotter');
            this.updatePlotter(args);
        }, this);
        this.controller.on('loaded-catstore', function (args) {
            LOG.debug('Plotter:onLoadedCatstore');
            this.onLoadedCatstore(args);
        }, this);
        this.controller.on('loaded-leafstore', function (args) {
            LOG.debug('Plotter:onLoadedCatstore');
            this.onLoadedLeafstore(args);
        }, this);
        this.controller.on('exception-metadatastore', function () {
            this.collapse();
        }, this);
        this.on('resize', function () {
            LOG.debug('Plotter:resize');
            this.resizePlotter();
        }, this);
        this.on("afterrender", function () {
            LOG.debug('Plotter:afterrender');
            this.resizePlotter();
        }, this);
    },

    updatePlotter : function (args) {
        LOG.debug('Plotter:updatePlotter: Observed request to update plotter');

        var endpoint = args.url;
        var offering = args.offering;
        this.errorDisplayed = false;
        this.plotterTitle = args.featureTitle;
        this.yLabels = [];

        if (this.sosStore) {
            this.sosStore = [];
        }
		Ext.get(this.plotterDiv).dom.innerHTML = '';
        var height = Math.round(0.5 * parseInt(Ext.get(this.plotterDiv).dom.style.height));
        LOADMASK = new Ext.LoadMask(Ext.get(this.plotterDiv).dom, {
            msg: '<div id="cida-load-msg">Loading...</div><img height="' + height + '" src="images/cida-anim.gif" />',
            msgCls: 'cida-load-plotter'
        });
        LOADMASK.show();

        this.topToolbar.removeAll(true);

        // Add the title
		var scenarioButtons = [];
		var scenario;
		var index = 0;
		Ext.iterate(this.scenarioGcmJSON, function (scenario) {
			if (index === 0) {
				this.visibility[index] = true;
			}
			else {
				this.visibility[index] = false;
			}

			scenarioButtons.push(new Ext.Button({
				text : scenario,
				id : 'plotter-toolbar-btngrp-' + scenario,
				sequencePosition : index,
				pressed : this.visibility[index],
				enableToggle: true
			}));
			index++;
		}, this);
        this.topToolbar.add(
            new Ext.Toolbar.TextItem({
                id : 'title',
                html : this.plotterTitle.replace('deg', '&deg;') + this.titleTipText
            }),
            new Ext.Toolbar.Fill(),
            new Ext.ButtonGroup({
                columns : scenarioButtons.length,
                layout : 'table',
                ref : 'plotter-toolbar-buttongroup',
                items : scenarioButtons
            }),
            new Ext.Button({
                itemId : 'errorBarsButton',
                text : 'Min/Max',
                ref : 'plotter-toolbar-errorbars-button',
                pressed : this.errorBarsOn,
                enableToggle: true
            }),
            new Ext.Toolbar.Spacer(),
            new Ext.Button({
                itemId : 'plotter-toolbar-download-button',
                text : 'Download As CSV',
                ref : 'plotter-toolbar-download-button'
            })
		);

        for (var i = 0, itemCount = this.topToolbar['plotter-toolbar-buttongroup'].items.getCount();i < itemCount;i++) {
            var item = this.topToolbar['plotter-toolbar-buttongroup'].items.itemAt(i);
            item.on('click', function (obj) {
                this.graph.setVisibility(obj.sequencePosition, obj.pressed );
                this.visibility[obj.sequencePosition] = obj.pressed;
            }, this);
        }
        this.topToolbar["plotter-toolbar-errorbars-button"].on('click', function (obj) {
            this.graph.updateOptions({
                fillAlpha : obj.pressed ? 0.15 : 0.0

            });
            this.errorBarsOn = obj.pressed;
        }, this);
        this.topToolbar.doLayout();

        Ext.iterate(this.scenarioGcmJSON, function (scenario, object) {
            Ext.iterate(object, function (gcm) {
                if (gcm !== 'ensemble') {
                    this.scenarioGcmJSON[scenario][gcm] = new Array();
                    var meta = {};
                    var url = endpoint.replace("{shapefile}", this.controller.getCurrentFOI());
                    url = url.replace(/{gcm}/g, gcm);
                    url = url.replace(/{scenario}/g, scenario);
                    url = url.replace('{threshold}', Math.round(this.controller.getThreshold()));
                    meta.url = url ;
                    meta.scenario = scenario;
                    meta.gcm = gcm;
                    this.loadSOSStore(meta, offering);
                }
            }, this);
        }, this);
        this.resizePlotter();
    },

	/**
	 * Using the leaf store, test whether or not it contains a scenario and
	 * whether or not that scenario contains a given gcm
	 *
	 * @argument {Object} args "store" - {Object} The leaf store, "scenario" - {String} , "gcm" - {String}
	 */
	testScenarioGCMComboExists : function (args) {
		args = args || {};
		var store = args.store;
		var scenario = args.scenario;
		var gcm = args.gcm;

		var scenarioGCMComboExists = false;

		// This should always be true, unless we selectively don't have scenarios
		var scenarioIndex = store.find('scenarios', scenario);
		if (scenarioIndex > -1) {
			var gcmArray = this.leafStore.getAt(scenarioIndex).get('gcms').map(function (gcm) {
				return this.cleanUpIdentifiers(gcm[0].toLowerCase());
			}, this);
			scenarioGCMComboExists = gcmArray.indexOf(gcm.toLowerCase()) > -1;
		}

		return scenarioGCMComboExists;
	},

    resizePlotter : function () {
        LOG.debug('Plotter:resizePlotter()');
        var divPlotter = Ext.get(this.plotterDiv);
        var divLegend = Ext.get(this.legendDiv);

        divLegend.setWidth(this.legendWidth);
        divPlotter.setWidth(this.getWidth() - (this.legendWidth + 2));
        divPlotter.setHeight(this.getHeight() - this.toolbar.getHeight());
        if (this.graph) {
            this.graph.resize(divPlotter.getWidth(), divPlotter.getHeight());
        }
    },
	cloneScenarioGcmJSON : function () {
		return Ext.util.JSON.decode(Ext.util.JSON.encode(this.origScenarioGcmJSON));
	},
    onLoadedCatstore : function (args) {
        Ext.each(args.record.get("scenarios"), function (scenario) {
            var scenarioKey = this.cleanUpIdentifiers(scenario[0]);
            this.origScenarioGcmJSON[scenarioKey] = {};
            Ext.each(args.record.get("gcms"), function (gcm) {
                if (gcm[0] !== 'ensemble') {
                    var gcmKey = gcm[0];
                    this.origScenarioGcmJSON[scenarioKey][gcmKey] = [];
                }
            }, this);
        }, this);

        // Set up the text for the initial view of the plotter panel
        Ext.DomHelper.append(Ext.DomQuery.selectNode("div[id='dygraph-content']"), {
            tag : 'div',
           id : 'plotter-prefill-text',
            html : args.record.get('helptext')['plotWindowIntroText']
		});

        this.doLayout();

        // This tooltip will show up to the right of any title text
        this.titleTipText = '&nbsp;&nbsp;<span ext:qtip="'+args.record.get('helptext')['plotHelp']+'" class="x-combo-list-item"><img class="quicktip-img" src="images/info.gif" /></span>';
    },

	onLoadedLeafstore : function (args) {
		this.scenarioGcmJSON = this.cloneScenarioGcmJSON();
		this.leafStore = args.store;
		Ext.iterate(this.scenarioGcmJSON, function (scenario, gcms, scenarioGcmJSON) {
			for (var gcm in gcms) {
				var hasGCM = this.testScenarioGCMComboExists({
					store : this.leafStore,
					gcm : gcm,
					scenario : scenario
				});

				if (!hasGCM) {
					delete scenarioGcmJSON[scenario][gcm];
					if (Object.isEmpty(scenarioGcmJSON[scenario])) {
						delete scenarioGcmJSON[scenario];
					}
				}
			}
        }, this);
	},

    loadSOSStore : function (meta, offering) {
        var url = "proxy/" + meta.url + "service=SOS&request=GetObservation&version=1.0.0&offering=" + encodeURIComponent(encodeURIComponent(offering));

        this.sosStore.push(new GDP.SOSGetObservationStore({
            url : url, // gmlid is url for now, eventually, use SOS endpoint + gmlid or whatever param
            autoLoad : true,
            proxy : new Ext.data.HttpProxy({
                url: url,
                disableCaching: false,
                method: "GET"
            }),
            baseParams : {},
            listeners : {
                load : function (store) {
                    this.globalArrayUpdate(store, meta, offering);
                },
                exception : function () {
                    LOG.debug('Plotter: SOS store has encountered an exception.');
                    // I only want to display this message once per request,
                    if (!this.errorDisplayed) {
                        this.errorDisplayed = true;
                        this.controller.sosExceptionOccurred();
                    }
                },
                scope: this
            }

        }));
    },
    globalArrayUpdate : function (store, meta, offering) {
        LOG.debug('Plotter:globalArrayUpdate() for ' + meta.url);
        var record = store.getAt(0);
        if (!record) {
            if (!this.errorDisplayed) {
                this.errorDisplayed = true;
                this.controller.sosExceptionOccurred();
            }
            return;
        }
        this.scenarioGcmJSON[meta.scenario][meta.gcm] = function (values) {
            Ext.each(values, function (item, index, allItems) {
                for(var i=0; i<item.length; i++) {
                    var value;
                    if (i === 0) {
                        value = Date.parseISO8601(item[i].split('T')[0]);
                    }
                    else {
                        value = parseFloat(item[i]);
                    }
                    allItems[index][i] = value;
                }
            });
            return values;
        }(record.get('values'));

        var isComplete = true;

        Ext.iterate(this.scenarioGcmJSON, function (key, value, object) {
            Ext.iterate(value, function (key, value, object) {
                if (value.length === 0) {
                    isComplete = false;
                }
            }, this);
        }, this);
        if (isComplete) {
            // calculate this.plotterData;
            this.plotterData = [];
            var observationsLength;
            var scenarios = [];
            var gcms = [];
            Ext.iterate(this.scenarioGcmJSON, function (scenario, value) {
                scenarios.push(scenario);
                Ext.iterate(value, function (gcm, value) {
                    if(gcms.indexOf(gcm) === -1 && gcm !== 'ensemble') {
                        gcms.push(gcm);
                    }
                    if (!observationsLength) {
                        observationsLength = value.length;
                    }
                });
            });

            this.yLabels = scenarios;

            this.plotterYMin = 1000000;
            this.plotterYMax = 0;

            for (var i=0; i<observationsLength; i++) {
                this.plotterData.push(new Array());
                this.plotterData[i][0] = this.scenarioGcmJSON[scenarios[0]][gcms[0]][i][0];

                Ext.each(scenarios, function (scenario) {
                    var scenarioArray = [];
                    Ext.each(gcms, function (gcm) {
						if (this.scenarioGcmJSON[scenario][gcm]) {
							scenarioArray.push(this.scenarioGcmJSON[scenario][gcm][i][1]);
						}
                    }, this);
                    var min = Array.min(scenarioArray);
                    var mean = Array.mean(scenarioArray);
                    var max = Array.max(scenarioArray);
                    this.plotterData[i].push([min, mean, max]);
                    if (min < this.plotterYMin) {
                        this.plotterYMin = min;
                    }
                    if (max > this.plotterYMax) {
                        this.plotterYMax = max;
                    }
                }, this);
            }
            this.dygraphUpdateOptions(store);

            // Set up the download CSV button
            this.topToolbar["plotter-toolbar-download-button"].un('click');
            this.topToolbar["plotter-toolbar-download-button"].on('click', function (){
                var id = Ext.id();
                var frame = document.createElement('iframe');
                frame.id = id;
                frame.name = id;
                frame.className = 'x-hidden';
                if (Ext.isIE) {
                    frame.src = Ext.SSL_SECURE_URL;
                }
                document.body.appendChild(frame);

                if (Ext.isIE) {
                    document.frames[id].name = id;
                }

                var form = Ext.DomHelper.append(document.body, {
                    tag:'form',
                    method:'post',
                    action: 'export?filename='+ (this.controller.getCurrentFOI() + "_" + this.controller.getFeatureTitle()).replace(' ', '-') +'.csv',
                    target:id
                });
                Ext.DomHelper.append(form, {
                    tag:'input',
                    name : 'data',
                    value: function (scope) {
                        var observationsLength;
                        var scenarios = [];
                        var gcms = [];
                        Ext.iterate(scope.scenarioGcmJSON, function (scenario, value) {
                            scenarios.push(scenario);
                            Ext.iterate(value, function (gcm, value) {
                                if(gcms.indexOf(gcm) === -1 && gcm !== 'ensemble') {
                                    gcms.push(gcm);
                                }
                                if (!observationsLength) {
                                    observationsLength = value.length;
                                }
                            });
                        });

                        var csv = '#' + scope.plotterTitle + ' See: ' + window.location + ' for documentation of source data.\n';
                        var line = 'date, ';
                        Ext.each(scenarios, function (scenario) {
                            Ext.each(gcms, function (gcm) {
								if (scope.scenarioGcmJSON[scenario][gcm]) {
									line += gcm + " " + scenario + ",";
								}
                            }, this);
                        }, this);
                        csv += line.substr(0, line.length - 1) + "\n";

                        for (var i=0; i<observationsLength; i++) {
                            var line2 = '';
                            line2 += scope.scenarioGcmJSON[scenarios[0]][gcms[0]][i][0].getFullYear() + ",";

                            Ext.each(scenarios, function (scenario) {
                                Ext.each(gcms, function (gcm) {
									if (scope.scenarioGcmJSON[scenario][gcm]) {
										line2 += scope.scenarioGcmJSON[scenario][gcm][i][1] + ",";
									}
                                }, this);
                            }, this);
                            csv += line2.substr(0, line2.length - 1) + "\n";
                        }

                        return encodeURIComponent(csv);
                    }(this)
                });

                document.body.appendChild(form);
                var callback = function (e) {
                    var rstatus = (e && typeof e.type !== 'undefined'?e.type:this.dom.readyState );

                    switch(rstatus){
                        case 'loading':  //IE  has several readystate transitions
                        case 'interactive': //IE

                            break;

                        case 'load': //Gecko, Opera
                        case 'complete': //IE
                            if(Ext.isIE){
                                this.dom.src = "javascript:false"; //cleanup
                            }
                            break;
                        default:
                    }
                };

                Ext.EventManager.on(frame, Ext.isIE?'readystatechange':'load', callback);
                form.submit();
            }, this);

        }
    },
    dygraphUpdateOptions : function (store) {
        var record = store.getAt(0);

        // this is mean for us, probably figure this out better?
        var yaxisUnits = record.get('dataRecord')[1].uom;

        // TODO figure out what to do if dataRecord has more than time and mean
        this.graph = new Dygraph(
            Ext.get(this.plotterDiv).dom,
            this.plotterData,
            { // http://dygraphs.com/options.html
                hideOverlayOnMouseOut : false,
                legend: 'always',
                customBars: true,
                errorBars: true,
                fillAlpha: this.errorBarsOn ? 0.15 : 0.0,
                labels: ["Date"].concat(this.yLabels),
                labelsDiv: Ext.get(this.legendDiv).dom,
                labelsDivWidth: this.legendWidth,
                labelsSeparateLines : true,
                labelsDivStyles: {
                    'textAlign': 'right'
                },
                rightGap : 5,
                showRangeSelector: true,
                //ylabel: record.data.dataRecord[1].name,
                yAxisLabelWidth: 75,
                ylabel: this.controller.getDerivative().get('derivative') + " " + this.controller.formatValueForDisplay(this.controller.getThreshold(), this.controller.getUnits(), true),
                valueRange: [this.plotterYMin - (this.plotterYMin / 10) , this.plotterYMax + (this.plotterYMax / 10)],
                visibility : this.visibility,
                axes: {
                    x: {
                        valueFormatter: function (ms) {
                            return '<span style="font-weight: bold; text-size: big">' +
                            new Date(ms).strftime('%Y') +
                            '</span>';
                        },
                        axisLabelFormatter: function (d) {
                            return d.strftime('%Y');
                        }
                    },
                    y: {
                        valueFormatter: function (y) {
                            return Math.round(y) + " " + yaxisUnits + "<br />";
                        }
                    }
                }
            }
            );
    },
    // These are some business rules for how our scenario or gcms appear in urls
    cleanUpIdentifiers : function (str) {
        str = str.toLowerCase();
        str = str.replace(' ', '_');
        str = str.replace('.', '-');
        return str;
    }
});

