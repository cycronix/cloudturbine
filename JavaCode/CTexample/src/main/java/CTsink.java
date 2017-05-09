/*
Copyright 2014-2017 Cycronix

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

import cycronix.ctlib.*;

/**
 * CloudTurbine demo sink
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 10/19/2015
 * 
*/

public class CTsink {

	public static void main(String[] args) {
		String rootFolder = "CTexample";
		String password = null;
		if(args.length > 0) rootFolder = args[0];
		if(args.length > 1) password = args[1];
		
		try {
			CTreader ctr = new CTreader(rootFolder);	// set CTreader to rootFolder
			if(password!=null) ctr.setPassword(password);

			// loop and read what source(s) and chan(s) are available
			for(String source:ctr.listSources()) {
				System.err.println("Source: "+source);
				for(String chan:ctr.listChans(source)) {
					System.err.println("Chan: "+chan);
					CTdata data = ctr.getData(source, chan, 0., 1000., "oldest");
					double[] dt = data.getTime();
					if(chan.endsWith(".f32")) {
						float[] dd = data.getDataAsFloat32();
						for(int i=0; i<dd.length; i++) System.err.println("time: "+dt[i]+", data: "+dd[i]);	
					}
					else if(chan.endsWith(".num")) {
						float[] dd = data.getDataAsNumericF32();
						for(int i=0; i<dd.length; i++) System.err.println("time: "+dt[i]+", data: "+dd[i]);	
					}
					else {
						byte[][] dd = data.getData();
						for(int i=0; i<dd.length; i++) System.err.println("time: "+dt[i]+", data: "+new String(dd[i]));
					}
				}
			}
		}
		catch(Exception e) {
			System.err.println("CTsink exception: "+e);
			e.printStackTrace();
		} 
	}
}
