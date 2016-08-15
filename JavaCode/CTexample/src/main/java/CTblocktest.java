

import cycronix.ctlib.*;

public class CTblocktest {
	static int nsamp = 1000;			// total number of data samples (per chan)
	static int nchan = 10;				// number of channels 
//	static long dt = 1000L;				// msec per point
	static double dt = 1.;				// sec per point
	static long blockPts=10;			// points per block
	static long segBlocks=10;			// blocks per segment (was 10)
	
	public static void main(String[] args) {
		// run each permutation of blockMode, zipMode, numMode
		runTest("CTbin",		false,	false,	false);
		runTest("CTblockbin",	true,	false,	false);
		runTest("CTblockzipbin",true,	true,	false);
		runTest("CTzipbin",		false,	true,	false);
		runTest("CTnum",		false,	false,	true);
		runTest("CTblocknum",	true,	false,	true);
		runTest("CTblockzipnum",true,	true,	true);
		runTest("CTzipnum",		false,	true,	true);
		System.err.println("Done. nsamp: "+nsamp+", nchan: "+nchan+", pts/block: "+blockPts+", blocks/segment: "+segBlocks);
	}
	
	static void runTest(String modeName, boolean blockMode, boolean zipMode, boolean numMode) {
		try {
			CTwriter ctw = new CTwriter("CTsource/"+modeName);		// new CTwriter at root/source folder

			ctw.setBlockMode(blockMode,zipMode); 		// pack into binary blocks? (linear timestamps per block)
			ctw.autoFlush(blockPts*dt, segBlocks);		// autoflush blocks and segments

			CTinfo.setDebug(false);
			
//			long iTime = System.currentTimeMillis();
//			long iTime = 1460000000000L;					// msec 
			double iTime = 1460000000.;						// sec 

			// loop and write some output
			for(int i=1; i<=nsamp; i++,iTime+=dt) {				// output to nfile
				ctw.setTime(iTime);								// msec (blocks ignore intermediate timestamps)
				
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
