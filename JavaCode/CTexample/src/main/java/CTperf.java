
import java.io.File;

import cycronix.ctlib.*;

/**
 * CloudTurbine demo source
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2014/03/10
 * 
 */

/*
 * Copyright 2014 Cycronix
 * All Rights Reserved
 *
 *   Date      By	Description
 * MM/DD/YYYY
 * ----------  --	-----------
 * 03/10/2014  MJM	Created.
 */

public class CTperf {
	static String rootFolder = "CTexample";
	static boolean blockMode = true;
	// read-performance:
	static int nfile=10;						// Rate is proportional to: ~1/nfile
	static int nchan = 1;						// ~1/sqrt(nchan)
	static int blocksize = 1000;				// ~blocksize
	static int ncount = blocksize * nfile;
	static boolean debug = false;
	
	public static void main(String[] args) {
		if(args.length > 0) rootFolder = args[0];

		System.err.println("CTperf, nfile: "+nfile+", nchan: "+nchan+", blocksize: "+blocksize);

		doSource();
		doSink();
	}

	static void doSource() {
		try {
			String dataFolder = rootFolder + File.separator + "CTperf";
			deleteFiles(dataFolder);
			
			// setup CTwriter
			CTwriter ctw = new CTwriter(dataFolder);
			ctw.setZipMode(true);			// bundle to zip files
			//			ctw.autoFlush(200);				// automatically flush at this interval (msec)
			//			ctw.setDebug(debug);
//			ctw.setBlockMode(blockMode);			// block data per flush

			long time = System.currentTimeMillis();
			long dt = 10;
			long startTime = time;
			if(blockMode) ctw.setBlockMode(blockMode);			// block duration (msec)
			//			time = 0;									// try absolute 0-based time						
			// loop and write some output
			if(blockMode) ctw.setTime(time);			// blockMode:  set time at start of block
			for(int i=1; i<=ncount; i++) {
				ctw.setTime(time+=dt);
				for(int j=0; j<nchan; j++) ctw.putData("c"+j, i+j);

				if(!blockMode) ctw.flush();				// flush (zip) them
				else if((i%blocksize)==0) {
					ctw.flush();
					if(debug) System.out.println("put block: "+i);
				}
			}
			long stopTime = System.currentTimeMillis();
			long PPS = (long)(1000.*(nchan*ncount)/(stopTime-startTime));
			System.out.println("CTperf, Total Time (ms): "+(stopTime-startTime)+", Pts/Sec: "+PPS+", PPS/Chan: "+(PPS/nchan));
		} catch(Exception e) {
			System.err.println("CTperf exception: "+e);
			e.printStackTrace();
		} 
	}

	static void doSink() {
		try {
			CTreader ctr = new CTreader(rootFolder);	// set CTreader to rootFolder
			//		ctr.setDebug(true);

			long startTime = System.currentTimeMillis();
			long cidx = 0;
			long ncount = 0;

			// loop and read what source(s) and chan(s) are available
			for(String source:ctr.listSources()) {
				for(String chan:ctr.listChans(source)) {
					if(cidx >= 100) {
						System.err.println("doSink breaking early at cidx=100/"+nchan);
						break;			// for large counts don't take forever
					}
					
					CTdata data = ctr.getData(source, chan, 0., 1000000., "oldest");
					//	CTdata data = ctr.getData(source, chan, 0., 1000000., "absolute");

					int[] dd = data.getDataAsInt32();
					//				if(nchan <= 100 || idx%100==0) 
					if(debug) System.err.println("nchan: "+cidx+", ncount: "+ncount+", dd.length: "+dd.length+", Chan: "+chan);
					ncount += dd.length;
//					if(dd.length < 100) for(int d:dd) System.err.println("data: "+d);
					cidx++;
				}
			}
			long stopTime = System.currentTimeMillis();
			long PPS = (long)(1000.*(ncount)/(stopTime-startTime));
			long FPS = (long)(1000.*(cidx*nfile)/(stopTime-startTime));
			System.out.println("CTsink2, Total Time (ms): "+(stopTime-startTime)+", Pts/Sec: "+PPS+", PPS/Chan: "+(PPS/nchan)+", FPS: "+FPS+", RateFactor F: "+(PPS * Math.sqrt(cidx)/blocksize));
		}
		catch(Exception e) {
			System.err.println("CTsink2 exception: "+e);
			e.printStackTrace();
		} 
	}
	
	static void deleteFiles(String folder) {
		File directory = new File(folder);
	    if(directory.exists()){
	        File[] files = directory.listFiles();
	        if(null!=files){
	            for(int i=0; i<files.length; i++) files[i].delete();
	        }
	    }
	    return;
	}
}
