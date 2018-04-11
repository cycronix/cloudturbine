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
//---------------------------------------------------------------------------------	
// CTplugin:  read CloudTurbine files stored by timestamp folders
// adapted from CTread, DT plugin wrapper
// Matt Miller, Cycronix
// 02/18/2014

package ctplugin;

import java.io.File;
import java.util.ArrayList;
import com.rbnb.sapi.*;

import cycronix.ctlib.*;

//---------------------------------------------------------------------------------	

public class CTplugin {
	
//	private static String 	sourceFolder = null;
	private static String 	rbnbServer = "localhost:3333";  // 127.0.0.1 is more DTN than "localhost"?
	private static boolean 	swapFlag=false;			// swap bytes?
	private static char		typeDefault = 'n';		
	private static boolean 	debug = false;
	private static boolean 	preRegister=false;
	
//---------------------------------------------------------------------------------	
// constructor for PlugIn loop

 public CTplugin(String rootFolder) {	
	 
	try {
	CTreader ctreader = new CTreader(rootFolder);
	ctreader.setDebug(debug);
	
	ArrayList<String>sourceList = ctreader.listSources();

	if(sourceList.size() == 0) {
		if(debug) System.err.println("No Sources in rootFolder: "+rootFolder);
		new PIrun(rootFolder, null, ctreader).start();
	}
	else {
		if(debug) System.err.println("Multiple Sources found: "+sourceList.size());
		for(String source:sourceList) {
			new PIrun(rootFolder, source, ctreader).start();
		}
	}
	} catch(Exception e) {
		System.err.println("Oops, error on startup: "+e);
		System.exit(0);
	}
 }
 
//---------------------------------------------------------------------------------	   
 public static void main (String[] args) {

 	boolean printHelp=false;
 	String rootFolder="CTdata";
 	int dirArg = 0;

 	while((dirArg<args.length) && args[dirArg].startsWith("-")) {		// clugey arg parsing
 		if(args[dirArg].equals("-r")) 	swapFlag = true;
 		if(args[dirArg].equals("-h")) 	printHelp = true;
 		if(args[dirArg].equals("-x")) 	debug = true;
 		if(args[dirArg].equals("-p")) 	preRegister = true;
 		if(args[dirArg].equals("-f"))  	rootFolder = args[++dirArg]; 
 		if(args[dirArg].equals("-a"))  	rbnbServer = args[++dirArg]; 
 		if(args[dirArg].equals("-t"))  	typeDefault = args[++dirArg].charAt(0); 
 		dirArg++;
 	}
 	
 	if(args.length > dirArg) rootFolder = args[dirArg++];
 	
 	if(printHelp) {
 		System.out.println(
	    		"CTplugin"+
	    		"\n -a <host>          DataTurbine server address; default="+rbnbServer+
	    		"\n -t <typeDefault>   default data type if file extension is not recognized; default="+typeDefault+
	    		"\n -r                 reverse byte order?; default=false"+
	    		"\n -x                 turn on debug?; default=false"+
	    		"\n -p                 pre-register channels for discovered sources?; default=false"+
 				"\n -h                 display this message"+
	    		"\n <folder>           CloudTurbine data folder; default="+rootFolder
 				);
 		System.exit(0);
 	}

 	System.err.println("CTplugin, rootFolder: "+rootFolder+"...");
// 	System.err.println("default fileType: "+typeDefault);
    new CTplugin(rootFolder);			// start the plugin check 
     
     try{					// hang out
     	while(true) Thread.sleep(1000);
     }
     catch(Exception e) {
     	System.err.println("CTplugin exception (exiting): "+e);
     }
 }

//---------------------------------------------------------------------------------	   
//PIrun:  PlugIn run top level logic
 
	private class PIrun extends Thread {
		
		private PlugIn plugin=null; 			//plugin connection
		private Sink sink=null; 				//sink connection
		private String sinkName="CTplugin"; 		//sink connection name
		private CTreader ctreader;
		private String 	sourceFolder = null;	
		private String sName;
		private PlugInChannelMap regPicm = null;
		
		PIrun(String rootFolder, String source, CTreader ctrdr) {
			if(source == null)	{
				sourceFolder = rootFolder;
				String[] sParts = rootFolder.split(File.separator);
				sName = sParts[sParts.length-1];
			}
			else {
				sName = source;
				sourceFolder = rootFolder + File.separator + source;
			}
			if(debug) System.err.println("PIrun: "+sName);
			ctreader = ctrdr;
		}
		
		public void run() {
			if(debug) System.err.println("PIrun, sourceFolder: "+sourceFolder+", sName: "+sName);
			
			PlugInChannelMap picm=new PlugInChannelMap();
			plugin=new PlugIn();

			try {
				plugin.OpenRBNBConnection(rbnbServer,sName);	
				sink = new Sink();
				sink.OpenRBNBConnection(rbnbServer,sinkName);
			} catch(Exception e) {
				System.err.println("Error on connect: "+e);
				System.exit(0);
				//		    RBNBProcess.exit(0);
			}

			if(preRegister) {
				try {
					System.err.println("pre-registering channels for source: "+sName);
					picm.Add("...");
					regPicm = handleRegistration(picm);
//					plugin.Register(picm);			// pre-register
					System.err.println("pre-register done: "+sName);
				} catch(Exception se) {
					System.err.println("Oops, exception on preRegister: "+se);
				}
			}
			
			//process is to wait for request, get data from sink, convert data, send response, repeat
			while (true) {
				try {
					if(debug) System.err.println("waiting on fetch...");
					picm=plugin.Fetch(-1); //block until request arrives
					if(debug) System.err.println("request picm: "+picm);

					if(picm.NumberOfChannels()==0) {
						System.err.println("oops, no channels in request");
						continue;
					}

					if (picm.GetRequestReference().equals("registration")) {
						if(debug) System.err.println("registration request!");
						plugin.Flush(handleRegistration(picm));
						continue;
					}
					else {
						double tget = picm.GetRequestStart();
						double tdur = picm.GetRequestDuration();
						String tmode = picm.GetRequestReference();
						CTmap ctmap = PI2CTmap(picm);
//						ctmap = ctreader.getDataMap(ctmap, sourceFolder, tget, tdur, tmode);	// old abs path source
						ctmap = ctreader.getDataMap(ctmap, sName, tget, tdur, tmode);			// new rel path source
						picm = CT2PImap(picm, ctmap, tget, tdur, tmode);
						if(debug) System.err.println("Flush picm: "+picm+", nframe: "+ctmap.size());
						if(picm == null) System.err.println("no channels!");
						else			 plugin.Flush(picm);
					}
				} 
				catch(Exception e) {
					System.err.println("oops, exception: "+e+", picm: "+picm);
					e.printStackTrace();
					try{
						Thread.sleep(1000);
						picm.PutDataAsString(0,"error: "+e);  
						plugin.Flush(picm);
					} catch(Exception ee){};	// no busy loop
					//				System.exit(0);		// no infinite loops
				}
			}
		}

		//---------------------------------------------------------------------------------	
		// handleRegistration:  reply to registration request

		private PlugInChannelMap handleRegistration(PlugInChannelMap picm) throws Exception {
			// 	picm.PutTime( (System.currentTimeMillis()/1000.0), 0.0);

			try{ 
				if ( ((picm.GetName(0).equals("...")) || (picm.GetName(0).equals("*"))) ) {
					// JPW, in next 2 calls, change sourceFolder to sName (ie, don't use full path)
					double otime=ctreader.oldTime(sName); 
					double ntime=ctreader.newTime(sName);
					if(debug) System.err.println("handleRegistration picm[0]: "+picm.GetName(0)+", oldtime: "+otime+", newtime: "+ntime);

					picm.PutTime(otime, ntime-otime);
					if(regPicm != null) {	// round about way to cache registration. actual RBNB pre-register does not provide time limits
						if(debug) System.err.println("returning pre-fetched registration...");
						picm.Clear();
						for(int i=0; i<regPicm.NumberOfChannels(); i++) {
							picm.Add(regPicm.GetName(i));
							picm.PutDataAsString(i,regString(regPicm.GetName(i)));
							picm.PutMime(i,"text/xml");
						}
						return picm;		// return pre-fetched registration
					}
					
					ArrayList<String>chanlist = ctreader.listChans(sourceFolder);
					if(chanlist == null) {
						throw(new Exception("Oops, no channels for sourceFolder: "+sourceFolder));
					}
					picm.Clear();
					for(int i=0; i<chanlist.size(); i++) {
						int j = picm.Add(chanlist.get(i));
						if(debug) System.err.println("register["+j+"]: "+picm.GetName(j));
						picm.PutDataAsString(i,regString(chanlist.get(i)));
						picm.PutMime(i,"text/xml");
					}
				} else {
					CTmap ctmap = PI2CTmap(picm);								// ignore time limits registration request
					// JPW, in next 2 calls, change sourceFolder to sName (ie, don't use full path)
					double otime=ctreader.oldTime(sName,ctmap); 
					double ntime=ctreader.newTime(sName,ctmap);
					picm.PutTime(otime, ntime-otime);
					if(debug) System.err.println("handleRegistration picm[0]: "+picm.GetName(0)+", oldtime: "+otime+", newtime: "+ntime);
				
					int nchan = picm.NumberOfChannels();
					if(debug) System.err.println("reg-request channel: "+picm.GetName(0)+", num: "+nchan);
					for(int i=0; i<nchan; i++) {			// put start/end time for each channel individually?
						picm.PutDataAsString(i,regString(picm.GetName(i)));
						picm.PutMime(i,"text/xml");
					}
				}
			} catch(Exception e) {  System.err.println("handleRegistration exception: "+e); e.printStackTrace(); }
			//	 plugin.Flush(picm);
			return(picm);
		}

		String regString(String fname) {
			String fmime = "\t\t<mime>application/octet-stream</mime>\n";
			if(fname.endsWith(".jpg")) fmime = "\t\t<mime>image/jpeg</mime>\n";
			char ftype = CTinfo.fileType(fname,typeDefault);
			// 	System.err.println("fname: "+fname+", fmime: "+fmime+", ftype: "+ftype);
			int wlen = 1;
			if	   (ftype == 'F' || ftype == 'I' || ftype == 'N') wlen = 8;
			else if(ftype == 'f' || ftype == 'i' || ftype == 'n') wlen = 4;

			String result=
					"<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
							+"<!DOCTYPE rbnb>\n"
							+"<rbnb>\n"
							+"\t\t<size>"+wlen+"</size>\n"
							+fmime
							+"</rbnb>\n"; 
			return(result);
		}

		//---------------------------------------------------------------------------------	
		// utility conversion funcs
		public CTmap PI2CTmap(PlugInChannelMap picm) {
			CTmap ctmap = new CTmap();
			int nchan = picm.NumberOfChannels();
			for(int i=0; i<nchan; i++) {
				//			ctmap.add(picm.GetName(i),new CTdata(picm.GetTimes(i), picm.GetDataAsFloat64(i)));
				ctmap.add(picm.GetName(i));		// only need names this direction
			}
			return ctmap;
		}

		public PlugInChannelMap CT2PImap(PlugInChannelMap picm, CTmap ctmap, double tget, double tdur, String tmode) {
			int nchan = ctmap.size();
			try {
				for(int i=0; i<nchan; i++) {
					String cname = ctmap.getName(i);
					picm.Add(cname);
					CTdata td;			
//					if(tmode.equals("absolute")) td = ctmap.getTimeData(cname, tget, tdur);	// use tget,tdur for trim
//					else						 td = ctmap.getTimeData(cname);
					if(debug) System.err.println("getTimeData, tget: "+tget+", tdur: "+tdur+", tmode: "+tmode);
//					td = ctmap.getTimeData(cname, tget, tdur, tmode);
					td = ctmap.getTimeData(cname);		// already trimmed MJM 4/27/16
					if(debug) System.err.println("cname: "+cname+", fileType: "+CTinfo.fileType(cname, typeDefault)+", td.size: "+td.size());
					if(td == null || td.size()==0) continue;		// no data found this time

					td.setSwap(swapFlag);
					char fileType = CTinfo.fileType(cname, typeDefault);

					if(fileType != 'B') picm.PutTimes(td.getTime());		// doesn't work with byte[][]
					//				System.err.println("PutTimes len: "+td.getTime().length);
					switch(fileType) {
					case 'F':	picm.PutDataAsFloat64(i,td.getDataAsFloat64()); break;
					case 'f':	picm.PutDataAsFloat32(i,td.getDataAsFloat32()); break;
					case 'I':	picm.PutDataAsInt64(i,td.getDataAsInt64()); break;
					case 'i':	picm.PutDataAsInt32(i,td.getDataAsInt32()); break;
					case 'j':	picm.PutDataAsInt16(i,td.getDataAsInt16()); break;
					case 'n':	picm.PutDataAsFloat32(i,td.getDataAsNumericF32()); break;
					case 'N':	picm.PutDataAsFloat64(i,td.getDataAsNumericF64()); break;
					case 'B':
					default:
						if(debug) System.err.println("PutByte[] len: "+td.size());
						double[] time = td.getTime();
						byte[][] bdata = td.getData();
						for(int j=0; j<td.size(); j++) {
							picm.PutTime(time[j], 0.);
							picm.PutDataAsByteArray(i,bdata[j]); 
						}
						break;
					}

				}
			} catch(Exception e) {
				System.err.println("CTplugin Exception: "+e);
//				e.printStackTrace();
				return null;
			}
			return picm;
		}
	}
}



