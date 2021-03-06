/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
define([
        "dojo/_base/declare",
        "dojo/_base/event",
        "dojo/_base/connect",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/parser",
        "dojo/json",
        "dojo/store/Memory",
        "dojo/data/ObjectStore",
        "dojox/html/entities",
        "dijit/registry",
        "qpid/common/TimeZoneSelector",
        "dojo/text!../../showPreferences.html",
        "qpid/common/util",
        "dijit/Dialog",
        "dijit/form/NumberSpinner",
        "dijit/form/CheckBox",
        "dijit/form/Textarea",
        "dijit/form/FilteringSelect",
        "dijit/form/TextBox",
        "dijit/form/DropDownButton",
        "dijit/form/Button",
        "dijit/form/Form",
        "dijit/layout/BorderContainer",
        "dijit/layout/TabContainer",
        "dijit/layout/ContentPane",
        "dojox/grid/EnhancedGrid",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
function (declare, event, connect, dom, domConstruct, parser, json, Memory, ObjectStore, entities, registry, TimeZoneSelector, markup, util) {

  var preferenceNames = ["timeZone", "updatePeriod"];

  return declare("qpid.management.Preferences", null, {

    preferencesDialog: null,

    constructor: function(management)
    {
      var that = this;
      this.management = management;
      this.userPreferences = {};
      this.domNode = domConstruct.create("div", {innerHTML: markup});
      parser.parse(this.domNode).then(function(instances)
      {
        that._postParse();
      });
    },
    _postParse: function()
    {
      var that = this;
      this.preferencesDialog = registry.byId("preferences.preferencesDialog");
      for(var i=0; i<preferenceNames.length; i++)
      {
        var name = preferenceNames[i];
        this[name] = registry.byId("preferences." + name);
        this[name].on("change", function(val){that._toggleSetButtons();});
      }

      this.timeZoneSelector = registry.byId("preferences.timeZone");
      this.timeZoneSelector.set("timezones", this.management.timezone.getAllTimeZones());
      this.setButton = registry.byId("preferences.setButton");
      this.setAndCloseButton = registry.byId("preferences.setAndCloseButton");
      this.setButton.on("click", function(e){that._savePreferences(e, false);});
      this.setAndCloseButton.on("click", function(e){that._savePreferences(e, true);});
      this.theForm = registry.byId("preferences.preferencesForm");
      this.usersGrid = registry.byId("preferences.users");
      this.usersGrid.set("structure", [ { name: "User", field: "name", width: "50%"},
                                 { name: "Authentication Provider", field: "authenticationProvider", width: "50%"}]);
      this.deleteButton = registry.byId("preferences.deleteButton");
      this.deleteAndCloseButton = registry.byId("preferences.deleteAndCloseButton");
      this.deleteButton.on("click", function(e){that._deletePreferences(false);});
      this.deleteAndCloseButton.on("click", function(e){that._deletePreferences(true);});

      var deletePreferencesButtonToggler = function(rowIndex){
        var data = that.usersGrid.selection.getSelected();
        that.deleteButton.set("disabled",!data.length );
        that.deleteAndCloseButton.set("disabled",!data.length );
      };
      connect.connect(this.usersGrid.selection, 'onSelected',  deletePreferencesButtonToggler);
      connect.connect(this.usersGrid.selection, 'onDeselected',  deletePreferencesButtonToggler);
      this.theForm.on("submit", function(e){event.stop(e); return false;});

      this._setValues();

      deletePreferencesButtonToggler();
      this.preferencesDialog.startup();
    },

    showDialog: function(){
      this._setValues();
      var that = this;
      this._loadUserPreferences(function(){that.preferencesDialog.show();});
    },

    destroy: function()
    {
      if (this.preferencesDialog)
      {
        this.preferencesDialog.destroyRecursevly();
        this.preferencesDialog = null;
      }
    },

    _savePreferences: function(e, hideDialog)
    {
      var that =this;
      event.stop(e);
      if(this.theForm.validate()){
        var preferences = {};
        for(var i=0; i<preferenceNames.length; i++)
        {
          var name = preferenceNames[i];
          var preferenceWidget = this[name];
          if (preferenceWidget)
          {
            preferences[name] = preferenceWidget.hasOwnProperty("checked") ? preferenceWidget.checked : preferenceWidget.get("value");
          }
        }

        this.management.userPreferences.save(
            preferences,
            function(preferences)
            {
              success = true;
              if (hideDialog)
              {
                that.preferencesDialog.hide();
              }
              else
              {
                var reloadUsers = true;
                if (that.users)
                {
                  var authenticatedUser = dom.byId("authenticatedUser").innerHTML;
                  for(var i=0; i<that.users.length; i++)
                  {
                    if (that.users[i].name == authenticatedUser)
                    {
                      reloadUsers = false;
                      break;
                    }
                  }
                }
                if (reloadUsers)
                {
                  that._loadUserPreferences();
                }
              }
              that._toggleSetButtons();
            });
      }
    },

    _deletePreferences: function(hideDialog){
    var that = this;
    var postDelete = function(data)
       {
        that._loadUserPreferences();
        var authenticatedUser = dom.byId("authenticatedUser").innerHTML;
        for(i = 0; i<data.length; i++)
        {
          if (data[i].name == authenticatedUser)
          {
            that.management.userPreferences.resetPreferences();
            that._setValues();
            break;
          }
        }
        if (hideDialog)
        {
          that.preferencesDialog.hide();
        }
       };

      util.deleteSelectedRows(this.usersGrid,
                              "Are you sure you want to delete preferences for user",
                              this.management,
                              "service/userpreferences",
                              null,
                              "user",
                              postDelete );
    },

    _setValues: function()
    {
      for(var i = 0; i < preferenceNames.length; i++)
      {
        var name = preferenceNames[i];
        var preferenceWidget = this[name];
        if (preferenceWidget)
        {
          var value = this.management.userPreferences[name]
          if (typeof value == "string")
          {
            value = entities.encode(String(value))
          }
          if (!value && name == "updatePeriod")
          {
            // set to default
            value = 5;
          }
          preferenceWidget.set("value", value);
          if (preferenceWidget.hasOwnProperty("checked"))
          {
            preferenceWidget.set("checked", this.management.userPreferences[name] ? true : false);
          }
        }
      }
      this._toggleSetButtons();
    },

    _loadUserPreferences : function(callback)
    {
      var that = this;
      this.management.get({url: "service/userpreferences"}).then(
                          function(users)
                          {
                                 for(var i=0; i<users.length; i++)
                                 {
                                     users[i].id = encodeURIComponent(users[i].authenticationProvider)
                                         + "/"
                                         + encodeURIComponent(users[i].name);
                                 }
                                 that.users = users;
                                 var usersStore = new Memory({data: users, idProperty: "id"});
                                 var usersDataStore = new ObjectStore({objectStore: usersStore});
                                 if (that.usersGrid.store)
                                 {
                                   that.usersGrid.store.close();
                                 }
                                 that.usersGrid.set("store", usersDataStore);
                                 if (callback)
                                 {
                                    callback();
                                 }
                                 that.usersGrid._refresh();
                          });
    },

    _toggleSetButtons: function()
    {
      var changed = false;
      for(var i=0; i<preferenceNames.length; i++)
      {
        var name = preferenceNames[i];
        var preferenceWidget = this[name];
        if (preferenceWidget)
        {
          var value = preferenceWidget.hasOwnProperty("checked") ? preferenceWidget.checked : preferenceWidget.get("value");
          if (value != this.management.userPreferences[name])
          {
            changed = true;
            break;
          }
        }
      }
      this.setButton.set("disabled", !changed);
      this.setAndCloseButton.set("disabled", !changed);
    }

  });
});
