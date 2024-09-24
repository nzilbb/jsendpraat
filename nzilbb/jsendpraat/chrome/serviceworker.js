//
// Copyright 2015-2024 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of jsendpraat.
//
//    jsendpraat is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 2 of the License, or
//    (at your option) any later version.
//
//    jsendpraat is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with jsendpraat; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//

console.log("service_worker.js");

var debug = true;

// host version checking
var hostVersion = null;
var hostVersionMin = "20180606.1040";

// host communication
var praatPort = null;
var praatPortHasNeverInitialised = true;

// track media on each tab, so we can keep the page action popup up-to-date when they change tabs
// TODO move to chrome.storage.local
var tabMedia = {}; // key = page URL 

// track the content.js/popup.js connections, so we can pass back updates from
// TODO move to chrome.storage.local
var callbackPort = {}; // key = callbackId, generally the tab ID (or "popup") 

chrome.runtime.onConnect.addListener(
  function(port) {
    // make sure they can be called back by sendpraat
    if (port.sender.tab) {
      port.callbackId = String(port.sender.tab.id);
    } else {
      port.callbackId = "popup";
    }
    callbackPort[port.callbackId] = port;

    // react to messages
    port.onMessage.addListener(
      function(msg) 
      {
        console.log("msg: " + JSON.stringify(msg));
	if (msg.message == "activateAudioTags") {
	  if (debug) console.log("activate " + msg.urls);
	  if (msg.urls.length > 0) {
	    // register this media for this url
	    tabMedia[port.sender.url] = msg.urls;
	    updatePageAction(port.sender.tab.id);
	  }
	} else if (msg.message == "sendpraat") {
	  if (debug) console.log("sendpraat " + msg.sendpraat);
	  checkPraatPort();
	  msg.clientRef = port.callbackId;
	  praatPort.postMessage(msg);
	} else if (msg.message == "upload") {
	  if (debug) console.log("upload " + msg.fileUrl + " to " + msg.uploadUrl);
	  checkPraatPort();
	  msg.clientRef = port.callbackId;
	  praatPort.postMessage(msg);
	}
      });
  });

chrome.tabs.onActivated.addListener(
  function(activeInfo) {
    updatePageAction(activeInfo.tabId);
  });

function updatePageAction(tabId) {
  pageActionTabId = tabId;
  chrome.tabs.get(tabId, 
		  function(tab) {
		    // if there's praatable media registered for this URL
		    if (tabMedia[tab.url])
		    { // show (and update) the page action
		      //chrome.action.enable(tab.id);
                      // TODO post message with URLs
		    }
		  });
}

function checkPraatPort() {
  if (!praatPort) {
    praatPort = chrome.runtime.connectNative('nzilbb.jsendpraat.chrome');
    console.log(`chrome.runtime.connectNative: ${praatPort}`);
    praatPort.onMessage.addListener(function(msg) {
      console.log("praatPort: " + JSON.stringify(msg));
      if (msg.message == "version" || msg.code >= 900) {
	hostVersion = msg.version;
	console.log("nzilbb.jsendpraat.chrome: Host version is " + hostVersion);
	if (!hostVersion || hostVersion < hostVersionMin) {
	  console.log("nzilbb.jsendpraat.chrome: Need at least version " + hostVersionMin);
	  praatPort.disconnect();
	  praatPort = null;
	  if (praatPortHasNeverInitialised) {
	    chrome.tabs.create({url:"upgrade.html"});
	  }
	}
      } else {
	if (debug) console.log("nzilbb.jsendpraat.chrome: Received " + msg.code + " for " + msg.clientRef);
	if (msg.clientRef) 
	{ // reply to the last message port
	  praatPortHasNeverInitialised = false;
	  try { callbackPort[msg.clientRef].postMessage(msg); } catch(x) {}
	}
      }
    });
    praatPort.onDisconnect.addListener(function() {
      console.log("nzilbb.jsendpraat.chrome: Disconnected");
      praatPort = null;
      if (praatPortHasNeverInitialised) {
	chrome.tabs.create({url:"install.html"});
      }
    });
    // check the version of the host
    console.log("nzilbb.jsendpraat.chrome: Checking host version...");
    praatPort.postMessage({ message: "version" });
  }
}
