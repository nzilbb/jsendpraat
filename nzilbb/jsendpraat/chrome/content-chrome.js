//
// Copyright 2015-2016 New Zealand Institute of Language, Brain and Behaviour, 
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

var background = null
var messageHandler = null;

function registerBackgroundMessageHandler(handler) {
  messageHandler = handler;
}

function postMessageToBackground(message) {
  if (!background) {
    background = chrome.runtime.connect({name: "content"});
    background.onDisconnect.addListener(()=>{ // probably never fired?
      background = null;
    });
    
    if (messageHandler) {
      background.onMessage.addListener(messageHandler);
    }
  }
  background.postMessage(message);
}

window.addEventListener('pageshow', (event) => {
  // If the page is restored from BFCache, ensure a new connection is set up.
  if (event.persisted) background = null;
});

