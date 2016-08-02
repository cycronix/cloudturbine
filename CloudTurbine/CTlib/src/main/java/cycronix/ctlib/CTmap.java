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
			tdata = tdata.timeRange(CTinfo.fileType(cname), tstart, tdur, tmode);
//			tdata = tdata.timeRange(CTinfo.wordSize(CTinfo.fileType(cname)), tstart, tdur, tmode);

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
			if(tdata != null) 
				tdata = tdata.timeRange(CTinfo.fileType(cname), tstart, tdur, tmode);

			this.put(cname, tdata);
		}
	}
}

