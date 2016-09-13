/**
 * CloudTurbine example: test various block formats
 * Writes same data in permutations of 3 cases: pack, zip, num (8 total tests)
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 09/01/2016
 * 
*/

/*
* Copyright 2016 Cycronix
* All Rights Reserved
*
*   Date      By	Description
* MM/DD/YYYY
* ----------  --	-----------
* 09/01/2016  MJM	Created.
*/

import cycronix.ctlib.*;

public class CTblocktest {
	static int nsamp = 1000;			// total number of data samples (per chan)
	static int nchan = 10;				// number of channels 
//	static long dt = 1000L;				// msec per point
	static double dt = 1.;				// sec per point
	static long blockPts=10;			// points per block
	static long segBlocks=10;			// blocks per segment (was 10)
	static boolean relativeTime=true;	// relative (vs absolute) times
	
	public static void main(String[] args) {
		// run each permutation of packMode, zipMode, numMode
		runTest("CTbin",		false,	false,	false);
		runTest("CTpackbin",	true,	false,	false);
		runTest("CTpackzipbin",true,	true,	false);
		runTest("CTzipbin",		false,	true,	false);
		runTest("CTnum",		false,	false,	true);
		runTest("CTpacknum",	true,	false,	true);
		runTest("CTpackzipnum",true,	true,	true);
		runTest("CTzipnum",		false,	true,	true);
		System.err.println("Done. nsamp: "+nsamp+", nchan: "+nchan+", pts/block: "+blockPts+", blocks/segment: "+segBlocks);
	}
	
	static void runTest(String modeName, boolean packMode, boolean zipMode, boolean numMode) {
		try {
			CTwriter ctw = new CTwriter("CTsource/"+modeName);		// new CTwriter at root/source folder

			ctw.setTimeRelative(relativeTime); 			// relative (vs absolute) timestamps
			ctw.setBlockMode(packMode,zipMode); 		// pack multiple points per block? (linear timestamps per block)
			ctw.autoFlush(blockPts*dt, segBlocks);		// autoflush blocks and segments

			CTinfo.setDebug(false);
			
//			long iTime = System.currentTimeMillis();
//			long iTime = 1460000000000L;					// msec 
			double iTime = 1460000000.;						// sec 

			// loop and write some output
			for(int i=1; i<=nsamp; i++,iTime+=dt) {				// output to nfile
				ctw.setTime(iTime);								// msec (packed-data ignore intermediate timestamps)
				
				for(int j=0; j<nchan; j++) {		// nchan per time-step
					if(numMode) ctw.putData("c"+j, new Float(i+j));		// numeric format (default)
					else		ctw.putData("c"+j+".f32", new Float(i+j));		// binary format
				}

//				if((i%blockPts)==0) ctw.flush();		// flush one per N (no-op if not block/zip/rel mode)								
//				Thread.sleep(10);			// minimum sleep per loop
			}
			ctw.flush();										// clean up

		} catch(Exception e) {
			System.err.println("Exception: "+e);
		} 
	}
}
