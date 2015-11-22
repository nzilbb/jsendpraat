# jsendpraat

Java implementation of sendpraat, which can also function as a Chrome Native Messaging Host, for communication with a Chrome extension.

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

This implementation also works as a Chrome Native Messaging Host - it can be started from the command line, and accepts messages on stdin using Chrome's Native Messaging protocol. Operating in this mode, two extra functions are supported:
* Praat commands can include URLs, which are automatically downloaded to a local file and the local file name substituted into the command before execution. The format for a message is:
```
    {
       "message" : "sendpraat"
       "sendpraat" : [
         "praat",
         "Quit"
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
