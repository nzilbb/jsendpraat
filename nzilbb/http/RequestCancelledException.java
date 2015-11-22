//
// Copyright 2011-2015 New Zealand Institute of Language, Brain and Behaviour, 
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
 * Exception thrown when an HttpRequestPostMultipart is cancelled by the user.
 * @author Robert Fromont robert.fromont@canterbury.ac.nz
 */
@SuppressWarnings("serial")
public class RequestCancelledException
   extends java.io.IOException
{
   /**
    * The request that was cancelled
    * @see #getRequest()
    * @see #setRequest(HttpRequestPostMultipart)
    */
   protected HttpRequestPostMultipart rRequest;
   /**
    * Getter for {@link #rRequest}: The request that was cancelled
    * @return The request that was cancelled
    */
   public HttpRequestPostMultipart getRequest() { return rRequest; }
   /**
    * Setter for {@link #rRequest}: The request that was cancelled
    * @param rNewRequest The request that was cancelled
    */
   public void setRequest(HttpRequestPostMultipart rNewRequest) { rRequest = rNewRequest; }
   
   /**
    * Default constructor
    */
   public RequestCancelledException()
   {
   } // end of constructor
   
   /**
    * Constructor
    */
   public RequestCancelledException(String s)
   {
      super(s);
   } // end of constructor
   
   /**
    * Constructor
    */
   public RequestCancelledException(HttpRequestPostMultipart request)
   {	 
      setRequest(request);
   } // end of constructor
   
} // end of class CancelledException
