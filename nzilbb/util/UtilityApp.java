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
package nzilbb.util;

import java.applet.*;
import javax.swing.*;
import javax.swing.table.*;
import java.lang.*;
import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Base class that standardizes various common functions for utilitiy applications - whether they be run as full applications from the command line or via JNLP or as applets within a browser.
 * <p>To implement a self-documenting, self-configuring application/applet,
 * simply extend this class, annotate any setXXX() methods with a {@link Switch}
 * annotation, and call <code>mainRun(String argv[])</code> from the derived
 * class's <code>public static void main(String argv[])</code>
 * <p>To provide further information, you may use the {@link UtilityDescription} 
 * annotation on the derived class itself.
 * <p>You can also use the {@link #setDefaultHeight}, {@link #setDefaultWidth},
 * and {@link #setDefaultWindowTitle} methods to influence the configuration of
 * the application window (i.e. for when not invoked as an applet)
 * <p>Doing this has the following effect:
 * <ul>
 *  <li>If the application is run from the command line with the -usage flag
 *  then a list of switches (one for each @Switch annotated setter) is listed
 *  to stderr, along with description information annotated with {@link UtilityDescription}</li>
 *  <li>If the application is run from the command line with switches that 
 *  correspond to @Switch annotated setters, then the setter will be called
 *  with the given value - e.g. if -myswitch=myvalue is called, then the
 *  equivalent of <code>myObject.setMyswitch("myvalue")</code> is executed</li>
 *  <li>If the application is run from the command line and any switches that
 *  are marked as compulsory are not set, then execution is halted with an
 *  error message and the usage information written to stderr</li>
 *  <li>If the application is run from the command line with arguments that
 *  don't start with '-' then these are added to a {@link #vArguments}</li>
 *  <li>If the application is run as an applet and the derived class's 
 *  <code>init()</code> method calls <code>interpretAppletParameters()</code>
 *  then any @Switch annotated setters are interpreted as possible applet 
 *  parameters, and the setters are called with the given parameter value if
 *  present.</li>
 *  <li>When the application is run, if it finds a .properties file that matches
 *  the main class name, it is used to call @Switch annotated setter corresponding
 *  to the properties defined in the file.  Arguments can also be specified by
 *  defining properties called arg[0], arg[1], etc.</li>
 * </ul>
 * <p> e.g.
 * <pre>
 * import nz.ac.canterbury.ling.UtilityApp;
 * import nz.ac.canterbury.ling.UtilityDescription;
 * import nz.ac.canterbury.ling.Switch;
 * 
 * &#64;UtilityDescription(value="A very useful utility",arguments="extra-arg-1 extra-arg-2")
 * public class Useful extends UtilityApp
 * {
 *    public Useful()
 *    {
 *       setDefaultWindowTitle("This application is useful");
 *       setDefaultWidth(800);
 *       setDefaultHeight(600);
 *    }
 * 
 *    public static void main(String argv[])
 *    {
 *       new Useful().mainRun(argv);
 *    }
 * 
 *    &#64;Switch(value="This is a compulsory string switch that fulfils some purpose",compulsory=true)
 *    public void setSomeString(String s) {...}
 * 
 *    &#64;Switch("This is an optional boolean switch")
 *    public void setSomeBoolean(Boolean b) {...}
 * 
 *    public void init()
 *    {
 *       interpretAppletParameters();
 *       ...
 *    }
 *    public void start()
 *    {
 *       for (String sArgument: vArguments)
 *       {
 * 	 ...
 *       }
 *    }
 * }
 * </pre>
 * <p>This could then be invoked as:<br>
 * <tt>java Useful -SomeString=Hello -SomeBoolean sundryArg1 sundryArg2</tt>
 * @author Robert Fromont robert.fromont@canterbury.ac.nz
 */

public class UtilityApp
   extends JApplet
{
   static final long serialVersionUID = 1;      

   // Attributes:
   
   /**
    * The window in which the application runs if invoked from the command-line or via JNLP
    * @see #sDefaultWindowTitle
    * @see #iDefaultHeight
    * @see #iDefaultWidth
    */
   protected JFrame frame_;
   
   /**
    * Default height of the application.
    * @see #getDefaultHeight()
    * @see #setDefaultHeight(int)
    */
   protected int iDefaultHeight = 600;
   /**
    * Getter for {@link #iDefaultHeight}: Default height of the application.
    * @return Default height of the application.
    */
   public int getDefaultHeight() { return iDefaultHeight; }
   /**
    * Setter for {@link #iDefaultHeight}: Default height of the application.
    * @param iNewDefaultHeight Default height of the application.
    */
   public void setDefaultHeight(int iNewDefaultHeight) { iDefaultHeight = iNewDefaultHeight; }
   
   /**
    * Default width of the application.
    * @see #getDefaultWidth()
    * @see #setDefaultWidth(int)
    */
   protected int iDefaultWidth = 800;
   /**
    * Getter for {@link #iDefaultWidth}: Default width of the application.
    * @return Default width of the application.
    */
   public int getDefaultWidth() { return iDefaultWidth; }
   /**
    * Setter for {@link #iDefaultWidth}: Default width of the application.
    * @param iNewDefaultWidth Default width of the application.
    */
   public void setDefaultWidth(int iNewDefaultWidth) { iDefaultWidth = iNewDefaultWidth; }
   

   /**
    * Default title for the application window.
    * @see #getDefaultWindowTitle()
    * @see #setDefaultWindowTitle(String)
    */
   protected String sDefaultWindowTitle;
   /**
    * Getter for {@link #sDefaultWindowTitle}: Default title for the application window.
    * @return Default title for the application window.
    */
   public String getDefaultWindowTitle() 
   { 
      if (sDefaultWindowTitle != null)
	 return sDefaultWindowTitle; 
      else
	 return getClass().getSimpleName();
   }
   /**
    * Setter for {@link #sDefaultWindowTitle}: Default title for the application window.
    * @param sNewDefaultWindowTitle Default title for the application window.
    */
   public void setDefaultWindowTitle(String sNewDefaultWindowTitle) { sDefaultWindowTitle = sNewDefaultWindowTitle; }


   /**
    * Whether or not to display usage information
    * @see #getUsage()
    * @see #setUsage(Boolean)
    */
   protected Boolean bUsage = Boolean.FALSE;
   /**
    * Getter for {@link #bUsage}: Whether or not to display usage information
    * @return Whether or not to display usage information
    */
   public Boolean getUsage() { return bUsage; }
   /**
    * Setter for {@link #bUsage}: Whether or not to display usage information
    * @param bNewUsage Whether or not to display usage information
    */
   @Switch("Whether or not to display usage information")
   public void setUsage(Boolean bNewUsage) { bUsage = bNewUsage; }
  

   /**
    * Arguments passed in on the command line.  i.e. command line arguments that
    * don't start with '-'.  Command-line arguments that start with '-' are 
    * interpreted as <i>switches</i>, which set bean attributes.
    */
   protected Vector<String> vArguments = new Vector<String>();
   
   // Methods:
   
   /**
    * Main entrypoint if run as an application - this should be called by the
    * <code>public static void main(String argv[])</code> of the derived class.
    */
   public void mainRun(String argv[])
   {
      frame_ = new JFrame(getDefaultWindowTitle());
      frame_.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

      interpretPropertiesParameters();

      @SuppressWarnings("rawtypes")
      Class myClass = getClass();
      Method[] methods = myClass.getMethods();
      // arguments
      for (String sArg : argv)
      {
	 if (sArg.startsWith("-"))
	 { // switch
	    sArg = sArg.substring(1);
	    int iEquals = sArg.indexOf('=');
	    String sName = sArg;
	    String sValue = "true";
	    if (iEquals >= 0)
	    {
	       sName = sArg.substring(0, iEquals);
	       sValue = sArg.substring(iEquals + 1);
	    }
	    if (sName.length() > 0)
	    {
	       // check for a bean setter with that name
	       Method setter = null;
	       for (Method method : methods)
	       {
		  if (method.getAnnotation(Switch.class) != null
		      && method.getName().equalsIgnoreCase("set" + sName)
		      && method.getParameterTypes().length == 1)
		  {
		     setter = method;
		     break;
		  }
	       } // next method
	       if (setter != null)
	       {
		  try
		  {
		     @SuppressWarnings("rawtypes")
		     Class parameterClass = setter.getParameterTypes()[0];
		     if (parameterClass.equals(String.class))
		     {
			setter.invoke(this, sValue);
		     }
		     else if (parameterClass.equals(Boolean.class))
		     {
			setter.invoke(this, Boolean.valueOf(sValue));
		     }
		     else if (parameterClass.equals(Integer.class))
		     {
			setter.invoke(this, Integer.valueOf(sValue));
		     }
		     else if (parameterClass.equals(Double.class))
		     {
			setter.invoke(this, Double.valueOf(sValue));
		     }
		     else if (parameterClass.equals(URL.class))
		     {
			setter.invoke(this, new URL(sValue));
		     }
		     else if (parameterClass.equals(File.class))
		     {
			setter.invoke(this, new File(sValue));
		     }
		  }
		  catch (Throwable t)
		  {
		     System.err.println("Error interpreting switch: " + sArg 
					+ " : " + t);
		  }
	       } // found the setter
	       else
	       {
		  System.err.println("Ignoring unknown switch: " + sArg);
	       }
	    } // sName is not ""
	 }
	 else
	 { // argument
	    vArguments.add(sArg);
	 }
	 
      } // next argument

      // check that all compulsory switches are set
      Vector<String> vSwitches = new Vector<String>();
      Vector<String> vCompulsorySwitches = new Vector<String>();
      Vector<String> vOptionalSwitches = new Vector<String>();
      for (Method method : methods)
      {
	 Switch switchAnnotation = method.getAnnotation(Switch.class);
	 if (switchAnnotation != null && method.getParameterTypes().length == 1)
	 {	    
	    String sSwitchName = method.getName().replaceFirst("set", "");
	    @SuppressWarnings("rawtypes")
	    Class parameterClass = method.getParameterTypes()[0];
	    String sEg = "-" + sSwitchName + "=<value>";
	    if (parameterClass.equals(Boolean.class))
	    {
	       if (sSwitchName.equals("Usage"))
	       {
		  sEg = "-" + sSwitchName;
	       }
	       else
	       {
		  sEg = "-" + sSwitchName + " or -" + sSwitchName + "=false";
	       }
	    }
	    else if (parameterClass.equals(Integer.class)
	       || parameterClass.equals(Double.class))
	    {
	       sEg = "-" + sSwitchName + "=<number>";
	    }
	    else if (parameterClass.equals(URL.class))
	    {
	       sEg = "-" + sSwitchName + "=<URL>";
	    }
	    else if (method.getParameterTypes()[0].equals(File.class))
	    {
	       sEg = "-" + sSwitchName + "=<path>";
	    }
	    String sUsage = sEg + "\t" + switchAnnotation.value();
	    if (switchAnnotation.compulsory())
	    {
	       try
	       {
		  @SuppressWarnings("unchecked")
		  Method getter = myClass.getMethod(method.getName().replaceFirst("set", "get"));
		  if (getter != null)
		  {
		     try
		     {
			if (getter.invoke(this) == null)
			{
			   System.err.println("compulsory switch '"  + sSwitchName + "' not specified");
			   setUsage(true);
			}
		     }
		     catch (IllegalAccessException x)
		     {
			System.err.println(x.toString());
		     }
		     catch (InvocationTargetException y)
		     {
			System.err.println(y.toString());
		     }
		  }
		  vCompulsorySwitches.add(sUsage);
		  vSwitches.add(sEg);
	       }
	       catch (NoSuchMethodException x){}
	    } // compulsory
	    else
	    { // optional
	       vOptionalSwitches.add(sUsage);
	       vSwitches.add("[" + sEg + "]");
	    } // optional
	 } // method is annotated
      } // next method
      
      // display usage?
      if (getUsage())
      {
	 System.err.println(myClass.getSimpleName() + " usage:");
	 @SuppressWarnings("unchecked")
	 UtilityDescription myAnnotation 
	    = (UtilityDescription)myClass.getAnnotation(UtilityDescription.class);
	 if (myAnnotation != null) System.err.println(myAnnotation.value());
	 String sSwitchEgs = "";
	 for (String s : vSwitches) sSwitchEgs += " " + s;
	 System.err.println("java " 
			    + myClass.getName() + sSwitchEgs
			    + (myAnnotation != null 
			       && myAnnotation.arguments().length() > 0?
			       " "+myAnnotation.arguments():""));
	 if (vCompulsorySwitches.size() > 0)
	 {
	    System.err.println("compulsory switches:");
	    for (String s : vCompulsorySwitches) System.err.println("\t"+s);
	 }
	 if (vOptionalSwitches.size() > 0)
	 {
	    System.err.println("optional switches:");
	    for (String s : vOptionalSwitches) System.err.println("\t"+s);
	 }
	 return;		    
      } // usage
      
      init();
      start();
      
      Toolkit toolkit = frame_.getToolkit();
      Dimension screenSize = toolkit.getScreenSize();
      int top = (screenSize.height - iDefaultHeight) / 2;
      int left = (screenSize.width - iDefaultWidth) / 2;
      // icon
      try
      {
	 URL imageUrl = getClass().getResource(
	    getClass().getSimpleName() + ".png");
	 if (imageUrl != null)
	 {
	    frame_.setIconImage(toolkit.createImage(imageUrl));
	 }
      }
      catch(Exception exception)
      {}
      frame_.getContentPane().add("Center", this);
      frame_.setSize(iDefaultWidth, iDefaultHeight);
      frame_.setLocation(left, top);
      frame_.setVisible(true);
   }
   
   /**
    * Default constructor
    */
   public UtilityApp()
   {
   } // end of constructor
 
   /**
    * Returns information about the parameters that are understood by this applet. An applet should override this method to return an array of Strings describing these parameters.
    * <p>This information is derived by exploring setters that are annotated with {@link Switch}
    */
   public String[][] getParameterInfo()
   {
      Vector<String[]> vParameters = new Vector<String[]>();
      // look for switches
      for (Method setter : getClass().getMethods())
      {
	 Switch switchAnnotation = setter.getAnnotation(Switch.class);
	 if (switchAnnotation != null && setter.getParameterTypes().length == 1)
	 {	    
	    String[] aParameter = new String[3];
	    
	    aParameter[0] = setter.getName().replaceFirst("set", "");
	    if (aParameter[0].equals("Usage")) continue;
	    aParameter[1] = setter.getParameterTypes()[0].getSimpleName();
	    aParameter[2] = switchAnnotation.value();
	    vParameters.add(aParameter);
	 } // this method is a switch
      } // next method
      return (String[][])vParameters.toArray();
   }

   /**
    * Should be called from the init() method of derived classes, this method interprets parameters passed to the applet, setting bean attributes appropiately.
    */
   public void interpretAppletParameters()
   {
      // don't do this if we're not an applet
      if (frame_ != null) return;

      // first check for a .properties file
      interpretPropertiesParameters();

      // set switches from parameters
      for (Method setter : getClass().getMethods())
      {
	 Switch switchAnnotation = setter.getAnnotation(Switch.class);
	 if (switchAnnotation != null && setter.getParameterTypes().length == 1)
	 {	    
	    String sSwitchName = setter.getName().replaceFirst("set", "");
	    String sValue = getParameter(sSwitchName);
	    if (sValue != null)
	    {
	       try
	       {
		  @SuppressWarnings("rawtypes")
		  Class parameterClass = setter.getParameterTypes()[0];
		  if (parameterClass.equals(String.class))
		  {
		     setter.invoke(this, sValue);
		  }
		  else if (parameterClass.equals(Boolean.class))
		  {
		     setter.invoke(this, Boolean.valueOf(sValue));
		  }
		  else if (parameterClass.equals(Integer.class))
		  {
		     setter.invoke(this, Integer.valueOf(sValue));
		  }
		  else if (parameterClass.equals(Double.class))
		  {
		     setter.invoke(this, Boolean.valueOf(sValue));
		  }
		  else if (parameterClass.equals(URL.class))
		  {
		     setter.invoke(this, new URL(sValue));
		  }
		  else if (parameterClass.equals(File.class))
		  {
		     setter.invoke(this, new File(sValue));
		  }
	       }
	       catch (Throwable t)
	       {
		  System.err.println("Error interpreting parameter: " 
				     + sSwitchName + " : " + t);
	       }
	    } // there is a parameter
	 } // this method is a switch
      } // next method
      // now look for (unnamed) arguments
      int i = 0;
      while (getParameter("arg["+i+"]") != null)
      {
	 vArguments.add(getParameter("arg["+i+"]"));
	 i++;
      } // next argument
   } // end of interpretAppletParameters()

   /**
    * This method looks for a .properties file matching the class name, and if it finds one, uses it to set bean attributes and arguments appropiately.
    * Arguments are interpreted is being the values of Properties named arg[0], arg[1], etc.
    */
   public void interpretPropertiesParameters()
   {
      // look for properties file
      URL settingsUrl = getClass().getResource(
	 getClass().getSimpleName() + ".properties");
      if (settingsUrl != null)
      {
	 try
	 {
	    URLConnection cnxn = settingsUrl.openConnection();
	    InputStream is = cnxn.getInputStream();
	    Properties settings = new Properties();
	    settings.load(is);
	    is.close();
	    
	    // set switches from parameters
	    for (Method setter : getClass().getMethods())
	    {
	       Switch switchAnnotation = setter.getAnnotation(Switch.class);
	       if (switchAnnotation != null && setter.getParameterTypes().length == 1)
	       {	    
		  String sSwitchName = setter.getName().replaceFirst("set", "");
		  String sValue = settings.getProperty(sSwitchName);
		  if (sValue != null)
		  {
		     try
		     {
			@SuppressWarnings("rawtypes")
			Class parameterClass = setter.getParameterTypes()[0];
			if (parameterClass.equals(String.class))
			{
			   setter.invoke(this, sValue);
			}
			else if (parameterClass.equals(Boolean.class))
			{
			   setter.invoke(this, Boolean.valueOf(sValue));
			}
			else if (parameterClass.equals(Integer.class))
			{
			   setter.invoke(this, Integer.valueOf(sValue));
			}
			else if (parameterClass.equals(Double.class))
			{
			   setter.invoke(this, Boolean.valueOf(sValue));
			}
			else if (parameterClass.equals(URL.class))
			{
			   setter.invoke(this, new URL(sValue));
			}
			else if (parameterClass.equals(File.class))
			{
			   setter.invoke(this, new File(sValue));
			}
		     }
		     catch (Throwable t)
		     {
			System.err.println("Error interpreting parameter: " 
					   + sSwitchName + " : " + t);
		     }
		  } // there is a parameter
	       } // this method is a switch
	    } // next method

	    // now look for (unnamed) arguments
	    int i = 0;
	    while (settings.getProperty("arg["+i+"]") != null)
	    {
	       vArguments.add(settings.getProperty("arg["+i+"]"));
	       i++;
	    } // next argument

	 }
	 catch(Exception exception)
	 {
	    System.err.println(exception.toString());
	 }
      }

   } // end of interpretPropertiesParameters()
   
} // end of class UtilityApp
