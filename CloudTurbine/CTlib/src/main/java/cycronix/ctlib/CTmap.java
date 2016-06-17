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
public class CTmap extends TreeMap<String,CTdata>{
	private static final long serialVersionUID = 1L;
//	private Map<String, CTdata> ctMap = new TreeMap<String, CTdata>();
	private boolean hasData=false;
	
	public CTmap() {
		super();
	}
	
	CTmap(String name) {
		this.add(name,  null);
		hasData = false;
	}
	
	boolean hasData() {
		return hasData;
	}
	
	public void add(String cname) {
		add(cname, null);		// list-of-channels without data (for ref)
	}
	
	public void add(String cname, CTdata timedata) {
		if(timedata != null) hasData=true;				// book-keep
		CTdata tdata = this.get(cname);
		if(tdata != null) {
			tdata.add(timedata);		// append vs replace
			this.put(cname,  tdata);
		}
		else	this.put(cname,  timedata);
	}
	
	public CTdata get(String cname, double tstart, double tdur) {		// time units = seconds
//		System.err.println("CTmap.get: "+cname+", ctMap.get: "+this.get(cname));
		return this.get(cname, tstart, tdur, "absolute");
	}
	
	public CTdata get(String cname, double tstart, double tdur, String tmode) {		// time units = seconds
//		System.err.println("CTmap.get: "+cname+", ctMap.get: "+this.get(cname));
		CTdata tdata = this.get(cname);
		if(tdata != null) 						// merge multiple tdata objects into one on fetch
			tdata = tdata.timeRange(CTinfo.wordSize(CTinfo.fileType(cname)), tstart, tdur, tmode);
		return tdata;
	} 
	
	@Deprecated
	public CTdata getTimeData(String cname) {
		return this.get(cname);
	}
	
	@Deprecated
	public CTdata getTimeData(String cname, double tstart, double tdur) {		// time units = seconds
		return this.get(cname, tstart, tdur, "absolute");
	}
	
	@Deprecated
	public CTdata getTimeData(String cname, double tstart, double tdur, String tmode) {		// time units = seconds
		return this.get(cname, tstart, tdur, tmode);
	} 
	
	public String getName(int index) {
		return (String) this.keySet().toArray()[index];
	}
	
	public boolean checkName(String cname) {
		return this.containsKey(cname);
	}

	// trim entire map of channels by time-range
	void trim(double tstart, double tdur, String tmode) {
		for (Map.Entry<String, CTdata> entry : this.entrySet()) {
		    String cname = entry.getKey();
		    CTdata tdata = entry.getValue();
			if(tdata != null) tdata = tdata.timeRange(CTinfo.wordSize(CTinfo.fileType(cname)), tstart, tdur, tmode);
			this.put(cname, tdata);
		}
	}
/*	
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
*/
}

