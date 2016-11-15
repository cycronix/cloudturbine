/**
 * CTmetrics:  CloudTurbine metrics-generating utility
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 11/07/2016
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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import cycronix.ctlib.*;


/*
* Copyright 2016 Cycronix
* All Rights Reserved
*
*   Date      By	Description
* MM/DD/YYYY
* ----------  --	-----------
* 11/07/2016  MJM	Created.
*/

//---------------------------------------------------------------------------------	

public class CTmetrics {
	CTreader ctr;								// CTreader
	CTwriter ctw;								// CTwriter

	boolean debug=false;						// debug T/F
	boolean zipmode=true;						// zip output T/F
	String 	monitorFolder = "CTdata";			// monitor source(s) in this folder
	String 	metricsSource = "CTdata/CTmetrics";	// write results to this folder
	double 	timePerBlock=60.;					// time interval per block on output 
	double 	timePerSample=5.;					// time per sample (samplesPerBlock = timePerBlock/timePerSample) (sec)
	double 	timePerLoop=3600.;					// time per trim-data loop (sec)
	long   	blocksPerSegment=100;				// blocks per segment, 0 to disable
	boolean individualSources=false;			// monitor data/disk usage for each source individually
	
	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] args) {

		new CTmetrics(args);
	}

	//--------------------------------------------------------------------------------------------------------
	public CTmetrics(String[] args) {
		
		if(!parseArgs(args)) return;
		
		System.err.println("CTmetrics monitor: "+monitorFolder+", output to: "+metricsSource);

		CTinfo.setDebug(debug);

		long mtimePerBlock = (long)(timePerBlock * 1000.);
		if(timePerSample>=timePerBlock || timePerSample==0) timePerSample = timePerBlock;
		long mtimePerSample = (long)(timePerSample * 1000.);
		
		try {
			CTwriter ctw = new CTwriter(metricsSource, timePerLoop);
			ctw.setBlockMode(true,zipmode);
			ctw.autoSegment(blocksPerSegment);					// blocks/segment
			ctw.autoFlush(mtimePerBlock);

			CTreader ctr = new CTreader(monitorFolder);
			File f = new File(metricsSource);		// pre-make output folder to use as getSpace ref
			f.mkdirs();

			while(true) {
				long freeSpace = f.getFreeSpace();
//				long totalSpace = f.getTotalSpace();
				ctw.setTime(System.currentTimeMillis());			// common time all samples/block
//				ctw.putData("UsedSpace", (totalSpace - freeSpace));
				ctw.putData("FreeSpace",  freeSpace);

				if(individualSources) {
					ArrayList<String>sources = ctr.listSources();		// update list every iteration
					if(sources.size() == 0) System.err.println("Warning:  no sources found in monitorFolder: "+monitorFolder);
					for(String source:sources) {				// {Loop by Source}
						String sourcePath = monitorFolder+File.separator+source;
						long diskSize = CTinfo.diskUsage(sourcePath, 4096);
						long dataSize = CTinfo.dataUsage(sourcePath);
						String src = source.replace(File.separator, "_");	// can't have multi-level channel name!
						ctw.putData(src+"_DiskUsage", diskSize);
						ctw.putData(src+"_DataUsage", dataSize);
						//					System.err.println("metrics source: "+sourcePath);
					}
				}
				else {
					long diskSize = CTinfo.diskUsage(monitorFolder, 4096);
					long dataSize = CTinfo.dataUsage(monitorFolder);
					ctw.putData("DiskSpace", diskSize);
					ctw.putData("DataSpace", dataSize);
				}
				Thread.sleep(mtimePerSample);			// one or more samples per block
			}
			
		} catch (Exception e) {
			System.err.println("CTmetrics Exception: "+e);
		}
	}
	
	//--------------------------------------------------------------------------------------------------------
	// Argument processing using Apache Commons CLI
	private boolean parseArgs(String[] args) {
		
		// 1. Setup command line options
		Options options = new Options();
		options.addOption("h", "help", false, "Print this message");
		options.addOption("x", "debug", false, "debug mode"+", default: "+debug);
		options.addOption("z", "zip", false, "zip mode"+", default: "+zipmode);
		options.addOption("i", "individualSources", false, "monitor sources individually");

		options.addOption(Option.builder("o").argName("metricsFolder").hasArg().desc("name of output folder"+", default: "+metricsSource).build());
		options.addOption(Option.builder("T").argName("timePerBlock").hasArg().desc("time per output block (sec)"+", default: "+timePerBlock).build());
		options.addOption(Option.builder("t").argName("timePerSample").hasArg().desc("time per sample (sec)"+", default: "+timePerSample).build());
		options.addOption(Option.builder("r").argName("ringBuffer").hasArg().desc("ring-buffer trim duration (sec), 0 to disable"+", default: "+timePerLoop).build());
		options.addOption(Option.builder("s").argName("blocksPerSegment").hasArg().desc("segment size (blocks), 0 to disable"+", default: "+blocksPerSegment).build());

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
		if(extraArgs!=null && extraArgs.length>0) monitorFolder = extraArgs[0];
		
		if (line.hasOption("help")) {			// Display help message and quit
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(120);
			formatter.printHelp( "CTmetrics [options] sourceFolder (default: "+monitorFolder+")\noptions: ", options );
			return false;
		}

		debug 		 		= line.hasOption("debug");
		zipmode 		 	= line.hasOption("zip")?(!zipmode):zipmode;		// toggles default
		individualSources 	= line.hasOption("individualSources");

		metricsSource 		= line.getOptionValue("o",metricsSource);
		timePerBlock 		= Double.parseDouble(line.getOptionValue("T",""+timePerBlock));
		timePerSample 		= Double.parseDouble(line.getOptionValue("t",""+timePerSample));
		timePerLoop  		= Double.parseDouble(line.getOptionValue("r",""+timePerLoop));
		blocksPerSegment 	= Long.parseLong(line.getOptionValue("s",""+blocksPerSegment));

		return true;		// OK to go
	}
}