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
package nzilbb.jsendpraat.chrome;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.URL;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.ActionEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import nzilbb.util.UtilityApp;
import nzilbb.util.UtilityDescription;
import nzilbb.util.Switch;
 // from jRegistryKey.jar
import ca.beq.util.win32.registry.RegistryKey;
import ca.beq.util.win32.registry.RegistryValue;
import ca.beq.util.win32.registry.RootKey;

/**
 * Installer for the jsendpraat Chrome Native Messaging host.
 * @author Robert Fromont robert.fromont@canterbury.ac.nz
 */
@SuppressWarnings("serial")
@UtilityDescription("Installer for Browser/Praat integration Messaging Host")
public class HostInstaller
  extends UtilityApp
{
   static
   {
      try 
      {
	 UIManager.setLookAndFeel(
	    UIManager.getSystemLookAndFeelClassName());
      } 
      catch (Exception e) 
      {
	 System.err.println("Could not set Look and Feel: " + e.getMessage());
      }
   }
   
   // Attributes:   

   /** Progress bar */
   JProgressBar progress = new JProgressBar();

   /** Install button */
   JButton install = new JButton("Install");
   /** Cancel button */
   JButton cancel = new JButton("Cancel");

   /** Operating system */
   protected enum OS { Other, Linux, Windows, Mac };
   protected OS os = OS.Other;
   
   // Methods:
   
   /**
    * Default constructor.
    */
   public HostInstaller()
   {
      setDefaultWindowTitle("Install Praat Messaging Host");
      setDefaultWidth(600);
      setDefaultHeight(170);
   } // end of constructor

   /** Command-line program entrypoint */
   public static void main(String argv[])
   {
      new HostInstaller().mainRun(argv);
   }
   
   /** Applet initialisation */
   public void init()
   {
      interpretAppletParameters();

      if (frame_ != null) frame_.addWindowListener(new WindowAdapter()
	 {
	    public void windowClosing(WindowEvent e) { System.exit(0); }
	 });

      GridLayout layout = new GridLayout(4,1);
      setLayout(layout);
      setBackground(Color.WHITE);
      getContentPane().setBackground(Color.WHITE);      
      ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

      add(new JLabel("This will install support for interacting with Praat directly from your web browser."));
      add(new JLabel("Please click 'Install' to continue."));
      add(progress);
      progress.setStringPainted(true);

      JPanel buttons = new JPanel(new FlowLayout());
      buttons.setBackground(Color.WHITE);
      install.addActionListener(new ActionListener()
	 {
	    public void actionPerformed(ActionEvent event) { install(); }
	 });
      buttons.add(install);
      cancel.addActionListener(new ActionListener()
	 {
	    public void actionPerformed(ActionEvent event) { System.exit(0); }
	 });
      buttons.add(cancel);

      add(buttons);
   }

   /** Execution */
   public void start()
   {
   }

   /** Installation */
   public void install()
   {
      progress.setMaximum(7);
      String osName = java.lang.System.getProperty("os.name");
      String osArch = java.lang.System.getProperty("os.arch");
      int firstSpace = osName.indexOf(' ');
      if (firstSpace > 0) osName = osName.substring(0, firstSpace);

      if (osName.startsWith("Linux"))
      {
	 os = OS.Linux;
      }
      else if (osName.startsWith("Windows"))
      {
	 os = OS.Windows;
      }
      else if (osName.startsWith("Mac"))
      {
	 os = OS.Mac;
      }
      message("Operating system: " + os);

      progress.setValue(1);

      String userHome = ".";
      try { userHome = java.lang.System.getProperty("user.home"); }
      catch(Exception exception) { message("Can't get user.home: " + exception); }
      message("Home directory: " + userHome);

      progress.setValue(2);

      install.setEnabled(false);
      try
      {
	 File homeDir = new File(userHome);
	 
	 File binDir = new File(homeDir, "jsendpraat");
	 binDir.mkdir();

	 File manifestDirChrome = binDir;
	 boolean chromeInstalled = false;
	 File manifestDirFirefox = binDir;	
	 boolean firefoxInstalled = false;
	 switch (os)
	 {
	    case Linux: 
	    {
	       // chrome
	       File configDir = new File(homeDir, ".config");
	       File browserConfigDir = new File(configDir, "chromium");
	       if (!browserConfigDir.exists())
	       {
		  browserConfigDir = new File(configDir, "google-chrome");
		  if (!browserConfigDir.exists())
		  {
		     message(
			"Neither Google Chrome nor Chromium appear to be installed; " 
			+ browserConfigDir.getPath() + " not found.");
		  }
	       }
	       chromeInstalled = browserConfigDir.exists();
	       manifestDirChrome = new File(browserConfigDir, "NativeMessagingHosts");

	       // firefox
	       File mozillaDir = new File(homeDir, ".mozilla");
	       firefoxInstalled = mozillaDir.exists();
	       manifestDirFirefox = new File(mozillaDir, "native-messaging-hosts");
	       break;
	    }
	    case Windows:
	    {
	       // put everything in APPDATA
	       String APPDATA = System.getenv("APPDATA");
	       if (APPDATA != null)
	       {
		  binDir = new File(System.getenv("APPDATA"));
	       }
	       else
	       {
		  binDir = new File(homeDir, "AppData");
		  binDir = new File(binDir, "Roaming");
	       }
	       binDir = new File(binDir, "jsendpraat");
	       binDir.mkdir();
	       manifestDirChrome = binDir;	
	       manifestDirFirefox = manifestDirChrome;
	       chromeInstalled = true;
	       firefoxInstalled = true;
	       break;
	    }
	    case Mac:
	    {
	       // chrome
	       File libraryDir = new File(homeDir, "Library");
	       File applicationSupportDir = new File(libraryDir, "Application Support");
	       File browserConfigDir = new File(applicationSupportDir, "Chromium");
	       if (!browserConfigDir.exists())
	       {
		  File googleDir = new File(applicationSupportDir, "Google");
		  browserConfigDir = new File(googleDir, "Chrome");
		  if (!browserConfigDir.exists())
		  {
		     message(
			"Neither Google Chrome nor Chromium appear to be installed; " 
			+ browserConfigDir.getPath() + " not found.");
		  }
	       }
	       chromeInstalled = browserConfigDir.exists();
	       manifestDirChrome = new File(browserConfigDir, "NativeMessagingHosts");

	       // firefox
	       File mozillaConfigDir = new File(applicationSupportDir, "Mozilla");
	       firefoxInstalled = mozillaConfigDir.exists();
	       manifestDirFirefox = new File(browserConfigDir, "NativeMessagingHosts");
	       break;
	    }
	    default:
	    {
	       throw new Exception("Sorry, your operating system is not supported: " + osName);
	    }
	 }
	 message("Installing chrome manifest in: " + manifestDirChrome.getPath());
	 if (chromeInstalled && !manifestDirChrome.exists()) 
	 {
	    if (!manifestDirChrome.mkdir())
	    {
	       error("Could not create manifest directory: " + manifestDirChrome.getPath());
	    }
	 }
	 message("Installing firefox manifest in: " + manifestDirFirefox.getPath());
	 if (firefoxInstalled && !manifestDirFirefox.exists()) 
	 {
	    if (!manifestDirFirefox.mkdir())
	    {
	       error("Could not create manifest directory: " + manifestDirFirefox.getPath());
	    }
	 }
	 message("Installing application in: " + binDir.getPath());
	 progress.setValue(3);

	 // extract executable jar
	 String hostJar = "jsendpraat.jar";
	 message("Extracting: " + hostJar);
	 File hostJarFile = new File(binDir, hostJar);
	 URL hostJarUrl = getClass().getResource("/"+hostJar);
	 InputStream jarStream = hostJarUrl.openStream();
	 FileOutputStream outStream = new FileOutputStream(hostJarFile);
	 byte[] buffer = new byte[1024];
	 int bytesRead = jarStream.read(buffer);
	 while(bytesRead >= 0)
	 {
	    outStream.write(buffer, 0, bytesRead);
	    bytesRead = jarStream.read(buffer);
	 } // next chunk of data
	 jarStream.close();
	 outStream.close();

	 progress.setValue(4);

	 // extract/update execution script
	 String hostScript = os == OS.Windows?"jsendpraat.bat":"jsendpraat.sh";
	 message("Extracting: " + hostScript);
	 File hostScriptFile = new File(binDir, hostScript);
	 URL hostScriptUrl = getClass().getResource("/"+hostScript);
	 BufferedReader hostScriptReader = new BufferedReader(new InputStreamReader(hostScriptUrl.openStream()));
	 PrintWriter hostScriptWriter = new PrintWriter(hostScriptFile);
	 String line = hostScriptReader.readLine();
	 while(line != null)
	 {
	    hostScriptWriter.println(
	       line.replace("${jarpath}", hostJarFile.getPath()));
	    line = hostScriptReader.readLine();
	 } // next line
	 hostScriptReader.close();
	 hostScriptWriter.close();

	 // mark script as executable
	 if (os != OS.Windows)
	 {
	    makeExecutable(hostScriptFile);
	 }
	 progress.setValue(5);

	 if (manifestDirChrome.exists()) // only if Chrome is installed
	 {
	    // extract/update manifest
	    String extension = "nzilbb.jsendpraat.chrome";
	    String manifest = extension+".json";
	    message("Extracting: " + manifest);
	    File manifestFile = new File(manifestDirChrome, manifest);
	    URL manifestUrl = getClass().getResource("/"+manifest);
	    BufferedReader manifestReader = new BufferedReader(new InputStreamReader(manifestUrl.openStream()));
	    PrintWriter manifestWriter = new PrintWriter(manifestFile);
	    line = manifestReader.readLine();
	    while(line != null)
	    {
	       // skip the allowed_extensions line, which is for Firefox
	       if (!line.matches(".*allowed_extensions.*"))
	       {
		  manifestWriter.println(line.replace("${exepath}", hostScriptFile.getPath().replace("\\","\\\\")));
	       }
	       line = manifestReader.readLine();
	    } // next line
	    manifestReader.close();
	    manifestWriter.close();
	    
	    if (os == OS.Windows)
	    {
	       progress.setValue(6);
	       // we need to add an entry to registry
	       try
	       {
		  String dll = "jRegistryKey.dll";
		  File dllFile = File.createTempFile("install-jsendpraat.", "."+dll);
		  dllFile.deleteOnExit();
		  message("Extracting: " + dllFile.getPath());
		  URL dllUrl = getClass().getResource("/"+dll);
		  jarStream = dllUrl.openStream();
		  outStream = new FileOutputStream(dllFile);
		  buffer = new byte[1024];
		  bytesRead = jarStream.read(buffer);
		  while(bytesRead >= 0)
		  {
		     outStream.write(buffer, 0, bytesRead);
		     bytesRead = jarStream.read(buffer);
		  } // next chunk of data
		  jarStream.close();
		  outStream.close();
		  System.load(dllFile.getPath());
		  
		  RegistryKey r = new RegistryKey(
		     RootKey.HKEY_CURRENT_USER,
		     "Software\\Google\\Chrome\\NativeMessagingHosts\\" + extension);
		  if (!r.exists())
		  {
		     message("Windows: creating registry key for " + extension);
		     r.create();
		  }
		  message("Windows: setting registry value to " + manifestFile.getPath());
		  r.setValue(new RegistryValue(manifestFile.getPath()));
	       }
	       catch (Throwable t)
	       {
		  message(t.getMessage());
		  message("Could not set registry directly, falling back to .reg file...");
		  
		  // write a registry file
		  File regFile = new File(manifestDirChrome, "jsendpraat.reg");
		  PrintWriter regWriter = new PrintWriter(regFile);
		  regWriter.println("Windows Registry Editor Version 5.00");
		  regWriter.println();
		  regWriter.println("[HKEY_CURRENT_USER\\Software\\Google\\Chrome\\NativeMessagingHosts\\nzilbb.jsendpraat.chrome]");
		  regWriter.println("@=\""+manifestFile.getPath().replace("\\","\\\\")+"\"");
		  regWriter.close();
		  
		  // import it with regedit
		  String[] cmd = {"cmd","/c","regedit","-s",regFile.getPath()};
		  Process proc = Runtime.getRuntime().exec(cmd);
		  proc.waitFor();
	       }
	    }
	 } // chrome is installed

	 if (manifestDirFirefox.exists()) // only if Firefox is installed
	 {
	    // extract/update manifest
	    String extension = "nzilbb.jsendpraat.chrome";
	    String manifest = extension+".json";
	    message("Extracting: " + manifest);
	    File manifestFile = new File(manifestDirFirefox, manifest);
	    URL manifestUrl = getClass().getResource("/"+manifest);
	    BufferedReader manifestReader = new BufferedReader(new InputStreamReader(manifestUrl.openStream()));
	    PrintWriter manifestWriter = new PrintWriter(manifestFile);
	    line = manifestReader.readLine();
	    while(line != null)
	    {
	       // skip the allowed_origins line, which is for Chrome
	       if (!line.matches(".*allowed_origins.*"))
	       {
		  manifestWriter.println(line.replace("${exepath}", hostScriptFile.getPath().replace("\\","\\\\")));
	       }
	       line = manifestReader.readLine();
	    } // next line
	    manifestReader.close();
	    manifestWriter.close();
	    
	    if (os == OS.Windows)
	    {
	       progress.setValue(6);
	       // we need to add an entry to registry
	       try
	       {
		  String dll = "jRegistryKey.dll";
		  File dllFile = File.createTempFile("install-jsendpraat.", "."+dll);
		  dllFile.deleteOnExit();
		  message("Extracting: " + dllFile.getPath());
		  URL dllUrl = getClass().getResource("/"+dll);
		  jarStream = dllUrl.openStream();
		  outStream = new FileOutputStream(dllFile);
		  buffer = new byte[1024];
		  bytesRead = jarStream.read(buffer);
		  while(bytesRead >= 0)
		  {
		     outStream.write(buffer, 0, bytesRead);
		     bytesRead = jarStream.read(buffer);
		  } // next chunk of data
		  jarStream.close();
		  outStream.close();
		  System.load(dllFile.getPath());
		  
		  RegistryKey r = new RegistryKey(
		     RootKey.HKEY_CURRENT_USER,
		     "Software\\Mozilla\\NativeMessagingHosts\\" + extension);
		  if (!r.exists())
		  {
		     message("Windows: creating registry key for " + extension);
		     r.create();
		  }
		  message("Windows: setting registry value to " + manifestFile.getPath());
		  r.setValue(new RegistryValue(manifestFile.getPath()));
	       }
	       catch (Throwable t)
	       {
		  message(t.getMessage());
		  message("Could not set registry directly, falling back to .reg file...");
		  
		  // write a registry file
		  File regFile = new File(manifestDirFirefox, "jsendpraat.reg");
		  PrintWriter regWriter = new PrintWriter(regFile);
		  regWriter.println("Windows Registry Editor Version 5.00");
		  regWriter.println();
		  regWriter.println("[HKEY_CURRENT_USER\\Software\\Mozilla\\NativeMessagingHosts\\nzilbb.jsendpraat.chrome]");
		  regWriter.println("@=\""+manifestFile.getPath().replace("\\","\\\\")+"\"");
		  regWriter.close();
		  
		  // import it with regedit
		  String[] cmd = {"cmd","/c","regedit","-s",regFile.getPath()};
		  Process proc = Runtime.getRuntime().exec(cmd);
		  proc.waitFor();
	       }
	    }
	 } // firefox is installed

	 progress.setValue(progress.getMaximum());
	 message("Installation complete.");	 
      }
      catch(Exception x)
      {
	 error(x.getMessage());
      }      
      catch(Throwable t)
      {
	 error(t.toString());
      }      
      cancel.setText("Finish");
   }
   
   /**
    * Makes a file executable.
    * @param f
    */
   public void makeExecutable(File f)
   {
      String[] cmdArray = {"/bin/chmod", "a+x", f.getPath()};
      try
      {
	 Runtime.getRuntime().exec(cmdArray);
      }
      catch(Exception x)
      {
	 message("Could not mark file as executable " + cmdArray[2] + " : " + x);
      }
   } // end of makeExecutable()
         
   /**
    * Displays a status message.
    * @param s Message
    */
   public void message(String s)
   {
      progress.setString(s);
      System.out.println(s);
   } // end of message()
   
   /**
    * Display a popup error.
    * @param s
    */
   public void error(String s)
   {
      message(s);
      JOptionPane.showMessageDialog(frame_, s, "Error", JOptionPane.ERROR_MESSAGE);
   } // end of alert()

} // end of class HostInstaller
