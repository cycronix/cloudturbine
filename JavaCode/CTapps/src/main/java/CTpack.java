
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
	
	boolean blockMode=true;
	boolean zipMode=true;
	static long blockPts=10;			// points per block
	static long segBlocks=10;			// blocks per segment (was 10)
	static double timePerBlock=1.;		// time interval per block on output
	
	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] arg) {
		new CTpack(arg);
	}
	
	//--------------------------------------------------------------------------------------------------------
	public CTpack(String[] args) {
		
		
    	if(args.length == 0) {
    		System.err.println("CTpack -x -p <packFolder> rootFolder");
    	}
    	
     	int dirArg = 0;
     	while((dirArg<args.length) && args[dirArg].startsWith("-")) {		// arg parsing
     		if(args[dirArg].equals("-x")) 	debug = true;
     		if(args[dirArg].equals("-p"))  	packFolder = args[++dirArg]; 
     		dirArg++;
     	}
     	if(args.length > dirArg) rootFolder = args[dirArg++];  	
		
		// setup CTreader
		
     	try {
     		CTreader ctr = new CTreader(rootFolder);	// set CTreader to rootFolder
     		
     		// loop and read what source(s) and chan(s) are available
     		for(String source:ctr.listSources()) {
         		boolean done = false;
 				System.err.println("Source: "+source);

     			for(double thisTime=0.; done==false; thisTime+=timePerBlock) {
     				System.err.println("thisTime: "+thisTime);
     				ArrayList<String>chanList = new ArrayList<String>();
     				chanList = ctr.listChans(source);
     				ArrayList<byte[][]> dataList = new ArrayList<byte[][]>();
     				ArrayList<double[]> timeList = new ArrayList<double[]>();
     				
     				for(String chan:chanList) {
     					CTdata data = ctr.getData(source, chan, thisTime, timePerBlock, "oldest");		// get next chunk
     					double[] t = data.getTime();
     					byte[][] d = data.getData();
     					timeList.add(t);
     					dataList.add(d);
     					System.err.println("Chan: "+chan+", data.size: "+d.length+", time.size: "+t.length);
     					if(d.length == 0) done=true;
     				}

//     				CTwriter ctw = new CTwriter(packFolder+"/"+source);		// new CTwriter at root/source folder
//     				ctw.setBlockMode(blockMode,zipMode); 					// pack into binary blocks? (linear timestamps per block)
//     				ctw.autoFlush(timePerBlock, segBlocks);					// autoflush blocks and segments
     			}
     		}
     	}
     	catch(Exception e) {
     		System.err.println("CTsink exception: "+e);
     		e.printStackTrace();
     	} 
		

	}
	
} //end class CTudp
