/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

// ArgHandler - utility class for building and parsing commmand line arguments
// Eric Friets
// January 2001
// Copyright 2001 Creare Incorporated

package cycronix.ctlib;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Utility class for building and parsing commmand line arguments.
 * ArgHandler converts between command line arguments and options.  If
 * constructed with a single space-delimited string or an array of strings,
 * developers can query for the presence of particular flags and their
 * associated options, if any.  Flags and associated options can also be
 * added individually, and a complete argument string or array of strings
 * created for use in starting a PlugIn or application.
*/

// 02/11/2002  WHF  Added overloaded methods to getOption which 
//   return a default if option not found, eliminating silly if(null)
//  checks from user code.

public class ArgHandler {
private Hashtable hash=new Hashtable();

//main method for testing
/*public static void main(String[] argv) {
  String args=new String("-r me:33 -d -g foo -hgoo");
  try {
    ArgHandler ah=new ArgHandler(argv);
    System.err.println("ArgHandler string: "+ah.getArgString());
    String[] argvec=ah.getArgArray();
    for (int i=0;i<argvec.length;i++) {
      System.err.println("ArgHandler "+i+": "+argvec[i]);
    }
  } catch (Exception e) {
    e.printStackTrace();
  }
} */

//constructors

/**
  * default constructor - used when building up flags and options piecemeal
  */
public ArgHandler() {
} //end default constructor

/**
  * construct from space-delimited string by converting to string array,
  * then parsing
  */
/**
 * construct from space-delimited string by converting to string array, then parsing
 * @param arg argument 
 * @throws Exception on error
 */
public ArgHandler(String arg) throws Exception {
  Vector v=new Vector();
  int last=-1;
  int next=-1;
  while ((next=arg.indexOf(" ",last+1))!=-1) {
    v.addElement(arg.substring(last+1,next));
    last=next;
  }
  v.addElement(arg.substring(last+1));
  String[] args=new String[v.size()];
  v.copyInto(args);
  parseArgs(args);
} //end String constructor


/**
 * construct from string array by parsing
 * @param args arguments
 * @throws Exception on error
 */
public ArgHandler(String[] args) throws Exception {
  parseArgs(args);
} //end String[] constructor

//parsing method - note that options may be with or following the flag
private void parseArgs(String[] args) throws Exception {
  for (int i=0;i<args.length;i++) {
    if (!args[i].startsWith("-")) {
      throw new Exception("Incorrect format.  Flags must be preceeded by a dash.");
    }
    char flag=args[i].charAt(1);
    String option=null;
    if (args[i].length()>2) {
      option=args[i].substring(2);
    } else if (++i<args.length && !args[i].startsWith("-")) {
      option=args[i];
    } else {
      option=new String("");
      i--;
    }
    hash.put(new Character(flag),option);
  }
} //end parseArgs method

//methods to add fiags and fields

/**
 * add flag with no option
 * @param flag Flag to add
 * @return T/F replace old flag
 */
public boolean addFlag(char flag) {
  Character cflag=new Character(flag);
  String option=new String("");
  Object oldOption=hash.put(cflag,option);
  if (oldOption!=null) {
    hash.put(cflag,oldOption);
    return false;
  }
  return true;
}

/**
 * add flag with option
 * @param flag Flag to add
 * @param option flagged option
 * @return T/F replace old flag
 */
public boolean addFlag(char flag, String option) {
  Character cflag=new Character(flag);
  Object oldOption=hash.put(cflag,option);
  if (oldOption!=null) {
    hash.put(cflag,oldOption);
    return false;
  }
  return true;
}

/**
 * clear flag
 * @param flag Flag to clear
 */
public void clearFlag(char flag) {
  Character cflag=new Character(flag);
  hash.remove(cflag);
} //end method clearFlag

//methods to return complete argument lists

/**
 * return argument list as space-delimited string
 * @return string with arg list
 */
public String getArgString() {
  StringBuffer sb=new StringBuffer();
  Enumeration flags=hash.keys();
  while (flags.hasMoreElements()) {
    Character flag=(Character)flags.nextElement();
    sb.append('-').append(flag.charValue()).append(' ');;
    String option=(String)hash.get(flag);
    if (!option.equals("")) {
      sb.append(option).append(' ');;
    }
  }
  return sb.toString();
} //end method getArgString

/**
 * return argument list as string array.  Options are separated from flags.
 * @return string array of args 
 */
public String[] getArgArray() {
  Vector v=new Vector();
  Enumeration flags=hash.keys();
  while (flags.hasMoreElements()) {
    Character flag=(Character)flags.nextElement();
    v.addElement("-"+flag.toString());
    String option=(String)hash.get(flag);
    if (!option.equals("")) {
      v.addElement(option);
    }
  }
  String[] argArray=new String[v.size()];
  v.copyInto(argArray);
  return argArray;
} //end method getArgArray

//methods to test for flags and get their options

/**
 * test for flags and get their options
 * @param flag Flag to test
 * @return T/F
 */
//tests for flag
public boolean checkFlag(char flag) {
  Character cflag=new Character(flag);
  Object value=hash.get(cflag);
  if (value==null) {
    return false;
  } else {
    return true;
  }
} //end method getFlag

/**
 * returns option for specified flag, or null if no option set
 * @param flag Flag to check
 * @return T/F
 */
public String getOption(char flag) {
  Character cflag=new Character(flag);
  String value=(String)hash.get(cflag);
  if (value==null || value.equals("")) {
    return null;
  } else {
    return value;
  }
} //end method getOption

/**
  * Returns option for specified flag, or <code>defaultOption</code>
  *  if no option set.
  */
/**
 * Returns option for specified flag, or <code>defaultOption</code> if no option set.
 * @param flag specified flag
 * @param defaultOption default
 * @return option string
 */
public String getOption(char flag, String defaultOption) {
  Character cflag=new Character(flag);
  String value=(String)hash.get(cflag);
  if (value==null || value.equals("")) {
    return defaultOption;
  } else {
    return value;
  }
} //end method getOption

} //end class ArgHandler
