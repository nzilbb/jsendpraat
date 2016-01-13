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

package nzilbb.http;

import java.net.*;
import java.io.*;
import java.util.*;
import java.text.*;
import javax.swing.*;

/**
 * Downloads a file from the server to the client, if it hasn't already been downloaded.
 * @author Robert Fromont robert.fromont@canterbury.ac.nz
 */
public class FileDownloader
   extends Thread
{
   // shared
   static private TemporaryFileStore filesAlreadyDownloaded = new TemporaryFileStore();

   /** Maps of successful authorizations, keyed on host name */
   static private HashMap<String,Vector<String>> hostAuthorizations = new HashMap<String,Vector<String>>();

   // Attributes:
   private URL url_;
   private File localFile_;
   private IProgressIndicator pb_;
   private final int chunkSize = 10000;
   private IMessageHandler messageHandler_;
      
   /**
    * Whether to cache files, or download them every time.
    */
   private boolean bCache = true;
   /**
    * Cache accessor 
    * @return Whether to cache files, or download them every time.
    */
   public boolean getCache() { return bCache; }
   /**
    * Cache mutator
    * @param bNewCache Whether to cache files, or download them every time.
    */
   public void setCache(boolean bNewCache) { bCache = bNewCache; }

   /**
    * Default suffix for temporary files
    */
   private String sDefaultSuffix = ".wav";
   /**
    * DefaultSuffix accessor 
    * @return Default suffix for temporary files
    */
   public String getDefaultSuffix() { return sDefaultSuffix; }
   /**
    * DefaultSuffix mutator
    * @param sNewDefaultSuffix Default suffix for temporary files
    */
   public void setDefaultSuffix(String sNewDefaultSuffix) { sDefaultSuffix = sNewDefaultSuffix; }
   
   /**
    * Whether file extensions are to be made lowercase or not.
    * @see #getDowncaseExtensions()
    * @see #setDowncaseExtensions(boolean)
    */
   protected boolean bDowncaseExtensions = false;
   /**
    * Getter for {@link #bDowncaseExtensions}: Whether file extensions are to be made lowercase or not.
    * @return Whether file extensions are to be made lowercase or not.
    */
   public boolean getDowncaseExtensions() { return bDowncaseExtensions; }
   /**
    * Setter for {@link #bDowncaseExtensions}: Whether file extensions are to be made lowercase or not.
    * @param bNewDowncaseExtensions Whether file extensions are to be made lowercase or not.
    */
   public void setDowncaseExtensions(boolean bNewDowncaseExtensions) { bDowncaseExtensions = bNewDowncaseExtensions; }

   /**
    * Whether the downloader is currently downloading or not
    * @see #getDownloading()
    */
   protected boolean bDownloading = false;
   /**
    * Getter for {@link #bDownloading}: Whether the downloader is currently downloading or not
    * @return Whether the downloader is currently downloading or not
    */
   public boolean getDownloading() { return bDownloading; }
   
   /**
    * Last error that occurred.
    * @see #getLastError()
    * @see #setLastError(String)
    */
   protected String lastError;
   /**
    * Getter for {@link #lastError}: Last error that occurred.
    * @return Last error that occurred.
    */
   public String getLastError() { return lastError; }
   /**
    * Setter for {@link #lastError}: Last error that occurred.
    * @param newLastError Last error that occurred.
    */
   public void setLastError(String newLastError) { lastError = newLastError; }
      
   /**
    * Constructor
    */
   public FileDownloader(URL url)
   {
      url_ = url;
   } // end of constructor

   /**
    * Constructor
    */
   public FileDownloader(URL url, final JProgressBar pb, IMessageHandler messageHandler)
   {
      url_ = url;
      pb.setStringPainted(true);
      pb_ = new IProgressIndicator()
	 {
	    public void setMaximum(int max) { pb.setMaximum(max); }
	    public void setValue(int progress) { pb.setValue(progress); }
	    public void setString(String s) { pb.setString(s); }
	    public int getMaximum() { return pb.getMaximum(); }
	    public int getValue() { return pb.getValue(); }
	    public String getString() { return pb.getString(); }
	 };
      messageHandler_ = messageHandler;
   } // end of constructor

   /**
    * Constructor
    */
   public FileDownloader(URL url, IProgressIndicator pb, IMessageHandler messageHandler)
   {
      url_ = url;
      pb_ = pb;
      messageHandler_ = messageHandler;
   } // end of constructor
   
   /**
    * Returns a (possibly empty) list of authorizations for a given URL.
    * @param url
    * @return A (possibly empty) list of authorizations for a given URL.
    */
   public static Vector<String> getAuthorizations(URL url)
   {
      if (hostAuthorizations.containsKey(url.getHost()))
      {
	 return hostAuthorizations.get(url.getHost());
      }
      else
      {
	 return new Vector<String>();
      }
   } // end of getAuthorizations()

   /**
    * Gets the local files version of a given URL.
    * @param url
    * @return The local file containing the content of the given URL, or null if it has not been downloaded.
    */
   public static File getDownloadedFile(URL url)
   {
      return filesAlreadyDownloaded.getFile(url.toString());
   } // end of getDownloadedFile()
      
   /**
    * Start the thread
    */
   public void start()
   {
      // this here to ensure that if it's checked below before the run()
      // method is called, it will correctly return true
      bDownloading = true;
      super.start();
   } // end of start()
      
   /**
    * Thread run.
    */
   public void run()
   {
      bDownloading = true;
      try
      {
	 if (pb_ != null)
	 {
	    pb_.setValue(0);
	 }
	    
	 synchronized (this)
	 {
	    try
	    {
	       // if it's a local file, don't bother downloading
	       if (url_.getProtocol().equals("file"))
	       {
		  localFile_ = new File(url_.getFile());
		  if (pb_ != null)
		  {
		     pb_.setValue(pb_.getMaximum());
		     pb_.setString(localFile_.getPath());
		  }
		  return;
	       }
		  
	       if (bCache)
	       {
		  // if we've already downloaded it, don't do it again
		  localFile_ = filesAlreadyDownloaded.getFile(url_.toString());
	       }
	       if (localFile_ == null || !localFile_.exists())
	       {		     
		  URLConnection cnxn = openConnection(url_);
		  if (cnxn == null)
		  {
		     if (lastError != null)
		     {
			throw new Exception(lastError);
		     }
		     else
		     {
			throw new Exception("Could not retrieve: " + url_);
		     }
		  }
		  int contentLength = cnxn.getContentLength();
		  if (contentLength < 0) contentLength = 1000000;
		  if (pb_ != null)
		  {
		     pb_.setMaximum(contentLength);
		     pb_.setString(url_.toString());
		  }
		     
		  String strSuffix = null;
		  try
		  {
		     // if there's a filename specified
		     String sContentDisposition 
			= cnxn.getHeaderField("Content-Disposition");
		     if (sContentDisposition != null)
		     {
			MessageFormat msgContentDisposition 
			   = new MessageFormat("attachment; filename={0}");
			try
			{
			   Object[] aFileName = msgContentDisposition.parse(
			      sContentDisposition);
			   strSuffix = "-" + aFileName[0].toString()
			      // replace any eclosing quotes
			      .replaceAll("\"","");
			}
			catch(Throwable t) {}
		     }
		     if (strSuffix == null)
		     {
			// deduce suffix from file name
			int iLastSlash = url_.getPath().lastIndexOf('/');
			if (iLastSlash >= 0)
			{
			   strSuffix 
			      = "-" + url_.getPath().substring(iLastSlash+1);
			}
		     }
		     if (strSuffix == null)
		     {
			// deduce suffix from mime type 
			strSuffix = "." + cnxn.getContentType().substring(
			   cnxn.getContentType().lastIndexOf("/") + 1);
			// if it's x-wav, make it wav, etc.
			strSuffix = "." + strSuffix.substring(
			   strSuffix.lastIndexOf("-") + 1);
		     }
		  }
		  catch(Exception exception)
		  {}
		  if (strSuffix == null) strSuffix = sDefaultSuffix;
		  // replace any URL encoding '%'s
		  strSuffix = URLDecoder.decode(strSuffix, "UTF-8");
		  if (getDowncaseExtensions())
		  {
		     int iExtensionStarts = strSuffix.lastIndexOf('.');
		     if (iExtensionStarts > 0)
		     {
			strSuffix = strSuffix.substring(0, iExtensionStarts)
			   + strSuffix.substring(iExtensionStarts).toLowerCase();
		     }
		  }
		     
		  // create a temporary file we know will be deleted
		  localFile_ = filesAlreadyDownloaded.createTempFile(
		     url_.toString(), "000", strSuffix);
		  localFile_.deleteOnExit();		  
		     
		  InputStream is = cnxn.getInputStream();
		  FileOutputStream os = new FileOutputStream(localFile_);
		  byte [] chunk = new byte[chunkSize];
		  for(int numBytes = is.read(chunk);
		      numBytes >= 0; 
		      numBytes = is.read(chunk))
		  {
		     os.write(chunk, 0, numBytes);
		     if (pb_ != null) pb_.setValue(pb_.getValue() + numBytes);
		  } // next chunk
		  os.close();
		  is.close();
	       } // not already downloaded
		  
	       if (pb_ != null)
	       {
		  pb_.setValue(pb_.getMaximum());
		  pb_.setString(localFile_.getPath());
	       }
	    }
	    catch(Exception exception)
	    {
	       if (messageHandler_ != null) messageHandler_.error(exception.getMessage());
	       setLastError(exception.getMessage());
	       if (pb_ != null)
	       {
		  pb_.setString(exception.getMessage());
	       }
	       if (localFile_ != null) localFile_.delete();
	    }
	    finally
	    {
	       notifyAll();
	    }
	 } // synchronized
      }
      finally
      {
	 bDownloading = false;
      }
   } // end of run()
   
   /**
    * Opens a connection to the given URL.  If the connection requires a username/password, the user is asked to provide it.
    * @param url An HTTP URL.
    * @return A connection to the URL, or null if the user cancels out of entering a username/password.
    * @throws Exception
    */
   public URLConnection openConnection(URL url)
      throws Exception
   {
      HttpURLConnection connection = (HttpURLConnection)url.openConnection();
      try
      {
	 connection.getInputStream(); // throws exception if unauthorized
	 return connection;
      }
      catch (Exception x)
      {
	 connection.disconnect();
	 if (connection.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED)
	 {
	    // first, see if we already have a valid authorization for this host
	    for (String authorization : getAuthorizations(url))
	    {
	       connection = (HttpURLConnection)url.openConnection();
	       try
	       {
		  connection.setRequestProperty("Authorization", authorization);
		  connection.getInputStream(); // throws exception if unauthorized
		  // if we got this far, it worked!
		  return connection;
	       }
	       catch(Exception exception)
	       {
		  connection.disconnect();
	       }
	    }
	    
	    // need a new authorization
	    JPasswordField txtPassword = new JPasswordField();
	    JTextField txtUsername = new JTextField();
	    JButton btnOK = new JButton("Login");
	    final JDialog dlg = new JDialog((java.awt.Frame)null, "Login");
	    dlg.setBackground(java.awt.Color.WHITE);
	    dlg.getContentPane().setBackground(java.awt.Color.WHITE);      
	    dlg.getContentPane().setLayout(new java.awt.GridLayout(3,2));
	    ((JPanel)dlg.getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
	    dlg.getContentPane().add(new JLabel("Username:"));
	    dlg.getContentPane().add(txtUsername);
	    dlg.getContentPane().add(new JLabel("Password:"));
	    dlg.getContentPane().add(txtPassword);
	    dlg.getContentPane().add(new JLabel(""));
	    dlg.getContentPane().add(btnOK);
	    java.awt.event.ActionListener login = new java.awt.event.ActionListener() 
	       {
		  public void actionPerformed(java.awt.event.ActionEvent e) 
		  {
		     dlg.setVisible(false);
		  }
	       };
	    btnOK.addActionListener(login);
	    txtUsername.addActionListener(login);
	    txtPassword.addActionListener(login);
	    dlg.setDefaultCloseOperation(javax.swing.WindowConstants.HIDE_ON_CLOSE);
	    dlg.setModal(true);
	    dlg.setSize(400,130);

	    // loop until a username/password works
	    String authorization = null;
	    String username = null;
	    while (authorization == null)
	    {
	       txtPassword.setText("");
	       dlg.setVisible(true);
	       String password = new String(txtPassword.getPassword());
	       if (password.length() == 0) break;
	       username = txtUsername.getText();
	       authorization = "Basic " 
		  + new sun.misc.BASE64Encoder().encode((username+":"+password).getBytes());
	       connection = (HttpURLConnection)url.openConnection();
	       connection.setRequestProperty("Authorization", authorization);
	       try 
	       { 
		  connection.getInputStream(); // maybe throws exception

		  // if we get this far, it worked, so remember the authorization for next time
		  if (!hostAuthorizations.containsKey(url.getHost()))
		  {
		     hostAuthorizations.put(url.getHost(), new Vector<String>());
		  }
		  hostAuthorizations.get(url.getHost()).add(authorization);		  

		  return connection;
	       }
	       catch (Exception xx)
	       {
		  if (connection.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED)
		  {
		     connection.disconnect();
		     authorization = null;
		  }
	       }
	    } // next attempt
	 } // HTTP_UNAUTHORIZED returned
	 else
	 {
	    setLastError(x.getMessage());
	 }
      } // exception getting content
      return null;
   } // end of openConnection()

   /**
    * Access to the local copy of the file.
    */
   public File getLocalFile()
   {
      return localFile_;
   } // end of getLocalFile()

   /**
    * Returns the folder being used to store download files
    * @return The directory used to store downloaded files
    */
   public File getDownloadDir()
   {
      return filesAlreadyDownloaded.getSubdirectory();
   } // end of getDownloadDir()

} // end of class FileDownloader
