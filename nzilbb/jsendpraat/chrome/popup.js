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

var background = chrome.runtime.connect({name: "popup"});
background.onMessage.addListener(
    function(msg) 
    {
	if (msg.message == "progress")
	{
	    console.log("Progress " + msg.string + " " + Math.floor(msg.value * 100 / msg.maximum) + "%");
	}
    });

document.addEventListener('DOMContentLoaded', function () {
    var background = chrome.extension.getBackgroundPage();
    var urls = background.tabMedia[background.lastPageUrl];
    for (u in urls)
    {
	console.log("u " + u + " " + urls[u]);
        var li = document.createElement("a");
        li.className = "praaturl";
	li.href = "#";
        li.url = urls[u];
        li.title = "Open in Praat";
        li.onclick = function() { openInPraat(this.url); };
	li.appendChild(document.createTextNode(urls[u]));
        praatMediaList.appendChild(li);
    } // next url
});
function openInPraat(url)
{
    var command = ["praat", "Read from file... " + url, "Edit"];
    sendpraat(command);
}

function sendpraat(script)
{
    background.postMessage(
	{
	    "message" : "sendpraat", 
	    "sendpraat" : script
	});
}
