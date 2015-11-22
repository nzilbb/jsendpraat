//
// Copyright 2015 New Zealand Institute of Language, Brain and Behaviour, 
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
var hostVersion = null;
var hostVersionMin = "20151028.1857";
var praatPort = null;
var praatPortHasNeverInitialised = true;
var lastContent = null;
var lastPageUrl = null;
var tabMedia = {};

chrome.runtime.onConnect.addListener(
    function(content) 
    {
	content.onMessage.addListener(
	    function(msg) 
	    {
		if (msg.message == "activateAudioTags") {
		    console.log("activate " + msg.urls);
		    // register this media for this url
		    tabMedia[content.sender.url] = msg.urls;
		    updatePageAction(content.sender.tab.id);
		}
		else if (msg.message == "sendpraat") {
		    console.log("sendpraat " + msg.sendpraat);
		    lastContent = content;
		    checkPraatPort();
		    praatPort.postMessage(msg);
		}
		else if (msg.message == "upload") {
		    console.log("upload " + msg.fileUrl + " to " + msg.uploadUrl);
		    lastContent = content;
		    checkPraatPort();
		    praatPort.postMessage(msg);
		}
	    });
    });

chrome.tabs.onActivated.addListener(
    function(activeInfo) {
	updatePageAction(activeInfo.tabId);
    });

function updatePageAction(tabId) {
    chrome.tabs.get(tabId, 
		    function(tab) {
			lastPageUrl = tab.url;
			// if there's praatable media registered for this URL
			if (tabMedia[lastPageUrl])
			{ // show (and update) the page action
			    chrome.pageAction.show(tab.id);
			}
		    });
}

function checkPraatPort() {
    if (!praatPort) {
	praatPort = chrome.runtime.connectNative('nzilbb.chrome.jsendpraat');
	praatPort.onMessage.addListener(function(msg) {
	    if (msg.message == "version" || msg.code >= 900) {
		hostVersion = msg.version;
		console.log("nzilbb.chrome.sendpraat: Host version is " + hostVersion);
		if (!hostVersion || hostVersion < hostVersionMin) {
		    console.log("nzilbb.chrome.sendpraat: Need at least version " + hostVersionMin);
		    praatPort.disconnect();
		    praatPort = null;
		    if (praatPortHasNeverInitialised) {
			chrome.tabs.create({url:"upgrade.html"});
		    }
		}
	    } else {
		console.log("nzilbb.chrome.sendpraat: Received " + msg.code);
		if (lastContent) 
		{ // reply to the last message port
		    praatPortHasNeverInitialised = false;
		    try { lastContent.postMessage(msg); } catch(x) {}
		}
	    }
	});
	praatPort.onDisconnect.addListener(function() {
	    console.log("nzilbb.chrome.sendpraat: Disconnected");
	    praatPort = null;
	    if (praatPortHasNeverInitialised) {
		chrome.tabs.create({url:"install.html"});
	    }
	});
	// check the version of the host
	console.log("nzilbb.chrome.sendpraat: Checking host version...");
	praatPort.postMessage({ message: "version" });
    }
}
