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
			CTinfo.setDebug(true);
			ctw.setBlockMode(true,false);
			ctw.autoFlush(0,0);					// no autoflush, no segments
			
			double time = 1460000000.;	
			double dt = 1.;
			
			// loop and write some output
			for(int i=0; i<10; i++) {
				ctw.setTime(time);				
				ctw.putData("foo.txt", (char)('a'+i));		// character data
				if(((i+1)%blockPts)==0) ctw.flush();
				System.err.println("flushed: "+i);
				time += dt;
			}
		} catch(Exception e) {
			System.err.println("CTsource exception: "+e);
			e.printStackTrace();
		} 
	}
}
