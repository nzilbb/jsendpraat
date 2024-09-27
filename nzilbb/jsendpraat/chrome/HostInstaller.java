//
// Copyright 2015-2024 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.util.Vector;
import java.util.jar.JarFile;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import nzilbb.util.Switch;
import nzilbb.util.UtilityApp;
import nzilbb.util.UtilityDescription;
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
public class HostInstaller extends UtilityApp {
  static {
    try {
      UIManager.setLookAndFeel(
        UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      System.err.println("Could not set Look and Feel: " + e.getMessage());
    }
  }
   
  // Attributes:   

  /** Progress bar */
  JProgressBar progress = new JProgressBar();

  /** Install button */
  JButton install = new JButton("Install");
  /** Uninstall button */
  JButton uninstall = new JButton("Uninstall");
  /** Cancel button */
  JButton cancel = new JButton("Cancel");

  /** Operating system */
  protected enum OS { Other, Linux, Windows, Mac };
  protected OS os = OS.Other;
   
  // Methods:
   
  /**
   * Default constructor.
   */
  public HostInstaller() {
    setDefaultWindowTitle("Install Praat Messaging Host");
    setDefaultWidth(600);
    setDefaultHeight(170);
  } // end of constructor

  /** Command-line program entrypoint */
  public static void main(String argv[]) {
    new HostInstaller().mainRun(argv);
  }
   
  /** Applet initialisation */
  public void init() {
    interpretAppletParameters();

    if (frame_ != null) frame_.addWindowListener(new WindowAdapter() {
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
    install.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent event) { install(); }
      });
    buttons.add(install);
    uninstall.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent event) { uninstall(); }
      });
    buttons.add(uninstall);
    cancel.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent event) { System.exit(0); }
      });
    buttons.add(cancel);

    add(buttons);

    // Report our version if possible - it's a comment on the jar file
    URL url = getClass().getResource(getClass().getSimpleName() + ".class");
    String sUrl = url.toString();
    if (sUrl.startsWith("jar:")) {
      int iUriStart = 4;
      int iUriEnd = sUrl.indexOf("!");
      String sFileUri = sUrl.substring(iUriStart, iUriEnd);
      try {
        File fJar = new File(new URI(sFileUri));
        JarFile jfJar = new JarFile(fJar);
        String comment = jfJar.getComment();
        if (comment != null) System.out.println(comment);
            
      } catch(Exception exception) {
      }
    }
  }

  /** Execution */
  public void start() {
  }

  File binDir = null;
  File manifestDirChrome = null;
  boolean chromeInstalled = false;
  File manifestDirChromium = null;
  boolean chromiumInstalled = false;
  File manifestDirFirefox = null;	
  boolean firefoxInstalled = false;
  
  /** Determine the locations of manifest files. */
  public void determineFileLocations(boolean mkdirs) throws Exception {
    String osName = java.lang.System.getProperty("os.name");
    String osArch = java.lang.System.getProperty("os.arch");
    int firstSpace = osName.indexOf(' ');
    if (firstSpace > 0) osName = osName.substring(0, firstSpace);
     
    if (osName.startsWith("Linux")) {
      os = OS.Linux;
    } else if (osName.startsWith("Windows")) {
      os = OS.Windows;
    } else if (osName.startsWith("Mac")) {
      os = OS.Mac;
    }
    message("Operating system: " + os);
    String userHome = ".";
    try { userHome = java.lang.System.getProperty("user.home"); }
    catch(Exception exception) { message("Can't get user.home: " + exception); }
    message("Home directory: " + userHome);

    File homeDir = new File(userHome);
     
    binDir = new File(homeDir, "jsendpraat");
    if (mkdirs) binDir.mkdir();
     
    manifestDirChrome = binDir;
    chromeInstalled = false;
    manifestDirChromium = binDir;
    chromiumInstalled = false;
    manifestDirFirefox = binDir;	
    boolean firefoxInstalled = false;
    switch (os) {
      case Linux: {
        // chromium
        File configDir = new File(homeDir, ".config");
        File chromiumConfigDir = new File(configDir, "chromium");
        chromiumInstalled = chromiumConfigDir.exists();
        manifestDirChromium = new File(chromiumConfigDir, "NativeMessagingHosts");
         
        // chrome
        File chromeConfigDir = new File(configDir, "google-chrome");
        chromeInstalled = chromeConfigDir.exists();
        manifestDirChrome = new File(chromeConfigDir, "NativeMessagingHosts");
         
        // firefox
        File mozillaDir = new File(homeDir, ".mozilla");
        firefoxInstalled = mozillaDir.exists();
        manifestDirFirefox = new File(mozillaDir, "native-messaging-hosts");
        break;
      } case Windows: {
          // put everything in APPDATA
          String APPDATA = System.getenv("APPDATA");
          if (APPDATA != null) {
            binDir = new File(System.getenv("APPDATA"));
          } else {
            binDir = new File(homeDir, "AppData");
            binDir = new File(binDir, "Roaming");
          }
          binDir = new File(binDir, "jsendpraat");
          manifestDirChrome = binDir;	
          manifestDirFirefox = manifestDirChrome;
          chromeInstalled = true;
          firefoxInstalled = true;
          break;
        } case Mac: {
            // chromium
            File libraryDir = new File(homeDir, "Library");
            File applicationSupportDir = new File(libraryDir, "Application Support");
            File chromiumConfigDir = new File(applicationSupportDir, "Chromium");
            chromeInstalled = chromiumConfigDir.exists();
            manifestDirChrome = new File(chromiumConfigDir, "NativeMessagingHosts");
         
            // chrome
            File googleDir = new File(applicationSupportDir, "Google");
            File chromeConfigDir = new File(googleDir, "Chrome");
            chromeInstalled = chromeConfigDir.exists();
            manifestDirChrome = new File(chromeConfigDir, "NativeMessagingHosts");
         
            // firefox
            File mozillaConfigDir = new File(applicationSupportDir, "Mozilla");
            firefoxInstalled = mozillaConfigDir.exists();
            manifestDirFirefox = new File(mozillaConfigDir, "NativeMessagingHosts");
            break;
          } default: {
              throw new Exception("Sorry, your operating system is not supported: " + osName);
            }
    }
    if (chromiumInstalled) {
      if (!manifestDirChromium.exists()) {
        message("Installing chromium manifest in: " + manifestDirChromium.getPath());
        if (mkdirs && !manifestDirChromium.mkdir()) {
          error("Could not create manifest directory: " + manifestDirChromium.getPath());
        }
      } else {
        message("Chromium manifest directory exists: " + manifestDirChromium.getPath());
      }
    }
    if (chromeInstalled) {
      if(!manifestDirChrome.exists()) {
        message("Installing chrome manifest in: " + manifestDirChrome.getPath());
        if (mkdirs && !manifestDirChrome.mkdir()) {
          error("Could not create manifest directory: " + manifestDirChrome.getPath());
        }
      } else {
        message("Chrome manifest directory exists: " + manifestDirChrome.getPath());
      }
    }
    if (firefoxInstalled) {
      if(!manifestDirFirefox.exists()) {
        message("Installing firefox manifest in: " + manifestDirFirefox.getPath());
        if (!mkdirs && manifestDirFirefox.mkdir()) {
          error("Could not create manifest directory: " + manifestDirFirefox.getPath());
        }
      } else {
        message("Firefox manifest directory exists: " + manifestDirFirefox.getPath());
      }
    }
  }
  
  /** Installation */
  public void install() {
    progress.setMaximum(7);
    progress.setValue(1);

    install.setEnabled(false);
    uninstall.setEnabled(false);
    try {
      determineFileLocations(true);
      message("Installing application in: " + binDir.getPath());
      progress.setValue(2);

      // extract executable jar/exe
      String hostJar = "jsendpraat.jar";
      message("Extracting: " + hostJar);
      File hostJarFile = new File(binDir, hostJar);
      URL hostJarUrl = getClass().getResource("/"+hostJar);
      InputStream jarStream = hostJarUrl.openStream();
      FileOutputStream outStream = new FileOutputStream(hostJarFile);
      byte[] buffer = new byte[1024];
      int bytesRead = jarStream.read(buffer);
      while(bytesRead >= 0) {
        outStream.write(buffer, 0, bytesRead);
        bytesRead = jarStream.read(buffer);
      } // next chunk of data
      jarStream.close();
      outStream.close();

      progress.setValue(3);

      // extract/update execution script
      String hostScript = os == OS.Windows?"jsendpraat.bat":"jsendpraat.sh";
      message("Extracting: " + hostScript);
      File hostScriptFile = new File(binDir, hostScript);
      URL hostScriptUrl = getClass().getResource("/"+hostScript);
      BufferedReader hostScriptReader = new BufferedReader(new InputStreamReader(hostScriptUrl.openStream()));
      PrintWriter hostScriptWriter = new PrintWriter(hostScriptFile);
      String line = hostScriptReader.readLine();
      while(line != null) {
        hostScriptWriter.println(
          line.replace("${jarpath}", hostJarFile.getPath()));
        line = hostScriptReader.readLine();
      } // next line
      hostScriptReader.close();
      hostScriptWriter.close();

      // mark script as executable
      if (os != OS.Windows) {
        makeExecutable(hostScriptFile);
      }
      progress.setValue(4);

      if (manifestDirChrome.exists() || manifestDirChromium.exists()) {
        // first ensure any old manifests are deleted
        String[] otherPossibleOldManifests = {
          "nzilbb.chrome.jsendpraat.json",
          "nzilbb.chrome.jsendpraat-firefox.json",
          "nzilbb.jsendpraat.chrome-firefox.json"
        };
        for (String fileName : otherPossibleOldManifests) {
          File toDelete = new File(manifestDirChrome, fileName);
          if (toDelete.exists()) {
            if (toDelete.delete()) {
              message("Deleted " + toDelete.getPath());
            } else {
              message("Could not delete " + toDelete.getPath());
            }
          }
          toDelete = new File(manifestDirChromium, fileName);
          if (toDelete.exists()) {
            if (toDelete.delete()) {
              message("Deleted " + toDelete.getPath());
            } else {
              message("Could not delete " + toDelete.getPath());
            }
          }
        }
	    	    
        // extract/update manifest
        String extension = "nzilbb.jsendpraat.chrome";
        String manifest = extension+".json";
        File manifestFile = new File(manifestDirChrome, manifest);
        message("Extracting for Chrome/Chromium: " + manifest);
        if (manifestDirChrome.exists()) {
          URL manifestUrl = getClass().getResource("/"+manifest);
          BufferedReader manifestReader = new BufferedReader(new InputStreamReader(manifestUrl.openStream()));
          PrintWriter manifestWriter = new PrintWriter(manifestFile);
          line = manifestReader.readLine();
          while(line != null) {
            // skip the allowed_extensions line, which is for Firefox
            if (!line.matches(".*allowed_extensions.*")) {
              manifestWriter.println(line.replace("${exepath}", hostScriptFile.getPath().replace("\\","\\\\")));
            }
            line = manifestReader.readLine();
          } // next line
          manifestReader.close();
          manifestWriter.close();
        }
        if (manifestDirChromium.exists()) {
          manifestFile = new File(manifestDirChromium, manifest);
          URL manifestUrl = getClass().getResource("/"+manifest);
          BufferedReader manifestReader = new BufferedReader(new InputStreamReader(manifestUrl.openStream()));
          PrintWriter manifestWriter = new PrintWriter(manifestFile);
          line = manifestReader.readLine();
          while(line != null) {
            // skip the allowed_extensions line, which is for Firefox
            if (!line.matches(".*allowed_extensions.*")) {
              manifestWriter.println(line.replace("${exepath}", hostScriptFile.getPath().replace("\\","\\\\")));
            }
            line = manifestReader.readLine();
          } // next line
          manifestReader.close();
          manifestWriter.close();
        }
	    
        if (os == OS.Windows) {
          progress.setValue(5);
          // we need to add an entry to registry
          try {
            String dll = "jRegistryKey.dll";
            File dllFile = File.createTempFile("install-jsendpraat.", "."+dll);
            dllFile.deleteOnExit();
            message("Extracting: " + dllFile.getPath());
            URL dllUrl = getClass().getResource("/"+dll);
            jarStream = dllUrl.openStream();
            outStream = new FileOutputStream(dllFile);
            buffer = new byte[1024];
            bytesRead = jarStream.read(buffer);
            while(bytesRead >= 0) {
              outStream.write(buffer, 0, bytesRead);
              bytesRead = jarStream.read(buffer);
            } // next chunk of data
            jarStream.close();
            outStream.close();
            System.load(dllFile.getPath());
		  
            RegistryKey r = new RegistryKey(
              RootKey.HKEY_CURRENT_USER,
              "Software\\Google\\Chrome\\NativeMessagingHosts\\" + extension);
            if (!r.exists()) {
              message("Windows: creating registry key for " + extension);
              r.create();
            }
            message("Windows: setting registry value to " + manifestFile.getPath());
            r.setValue(new RegistryValue(manifestFile.getPath()));
          } catch (Throwable t) {
            message(t.getMessage());
            message("Could not set registry directly, falling back to .reg file...");
		  
            // write a registry file
            File regFile = new File(
              manifestDirChrome.exists()? manifestDirChrome : manifestDirChromium,
              "jsendpraat.reg");
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
      } // chrome or chromium is installed

      if (manifestDirFirefox.exists()) { // only if Firefox is installed
        // first ensure any old manifests are deleted
        String[] otherPossibleOldManifests = {
          "nzilbb.chrome.jsendpraat.json",
          "nzilbb.chrome.jsendpraat-firefox.json",
          "nzilbb.jsendpraat.chrome-firefox.json"
        };
        for (String fileName : otherPossibleOldManifests) {
          File toDelete = new File(manifestDirChrome, fileName);
          if (toDelete.exists()) {
            if (toDelete.delete()) {
              message("Deleted " + toDelete.getPath());
            } else {
              message("Could not delete " + toDelete.getPath());
            }
          }
        }
	    	    
        // extract/update manifest
        String extension = "nzilbb.jsendpraat.chrome";
        String manifest = extension+".json";
        String manifestFirefox = manifest;
        // on Windows, Firefox and Chrome have their manifests in the same directory,
        // so we use a different file name for this
        if (os == OS.Windows) manifestFirefox = extension+"-firefox.json";
        message("Extracting for Firefox: " + manifest);
        File manifestFile = new File(manifestDirFirefox, manifestFirefox);
        URL manifestUrl = getClass().getResource("/"+manifest);
        BufferedReader manifestReader = new BufferedReader(new InputStreamReader(manifestUrl.openStream()));
        PrintWriter manifestWriter = new PrintWriter(manifestFile);
        line = manifestReader.readLine();
        while(line != null) {
          // skip the allowed_origins line, which is for Chrome
          if (!line.matches(".*allowed_origins.*")) {
            manifestWriter.println(line.replace("${exepath}", hostScriptFile.getPath().replace("\\","\\\\")));
          }
          line = manifestReader.readLine();
        } // next line
        manifestReader.close();
        manifestWriter.close();
	    
        if (os == OS.Windows) {
          progress.setValue(6);
          // we need to add an entry to registry
          try {
            String dll = "jRegistryKey.dll";
            File dllFile = File.createTempFile("install-jsendpraat.", "."+dll);
            dllFile.deleteOnExit();
            message("Extracting: " + dllFile.getPath());
            URL dllUrl = getClass().getResource("/"+dll);
            jarStream = dllUrl.openStream();
            outStream = new FileOutputStream(dllFile);
            buffer = new byte[1024];
            bytesRead = jarStream.read(buffer);
            while(bytesRead >= 0) {
              outStream.write(buffer, 0, bytesRead);
              bytesRead = jarStream.read(buffer);
            } // next chunk of data
            jarStream.close();
            outStream.close();
            System.load(dllFile.getPath());
		  
            RegistryKey r = new RegistryKey(
              RootKey.HKEY_CURRENT_USER,
              "Software\\Mozilla\\NativeMessagingHosts\\" + extension);
            if (!r.exists()) {
              message("Windows: creating registry key for " + extension);
              r.create();
            }
            message("Windows: setting registry value to " + manifestFile.getPath());
            r.setValue(new RegistryValue(manifestFile.getPath()));
          } catch (Throwable t) {
            message(t.getMessage());
            message("Could not set registry directly, falling back to .reg file...");
		  
            // write a registry file
            File regFile = new File(manifestDirFirefox, "jsendpraat-firefox.reg");
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
    } catch(Exception x) {
      error(x.getMessage());
    } catch(Throwable t) {
      error(t.toString());
    }      
    cancel.setText("Finish");
  }
   
  /** Uninstallation */
  public void uninstall() {
    install.setEnabled(false);
    uninstall.setEnabled(false);
    progress.setMaximum(6);
    progress.setValue(1);
    message("Determine file locations...");
    try {
      determineFileLocations(true);
      progress.setValue(2);
      File manifest = new File(manifestDirChrome, "nzilbb.jsendpraat.chrome.json");
      if (manifest.exists()) {
        message("Delete: " + manifest.getPath());
        manifest.delete();
      }
      progress.setValue(3);
      manifest = new File(manifestDirChromium, "nzilbb.jsendpraat.chrome.json");
      if (manifest.exists()) {
        message("Delete: " + manifest.getPath());
        manifest.delete();
      }
      progress.setValue(4);
      manifest = new File(manifestDirFirefox, "nzilbb.jsendpraat.chrome.json");
      if (manifest.exists()) {
        message("Delete: " + manifest.getPath());
        manifest.delete();
      }
      progress.setValue(5);
      message("Delete: " + binDir.getPath());
      recursivelyDeleteFiles(binDir);
     
      message("Uninstalled");
      progress.setValue(progress.getMaximum());
    } catch(Exception x) {
      error(x.getMessage());
    } catch(Throwable t) {
      error(t.toString());
    }      
    cancel.setText("Finish");
  }
  
  /**
   * Recursively deletes files and directories.
   * @param f
   * @param sPath
   * @return A list of files that could not be removed.
   */
  public Vector<File> recursivelyDeleteFiles(File f) throws Exception {
    Vector<File> notDeleted = new Vector<File>();
    if (f.isDirectory()) {
      File[] files = f.listFiles();
      if (files != null) {
        for (int i = 0; i < files.length; i++) {
          notDeleted.addAll(recursivelyDeleteFiles(files[i]));
        }
      }
    }
    if (!f.delete()) {
      message("Could not remove " + f.getPath());
      notDeleted.add(f);
    } else {
      message("Removed " + f.getPath());
    }
    return notDeleted;
  } // end of recursivelyDeleteFiles()
  
  /**
   * Makes a file executable.
   * @param f
   */
  public void makeExecutable(File f) {
    String[] cmdArray = {"/bin/chmod", "a+x", f.getPath()};
    try {
      Runtime.getRuntime().exec(cmdArray);
    } catch(Exception x) {
      message("Could not mark file as executable " + cmdArray[2] + " : " + x);
    }
  } // end of makeExecutable()
         
  /**
   * Displays a status message.
   * @param s Message
   */
  public void message(String s) {
    progress.setString(s);
    System.out.println(s);
  } // end of message()
   
  /**
   * Display a popup error.
   * @param s
   */
  public void error(String s) {
    message(s);
    JOptionPane.showMessageDialog(frame_, s, "Error", JOptionPane.ERROR_MESSAGE);
  } // end of alert()

} // end of class HostInstaller
