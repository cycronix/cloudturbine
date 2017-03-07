/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
//---------------------------------------------------------------------------------	
// CTweb:  Web HTTP interface to CTreader
// Matt Miller, Cycronix

// 11/01/2016	revert to Jetty
// 07/16/2014	converted to NanoHTTPD
// 04/04/2016	updated to NanoHTTPD V2.2
// 02/18/2014	initial version using Jetty

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

package ctweb;
 
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

//import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.server.Connector;

import cycronix.ctlib.CTdata;
import cycronix.ctlib.CTinfo;
import cycronix.ctlib.CTreader;

//---------------------------------------------------------------------------------	

public class CTweb {
	
	private static final String servletRoot = "/CT";
	private static final String rbnbRoot = "/RBNB";
//	private static String rootFolder="CTdata";
	private static String rootFolder=null;				// for compat with CT2DB
	private static CTreader ctreader=null;
	public static boolean debug=false;
	public static boolean Debug=false;					// debug local plus CT
	private static boolean logOut=false;
	private static boolean swapFlag = false;
    private static String resourceBase = "CTweb";
    private static String sourceFolder = null;
    private static int MaxDat = 1000000;				// max number data elements to return (was 65536)
    private static long queryCount=0;
    private static String keyStoreFile="ctweb.jks";		// HTTPS keystore file path
    private static String keyStorePW="ctweb.pw";		// keystore PW
	private static int	port = 8000;					// default port
	private static int sslport = 8443;					// HTTPS port (0 means none)
	//---------------------------------------------------------------------------------	

    public static void main(String[] args) throws Exception {

    	if(args.length == 0) {
    		System.err.println("CTweb -r -x -l -p <port> -P <sslport> -f <webfolder> -s <sourceFolder> -k <keystorefile> -K <keystorePW> rootFolder");
    	}
    	
     	int dirArg = 0;
     	while((dirArg<args.length) && args[dirArg].startsWith("-")) {		// arg parsing
     		if(args[dirArg].equals("-r")) 	swapFlag = true;
     		if(args[dirArg].equals("-x")) 	debug = true;
     		if(args[dirArg].equals("-X")) 	Debug=true; 
     		if(args[dirArg].equals("-l")) 	logOut = true;
     		if(args[dirArg].equals("-p")) 	port = Integer.parseInt(args[++dirArg]);
     		if(args[dirArg].equals("-P")) 	sslport = Integer.parseInt(args[++dirArg]);
     		if(args[dirArg].equals("-f"))  	resourceBase = args[++dirArg]; 
     		if(args[dirArg].equals("-s"))  	sourceFolder = args[++dirArg]; 
     		if(args[dirArg].equals("-k"))	keyStoreFile = args[++dirArg];
     		if(args[dirArg].equals("-K"))	keyStorePW = args[++dirArg];
     		dirArg++;
     	}
     	if(args.length > dirArg) rootFolder = args[dirArg++];

     	// If sourceFolder has been specified, make sure it exists
     	if ( (sourceFolder != null) && ( (new File(sourceFolder).exists() == false) || (new File(sourceFolder).isDirectory() == false) ) ) {
     		System.err.println("The source folder doesn't exist or isn't a directory.");
 			System.exit(0);
     	}

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

     	// create CT reader 
     	ctreader = new CTreader(rootFolder);
     	CTinfo.setDebug(Debug);
       
     	// setup and start Jetty HTTP server
     	Server server = setupHTTP();
        server.start();
        server.join();
    }

    
    //---------------------------------------------------------------------------------	
    // setup HTTP/S Jetty 
    
    private static Server setupHTTP() throws FileNotFoundException {

        // Create a basic jetty server object without declaring the port. 
        Server server = new Server();

        // HTTP Configuration
        HttpConfiguration http_config = new HttpConfiguration();
        if(sslport>0) {
        	http_config.setSecureScheme("https");
        	http_config.setSecurePort(sslport);
        	http_config.addCustomizer(new SecureRequestCustomizer(false,0L,false));	// disable HSTS, allow HTTP and HTTPS both
        }
        http_config.setOutputBufferSize(32768);
   
        // HTTP connector
        ServerConnector http = new ServerConnector(server,
                new HttpConnectionFactory(http_config));
        http.setPort(port);
        http.setIdleTimeout(30000);

        if(sslport>0) {		// setup HTTPS
        	File ksFile = new File(keyStoreFile);
        	if (ksFile.exists()) {
        		// SSL Context Factory for HTTPS
        		SslContextFactory sslContextFactory = new SslContextFactory();
        		
//                sslContextFactory.setExcludeCipherSuites("^.*_(MD5|SHA|SHA1)$");		// enable old ciphers?
        		
        		sslContextFactory.setKeyStorePath(ksFile.getAbsolutePath());
        		sslContextFactory.setKeyStorePassword(keyStorePW);
        		//        	sslContextFactory.setKeyManagerPassword(keypw);

        		// HTTPS Configuration
        		// A new HttpConfiguration object is needed for the next connector and
        		// you can pass the old one as an argument to effectively clone the
        		// contents. On this HttpConfiguration object we add a
        		// SecureRequestCustomizer which is how a new connector is able to
        		// resolve the https connection before handing control over to the Jetty
        		// Server.
        		HttpConfiguration https_config = new HttpConfiguration(http_config);
        		SecureRequestCustomizer src = new SecureRequestCustomizer();
        		src.setStsMaxAge(2000);
        		src.setStsIncludeSubDomains(true);
        		https_config.addCustomizer(src);

        		// HTTPS connector
        		// We create a second ServerConnector, passing in the http configuration
        		// we just made along with the previously created ssl context factory.
        		// Next we set the port and a longer idle timeout.
        		ServerConnector https = new ServerConnector(server,
        				new SslConnectionFactory(sslContextFactory,HttpVersion.HTTP_1_1.asString()),
        				new HttpConnectionFactory(https_config));
        		https.setPort(sslport);
        		https.setIdleTimeout(500000);

        		// Here you see the server having multiple connectors registered with
        		// it, now requests can flow into the server from both http and https
        		// urls to their respective ports and be processed accordingly by jetty.

        		// Set the connectors
        		server.setConnectors(new Connector[] { http, https });
        	}
        	else {
        		System.err.println("Keystore file ("+keyStoreFile+") not found; HTTPS disabled.");
        		sslport = 0;
        		server.setConnectors(new Connector[] { http });
        	}
        }
        else server.setConnectors(new Connector[] { http });

        // Set a handler
        
        // following ResourceHandler code doesn't seem to help? Handle resourceBase explicitly in doGet()
//        ResourceHandler resource_handler = new ResourceHandler();
//        resource_handler.setDirectoriesListed(true);
//       resource_handler.setWelcomeFiles(new String[]{ "index.htm", "index.html", "cloudscan.htm", "webscan.htm"});
//        resource_handler.setResourceBase(resourceBase);

        ServletHandler shandler = new ServletHandler();
        ServletHolder sholder;
        sholder = new ServletHolder(new CTServlet());

        sholder.setAsyncSupported(true);					// need fewer threads if non-blocking?
        sholder.setInitParameter("maxThreads", "100");		// how many is good?
        shandler.addServletWithMapping(sholder, "/*");
        server.setHandler(shandler);

        String msg;
        if(sslport > 0) msg = ", HTTP port: "+port+", HTTPS port: "+sslport;
        else				 msg = ", HTTP port: "+port;
        System.out.println("Server started.  webFolder: "+resourceBase+", dataFolder: "+rootFolder+msg+"\n");

        return server;
    }
    
    //---------------------------------------------------------------------------------	
    // callback for http requests
    @SuppressWarnings("serial")
    public static class CTServlet extends HttpServlet {
        
    	@Override
    	protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			if(debug) System.err.println("doOptions, request: "+request.getPathInfo()+", queryCount: "+queryCount+", request.method: "+request.getMethod());
			response.addHeader("Access-Control-Allow-Origin", "*");            // CORS enable
			response.addHeader("Access-Control-Allow-Methods", "GET, POST, HEAD, OPTIONS");   // CORS enable
			response.addHeader("Access-Control-Allow-Headers", "If-None-Match");
//			response.addHeader("Allow", "GET, POST, HEAD, OPTIONS");
    	}
    	
    	@Override
    	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    		
    		if(debug) {
    			String uri = request.getScheme() + "://" +
    		             request.getServerName() + 
    		             ("http".equals(request.getScheme()) && request.getServerPort() == 80 || "https".equals(request.getScheme()) && request.getServerPort() == 443 ? "" : ":" + request.getServerPort() ) +
    		             request.getRequestURI() +
    		            (request.getQueryString() != null ? "?" + request.getQueryString() : "");
    			System.err.println("doGet, URI: "+uri+", queryCount: "+queryCount+", request.method: "+request.getMethod());
    		}
//    		String servletPath = request.getServletPath();
    		String pathInfo = request.getPathInfo();
    		
    		queryCount++;
    		StringBuilder sbresp = new StringBuilder(64);			// estimate initial size

    		// system clock utility
    		if(pathInfo.equals("/sysclock")) {
    			response.setContentType("text/plain");
    			response.getWriter().println(""+System.currentTimeMillis());
    		}

    		// server resource files
    		if(!pathInfo.startsWith(servletRoot)  && !pathInfo.startsWith(rbnbRoot)) {
    			try {
    				if(pathInfo.equals("/")) {
    					if(new File(resourceBase+"/index.htm").exists()) 		pathInfo = "/index.htm";
    					else if(new File(resourceBase+"/index.html").exists()) 	pathInfo = "/index.html";
    					else													pathInfo = "/webscan.htm";
    				}

        			response.setContentType(mimeType(pathInfo, "text/html"));	
        			OutputStream out = response.getOutputStream();
    				FileInputStream in = new FileInputStream(resourceBase+pathInfo);	// limit to resourceBase folder
    				byte[] buffer = new byte[4096];
    				int length;
    				while ((length = in.read(buffer)) > 0){
    				    out.write(buffer, 0, length);
    				}
    				in.close();
    				out.flush();
    			} catch(Exception e) {
//    				System.err.println("Exception on welcome file read, pathInfo: "+pathInfo+", Exception: "+e);
					formResponse(response, null);		// add CORS header even for error response
    				response.sendError(HttpServletResponse.SC_NOT_FOUND);
    			}
    			return;
    		}

    		String pathParts[] = pathInfo.split("/");		// split into 2 parts: cmd/multi-part-path

    		try {
    			double duration=0., start=0.;
    			String reference="newest";			
    			String param;	char ftype='s';	  char fetch = 'b';
    			param = request.getParameter("d");	if(param != null) duration = Double.parseDouble(param);
    			param = request.getParameter("t");	if(param != null) { start = Double.parseDouble(param); reference="absolute"; }
    			param = request.getParameter("r");	if(param != null) reference = param;
    			param = request.getParameter("f");	if(param != null) fetch = param.charAt(0);
    			param = request.getParameter("dt");	if(param != null) ftype = param.charAt(0);

    			if(pathInfo.equals(servletRoot+"/") || pathInfo.equals(rbnbRoot+"/")) pathInfo = servletRoot;		//  strip trailing slash

    			if(pathInfo.equals(servletRoot) || pathInfo.equals(rbnbRoot)) {			// Root level request for Sources
    				if(debug) System.err.println("source request: "+pathInfo);
    				
    				printHeader(sbresp,pathInfo,"/");
    				ArrayList<String> slist = new ArrayList<String>();

    				if(sourceFolder == null) slist = ctreader.listSources();
    				// if(sourceFolder == null) slist = ctreader.listSourcesRecursive();	// recursive now default
    				else					 slist.add(sourceFolder);

    				if(slist==null || slist.size()==0) sbresp.append("No Sources!");
    				else {
    					for(String sname : slist) {
    						if(debug) System.err.println("src: "+sname);
//        					if(debug) System.err.println("src: "+sname+", sourceDiskSize: "+ (CTinfo.diskUsage(rootFolder+File.separator+sname,4096)/1024)+"K");
    						sbresp.append("<li><a href=\""+(pathInfo+"/"+sname)+"/\">"+sname+"/</a><br>");          
    					}
    				}

    				formResponse(response, sbresp);
    				return;
    			}
    			else if(pathInfo.endsWith("/")) {										// Source level request for Channels

    				if(debug) System.err.println("channel request: "+pathInfo);
    				if(pathParts.length < 3) {
						formResponse(response, null);		// add CORS header even for error response
						if(debug) System.err.println("warning, pathparts.length<3: "+pathParts.length);
        				response.sendError(HttpServletResponse.SC_NOT_FOUND);
        				return;
    				}
    				String sname = pathParts[2];
    				for(int i=3; i<pathParts.length; i++) sname += ("/"+pathParts[i]);		// multi-level source name
    				if(sname.endsWith("/")) sname = sname.substring(0,sname.length()-2);
    				if(debug) System.err.println("CTweb listChans for source: "+(rootFolder+File.separator+sname));
    				ArrayList<String> clist = ctreader.listChans(rootFolder+File.separator+sname);

    				if(clist == null) sbresp.append("<NULL>");
    				else {
    					if(ftype == 'H') {								// all chans in HTML table format

    						double time[] = null;
    						ArrayList<String[]> chanlist = new ArrayList<String[]>();

    						sbresp.append("<table id="+sname+">\n");
    						sbresp.append("<tr><th>Time</th>");

    						for(String chan : clist) {
    							sbresp.append("<th>"+chan+"</th>");
    							CTdata tdata = ctreader.getData(sname,chan,start,duration,reference);
    							if(time == null) time = tdata.getTime();			// presume all times follow first chan
    							chanlist.add(tdata.getDataAsString(CTinfo.fileType(chan,'s')));
    						}
    						sbresp.append("</tr>\n");
    						for(int i=0; i<time.length; i++) {
    							sbresp.append("<tr>");
    							sbresp.append("<td>"+(time[i]/86400.+25569.)+"</td>");		// spreadsheet time (epoch 1900)
    							for(int j=0; j<chanlist.size(); j++) {
    								String c[] = chanlist.get(j);							// possibly unequal data sizes
    								if(i < c.length) sbresp.append("<td>"+c[i]+"</td>");
    							}
    							sbresp.append("</tr>\n");	
    						}
    						sbresp.append("</table>");
    					}
    					else {
    						printHeader(sbresp,pathInfo,"/"+pathParts[1]);
    						for(String cname : clist) {
    							if(debug) System.err.println(sname+"/chan: "+cname);
    							sbresp.append("<li><a href=\""+cname+"\">"+cname+"</a><br>");
    						}
    					}
    				}

    				formResponse(response, sbresp);
    				return;
    			}
    			else {																		// Channel level request for data
    				if(debug) System.err.println("data request: "+pathInfo);
    				
    				String source = pathParts[2];
    				for(int i=3; i<pathParts.length-1; i++) source += ("/"+pathParts[i]);		// multi-level source name
    				//    			String chan = pathParts[3];				//  presumes /CT/Source/Chan with no sub-level nesting
    				String chan = pathParts[pathParts.length-1];
    				String sourcePath = rootFolder+File.separator+source;
    				String[] strdata=null;		

    				// setTimeOnly deprecated NOP
    				//    			if(fetch == 't') 	ctreader.setTimeOnly(true);		// don't waste time/memory getting data...
    				//    			else    			ctreader.setTimeOnly(false);

    				CTdata tdata = ctreader.getData(source,chan,start,duration,reference);
    				if(tdata == null) {		// empty response for WebTurbine compatibility
    					if(debug) System.err.println("No such channel: "+pathInfo+", chan: "+chan+", start: "+start+", duration: "+duration+", refernce: "+reference);					
    					formResponse(response, null);		// add CORS header even for error response
        				response.sendError(HttpServletResponse.SC_NOT_FOUND);
    					return;
    				}
    				else {
    					tdata.setSwap(swapFlag);
    					double time[] = tdata.getTime();
    					if(time == null) {
    						if(debug) System.err.println("Oops, got data but no time data?: "+pathInfo);
    						formResponse(response, null);		// add CORS header even for error response
    	    				response.sendError(HttpServletResponse.SC_NOT_FOUND);
    	    				return;
    					}
    					
    					int numData = time.length;
    					if(debug) System.err.println("--------CTweb getData: "+chan+", numData: "+numData+", fetch: "+fetch+", ftype: "+ftype+", pathInfo: "+pathInfo);

        				// check for If-None-Match and skip duplicates.
        				if(duration==0 && fetch=='b' && reference.equals("absolute")) {		// only works for single-object requests
        					String ifnonematch = request.getHeader("If-None-Match");
        					if(ifnonematch != null) {
        						String[] matchparts = ifnonematch.split(":");
        						if(matchparts.length == 2) {
        							String matchchan = matchparts[0];
        							long matchtime = Long.parseLong(matchparts[1]);		// int-msec
        							long gottime = (long)(1000.* time[0]);				// int-msec for compare
        							String reqchan = source + "/" + chan;				// reconstruct full path
        							
        							if((matchtime == gottime) && matchchan.equals(reqchan)) {
        								if(debug) System.err.println("NOT_MODIFIED: "+matchchan+", reqTime: "+start+", gotTime: "+gottime+", ref: "+reference);

        	   							// add header info about time limits
            							double oldTime = ctreader.oldTime(sourcePath,chan);
            							double newTime = ctreader.newTime(sourcePath,chan);
            							double lagTime = ((double)System.currentTimeMillis()/1000.) - newTime;
        								formHeader(response, time[0], time[time.length-1], oldTime, newTime, lagTime);
        								
        								formResponse(response,null);
        								response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
        								return;
        							}
        						}
        					}
        				}
        					
    					if(numData > MaxDat && ftype != 'b') {		// unlimited binary fetch
    						System.err.println("CTweb: limiting output points to: "+MaxDat);
    						numData = MaxDat;
    					}
    					
    					// if(time.length == 0) System.err.println("CTweb warning: no data!");
    					if(numData > 0) {
    						if(ftype == 's' /* && fetch=='b' */) ftype = CTinfo.fileType(chan,'s');	// over-ride for certain binary types
    						if(fetch=='t') ftype ='s';								// time-only data returned as string
    						if(debug) System.err.println("getData: "+chan+"?t="+start+"&d="+duration+"&r="+reference+", ftype: "+ftype);

    						switch(ftype) {

    						// binary types returned as byteArrays (no time stamps sent!)
    						case 'b':	
    						case 'B':   
    							byte[] bdata = tdata.getDataAsByteArray();		// get as single byte array vs chunks

	   							// add header info about time limits
    							double oldTime = ctreader.oldTime(sourcePath,chan);
    							double newTime = ctreader.newTime(sourcePath,chan);
    							double lagTime = ((double)System.currentTimeMillis()/1000.) - newTime;
								formHeader(response, time[0], time[time.length-1], oldTime, newTime, lagTime);
    						
    							if(chan.toLowerCase().endsWith(".jpg")) 		response.setContentType("image/jpeg");
    							else if(chan.toLowerCase().endsWith(".wav")) 	response.setContentType("audio/wav");
    							else											response.setContentType("application/octet-stream");

    							if(bdata == null || bdata.length==0) {
    								if(debug) System.err.println("No data for request: "+pathInfo);
    								formResponse(response, null);		// add CORS header even for error response
    								response.sendError(HttpServletResponse.SC_NOT_FOUND);
    								return;
    							}
    							else	formResponse(response,null);

    							if(bdata.length < 65536) {	// unchunked
//    								System.err.println("b.length: "+bdata.length);
    								response.setContentLength(bdata.length);
    								response.getOutputStream().write(bdata);
    							}
    							else {			// chunked transfer
    								OutputStream out = response.getOutputStream();
    								InputStream input = new ByteArrayInputStream(bdata);		// only return 1st image?
    								byte[] buffer = new byte[16384];
    								int length;
    								while ((length = input.read(buffer)) > 0){
    									out.write(buffer, 0, length);
    								}
    								input.close();
    								out.flush();
    							}


    							return;

    							// HTML table format (for import to spreadsheets)
    						case 'H':			
    							strdata = tdata.getDataAsString(CTinfo.fileType(chan,'s'));		// convert any/all numeric types to string
    							if(strdata != null) {
    								sbresp.append("<table id="+source+"/"+chan+">\n");
    								sbresp.append("<tr><th>Time</th><th>"+source+"/"+chan+"</th></tr>");
    								for(int i=time.length-numData; i<numData; i++) {
    									sbresp.append("<tr>");
    									if(fetch != 'd') sbresp.append("<td>"+(time[i]/86400.+25569.)+"</td>");		// spreadsheet time (epoch 1900)
    									if(fetch != 't') sbresp.append("<td>"+strdata[i]+"</td>");
    									sbresp.append("</tr>\n");	
    								}
    								sbresp.append("</table>");
    								formResponse(response, sbresp);
    								return;
    							}
    							else {
    								System.err.println("Unrecognized ftype: "+ftype);
    								formResponse(response, null);		// add CORS header even for error response
    								response.sendError(HttpServletResponse.SC_NOT_FOUND);
    								return;
    							}

    							// all other types returned as rows of time,value strings
    						default:
    							strdata = tdata.getDataAsString(ftype);		// convert any/all numeric types to string
    							if(strdata != null) {
    								if(fetch=='t') 
    									for(int i=time.length-numData; i<numData; i++) sbresp.append(formatTime(time[i])+"\n");		// most recent
    								else if(fetch=='d') 
    									for(int i=time.length-numData; i<numData; i++) sbresp.append(strdata[i]+"\n");	
    								else
    									for(int i=time.length-numData; i<numData; i++) sbresp.append(formatTime(time[i]) +","+strdata[i]+"\n");	
    							
    	   							// add header info about time limits
        							oldTime = ctreader.oldTime(sourcePath,chan);
        							newTime = ctreader.newTime(sourcePath,chan);
        							lagTime = ((double)System.currentTimeMillis()/1000.) - newTime;
    								formHeader(response, time[0], time[time.length-1], oldTime, newTime, lagTime);  
//    								response.setContentType(mimeType(pathInfo, "text/html"));
    								response.setContentType("text/html");		// all string data in this case!
    								formResponse(response, sbresp);
    								return;
    							}
    							else {
    								System.err.println("Unrecognized ftype: "+ftype);
    								formResponse(response, null);		// add CORS header even for error response
    								response.sendError(HttpServletResponse.SC_NOT_FOUND);
    								return;
    							}
    						}

    					}
    					else {
    						// add header info about time limits even if no data
    						if(debug) System.err.println("No data for: "+pathInfo);
    						double oldTime = ctreader.oldTime(sourcePath,chan);
    						double newTime = ctreader.newTime(sourcePath,chan);
    						double lagTime = ((double)System.currentTimeMillis()/1000.) - newTime;
    						formHeader(response, start, duration, oldTime, newTime, lagTime);
        					formResponse(response, null);		// add CORS header even for error response
            				response.sendError(HttpServletResponse.SC_NOT_FOUND);
            				return;
    					}
    				}
    			}
    		} catch(Exception e) { 
    			System.err.println("doGet Exception: "+e); e.printStackTrace(); 
    		}

    		if(debug) System.err.println("Unable to respond to: "+pathInfo);
			formResponse(response, null);		// add CORS header even for error response
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
    	}
    }
    
    //---------------------------------------------------------------------------------	
    private static void formHeader(HttpServletResponse response, double startTime, double endTime, double oldTime, double newTime, double lagTime) {
    	response.addHeader("time", formatTime(startTime));								// sec
		response.addHeader("Last-Modified", ""+new Date((long)(1000*endTime)).toGMTString());			// msec
		
		double duration = endTime - startTime;
		response.addHeader("duration", formatTime(duration));
		response.addHeader("X-Duration", formatTime(duration));		// compatible with WebTurbine

		response.addHeader("oldest", formatTime(oldTime));
		response.addHeader("newest", formatTime(newTime));
		response.addHeader("lagtime",formatTime(lagTime));

		response.addHeader("cache-control", "private, max-age=3600");			// enable browse cache
		
		if(debug) System.err.println("+++CTweb: time: "+startTime+", duration: "+duration+", oldest: "+oldTime+", newest: "+newTime+", hlag: "+lagTime);
    }
    
    //---------------------------------------------------------------------------------	
 
    private static void formResponse(HttpServletResponse resp, StringBuilder sbresp) {
		resp.addHeader("Access-Control-Allow-Origin", "*");            // CORS enable
		resp.addHeader("Access-Control-Allow-Methods", "GET, POST, HEAD, OPTIONS");   // CORS enable
		resp.addHeader("Access-Control-Expose-Headers", "oldest,newest,duration,time,lagtime");
		if(sbresp == null) return;
		try {
			resp.getWriter().println(sbresp.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
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
    
    private static String mimeType(String fname, String deftype) {
		String mime = deftype;
		if		(fname.toLowerCase().endsWith(".css")) mime = "text/css";
		else if	(fname.toLowerCase().endsWith(".js")) mime = "application/javascript";
		else if	(fname.toLowerCase().endsWith(".jpg")) mime = "image/jpeg";
		else if	(fname.toLowerCase().endsWith(".png")) mime = "image/png";
		else if	(fname.toLowerCase().endsWith(".wav")) mime = "audio/wav";
		else if (fname.toLowerCase().endsWith(".csv")) mime = "text/css";
		if(debug) System.err.println("fname: "+fname+", mime type: "+mime);
		return mime;
    }
}


