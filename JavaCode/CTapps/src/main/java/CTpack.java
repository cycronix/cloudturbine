
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
	
	boolean blockMode=true;				// warning:  non-block audio is deadly inefficient
	boolean zipMode=true;				// zip on by default
	static long segBlocks=10;			// blocks per segment (was 10)
	static double timePerBlock=10.;		// time interval per block on output
	
	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] arg) {
		new CTpack(arg);
	}
	
	//--------------------------------------------------------------------------------------------------------
	public CTpack(String[] args) {
		
		
    	if(args.length == 0) {
    		System.err.println("CTpack -x -z -b -p <packFolder> -t <timePerBlock> -s <segBlocks> rootFolder");
    	}
    	
     	int dirArg = 0;
     	while((dirArg<args.length) && args[dirArg].startsWith("-")) {		// arg parsing
     		if(args[dirArg].equals("-x")) 	debug = true;
     		if(args[dirArg].equals("-z")) 	zipMode = false;				// turn OFF zip
     		if(args[dirArg].equals("-b")) 	blockMode = false;				// turn OFF packed blocks

     		if(args[dirArg].equals("-p"))  	packFolder = args[++dirArg]; 
     		if(args[dirArg].equals("-t"))  	timePerBlock = Double.parseDouble(args[++dirArg]); 
     		if(args[dirArg].equals("-s"))  	segBlocks = Integer.parseInt(args[++dirArg]); 

     		dirArg++;
     	}
     	if(args.length > dirArg) rootFolder = args[dirArg++];  	
     	if(!blockMode) System.err.println("Warning: unpacked blocks may result in very large number of files!!");
     	
		// setup CTreader
		
     	try {
     		CTreader ctr = new CTreader(rootFolder);	// set CTreader to rootFolder
     		// loop and read what source(s) and chan(s) are available
     		for(String source:ctr.listSources()) {							// {Loop by Source}
//         		boolean done = false;
         		String sourcePath = rootFolder + File.separator + source;
 				System.err.println("Source: "+source);
 	     		double oldTime = ctr.oldTime(sourcePath);		// go absolute time oldest to newest (straddling gaps)
 	     		double newTime = ctr.newTime(sourcePath);
 				System.err.println("source oldTime: "+oldTime+", newTime: "+newTime);
 				
 				// setup CTwriter for each root source
 				CTwriter ctw = new CTwriter(packFolder+"/"+source);		// new CTwriter at root/source folder
 				ctw.setBlockMode(blockMode,zipMode); 					// pack into binary blocks (linear timestamps per block)
 				ctw.autoFlush(0, segBlocks);							// autoflush segments
 				
//     			for(double thisTime=0.; true; thisTime+=timePerBlock) {		// {Loop by Time}
         		for(double thisTime=oldTime; thisTime<=newTime; thisTime+=timePerBlock) {		// {Loop by Time}
//     				System.err.println("thisTime: "+thisTime);

     				for(String chan:ctr.listChans(source)) {				// {Loop by Chan}
//     					CTdata data = ctr.getData(source, chan, thisTime, timePerBlock, "oldest");		// get next chunk
     					CTdata data = ctr.getData(source, chan, thisTime, timePerBlock, "absolute");	// get next chunk

     					double[] t = data.getTime();
     					byte[][] d = data.getData();

//     					System.err.println("+Chan: "+chan+", data.size: "+d.length+", time.size: "+t.length);
     					for(int j=0; j<data.size(); j++) {		// re-write in same chunks as root source
     						ctw.setTime(t[j]);
     						ctw.putData(chan, d[j]);
     					}
//     					if(d.length == 0) done=true;
     				}
     				ctw.flush();								// manually flush each timePerBlock
     				
//     				if(done) break;		// break after all chans written
     			}
     		}
     	}
     	catch(Exception e) {
     		System.err.println("CTsink exception: "+e);
     		e.printStackTrace();
     	} 
		

	}
	
} //end class CTudp
