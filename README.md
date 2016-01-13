# jsendpraat

Praat (http://praat.org) is a popular phonetics tool developed by Paul Boersma and David Weenink at the University of Amsterdam. Praat can receive commands from other programs using a mechanism called "sendpraat".

*jsendpraat*, developed by the NZILBB at the University of Canterbury (http://www.nzilbb.canterbury.ac.nz), includes a Java implementation of sendpraat, and browser extensions (for Chrome and Firefox), allowing interaction with Praat from a web browser. These extensions were developed primarily for use with LaBB-CAT, a brower-based linguistics tool (https://labbcat.canterbury.ac.nz), but can be used to open embedded audio in Praat from any web page.

jsendpraat.jar functions as a Chrome Native Messaging Host, which manages communication between the browser (Chrome or Firefox) extension and Praat. The Java code for jsendpraat.jar and the Javascript code for the browser extensions are here. The packaged Chrome extension is available here: https://chrome.google.com/webstore/detail/praat-integration/hmmnebkieionilgpepijmfabdickmnig

This implementation tries to load and run JNI native libraries for executing sendpraat compiled from original C code.  Failing that, it looks for and runs the sendpraat standalone program, which  must be installed in the same folder as the praat program. Failing that, it attempts to use a pure-java implementation of sendpraat, which uses signals and only works on Linux.

If praat is not already running, a new praat process is started. If the praat program cannot be found, the user is asked where it is.

--------------------------

A compiled version of jsendpraat is available [here](bin/jsendpraat.jar)

It works just like sendpraat on the command line (except it's the same file for any platform), e.g.

`java -jar jsendpraat.jar Praat Quit`

Alternatively, you can call sendpraat from Java by using:

```
nzilbb.jsendpraat.SendPraat sp = new nzilbb.jsendpraat.SendPraat();
...
sp.sendpraat("Praat", "Quit");
```

jsendpraat.jar works as a Chrome Native Messaging Host if the first command line argument is not "Praat". It then accepts messages on stdin using Chrome's Native Messaging protocol (https://developer.chrome.com/extensions/nativeMessaging#native-messaging-host-protocol). The format for a message is:
```
    {
       "message" : "sendpraat"
       "sendpraat" : [
         "praat",
         "Quit"
       ]
    }
```
Operating in this mode, two extra functions are supported:
* Praat commands can include URLs, which are automatically downloaded to a local file and the local file name substituted into the command before execution. e.g.
The format for a message is:
```
    {
       "message" : "sendpraat"
       "sendpraat" : [
         "praat",
         "Read from file... https://myserver/myfile.wav",
         "Edit"
       ]
    }
```
* In addition to sendpraat commands, files that have been downloaded can be re-uploaded, so TextGrids can be downloaded, edited by the user, and then re-uploaded.  The format for upload messages is:
```
    {
        "message" : "upload", 
        "sendpraat" : [
           "praat",
           "select TextGrid " + nameInPraat, // name of a textgrid object in Praat
           "Write to text file... " + fileUrl // original URL of the downloaded file
        ], 
        "uploadUrl" : uploadUrl, // URL to upload to
        "fileParameter" : fileParameter, // name of file HTTP parameter
        "fileUrl" : fileUrl, // original URL of the downloaded file
        "otherParameters" : otherParameters // extra HTTP request parameters
    }
```
