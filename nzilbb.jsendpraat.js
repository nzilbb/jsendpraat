/**
 * @file nzilbb.jsendpraat module for establishing communication with the jsendpraat browser 
 * extension - https://github.com/nzilbb/jsendpraat
 *
 * @example
 * window.onload = function() {
 *  nzilbb.jsendpraat.detectExtension(
 *
 *      function () { // onExtensionDetected
 *        console.log("jsendpraat extension is installed.");
 *      }, // onExtensionDetected 
 *
 *      function (code, error) { // onSendPraatResponse
 *        console.log("sendpraat returned: " + code + " " + error);
 *      }, // onSendPraatResponse
 *
 *      function (string, value, maximum, error, code) { // onProgress
 *        document.getElementById("progress").maximum = maximum;
 *        document.getElementById("progress").value = value;
 *      }, // onProgress
 *
 *      function (code, summary, error) { // onUploadResponse
 *        console.log("upload returned: " + code + " " + summary + " " + error);
 *      } // onUploadResponse
 *   );
 * };
 * ...
 * var wavUrl = "http://myserver/myfile.wav";
 * nzilbb.jsendpraat.sendpraat([ 
 *    "Read from file... " + wavUrl,
 *    "Edit"
 *  ]);
 *
 * @author Robert Fromont robert.fromont@canterbury.ac.nz
 * @license magnet:?xt=urn:btih:1f739d935676111cfff4b4693e3816e664797050&dn=gpl-3.0.txt GPL v3.0
 * @copyright 2016-2018 New Zealand Institute of Language, Brain and Behaviour, University of Canterbury
 *
 *    This file is part of jsendpraat.
 *
 *    jsendpraat is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    jsendpraat is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with jsendpraat; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * @lic-end
 */

"use strict";

// namespace
var nzilbb = nzilbb || {};
nzilbb.jsendpraat = nzilbb.jsendpraat || {};
nzilbb.jsendpraat.chrome = nzilbb.jsendpraat.chrome || {};

nzilbb.jsendpraat.debug = true;

nzilbb.jsendpraat.log = function(message) {
  if (nzilbb.jsendpraat.debug) console.log(message);
}

/**
 * Whether jsendpraat installed. 
 *  - false if the browser is incompatible
 *  - true if detectExtension() has been called and the extension is installed
 *  - null otherwise
 */
nzilbb.jsendpraat.isInstalled = null;
/**
 * The version of the browser extension.
 */
nzilbb.jsendpraat.version = null;
/**
 * The version of the Native Messaging Host (locally installed software that liases
 * between the extension and Praat) if known.
 */
nzilbb.jsendpraat.chrome.version = null;

/**
 * Callback invoked when the jsendpraat extension is detected.
 *
 * @callback extensionDetectedCallback
 * @param {string} version The version of the extension/native messaging host
 *  (the locally installed software that liases between the browser extension and Praat).
 */
/**
 * Callback invoked when progress is updated.
 *
 * @callback progressCallback
 * @param {string} string A description of the progress, if any.
 * @param {number} value The progress value.
 * @param {number} maximum The maximum possible progress value.
 * @param {string} [error] The error message if any.
 * @param {number} [code] The error code, if any.
 */
/**
 * Callback invoked when a sendpraat request is processed.
 *
 * @callback sendpraatResponse
 * @param {number} code The error code, if any. Possible values include:
 *  0:   Success
 *  1:   Sendpraat returned an error
 *  500: No arguments were passed
 *  600: There was an IO error during the download processing
 *  900: The incoming message could not be parsed as JSON
 *  999: Some other error
 * @param {string} [error] The error message if any.
 */
/**
 * Callback invoked when a upload request is processed.
 *
 * @callback uploadResponse
 * @param {number} code The error code, if any. Possible values include:
 *  0:   Success
 *  1:   Sendpraat returned an error
 *  100: There was an error during the upload request, 
 *       e.g. the URL passed for "fileUrl" does not correspond to a file that was
 *       already downloaded.
 *  500: No arguments were passed
 *  600: There was an IO error during the download processing
 *  700: There was an IO error during the upload request processing
 *  800: The upload request included a malformed URL
 *  900: The incoming message could not be parsed as JSON
 *  999: Some other error
 * @param {string} [summary] A descriptive message
 * @param {string} [error] The error message if any.
 */

/** 
 * Detects whether the browser extension is installed.
 * @callback {extensionDetectedCallback} [onExtensionDetected] Invoked once, when communication with the plugin is established.
 * @callback {sendpraatResponse} [onSendPraatResponse] Invoked whenever the response to a sendpraat(script) call is received.
 * @param {progressCallback} [onProgress] Invoked when a progress update is received - e.g. during download or upload of files.
 * @callback {uploadResponse} [onUploadResponse] Invoked when the response to an upload() call is received.
 * @callback {extensionDetectedCallback} [onNativeMessagingHostDetected] Invoked once, if communication with the Native Messaging Host is established.
 */
nzilbb.jsendpraat.detectExtension = function(onExtensionDetected, onSendPraatResponse, onProgress, onUploadResponse, onNativeMessagingHostDetected) {
  var isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);
  if (!isSafari && window.postMessage) { // extension could be compatible with this browser
    window.addEventListener("message", function(event) {
      // We only accept messages from ourselves
      if (event.source != window) return;          
      if (event.data.type == "FROM_PRAAT_EXTENSION") {
	if (event.data.type && event.data.message == "ACK") {
	  nzilbb.jsendpraat.log("jsendpraat extension is installed: v" + event.data.version);
	  nzilbb.jsendpraat.isInstalled = true;
	  nzilbb.jsendpraat.version = event.data.version;

          if (onNativeMessagingHostDetected) {
            // find out what the messaging host version is
            window.postMessage({ 
              "type": "FROM_PRAAT_PAGE", 
              "message": "version"
            }, '*');
          }
          
	  if (onExtensionDetected) onExtensionDetected(nzilbb.jsendpraat.version);
	} else if (event.data.message == "version") { // native messaging version
	  nzilbb.jsendpraat.log("jsendpraat Native Messaging Host: v" + event.data.version);
	  nzilbb.jsendpraat.chrome.version = event.data.version;
	  if (onNativeMessagingHostDetected) {
            onNativeMessagingHostDetected(nzilbb.jsendpraat.chrome.version);
          }
	} else if (event.data.message == "sendpraat") {
	  nzilbb.jsendpraat.log("jsendpraat sendpraat: " + JSON.stringify(event.data));
	  if (onSendPraatResponse) {
	    onSendPraatResponse(event.data.code, event.data.error);
	  }
	} else if (event.data.message == "progress") {
	  nzilbb.jsendpraat.log("jsendpraat progress: " + JSON.stringify(event.data));
	  if (onProgress) {
	    onProgress(event.data.string, event.data.value, event.data.maximum, 
		       event.data.error, event.data.code);
	  }
	} else if (event.data.message == "upload") {
	  nzilbb.jsendpraat.log("jsendpraat upload: " + JSON.stringify(event.data));
	  if (onUploadResponse) {
	    var messages = "";
	    // if it's LaBB-CAT, we can return more detail
	    for (var e in event.data.errors) messages += event.data.errors[e] + "\n";
	    messages = messages.trim();
	    for (var m in event.data.messages) messages += event.data.messages[m] + "\n";
	    // display mapping messages too
	    for (var m in event.data.model.mappings)
	    {
	      var mapping = event.data.model.mappings[m];
	      messages += mapping.layer + ":\t" + mapping.status + "\n";
	    }
	    onUploadResponse(event.data.code, messages, event.data.error);
	  }
	}
      } // FROM_PRAAT_EXTENSION
    }, false); // addEventListener

    // ping the extension to see if there's an acknowledgement
    window.postMessage({ type: 'FROM_PRAAT_PAGE', message: 'PING' }, '*');
    
    return true; // browser compatible
  } else { // browser doesn't have window.postMessage so the extension isn't compatible
    nzilbb.jsendpraat.isInstalled = false;
    return false; // browser not compatible
  }    
}

/**
 * Send a script for Praat to execute.
 * @param {(string|string[])} script The script to send to Praat. 
 *  This can be a single string for a one-line script (e.g. "Quit") or an array of strings 
 *  for multiline scripts (e.g. ["Read from file... http://myserver/myfile.wav, "Edit"]). 
 *  If it's an array, the first element may be the target program - i.e. "praat" or "als".
 * @param {string} authorization The Authorization header to be sent with any HTTP requests. 
 * @returns false if the a connection to the extension has not been previously established,
 *  or script is null, or true otherwise.
 */
nzilbb.jsendpraat.sendpraat = function(script, authorization) {
  if (!nzilbb.jsendpraat.isInstalled) return false;
  // ensure the script is an array whose firest element is "praat" or "als"
  if (!script) return false;
  if (Object.prototype.toString.call(script) === '[object Array]') {
    if (script.length == 0) return false;
    if (script[0].toLowerCase() != "praat"
	&& script[0].toLowerCase() != "als") {
      script.unshift("praat");
    }
  } else { // not an array, presumably a one-line script string
    script = [ "praat", script ];
  }
  window.postMessage(
    { 
      "type": "FROM_PRAAT_PAGE", 
      "message": "sendpraat",
      "sendpraat": script,
      "authorization": authorization
    }, '*');
  return true;
}

/**
 * Send a script for Praat to execute, and then upload a file to the server. 
 * This can be used to upload to a the server a previously downloaded and then edited TextGrid.
 * @param {(string|string[])} script The script to send to Praat. 
 *  This can be a single string for a one-line script (e.g. "Quit") or an array of strings 
 *  for multiline scripts (e.g. ["Read from file... http://myserver/myfile.wav, "Edit"]). 
 *  If it's an array, the first element may be the target program - i.e. "praat" or "als".
 * @param {string} uploadUrl URL to upload to.
 * @param {string} fileParameter name of file HTTP parameter.
 * @param {string} fileUrl original URL for the file to upload.
 * @param {Object} otherParameters extra HTTP request parameters.
 * @param {string} authorization The Authorization header to be sent with any HTTP requests. 
 * @returns false if the a connection to the extension has not been previously established,
 *  or script is null, or true otherwise.
 */
nzilbb.jsendpraat.upload = function(script, uploadUrl, fileParameter, fileUrl, otherParameters, authorization) {
  if (!nzilbb.jsendpraat.isInstalled) return false;
  // ensure the script is an array whose firest element is "praat" or "als"
  if (script) {
    if (Object.prototype.toString.call(script) === '[object Array]') {
      if (script.length == 0) return false;
      if (script[0].toLowerCase() != "praat"
	  && script[0].toLowerCase() != "als") {
	script.unshift("praat");
      }
    } else { // not an array, presumably a one-line script string
      script = [ "praat", script ];
    }
  }
  window.postMessage(
    { 
      "type": "FROM_PRAAT_PAGE", 
      "message": "upload",
      "sendpraat": script,
      "uploadUrl" : uploadUrl,
      "fileParameter" : fileParameter, 
      "fileUrl" : fileUrl, // original URL for the file to upload
      "otherParameters" : otherParameters,
      "authorization": authorization
    }, '*');
  return true;
}
