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

		long blockPts = 10;			// points per block flush
		
		try {
			// setup CTwriter
			CTwriter ctw = new CTwriter(dstFolder);
			CTinfo.setDebug(false);
			ctw.setBlockMode(true,false);
			ctw.autoFlush(0);					// no autoflush, no segments
			ctw.autoSegment(0);
			
			double time = 1460000000.;			// round-number starting time
			double dt = 1.;
			
			// loop and write some output
			for(int i=0; i<96; i++) {
				ctw.setTime(time);				
				ctw.putData("foo.txt", (char)(' '+i));	// character data starting at first printable char
				if(((i+1)%blockPts)==0) ctw.flush();
				System.err.println("flushed: "+i);
				time += dt;
			}
			ctw.flush(); 	// wrap up
		} catch(Exception e) {
			System.err.println("CTsource exception: "+e);
			e.printStackTrace();
		} 
	}
}
