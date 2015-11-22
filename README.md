# jsendpraat

Java implementation of sendpraat.

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
