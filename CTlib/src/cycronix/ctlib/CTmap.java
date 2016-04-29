package cycronix.ctlib;

//---------------------------------------------------------------------------------	
// CTmap: map of CTdata's by name
// Matt Miller, Cycronix
// 02/18/2014

import java.util.Map;
import java.util.TreeMap;

/**
 * A CloudTurbine utility class to manage TreeMap of name-CTdata pairs
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2014/03/06
 * 
*/

/*
* Copyright 2014 Cycronix
* All Rights Reserved
*
*   Date      By	Description
* MM/DD/YYYY
* ----------  --	-----------
* 02/06/2014  MJM	Created.
*/

// CTmap:  map of CTdata's by name
public class CTmap {
	private Map<String, CTdata> ctMap = new TreeMap<String, CTdata>();
	private boolean hasData=false;
//	double refTime=0.;
	
	public CTmap() {}
	
	CTmap(String name) {
		this.add(name,  null);
		hasData = false;
//		System.err.println("ctmap hasData: FALSE");
	}
	
	boolean hasData() {
		return hasData;
	}
	
	public void add(String cname) {
		add(cname, null);		// list-of-channels without data (for ref)
	}
	
	void add(String cname, CTdata timedata) {
		if(timedata != null) hasData=true;				// book-keep
//		System.err.println("ctmap hasData: TRUE");

		CTdata tdata = ctMap.get(cname);
		if(tdata != null) {
			tdata.add(timedata);		// append vs replace
			ctMap.put(cname,  tdata);
//			System.err.println("ctMap.add, tdata.size: "+tdata.size());
		}
		else	ctMap.put(cname,  timedata);
//		for(String k:ctMap.keySet()) System.err.println("ctMap: "+k+", k.equals: "+k.equals(cname)+", tdata: "+timedata);
//		System.err.println("CTmap.add: "+cname+", ctMap.size: "+ctMap.size()+", ctMap.get: "+ctMap.get(cname));		// why null get here?
	}
	
	public CTdata getTimeData(String cname) {
//		System.err.println("getTimeData: "+cname+", ctMap.get: "+ctMap.get(cname));
//		return ctMap.get(cname);
		return getTimeData(cname, 0., -1.);		// full time range (strip dupes)
	}
	
	public CTdata getTimeData(String cname, double tstart, double tdur) {		// time units = seconds
//		System.err.println("getTimeData: "+cname+", ctMap.get: "+ctMap.get(cname));
		return getTimeData(cname, tstart, tdur, "absolute");
	}
	
	public CTdata getTimeData(String cname, double tstart, double tdur, String tmode) {		// time units = seconds
//		System.err.println("getTimeData: "+cname+", ctMap.get: "+ctMap.get(cname));
		CTdata tdata = ctMap.get(cname);
		if(tdata != null) tdata = tdata.timeRange(wordSize(fileType(cname)), tstart, tdur, tmode);
		return tdata;
	} 
	
	public String getName(int index) {
		return (String) ctMap.keySet().toArray()[index];
	}
	
	public boolean checkName(String cname) {
		return ctMap.containsKey(cname);
	}
	
	public int size() {
		return ctMap.size();
	}
	
	void put(String key, CTdata value) { 
		ctMap.put(key, value);
	}
	
	CTdata get(String key) {	// shortcut to getTimeData(cname), but doesn't interpolate block-times
		return ctMap.get(key);
	}
	
	// trim entire map of channels by time-range
	void trim(double tstart, double tdur, String tmode) {
		for (Map.Entry<String, CTdata> entry : ctMap.entrySet()) {
		    String cname = entry.getKey();
		    CTdata tdata = entry.getValue();
			if(tdata != null) tdata = tdata.timeRange(wordSize(fileType(cname)), tstart, tdur, tmode);
			ctMap.put(cname, tdata);
		}
	}
	
	//--------------------------------------------------------------------------------------------------------
	// fileType:  return file type code based on file extension
	
	public static char fileType(String fname) {
		return fileType(fname, 's');
	}
	
	public static char fileType(String fName, char typeDefault) {
		
		char fType = typeDefault;		// default
		if		(fName.endsWith(".bin")) fType = 'B';
		else if	(fName.endsWith(".jpg")) fType = 'B';
		else if	(fName.endsWith(".wav")) fType = 'j';		// was 'B'
		else if	(fName.endsWith(".mp3")) fType = 'B';
		else if	(fName.endsWith(".pcm")) fType = 'j';		// FFMPEG s16sle audio
		else if	(fName.endsWith(".txt")) fType = 's';	
		else if	(fName.endsWith(".f32")) fType = 'f';
		else if	(fName.endsWith(".f64")) fType = 'F';
		else if	(fName.endsWith(".i16")) fType = 'j';		// 's' is string for compat with WebTurbine
		else if	(fName.endsWith(".i32")) fType = 'i';
		else if	(fName.endsWith(".i64")) fType = 'I';
		else if (fName.endsWith(".Num")) fType = 'N';
		else if (fName.endsWith(".num")) fType = 'n';
		return(fType);
	}
	
	public static int wordSize(char ftype) {
		switch(ftype) {
		case 'f':	return 4;
		case 'F':	return 8;
		case 'j':	return 2;
		case 'i':	return 4;
		case 'I':	return 8;
		default:	return 1;	
		}
	}
}
