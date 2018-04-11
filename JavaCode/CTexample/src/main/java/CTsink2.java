

import cycronix.ctlib.*;
 
/**
 * CloudTurbine demo sink
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

public class CTsink2 {

	public static void main(String[] args) {
		String rootFolder = "CTexample";
		if(args.length > 0) rootFolder = args[0];
//		System.err.println("sleeping..."); try{Thread.sleep(30000);} catch(Exception e){}; System.err.println("proceeding!");
		
		try {
			CTreader ctr = new CTreader(rootFolder);	// set CTreader to rootFolder
//			ctr.setDebug(true);
			
			long startTime = System.currentTimeMillis();
			long nchan = 0;
			long nsource = 0;
			long ncount = 0;
			
			// loop and read what source(s) and chan(s) are available
			for(String source:ctr.listSources()) {
				nsource++;
				System.err.println("Source: "+source);
				for(String chan:ctr.listChans(source)) {
					nchan++;
					CTdata data = ctr.getData(source, chan, 0., 1000000., "oldest");
//					CTdata data = ctr.getData(source, chan, 0., 1000000., "absolute");

					int[] dd = data.getDataAsInt32();
//					if(nchan <= 100 || idx%100==0) 
						System.err.println("nchan: "+nchan+", ncount: "+ncount+", dd.length: "+dd.length+", Chan: "+chan);
					ncount += dd.length;
					if(dd.length < 100) for(int d:dd) System.err.println("data: "+d);
					if(nchan >= 100) break;			// for large counts don't take forever
				}
			}
			long stopTime = System.currentTimeMillis();
			System.out.println("Nsource: "+nsource+", Nchan: "+nchan+", Ncount: "+ncount+", Total Time (ms): "+(stopTime-startTime)+", Pts/Sec: "+(long)(1000.*(ncount)/(stopTime-startTime)));

		}
		catch(Exception e) {
			System.err.println("CTsink2 exception: "+e);
			e.printStackTrace();
		} 
	}
}
