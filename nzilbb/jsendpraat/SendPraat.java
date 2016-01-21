//
// Copyright 2004-2015 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.jsendpraat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.swing.JOptionPane;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import nzilbb.http.FileDownloader;
import nzilbb.http.IMessageHandler;
import nzilbb.http.IProgressIndicator;
import nzilbb.http.HttpRequestPostMultipart;

/**
 * Java implementation of sendpraat.
 * <p>This class tries to load and run JNI native libraries for executing sendpraat compiled from
 * original C code.  Failing that, it looks for and runs the sendpraat standalone program, which 
 * must be installed in the same folder as the praat program. Failing that, it attempts to use
 * an all-java implementation of sendpraat, which uses signals and only works on Linux.
 * <p>If praat is not already running, a new praat process is started. If the praat program cannot
 * be found, the user is asked where it is.
 * <p>This class also works as a Chrome Native Messaging Host - it can be started from the command
 * line, and accepts messages on stdin using Chrome's 
 * <a href="https://developer.chrome.com/extensions/nativeMessaging#native-messaging-host-protocol">Native Messaging protocol</a>. 
 * Operating in this mode, two extra functions are supported:
 * <ul>
 *  <li>Praat commands can include URLs, which are automatically downloaded to a local file and
 *   the local file name substituted into the command before execution.</li>
 *  <li>In addition to sendpraat commands, files that have been downloaded can be re-uploaded, 
 *   so TextGrids can be downloaded, edited by the user, and then re-uploaded.  The format for
 *   upload messages is:<pre>
 *    {
 *        "message" : "upload", 
 *        "sendpraat" : [
 *           "praat",
 *           "select TextGrid " + <var>nameInPraat</var>, // name of a textgrid object in Praat
 *           "Write to text file... " + <var>fileUrl</var> // original URL of the downloaded file
 *        ], 
 *        "uploadUrl" : <var>uploadUrl</var>, // URL to upload to
 *        "fileParameter" : <var>fileParameter</var>, // name of file HTTP parameter
 *        "fileUrl" : <var>fileUrl</var>, // original URL of the downloaded file
 *        "otherParameters" : <var>otherParameters</var> // extra HTTP request parameters
 *        "clientRef" : <var>reference</var> // an optional reference string that's passed back to the client
 *    }
 *  </pre></li>
 * </ul>
 */
public class SendPraat
{
   static
   { // try to use native look & feel
      try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
   }

   // Attributes:
   
   /** Native library file */
   private static File fNativeLibrary = null;
   /** Whether native library file was successfully loaded */
   private static boolean bNativeLibraryLoaded = false;
   /** Praat process we started */
   private static Process procPraat;
   /** Regular expression for detecting URLs in script commands */
   private Pattern httpUrlPattern;
   /** Native byte order (from Native Message Host JSON message passing) */
   private ByteOrder nativeByteOrder = ByteOrder.nativeOrder();

   /**
    * Number of milliseconds to give to praat for it to start up, before sending it messages.
    */
   private long lWaitMsPraatStart = 1750;

   /**
    * Praat's executable file name.
    */
   private String praatProgramName = "praat";
   /**
    * Praat's executable file name.
    */
   public String getPraatProgramName() { return praatProgramName; }
      
   /**
    * The local filesystem path to the folder/directory containing the praat and sendpraat program.
    */
   static private String pathToPraat;
   /**
    * PathToPraat accessor 
    * @return The local filesystem path to the folder/directory containing the praat and sendpraat program.
    */
   static public String getPathToPraat() { return pathToPraat; }
   /**
    * PathToPraat mutator
    * @param sNewPathToPraat The local filesystem path to the folder/directory containing the praat and sendpraat program.
    */
   static public void setPathToPraat(String sNewPathToPraat) { pathToPraat = sNewPathToPraat; }
   
   /**
    * Whether to use verbose logging or not.
    * @see #getVerbose()
    * @see #setVerbose(boolean)
    */
   protected boolean verbose = true; // TODO default to false
   /**
    * Getter for {@link #verbose}: Whether to use verbose logging or not.
    * @return Whether to use verbose logging or not.
    */
   public boolean getVerbose() { return verbose; }
   /**
    * Setter for {@link #verbose}: Whether to use verbose logging or not.
    * @param newVerbose Whether to use verbose logging or not.
    */
   public void setVerbose(boolean newVerbose) { verbose = newVerbose; }
   
   /** Error message returned */
   private String errorMessage;
   /** Sendpraat timeout */
   private long theTimeOut;
   
   /** Whether this is an xwindows (e.g. linux) system */
   private boolean xwin = false;
   /** Whether this is a Windows system */
   private boolean win = false;
   /** Whether this is an OS X system */
   private boolean mac = false;

   /** Configuration file */
   private File sendpraatXml;
   
   /** System property value: os.name */
   private String sOsName;
   /** System property value: os.arch */
   private String sOsArch;
   /** System property value: user.home */
   private String sUserHome;
   /** System property value: user.dir */
   private String sUserDir;
   /** Our process id */
   private int iPid = -1;
   /** Whether to send message sizes */
   private boolean bSendMessageSize = true;
   
   /**
    * Constructor
    */
   public SendPraat()
   {
      httpUrlPattern = Pattern.compile("https?://\\S+");
      sOsName = java.lang.System.getProperty("os.name");
      sOsArch = java.lang.System.getProperty("os.arch");
      int iFirstSpace = sOsName.indexOf(' ');
      if (iFirstSpace > 0) sOsName = sOsName.substring(0, iFirstSpace);
      try { sUserHome = java.lang.System.getProperty("user.home"); }
      catch(Exception exception) { log("Can't get user.home: " + exception); }
      try { sUserDir = java.lang.System.getProperty("user.dir"); }
      catch(Exception exception) { log("Can't get user.dir: " + exception); }
      xwin = sOsName.startsWith("Linux");
      win = sOsName.startsWith("Windows");
      mac = sOsName.startsWith("Mac");

      // try to load settings
      try
      {
	 Properties settings = new Properties();
	 File homeDir = new File(sUserHome);
	 File configDir = homeDir;
	 // try to respect config file conventions of each platform
	 if (win)
	 {
	    String APPDATA = System.getenv("APPDATA");
	    if (APPDATA != null)
	    {
	       configDir = new File(System.getenv("APPDATA"));
	    }
	    else
	    {
	       configDir = new File(configDir, "AppData");
	       configDir = new File(configDir, "Local");
	    }
	    configDir = new File(configDir, "jsendpraat");
	 }
	 else if (xwin)
	 {
	    configDir = new File(configDir, ".config");
	    configDir = new File(configDir, "jsendpraat");
	 }
	 else if (mac)
	 {
	    configDir = new File(configDir, "Library");
	    configDir = new File(configDir, "Application Support");
	    configDir = new File(configDir, "jsendpraat");
	 }
	 configDir.mkdir(); // in case it's not there yet
	 sendpraatXml = new File(configDir, "SendPraat.xml");
	 log("Settings in: " + sendpraatXml.getPath());
	 settings.loadFromXML(new FileInputStream(sendpraatXml));
	 setPathToPraat(settings.getProperty("pathToPraat"));
	 log("Loaded path: " + getPathToPraat());
      }
      catch(Throwable exception)
      {
	 log("Could not load settings: " + exception);
      }

      checkPraatLocation();

      if (fNativeLibrary == null)
      {
	 // load the native library?
	 String sNativeLibraryName = "libsendpraat" + sOsName + sOsArch + ".";
	 URL urlLibrary = null;
	 if (win)
	 {
	    //sNativeLibraryName = "libsendpraatWindows.dll";
	    sNativeLibraryName += "dll";
	 }
	 else
	 {
	    try
	    {
	       // force X11 libraries to load
	       new java.awt.Frame("sendpraat");
	    }
	    catch (Throwable t)
	    {
	       log("SendPraat: could not load X11: " + t.toString());
	    }
	    sNativeLibraryName += "so";
	 }	    
	 try
	 {
	    urlLibrary = getClass().getResource(sNativeLibraryName);
	 }
	 catch (Throwable t)
	 {
	    log("SendPraat: error getting native url (" + sNativeLibraryName + "): " + t.toString());
	 }
	 
	 if (urlLibrary != null)
	 {
	    try
	    {
	       // extract the native library
	       //log("SendPraat: opening input stream");
	       BufferedInputStream in = new BufferedInputStream(urlLibrary.openStream());
	       fNativeLibrary = File.createTempFile("SendPraat.","."+sNativeLibraryName);
	       fNativeLibrary.deleteOnExit();
	       //log("SendPraat: file: " + fNativeLibrary.getPath());
	       FileOutputStream out = new FileOutputStream(fNativeLibrary);
	       //log("SendPraat: opening output stream");
	       byte [] chunk = new byte[1024];
	       for(int numBytes = in.read(chunk); numBytes >= 0; numBytes = in.read(chunk))
	       {
		  out.write(chunk, 0, numBytes);
	       } // next chunk
	       out.close();
	       in.close();	       
	       //log("SendPraat: finished extracting file");
	    }
	    catch (Throwable t)
	    {
	       logError("SendPraat: error extracting native library (" 
		     + urlLibrary.toString() + "): " + t.toString());
	    }
	 }
	 
	 if (fNativeLibrary != null)
	 {
	    try
	    {
	       if(fNativeLibrary.exists())
	       {
		  System.load(fNativeLibrary.getPath());
		  bNativeLibraryLoaded = true;
		  log("SendPraat: Loaded native library: " + fNativeLibrary);
	       }
	       else
	       {
		  logError("SendPraat: failed to extract native library: " + fNativeLibrary.getPath());
	       }
	       
	    }
	    catch (Throwable t)
	    {
	       logError("SendPraat: error loading native library ("
		     + fNativeLibrary.getPath() + "): " + t.toString());
	       fNativeLibrary.delete();
	       bNativeLibraryLoaded = false;
	       fNativeLibrary = null;
	    }
	 } // native library file specified
      } // try to load native library
      else
      {
	 log("Already loaded: " + fNativeLibrary.getPath());
      }

   } // end of constructor
   
   /** JNI declaration */
   private native String sendpraatNative(String programName, long timeOut, String text);
   
   /**
    * Checks that the Praat location has been set, and works.
    * @return true if Praat has been located and is accessible, false otherwise.
    */
   public boolean checkPraatLocation()
   {
      log("Checking whether we know where praat is...");
      if (pathToPraat == null)
      {
	 if (mac)
	 {
	    setPathToPraat("/Applications/");
	 }
	 else
	 {
	    // on linux based systems, Praat can install itself on to the path - so we try to find out where...
	    try
	    {
	       String[] cmd = {"/usr/bin/which","praat"};
	       Process proc = Runtime.getRuntime().exec(cmd);
	       proc.waitFor();
	       BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
	       String sWhich = in.readLine();
	       in.close();
	       setPathToPraat(sWhich.substring(0, sWhich.length() - "praat".length()));
	    }
	    catch (Throwable t) 
	    {
	       // System.out.println("" + t);
	    }
	 }
	 if (pathToPraat != null)
	 {
	    try
	    { // try saving it for future reference
	       Properties settings = new Properties();
	       settings.setProperty("pathToPraat", getPathToPraat());
	       settings.storeToXML(new FileOutputStream(sendpraatXml), 
				   "Automatically deduced praat location", "UTF-8");
	    }
	    catch(Throwable exception)
	    {
	       logError("Could not save settings: " + exception);
	    }
	 }
      } // pathToPraat == null

      // set praat program name
      try
      {
	 if (mac)
	 {
	    // Mactintoshes are slower in responding, so adjust our
	    // wait times
	    lWaitMsPraatStart *= 2;
	    lWaitMsPraatStart *= 2;
	    
	    // also sendpraat is usually not installed as executable, so try to make it executable
	    String sPath = (pathToPraat==null?"":pathToPraat) + "sendpraat";
	    String[] cmdArray = {"/bin/chmod", "a+x", sPath};		  
	    try
	    {
	       Runtime.getRuntime().exec(cmdArray);
	       try { Thread.sleep(lWaitMsPraatStart); } catch(InterruptedException x){}
	    }
	    catch(Exception x)
	    {
	       logError("Can't chmod " + cmdArray[2] + " : " + x);
	    }
	    
	    // And the path needs adjusting
	    praatProgramName = "Praat.app/Contents/MacOS/Praat";
	 }
	 else if (win)
	 {
	    praatProgramName = "Praat.exe";
	 }
      }
      catch(Throwable t)
      {
	 logError(""+t);
      }

      // check praat exists where it's supposed to
      File praatProgramFile = new File((pathToPraat==null?"":pathToPraat) + praatProgramName);
      log("Praat file: " + praatProgramFile.getPath());
      if (!praatProgramFile.exists())
      {
	 // allow the user to point us to the file
	 JOptionPane.showMessageDialog(
	    null, "Praat can not be found."
	    +"\nPlease specify the location where you have installed Praat and SendPraat", 
	    "Praat not found", JOptionPane.ERROR_MESSAGE);
	 JFileChooser chooser = new JFileChooser();
	 try
	 {
	    chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
	 }
	 catch(Exception xDir)
	 {}
	 chooser.setDialogTitle("Select the Praat program file.");
	 int returnVal = chooser.showOpenDialog(null);
	 if(returnVal == JFileChooser.APPROVE_OPTION) 
	 {
	    log("Using dir: " + chooser.getSelectedFile().getParentFile().getPath());	    
	    setPathToPraat(chooser.getSelectedFile().getParentFile().getPath() + File.separator);
	    try
	    { // try saving it for future reference
	       Properties settings = new Properties();
	       settings.setProperty("pathToPraat", getPathToPraat());
	       settings.storeToXML(new FileOutputStream(sendpraatXml), 
				   "User-selected praat location", "UTF-8");
	    }
	    catch(Throwable exception)
	    {
	       logError("Could not save settings: " + exception);
	    }
	    return true;
	 }
	 else
	 {
	    log("Cancelled");
	    return false;
	 }
      }
      return true;
   } // end of checkPraatLocation()
   
   /**
    * Starts Praat ready to receive commands, or stops praat if bStopPraat == true
    */
   public Process startPraat()
   {
      // is praat already/still running
      try
      {
	 if (procPraat != null)
	 {
	    // throws IllegalThreadStateException if it's still running:
	    procPraat.exitValue(); 
	 }
	 String strCommand = (pathToPraat==null?"":pathToPraat) + praatProgramName;
	 try
	 {
	    String[] cmdArray = { strCommand };
	    procPraat = Runtime.getRuntime().exec(cmdArray);
	    log("Praat started");

	    try { Thread.sleep(lWaitMsPraatStart); } catch(InterruptedException x){}

	    // if there was any stdout/stderr output from praat, print it on our stderr
	    InputStream inStream = procPraat.getInputStream();
	    InputStream errStream = procPraat.getErrorStream();
	    byte[] buffer = new byte[1024];
	    int bytesRead = inStream.available();
	    while(bytesRead > 0)
	    {
	       // write to the log file
	       bytesRead = inStream.read(buffer);
	       System.err.write(buffer, 0, bytesRead);

	       // data ready?
	       bytesRead = inStream.available();
	    } // next chunk of data
	    bytesRead = errStream.available();
	    while(bytesRead > 0)
	    {
	       // write to the log file
	       bytesRead = errStream.read(buffer);
	       System.err.write(buffer, 0, bytesRead);

	       // data ready?
	       bytesRead = errStream.available();
	    } // next chunk of data

	 }
	 catch(IOException ioException)
	 {
	    logError(ioException.getMessage() 
		  + " - Are you sure " + strCommand + " is installed and available?");
	 }
	 catch (java.security.AccessControlException accessException)
	 {
	    logError(accessException.getMessage() 
		  + " - Are you sure " + strCommand + " is installed and available?");
	 }
	 catch(Exception exception)
	 {
	    logError(exception.getClass() + ": " + exception.getMessage());
	 }
      }
      catch(IllegalThreadStateException exception)
      {} // praat already running
      return procPraat;
   } // end of startPraat()

   /**
    * Runs the message-handling loop for handling messages as a 
    * <a href="https://developer.chrome.com/extensions/nativeMessaging#native-messaging-host-protocol">Chromium Native Messaging</a> host.
    * <p>The loop reads JSON messages from stdin (prefixed by a 4-byte message size indicator)
    * and expects the JSON object to have a "sendpraat" attribute whose value is an array of
    * arguments that would be passed to sendpraat on the command line.
    * <p> e.g. 
    * <pre>
    * {
    *   "message" : "sendpraat",
    *   "sendpraat" : [
    *     "praat",
    *     "Quit"
    *   ],
    *   "clientRef" : "it's me"
    * }
    * </pre>
    * <p>In addition to sendpraat commands, files that have been downloaded can be re-uploaded, 
    * so TextGrids can be downloaded, edited by the user, and then re-uploaded.  The format for
    * upload messages is:<pre>
    *    {
    *        "message" : "upload", 
    *        "sendpraat" : [
    *           "praat",
    *           "select TextGrid " + <var>nameInPraat</var>, // name of a textgrid object in Praat
    *           "Write to text file... " + <var>fileUrl</var> // original URL of the downloaded file
    *        ], 
    *        "uploadUrl" : <var>uploadUrl</var>, // URL to upload to
    *        "fileParameter" : <var>fileParameter</var>, // name of file HTTP parameter
    *        "fileUrl" : <var>fileUrl</var>, // original URL of the downloaded file
    *        "otherParameters" : <var>otherParameters</var>, // extra HTTP request parameters
    *        "clientRef" : <var>reference</var> // an optional reference string that's passed back to the client
    *    }
    *  </pre>
    * <p>The response is written in JSON to stdout (prefixed by a 4-byte message size indicator)
    * with an "error" attribute set the a message, if something went wrong, and a "code" attribute
    * whose value is an integer:
    * <dl>
    *  <dt>0</dt> <dd>Success</dd>
    *  <dt>1</dt> <dd>Sendpraat returned an error</dd>
    *  <dt>100</dt> <dd>There was an error during the upload request, 
    *                   e.g. the URL passed for "fileUrl" does not correspond to a file that was
    *                   already downloaded.</dd>
    *  <dt>500</dt> <dd>No arguments were passed</dd>
    *  <dt>600</dt> <dd>There was an IO error during the download processing</dd>
    *  <dt>700</dt> <dd>There was an IO error during the upload request processing</dd>
    *  <dt>800</dt> <dd>The upload request included a malformed URL</dd>
    *  <dt>900</dt> <dd>The incoming message could not be parsed as JSON</dd>
    *  <dt>999</dt> <dd>Some other error</dd>
    * </dl>
    */
   protected void chromiumHost()
   {
      DataInputStream stdin = new DataInputStream(System.in);
      DataOutputStream stdout = new DataOutputStream(System.out);
      byte[] messageSizeBuffer = new byte[4];
      while (true)
      {
	 log("Waiting for message...");
	 try
	 {
	    // get message size
	    stdin.readFully(messageSizeBuffer);
	    long messageSize = ByteBuffer.wrap(messageSizeBuffer)
	       .order(nativeByteOrder).getInt() & 0xFFFFFFFFL;
	    log("Message size: " + messageSize);
	    
	    // read message
	    byte[] bMessage = new byte[(int)messageSize];
	    stdin.readFully(bMessage);
	    String strMessage = new String(bMessage);
	    log("Message: " + strMessage);
	    JSONObject jsonReply = new JSONObject("{ \"message\":\"sendpraat\", \"error\":\"Invalid message\", \"code\":999}");
	    String clientRef = null;
	    try
	    {
	       // parse JSON
	       JSONObject jsonMessage = new JSONObject(strMessage);
	       if (jsonMessage.has("clientRef"))
	       {
		  clientRef = jsonMessage.getString("clientRef");
	       }
	       if ("version".equals(jsonMessage.getString("message")))
	       {
		  jsonReply.put("message", "version");
		  jsonReply.put("version", "20160113.1254");
		  jsonReply.remove("error");
		  jsonReply.put("code", 0);
	       }
	       else
	       { // assume a sendpraat message
		  JSONArray jsonArguments = jsonMessage.getJSONArray("sendpraat");
		  
		  if (jsonArguments.length() == 0)
		  {
		     if ("sendpraat".equals(jsonMessage.getString("message"))) // not for "upload"
		     {
			jsonReply.put("error", "sendpraat command was empty");
			jsonReply.put("code", 500);
		     }
		  }
		  else
		  { // there are arguments
		     // get the argments as an array of strings
		     String argv[] = new String[jsonArguments.length()];
		     for (int i = 0; i < argv.length; i++)
		     {
			// download any HTTP URLs to local files...
			argv[i] = convertHttpToLocal(jsonArguments.getString(i), stdout, clientRef);
		     } // next arguments
		     String reply = sendpraat(argv);
		     jsonReply.put("error", reply);
		     if (reply != null) 
		     { // error
			jsonReply.put("code", 1);
		     }
		     else
		     { // success
			jsonReply.put("code", 0);
		     }
		  }

		  if ("upload".equals(jsonMessage.getString("message")))
		  {
		     jsonReply = processUpload(jsonMessage);
		  }
	       } // sendpraat or upload message
	    }
	    catch(JSONException exception)
	    {
	       logError("Error parsing message: " + exception);
	       jsonReply.put("error", exception.toString());
	       jsonReply.put("code", 900);
	    }
	    catch(Exception exception)
	    {
	       logError("Error: " + exception);
	       jsonReply.put("error", exception.getMessage());
	       jsonReply.put("code", 600);
	    }
	    if (clientRef != null)
	    {
	       jsonReply.put("clientRef", clientRef);
	    }
	    // reply to message
	    String reply = jsonReply.toString();
	    log("reply: " + reply);
	    byte[] replyBuffer = reply.getBytes("UTF-8");
	    if (bSendMessageSize)
	    {
	       ByteBuffer replySizeBuffer = ByteBuffer.allocate(4).order(nativeByteOrder);
	       replySizeBuffer.putInt(replyBuffer.length);
	       stdout.write(replySizeBuffer.array(), 0, 4);
	       stdout.flush();
	    }
	    stdout.write(replyBuffer, 0, replyBuffer.length);
	    stdout.flush();
	 }
	 catch(EOFException exception)
	 {
	    break;
	 }
	 catch(IOException exception)
	 {
	    logError(exception.toString());
	    break;
	 }
      } // next message
      log("Goodbye");
      System.exit(0);
   } // end of chromiumHost()
   
   /**
    * Converts all http:// and https:// URLs in the given string to local file paths, by downloading the content to a local file.
    * @param s The command to convert.
    * @param stdout For reporting progress.
    * @param clientRef Reference to pass back to the client on progress updates.
    * @return The given string, with all HTTP URLs converted to local paths where possible.
    * @throws Exception If something goes wrong during download.
    */
   public String convertHttpToLocal(String s, final DataOutputStream stdout, final String clientRef)
      throws Exception
   {
      Matcher httpUrlMatcher = httpUrlPattern.matcher(s);
      StringBuffer newS = new StringBuffer();
      int position = 0;
      // while there are more URLs in the string
      while (httpUrlMatcher.find())
      {
	 // copy through the non-URL stuff that precedes this URL
	 newS.append(s.substring(position, httpUrlMatcher.start()));
	 File file = null;
	 URL url = null;
	 url = new URL(httpUrlMatcher.group());
	 log("Fetching " + url);
	 
	 FileDownloader downloader = new FileDownloader(
	    url, new IProgressIndicator()
	       {
		  int maximum = 100;
		  public int getMaximum() { return maximum; }
		  public void setMaximum(int newMaximum) { maximum = newMaximum; reportProgress(); }
		  int value = 0;
		  public int getValue() { return value; }
		  public void setValue(int newValue) 
		  { 
		     // only report report progress every 5%, so as not to flood the caller
		     // ...more than 5% chunk
		     boolean report = (Math.abs(newValue - value) * 100) / maximum > 5 
			// or little chunks, and we've reached a 5% multiple
			|| ((newValue * 100) / maximum % 5 == 0 
			    && (value * 100) / maximum % 5 != 0)
			// or if we reach the end (or beginning)
			|| newValue == maximum || newValue == 0;
		     value = newValue; 
		     if (report) reportProgress(); 
		  }
		  String string = "";
		  public String getString() { return string; }
		  public void setString(String newString) 
		  { 
		     boolean report = !newString.equals(string);
		     string = newString; 
		     if (report) reportProgress(); 
		  }
		  void reportProgress()
		  {
		     JSONObject json = new JSONObject();
		     json.put("message", "progress");
		     json.put("maximum", maximum);
		     json.put("value", value);
		     json.put("string", string);
		     if (clientRef != null) json.put("clientRef", clientRef);
		     String reply = json.toString();
		     log("progress: " + reply);
		     try
		     {
			byte[] replyBuffer = reply.getBytes("UTF-8");
			if (bSendMessageSize)
			{
			   ByteBuffer replySizeBuffer = ByteBuffer.allocate(4).order(nativeByteOrder);
			   replySizeBuffer.putInt(replyBuffer.length);
			   stdout.write(replySizeBuffer.array(), 0, 4);
			   stdout.flush();
			}
			stdout.write(replyBuffer, 0, replyBuffer.length);			
			stdout.flush();
		     }
		     catch(UnsupportedEncodingException exception) { logError(exception.toString()); }
		     catch(IOException exception) { logError(exception.toString()); }
		  }
	       }, 
	    new IMessageHandler()
	    {
	       public void message(String s) { log(s); }
	       public void error(String s) 
	       { 		     
		  logError(s); 
		  
		  // send error back to client too
		  JSONObject json = new JSONObject();
		  json.put("message", "progress");
		  json.put("error", s);
		  json.put("code", 600);
		  if (clientRef != null) json.put("clientRef", clientRef);
		  String reply = json.toString();
		  try
		  {
		     byte[] replyBuffer = reply.getBytes("UTF-8");
		     if (bSendMessageSize)
		     {
			ByteBuffer replySizeBuffer = ByteBuffer.allocate(4).order(nativeByteOrder);
			replySizeBuffer.putInt(replyBuffer.length);
			stdout.write(replySizeBuffer.array(), 0, 4);
			stdout.flush();
		     }
		     stdout.write(replyBuffer, 0, replyBuffer.length);			
		     stdout.flush();
		  }
		  catch(UnsupportedEncodingException exception) { logError(exception.toString()); }
		  catch(IOException exception) { logError(exception.toString()); }
	       }
	    });
	 synchronized (downloader)
	 {
	    downloader.start();
	    downloader.wait();
	    
	    // set the local file for Praat
	    file = downloader.getLocalFile();
	    if (file == null)
	    {
	       logError("Download of " + url + " failed.");
	       throw new Exception(downloader.getLastError()!=null?downloader.getLastError()
				   :"Download of " + url + " failed.");
	    }
	    else
	    {
	       log("Local file: " + file.getPath());
	    }
	 } // synchronized
	 if (file != null && file.exists())
	 {
	    newS.append(file.getPath());
	 }
	 else
	 {
	    // couldn't get content, so pass through the URL unchanged
	    newS.append(url.toString());
	 }
	 
	 // update the position to the end of the pattern for next time around
	 position = httpUrlMatcher.end();
      } // next URL
      // copy through the non-URL end of the string
      newS.append(s.substring(position));
      log("command: " + newS);
      return newS.toString();
   } // end of convertHttpToLocal()

   /**
    * Processes a file (TextGrid) upload request.
    * @param jsonMessage
    * @return Reply to return to the caller.
    */
   public JSONObject processUpload(JSONObject jsonMessage)
   {
      JSONObject jsonReply = new JSONObject("{ \"message\":\"upload\", \"error\":\"Invalid upload\", \"code\":999}");
      try
      {
	 URL uploadUrl = new URL(jsonMessage.getString("uploadUrl"));
	 URL fileUrl = new URL(jsonMessage.getString("fileUrl"));
	 File file = FileDownloader.getDownloadedFile(fileUrl);
	 if (file == null) throw new Exception("There is no local file for: " + fileUrl);
	 String fileParameter = jsonMessage.getString("fileParameter");
	 JSONObject otherParameters = jsonMessage.getJSONObject("otherParameters");
	 
	 String auth = null;
	 // do we need authorization?
	 HttpURLConnection connection = (HttpURLConnection)uploadUrl.openConnection();
	 try
	 {
	    connection.getInputStream(); // throws exception if unauthorized
	    connection.disconnect();
	 }
	 catch (Exception x)
	 {
	    connection.disconnect();
	    if (connection.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED)
	    {	    
	       for (String authorization : FileDownloader.getAuthorizations(uploadUrl))
	       {
		  connection = (HttpURLConnection)uploadUrl.openConnection();
		  try
		  {
		     connection.setRequestProperty("Authorization", authorization);
		     connection.getInputStream(); // throws exception if unauthorized
		     connection.disconnect();
		     auth = authorization;
		     break;
		  }
		  catch(Exception exception)
		  {
		     connection.disconnect();
		  }
	       } // next possible authorization
	    } // unauthorized
	 } // catch
	 log("uploading... " + auth); // TODO remove auth logging
	 // now run the real request
	 HttpRequestPostMultipart postRequest = new HttpRequestPostMultipart(uploadUrl, auth);
	 postRequest.setHeader("Accept", "application/json");
	 postRequest.setParameter(fileParameter, file);
	 for (String parameter : otherParameters.keySet())
	 {
	    postRequest.setParameter(parameter, otherParameters.get(parameter));
	 } // next parameter
	 BufferedReader reader = new BufferedReader(
	    new InputStreamReader(postRequest.post().getInputStream()));
	 StringBuilder s = new StringBuilder();
	 String sLine = reader.readLine();
	 while (sLine != null)
	 {
	    s.append(sLine);
	    s.append("\n");
	    sLine = reader.readLine();
	 }
	 reader.close();
	 jsonReply = new JSONObject(s.toString());
	 if (!jsonReply.has("message")) jsonReply.put("message", "upload");
	 if (!jsonReply.has("code")) jsonReply.put("code", 0); // success
      }
      catch(MalformedURLException exception)
      {
	 jsonReply.put("error", exception.toString());
	 jsonReply.put("code", 800);
      }
      catch(IOException exception)
      {
	 jsonReply.put("error", exception.toString());
	 jsonReply.put("code", 700);
      }
      catch(JSONException exception)
      {
	 jsonReply.put("error", exception.toString());
	 jsonReply.put("code", 900);
      }
      catch(Exception exception)
      {
	 jsonReply.put("error", exception.getMessage());
	 jsonReply.put("code", 100);
      }
      return jsonReply;
   } // end of processUpload()
   /**
    * Invokes sendpraat, using the native library or whatever other method is available.
    * @param programName
    * @param timeOut
    * @param text
    * @return An error, or null
    */
   private String sendpraat(String programName, long timeOut, String text)
   {
      log("sendpraat: " + programName + " " + timeOut + " " + text);
      if (bNativeLibraryLoaded)
      {
	 String sReturned = sendpraatNative(programName, timeOut, text);
	 if (sReturned != null) log("Native library returned: " + sReturned);
	 return sReturned;
      }
      else
      {
	 // try to execute the native program
	 log("Trying to execute native program...");
	 try
	 {
	    return sendpraatExternal(programName, timeOut, text);
	 }
	 catch(Exception exception)
	 {
	    log("Program failed: " + exception + " - trying pure java method...");
	    
	    // if we get this far, last ditch effort - use signals
	    return sendpraatJava(programName, timeOut, text);
	 }
      }
   }
   
   /**
    * Attempt to run sendpraat by executing an external program
    * @param programName
    * @param timeOut
    * @param text
    * @return An error message, or null
    * @throws Exception
    */
   public String sendpraatExternal(String programName, long timeOut, String text)
      throws Exception
   {
      // try to execute the native program
      String[] cmdArray = 
	 {
	    pathToPraat + "sendpraat" + (
	       win?".exe":
	       mac?"_"+java.lang.System.getProperty("os.arch")
	       :""),
	    programName,
	    text
	 };
      Process proc = null;
      try
      {
	 proc = Runtime.getRuntime().exec(cmdArray);
      }
      catch (Exception x)
      {
	 // second chance, this time without trying to be clever about
	 // the exe name
	 cmdArray[0] = pathToPraat + "sendpraat";
	 proc = Runtime.getRuntime().exec(cmdArray);
      }
      
      if (mac && false)
      {
	 // the MacIntosh version returns an error even if it succeeds,
	 // so we don't bother waiting for it
	 return null;
      }
      
      // return any error
      BufferedReader in = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
      StringWriter out = new StringWriter();
      String sLine = in.readLine();
      while(sLine != null)
      {
	 out.write(sLine + "\n");
	 
	 // data ready?
	 sLine = in.readLine();
      } // next chunk of data	 
      out.close();
      in.close();
      String sStdOut = out.toString().trim();
      return sStdOut.length() > 0?sStdOut:null;
   } // end of sendpraatExternal()
   
   /**
    * Main entrypoint for calling from the command-line
    * @param argv Command-line arguments
    */
   public static void main(String argv[])
   {
      SendPraat app = new SendPraat();
      if (argv.length == 0
	  || argv[0].toLowerCase().indexOf("usage") >= 0
	  || argv[0].toLowerCase().indexOf("help") >= 0)
      {
	 app.printUsage();
      }
      else if (argv[0].toLowerCase().equals("praat"))
      {
	 String args = "";
	 for (String a : argv) args += " " + a;
	 String result = app.sendpraat(argv);
	 if (result != null) System.out.println(result);
      }
      else
      {
	 for (String arg : argv) System.err.println("ARG: \"" + arg + "\"");
	 if (argv[0].equals("--suppress-message-size"))
	 {
	    app.bSendMessageSize = false;
	    System.err.println("Supressing message size headers.");
	 }
	 app.chromiumHost();
      }
      return;
   } // end of main()
   
   // PrintWriter logWriter = null;
   /**
    * Log a message (if in verbose mode)
    * @param s The message to log
    */
   public void log(String s)
   {
      if (verbose) System.err.println(s);
   } // end of log()

   /**
    * Log an error (if in verbose mode)
    * @param s The error to log
    */
   public void logError(String s)
   {
      System.err.println(s);
   } // end of log()
   
   /**
    * Prints command-line usage information to stderr
    */
   public void printUsage()
   {
      System.err.println ("Syntax:");
      if (win)
      {
	 System.err.println ("   sendpraat <program> <message>");
      }
      else
      {
	 System.err.println ("   sendpraat [<timeOut>] <program> <message>");
      }
      System.err.println ("");
      System.err.println ("Arguments:");
      System.err.println ("   <program>: the name of a running program that uses the Praat shell.");
      System.err.println ("   <message>: a sequence of Praat shell lines (commands and directives).");
      if (!win)
      {
	 System.err.println ("   <timeOut>: the number of seconds that sendpraat will wait for an answer");
	 System.err.println ("              before writing an error message. A <timeOut> of 0 means that");
	 System.err.println ("              the message will be sent asynchronously, i.e., that sendpraat");
	 System.err.println ("              will return immediately without issuing any error message.");
      }
      System.err.println ("");
      System.err.println ("Usage:");
      System.err.println ("   Each line is a separate argument.");
      System.err.println ("   Lines that contain spaces should be put inside double quotes.");
      System.err.println ("");
      System.err.println ("Examples:");
      System.err.println ("");
      if (win)
      {
	 System.err.println ("   sendpraat praat Quit");
      }
      else
      {
	 System.err.println ("   sendpraat 0 praat Quit");
      }
      System.err.println ("      Causes the program \"praat\" to quit (gracefully).");
      System.err.println ("      This works because \"Quit\" is a fixed command in Praat's Control menu.");
      if (!win)
      {
	 System.err.println ("      Sendpraat will return immediately.");
      }
      System.err.println ("");
      if (win)
      {
	 System.err.println ("   sendpraat praat \"Play reverse\"");
      }
      else
      {
	 System.err.println ("   sendpraat 1000 praat \"Play reverse\"");
      }
      System.err.println ("      Causes the program \"praat\", which can play sounds,");
      System.err.println ("      to play the selected Sound objects backwards.");
      System.err.println ("      This works because \"Play reverse\" is an action command");
      System.err.println ("      that becomes available in Praat's dynamic menu when Sounds are selected.");
      if (!win)
      {
	 System.err.println ("      Sendpraat will allow \"praat\" at most 1000 seconds to perform this.");
      }
      System.err.println ("");
      if (win)
      {
	 System.err.println ("   sendpraat praat \"execute C:\\MyDocuments\\MyScript.praat\"");
      }
      else if (mac)
      {
	 System.err.println ("   sendpraat praat \"execute ~/MyResearch/MyProject/MyScript.praat\"");
      }
      System.err.println ("      Causes the program \"praat\" to execute a script.");
      if (!win)
      {
	 System.err.println ("      Sendpraat will allow \"praat\" at most 10 seconds (the default time out).");
      }
      System.err.println ("");
      System.err.println ("   sendpraat als \"for i from 1 to 5\" \"Draw circle... 0.5 0.5 0.1*i\" \"endfor\"");
      System.err.println ("      Causes the program \"als\" to draw five concentric circles");
      System.err.println ("      into its Picture window.");
   } // end of printUsage()

   /**
    * Send a message to praat.
    * @param programName is the name of the program that receives the message.
    *    This program must have been built with the Praat shell (the most common such programs are Praat and ALS).
    *    On Unix, the program name is usually all lower case, e.g. "praat" or "als", or the name of any other program.
    *    On Windows, you can use either "Praat", "praat", or the name of any other program.
    *    On Macintosh, 'programName' must be "Praat", "praat", "ALS", or the Macintosh signature of any other program.
    * @param timeOut is the time (in seconds) after which sendpraat will return with a time-out error message
    *    if the receiving program sends no notification of completion.
    *    On Unix and Macintosh, the message is sent asynchronously if 'timeOut' is 0;
    *    this means that sendpraat will return OK (NULL) without waiting for the receiving program
    *    to handle the message.
    *    On Windows, the time out is ignored.
    * @param text contains the contents of the Praat script to be sent to the receiving program.
    */
   public String sendpraatJava (String programName, long timeOut, String text)
   {
      return sendpraatJava (null, programName, timeOut, text);
   }
   
   /**
    * Sends a message to praat using an array of strings (e.g. from the command line) as arguments.
    * @param argv
    * @return The response
    */
   public String sendpraat(String[] argv)
   {
      int iarg = 0;
      int timeOut = 0;
      if (!win)
      {
	 /*
	  * Get time-out.
	  */
	 if (Character.isDigit (argv [iarg].charAt(0))) timeOut = Integer.parseInt((argv [iarg ++]));
      }
      
      /*
       * Get program name.
       */
      if (iarg == argv.length) 
      {
	 System.err.println ("sendpraat: missing program name. Type \"sendpraat --usage\" to get help.");
	 return "missing program name";
      }
      String programName = argv [iarg ++];
      
      /*
       * Create the message string.
       */
      int line = 0;
      int length = 0;
      for (line = iarg; line < argv.length; line ++) length += argv[line].length() + 1;
      length --;
      String message = "";
      for (line = iarg; line < argv.length; line ++) 
      {
	 message += argv [line];
	 if (line < argv.length - 1) message += "\n";
      }
      
      /*
       * Send message.
       */
      String result = sendpraat (programName, timeOut, message);
      if (result != null)
	 // && 
	 //  (result.startsWith("Program praat not running")
	 //   || result.startsWith("Could not send message")))
      {
	 log("Praat not running - will try to start it and send again...");
	 // try starting praat first
	 startPraat();
	 // then send again
	 result = sendpraat (programName, timeOut, message);
      }
      return result;
   } // end of sendpraat()

   /**
    * Send two messages to praat.
    * @param programName is the name of the program that receives the message.
    *    This program must have been built with the Praat shell (the most common such programs are Praat and ALS).
    *    On Unix, the program name is usually all lower case, e.g. "praat" or "als", or the name of any other program.
    *    On Windows, you can use either "Praat", "praat", or the name of any other program.
    *    On Macintosh, 'programName' must be "Praat", "praat", "ALS", or the Macintosh signature of any other program.
    * @param text contains the contents of the Praat script to be sent to the receiving program.
    */
   public String sendpraat(String programName, String text)
   {
      return sendpraat(programName, 0, text);
   }
   
   /**
    * Send a message to praat.
    * This is a partial port of the c code.  Currently only implements 
    * communication with signals, and uses a Linux-specific method to obtain
    * the current PID.
    * @param display is the Display pointer, which will be available if you call sendpraat from an X Window program.
    *    If 'display' is NULL, sendpraat will open the display by itself, and close it after use.
    *    On Windows and Macintosh, sendpraat ignores the 'display' parameter.
    * @param programName is the name of the program that receives the message.
    *    This program must have been built with the Praat shell (the most common such programs are Praat and ALS).
    *    On Unix, the program name is usually all lower case, e.g. "praat" or "als", or the name of any other program.
    *    On Windows, you can use either "Praat", "praat", or the name of any other program.
    *    On Macintosh, 'programName' must be "Praat", "praat", "ALS", or the Macintosh signature of any other program.
    * @param timeOut is the time (in seconds) after which sendpraat will return with a time-out error message
    *    if the receiving program sends no notification of completion.
    *    On Unix and Macintosh, the message is sent asynchronously if 'timeOut' is 0;
    *    this means that sendpraat will return OK (NULL) without waiting for the receiving program
    *    to handle the message.
    *    On Windows, the time out is ignored.
    * @param text contains the contents of the Praat script to be sent to the receiving program.
    */
   public String sendpraatJava (Object display, String programName, long timeOut, String text)
   {
	String nativeProgramName = "";
	//#if xwin
	String home = "";
	String pidFileName = "";
	String messageFileName = "";
	File pidFile = null;
	long pid = 0;
	long wid = 0;
	/*
	 * Clean up from an earlier call.
	 */
	errorMessage = null;
	/*
	 * Handle case differences.
	 */
	nativeProgramName = programName;
	if (xwin)
	{
	   nativeProgramName
	      = "" + Character.toLowerCase(nativeProgramName.charAt(0))
	      + nativeProgramName.substring(1);
	}
	else
	{
	   nativeProgramName
	      = "" + Character.toUpperCase(nativeProgramName.charAt(0))
	      + nativeProgramName.substring(1);
	}
	/*
	 * If the text is going to be sent in a file, create its name.
	 * The file is going to be written into the preferences directory of the receiving program.
	 * On X Window, the name will be something like /home/jane/.praat-dir/message.
	 * On Windows, the name will be something like C:\Documents and Settings\Jane\Praat\Message.txt,
	 * or C:\Windows\Praat\Message.txt on older systems.
	 * On Macintosh, the text is NOT going to be sent in a file.
	 */
	if (xwin)
	{
	   if (sUserHome == null) 
	   {
	      errorMessage = "HOME environment variable not set.";
	      return errorMessage;
	   }
	   messageFileName = sUserHome 
	      + File.separator + "." + programName + "-dir" 
	      + File.separator + "message";
	}
	else if (win)
	{	   
	   if (sUserHome == null) 
	   {
	      errorMessage = "user.home property not available.";
	      return errorMessage;
	   }
	   messageFileName = sUserHome 
	      + File.separator + programName 
	      + File.separator + "Message.txt";
	}
	/*
	 * Get the PID if possible
	 */
	if (xwin)
	{
	   try
	   {
	      // this works on linux...
	      iPid = Integer.parseInt( ( new File("/proc/self"))
	      .getCanonicalFile().getName() );
	   }
	   catch(Exception x)
	   {
	      errorMessage = "Cannot get process ID (" + x + ").\n";
	      return errorMessage;
	   }
	}
	
	/*
	 * Write the message file (Unix and Windows only).
	 */
	if (xwin || win)
	{
	   File messageFile = new File(messageFileName);
	   try
	   {
	      FileWriter wMessageFile = new FileWriter(messageFile);
	      if (xwin)
	      {
		 if (timeOut > 0)
		 {
		    wMessageFile.write("#" + iPid + "\n");   /* Write own process ID for callback. */
		 }
	      }
	      wMessageFile.write(text);
	      wMessageFile.close();
	   }
	   catch (IOException x)
	   {
	      errorMessage = "Cannot create message file \"" 
		 + messageFileName + "\" (" + x + ").\n";
	      return errorMessage;
	   }
	}
	/*
	 * Where shall we send the message?
	 */
	if (xwin)
	{
	   /*
	    * Get the process ID and the window ID of a running Praat-shell program.
	    */
	   pidFileName = sUserHome 
	      + File.separator + "." + programName + "-dir"
	      + File.separator + "pid";
	   pidFile = new File(pidFileName);
	   if (!pidFile.exists())
	   {
	      errorMessage = "Program " + programName 
		 + " not running (or a version older than 3.6).";
	      return errorMessage;
	   }
	   try
	   {
	      BufferedReader rPidFile = new BufferedReader(new FileReader(pidFile));
	      String sLine = rPidFile.readLine();
	      int iSpace = sLine.indexOf(' ');
	      pid = Long.parseLong(sLine.substring(0, iSpace));
	      wid = Long.parseLong(sLine.substring(iSpace+1));	      
	      rPidFile.close();
	   }
	   catch (IOException x)
	   {
	      errorMessage = "Program " + programName 
		 + " not running or disk has been full: " + x;
	      return errorMessage;
	   }
	}
	else if (win)
	{
	   /*
	    * Get the window handle of the "Objects" window of a running Praat-shell program.
	    */
	   errorMessage = "Please download sendpraat and save it to the same folder where Praat is installed: http://www.fon.hum.uva.nl/praat/sendpraat.html";
	   return errorMessage;	   	   
	}
	else if (mac)
	{
	   /*
	    * Convert the program name to a Macintosh signature.
	    * I know of no system routine for this, so I'll just translate the two most common names:
	    */
	   errorMessage = "Please download sendpraat and save it to the same folder where Praat is installed: http://www.fon.hum.uva.nl/praat/sendpraat.html";
	   return errorMessage;
	}
	else
	{
	   errorMessage = "Please download sendpraat and save it to the same folder where Praat is installed: http://www.fon.hum.uva.nl/praat/sendpraat.html";
	   return errorMessage;
	}
	/*
	 * Send the message.
	 */
 	if (xwin)
	{
	   /*
	    * Use interrupt mechanism.
	    */
	   try
	   {
	      java.lang.Runtime.getRuntime().exec("kill -s SIGUSR1 " + pid);
	   }
	   catch (Exception x)
	   {
	      errorMessage = "Cannot send message to " + programName 
		 + " (process " + pid + "). "
		 + "The program " + programName 
		 + " may have been started by a different user, "
		 + "or may have crashed: " + x;
	      return errorMessage;
	   }
	} 		
	/*
	 * Notify the caller of success (NULL pointer) or failure (string with an error message).
	 */
	return errorMessage;
   }
   public void handleCompletion (int message) 
   { 
      //?(void) message; 
   }
   public void handleTimeOut (int message) 
   {
      logError("Timed out after " + theTimeOut + " seconds.");
   }
   
   private synchronized native void SendMessage(long HWND, int message, long wParam, int lParam) throws UnsatisfiedLinkError;
} // end of class SendPraat
