
import java.util.LinkedHashMap;
import java.util.Map;

import cycronix.ctlib.*;

/**
 * CloudTurbine demo source
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2016/09/13
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

public class CTsource {
	public static void main(String[] args) {
		String dstFolder = "";
		String password = null;
		if(args.length > 0) dstFolder = args[0];
		else				dstFolder = "CTsource";
		if(args.length > 1) password = args[1];
		
		long blockPts = 5;			// points per block flush
		
		try {
			// setup CTwriter
			CTwriter ctw = new CTwriter(dstFolder, 100.);			// ,trimtime
			if(password!=null) ctw.setPassword(password);

			CTinfo.setDebug(false);
			ctw.setBlockMode(false,false);		// no pack, no zip
			ctw.autoFlush(0);					// no autoflush, no segments
			ctw.autoSegment(10);					// was 10
//			ctw.setHiResTime(true);             // usec timestamps
			
			double time = 1460000000.;			// round-number starting time
			double dt = 1.;
			Map<String,Object>cmap = new LinkedHashMap<String,Object>();
			
			// loop and write some output
			for(int i=0; i<1000; i++) {
				ctw.setTime(time);				
				cmap.clear(); 
				cmap.put("c0", 9999);
				cmap.put("c1.f32", (float)(i+100));
				cmap.put("c2.txt", ""+i);
				ctw.putData(cmap);
				
				if(((i+1)%blockPts)==0) {
					ctw.flush();
					System.err.println("flushed: "+i);
				}
				time += dt;
				Thread.sleep(100);
			}
			ctw.flush(); 	// wrap up
		} catch(Exception e) {
			System.err.println("CTsource exception: "+e);
			e.printStackTrace();
		} 
	}
}
