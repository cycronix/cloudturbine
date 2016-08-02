
import cycronix.ctlib.*;

/**
 * CloudTurbine demo sink
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 10/19/2015
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

public class CTsink {

	public static void main(String[] args) {
		String rootFolder = "CTexample";
		if(args.length > 0) rootFolder = args[0];
		
		try {
			CTreader ctr = new CTreader(rootFolder);	// set CTreader to rootFolder
			
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
					if(chan.endsWith(".num")) {
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
