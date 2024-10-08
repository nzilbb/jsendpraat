//
// Copyright 2016-2024 New Zealand Institute of Language, Brain and Behaviour, 
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

var background = null
var messageHandler = null;

function registerBackgroundMessageHandler(handler) {
  messageHandler = handler;
}

function postMessageToBackground(message) {
  if (!background) {
    background = chrome.runtime.connect({name: "content"});
    console.log("backgound connnected");
    background.onDisconnect.addListener(()=>{ // probably never fired?
      background = null;
    });
    
    if (messageHandler) {
      console.log("adding message handler");
      background.onMessage.addListener(messageHandler);
    }
  }
  console.log("posting " + JSON.stringify(message));
  background.postMessage(message);
}

window.addEventListener('pageshow', (event) => {
  // If the page is restored from BFCache, ensure a new connection is set up.
  if (event.persisted) background = null;
});

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

// ask messaging host for its version
function messageHostVersion() {
  postMessageToBackground({
    "message" : "version"
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
      || msg.message == "sendpraat"
      || msg.message == "version") {
    msg.type = "FROM_PRAAT_EXTENSION";
    window.postMessage(msg);
  }
});

// set up handlers for incoming messages from the content (LaBB-CAT transcripts)
window.addEventListener("message", function(event) {    
  if (event.data.type && (event.data.type == "FROM_PRAAT_PAGE")) {
    // from Praat-supporting page
    switch (event.data.message) {
    case "PING": // transcript is pinging the extension, so acknowledge...
      window.postMessage({
        type: 'FROM_PRAAT_EXTENSION',
        message: 'ACK',
        version: chrome.runtime.getManifest().version
      }); 
      break;
    case "sendpraat":
      sendpraat(event.data.sendpraat, event.data.authorization);
      break;
    case "upload":
      upload(event.data.sendpraat, event.data.uploadUrl, event.data.fileParameter, event.data.fileUrl, event.data.otherParameters, event.data.authorization);
      break;
    case "version":
      messageHostVersion();
      break;
    } // switch on event.data.message
  } // FROM_PRAAT_PAGE
  return Promise.resolve("ok");
}, false);

// find all audio elements on the page
function findAudioUrls() {

  var urls = [];
  var audioTags = document.getElementsByTagName("audio");
  for (a = 0; a < audioTags.length; a++) {
    var audio = audioTags[a];
    if (audio.src) {
      if (audio.src.search(/\.wav$/i) >= 0)
      {
	if (!urls.includes(audio.src)) {
	  urls.push(audio.src);
	}
      }
      if (audio.src.search(/\.flac$/i) >= 0)
      {
	if (!urls.includes(audio.src)) {
	  urls.push(audio.src);
	}
      }
      if (audio.src.search(/\.mp3$/i) >= 0)
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
	  || source.src.search(/\.wav$/i) >= 0)
      {
	if (!urls.includes(source.src)) {
	  urls.push(source.src);
	}
      }
    } // next <source>	    
    for (s = 0; s < sources.length; s++) {
      var source = sources[s];
      if (source.type == "audio/flac"
	  || source.src.search(/\.flac$/i) >= 0)
      {
	if (!urls.includes(source.src)) {
	  urls.push(source.src);
	}
      }
    } // next <source>	    
    for (s = 0; s < sources.length; s++) {
      var source = sources[s];
      if (source.type == "audio/mpeg"
	  || source.src.search(/\.mp3$/i) >= 0)
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
    if (anchor.href) {
      var href = anchor.href.replace(/#.*$/,"");
      if (href.toLowerCase().endsWith(".wav")
          || href.toLowerCase().endsWith(".flac")
          || href.toLowerCase().endsWith(".mp3")) {
	if (!urls.includes(href)) {
	  urls.push(href);
	}
      } // file ends with audio extension
    } // there's an href
  } // next <a>

  return urls;
}

activateAudioTags(findAudioUrls());
