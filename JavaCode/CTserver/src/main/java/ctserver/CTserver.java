//---------------------------------------------------------------------------------	
// CTserver:  Web HTTP interface to CTreader
// Matt Miller, Cycronix
// 02/18/2014	initial version using Jetty
// 07/16/2014	converted to NanoHTTPD
// 04/04/2016	updated to NanoHTTPD V2.2

/*
 URL Syntax:
 
 http://cloudturbine.net:/CT/Source/Channel?key1=value&key2=value
 
 where key-value pairs:
 
 key	value		examples						description
 
 t		time		123456789					time relative to tref (sec)
 r		tref		absolute,newest,oldest		time reference
 d		duration	100							time interval (sec)
 dt		datatype	s							format as string (s) default, binary (b), HTML (H)
 f		timefetch	t,d							f=t to fetch time-only (default time+data)

 */

package ctserver;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import javax.imageio.ImageIO;

import cycronix.ctlib.*;

//---------------------------------------------------------------------------------	

public class CTserver extends NanoHTTPD {
	
	private static final String servletRoot = "/CT";
	private static final String rbnbRoot = "/RBNB";
//	private static String rootFolder="CTdata";
	private static String rootFolder=null;		// for compat with CT2DB
	private static CTreader ctreader=null;
	public static boolean debug=false;
	private static boolean logOut=false;
	private static boolean swapFlag = false;
    private static String resourceBase = "CTweb";
    private static String sourceFolder = null;
    private static int MaxDat = 1000000;		// max number data elements to return (was 65536)
    private static long queryCount=0;
    private static int scaleImage=1;			// reduce image size by factor
    
	//---------------------------------------------------------------------------------	

	public CTserver(int port) {
// TODO for HTTPS support:
//	    super.makeSecure(sslServerSocketFactory, sslProtocols);
// e.g.    super.makeSecure(NanoHTTPD.makeSSLSocketFactory("/keystore.jks", "password".toCharArray()));
// nanoHTTPD test code: https://github.com/NanoHttpd/nanohttpd/.../HttpSSLServerTest.java
		
	    super(port);
     	ctreader = new CTreader(rootFolder);
     	CTinfo.setDebug(debug);
	}
	
	public CTserver(int port, String IresourceBase, String IrootFolder) {
        super(port);
		resourceBase = IresourceBase;
		rootFolder = IrootFolder;
     	ctreader = new CTreader(rootFolder);
     	CTinfo.setDebug(debug);
	}
	
	//---------------------------------------------------------------------------------	

    public static void main(String[] args) throws Exception {
    	int	port = 8000;			// default port
    	
    	if(args.length == 0) {
    		System.err.println("CTserver -r -x -l -p <port> -f <webfolder> -s <sourceFolder> rootFolder");
    	}
    	
     	int dirArg = 0;
     	while((dirArg<args.length) && args[dirArg].startsWith("-")) {		// arg parsing
     		if(args[dirArg].equals("-r")) 	swapFlag = true;
     		if(args[dirArg].equals("-x")) 	debug = true;
     		if(args[dirArg].equals("-l")) 	logOut = true;
     		if(args[dirArg].equals("-p")) 	port = Integer.parseInt(args[++dirArg]);
     		if(args[dirArg].equals("-S")) 	scaleImage = Integer.parseInt(args[++dirArg]);
     		if(args[dirArg].equals("-f"))  	resourceBase = args[++dirArg]; 
     		if(args[dirArg].equals("-s"))  	sourceFolder = args[++dirArg]; 
     		dirArg++;
     	}
     	if(args.length > dirArg) rootFolder = args[dirArg++];

     	if(rootFolder == null && sourceFolder != null) {	// source is full path
     		rootFolder = new File(sourceFolder).getParent();
     		sourceFolder = new File(sourceFolder).getName();
     	}
     	else if(rootFolder == null) {				// check for a couple defaults
     		if		(new File("CTdata").exists()) 		rootFolder = "CTdata";
     		else if (new File(".."+File.separator+"CTdata").exists()) rootFolder = ".."+File.separator+"CTdata";
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
     	     	
     	CTserver server = new CTserver(port);
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

    //---------------------------------------------------------------------------------	
    public long queryCount() {
    	return queryCount;
    }
    
  //---------------------------------------------------------------------------------	
    // callback for http requests

    @Override public Response serve(IHTTPSession session) {
    	queryCount++;
    	String request = session.getUri();
    	Map<String,String> parms = session.getParms();
    	StringBuilder response = new StringBuilder(64);			// estimate initial size
    	boolean remoteAddr = !session.getHeaders().get("remote-addr").equals("127.0.0.1");
    	CTinfo.debugPrint(logOut,new Date().toString()+", request: "+request+", parms: "+session.getQueryParameterString()+", remote-addr: "+session.getHeaders().get("remote-addr"));
    	
    	// system clock utility
    	if(request.equals("/sysclock")) return formResponse(newFixedLengthResponse(Response.Status.OK,MIME_PLAINTEXT,(""+System.currentTimeMillis())));
    	
    	// server resource files
    	if(!request.startsWith(servletRoot)  && !request.startsWith(rbnbRoot)) {
    		try {
    			if(request.equals("/")) {
    				if(new File(resourceBase+"/index.htm").exists()) 		request = "/index.htm";
    				else if(new File(resourceBase+"/index.html").exists()) 	request = "/index.html";
    				else													request = "/webscan.htm";
    			}
    			FileInputStream fis = new FileInputStream(resourceBase+request);
    			return formResponse(newChunkedResponse(Response.Status.OK, mimeType(request, MIME_HTML), fis));
    		} catch(Exception e) {
    			return formResponse(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found: "+e));
    		}
    	}

    	String pathParts[] = request.split("/");		// split into 2 parts: cmd/multi-part-path
    	// response.addHeader("Access-Control-Allow-Origin", "*");		// for debugging

    	try {
			double duration=0., start=0.;
			String reference="newest";			
			String param;	char ftype='s';	  char fetch = 'b';
			param = parms.get("d");		if(param != null) duration = Double.parseDouble(param);
			param = parms.get("t");		if(param != null) { start = Double.parseDouble(param); reference="absolute"; }
			param = parms.get("r");		if(param != null) reference = param;
			param = parms.get("f");		if(param != null) fetch = param.charAt(0);
			param = parms.get("dt");	if(param != null) ftype = param.charAt(0);
    		
    		if(request.equals(servletRoot+"/") || request.equals(rbnbRoot+"/")) request = servletRoot;		//  strip trailing slash
    		
    		if(request.equals(servletRoot) || request.equals(rbnbRoot)) {			// Root level request for Sources
    			CTinfo.debugPrint("source request: "+request);
    			printHeader(response,request,"/");
    			ArrayList<String> slist = new ArrayList<String>();
    			
    			if(sourceFolder == null) slist = ctreader.listSources();
//    			if(sourceFolder == null) slist = ctreader.listSourcesRecursive();
    			else					 slist.add(sourceFolder);
    			
    			if(slist==null || slist.size()==0) response.append("No Sources!");
    			else {
    				for(String sname : slist) {
    					CTinfo.debugPrint("src: "+sname);
    					response.append("<li><a href=\""+(request+"/"+sname)+"/\">"+sname+"/</a><br>");          
    				}
    			}

//    			return newFixedLengthResponse(Response.Status.OK, MIME_HTML, response.toString());
				return formResponse(response.toString());
    		}
//    		else if(pathParts.length == 3) {
        	else if(request.endsWith("/")) {										// Source level request for Channels

    			CTinfo.debugPrint("channel request: "+request);
    			if(pathParts.length < 3) return formResponse(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found (bad trailing slash?)"));
    			String sname = pathParts[2];
    			for(int i=3; i<pathParts.length; i++) sname += ("/"+pathParts[i]);		// multi-level source name
    			if(sname.endsWith("/")) sname = sname.substring(0,sname.length()-2);
    			CTinfo.debugPrint("CTserver listChans for source: "+(rootFolder+File.separator+sname));
    			ArrayList<String> clist = ctreader.listChans(rootFolder+File.separator+sname);

    			if(clist == null) response.append("<NULL>");
    			else {
    				if(ftype == 'H') {								// all chans in HTML table format
    	
    	    			double time[] = null;
    	    			ArrayList<String[]> chanlist = new ArrayList<String[]>();
    	    			
						response.append("<table id="+sname+">\n");
						response.append("<tr><th>Time</th>");
						
    					for(String chan : clist) {
    						response.append("<th>"+chan+"</th>");
    						CTdata tdata = ctreader.getData(sname,chan,start,duration,reference);
    	    				if(time == null) time = tdata.getTime();			// presume all times follow first chan
    	    				chanlist.add(tdata.getDataAsString(CTinfo.fileType(chan,'s')));
    					}
    					response.append("</tr>\n");
						for(int i=0; i<time.length; i++) {
							response.append("<tr>");
							response.append("<td>"+(time[i]/86400.+25569.)+"</td>");		// spreadsheet time (epoch 1900)
							for(int j=0; j<chanlist.size(); j++) {
								String c[] = chanlist.get(j);								// possibly unequal data sizes
								if(i < c.length) response.append("<td>"+c[i]+"</td>");
							}
							response.append("</tr>\n");	
						}
						response.append("</table>");
    				}
    				else {
    	    			printHeader(response,request,"/"+pathParts[1]);
    					for(String cname : clist) {
    						CTinfo.debugPrint(sname+"/chan: "+cname);
    						response.append("<li><a href=\""+cname+"\">"+cname+"</a><br>");
    					}
    				}
    			}

//    			return newFixedLengthResponse(Response.Status.OK, MIME_HTML, response.toString());
				return formResponse(response.toString());
    		}
    		else {									// Channel level request for data
    			CTinfo.debugPrint("data request: "+request);
    			String source = pathParts[2];
    			for(int i=3; i<pathParts.length-1; i++) source += ("/"+pathParts[i]);		// multi-level source name
//    			String chan = pathParts[3];				//  presumes /CT/Source/Chan with no sub-level nesting
    			String chan = pathParts[pathParts.length-1];
    			String sourcePath = rootFolder+File.separator+source;
    			String[] strdata=null;		

    			if(fetch == 't') 	ctreader.setTimeOnly(true);		// don't waste time/memory getting data...
    			else    			ctreader.setTimeOnly(false);
    			
    			CTdata tdata = ctreader.getData(source,chan,start,duration,reference);
    			if(tdata == null) {		// empty response for WebTurbine compatibility
    				CTinfo.debugPrint("no such channel: "+source+File.separator+chan);
    				return formResponse(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"));
    			}
    			else {
    				tdata.setSwap(swapFlag);
    				double time[] = tdata.getTime();
    				if(time == null) {
						System.err.println("Oops, No data on fetch: "+request);
	    				return formResponse(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"));
    				}
    				int numData = time.length;
    				CTinfo.debugPrint("--------CTserver getData: "+chan+", numData: "+numData+", fetch: "+fetch+", ftype: "+ftype);

    				if(numData > MaxDat && ftype != 'b') {		// unlimited binary fetch
    					System.err.println("limiting output points to: "+MaxDat);
    					numData = MaxDat;
    				}
    				//            			if(time.length == 0) System.err.println("CTserver warning: no data!");
    				if(numData > 0) {

    					if(fetch == 't') {		// only want times (everything else presume 'b' both)
    						for(int i=time.length-numData; i<numData; i++) response.append(formatTime(time[i]) + "\n");		// if limited, get most recent
//    						return newFixedLengthResponse(Response.Status.OK, MIME_HTML, response.toString());
    						return formResponse(response.toString());
    					}
    					else {		
    						if(ftype == 's') ftype = CTinfo.fileType(chan,'s');
    						CTinfo.debugPrint("getData: "+chan+"?t="+start+"&d="+duration+"&r="+reference+", ftype: "+ftype);

    						switch(ftype) {

    						// binary types returned as byteArrays
    						case 'b':	
    						case 'B':           
    							byte[][]bdata = tdata.getData();

    							if(bdata == null || bdata[0] == null)
    								return formResponse(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"));
    		    				
    							NanoHTTPD.Response resp;
        						InputStream input;

            					if(chan.endsWith(".jpg")) {
            						if(scaleImage>1 && remoteAddr && bdata[0].length>100000) {			// down-size large images
            							BufferedImage image=ImageIO.read(new ByteArrayInputStream(bdata[0]));
            							image = scale(image, image.getWidth()/scaleImage, image.getHeight()/scaleImage);
            							ByteArrayOutputStream baos = new ByteArrayOutputStream();
            							ImageIO.write(image, "jpg", baos);
            							input = new ByteArrayInputStream(baos.toByteArray());
            						} else {
            							input = new ByteArrayInputStream(bdata[0]);		// only return 1st image
            						}
        							resp = formResponse(newChunkedResponse(Response.Status.OK, mimeType(chan, "application/image/jpeg"), input));
            					}
            					else {           						
            						ByteArrayOutputStream output = new ByteArrayOutputStream();	
            						if(bdata.length > 1 /* && !chan.endsWith(".wav") && !chan.endsWith(".mp3") */) {	
//                					if(bdata.length > 1 && !chan.endsWith(".wav") && !chan.endsWith(".mp3")) {	// don't want to concatenate mp3, wav audio...
            							for(byte[] b : bdata) output.write(b, 0, b.length);		// concatenate response
            							input = new ByteArrayInputStream(output.toByteArray());
            							CTinfo.debugPrint("concatenated data ("+bdata.length+") chunks: "+output.size());
            						}
            						else {
            							input = new ByteArrayInputStream(bdata[0]);
            							CTinfo.debugPrint("single chunk data, size: "+bdata[0].length);
            						}
        							resp = newChunkedResponse(Response.Status.OK, mimeType(chan, "application/octet-stream"), input);
            					}
    							
            					String htime = formatTime(time[0]);		// [0] at start or [time.length-1] at end???
            					String hdur = "0";
            					if(time.length > 1) hdur = formatTime(time[time.length-1]-time[0]);
            					double oldTime = ctreader.oldTime(sourcePath,chan);
            					double newTime = ctreader.newTime(sourcePath,chan);

            					double lagTime = ((double)System.currentTimeMillis()/1000.) - newTime;
            					String holdest = formatTime(oldTime);		// cache these?
            					String hnewest = formatTime(newTime);         			
            					String hlag = formatTime(lagTime);
            					
            					CTinfo.debugPrint("time[0]: "+time[0]+", time.length: "+time.length+", hdur: "+hdur);
    							resp.addHeader("time", htime);								// sec
    							long lastmod = (long)(1000*time[time.length-1]);
    							String smod = new Date(lastmod).toGMTString();
    							CTinfo.debugPrint("lastmod: "+smod);
            					resp.addHeader("Last-Modified", ""+smod);			// msec
            					
    							resp.addHeader("duration", hdur);
    							resp.addHeader("X-Duration", hdur);		// compatible with WebTurbine

    							resp.addHeader("oldest", holdest);
    							resp.addHeader("newest", hnewest);
    							resp.addHeader("cache-control", "private, max-age=3600");			// enable browse cache
 
        						CTinfo.debugPrint("---CTserver: "+chan+", size: "+input.available() +", nitems: "+bdata.length);
        						CTinfo.debugPrint("+++CTserver: time: "+htime+", duration: "+hdur+", oldest: "+holdest+", newest: "+hnewest+", hlag: "+hlag);

    							return formResponse(resp);	
    						
    						// HTML table format (for import to spreadsheets)
    						case 'H':			
    							strdata = tdata.getDataAsString(CTinfo.fileType(chan,'s'));		// convert any/all numeric types to string
    							if(strdata != null) {
    								response.append("<table id="+source+"/"+chan+">\n");
    								response.append("<tr><th>Time</th><th>"+source+"/"+chan+"</th></tr>");
    								for(int i=time.length-numData; i<numData; i++) {
    									response.append("<tr>");
    									if(fetch != 'd') response.append("<td>"+(time[i]/86400.+25569.)+"</td>");		// spreadsheet time (epoch 1900)
    									if(fetch != 't') response.append("<td>"+strdata[i]+"</td>");
    									response.append("</tr>\n");	
    								}
    								response.append("</table>");
    								return formResponse(newFixedLengthResponse(Response.Status.OK, MIME_HTML, response.toString()));
    							}
    							else {
    								System.err.println("Unrecognized ftype: "+ftype);
    								return formResponse(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"));
    							}
    							
    						// all other types returned as rows of time,value strings
    						default:
    							strdata = tdata.getDataAsString(ftype);		// convert any/all numeric types to string
    							if(strdata != null) {
    								for(int i=time.length-numData; i<numData; i++) response.append(formatTime(time[i]) +","+strdata[i]+"\n");		// most recent
//    								return newFixedLengthResponse(Response.Status.OK, MIME_HTML, response.toString());
    								return formResponse(response.toString());
    							}
    							else {
    								System.err.println("Unrecognized ftype: "+ftype);
    								return formResponse(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"));
    							}
    						}
    					}
    				}
    			}
    		}
    	} catch(Exception e) { 
    		System.err.println("doGet Exception: "+e); e.printStackTrace(); 
    	}
    	
    	return formResponse(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"));
    }
    
    //---------------------------------------------------------------------------------	

    private static NanoHTTPD.Response formResponse(String response) {
		return formResponse(newFixedLengthResponse(Response.Status.OK, MIME_HTML, response.toString()));
    }
    
    private static NanoHTTPD.Response formResponse(NanoHTTPD.Response resp) {
		resp.addHeader("Access-Control-Allow-Origin", "*");            // CORS enable
		resp.addHeader("Access-Control-Allow-Methods", "GET, POST");   // CORS enable
		resp.addHeader("Access-Control-Expose-Headers", "oldest,newest,duration,time");
		return resp;
    }
    
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
    
    //---------------------------------------------------------------------------------	
    private BufferedImage scale(BufferedImage img, int targetWidth, int targetHeight) {

        int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = img;
        BufferedImage scratchImage = null;
        Graphics2D g2 = null;

        int w = img.getWidth();
        int h = img.getHeight();

        int prevW = w;
        int prevH = h;

        do {
            if (w > targetWidth) {
                w /= 2;
                w = (w < targetWidth) ? targetWidth : w;
            }

            if (h > targetHeight) {
                h /= 2;
                h = (h < targetHeight) ? targetHeight : h;
            }

            if (scratchImage == null) {
                scratchImage = new BufferedImage(w, h, type);
                g2 = scratchImage.createGraphics();
            }

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(ret, 0, 0, w, h, 0, 0, prevW, prevH, null);

            prevW = w;
            prevH = h;
            ret = scratchImage;
        } while (w != targetWidth || h != targetHeight);

        if (g2 != null) {
            g2.dispose();
        }

        if (targetWidth != ret.getWidth() || targetHeight != ret.getHeight()) {
            scratchImage = new BufferedImage(targetWidth, targetHeight, type);
            g2 = scratchImage.createGraphics();
            g2.drawImage(ret, 0, 0, null);
            g2.dispose();
            ret = scratchImage;
        }

        return ret;
    }
    
}