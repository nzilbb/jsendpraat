# jsendpraat

Java implementation of sendpraat.

This class tries to load and run JNI native libraries for executing sendpraat compiled from
original C code.  Failing that, it looks for and runs the sendpraat standalone program, which 
must be installed in the same folder as the praat program. Failing that, it attempts to use
a pure-java implementation of sendpraat, which uses signals and only works on Linux.

If praat is not already running, a new praat process is started. If the praat program cannot
be found, the user is asked where it is.
