/*
Copyright 2018 Cycronix

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

package ctblocktest;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import cycronix.ctlib.*;

/**
 * CloudTurbine example: test various block formats
 * Writes same data in permutations of 3 cases: pack, zip, num (8 total tests)
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 09/01/2016
 * 
*/

public class CTblocktest {
	static int nsamp = 1000;			// total number of data samples (per chan) was 1000
	static int nchan = 10;				// number of channels 	was 10
	static double dt = 1.;				// sec per point
	static long blockPts=10;			// points per block
	static long segBlocks=10;			// blocks per segment (was 10)
	static boolean relativeTime=true;	// relative (vs absolute) times
	static boolean readCheck=false;		// perform cross-check on results
	
	static boolean debug=false;
	static String sourceFolder="CTblocktest";
	
	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] args) {
		new CTblocktest(args);
	}

	//--------------------------------------------------------------------------------------------------------
	public CTblocktest(String[] args) {
				
		if(!parseArgs(args)) return;
		CTinfo.setDebug(debug);
		if(readCheck) System.err.println("Note: for reliable readCheck delete any pre-existing data at: "+sourceFolder);
		
		// run each permutation of packMode, zipMode, numMode
		System.err.println("starting writeTests...");
		runTest("CTbin",		false,	false,	false);
		runTest("CTpackbin",	true,	false,	false);
		runTest("CTpackzipbin", true,	true,	false);
		runTest("CTzipbin",		false,	true,	false);
		runTest("CTnum",		false,	false,	true);
		runTest("CTpacknum",	true,	false,	true);
		runTest("CTpackzipnum", true,	true,	true);
		runTest("CTzipnum",		false,	true,	true);
		System.err.println("Done Writing. nsamp: "+nsamp+", nchan: "+nchan+", pts/block: "+blockPts+", blocks/segment: "+segBlocks);
		
		// cross-check same data all cases
		if(readCheck) {
			int errCount=0;
			System.err.println("starting readChecks...");
			errCount += crossCheck("CTbin",			false,	false,	false);
			errCount += crossCheck("CTpackbin",		true,	false,	false);
			errCount += crossCheck("CTpackzipbin", 	true,	true,	false);
			errCount += crossCheck("CTzipbin",		false,	true,	false);
			errCount += crossCheck("CTnum",			false,	false,	true);
			errCount += crossCheck("CTpacknum",		true,	false,	true);
			errCount += crossCheck("CTpackzipnum", 	true,	true,	true);
			errCount += crossCheck("CTzipnum",		false,	true,	true);
			if(errCount==0) System.err.println("Done checking, SUCCESS");
			else			System.err.println("Done checking, FAIL ("+errCount+" Errors)");
		}
	}
	
	//--------------------------------------------------------------------------------------------------------
	static void runTest(String modeName, boolean packMode, boolean zipMode, boolean numMode) {
		try {
			CTwriter ctw = new CTwriter(sourceFolder+File.separator+modeName);		// new CTwriter at root/source folder

			ctw.setTimeRelative(relativeTime); 			// relative (vs absolute) timestamps
			ctw.setBlockMode(packMode,zipMode); 		// pack multiple points per block? (linear timestamps per block)
			ctw.autoFlush(blockPts*dt);					// autoflush blocks and segments
			ctw.autoSegment(segBlocks);
			CTinfo.setDebug(false);

			double iTime = 1460000000.;						// sec 

			// loop and write some output
			for(int i=0; i<nsamp; i++,iTime+=dt) {				// output to nfile
				ctw.setTime(iTime);								// msec (packed-data ignore intermediate timestamps)
				
				for(int j=0; j<nchan; j++) {		// nchan per time-step
					if(numMode) ctw.putData("c"+j, new Float(i+j));		// numeric format (default)
					else		ctw.putData("c"+j+".f32", new Float(i+j));		// binary format
//					System.err.println("put chan: c"+j+".f32: "+new Float(i+j));
				}
			}
			ctw.flush();										// clean up

		} catch(Exception e) {
			System.err.println("Exception: "+e);
		} 
		System.err.println("writeTest done: "+modeName);
	}
	
	//--------------------------------------------------------------------------------------------------------
	static int crossCheck(String modeName, boolean packMode, boolean zipMode, boolean numMode) {
		int errCount=0;

		try {
			CTreader ctr = new CTreader(sourceFolder);		// new CTreader at root folder
			CTinfo.setDebug(false);
			int j=0;
			
			for(String chan:ctr.listChans(modeName)) {
//				System.err.println("Chan: "+chan+", out of: "+ctr.listChans(modeName).size());
				CTdata data = ctr.getData(modeName, chan, 0., nsamp*dt, "oldest");
				double[] t = data.getTime();
				float[] dd;
				if(numMode) dd = data.getDataAsNumericF32();
				else		dd = data.getDataAsFloat32();
				
				double iTime = 1460000000.;						// sec 
				for(int i=0; i<dd.length; i++, iTime+=dt) {
//					System.err.println("i: "+i+", j: "+j);

					if(dd[i]!= new Float(i+j)) {
						if(debug) System.err.println(modeName+": readCheck error, chan: "+chan+": dd["+i+"]: "+dd[i]+" vs "+new Float(i+j)+", j: "+j);
						errCount++;
					}
					if(t[i]!= iTime) {
						if(debug) System.err.println(modeName+": readCheck error, chan: "+chan+": t["+i+"]: "+t[i]+" vs "+iTime+", j: "+j);
						errCount++;
					}
				}
				j++;
			}
		} catch(Exception e) {
			System.err.println("Exception: "+e);
		} 
		
		if(errCount==0) System.err.println("readTest done: "+modeName);
		else			System.err.println("readTest done: "+modeName+", Errors: "+errCount);
		return errCount;
	}
	
	//--------------------------------------------------------------------------------------------------------
	// Argument processing using Apache Commons CLI
	private boolean parseArgs(String[] args) {
		
		// 1. Setup command line options
		Options options = new Options();
		options.addOption("h", "help", false, "Print this message");
		options.addOption("x", "debug", false, "debug mode"+", default: "+debug);
		options.addOption("r", "readCheck", false, "readCheck mode"+", default: "+readCheck);

//		options.addOption(Option.builder("o").argName("outputFolder").hasArg().desc("name of output folder"+", default: "+sourceFolder).build());
		options.addOption(Option.builder("n").argName("nsamp").hasArg().desc("number samples per channel"+", default: "+nsamp).build());
		options.addOption(Option.builder("c").argName("nchan").hasArg().desc("number of channels"+", default: "+nchan).build());

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
		if(extraArgs!=null && extraArgs.length>0) sourceFolder = extraArgs[0];
		
		if (line.hasOption("help")) {			// Display help message and quit
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(120);
			formatter.printHelp( "CTblocktest [options] sourceFolder (default: "+sourceFolder+")\noptions: ", options );
			return false;
		}

		debug 		 		= line.hasOption("debug");
		readCheck			= line.hasOption("readCheck");

		nsamp 	= Integer.parseInt(line.getOptionValue("n",""+nsamp));
		nchan 	= Integer.parseInt(line.getOptionValue("c",""+nchan));

		return true;		// OK to go
	}
}
