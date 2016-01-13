//
// Copyright 2016 New Zealand Institute of Language, Brain and Behaviour, 
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

const { emit } = require('sdk/event/core');
var child_process = require("sdk/system/child_process");
var buttons = require("sdk/ui/button/action");
var tabs = require("sdk/tabs");
var panel = require("sdk/panel");
var pageMod = require("sdk/page-mod");

var debug = true;

// host version checking
var hostVersion = null;
var hostVersionMin = "20160113.1254";

var tabMedia = {}; // key = tab.id

// results of looking for audio tags and URLs on a page
function foundAudioUrls(worker, urls) {
    if (debug) console.log("foundAudioUrls: " + worker.tab.id + " - " + urls.length);
    tabMedia[worker.tab.id] = {
	worker: worker,
	port: worker.port,
	urls: urls
    };
    setButtonForTab(tabs.activeTab.id);
}

// enable/disable the button and update its badge, given a tab
function setButtonForTab(tabId) {
    if (!tabMedia[tabId]) return;
    var urls = tabMedia[tabId].urls;
    if (urls.length == 0) { // no audio
	praatButton.disabled = true; 
	praatButton.badge = null;
	praatButton.icon = {
	    "16": "./disabled-praat-16.png",
	    "32": "./disabled-praat-32.png",
	    "64": "./disabled-praat-64.png"
	};
    } else { // some audio
	praatButton.disabled = false;
	praatButton.badge = urls.length;
	praatButton.icon = {
	    "16": "./praat-16.png",
	    "32": "./praat-32.png",
	    "64": "./praat-64.png"
	};
    }
}

// send a message to the host process to forward to Praat
function sendpraat(tabId, script) {
    if (debug) console.log("tab " + tabId + " script: " + script);
    if (!tabMedia[tabId]) return;

    checkHost();
    var message = {
	"message" : "sendpraat",
	"sendpraat" : script,
	"clientRef" : tabId
    };
    sendToHost(message);
}

// send a message to the host process to upload a TextGrid
function upload(tabId, sendpraat, uploadUrl, fileParameter, fileUrl, otherParameters) {
    if (debug) console.log("tab " + tabId + " upload: " + uploadUrl);
    checkHost();
    var message =  {
        "message" : "upload", 
        "sendpraat" : sendpraat, 
        "uploadUrl" : uploadUrl,
        "fileParameter" : fileParameter,
        "fileUrl" : fileUrl,
        "otherParameters" : otherParameters,
        "clientRef" : tabId
    };
    sendToHost(message);
}

// handle messages from tabs or the popup 
function handleMessage(worker, msg) {

    var tabId = worker?worker.tab.id:"popup";
    switch (msg.message) {
    case "activateAudioTags": 
	if (debug) console.log("activate " + msg.urls);
	foundAudioUrls(worker, msg.urls);
	break;
    case "sendpraat":
	if (debug) console.log("sendpraat " + msg.sendpraat);
	sendpraat(tabId, msg.sendpraat);
	break;
    case "upload":
	if (debug) console.log("upload " + msg.fileUrl + " to " + msg.uploadUrl);
	upload(tabId, msg.sendpraat, msg.uploadUrl, msg.fileParameter, msg.fileUrl, msg.otherParameters);
	break;
    }
}

// update button for the currently active tab
// TODO this doesn't seem to work properly when there are multiple windows
tabs.on('activate', function () {
  setButtonForTab(tabs.activeTab.id);
});

// add script to all pages that allows communication with the plugin (and thus praat) from pages
// (i.e. LaBB-CAT transcript pages)
pageMod.PageMod({
    include: "*",
    contentScriptFile: "./content.js",
    onAttach: function(worker) {

	// set up handlers for incoming messages from content.js
	worker.port.on("message", function(msg) {
	    handleMessage(worker, msg);
	});
    }
});

// panel for listing URLs on
var popup = panel.Panel({
    contentURL: "./popup.html",
    contentScriptFile: "./popup.js"
});
popup.port.on("message", function(msg) {
    handleMessage(null, msg);
});
popup.on("show", function() {
  popup.port.emit("message", { 
      message: "list", 
      urls: tabMedia[tabs.activeTab.id].urls});
});
tabMedia["popup"] = {port: popup.port};

// button for opening audio in Praat
var praatButton = buttons.ActionButton({
    id: "praat-link",
    label: "Open audio in Praat",
    icon: {
	"16": "./disabled-praat-16.png",
	"32": "./disabled-praat-32.png",
	"64": "./disabled-praat-64.png"
    },
    onClick: function(state) {
	// if there are actually URLs to show
	if (tabMedia[tabs.activeTab.id].urls.length > 0) {
	    // show the popup
	    popup.show({
		position: praatButton, 
		width: 500, 
		height: (tabMedia[tabs.activeTab.id].urls.length + 2) * 25});
	}
    }
});

var hostProcess = null;

// ensure the host process is running
function checkHost() {
    if (!hostProcess) {
	var system = require("sdk/system");
	var windows = system.platform.indexOf("win") == 0;
	var homeDir = windows ? system.env.USERPROFILE : system.env.HOME;
	var sep = windows ? "\\" : "/";

	var cmd = homeDir + sep + "jsendpraat" + sep + "jsendpraat" + (windows?".bat":".sh")
	// suppress message size headers on responses, because on some systems (looking at you OS X)
	// subprocess.js refuses to convert non UTF-8 characters into strings
	    + " --suppress-message-size"; 

	if (debug) console.log("cmd: " + cmd);
	var args = [ system.name ]; // e.g. "firefox"
	var env = {
	    PATH: system.env.PATH,
	    USER: system.env.USER,
	    HOME: system.env.HOME,
	    DISPLAY: system.env.DISPLAY
	};
	hostProcess = child_process.exec(cmd, {
	    env : windows?system.env:env
	});
	
	hostProcess.stdout.on('data', receivedFromHost);
	
	hostProcess.stderr.on('data', function (data) {
	    console.log(cmd + ": " + data);
	});
    
	hostProcess.on('close', function (code) {
	    console.log(cmd + " exited with code " + code);
	    hostProcess = null;
	});
    
	hostProcess.on('error', function (err) {
	    if (err.message.indexOf("NS_ERROR_FILE_TARGET_DOES_NOT_EXIST") >= 0
		|| err.message.indexOf("NS_ERROR_FILE_NOT_FOUND") >= 0) {
		// not installed yet	
		console.log("Messaging host is not yet installed: " + cmd);
		tabs.open("./install.html");
	    } else { // some other error
		console.log("Failed to start " + cmd + ": " + err);
		error("Failed to start " + cmd, err.message);
	    }
	    hostProcess = null;
	});

	// check the version of the host
	sendToHost({ message: "version"});

    }
}

// send a message to the host process:
// this uses the Chromium Native Messaging host protocol:
// https://developer.chrome.com/extensions/nativeMessaging#native-messaging-host-protocol
// i.e. "each message is serialized using JSON, UTF-8 encoded and is preceded with 32-bit
// message length in native byte order."
function sendToHost(message) {
    var text = JSON.stringify(message);
    if (debug) console.log("sendToHost: " + text);
    
    // send message size
    var messageSize = text.length;
    var messageSizeBytes = intTo4ByteString(messageSize);
    if (debug) console.log("size: " + messageSize + " = " + messageSizeBytes);
    emit(hostProcess.stdin, "data", messageSizeBytes);

    // send message
    emit(hostProcess.stdin, "data", text);
}

// a message was received from the host process:
// this uses the Chromium Native Messaging host protocol:
// https://developer.chrome.com/extensions/nativeMessaging#native-messaging-host-protocol
// i.e. "each message is serialized using JSON, UTF-8 encoded and is preceded with 32-bit
// message length in native byte order."
function receivedFromHost(data) {
    if (data.length <= 4) return; // the size of the message, which we just ignore...
    
    if (debug) console.log("Host process: " + data);
    var message = JSON.parse(data);
    
    if (message.message == "version" || message.code >= 900) {
	hostVersion = message.version;
	console.log("nzilbb.jsendpraat.chrome: Host version is " + hostVersion);
	if (!hostVersion || hostVersion < hostVersionMin) {
	    console.log("nzilbb.jsendpraat.chrome: Need at least version " + hostVersionMin);
	    emit(hostProcess.stdin, "end");
	    hostProcess.kill();
	    tabs.open("./upgrade.html");
	}
    } else {
	// find out who it's for
	var tabId = message.clientRef;
	if (debug) console.log("Forwarding message to : " + tabId + " - " + tabMedia[tabId].port);
	
	// forward the message to them
	tabMedia[tabId].port.emit("message", message);
    }
}

// ensure host process is killed when we exit
require("sdk/system/unload").when(function(reason) {
    if (hostProcess) {
	if (debug) console.log("Killing host process");
	emit(hostProcess.stdin, "end");
	hostProcess.kill();
    }
});

// display an error message
function error(title, message) {
    var errorMessage = panel.Panel({
	contentURL: "./error.html",
	contentScriptFile: "./error.js",
	contentScriptWhen: "end",
	contentScript: [
	    "document.getElementById('errorTitle').innerHTML = '"+title.replace(/\\/g, "\\\\").replace(/'/g, "\\'")+"';",
	    "document.getElementById('errorMessage').innerHTML = '"+message.replace(/\\/g, "\\\\").replace(/'/g, "\\'")+"';"
	]
    });
    errorMessage.port.on("OK", function() { errorMessage.destroy(); });
    errorMessage.show({
	width: 800,
	height: 200
    });
}

// converts the message size to a string representation of a 4-byte integer
function intTo4ByteString (num) {
    var arr = new ArrayBuffer(4); // an Int32 takes 4 bytes
    var view = new DataView(arr);
    view.setUint32(0, num, true); // little-endian TODO should test plaform for this
    var a = new Uint8Array(arr);
    return String.fromCodePoint(a[0], a[1], a[2], a[3]);
}
