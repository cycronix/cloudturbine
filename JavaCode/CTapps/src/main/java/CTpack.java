/**
 * CloudTurbine data-packaging utility
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 09/09/2016
 * 
 */

/*
 * Copyright 2016 Cycronix
 * All Rights Reserved
 *
 *   Date      By	Description
 * MM/DD/YYYY
 * ----------  --	-----------
 * 09/09/2016  MJM	Created.
 */

import java.io.File;
import java.util.ArrayList;
import cycronix.ctlib.*;
import org.apache.commons.cli.*;

//---------------------------------------------------------------------------------	
// CTpack:  read and re-write CT files into specified structure
// Matt Miller, Cycronix


/*
 * Copyright 2016 Cycronix
 * All Rights Reserved
 *
 *   Date      By	Description
 * MM/DD/YYYY
 * ----------  --	-----------
 * 08/30/2016  MJM	Created.
 */

//---------------------------------------------------------------------------------	

public class CTpack {
	CTreader ctr;
	CTwriter ctw;

	boolean debug=false;
	String rootFolder = "CTdata";
	String packFolder = "CTpack";

	boolean packMode=true;				// warning:  non-block audio is deadly inefficient
	boolean zipMode=true;				// zip on by default
	static long segBlocks=10;			// blocks per segment (was 10)
	static double timePerBlock=10.;		// time interval per block on output
	boolean singleFolder = false;		// pack everything into single zip file
	boolean binaryMode = false;			// convert CSV to float-binary?
	int binaryFmt = 32;				// convert to f32 or f64

	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] arg) {
		new CTpack(arg);
	}

	//--------------------------------------------------------------------------------------------------------
	public CTpack(String[] args) {

		if(!parseArgs(args)) return;

		CTinfo.setDebug(debug);
		System.err.println("CTpack output to "+packFolder+", zipMode: "+zipMode+", packMode: "+packMode+", singleFolder: "+singleFolder+", timeperBlock: "+timePerBlock+", segBlocks: "+segBlocks);
		if(!packMode) System.err.println("Warning: unpacked blocks may result in very large number of files!!");

		// setup CTreader
		if(singleFolder) {
			//			timePerBlock = Double.MAX_VALUE;
			zipMode = true;
			segBlocks = 0;
		}

		try {
			CTreader ctr = new CTreader(rootFolder);	// set CTreader to rootFolder
			// loop and read what source(s) and chan(s) are available
			ArrayList<String>sources = ctr.listSources();
			if(sources.size() == 0) System.err.println("Warning:  no sources found in rootFolder: "+rootFolder);
			for(String source:sources) {							// {Loop by Source}
				String sourcePath = rootFolder + File.separator + source;
				double oldTime = ctr.oldTime(sourcePath);		// go absolute time oldest to newest (straddling gaps)
				double newTime = ctr.newTime(sourcePath);
				System.err.println("Source: "+source+", oldTime: "+oldTime+", newTime: "+newTime);

				if(singleFolder) {			// crop time to LCD of all chans
					for(String chan:ctr.listChans(source)) {
						double thisTime = ctr.oldTime(sourcePath,chan);
						if(thisTime > oldTime) oldTime = thisTime;
						thisTime = ctr.newTime(sourcePath,chan);
						if(thisTime < newTime) newTime = thisTime;
						timePerBlock = newTime - oldTime;			// single fetch
					}
					System.err.println("Trimmed Time, Source: "+source+", oldTime: "+oldTime+", newTime: "+newTime);
				}

				// setup CTwriter for each root source
				CTwriter ctw = new CTwriter(packFolder+File.separator+source);		// new CTwriter at root/source folder
				ctw.setBlockMode(packMode,zipMode); 					// pack into binary blocks (linear timestamps per block)
				ctw.autoFlush(0);							
				ctw.autoSegment(segBlocks);								// auto create segments

				for(double thisTime=oldTime; thisTime<=newTime; thisTime+=timePerBlock) {		// {Loop by Time}
					if(debug) System.err.println("thisTime: "+thisTime);
					ctw.setTime(thisTime);			// write per request time?

					for(String chan:ctr.listChans(source)) {				// {Loop by Chan}
						if(debug) System.err.println("thisChan: "+chan);
						CTdata data = ctr.getData(source, chan, thisTime, timePerBlock-0.000001, "absolute");	// get next chunk (less 1us no-overlap?)
						/*		
     	NOTES: 
     	getData returns point-by-point CTdata with interpolated times per as-written blocks.
     	The original block boundaries are lost, but the getData times per point should accurately reflect source values.
     	On re-writing, the timestamps will be re-interpolated to new blocks, possibly straddling gaps:
     		Small gaps (e.g. due to sampling jitter) can cause small time-shifts, 
     		Large gaps (e.g. discontinuous data) can cause large time-errors
     	Options:
     	- Don't merge across segments, presuming discontinuous data in unique segments (how to detect?)
     	- Detect time-gaps when getData timestamps jump, auto-split into new blocks (heuristic?)
						 */
						double[] t = data.getTime();
						String[] sd=null;
						byte[][] bd=null;
						float[] fd=null;
						double[] dd=null;
						boolean numType = (CTinfo.fileType(chan)=='N');		// need to treat numeric data as such (needs CSV commas)
						if(numType) {
							if(binaryMode) {
								if(binaryFmt == 32) {
									if(chan.endsWith(".csv")) chan = chan.replace(".csv",  ".f32");
									else if(!chan.endsWith(".f32")) chan += ".f32";
									fd = data.getDataAsNumericF32();
								}
								else {
									if(chan.endsWith(".csv")) chan = chan.replace(".csv",  ".f64");
									else if(!chan.endsWith(".f64")) chan += ".f64";
									dd = data.getDataAsNumericF64();
								}
							}
							sd = data.getDataAsString(CTinfo.fileType(chan));	// numeric types
						}
						else			bd = data.getData();								// all other types
						//     					System.err.println("chan: "+chan+", numType: "+numType+", fileType: "+CTinfo.fileType(chan));
						if(t.length==0) {
							System.err.println("Warning, zero data for chan: "+chan+", at time: "+thisTime+":"+(thisTime+timePerBlock));
							continue;
						}

						System.err.println("putChan: "+chan+", at time[0]: "+t[0]+", dataSize: "+data.size());
						for(int j=0; j<data.size(); j++) {		// re-write fetched data per CTpack settings
							ctw.setTime(t[j]);
							if(numType) {
								if(binaryMode) {
									if(binaryFmt==32) ctw.putData(chan, fd[j]);
									else			  ctw.putData(chan, dd[j]);
								}
								else 				  ctw.putData(chan, sd[j]);
							}
							else			ctw.putData(chan, bd[j]);
						}
					}

					// write data to disk.  all-at-once, or once per block.  
					// TO DO:  add replay-timer for replay at natural pace
					if(singleFolder) {
						ctw.packFlush();			// one long block, pack all into single file
						break;
					}
					else 	ctw.flush();				// manually flush each timePerBlock
				}
			}
		}
		catch(Exception e) {
			System.err.println("CTsink exception: "+e);
			e.printStackTrace();
		} 

	}
	
	//--------------------------------------------------------------------------------------------------------
	// Argument processing using Apache Commons CLI
	// returns true if OK to proceed
	
	private boolean parseArgs(String[] args) {

		// Argument processing using Apache Commons CLI
		// 1. Setup command line options
		Options options = new Options();
		options.addOption("h", "help", false, "Print this message");
		options.addOption("x", "debug", false, "debug mode"+", default: "+debug);
		options.addOption("z", "zipmode", false, "zip mode"+", default: "+zipMode);
		options.addOption("f", "binarymode", true, "convert CSV to float binary, -f32, -f64");
		options.addOption("p", "packmode", false, "pack mode"+", default: "+packMode);
		options.addOption("1", "singleFile", false, "single zip file mode"+", default: "+singleFolder);

		options.addOption(Option.builder("o").argName("packFolder").hasArg().desc("name of output folder"+", default: "+packFolder).build());
		options.addOption(Option.builder("t").argName("timePerBlock").hasArg().desc("time per output block (sec)"+", default: "+timePerBlock).build());
		options.addOption(Option.builder("s").argName("segBlocks").hasArg().desc("blocks per segment"+", default: "+segBlocks).build());

		// 2. Parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {	line = parser.parse( options, args );	}
		catch( ParseException exp ) {	// oops, something went wrong
			System.err.println( "Command line argument parsing failed: " + exp.getMessage() );
			return false;
		}

		// 3. Retrieve the command line values
		String extraArgs[] = line.getArgs();
		if(extraArgs!=null && extraArgs.length>0) rootFolder = extraArgs[0];

		if (line.hasOption("help")) {			// Display help message and quit
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(120);
			formatter.printHelp( "CTpack [options] sourceFolder (default: "+rootFolder+"), where [options] can be: ", options );
			return false;
		}

		debug 		 = line.hasOption("debug");
		zipMode 	 = !line.hasOption("zipmode"); 				// flag turns OFF mode
		packMode 	 = !line.hasOption("packmode");
		singleFolder = line.hasOption("singleFile");

		packFolder = line.getOptionValue("o",packFolder);
		timePerBlock = Double.parseDouble(line.getOptionValue("t",""+timePerBlock));
		segBlocks = Integer.parseInt(line.getOptionValue("s",""+segBlocks));
		if(line.hasOption("binarymode")) {
			binaryMode = true;
			binaryFmt = Integer.parseInt(line.getOptionValue("f",""+32));
		}
		
		return true; 		// OK to go

		/*		// old ArgHandler way
    	if(args.length == 0) {
    		System.err.println("CTpack -x -z -p -1 -o <outFolder> -t <timePerBlock> -s <segBlocks> rootFolder");
    	}

     	int dirArg = 0;
     	while((dirArg<args.length) && args[dirArg].startsWith("-")) {		// arg parsing
     		if(args[dirArg].equals("-x")) 	debug = true;
     		if(args[dirArg].equals("-z")) 	zipMode = false;				// turn OFF zip
     		if(args[dirArg].equals("-p")) 	packMode = false;				// turn OFF packed blocks

     		if(args[dirArg].equals("-o"))  	packFolder = args[++dirArg]; 
     		if(args[dirArg].equals("-t"))  	timePerBlock = Double.parseDouble(args[++dirArg]); 
     		if(args[dirArg].equals("-s"))  	segBlocks = Integer.parseInt(args[++dirArg]); 

     		if(args[dirArg].equals("-1")) 	singleFolder = true;

     		dirArg++;
     	}
     	if(args.length > dirArg) rootFolder = args[dirArg++];  
		 */	
	}
	
} //end class CTpack
