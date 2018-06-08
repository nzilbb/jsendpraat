//
// Copyright 2016-2018 New Zealand Institute of Language, Brain and Behaviour, 
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

// CROSS-BROWSER CODE:

var debug = false;

// send a script to praat
function sendpraat(script, authorization) {
    postMessageToBackground({
	"message" : "sendpraat", 
	"sendpraat" : script,
	"authorization" : authorization // HTTP Authorization header
    });
}

// upload a praat-edited TextGrid file to the server
function upload(sendpraat, uploadUrl, fileParameter, fileUrl, otherParameters, authorization) {
    postMessageToBackground({
	"message" : "upload", 
	"sendpraat" : sendpraat, // script to run first
	"uploadUrl" : uploadUrl, // URL to upload to
	"fileParameter" : fileParameter, // name of file HTTP parameter
	"fileUrl" : fileUrl, // original URL for the file to upload
	"otherParameters" : otherParameters, // extra HTTP request parameters
	"authorization" : authorization // HTTP Authorization header
    });
}

function activateAudioTags(urls) {
    postMessageToBackground({
	"message" : "activateAudioTags", 
	"urls" : urls});
}

// set up handlers for incoming messages from index.js
registerBackgroundMessageHandler(function(msg) {
    if (msg.message == "progress"
       || msg.message == "upload"
       || msg.message == "sendpraat") {
	msg.type = "FROM_PRAAT_EXTENSION";
	window.postMessage(msg, '*');
    }
});

// set up handlers for incoming messages from the content (LaBB-CAT transcripts)
window.addEventListener("message", function(event) {    
    if (event.data.type && (event.data.type == "FROM_PRAAT_PAGE")) {
	// from Praat-supporting page
	switch (event.data.message) {
	case "PING": // transcript is pinging the extension, so acknowledge...
	    window.postMessage({ type: 'FROM_PRAAT_EXTENSION', message: 'ACK', version: 0.98 }, '*');
	    break;
	case "sendpraat":
	    sendpraat(event.data.sendpraat, event.data.authorization);
	    break;
	case "upload":
	    upload(event.data.sendpraat, event.data.uploadUrl, event.data.fileParameter, event.data.fileUrl, event.data.otherParameters, event.data.authorization);
	    break;
	} // switch on event.data.message
    } // FROM_PRAAT_PAGE
}, false);

// find all audio elements on the page
function findAudioUrls() { // TODO add this to a library shared between extensions

    var urls = [];
    var audioTags = document.getElementsByTagName("audio");
    for (a = 0; a < audioTags.length; a++) {
	var audio = audioTags[a];
	if (audio.src) {
	    if (audio.src.search(/\.wav$/) >= 0)
	    {
		if (!urls.includes(audio.src)) {
		    urls.push(audio.src);
		}
	    }
	    if (audio.src.search(/\.flac$/) >= 0)
	    {
		if (!urls.includes(audio.src)) {
		    urls.push(audio.src);
		}
	    }
	    if (audio.src.search(/\.mp3$/) >= 0)
	    {
		if (!urls.includes(audio.src)) {
		    urls.push(audio.src);
		}
	    }
	}
	var sources = audio.getElementsByTagName("source");
	for (s = 0; s < sources.length; s++) {
	    var source = sources[s];
	    if (source.type == "audio/wav"
		|| source.src.search(/\.wav$/) >= 0)
	    {
		if (!urls.includes(source.src)) {
		    urls.push(source.src);
		}
	    }
	} // next <source>	    
	for (s = 0; s < sources.length; s++) {
	    var source = sources[s];
	    if (source.type == "audio/flac"
		|| source.src.search(/\.flac$/) >= 0)
	    {
		if (!urls.includes(source.src)) {
		    urls.push(source.src);
		}
	    }
	} // next <source>	    
	for (s = 0; s < sources.length; s++) {
	    var source = sources[s];
	    if (source.type == "audio/mpeg"
		|| source.src.search(/\.mp3$/) >= 0)
	    {
		if (!urls.includes(source.src)) {
		    urls.push(source.src);
		}
	    }
	} // next <source>	    
    } // next <audio>

    var anchorTags = document.getElementsByTagName("a");
    for (a = 0; a < anchorTags.length; a++) {
	var anchor = anchorTags[a];
	if (anchor.href.endsWith(".wav") || anchor.href.endsWith(".mp3")) {
	    if (!urls.includes(anchor.href)) {
		urls.push(anchor.href);
	    }
	}
    } // next <a>

    return urls;
}

activateAudioTags(findAudioUrls());
