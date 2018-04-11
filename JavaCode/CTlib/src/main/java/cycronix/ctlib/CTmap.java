/*
Copyright 2018 Cycronix

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

package cycronix.ctlib;

import java.util.Map;
import java.util.TreeMap;

/**
 * A CloudTurbine utility class to manage TreeMap of name-CTdata pairs
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2014/03/06
 * 
*/

//---------------------------------------------------------------------------------	
//CTmap: map of CTdata's by name
//Matt Miller, Cycronix
//02/18/2014

// CTmap:  map of CTdata's by name
public class CTmap extends TreeMap<String,CTdata>{
	private static final long serialVersionUID = 1L;
//	private Map<String, CTdata> ctMap = new TreeMap<String, CTdata>();
	private boolean hasData=false;
	
	/**
	 * Constructor
	 */
	public CTmap() {
		super();
	}
	
	/**
	 * Constructor 
	 * @param name initial name 
	 */
	CTmap(String name) {
		this.add(name,  null);
		hasData = false;
	}
	
	/**
	 * test if CTmap has data in it
	 * @return T/F
	 */
	boolean hasData() {
		return hasData;
	}
	
	/**
	 * add named channel without data
	 * @param cname chan name
	 */
	public void add(String cname) {
		add(cname, null);		// list-of-channels without data (for ref)
	}
	
	/**
	 * add channel with data to map
	 * @param cname channel name
	 * @param timedata time/data 
	 */
	public void add(String cname, CTdata timedata) {
		if(timedata != null) hasData=true;				// book-keep
		CTdata tdata = this.get(cname);
		if(tdata != null) {
			tdata.add(timedata);		// append vs replace
			this.put(cname,  tdata);
		}
		else	this.put(cname,  timedata);
	}
	
	/**
	 * get CTdata for channel at time
	 * @param cname channel name
	 * @param tstart start time (sec)
	 * @param tdur interval duration (sec)
	 * @return CTdata with results
	 */
	public CTdata get(String cname, double tstart, double tdur) {		// time units = seconds
//		System.err.println("CTmap.get: "+cname+", ctMap.get: "+this.get(cname));
		return this.get(cname, tstart, tdur, "absolute");
	}
	
	/**
	 * get CTdata for channel at time and reference mode
	 * @param cname channel name
	 * @param tstart start time (sec)
	 * @param tdur interval (sec)
	 * @param tmode time reference ("absolute", "oldest", "newest")
	 * @return CTdata with results
	 */
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
	
	/**
	 * get name at index
	 * @param index integer
	 * @return channel name
	 */
	public String getName(int index) {
		return (String) this.keySet().toArray()[index];
	}
	
	/**
	 * check if name present
	 * @param cname channel name
	 * @return T/F
	 */
	public boolean checkName(String cname) {
		return this.containsKey(cname);
	}

	/**
	 * size of data
	 * @return size
	 */
	public long datasize() {
		long n=0;
		for (Map.Entry<String, CTdata> entry : this.entrySet()) {
			if(entry != null) {
				CTdata ctd = entry.getValue();
				if(ctd != null) n += ctd.size();
			}
		}
		return n;
	}
	
	// trim entire map of channels by time-range
	void trim(double tstart, double tdur, String tmode) {
		for (Map.Entry<String, CTdata> entry : this.entrySet()) {
		    String cname = entry.getKey();
		    CTdata tdata = entry.getValue();
//		    System.err.println("CTmap.trim, cname: "+cname+", lendat: "+tdata.size());
			if(tdata != null) 
				tdata = tdata.timeRange(CTinfo.fileType(cname), tstart, tdur, tmode);

			this.put(cname, tdata);
		}
	}
	
	// trim single channel in map by time-range
	void trim(String cname, double tstart, double tdur, String tmode) {
		this.put(cname, this.get(cname, tstart, tdur, tmode));
	}
}

