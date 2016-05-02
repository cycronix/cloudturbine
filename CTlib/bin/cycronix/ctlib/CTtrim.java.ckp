package cycronix.ctlib;

//---------------------------------------------------------------------------------	
// CTtrim:  trim (ring-buffer loop) CT files
// Matt Miller, Cycronix

import java.io.File;
import java.io.IOException;
import java.util.Arrays;


/**
* A class to trim (ring-buffer loop) CloudTurbine folder/files.
* <p>
* @author Matt Miller (MJM), Cycronix
* @version 2014/04/09
* 
*/

/*
* Copyright 2014 Cycronix
* All Rights Reserved
*
*   Date      By	Description
* MM/DD/YYYY
* ----------  --	-----------
* 03/09/2014  MJM	Created.
*/

//------------------------------------------------------------------------------------------------

public class CTtrim {

	private String parentPath=null;
	
	//------------------------------------------------------------------------------------------------
	// constructor
	
	public CTtrim(String dstFolder) throws IOException {
		parentPath = dstFolder;
	}

	//------------------------------------------------------------------------------------------------
	// do the file trimming
	public boolean dotrim(double oldTime) {
		
		// validity checks (beware unwanted deletes!)
		if(parentPath == null || parentPath.length() < 6) {
			System.err.println("CTtrim Error, illegal parent folder: "+parentPath);
			return false;
		}
		if(oldTime < 1.e9 || oldTime > 1.e12) {
			System.err.println("CTtrim time error, must specify full-seconds time since epoch.  oldTime: "+oldTime);
			return false;
		}
		
		File rootFolder = new File(parentPath);
		
		File[] flist = rootFolder.listFiles();
		Arrays.sort(flist);						// make sure sorted 

		for(File file:flist) {
			double ftime = fileTime(file);
//			System.err.println("CTtrim file: "+file.getAbsolutePath()+", ftime: "+ftime+", oldTime: "+oldTime+", diff: "+(ftime-oldTime));
			if(ftime <= 0) continue;				// not a CT time file
			if(ftime >= oldTime) return true;		// all done (sorted)
			System.err.println("CTtrim delete: "+file.getAbsolutePath());
			boolean status = file.delete();		// only trims top-level zip files (need recursive, scary)
			if(!status) System.err.println("CTtrim warning, failed to delete: "+file.getAbsolutePath());
		}
		
		return true;
	}
	
	//------------------------------------------------------------------------------------------------
	//fileTime:  parse time from file name, units: full-seconds (dupe from CTreader, should consolidate)
	private double fileTime(File file) {
		String fname = file.getName();
		if(fname.endsWith(".zip")) {
			fname = fname.split("\\.")[0];		// grab first part
		}
		
		double msec = 1;
		double ftime = 0.;
		if(fname.length() > 10) msec = 1000.;
		try {
			ftime = (double)(Long.parseLong(fname)) / msec;
		} catch(NumberFormatException e) {
			//				System.err.println("warning, bad time format, folder: "+fname);
			ftime = 0.;
		}
		return ftime;
	}
	
	//------------------------------------------------------------------------------------------------
	// test driver
	public static void main(String args[]) {
		if(args.length != 2) {
			System.err.println("Usage:  CTtrim <folder> <oldTime>");
			System.exit(0);
		}
		
		try {
			CTtrim ctt = new CTtrim(args[0]);
			double oldTime = Double.parseDouble(args[1]);
			ctt.dotrim(oldTime);
		} catch(Exception e) {
			System.err.println("CTtrim main exception: "+e);
			e.printStackTrace();
		}
	}	
	
}


