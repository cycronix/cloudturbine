/**
 * CloudTurbine demo data-packaging utility
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

//---------------------------------------------------------------------------------	
// CTpack:  read and re-write CT files into specified structure
// Matt Miller, Cycronix

import cycronix.ctlib.*;

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
	
	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] arg) {
		new CTpack(arg);
	}
	
	//--------------------------------------------------------------------------------------------------------
	public CTpack(String[] args) {
		
		
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
     	CTinfo.setDebug(debug);
     	if(args.length > dirArg) rootFolder = args[dirArg++];  	
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
 				CTwriter ctw = new CTwriter(packFolder+"/"+source);		// new CTwriter at root/source folder
 				ctw.setBlockMode(packMode,zipMode); 					// pack into binary blocks (linear timestamps per block)
 				ctw.autoFlush(0);							
 				ctw.autoSegment(segBlocks);								// auto create segments
 				
         		for(double thisTime=oldTime; thisTime<=newTime; thisTime+=timePerBlock) {		// {Loop by Time}
//     				System.err.println("thisTime: "+thisTime);
					ctw.setTime(thisTime);			// write per request time?

     				for(String chan:ctr.listChans(source)) {				// {Loop by Chan}
     					CTdata data = ctr.getData(source, chan, thisTime, timePerBlock, "absolute");	// get next chunk
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
     					byte[][] d = data.getData();
     					if(t.length==0 || d.length==0) {
     						System.err.println("Warning, zero data for chan: "+chan+", at time: "+thisTime+":"+(thisTime+timePerBlock));
     						continue;
     					}
     					
//     					System.err.println("+Chan: "+chan+", data.size: "+d.length+", time.size: "+t.length+", first bytearray length: "+d[0].length);
     					for(int j=0; j<data.size(); j++) {		// re-write fetched data per CTpack settings
     						ctw.setTime(t[j]);
     						ctw.putData(chan, d[j]);
     					}
     				}
     				
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
	
} //end class CTudp