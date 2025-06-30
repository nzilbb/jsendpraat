//
// Copyright 2004-2024 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.jar.JarFile;

/**
 * <p>Client HTTP Request class.
 * <p>Helps to send POST HTTP requests with various form data,
 * including files. Cookies can be added to be included in the request.</p>
 * <p>Originally com.myjavatools.web.ClientHttpRequest (version 1.0) by Vlad Patryshev</p>
 * <p>Adapted for jsendpraat by Robert Fromont</p>
 */
public class HttpRequestPostMultipart {
  protected HttpURLConnection connection;
  protected OutputStream os = null;
  protected Map<String,String> cookies = new HashMap<String,String>();
   
  /**
   * Sets a request parameter value
   * @param sKey
   * @param sValue
   */
  public void setHeader(String sKey, String sValue) {
    connection.setRequestProperty(sKey, sValue);
  } // end of setParameter()
   
  /** Cancel flag */
  protected boolean bCancelling = false;
   
  /**
   * Determines whether or not the request is being cancelled.
   * @return true, if the last request has been asked to cancel, false otherwise
   */
  public boolean isCancelling() {
    return bCancelling;
  } // end of isCancelling()
   
  /**
   * Cancel the current request
   */
  public void cancel() {
    bCancelling = true;
  } // end of cancel()
   
  protected void connect() throws IOException {
    if (os == null) {
      os = connection.getOutputStream();
    }
    // check whether we are cancelling here, as this method is called
    // before every write	
    if (bCancelling) {
      throw new RequestCancelledException(this);
    }
  }
   
  protected void write(char c) throws IOException {
    connect();
    os.write(c);
  }
   
  protected void write(String s) throws IOException {
    connect();
    os.write(s.getBytes());
  }
   
  protected void newline() throws IOException {
    connect();
    write("\r\n");
  }
   
  protected void writeln(String s) throws IOException {
    connect();
    write(s);
    newline();
  }
   
  private static Random random = new Random();
   
  protected static String randomString() {
    return Long.toString(random.nextLong(), 36);
  }
   
  String boundary = "---------------------------"
    + randomString() + randomString() + randomString();
   
  private void boundary() throws IOException {
    write("--");
    write(boundary);
  }
   
  /**
   * Creates a new multipart POST HTTP request on a freshly opened URLConnection
   *
   * @param connection an already open URL connection
   * @param sAuthorization Authorisation string or null if none is required
   * @throws IOException
   */
  public HttpRequestPostMultipart(HttpURLConnection connection, String sAuthorization)
    throws IOException {
    this.connection = connection;
    connection.setUseCaches(false);
    connection.setInstanceFollowRedirects(false);
    setUserAgent();
    if (sAuthorization != null) {
      if (sAuthorization.startsWith("Cookie ")) { // set Cookie header
        connection.setRequestProperty("Cookie", sAuthorization.substring(7));
      } else { // set Authorization header
        connection.setRequestProperty("Authorization", sAuthorization);
      }
    }
    connection.setDoOutput(true);
    connection.setRequestProperty(
      "Content-Type", "multipart/form-data; boundary=" + boundary);
    connection.setChunkedStreamingMode(1024);
  }
   
  /**
   * Creates a new multipart POST HTTP request for a specified URL
   *
   * @param url the URL to send request to
   * @param sAuthorization Authorisation string or null if none is required
   * @throws IOException
   */
  public HttpRequestPostMultipart(URL url, String sAuthorization) throws IOException {
    this((HttpURLConnection)url.openConnection(), sAuthorization);
  }
   
  /**
   * Creates a new multipart POST HTTP request for a specified URL string
   *
   * @param urlString the string representation of the URL to send request to
   * @throws IOException
   */
  public HttpRequestPostMultipart(String urlString, String sAuthorization) throws IOException {
    this(new URL(urlString), sAuthorization);
  }   
   
  static String UserAgent = null;
  /**
   * Sets the user-agent header to indicate the name/version of the library.
   */
  public HttpRequestPostMultipart setUserAgent() {
    if (UserAgent == null) {
      UserAgent = ""+getClass().getPackage().getImplementationTitle()
        + " " + getClass().getPackage().getImplementationVersion();
    }
    setHeader("user-agent", UserAgent);
    return this;
   } // end of setUserAgent()
  
  @SuppressWarnings("rawtypes")
  private void postCookies() {
    StringBuffer cookieList = new StringBuffer();
      
    for (Iterator i = cookies.entrySet().iterator(); i.hasNext();) {
      Map.Entry entry = (Map.Entry)(i.next());
      cookieList.append(entry.getKey().toString() + "=" + entry.getValue());
	 
      if (i.hasNext()) {
        cookieList.append("; ");
      }
    }
    if (cookieList.length() > 0) {
      connection.setRequestProperty("Cookie", cookieList.toString());
    }
  }
   
  /**
   * adds a cookie to the requst
   * @param name cookie name
   * @param value cookie value
   * @throws IOException
   */
  public void setCookie(String name, String value) throws IOException {
    cookies.put(name, value);
  }
   
  /**
   * adds cookies to the request
   * @param cookies the cookie "name-to-value" map
   * @throws IOException
   */
  public void setCookies(Map<String,String> cookies) throws IOException {
    if (cookies == null) return;
    this.cookies.putAll(cookies);
  }
   
  /**
   * adds cookies to the request
   * @param cookies array of cookie names and values (cookies[2*i] is a name, cookies[2*i + 1]
   * is a value)
   * @throws IOException
   */
  public void setCookies(String[] cookies) throws IOException {
    if (cookies == null) return;
    for (int i = 0; i < cookies.length - 1; i+=2) 
    {
      setCookie(cookies[i], cookies[i+1]);
    }
  }
   
  private void writeName(String name) throws IOException {
    newline();
    write("Content-Disposition: form-data; name=\"");
    write(name);
    write('"');
  }
   
  /**
   * adds a string parameter to the request
   * @param name parameter name
   * @param value parameter value
   * @throws IOException
   */
  public void setParameter(String name, String value) throws IOException {
    if (value == null) return; //20100520 robert.fromont@canterbury.ac.nz 
    boundary();
    writeName(name);
    newline(); newline();
    writeln(value);
  }
   
  private void pipe(InputStream in, OutputStream out) throws IOException {
    //20100521 robert.fromont@canterbury.ac.nz smaller buffer to conserve memory
    byte[] buf = new byte[1024]; 
    int nread;
    int navailable;
    int total = 0;
    synchronized (in) {
      while((nread = in.read(buf, 0, buf.length)) >= 0) {
        //20110221 robert.fromont@canterbury.ac.nz added cancelability
        if(bCancelling) throw new RequestCancelledException(this);
	    
        out.write(buf, 0, nread);
        total += nread;
      }
    }
    out.flush();
    buf = null;
  }
   
  /**
   * adds a file parameter to the request
   * @param name parameter name
   * @param filename the name of the file
   * @param is input stream to read the contents of the file from
   * @throws IOException
   */
  public void setParameter(String name, String filename, InputStream is) throws IOException {
    try {
      boundary();
      writeName(name);
      write("; filename=\"");
      write(filename);
      write('"');
      newline();
      write("Content-Type: ");
      String type = HttpURLConnection.guessContentTypeFromName(filename);
      if (type == null) type = "application/octet-stream";
      writeln(type);
      newline();
      pipe(is, os);
      newline();
    } finally {
      is.close();  //20100521 robert.fromont@canterbury.ac.nz
    }
  }

  /**
   * adds a file parameter to the request
   * @param name parameter name
   * @param file the file to upload
   * @throws IOException
   */
  public void setParameter(String name, File file) throws IOException {
    if (file == null) return; //20100520 robert.fromont@canterbury.ac.nz 
    setParameter(name, file.getPath(), new FileInputStream(file));
  }
      
  /**
   * adds a parameter to the request; if the parameter is a File, the file is uploaded, 
   * otherwise the string value of the parameter is passed in the request
   * @param name parameter name
   * @param object parameter value, a File or anything else that can be stringified
   * @throws IOException
   */
  public void setParameter(String name, Object object) throws IOException {
    if (object == null) return; //20100520 robert.fromont@canterbury.ac.nz 
    if (object instanceof File) {
      setParameter(name, (File) object);
    } else if (object instanceof Iterable) {
      @SuppressWarnings("rawtypes")
        Iterator i = ((Iterable)object).iterator();
      while (i.hasNext()) {
        setParameter(name, i.next().toString());
      }
    } else {
      setParameter(name, object.toString());
    }
  }
      
  /**
   * adds parameters to the request
   * @param parameters "name-to-value" map of parameters; if a value is a file, the file is
   * uploaded, otherwise it is stringified and sent in the request
   * @throws IOException
   */
  @SuppressWarnings("rawtypes")
  public void setParameters(Map<String,String> parameters) throws IOException {
    if (parameters == null) return;
    for (Iterator i = parameters.entrySet().iterator(); i.hasNext();) {
      Map.Entry entry = (Map.Entry)i.next();
      setParameter(entry.getKey().toString(), entry.getValue());
    }
  }
      
  /**
   * adds parameters to the request
   * @param parameters array of parameter names and values (parameters[2*i] is a name, 
   * parameters[2*i + 1] is a value); if a value is a file, the file is uploaded, otherwise
   * it is stringified and sent in the request
   * @throws IOException
   */
  public void setParameters(Object[] parameters) throws IOException {
    if (parameters == null) return;
    for (int i = 0; i < parameters.length - 1; i+=2) {
      setParameter(parameters[i].toString(), parameters[i+1]);
    }
  }
      
  /**
   * posts the requests to the server, with all the cookies and parameters that were added
   * @return input stream with the server response
   * @throws IOException
   */
  public HttpURLConnection post() throws IOException {
    boundary();
    writeln("--");
    os.close();
    return connection;
  }
      
  /**
   * posts the requests to the server, with all the cookies and parameters that were added
   * before (if any), and with parameters that are passed in the argument
   * @param parameters request parameters
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameters
   */
  public HttpURLConnection post(Map<String,String> parameters) throws IOException {
    setParameters(parameters);
    return post();
  }
      
  /**
   * posts the requests to the server, with all the cookies and parameters that were added
   * before (if any), and with parameters that are passed in the argument
   * @param parameters request parameters
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameters
   */
  public HttpURLConnection post(Object[] parameters) throws IOException {
    setParameters(parameters);
    return post();
  }
      
  /**
   * posts the requests to the server, with all the cookies and parameters that were added
   * before (if any), and with cookies and parameters that are passed in the arguments
   * @param cookies request cookies
   * @param parameters request parameters
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameters
   * @see #setCookies
   */
  public HttpURLConnection post(Map<String,String> cookies, Map<String,String> parameters)
    throws IOException {
    setCookies(cookies);
    setParameters(parameters);
    return post();
  }

  /**
   * posts the requests to the server, with all the cookies and parameters that were added
   * before (if any), and with cookies and parameters that are passed in the arguments
   * @param cookies request cookies
   * @param parameters request parameters
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameters
   * @see #setCookies
   */
  public HttpURLConnection post(String[] cookies, Object[] parameters) throws IOException {
    setCookies(cookies);
    setParameters(parameters);
    return post();
  }

  /**
   * post the POST request to the server, with the specified parameter
   * @param name parameter name
   * @param value parameter value
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameter
   */
  public HttpURLConnection post(String name, Object value) throws IOException {
    setParameter(name, value);
    return post();
  }

  /**
   * post the POST request to the server, with the specified parameters
   * @param name1 first parameter name
   * @param value1 first parameter value
   * @param name2 second parameter name
   * @param value2 second parameter value
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameter
   */
  public HttpURLConnection post(String name1, Object value1, String name2, Object value2)
    throws IOException {
    setParameter(name1, value1);
    return post(name2, value2);
  }
      
  /**
   * post the POST request to the server, with the specified parameters
   * @param name1 first parameter name
   * @param value1 first parameter value
   * @param name2 second parameter name
   * @param value2 second parameter value
   * @param name3 third parameter name
   * @param value3 third parameter value
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameter
   */
  public HttpURLConnection post(
    String name1, Object value1, String name2, Object value2, String name3, Object value3)
    throws IOException {
    setParameter(name1, value1);
    return post(name2, value2, name3, value3);
  }
      
  /**
   * post the POST request to the server, with the specified parameters
   * @param name1 first parameter name
   * @param value1 first parameter value
   * @param name2 second parameter name
   * @param value2 second parameter value
   * @param name3 third parameter name
   * @param value3 third parameter value
   * @param name4 fourth parameter name
   * @param value4 fourth parameter value
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameter
   */
  public HttpURLConnection post(
    String name1, Object value1, String name2, Object value2, String name3, Object value3,
    String name4, Object value4)
    throws IOException {
    setParameter(name1, value1);
    return post(name2, value2, name3, value3, name4, value4);
  }
      
  /**
   * posts a new request to specified URL, with parameters that are passed in the argument
   * @param parameters request parameters
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameters
   */
  public static HttpURLConnection post(
    URL url, String sAuthorization, Map<String,String> parameters)
    throws IOException {
    return new HttpRequestPostMultipart(url, sAuthorization).post(parameters);
  }
      
  /**
   * posts a new request to specified URL, with parameters that are passed in the argument
   * @param parameters request parameters
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameters
   */
  public static HttpURLConnection post(URL url, String sAuthorization, Object[] parameters)
    throws IOException {
    return new HttpRequestPostMultipart(url, sAuthorization).post(parameters);
  }
      
  /**
   * posts a new request to specified URL, with cookies and parameters that are passed in the argument
   * @param cookies request cookies
   * @param parameters request parameters
   * @return input stream with the server response
   * @throws IOException
   * @see #setCookies
   * @see #setParameters
   */
  public static HttpURLConnection post(
    URL url, String sAuthorization, Map<String,String> cookies, Map<String,String> parameters)
    throws IOException {
    return new HttpRequestPostMultipart(url, sAuthorization).post(cookies, parameters);
  }
      
  /**
   * posts a new request to specified URL, with cookies and parameters that are passed in 
   * the argument
   * @param cookies request cookies
   * @param parameters request parameters
   * @return input stream with the server response
   * @throws IOException
   * @see #setCookies
   * @see #setParameters
   */
  public static HttpURLConnection post(
    URL url, String sAuthorization, String[] cookies, Object[] parameters) throws IOException {
    return new HttpRequestPostMultipart(url, sAuthorization).post(cookies, parameters);
  }
      
  /**
   * post the POST request specified URL, with the specified parameter
   * @param name1 parameter name
   * @param value1 parameter value
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameter
   */
  public static HttpURLConnection post(URL url, String sAuthorization, String name1, Object value1)
    throws IOException {
    return new HttpRequestPostMultipart(url, sAuthorization).post(name1, value1);
  }
      
  /**
   * post the POST request to specified URL, with the specified parameters
   * @param name1 first parameter name
   * @param value1 first parameter value
   * @param name2 second parameter name
   * @param value2 second parameter value
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameter
   */
  public static HttpURLConnection post(
    URL url, String sAuthorization, String name1, Object value1, String name2, Object value2)
    throws IOException {
    return new HttpRequestPostMultipart(url, sAuthorization).post(name1, value1, name2, value2);
  }
      
  /**
   * post the POST request to specified URL, with the specified parameters
   * @param name1 first parameter name
   * @param value1 first parameter value
   * @param name2 second parameter name
   * @param value2 second parameter value
   * @param name3 third parameter name
   * @param value3 third parameter value
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameter
   */
  public static HttpURLConnection post(
    URL url, String sAuthorization, String name1, Object value1, String name2, Object value2,
    String name3, Object value3)
    throws IOException {
    return new HttpRequestPostMultipart(url, sAuthorization).post(name1, value1, name2, value2, name3, value3);
  }
      
  /**
   * post the POST request to specified URL, with the specified parameters
   * @param name1 first parameter name
   * @param value1 first parameter value
   * @param name2 second parameter name
   * @param value2 second parameter value
   * @param name3 third parameter name
   * @param value3 third parameter value
   * @param name4 fourth parameter name
   * @param value4 fourth parameter value
   * @return input stream with the server response
   * @throws IOException
   * @see #setParameter
   */
  public static HttpURLConnection post(
    URL url, String sAuthorization, String name1, Object value1, String name2, Object value2,
    String name3, Object value3, String name4, Object value4)
    throws IOException {
    return new HttpRequestPostMultipart(url, sAuthorization).post(name1, value1, name2, value2, name3, value3, name4, value4);
  }
}
