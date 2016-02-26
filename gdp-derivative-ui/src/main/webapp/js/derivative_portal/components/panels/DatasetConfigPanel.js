Ext.ns("GDP");

/**
 * This panel is a holder for all of the controls related to the dataset
 */
GDP.DatasetConfigPanel = Ext.extend(Ext.Panel, {
    controller : undefined,
    capabilitiesStore : undefined,
    derivRecordStore : undefined,
    derivRecordStoreLoaded : false,
    derivativeStore : undefined,
    derivativeCombo : undefined,
    featureOfInterestCombo : undefined,
    gcmStore : undefined,
    gcmCombo : undefined,
    layerCombo : undefined,
    leafRecordStore : undefined,
	leafRecordStoreLoaded : undefined,
    parentRecordStore : undefined,
    scenarioStore : undefined,
    scenarioCombo : undefined,
    timestepName : undefined,
    timestepStore : undefined,
    timestepCombo : undefined,
    timestepComboConfig : undefined,
    zlayerCombo : undefined,
    zlayerName : undefined,
    zlayerStore : undefined,
    zlayerComboConfig : undefined,
    constructor : function (config) {
        LOG.debug('DatasetConfigPanel:constructor: Constructing self.');

        this.controller = config.controller;
        this.capabilitiesStore = config.capabilitiesStore;
        this.parentRecordStore = config.getRecordsStore;

        this.derivativeStore = new Ext.data.ArrayStore({
            storeId : 'derivativeStore',
            fields: ['derivative', 'quicktip']
        });
        this.derivativeCombo = new Ext.form.ComboBox({
            xtype : 'combo',
            mode : 'local',
            triggerAction : 'all',
            store : this.derivativeStore,
            fieldLabel : '<tpl for="."><span ext:qtip="Some information about derivative" class="x-combo-list-item"><img class="quicktip-img" src="images/info.gif" /></span></tpl> Derivative',
            forceSelection : true,
            lazyInit : false,
            editable : false,
            displayField : 'derivative',
            emptyText : 'Choose Derivative',
            tpl : '<tpl for="."><div ext:qtip="<b>{derivative}</b><br /><br />{quicktip}" class="x-combo-list-item">{derivative}</div></tpl>'
        });

        this.scenarioStore = new Ext.data.ArrayStore({
            storeId : 'scenarioStore',
            fields: ['scenario', 'quicktip']
        });
        this.scenarioCombo = new Ext.form.ComboBox({
            xtype : 'combo',
            mode : 'local',
            triggerAction : 'all',
            store : this.scenarioStore,
            fieldLabel : '<tpl for="."><span ext:qtip="Some information about emission scenario" class="x-combo-list-item"><img class="quicktip-img" src="images/info.gif" /></span></tpl> Scenario',
            forceSelection : true,
            lazyInit : false,
            editable : false,
            displayField : 'scenario',
            emptyText : 'Choose Emission Scenario',
            tpl : '<tpl for="."><div ext:qtip="<b>{scenario}</b><br /><br />{quicktip}" class="x-combo-list-item">{scenario}</div></tpl>'
        });

        this.timestepName = 'time';
        this.timestepStore = new Ext.data.ArrayStore({
            storeId : 'timestepStore',
            fields: [this.timestepName, 'timestepDisplayName']
        });
        this.timestepComboConfig = {
            mode : 'local',
            triggerAction : 'all',
            store : this.timestepStore,
            forceSelection : true,
            lazyInit : false,
            valueField : this.timestepName,
            displayField : 'timestepDisplayName',
            editable : false,
            autoWidth : true
        };
        this.timestepCombo = new Ext.form.ComboBox({
            hidden : true
        }, this.timestepComboConfig);

        this.gcmStore = new Ext.data.ArrayStore({
            storeId : 'gcmStore',
            fields: ['gcm', 'quicktip']
        });
        this.gcmCombo = new Ext.form.ComboBox({
            xtype : 'combo',
            mode : 'local',
            triggerAction : 'all',
            store : this.gcmStore,
            fieldLabel : '<tpl for="."><span ext:qtip="Some information about GCM" class="x-combo-list-item"><img class="quicktip-img" src="images/info.gif" /></span></tpl> GCM',
            forceSelection : true,
            lazyInit : false,
            editable : false,
            displayField : 'gcm',
            emptyText : 'Choose GCM',
            tpl : '<tpl for="."><div ext:qtip="<b>{gcm}</b><br /><br />{quicktip}" class="x-combo-list-item">{gcm}</div></tpl>'
        });

        this.zlayerName = 'elevation';
        this.zlayerStore = new Ext.data.ArrayStore({
            storeId : 'zlayerStore',
            idIndex: 0,
            fields: [this.zlayerName, 'zlayerDisplayName']
        });
        this.zlayerComboConfig = {
            mode : 'local',
            triggerAction: 'all',
            store : this.zlayerStore,
            forceSelection : true,
            lazyInit : false,
			valueField : this.zlayerName,
            displayField : 'zlayerDisplayName',
            emptyText : 'Loading...',
            autoWidth : true
        };

        this.zlayerCombo = new Ext.form.ComboBox(Ext.apply({
            editable : false,
            hidden : true
        }, this.zlayerComboConfig));

        var foiGetCapsStore = new GeoExt.data.WMSCapabilitiesStore({
            url: config.foiGetCapsURL,
            autoLoad: true,
            listeners: {
                load: function (data) {
                    Ext.each(data.data.items, function (item, index, allItems) {
                        item.data.layer.url = GDP.PROXY_PREFIX + item.data.layer.url;
                        item.id = 'featureLayer';
                        item.data.layer.setOpacity(0.3);
                    }, this);
                },
                exception: function (proxy, type, action, options, response, arg) {
                    LOG.error(response.responseText);
                    NOTIFY.error({
                        msg : response.responseText
                    });
                }
            }
        });
        this.featureOfInterestCombo = new Ext.form.ComboBox({
            xtype : 'combo',
            mode : 'local',
            triggerAction : 'all',
            store : foiGetCapsStore,
            fieldLabel : '<tpl for="."><span ext:qtip="Choose an area of interest" class="x-combo-list-item"><img class="quicktip-img" src="images/info.gif" /></span></tpl> Area Of Interest',
            forceSelection : true,
            lazyInit : false,
            editable : false,
            displayField : 'title',
            emptyText : 'Choose Area Of Interest',
            tpl : '<tpl for="."><div ext:qtip="{title}" class="x-combo-list-item">{title}</div></tpl>'
        });

        Ext.iterate([this.derivativeCombo, this.scenarioCombo, this.timestepCombo, this.gcmCombo, this.zlayerCombo, this.featureOfInterestCombo], function (item) {
            item.on('added', function (me, parent) {
                me.setWidth(parent.ownerCt.width - 15);
                me.setValue('');
            });
		}, this);

		config = Ext.apply({
			id: 'dataset-configuration-panel',
			title: 'Dataset Configuration',
			width: config.width || undefined,
			animate: true,
			border: false,
			items: [{
				xtype: 'fieldset',
				labelAlign: 'top',
				autoHeight: true,
				ref: 'derivativeFieldSet',
				defaultType: 'combo',
				layout: 'form',
				items: [
					this.derivativeCombo,
					this.zlayerCombo
				]
			}, {
				xtype: 'fieldset',
				labelAlign: 'top',
				title: 'Map',
				autoHeight: true,
				ref: 'mapFieldSet',
				defaultType: 'combo',
				layout: 'form',
				items: [
					this.scenarioCombo,
					this.gcmCombo,
					this.timestepCombo
				]
			}, {
				xtype: 'fieldset',
				id: 'plotFieldSet',
				labelAlign: 'top',
				ref: 'plotFieldSet',
				title: 'Plot',
				autoHeight: true,
				defaultType: 'combo',
				layout: 'form',
				items: [
					this.featureOfInterestCombo
				]
			}
				]
		}, config);

        GDP.DatasetConfigPanel.superclass.constructor.call(this, config);
        LOG.debug('DatasetConfigPanel:constructor: Construction complete.');

		 // Attach event handlers
        this.capabilitiesStore.on('load', function (capStore) {
            LOG.debug('DatasetConfigPanel: Capabilities store has finished loading.');
            this.capStoreOnLoad(capStore);
        }, this);
        this.capabilitiesStore.on('exception', function () {
            LOG.debug('DatasetConfigPanel: Capabilities store has encountered an exception.');
            this.controller.capabilitiesExceptionOccurred();
        }, this);
        this.parentRecordStore.on('load', function (catStore) {
            LOG.debug('DatasetConfigPanel: Catalog store has finished loading.');
            this.catStoreOnLoad(catStore);
        }, this);
        this.parentRecordStore.on('exception', function () {
            LOG.debug('DatasetConfigPanel: Meta data store has encountered an exception.');
            this.controller.getRecordsExceptionOccurred();
        }, this);
        this.derivativeCombo.on('select', function (combo, record) {
            this.controller.requestDerivative(record);
        }, this);
        this.featureOfInterestCombo.on('select', function (combo, record) {
            this.controller.requestFeatureOfInterest(record);
            var foiName = record.get('name');
            this.controller.currentFOI = foiName.substr(foiName.indexOf(":") + 1);
        }, this);
        this.scenarioCombo.on('select', function (combo, record) {
            this.controller.requestScenario(record);
        }, this);
        this.gcmCombo.on('select', function (combo, record) {
            this.controller.requestGcm(record);
        }, this);
        this.controller.on('selected-dataset', function (args) {
            LOG.debug('DatasetConfigPanel observed "selected-dataset"');
            this.onSelectedDataset(args);
        }, this);
        this.controller.on('selected-deriv', function (args) {
            LOG.debug('DatasetConfigPanel observed "selected-deriv"');
            this.onSelectedDerivative(args);
        }, this);
        this.controller.on('loaded-capstore', function (args) {
            LOG.debug('DatasetConfigPanel observed "loaded-capstore"');
            this.onLoadedCapstore(args);
        }, this);
        this.controller.on('loaded-catstore', function (args) {
            LOG.debug('DatasetConfigPanel observed "loaded-catstore"');
            this.onLoadedCatstore(args);
        }, this);
        this.controller.on('loaded-derivstore', function (args) {
            LOG.debug('DatasetConfigPanel observed "loaded-derivstore"');
            this.onLoadedDerivStore(args);
        }, this);
        this.controller.on('loaded-leafstore', function (args) {
            LOG.debug('DatasetConfigPanel observed "loaded-leafstore"');
            this.onLoadedLeafStore(args);
        }, this);
        this.controller.on('changelayer', function () {
            LOG.debug('DatasetConfigPanel: Observed "changelayer".');
            this.onChangeLayer();
        }, this);
        this.controller.on('changederiv', function () {
            LOG.debug('DatasetConfigPanel: Observed "changederiv".');
            this.onChangeDerivative();
        }, this);
        this.controller.on('changescenario', function (record) {
            LOG.debug('DatasetConfigPanel: Observed "changescenario".');
            this.onChangeScenario(record);
        }, this);
        this.controller.on('changegcm', function () {
            LOG.debug('DatasetConfigPanel: Observed "changegcm".');
            this.onChangeGcm();
        }, this);
        this.controller.on('changedimension', function (extentName) {
            LOG.debug('DatasetConfigPanel: Observed \'changedimension\'.');
            this.onChangeDimension(extentName);
        }, this);
        this.controller.on('exception-metadatastore', function () {
            this.collapse();
        }, this);

		// Event handlers connected. Load the parent store
		this.parentRecordStore.load();
    },
    capStoreOnLoad : function (capStore) {
        LOG.debug("DatasetConfigPanel: capStoreOnLoad()");
        var index = capStore.findBy(this.capsFindBy, this, 0);
        if (index > -1) {
            this.controller.loadedCapabilitiesStore({
                record : capStore.getAt(index)
            });
        }
    },
    catStoreOnLoad : function (catStore) {
        LOG.debug("DatasetConfigPanel: catStoreOnLoad()");
        this.controller.loadedGetRecordsStore({
            record : catStore.getAt(0)
        });
    },
    derivStoreOnLoad : function (derivStore) {
        LOG.debug("DatasetConfigPanel: derivStoreOnLoad()");

		// Create an array of available scenarios for this derivative
		var scenarioArray = derivStore.getAt(0).get('scenarios').map(function (scenario) {
			return scenario[0];
		});

        this.controller.loadedDerivStore({
            record : derivStore.getAt(0)
        });
    },
    leafStoreOnLoad : function (store) {
        LOG.debug("DatasetConfigPanel: leafStoreOnLoad()");
        this.controller.loadedLeafStore({
            store : store
        });
    },
    onSelectedDataset : function (args) {
        LOG.debug("DatasetConfigPanel: onSelectedDataset()");

		args = args || {};

		var record = args.record,
			url = args.url,
			recordIndex;

		// A record was not passed in with the args - find the leaf record to
		// populate the combobox by querying on the scenario combobox to get the
		// right set of records for the GCM combo
		if (!record) {
			recordIndex = this.leafRecordStore.data.findIndexBy(function (record) {
				return this.scenarioCombo.getValue() === record.data.scenarios[0][0];
			}, this);
			record = this.leafRecordStore.getAt(recordIndex);
		}

		if (!url) {
			url = GDP.PROXY_PREFIX + record.get("wms");
		}


        this.gcmStore.removeAll();

		// Need to add in the quicktip text from parentRecordStore to the leaf record.
		var parentGcms = this.parentRecordStore.data.get(0).data.gcms;
		var leafGcms = record.get('gcms');
		var findQuicktip = function(leafGcm) {
			Ext.each(parentGcms, function(parentGcm) {
				if (parentGcm[0] === leafGcm[0]) {
					leafGcm.push(parentGcm[1]);
					return;
				};
			});
		};
		Ext.each(leafGcms, function(gcm) {
			findQuicktip(gcm);
		});

        this.gcmStore.loadData(record.get("gcms"), true);

		this.controller.setOPeNDAPEndpoint(record.get("opendap"));

        if (this.controller.getShowChange()) {
            this.capabilitiesStore.proxy.setApi(Ext.data.Api.actions.read, url.replace('der_periods','der_diff'));
            this.capabilitiesStore.load();
		} else if (this.controller.getShowHistoricalPeriod()) {
			var scenario = this.controller.getScenario().get('scenario').toLowerCase();
			this.controller.scenario.set('scenario', scenario);
			this.capabilitiesStore.proxy.setApi(Ext.data.Api.actions.read,
				url.replace('der_periods_' + scenario, 'hist_der_periods'));
            this.capabilitiesStore.load();
        } else {
            this.capabilitiesStore.proxy.setApi(Ext.data.Api.actions.read, url);
            this.capabilitiesStore.load();
        }
    },
    onLoadedCapstore : function () {
        LOG.debug("DatasetConfigPanel: onLoadedCapStore()");
        this.controller.fireEvent('changegcm');
        this.controller.fireEvent('changelayer');
    },
    onLoadedCatstore : function (args) {
        LOG.debug("DatasetConfigPanel: onLoadedCatStore()");
        this.derivativeStore.removeAll();
        this.derivativeStore.loadData(args.record.get("derivatives"), true);
        this.scenarioStore.removeAll();
        this.scenarioStore.loadData(args.record.get("scenarios"), true);
        this.gcmStore.removeAll();
        this.gcmStore.loadData(args.record.get("gcms"), true);

        this.timestepCombo.label.update('<tpl for="."><span ext:qtip="' + args.record.get("fieldLabels").timeperiod + '" class="x-combo-list-item"><img class="quicktip-img" src="images/info.gif" /></span></tpl> Time Period For Map');
        this.derivativeCombo.label.update('<tpl for="."><span ext:qtip="' + args.record.get("fieldLabels").derivative + '" class="x-combo-list-item"><img class="quicktip-img" src="images/info.gif" /></span></tpl> Derivative');
        this.scenarioCombo.label.update('<tpl for="."><span ext:qtip="' + args.record.get("fieldLabels").scenario + '" class="x-combo-list-item"><img class="quicktip-img" src="images/info.gif" /></span></tpl> Emissions Scenario');
        this.gcmCombo.label.update('<tpl for="."><span ext:qtip="' + args.record.get("fieldLabels").gcm + '" class="x-combo-list-item"><img class="quicktip-img" src="images/info.gif" /></span></tpl> Climate Model');

        // http://internal.cida.usgs.gov/jira/browse/GDP-416
        this.derivativeCombo.setValue(args.record.get("derivatives")[0][0]);
        this.scenarioCombo.setValue(args.record.get("scenarios")[0][0]);
        this.gcmCombo.setValue(args.record.get("gcms")[0][0]);
        this.derivativeCombo.fireEvent('select', this, this.derivativeCombo.getStore().data.items[0]);
        this.scenarioCombo.fireEvent('select', this, this.scenarioCombo.getStore().data.items[0]);
        this.gcmCombo.fireEvent('select', this, this.gcmCombo.getStore().data.items[0]);
    },
    onLoadedDerivStore : function (args) {
        LOG.debug("DatasetConfigPanel: onLoadedDerivStore()");
        this.derivRecordStoreLoaded = true;
        this.controller.sosEndpoint = args.record.get("sos");
        this.loadLeafRecordStore({
			scenarios : args.record.get('scenarios')
		});
    },
    onLoadedLeafStore : function (args) {
        LOG.debug("DatasetConfigPanel: onLoadedLeafStore()");
		this.leafRecordStoreLoaded = true;
        this.controller.fireEvent('selected-dataset', args);
    },
    onChangeLayer : function () {
        LOG.debug("DatasetConfigPanel: onChangeLayer()");

        var layer = this.controller.getLayer();

        if (this.zlayerCombo) {
            this.derivativeFieldSet.remove(this.zlayerCombo);
        }

        if (this.timestepCombo) {
            this.mapFieldSet.remove(this.timestepCombo);
        }

        var loaded = this.controller.loadDimensionStore(layer, this.zlayerStore, this.zlayerName)
			&& this.controller.loadDimensionStore(layer, this.timestepStore, this.timestepName);

        if (loaded) {
            this.controller.time = this.timestepStore.data.items[0].data.time;

            var threshold = this.controller.getDimension(this.zlayerName),
				time = this.controller.getDimension(this.timestepName);

            if (time) {
                LOG.debug('DatasetConfigPanel: Time found for layer. Re-adding time step combobox.');
                this.timestepCombo = new Ext.form.ComboBox(Ext.apply({
                    fieldLabel : '<tpl for="."><span ext:qtip="' + this.getRecordsStore.data.items[0].data.fieldLabels.timeperiod + '" class="x-combo-list-item"><img class="quicktip-img" src="images/info.gif" /></span></tpl> Time Period For Map',
                    listWidth : this.width
                }, this.timestepComboConfig));
                this.timestepCombo.on('added', function (me, parent) {
                    me.setWidth(parent.ownerCt.width - 15);
                });
                this.mapFieldSet.add(this.timestepCombo);

                LOG.debug('DatasetConfigPanel: Setting timestep combobox to time: ' + time);
                this.timestepCombo.setValue(time);

                this.timestepCombo.on('select', function (combo, record) {
                    LOG.debug('DatasetConfigPanel:timeStepCombo: observed "select"');
                    this.controller.requestDimension(this.timestepName, record.get(this.timestepName));
                }, this);
            }

            if (threshold) {
                LOG.debug('DatasetConfigPanel: Threshold found for layer. Re-adding zlayer combobox.');
                this.zlayerCombo = new Ext.form.ComboBox(Ext.apply({
                    fieldLabel : '<tpl for="."><span ext:qtip="Which threshold to display the derivative data for" class="x-combo-list-item"><img class="quicktip-img" src="images/info.gif" /></span></tpl> ' + this.controller.getZAxisName(),
                    editable : false,
                    listWidth : this.width
                }, this.zlayerComboConfig));
                this.zlayerCombo.on('added', function (me, parent) {
                    me.setWidth(parent.ownerCt.width - 15);
                });
                this.derivativeFieldSet.add(this.zlayerCombo);

                LOG.debug('DatasetConfigPanel: Setting z-layer combobox to threshold: ' + threshold);
                this.zlayerCombo.setValue(threshold);
                this.controller.threshold = threshold;

                this.zlayerCombo.on('select', function (combo, record) {
                    LOG.debug('DatasetConfigPanel:zlayerCombo: observed "select"');
                    this.controller.requestDimension(this.zlayerName, record.get(this.zlayerName));
                }, this);
            }
            this.doLayout();
            if (this.controller.getFeatureAttribute()) {
                this.controller.updatePlotter();
            }
        }
    },
    onChangeDerivative : function () {
        LOG.debug("DatasetConfigPanel: onChangeDerivative()");
        this.loadDerivRecordStore();
    },
    onChangeScenario : function (args) {
        LOG.debug("DatasetConfigPanel: onChangeScenario()");

		args = args || {};

		// Derivative record store needs to be loaded first
        if (this.derivRecordStoreLoaded) {
			if (this.leafRecordStoreLoaded) {
				// A user has really selected a scenario. Use the leafRecordStore
				// to populate downstream events instead of calling out to CSW
				// again
				var scenario = args.record ? args.record.data.scenario : this.controller.getScenario(),
					leafRecord = this.leafRecordStore.getAt(this.leafRecordStore.find('scenarios', scenario));

				this.controller.fireEvent('selected-dataset', {
					record : leafRecord
				});
			} else {
				// We're in the app init phase
				this.loadLeafRecordStore();
			}
        }
    },
    onChangeGcm : function () {
        LOG.debug("DatasetConfigPanel: onChangeGcm()");
        if (this.controller.getGcm()) {
            var index = this.capabilitiesStore.findBy(this.capsFindBy, this, 0);
            LOG.debug('DatasetConfigPanel: onChangeGcm got index ', index);
            this.controller.requestLayer(this.capabilitiesStore.getAt(index));
        }
    },
    onChangeDimension : function (extentName) {
        LOG.debug("DatasetConfigPanel: onChangeDimension()");
        var threshold = this.controller.getDimension(this.zlayerName);
        this.controller.threshold = threshold;
        if (threshold && this.zlayerCombo) {
            this.zlayerCombo.setValue(threshold);
        }
        if (extentName === this.zlayerName && this.controller.getFeatureAttribute()) {
            this.controller.updatePlotter();
        }
    },
    capsFindBy : function (record, id) {
        LOG.debug("DatasetConfigPanel: capsFindBy()");
        var gcm = this.controller.getGcm();
		var layerName = record.get('layer').name;
        if (gcm) {
			if (this.controller.getShowHistoricalPeriod()) {
				return layerName.indexOf(gcm.get('gcm')) > -1
				return (gcm.get('gcm').indexOf(layerName)) > -1;
			}
			else {
				return (gcm.get("gcm") === layerName);
			}
        }
        return false;
    },

    loadDerivRecordStore : function () {
        LOG.debug("DatasetConfigPanel: loadDerivRecordStore()");
        // TODO fail nicely if this fails
        var derivative = this.controller.getDerivative();
        this.derivRecordStore = new GDP.MetadataRecordsStore({
            url : 'json/' + derivative.get('derivative').toLowerCase().replace(/ /g, '_') + '.json',
            storeId : 'metadataStore',
            opts : {},
            listeners : {
                load : function (derivStore) {
                    LOG.debug('DatasetConfigPanel: Meta data store has finished loading.');
                    this.derivStoreOnLoad(derivStore);
                },
                exception : function () {
                    LOG.debug('DatasetConfigPanel: Meta data store has encountered an exception.');
                    this.controller.getRecordsExceptionOccurred();
                },
                scope : this
            }
        });
        this.derivRecordStore.load();
    },

	/**
	 * Loads one or more metadata records based on identifier and an array of scenarios
	 * @argument {Object} args
	 *	parentIdentifier - fileIdentifier for derivative
	 *	scenarios - Array of scenario names
	 *	callbacks - {
	 *		success : [
	 *			function(GDP.MetadataRecordsStore) {}
	 *		],
	 *		error : [
	 *			function() {}
	 *		]
	 *	}
	 */
	loadLeafRecordStore: function (args) {
		LOG.debug("DatasetConfigPanel: loadLeafRecordStore()");
		args = args || {};
		var parentIdentifier = args.parentIdentifier || this.derivRecordStore.getAt(0).get("identifier"),
			scenarios = args.scenarios || [this.controller.getScenario().get('scenario')],

			callbacks = args.callbacks || {
				success: [
					function (store) {
						this.leafStoreOnLoad(store);
					}
				],
				error: [
					function () {
						this.controller.getRecordsExceptionOccurred();
					}
				]
			};

		if (scenarios && scenarios.length) {
			this.leafRecordStore = new GDP.MetadataRecordsStore({
				url: "json/" + parentIdentifier + '_scenarios.json',
				storeId: 'metadataStore',
				opts: {},
				listeners: {
					load: function (leafStore) {
						LOG.debug('DatasetConfigPanel: Metadata store has finished loading.');
						for (var sCallback = 0;sCallback < callbacks.success.length; sCallback++) {
							callbacks.success[sCallback].call(this, leafStore);
						}
					},
					exception: function() {
						LOG.debug('DatasetConfigPanel: Metadata store has encountered an exception.');
						for (var sCallback = 0; sCallback < callbacks.success.length; sCallback++) {
							callbacks.error[sCallback].call(this);
						}
					},
					scope: this
				}
			});
			this.leafRecordStore.load();
		}
	}
});
