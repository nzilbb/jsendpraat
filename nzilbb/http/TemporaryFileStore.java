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

import java.io.*;
import java.util.*;

/**
 * Stores a collection of cached files and deletes them when garbage-collected.  Sometimes files can't be deleted at that time (because other processes or native libraries have them open) so it also deletes leftover files from any old sessions.
 * @author Robert Fromont robert.fromont@canterbury.ac.nz
 */
public class TemporaryFileStore
{
   // Attributes:
   
   /** Collection of temporary files created by this object */
   protected Hashtable<Object,File> hFilesAlreadyDownloaded = new Hashtable<Object,File>();
   
   /** The directory where temporary files are kept.  Determined by the <i>java.io.tmpdir</i> system property and the value of {@link #getSubdirectoryName()} */
   protected File fSubdirectory;
   /**
    * The directory where temporary files are kept.  Determined by the <i>java.io.tmpdir</i> system property and the value of {@link #getSubdirectoryName()}.
    */
   public File getSubdirectory() { return fSubdirectory; }
   
   /**
    * Name of the subdirectory in which temporary files are stored.  The default value is "onzeminer".  This subdirectory will, under most circumstances, be created in the directory named by the <i>java.io.tmpdir</i> system property.
    */
   protected String sSubdirectoryName = "jsendpraat";
   /**
    * SubdirectoryName accessor 
    * @return Name of the subdirectory in which temporary files are stored.  The default value is "onzeminer".
    */
   public String getSubdirectoryName() { return sSubdirectoryName; }   
   
   /**
    * How many hours old a file in {@link #getSubdirectoryName()} has be be before being eligible for deletion. The default value is 6.
    */
   protected int iOldFileHours = 6;
   /**
    * OldFileHours accessor 
    * @return How many hours old a file in {@link #getSubdirectoryName()} has be be before being eligible for deletion. The default value is 6.
    */
   public int getOldFileHours() { return iOldFileHours; }
   /**
    * OldFileHours mutator
    * @param iNewOldFileHours How many hours old a file in {@link #getSubdirectoryName()} has be be before being eligible for deletion. The default value is 6.
    */
   public void setOldFileHours(int iNewOldFileHours) { iOldFileHours = iNewOldFileHours; }
   
   /**
    * Constructor
    * @param subdirectoryName Name of the subdirectory in which temporary files are stored.
    */
   public TemporaryFileStore(String subdirectoryName)
   {
      sSubdirectoryName = subdirectoryName;
      determineSubdirectory();
   } // end of constructor
   
   /**
    * Constructor
    * @param subdirectoryName Name of the subdirectory in which temporary files are stored.
    * @param oldFileHours How many hours old a file in {@link #getSubdirectoryName()} has be be before being eligible for deletion. 
    */
   public TemporaryFileStore(String subdirectoryName, int oldFileHours)
   {
      sSubdirectoryName = subdirectoryName;
      iOldFileHours = oldFileHours;
      determineSubdirectory();
   } // end of constructor
   
   /**
    * Constructor
    */
   public TemporaryFileStore()
   {
      determineSubdirectory();
   } // end of constructor
   
   /** Uses the <i>java.io.tmpdir</i> system property and the value of {@link #getSubdirectoryName()} to set {@link #getSubdirectory()} */
   private void determineSubdirectory()
   {
      if (sSubdirectoryName == null) sSubdirectoryName = "";
      sSubdirectoryName = sSubdirectoryName
	 // ensure the name is safe as a file name
	 .replaceAll("\\W","");
      if (sSubdirectoryName.length() == 0)
      {
	 sSubdirectoryName = "onzeminer";
      }
      File fParent = new File("."); // this is an emergency default only
      try
      {
	 fParent = new File(System.getProperty("java.io.tmpdir"));
      }
      catch (Throwable t)
      {
	 System.err.println(
	    "Cannot determine temporary files directory: " + t);
      }
      try
      {
	 fSubdirectory = new File(fParent, sSubdirectoryName);
	 
	 // ensure it exists
	 if (!fSubdirectory.exists())
	 {
	    if (!fSubdirectory.mkdir())
	    {
	       System.err.println(
		  "Cannot create temporary folder \"" 
		  + fSubdirectory.getPath() + "\"");
	    }
	 }
	 
	 System.err.println("Temporary files stored in " + fSubdirectory);
	 // delete any leftorver files in it
	 deleteOldFiles();
      }
      catch (Throwable t)
      {
	 System.err.println(
	    "Cannot determine generate subdirectory using \"" 
	    + fParent.getPath() + "\" and \"" + sSubdirectoryName 
	    + "\": " + t);
      }
      
   }
   
   /**
    * Gets the file based on the given key.
    * @param oKey
    * @return The file stored with the given key, or null if the key doesn't exist.
    */
   public File getFile(Object oKey)
   {
      return hFilesAlreadyDownloaded.get(oKey);
   } // end of getFile()
   
   /**
    * Creates an empty file in the default temporary-file directory, using the given prefix and suffix to generate its name, and calls putFile() for it. deleteOnExit() is also invoked for the new temporary file.
    * @param oKey
    * @param sPrefix - The prefix string to be used in generating the file's name; must be at least three characters long
    * @param sSuffix - The suffix string to be used in generating the file's name; may be null, in which case the suffix ".tmp" will be used 
    */
   public File createTempFile(Object oKey, String sPrefix, String sSuffix)
      throws IOException
   {
      File file = File.createTempFile(sPrefix, sSuffix, fSubdirectory);
      file.deleteOnExit();		  
      putFile(oKey, file);
      return file;
   }

   /**
    * Creates an empty file in the default temporary-file directory, using the given name to determine its name and key.
    * @param sName
    */
   public File createTempFile(String sName)
      throws IOException
   {      
      return createTempFile(sName, "___", sName);
   }
   
   /**
    * Adds a file to the collection.
    * @param oKey
    * @param file
    */
   public void putFile(Object oKey, File file)
   {
      hFilesAlreadyDownloaded.put(oKey, file);
   } // end of putFile()   
   
   /**
    * Deletes all files and removes them from the collection.
    */
   public void deleteAllFiles()
   {
      // delete temporary files
      Enumeration<File> enFiles = hFilesAlreadyDownloaded.elements();
      while (enFiles.hasMoreElements())
      {
	 File file = enFiles.nextElement();
	 if (!file.delete())
	 {
	    System.err.println("TemporaryFileStore: Could not delete file " + file.getPath());
	 }
	 else
	 {
	    System.err.println("TemporaryFileStore: Deleted file " + file.getPath());
	 }
      }
      hFilesAlreadyDownloaded.clear();
   } // end of deleteAllFiles()
   
   /**
    * Deletes old files - those created by some previous instance of this class with the same {@link #getSubdirectoryName()} as this one.  How old the file has to be to count as old is determined by the value of {@link #getOldFileHours()}
    */
   public void deleteOldFiles()
   {
      // identify old files
      File[] files = fSubdirectory.listFiles(new FileFilter()
	 {
	    public  boolean accept(File file) 
	    {
	       return (file.lastModified() < new Date().getTime() - (getOldFileHours() * 60 * 60 * 1000));
	    }
	 });
      // delete them
      for (int i = 0; i < files.length; i++)
      {
	 if (!files[i].delete())
	 {
	    System.err.println("Could not delete old temporary file "
			       + files[i].getPath());
	 }
      }
   } // end of deleteAllFiles()   
   
   /**
    * Called by the garbage collector
    */
   protected void finalize()
   {
      deleteAllFiles();
   } // end of finalize()
   
} // end of class TemporaryFileStore
