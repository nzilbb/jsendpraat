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

// content script for adding Praat related elements to the page

window.requestFileSystem  = window.requestFileSystem || window.webkitRequestFileSystem;

var background = chrome.runtime.connect({name: "content"});
background.onMessage.addListener(
    function(msg) {
	console.log("message " + msg.message);
	if (msg.message == "progress") {
	    console.log("Progress " + msg.string + " " + Math.floor(msg.value * 100 / msg.maximum) + "%");
	}
	msg.type = "FROM_PRAAT_EXTENSION";
	window.postMessage(msg, '*');
    });

window.addEventListener("message", function(event) {
    // We only accept messages from ourselves
    if (event.source != window) return;
    
    if (event.data.type && (event.data.type == "FROM_PRAAT_PAGE")) {
	// from Praat-supporting page
	switch (event.data.message) {
	case "PING": // transcript is pinging the extension, so acknowledge...
	    window.postMessage({ type: 'FROM_PRAAT_EXTENSION', message: 'ACK' }, '*');
	    break;
	case "sendpraat":
	    sendpraat(event.data.sendpraat);
	    break;
	case "upload":
	    upload(event.data.sendpraat, event.data.uploadUrl, event.data.fileParameter, event.data.fileUrl, event.data.otherParameters);
	    break;
	} // switch on event.data.message
    } // FROM_PRAAT_PAGE
}, false);

function activateAudioTags(urls) {
    background.postMessage({message: "activateAudioTags", urls: urls}, '*');
}
function openInPraat(url) {
    var command = ["praat", "Read from file... " + url, "Edit"];
    sendpraat(command);
}

function sendpraat(script) {
    background.postMessage({
	    "message" : "sendpraat", 
	    "sendpraat" : script
	});
}

function upload(sendpraat, uploadUrl, fileParameter, fileUrl, otherParameters) {
    background.postMessage({
	    "message" : "upload", 
	    "sendpraat" : sendpraat, // script to run first
	    "uploadUrl" : uploadUrl, // URL to upload to
	    "fileParameter" : fileParameter, // name of file HTTP parameter
	    "fileUrl" : fileUrl, // original URL for the file to upload
	    "otherParameters" : otherParameters // extra HTTP request parameters
	});
}

// find all audio elements on the page
var urls = [];
var audioTags = document.getElementsByTagName("audio");
for (a = 0; a < audioTags.length; a++) {
    var audio = audioTags[a];
    var sources = audio.getElementsByTagName("source");
    for (s = 0; s < sources.length; s++) {
	var source = sources[s];
	if (source.type == "audio/wav"
	    || source.src.search(/\.wav$/) >= 0)
	{
	    urls.push(source.src);
	}
    } // next <source>	    
    for (s = 0; s < sources.length; s++) {
	var source = sources[s];
	if (source.type == "audio/flac"
	    || source.src.search(/\.flac$/) >= 0)
	{
	    urls.push(source.src);
	}
    } // next <source>	    
    for (s = 0; s < sources.length; s++) {
	var source = sources[s];
	if (source.type == "audio/mpeg"
	    || source.src.search(/\.mp3$/) >= 0)
	{
	    urls.push(source.src);
	}
    } // next <source>	    
} // next <audio>
if (urls.length > 0) {
    activateAudioTags(urls);
}

