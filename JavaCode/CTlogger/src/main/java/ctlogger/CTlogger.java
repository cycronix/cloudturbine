package ctlogger;

//---------------------------------------------------------------------------------	
// CTlogger:  parse data logger file into CloudTurbine (zip) files
// Matt Miller, Cycronix
// 02/25/2014  	Initial version
// 08/06/2014	Add -H specify header line via argument

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

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
	static String CTrootfolder=null;		// top level folder to write CT data
	static String leadingID=null;			// leading ID string (IWG1 compliant)
	static String SourceName="CTlogger";	// CT source name
	static String HeaderLine=null;			// optional header line of channel names argument
	static String helpMsg = "CTlogger -x -r -z -g -k <skiplines> -f <flush_sec> -p <poll_sec> -n <nanVal> -i <leadingID> -s <SourceName> -H <HeaderLine> <logger.dat> <CTfolder>";
	static double trimTime=0.;				// amount of data to keep (ring buffer).  
	static boolean noBackwards=false;		// no backwards-going time allowed
	static boolean blockMode=false;			// block-mode CTwrite
	
	public static void main(String args[]) {

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

		System.err.println("CTlogger: "+loggerFileName+", CTrootfolder: "+CTrootfolder+", pollInterval: "+pollInterval);
		
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
					appendTime = (long)(ctreader.newTime(sourceFolder) * 1000);	 // convert sec to msec
					if(debug) System.err.println("appending past time: "+appendTime);
				}
				ctw.setZipMode(zipmode);			//  compress none-most, 0-9, 10=zip.gz
				ctw.setDebug(debug);
				if(autoflush>0) ctw.autoFlush(autoflush*1000);		// auto flush to zip once per interval (msec) of data
				ctw.setBlockMode(blockMode);	// blockMode doesn't work well, should flush(blockDur), but then interpolates intermediate timestamps
				ctw.setGZipMode(gzipmode);
				
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
				
				lineCount++;
				otime = time;
				if(debug) System.err.println("getData time: "+time+", data(0): "+data.get(0));
				ctw.setTime(time);
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
