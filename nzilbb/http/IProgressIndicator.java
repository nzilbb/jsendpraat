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

/**
 * Interface for an object that receives progress updates.
 * @author Robert Fromont robert.fromont@canterbury.ac.nz
 */
public interface IProgressIndicator
{
   /**
    * Set the maximum value for progress.
    * @param max Maximum possible progress value.
    */
   public void setMaximum(int max);

   /**
    * Get the maximum value for progress.
    * @return Maximum possible progress value.
    */
   public int getMaximum();

   /**
    * Sets the current progress value.
    * @param progress Progress value.
    */
   public void setValue(int progress);

   /**
    * Gets the current progress value.
    * @return Progress value.
    */
   public int getValue();
   
   /**
    * Sets a descriptive string for the progress.
    * @param s Descriptive string.
    */
   public void setString(String s);

   /**
    * Gets a descriptive string for the progress.
    * @return Descriptive string.
    */
   public String getString();

} // end of class IProgressIndicator
