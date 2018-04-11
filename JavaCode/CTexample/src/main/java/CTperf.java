
import java.io.File;

import cycronix.ctlib.*;

/**
 * CloudTurbine demo source, performance test
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2014/03/10
 * 
 */

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

public class CTperf {
	static String rootFolder = "CTexample";
	// read-performance:
	static int ncount=4*36000;					// 36000 = 1hrs @ 10Hz
	static int nchan = 30;						// ~1/sqrt(nchan)
	static int blocksize = 10;					// ~blocksize
	static int nfile = ncount / blocksize;
	static int blocksPerSeg = 100;
	static int dtmsec = 100;					// sample time-interval
	static boolean debug = false;
	static boolean dotrim = true;
	static boolean pacerate = false;
	static boolean blockMode = true;			// pack data?

	public static void main(String[] args) {
		if(args.length > 0) rootFolder = args[0];

		System.err.println("CTperf, nsamp: "+ncount+", nfile: "+nfile+", nchan: "+nchan+", blocksize: "+blocksize);

		doSource();
		doSink();
		doSink();
	}

	static void doSource() {
		System.err.println("Timing source write...");
		try {
			String dataFolder = rootFolder + File.separator + "CTperf";
//			deleteFiles(dataFolder);
			
			// setup CTwriter
			CTwriter ctw = new CTwriter(dataFolder);
			ctw.setZipMode(true);						// bundle to zip files
			//			ctw.autoFlush(200);				// automatically flush at this interval (msec)
			//			ctw.setDebug(debug);
//			ctw.setBlockMode(blockMode);			// block data per flush

			if(blockMode) ctw.setBlockMode(blockMode);			// block duration (msec)
			ctw.autoSegment(blocksPerSeg);
			
			if(dotrim) {
				System.err.println("deleting old folders/files...");
				try{ ctw.dotrim(864000.+(double) System.currentTimeMillis()/1000.);} catch(Exception e){};		// delete old data
				System.err.println("Done deleting.");
			}

			// loop and write some output
			long time = System.currentTimeMillis();
			long startTime = time;
//			if(blockMode) ctw.setTime(time);			// blockMode:  set time at start of block		

			for(int i=1; i<=ncount; i++) {
				if(pacerate)	time = System.currentTimeMillis();
				else 			time += dtmsec;
				
				ctw.setTime(time);
				for(int j=0; j<nchan; j++) ctw.putData("c"+j+".f32", (float)(i+j));
//				for(int j=0; j<nchan; j++) ctw.putData("c"+j, (float)(i+j));


//				if(!blockMode) ctw.flush();				// flush (zip) them
//				else 
				if((i%blocksize)==0) {
					ctw.flush();
					if(debug) System.out.println("put block: "+i);
				}
				if(pacerate) try{ Thread.sleep(dtmsec +time-System.currentTimeMillis() - 1); } catch(Exception e){};
			}
			long stopTime = System.currentTimeMillis();
			long PPS = (long)(1000.*(nchan*ncount)/(stopTime-startTime));
			System.out.println("CTsource, Total Time (ms): "+(stopTime-startTime)+", Pts/Sec: "+PPS+", PPS/Chan: "+(PPS/nchan));
		} catch(Exception e) {
			System.err.println("CTperf exception: "+e);
			e.printStackTrace();
		}
		System.err.println("Done write time test.");
	}

	static void doSink() {
		System.err.println("Timing sink read...");
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

					String[] dd = data.getDataAsString(CTinfo.fileType(chan));				// convert to string per CTweb fetch
					//				if(nchan <= 100 || idx%100==0) 
					if(debug) System.err.println("nchan: "+cidx+", ncount: "+ncount+", dd.length: "+dd.length+", Chan: "+chan+", dd[0]: "+dd[0]);
					ncount += dd.length;
//					if(dd.length < 100) for(int d:dd) System.err.println("data: "+d);
					cidx++;
				}
			}
			long stopTime = System.currentTimeMillis();
			long PPS = (long)(1000.*(ncount)/(stopTime-startTime));
			long FPS = (long)(1000.*(cidx*nfile)/(stopTime-startTime));
			System.out.println("CTsink, Total Time (ms): "+(stopTime-startTime)+", Pts/Sec: "+PPS+", PPS/Chan: "+(PPS/nchan)+", FPS: "+FPS);
		}
		catch(Exception e) {
			System.err.println("CTsink exception: "+e);
			e.printStackTrace();
		} 
		System.err.println("Done read time test.");
	}

}
