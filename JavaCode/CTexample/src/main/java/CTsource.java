// simple CloudTurbine CTwriter example

import cycronix.ctlib.*;

public class CTsource {
	static int nsamp = 100;			// number of data samples per chan
	static int nchan = 10;				// number of channels 
	static long dt = 1000L;				// msec per point
	static long blockPts=10;			// flush once per N points
	
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
		System.err.println("Done.  Output nsamp: "+nsamp+", nchan: "+nchan+", points/block: "+blockPts);
	}
	
	static void runTest(String modeName, boolean blockMode, boolean zipMode, boolean numMode) {
		try {
			CTwriter ctw = new CTwriter("CTsource/"+modeName);		// new CTwriter at root/source folder

			ctw.setBlockMode(blockMode); 				// pack into binary blocks? (linear timestamps per block)
			ctw.setTimeRelative(true);					// use relative timestamps?
			ctw.setZipMode(zipMode);
			CTinfo.setDebug(false);
			ctw.autoFlush(blockPts*dt); 				// auto-flush every N sec 
			
//			long iTime = System.currentTimeMillis();
			long iTime = 1460000000000L;						// msec resolution

			// loop and write some output
			for(int i=1; i<=nsamp; i++,iTime+=dt) {				// output to nfile
				ctw.setTime(iTime);								// msec (blocks ignore intermediate timestamps)
				
				for(int j=0; j<nchan; j++) {		// nchan per time-step
					if(numMode) ctw.putData("c"+j, new Float(i+j));		// numeric format (default)
					else		ctw.putData("c"+j+".f32", new Float(i+j));		// binary format
				}

//				if((i%blockPts)==0) 		// flush one per N (no-op if not block/zip/rel mode)
//					ctw.flush();			// implicitly let blockDuration = (lastTime-firstTime)
			}
			ctw.flush();										// clean up

		} catch(Exception e) {
			System.err.println("Exception: "+e);
		} 
	}
}
