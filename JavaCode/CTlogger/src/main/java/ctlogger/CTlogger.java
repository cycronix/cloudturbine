/*
Copyright 2014-2017 Cycronix

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package ctlogger;

//---------------------------------------------------------------------------------	
// CTlogger:  parse data logger file into CloudTurbine (zip) files
// Matt Miller, Cycronix
// 02/25/2014  	Initial version
// 08/06/2014	Add -H specify header line via argument
// 02/18/2017	JPW, add command line parsing using Apache Commons CLI

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
// Because java.text.ParseException is imported above, we can't import the CLI ParseException here
// import org.apache.commons.cli.ParseException;

import cycronix.ctlib.CTreader;
import cycronix.ctlib.CTwriter;

public class CTlogger {
	static boolean zipmode=true;			// true:  zip files
	static boolean debug=false;				// debug prints
	static boolean gzipmode=false;			// gzip for extra compression
	static boolean appendMode=true;			// append to end of existing CT data
	static boolean newFileMode=false;		// re-parse file every time 
	static long autoflush = 3600;			// autoflush interval (sec)
	static long pollInterval=60;			// default 1 minute (60 sec) polling interval
	static long skipLines = 2;				// # lines to skip between first chanNames line to data
	static boolean repeatFetch = false;		// auto-fetch data loop 
	static boolean storeTime = false;		// store time string as channel
	static String nanVal = "0";				// replace NAN with this
	static String loggerFileName=null;		// name of input data file to parse
	static String CTrootfolder="CTdata";	// top level folder to write CT data
	static String leadingID=null;			// leading ID string (IWG1 compliant)
	static String SourceName="CTlogger";	// CT source name
	static String HeaderLine=null;			// optional header line of channel names argument
	static double trimTime=0.;				// amount of data to keep (ring buffer).  
	static boolean noBackwards=false;		// no backwards-going time allowed
	static boolean blockMode=false;			// block-mode CTwrite
	
	public static void main(String args[]) {
		
		/**
		 * 
		 * Original code for command line parsing
		 * (This has been replaced by code using Apache Commons CLI, see below)
		 *
		String helpMsg = "CTlogger -x -r -z -g -k <skiplines> -f <flush_sec> -p <poll_sec> -n <nanVal> -i <leadingID> -s <SourceName> -H <HeaderLine> <logger.dat> <CTfolder>";
     	int dirArg = 0;
     	while((dirArg<args.length) && args[dirArg].startsWith("-")) {		// arg parsing
     		if(args[dirArg].equals("-h")) 	{ 
    			System.err.println(helpMsg);
    			System.exit(0);
     		}
     		if(args[dirArg].equals("-x")) 	{ debug = true;   }
     		if(args[dirArg].equals("-b")) 	{ noBackwards = true;   }
     		if(args[dirArg].equals("-g")) 	{ gzipmode = true;   }			// default false
     		if(args[dirArg].equals("-a")) 	{ appendMode = false;   }		// default true
     		if(args[dirArg].equals("-z"))  	{ zipmode = false; } 			// default true
     		if(args[dirArg].equals("-N")) 	{ newFileMode = true;   }		// default false
     		if(args[dirArg].equals("-f"))   { autoflush = Long.parseLong(args[++dirArg]); }
     		if(args[dirArg].equals("-p"))   { pollInterval = Long.parseLong(args[++dirArg]); }
     		if(args[dirArg].equals("-k"))   { skipLines = Long.parseLong(args[++dirArg]); }
     		if(args[dirArg].equals("-r"))   { repeatFetch = true; }	
     		if(args[dirArg].equals("-B"))   { blockMode = true; }	
     		if(args[dirArg].equals("-t"))   { storeTime = true; }	
     		if(args[dirArg].equals("-T"))   { trimTime = Double.parseDouble(args[++dirArg]); }
     		if(args[dirArg].equals("-n"))	{ nanVal = args[++dirArg]; }
     		if(args[dirArg].equals("-i"))	{ leadingID = args[++dirArg]; }
     		if(args[dirArg].equals("-s"))	{ SourceName = args[++dirArg]; }
     		if(args[dirArg].equals("-H"))	{ HeaderLine = args[++dirArg]; }
     		dirArg++;
     	}
		if(args.length < (dirArg+2)) {
			System.err.println(helpMsg);
			System.exit(0);
		}
		loggerFileName = args[dirArg++];		// args[0]:  logger.dat file
		CTrootfolder = args[dirArg++];			// args[1]:  CT destination folder     	
		*/
		
		//
		// Parse command line arguments
		//
		// 1. Setup command line options
		//
		Options options = new Options();
		// Boolean options (only the flag, no argument)
		options.addOption("h", "help", false, "Print this message");
		options.addOption("x", "debug", false, "turn on debug output");
		options.addOption("b", "nobackwards", false, "no backwards-going time allowed");
		options.addOption("g", "gzipmode", false, "turn on gzip for extra compression");
		options.addOption("a", "noappend", false, "turn off append mode (i.e., do not append to end of existing CT data)");
		options.addOption("z", "nozip", false, "turn off zip mode (it is on by default)");
		options.addOption("N", "newfilemode", false, "re-parse entire logger file every time it is checked");
		options.addOption("r", "repeatFetch", false, "turn on repeat fetch (auto-fetch data loop)");
		options.addOption("B", "blockMode", false, "turn on CloudTurbine writer block mode (multiple points per output data file, packed data)");
		options.addOption("t", "storeTime", false, "store time string as a channel; time is the first data entry in each line; if this option is not specified, then the time channel is skipped/not saved to CloudTurbine");
		// Options with an argument
		Option outputFolderOption = Option.builder("f")
				.argName("autoflush")
                .hasArg()
                .desc("flush interval (sec); default = \"" + autoflush + "\"")
                .build();
		options.addOption(outputFolderOption);
		outputFolderOption = Option.builder("p")
				.argName("pollInterval")
                .hasArg()
                .desc("if repeatFetch option has been specified, recheck the logger data file at this polling interval (sec); default = \"" + pollInterval + "\"")
                .build();
		options.addOption(outputFolderOption);
		outputFolderOption = Option.builder("k")
				.argName("skipLines")
                .hasArg()
                .desc("in logger file, the num lines to skip after the header line to get to the first line of data; default = \"" + skipLines + "\"")
                .build();
		options.addOption(outputFolderOption);
		outputFolderOption = Option.builder("T")
				.argName("trimTime")
                .hasArg()
                .desc("trim (ring-buffer loop) time (sec) (trimTime=0 for indefinite); default = \"" + trimTime + "\"")
                .build();
		options.addOption(outputFolderOption);
		outputFolderOption = Option.builder("n")
				.argName("nanVal")
                .hasArg()
                .desc("replace NAN with this; default = \"" + nanVal + "\"")
                .build();
		options.addOption(outputFolderOption);
		outputFolderOption = Option.builder("i")
				.argName("leadingID")
                .hasArg()
                .desc("leading ID string (IWG1 compliant)")
                .build();
		options.addOption(outputFolderOption);
		outputFolderOption = Option.builder("s")
				.argName("sourceName")
                .hasArg()
                .desc("CloudTurbine source name; default = \"" + SourceName + "\"")
                .build();
		options.addOption(outputFolderOption);
		outputFolderOption = Option.builder("H")
				.argName("HeaderLine")
                .hasArg()
                .desc("optional CSV list of channel names; if not supplied, this is read from the first line in the logger file")
                .build();
		options.addOption(outputFolderOption);
		outputFolderOption = Option.builder("l")
				.argName("loggerfilename")
                .hasArg()
                .desc("name of the logger data file; required argument")
                .build();
		options.addOption(outputFolderOption);
		outputFolderOption = Option.builder("o")
                .longOpt("outputfolder")
				.argName("folder")
                .hasArg()
                .desc("Location of output files (source is created under this folder); default = " + CTrootfolder)
                .build();
		options.addOption(outputFolderOption);

		//
		// 2. Parse command line options
		//
	    CommandLineParser parser = new DefaultParser();
	    CommandLine line = null;
	    try {
	        line = parser.parse( options, args );
	    }
	    catch( org.apache.commons.cli.ParseException exp ) {
	        // oops, something went wrong
	        System.err.println( "Command line argument parsing failed: " + exp.getMessage() );
	     	return;
	    }
	    
	    //
	    // 3. Retrieve the command line values
	    //
	    if (line.hasOption("help")) {
	    	// Display help message and quit
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.printHelp( "CTlogger", options );
	    	return;
	    }
	    debug = line.hasOption("x");
		noBackwards = line.hasOption("b");
		gzipmode = line.hasOption("g");
		appendMode = !line.hasOption("a");
		zipmode = !line.hasOption("z");
		newFileMode = line.hasOption("N");
		repeatFetch = line.hasOption("r");
		blockMode = line.hasOption("B");
		storeTime = line.hasOption("t");
		autoflush = Long.parseLong(line.getOptionValue("f",Long.toString(autoflush)));
		pollInterval = Long.parseLong(line.getOptionValue("p",Long.toString(pollInterval)));
		skipLines = Long.parseLong(line.getOptionValue("k",Long.toString(skipLines)));
		trimTime = Double.parseDouble(line.getOptionValue("T",Double.toString(trimTime)));
		nanVal = line.getOptionValue("n",nanVal);
		if (line.hasOption("i")) {
			leadingID = line.getOptionValue("i");
		}
		SourceName = line.getOptionValue("s",SourceName);
		if (line.hasOption("H")) {
			HeaderLine = line.getOptionValue("H");
		}
		if (line.hasOption("l")) {
			loggerFileName = line.getOptionValue("l");
		} else {
			System.err.println("ERROR: you must supply the logger file name.");
			return;
		}
		CTrootfolder = line.getOptionValue("o",CTrootfolder);
		
		if (!debug) {
			System.err.println("CTlogger: "+loggerFileName+", CTrootfolder: "+CTrootfolder+", pollInterval: "+pollInterval);
		} else {
			System.err.println("debug = " + debug);
			System.err.println("noBackwards = " + noBackwards);
			System.err.println("gzipmode = " + gzipmode);
			System.err.println("appendMode = " + appendMode);
			System.err.println("zipmode = " + zipmode);
			System.err.println("newFileMode = " + newFileMode);
			System.err.println("repeatFetch = " + repeatFetch);
			System.err.println("blockMode = " + blockMode);
			System.err.println("storeTime = " + storeTime);
			System.err.println("autoflush = " + autoflush);
			System.err.println("pollInterval = " + pollInterval);
			System.err.println("skipLines = " + skipLines);
			System.err.println("trimTime = " + trimTime);
			System.err.println("nanVal = " + nanVal);
			System.err.println("leadingID = " + leadingID);
			System.err.println("SourceName = " + SourceName);
			System.err.println("HeaderLine = " + HeaderLine);
			System.err.println("loggerFileName = " + loggerFileName);
			System.err.println("CTrootfolder = " + CTrootfolder);
		}
		
		//
		// Run CTlogger
		//
		if(!repeatFetch) getData(true);		// run once
		else {
			Timer timer = new Timer();
			TimerTask fetchTask = new TimerTask() {
				@Override public void run() {
					if(newFileMode) getData(true);
					else
					if(getData(false)) {		// pick up from old data if you can
						System.err.println("Failed to pick up from old data, refetch from start of file...");
						boolean status = getData(true);	
						System.err.println("refetch status: "+status);
					}
					if(debug) System.err.println("Waiting for data, pollInterval: "+pollInterval+" sec...");
				};
			};
			// repeatFetch@autoflush interval, convert to msec
			if((autoflush>0) && (pollInterval > autoflush)) pollInterval = autoflush;
			timer.scheduleAtFixedRate(fetchTask,  0,  pollInterval*1000);		
		}
	}
	
//---------------------------------------------------------------------------------	
	static BufferedReader br=null;					// re-useable call-to-call
	static ArrayList<String>chanNames=null;
	static CTwriter ctw=null;
	static CTreader ctreader=null;
	static SimpleDateFormat sdf1=null, sdf2=null, sdf3=null;
	static boolean firstrun=true;
	
	private static boolean getData(boolean newfile) {
		ArrayList <String>data;
		long appendTime = 0;
		if(debug) System.err.println("CTlogger fetch: "+loggerFileName);

		try {
			// logger file header parse
			if(newfile || (br==null)) {		// new file
				br = new BufferedReader(new FileReader(new File(loggerFileName)));
		
				// Channel names
				if(HeaderLine == null) HeaderLine = br.readLine();
				chanNames = new ArrayList<String>(Arrays.asList(HeaderLine.replace("\"","").split(",")));

				// Optionally skip lines
				for(int i=0; i<skipLines; i++) br.readLine();
				
				// CTwriter setup
				String sourceFolder = CTrootfolder+File.separator+SourceName;
				ctw = new CTwriter(sourceFolder, trimTime);
				
				if(appendMode) {
					ctreader = new CTreader(CTrootfolder);
					// JPW, change from sourceFolder to SourceName (ie, not full path)
					appendTime = (long)(ctreader.newTime(SourceName) * 1000);	 // convert sec to msec
					if(debug) System.err.println("appending past time: "+appendTime);
				}
				ctw.setDebug(debug);
				if(autoflush>0) ctw.autoFlush(autoflush*1000);		// auto flush to zip once per interval (msec) of data
				System.err.println("autoflush: "+autoflush+", blockMode: "+blockMode+", zipmode: "+zipmode);
				ctw.setBlockMode(blockMode,zipmode);	// blockMode doesn't work well, should flush(blockDur), but then interpolates intermediate timestamps
//				ctw.setZipMode(zipmode);			//  compress none-most, 0-9, 10=zip.gz
				ctw.setGZipMode(gzipmode);
				ctw.autoSegment(100);				// MJM 12/2/16
				
				// parsable time formats
				sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				sdf2 = new SimpleDateFormat("MM/dd/yyyy HH:mm");
				sdf3 = new SimpleDateFormat("yyyy/MM/dd'T'HH:mm:ss");
			}

			// parse data lines
			long otime = 0;
			String line;
			int lineCount = 0;
			int idxTime = 0;
			if(leadingID != null) idxTime = 1;		// skootch
			int idxFirstChan = idxTime+1;
			if(storeTime) idxFirstChan = idxTime;
			long oldTime=0;
			long adjTime=0;
			
			while ((line = br.readLine()) != null) {
				line = line.replace("\"","");			// remove extra quote chars
				line = line.replace(".txt", "");		// strip trailing ".txt" 
				line = line.replace("NAN", nanVal);		// replace "NAN" with legal number
				data = new ArrayList<String>(Arrays.asList(line.split(",")));
				long time = 0;
				// first-pass:  see if time is integer seconds or msec
				try {
					long tsec = Long.parseLong(data.get(idxTime));
					if(tsec > 1e10) time = tsec;				// msec
					else			time = 1000 * tsec;			// sec -> msec
				} catch(Exception e0) {
//					System.err.println("unparsed as long: "+line);
					try {						
						time = sdf1.parse(data.get(idxTime)).getTime();		// primary date format
					} catch(ParseException e1) {	
						try {
							time = sdf2.parse(data.get(idxTime)).getTime();		// alternate date format
						} catch(ParseException e2) {
							try {
								time = sdf3.parse(data.get(idxTime)).getTime();		// alternate date format
							} catch(ParseException e3) {
								errPrint("Exception, ignored: "+e3+", line: "+line);
								continue;
							}
						}
					}
				}

				if(time < 0) {
					errPrint("illegal timestamp, ignored: "+data.get(0));
					continue;
				}
				if(time <= otime && noBackwards) {
					errPrint("backwards-going time, ignored: "+data.get(0));			// warning
					continue;
				}
				if(time <= appendTime) {
//					errPrint("skipping past existing time: "+time+" < "+appendTime);	// debug
					continue;			// skip past existing data
				}
				
				System.err.println("timestr: "+data.get(idxTime)+", time: "+time+", time2str: "+new Date(time));
				if(time==oldTime) {
					adjTime++;			// prevent time dupes
				}
				else {
					oldTime = time;
					adjTime = time;
				}
				
				lineCount++;
				otime = time;
				if(debug) System.err.println("getData time: "+time+", data(0): "+data.get(0));
				ctw.setTime(adjTime);
				if(leadingID != null) {
					if(!data.get(0).equals(leadingID)) {
						errPrint("Error, leading ID mismatch.  Expected: "+leadingID+", got: "+data.get(0));
						continue;
					}
				}
				// write CT data (skip ID & optionally Time)
//				System.err.println("data.size: "+data.size()+", chanNames.size: "+chanNames.size());
				int firstChanName=0;
//				if(HeaderLine != null) firstChanName=1;			// mjm 10/2015:  why skip first?  causes overflow on chanNames j
				
//				System.err.println("--------idxFirstChan: "+idxFirstChan+", data.size: "+data.size()+", chanNames.size: "+chanNames.size());
//				for(int i=idxFirstChan, j=0; i<data.size(); i++,j++) {
				for(int i=idxFirstChan, j=firstChanName; i<data.size(); i++,j++) {				// if first-chanName (Time) column is labelled 
//					System.err.println("i: "+i+", j: "+j+", chanNames[j]: "+chanNames.get(j)+", data.get[i]: "+data.get(i));
					if(blockMode) 	ctw.putData(chanNames.get(j)+".f32", new Float(data.get(i)));
//					if(blockMode) 	ctw.addData(chanNames.get(j), new Float(data.get(i)));
					else			ctw.putData(chanNames.get(j), data.get(i));
//					if(i>100) break;			// MJM foo debug, problem with huge number of columns (chans)
				}
//				ctw.flush();
			}
			ctw.flush();								// clean up after last line read this pass
			if(newfile) { br.close(); br=null; }		// close if newfile mode
			if(lineCount>0 || firstrun) System.err.println("CTlogger processed "+loggerFileName+", lines: "+lineCount);
			firstrun=false;
//			if(debug) System.err.println("getData at EOF");
		} catch(Exception e) {
			errPrint("CTlogger exception: "+e);
			e.printStackTrace();
			return true;		// something went wrong
		}
		return false;			// OK
	}
	
	static long ecount = 0;								// limit exception debug prints
	static final long emax=1000;
	private static void errPrint(String msg) {
		if(++ecount <= emax) System.err.println(msg);
		if(ecount == emax) System.err.println("WARNING:  max errors exceeded, no more errors will be printed!");
	}

}
