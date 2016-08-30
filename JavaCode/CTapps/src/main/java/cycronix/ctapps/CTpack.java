package cycronix.ctapps;

//---------------------------------------------------------------------------------	
// CTpack:  read and re-write CT files into specified structure
// Matt Miller, Cycronix

import cycronix.ctlib.*;

/*
* Copyright 2016 Cycronix
* All Rights Reserved
*
*   Date      By	Description
* MM/DD/YYYY
* ----------  --	-----------
* 08/30/2016  MJM	Created.
*/

//---------------------------------------------------------------------------------	

public class CTpack {
	CTreader ctr;
	CTwriter ctw;
	
	boolean debug=false;
	String srcName="CTpack";
	
	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] arg) {
		new CTpack(arg);
	}
	
	//--------------------------------------------------------------------------------------------------------
	public CTpack(String[] arg) {
		
		try {
	//	    System.err.println("");
			ArgHandler ah=new ArgHandler(arg);
			if (ah.checkFlag('h') || arg.length==0) {
				throw new Exception("show usage");
			}
			if (ah.checkFlag('x')) {
				debug = true;
			}
		} catch (Exception e) {
			String msgStr = e.getMessage();
			if ( (msgStr == null) || (!msgStr.equalsIgnoreCase("show usage")) ) {
			    System.err.println("Exception parsing arguments:");
			    e.printStackTrace();
			}
			System.err.println("CTpack");
			System.err.println(" -h                     : print this usage info");
			System.err.println(" -x                     : debug mode");
			System.exit(0);
		}
		
		// setup CTreader
		
		
		// setup CTwriter
		try {
			ctw = new CTwriter(srcName);	
//			ctw.setZipMode(zipMode);
			CTinfo.setDebug(debug);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

	}
	
} //end class CTudp
