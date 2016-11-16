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

// CROSS-BROWSER CODE:

function messageHandler(msg) {
    var progressMessage = document.getElementById("progressMessage");
    progressMessage.style.display = "";
    if (msg.error) {
	removeAllChildren(progressMessage);
	progressMessage.appendChild(document.createTextNode(msg.error));
	progressMessage.classList.add("error");
	progressMessage.title = msg.error;
    } else if (msg.string) {
	removeAllChildren(progressMessage);
	progressMessage.appendChild(document.createTextNode(msg.string));
	progressMessage.classList.remove("error");
	progressMessage.title = msg.string;
    }

    switch (msg.message) {
    case "progress":
	var progress = document.getElementById("progress");
	progress.style.display = "";
	progress.title = msg.string;
	try { progress.value = Math.floor(msg.value * 100 / msg.maximum); } catch(x) {}
	break;
    case "list": 
	var urls = msg.urls;
	listMedia(urls);
	break;
    }
}

function listMedia(urls) {
    var praatMediaList = document.getElementById("praatMediaList");
    removeAllChildren(praatMediaList);
    document.getElementById("progressMessage").style.display = "none";
    document.getElementById("progress").style.display = "none";
    
    // add new urls
    for (var u in urls)
    {
	if (!document.getElementById(urls[u])) {
            var div = document.createElement("div");
	    div.className = "media"
	    
            var save = document.createElement("a");
            save.className = "save";
            save.download = urls[u].replace(/.*\//, "");
	    save.href = urls[u];
            save.title = "Save";
            save.target = "download";
            var img = document.createElement("img");
	    img.src = "document-save.png";
	    save.appendChild(img);
            div.appendChild(save);
	    
            var praat = document.createElement("a");
            praat.className = "praaturl";
	    praat.href = "#";
            praat.url = urls[u];
            praat.id = urls[u];
            praat.title = "Open in Praat";
            praat.onclick = function() { openInPraat(this.url); };
	    praat.appendChild(document.createTextNode(urls[u]));
            div.appendChild(praat);
	    
            praatMediaList.appendChild(div);
	}
	
    } // next url
}

function openInPraat(url) {
    var command = ["praat", "Read from file... " + url, "Edit"];
    sendpraat(command);
}

function removeAllChildren(element) {
    while (element.firstChild) element.removeChild(element.firstChild);
}
