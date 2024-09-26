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

// BROWSER-SPECIFIC CODE:

var background = chrome.runtime.connect({name: "popup"});
background.onMessage.addListener(messageHandler);

document.addEventListener('DOMContentLoaded', function () {
  chrome.storage.local.get("lastPageUrl").then((url) => {
    console.log(`lastPageUrl: ${url.lastPageUrl}`);
    chrome.storage.local.get([url.lastPageUrl]).then((media) => {
      if (media) {
        console.log("urlsJson: "+JSON.stringify(media[url.lastPageUrl]));
        listMedia(JSON.parse(media[url.lastPageUrl]));
      } else { // no media
        listMedia([]); // ensure the list is empty
      }
    });
  });
  return false;
});

function sendpraat(script, authorization) {
  background.postMessage({
      "message" : "sendpraat", 
      "sendpraat" : script,
      "authorization" : authorization // HTTP Authorization header
    });
}

