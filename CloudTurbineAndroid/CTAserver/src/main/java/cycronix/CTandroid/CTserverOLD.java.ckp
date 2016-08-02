//---------------------------------------------------------------------------------	
// CTserverOLD:  Web HTTP interface to CTreader
// Matt Miller, Cycronix
// 02/18/2014	initial version using Jetty
// 07/16/2014	converted to NanoHTTPDold

package cycronix.CTandroid;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

import android.content.res.AssetManager;
import android.os.Environment;
import cycronix.ctlib.CTdata;
import cycronix.ctlib.CTmap;
import cycronix.ctlib.CTreader;

//---------------------------------------------------------------------------------	

public class CTserverOLD extends NanoHTTPDold {
	
	private static final String servletRoot = "/CT";
	private static final String rbnbRoot = "/RBNB";
//	private static String rootFolder="CTdata";
	private static String rootFolder=null;		// for compat with CTAserver
	private static CTreader ctreader=null;
	private static boolean debug=false;
	private static boolean swapFlag = false;
    private static int MaxDat = 32768;			// max number data elements to return
    private static AssetManager amgr;

	//---------------------------------------------------------------------------------	

	public CTserverOLD(int port, AssetManager Iamgr) {
	    super(port);
	    amgr = Iamgr;
     	rootFolder = Environment.getExternalStorageDirectory() + "/CTdata";
     	System.err.println("rootFolder: "+rootFolder);
     	ctreader = new CTreader(rootFolder);
     	ctreader.setDebug(debug);
	}
	
	public CTserverOLD(int port, AssetManager Iamgr, String IrootFolder) {
        super(port);
	    amgr = Iamgr;
		rootFolder = Environment.getExternalStorageDirectory() + "/" + IrootFolder;
     	ctreader = new CTreader(rootFolder);
     	ctreader.setDebug(debug);
	}
	
	//---------------------------------------------------------------------------------	
/*
    public static void main(String[] args) throws Exception {
    	int	port = 8000;			// default port
    	
    	if(args.length == 0) {
    		System.err.println("CTserverOLD -r -x -p <port> -f <webfolder> rootFolder");
//    		System.exit(0);
    	}
    	
     	int dirArg = 0;
     	while((dirArg<args.length) && args[dirArg].startsWith("-")) {		// arg parsing
     		if(args[dirArg].equals("-r")) 	swapFlag = true;
     		if(args[dirArg].equals("-x")) 	debug = true;
     		if(args[dirArg].equals("-p")) 	port = Integer.parseInt(args[++dirArg]);
     		if(args[dirArg].equals("-f"))  	resourceBase = args[++dirArg]; 
     		dirArg++;
     	}
     	if(args.length > dirArg) rootFolder = args[dirArg++];

     	if(rootFolder == null) {				// check for a couple defaults
     		if		(new File("CTdata").exists()) 		rootFolder = "CTdata";
     		else if (new File("CloudTurbine").exists()) rootFolder = "CloudTurbine";
     		else {
        		System.err.println("Cannot find default data folder.  Please specify.");
        		System.exit(0);	
     		}
     	}
     	else {
     		if(!(new File(rootFolder).exists())) {
        		System.err.println("Cannot find specified data folder: "+rootFolder);
        		System.exit(0);	
     		}
     	}
     	
     	ctreader = new CTreader(rootFolder);
     	ctreader.setDebug(debug);
     	
     	CTserverOLD server = new CTserverOLD(port);
        try {
        	server.start();
        } catch (IOException ioe) {
            System.err.println("Couldn't start server:\n" + ioe);
            System.exit(-1);
        }
        System.out.println("Server started.  webFolder: "+resourceBase+", dataFolder: "+rootFolder+", HTTP port: "+port+"\n");
        while(true) {										// hang out until signalled
        	try { Thread.sleep(10000); } 
        	catch(Exception e){
        		server.stop();
        		System.out.println("Server stopped.");
        	};				
        }
    }
    */
  //---------------------------------------------------------------------------------	
    // callback for http requests

    @Override public Response serve(IHTTPSession session) {
    	String request = session.getUri();
    	Map<String,String> parms = session.getParms();
    	StringBuilder response = new StringBuilder(64);			// estimate initial size
    	if(debug) System.err.println("CTserverOLD Request: "+request);

    	// serve resource files
    	if(!request.startsWith(servletRoot)  && !request.startsWith(rbnbRoot)) {
    		try {
//    			if(request.equals("/")) request = "/webscan.htm";
    			if(request.equals("/")) request = "webscan.htm";
    			if(request.startsWith("/")) request = request.substring(1,request.length());
//    			FileInputStream fis = new FileInputStream(resourceBase+request);
    			InputStream fis = amgr.open(request);
    			//            		System.err.println("file: "+(resourceBase+request)+", size: "+fis.available());
    			return new NanoHTTPDold.Response(Response.Status.OK, mimeType(request, MIME_HTML), fis);
    		} catch(Exception e) {
    			return new NanoHTTPDold.Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found: "+e);
    		}
    	}

    	String pathParts[] = request.split("/");		// for now split into 2 parts: cmd/multi-part-path
    	// response.addHeader("Access-Control-Allow-Origin", "*");		// for debugging

    	try {
    		if(request.equals(servletRoot) || request.equals(rbnbRoot)) {			// Root level request for Sources
    			//            		System.err.println("source request: "+request);
    			printHeader(response,request,"/");
    			ArrayList<String> slist = ctreader.listSources();
    			if(slist==null || slist.size()==0) response.append("No Sources!");
    			else {
    				for(String sname : slist) {
    					response.append("<li><a href=\""+(request+"/"+sname)+"/\">"+sname+"/</a><br>");          
    				}
    			}
    			return new NanoHTTPDold.Response(Response.Status.OK, MIME_HTML, response.toString());
    		}
    		else if(pathParts.length == 3) {			// Source level request for Channels
//    			System.err.println("pathParts[0]: "+pathParts[0]+", pathParts[1]: "+pathParts[1]+", pathParts[2]: "+pathParts[2]);
    			printHeader(response,request,"/"+pathParts[1]);
    			String sname = pathParts[2];
    			if(sname.endsWith("/")) sname = sname.substring(0,sname.length()-2);

    			ArrayList<String> clist = ctreader.listChans(rootFolder+File.separator+sname);

    			if(clist == null) response.append("<NULL>");
    			else {
    				for(String cname : clist) {
    					response.append("<li><a href=\""+cname+"\">"+cname+"</a><br>");
    				}
    			}
    			return new NanoHTTPDold.Response(Response.Status.OK, MIME_HTML, response.toString());
    		}
    		else {									// Channel level request for data
    			//            		System.err.println("data request: "+request);
    			double duration=0., start=0.;
    			String reference="newest";
    			String source = pathParts[2];
    			String chan = pathParts[3];				//  presumes /CT/Source/Chan with no sub-level nesting

    			String param;	char ftype='s';	  char fetch = 'b';
    			param = parms.get("d");		if(param != null) duration = Double.parseDouble(param);
    			param = parms.get("t");		if(param != null) start = Double.parseDouble(param);
    			param = parms.get("r");		if(param != null) reference = param;
    			param = parms.get("f");		if(param != null) fetch = param.charAt(0);
    			param = parms.get("dt");	if(param != null) ftype = param.charAt(0);

    			CTdata tdata = ctreader.getData(source,chan,start,duration,reference);
    			if(tdata == null) {		// empty response for WebTurbine compatibility
    				if(debug) System.err.println("no such channel: "+chan);
    				return new NanoHTTPDold.Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    			}
    			else {
    				tdata.setSwap(swapFlag);
    				double time[] = tdata.getTime();
    				int numData = time.length;
    				if(numData > MaxDat) {
    					System.err.println("limiting output points to: "+MaxDat);
    					numData = MaxDat;
    				}
    				//            			if(time.length == 0) System.err.println("CTserverOLD warning: no data!");
    				if(numData > 0) {

    					if(fetch == 't') {		// only want times (everything else presume 'b' both)
    						for(int i=0; i<numData; i++) response.append(formatTime(time[i]) + "\n");
    						return new NanoHTTPDold.Response(Response.Status.OK, MIME_HTML, response.toString());
    					}
    					else {		
    						// all CTdata aggregated as array of byte[] arrays. Fetch/convert to desired format
    						if(ftype == 's') ftype = CTmap.fileType(chan,'s');
//    						System.err.println("getData: "+chan+"?t="+start+"&d="+duration+"&r="+reference+", ftype: "+ftype);

    						switch(ftype) {
    						case 's':			// string text
    							byte[][]Tdata = tdata.getData();
    							for(int i=0; i<numData; i++)
    								response.append(formatTime(time[i])+","+new String(Tdata[i])+"\n");

    							return new NanoHTTPDold.Response(Response.Status.OK, MIME_HTML, response.toString());

    						case 'N':
    							double Ndata[] = tdata.getDataAsNumericF64();
    							for(int i=0; i<numData; i++)
    								response.append(formatTime(time[i]) +","+Ndata[i]+"\n");

    							return new NanoHTTPDold.Response(Response.Status.OK, MIME_HTML, response.toString());

    						case 'n':
    							float ndata[] = tdata.getDataAsNumericF32();
    							for(int i=0; i<numData; i++)
    								response.append(formatTime(time[i]) +","+ndata[i]+"\n");

    							return new NanoHTTPDold.Response(Response.Status.OK, MIME_HTML, response.toString());

    						case 'I':
    							long[] ldata = tdata.getDataAsInt64();
    							for(int i=0; i<numData; i++)						
    								response.append(formatTime(time[i]) +","+ldata[i]+"\n");

    							return new NanoHTTPDold.Response(Response.Status.OK, MIME_HTML, response.toString());

    						case 'i':
    							int[] idata = tdata.getDataAsInt32();
    							for(int i=0; i<numData; i++)
    								response.append(formatTime(time[i]) +","+idata[i]+"\n");

    							return new NanoHTTPDold.Response(Response.Status.OK, MIME_HTML, response.toString());

    						case 'j':
    							short[] sdata = tdata.getDataAsInt16();
    							for(int i=0; i<numData; i++)
    								response.append(formatTime(time[i])+","+sdata[i]+"\n");

    							return new NanoHTTPDold.Response(Response.Status.OK, MIME_HTML, response.toString());

    						case 'F':	
    							double ddata[] = tdata.getDataAsFloat64();
    							for(int i=0; i<numData; i++)
    								response.append(formatTime(time[i]) +","+(float)ddata[i]+"\n");

    							return new NanoHTTPDold.Response(Response.Status.OK, MIME_HTML, response.toString());

    						case 'f':	
    							float fdata[] = tdata.getDataAsFloat32();
    							for(int i=0; i<numData; i++)
    								response.append(formatTime(time[i]) +","+(float)fdata[i]+"\n");

    							return new NanoHTTPDold.Response(Response.Status.OK, MIME_HTML, response.toString());

    						case 'b':	
    						case 'B':           
    							byte[][]bdata = tdata.getData();
    							InputStream input = new ByteArrayInputStream(bdata[0]);		// only return 1st image
    							if(bdata.length > 1) System.err.println("Warning, only returning first ByteArray");
    							NanoHTTPDold.Response resp = new NanoHTTPDold.Response(Response.Status.OK, mimeType(chan, "application/octet-stream"), input);
    							resp.addHeader("time", formatTime(time[0]));
    							return resp;

    						default:
    							System.err.println("Unrecognized ftype: "+ftype);
    							return new NanoHTTPDold.Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    						}
    					}
    				}
    			}
    		}
    	} catch(Exception e) { 
    		System.err.println("doGet Exception: "+e); e.printStackTrace(); 
    	}
    	
    	return new NanoHTTPDold.Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    }
    
    //---------------------------------------------------------------------------------	

    private static String mimeType(String fname, String deftype) {
		String mime = deftype;
		if		(fname.endsWith(".css")) mime = "text/css";
		else if	(fname.endsWith(".js")) mime = "application/javascript";
		else if	(fname.endsWith(".jpg")) mime = "image/jpeg";
		else if	(fname.endsWith(".png")) mime = "image/png";
//		System.err.println("mime type: "+mime);
		return mime;
    }
    
    private static String formatTime(double time) {
		if(((long)time) == time) return(Long.toString((long)time));
		else					 return Double.toString(time);		
//		else					 return new DecimalFormat("0.000").format(time);		// loses precision
    }
    
    private static void printHeader(StringBuilder response, String path, String uplevel) {
    	try {
    		String title = "Directory listing for: "+path;
    		response.append("<head><title>"+title+"</title></head>");
    		if(uplevel != null) response.append("<a href=\""+uplevel+"\">[Up one level]</a><br>");
    		response.append("<h3>"+title+"</h3>");
    	} catch(Exception e) {
    		System.err.println("oops, exception: "+e);
    	}
    }
}