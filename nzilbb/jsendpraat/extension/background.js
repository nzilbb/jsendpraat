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

var debug = true;

// host version checking
var hostVersion = null;
var hostVersionMin = "20180606.1040";

// host communication
var praatPort = null;
var praatPortHasNeverInitialised = true;

// track the content.js/popup.js connections, so we can pass back updates from
// TODO move to chrome.storage.local?
var callbackPort = {}; // key = callbackId, generally the tab ID (or "popup") 

chrome.runtime.onConnect.addListener(
  function(port) {
    // make sure they can be called back by sendpraat
    if (port.sender.tab) {
      port.callbackId = String(port.sender.tab.id);
    } else {
      port.callbackId = "popup";
    }
    if (debug) console.log("Tab port connected: " + port.callbackId);
    callbackPort[port.callbackId] = port;

    // react to messages
    port.onMessage.addListener(
      function(msg) {
	if (msg.message == "activateAudioTags") {
	  if (debug) console.log("activate " + msg.urls);
	  if (msg.urls.length > 0) {
	    // register this media for this url
            var item = {};
            item[port.sender.url] = JSON.stringify(msg.urls);
            chrome.storage.local.set(item);
            // set badge as the number of URLs
            chrome.action.setBadgeText({
              tabId: port.sender.tab.id,
              text: ""+msg.urls.length });
	  } else { // clear badge
            chrome.action.setBadgeText({
              tabId: port.sender.tab.id,
              text: null });
          }
	  updatePageAction(port.sender.tab.id);
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
	} else if (msg.message == "version") {
	  if (debug) console.log("version");
	  checkPraatPort();
	  msg.clientRef = port.callbackId;
	  praatPort.postMessage(msg);
	}
        return Promise.resolve(msg);
      });
    port.onDisconnect.addListener(
      function(port) {
        if (debug) console.log("Tab port disconnected: " + port.callbackId);
        callbackPort[port.callbackId] = port;
        return Promise.resolve("ok");
      });
    return Promise.resolve("ok");
  });

chrome.tabs.onActivated.addListener(
  function(activeInfo) {
    updatePageAction(activeInfo.tabId);
    return Promise.resolve("ok");
  });

function updatePageAction(tabId) {
  pageActionTabId = tabId;
  chrome.tabs.get(
    tabId, (tab) => {
      chrome.storage.local.set({"lastPageUrl":tab.url});
      // if there's praatable media registered for this URL
      chrome.storage.local.get(tab.url).then((urlsJson) => {
        if (urlsJson[tab.url]) { // show (and update) the page action
	  chrome.action.enable(tab.id);
        } else {
	  chrome.action.disable(tab.id);
        }
      });
    });
}

function checkPraatPort() {
  if (!praatPort) {
    praatPort = chrome.runtime.connectNative('nzilbb.jsendpraat.chrome');
    if (debug) console.log(`chrome.runtime.connectNative: ${praatPort}`);
    praatPort.onMessage.addListener(function(msg) {
      if (debug) console.log("praatPort: " + JSON.stringify(msg));
      if (msg.message == "version" || msg.code >= 900) {
	hostVersion = msg.version;
	console.log("nzilbb.jsendpraat.chrome: Host version is " + hostVersion);
        if (hostVersion) {
          chrome.action.setTitle({
            title: "v"+chrome.runtime.getManifest().version + " ("+hostVersion+")" });
        }
	try { callbackPort[msg.clientRef].postMessage(msg); } catch(x) {}
        
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
      return Promise.resolve("ok");
    });
    praatPort.onDisconnect.addListener(function() {
      if (debug) console.log("nzilbb.jsendpraat.chrome: Disconnected");
      praatPort = null;
      if (praatPortHasNeverInitialised) {
	chrome.tabs.create({url:"install.html"});
      }
      return Promise.resolve("disconnected");
    });
    // check the version of the host
    if (debug) console.log("nzilbb.jsendpraat.chrome: Checking host version...");
    praatPort.postMessage({ message: "version" });
  }
}

chrome.action.setTitle({ title: "v"+chrome.runtime.getManifest().version });
