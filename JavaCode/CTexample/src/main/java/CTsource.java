import java.util.ArrayList;
import java.util.HashMap;
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
* Copyright 2016 Cycronix
* All Rights Reserved
*
*   Date      By	Description
* MM/DD/YYYY
* ----------  --	-----------
* 09/13/2016  MJM	Created.
*/
 
public class CTsource {
	public static void main(String[] args) {
		String dstFolder = "";
		if(args.length > 0) dstFolder = args[0];
		else				dstFolder = "CTsource";

		long blockPts = 5;			// points per block flush
		
		try {
			// setup CTwriter
			CTwriter ctw = new CTwriter(dstFolder);
			CTinfo.setDebug(false);
			ctw.setBlockMode(false,true);		// no pack, no zip
			ctw.autoFlush(0);					// no autoflush, no segments
			ctw.autoSegment(0);
			
			double time = 1460000000.;			// round-number starting time
			double dt = 1.;
			Map<String,Object>cmap = new LinkedHashMap<String,Object>();
			
			// loop and write some output
			for(int i=0; i<100; i++) {
				ctw.setTime(time);				
				cmap.clear(); 
				cmap.put("c0", i+0);
				cmap.put("c1.f32", (float)(i+100));
				cmap.put("c2.txt", ""+i);
				ctw.putData(cmap);
				
				if(((i+1)%blockPts)==0) {
					ctw.flush();
					System.err.println("flushed: "+i);
				}
				time += dt;
			}
			ctw.flush(); 	// wrap up
		} catch(Exception e) {
			System.err.println("CTsource exception: "+e);
			e.printStackTrace();
		} 
	}
}
