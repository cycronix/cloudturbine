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
//---------------------------------------------------------------------------------	
// CT2CSV:  CloudTurbine to CSV file converter
// 08/01/2014 initial version

package ct2csv;

import java.io.File;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;

import cycronix.ctlib.*;

//---------------------------------------------------------------------------------	

public class CT2CSV {
	
	private static String outFolder="CTcsv";
	private static String rootFolder=".";		// for compat with CT2DB
	private static CTreader ctreader=null;
	private static boolean debug=false;
	private static boolean swapFlag = false;
	private static int MaxDat = 10000;			// max number data elements to return
	private static PrintWriter csvout;
	
	//---------------------------------------------------------------------------------	
 	// constructor
	public CT2CSV() {
	}
	
	//---------------------------------------------------------------------------------	

	public static void main(String[] args) throws Exception { 
		ArrayList<String>slist = new ArrayList<String>();	// source list
		String timespec = null;
		double tstart = 0., tduration=Double.MAX_VALUE;		// time units: seconds ref oldest
		
		// Handle Arguments
		int dirArg = 0;
		while((dirArg<args.length) && args[dirArg].startsWith("-")) {		// arg parsing
			if(args[dirArg].equals("-h")) {
				System.err.println("java -jar CT2CSV.jar -h -x -r -n <limit> -t <start,duration> -o <outFolder> -f <rootFolder> [source(s)]");
				System.err.println("where:");
				System.err.println("-h              display help message");
				System.err.println("-x              turn on debug output");
				System.err.println("-r              swap byte order; with this flag, interpret data as \"big-endian\", without this flag use \"little-endian\"");
				System.err.println("-n <limit>      limits number of output lines, default=" + MaxDat + ", set this option to increase");
				System.err.println("-t <start,dur>  specify offset from start of data plus duration to extract (sec)");
				System.err.println("-o <outFolder>  output folder for CSV files (default = ./" + outFolder + ")");
				System.err.println("-f <rootFolder> root folder containing data source folders, default=CTdata or CloudTurbine");
				System.err.println("[source(s)]     space-delimited list of one or more data sources; default=all available sources in rootFolder");
				System.exit(0);
			}
			if(args[dirArg].equals("-x")) 	debug = true;
			if(args[dirArg].equals("-r")) 	swapFlag = true;
			if(args[dirArg].equals("-n")) 	MaxDat = Integer.parseInt(args[++dirArg]);
			// if(args[dirArg].equals("-s"))	slist.add(new File(args[++dirArg]).getName());   // Not currently used
			if(args[dirArg].equals("-t"))	timespec = args[++dirArg];
			if(args[dirArg].equals("-o"))	outFolder = args[++dirArg];
			if(args[dirArg].equals("-f"))	rootFolder = args[++dirArg];
			
			dirArg++;
		}
		for(int i=dirArg; i<args.length; i++) slist.add(new File(args[i]).getName());		// add source(s) to slist (strip full path)
//		for(int i=dirArg; i<args.length; i++) slist.add(args[i]);		// add source(s) to slist

//		if(args.length > dirArg) rootFolder = args[dirArg++];
		
		// if(rootFolder.equals(".") && slist.size()==0) {		// no root or source specified: check for a couple defaults
		if(rootFolder.equals(".")) {	// no root folder specified: check for a couple defaults
			if		(new File("CTdata").exists()) 		rootFolder = "CTdata";
			else if (new File("CloudTurbine").exists()) rootFolder = "CloudTurbine";
//			else {
//				System.err.println("Cannot find default data folder.  Please specify.");
//				System.exit(0);	
//			}
		}
		
		if(!(new File(rootFolder).exists())) {
			System.err.println("Cannot find specified data folder: "+rootFolder);
			System.exit(0);	
		}
		
		// optionally specify time-interval
		if(timespec != null) {
			String[] tvals = timespec.split(",");
			if(tvals.length > 0) tstart = Double.parseDouble(tvals[0]);
			if(tvals.length > 1) tduration = Double.parseDouble(tvals[1]);
			System.err.println("Interval start: "+tstart+" (sec), time duration:  "+tduration+" (sec)");
		}
		
		// initialize
		ctreader = new CTreader(rootFolder);
		ctreader.setDebug(debug);

		if(slist.size() == 0) slist = ctreader.listSources();		// all sources if not specified
		if(slist.size()==0) {
			System.err.println("Warning:  Cannot find any sources!");
			System.exit(0);
		}
		
		new File(outFolder).mkdirs();

		// loop thru sources and channels, build TimeNameValue ArrayList for each source
		int nout=0;
		for(String source : slist) {								// for each source
			csvout = new PrintWriter(outFolder+File.separator+source+".csv");
			
			System.err.println("Processing source: "+source);
//			output(source+"\n");
			ArrayList<TimeNameValue> TNV = new ArrayList<TimeNameValue>();
			ArrayList<String> clist = ctreader.listChans(rootFolder+File.separator+source);
			if(clist == null) {
				System.err.println("Warning:  no channels in source: "+source);
				continue;
			}
			
			boolean warnonce=true;
			for(String chan : clist) {															// for each channel in source
				CTdata tdata = ctreader.getData(source,chan,tstart,tduration,"oldest");	// get all data 
				if(tdata != null) {		// got data
					tdata.setSwap(swapFlag);
					double time[] = tdata.getTime();
					int numData = time.length;
					if(numData > MaxDat) {
						if(warnonce) {
							System.err.println(source+": limiting output lines: "+MaxDat+" (use option -n to change)");
							warnonce=false;
						}
						numData = MaxDat;
					}
					char ftype = CTinfo.fileType(chan);

					String[] dstring = tdata.getDataAsString(ftype);
					if(dstring != null) {
						for(int i=0; i<numData; i++) {
							TNV.add(new TimeNameValue(time[i], chan, dstring[i]));
						}
					}
					else {
						System.err.println(source+"/"+chan+": "+chan+", unrecognized type: "+ftype);
					}	
				}	
			}	
			
			// now re-sort by time and spit out in CSV format
			Collections.sort(TNV, new TNVsort());
			
			if(TNV.size() == 0) {
				System.err.println("Warning:  No data processed from source: "+source);
				continue;
			}

			++nout;
			output("DateTime");			// print header
			for(int i=0; i<clist.size(); i++) output(","+clist.get(i));
			output("\n");

			double told=TNV.get(0).time;			// initialize	
			HashMap<String,String>vmap = new HashMap<String,String>();

			for(TimeNameValue tnv:TNV) {
				if(tnv.time > told) {					// output line if time advances past prior
					printLine(clist, formatTime(told), vmap);
					told = tnv.time;
					vmap.clear();
				}
				vmap.put(tnv.name, tnv.value);
			}
			printLine(clist, formatTime(told), vmap);		// last line
			csvout.close();
		}
		
		if(nout>0) System.err.println("Results in folder: "+outFolder);
		else	   System.err.println("Warning:  no data processed.");
	}

	//---------------------------------------------------------------------------------	
	// print a CSV line of values	
	private static void printLine(ArrayList<String>clist, String time, HashMap<String,String>vmap) {
		output(time);
		String vc;
		for(int i=0; i<clist.size(); i++) {	
			vc = vmap.get(clist.get(i));
			if(vc != null) 	output(","+vc);
			else			output(",");		// v may contain empty string if value missing
		}
		output("\n");
	}
	
	//---------------------------------------------------------------------------------	
	// output a line
	private static void output(String line) {
		if(debug) System.out.print(line);
		csvout.print(line);
		csvout.flush();
	}
	
	// format time string
	private static String formatTime(double time) {
//		SimpleDateFormat ft, fd;
		SimpleDateFormat fdt;
//		fd = new SimpleDateFormat("MM/dd/yyyy");
//		if(time == Math.floor(time)) ft = new SimpleDateFormat ("HH:mm:ss");
//		else						 ft = new SimpleDateFormat ("HH:mm:ss.SSS");
		Date d = new Date((long)(time*1000.));
		fdt = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss.SSS");
//	    return fd.format(d) + "," + ft.format(d) + "," + fdt.format(d);
	    return fdt.format(d);
	}
	

 }

//---------------------------------------------------------------------------------	
//  time name value triple data object
class TimeNameValue {
	double time;
    String name;
    String value;

    TimeNameValue(double time, String name, String value) {
    	this.time = time;
        this.name = name;
        this.value = value;
    }
}

// sort TimeNameValue ArrayList first by time, then by chanName
class TNVsort implements Comparator<TimeNameValue> {
	@Override
	public int compare(TimeNameValue tnv1, TimeNameValue tnv2) {
		if(tnv1.time > tnv2.time) 	return 1;
		else if(tnv1.time == tnv2.time) {
			return tnv1.name.compareTo(tnv2.name);
		}
		else						return -1;
	}
}