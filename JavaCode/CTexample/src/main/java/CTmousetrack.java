
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.cli.*;
import cycronix.ctlib.*;

/**
 * CloudTurbine demo source
 * Track mouse position and output x,y tracks
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2018/07/20
 * 
*/

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

public class CTmousetrack {

	enum CTWriteMode { LOCAL, FTP, HTTP, HTTPS }   // Modes for writing out CT data

	public static void main(String[] args) {

		String outLoc = new String("." + File.separator + "CTdata");    // Location of the base output data folder; only used when writing out CT data to a local folder
		String srcName = "CTmousetrack";                                        // name of the output CT source
		long blockPts = 10;                                                     // points per block flush
		long sampInterval = 10;                                                 // time between sampling updates, msec
		double trimTime = 0.0;                                                  // amount of data to keep (trim time), sec
		boolean debug = false;                                                  // turn on debug?

		// Specify the CT output connection
		CTWriteMode writeMode = CTWriteMode.LOCAL;     // The selected mode for writing out CT data
		String serverHost = "";				   // Server (FTP or HTTP/S) host:port
		String serverUser = "";				   // Server (FTP or HTTPS) username
		String serverPassword = "";			   // Server (FTP or HTTPS) password

		// Concatenate all of the CTWriteMode types
		String possibleWriteModes = "";
		for (CTWriteMode wm : CTWriteMode.values()) {
			possibleWriteModes = possibleWriteModes + ", " + wm.name();
		}
		// Remove ", " from start of string
		possibleWriteModes = possibleWriteModes.substring(2);

		//
		// Argument processing using Apache Commons CLI
		//
		// 1. Setup command line options
		Options options = new Options();
		options.addOption("h", "help", false, "Print this message.");
		options.addOption(Option.builder("o").argName("base output dir").hasArg().desc("Base output directory when writing data to local folder (i.e., this is the location of CTdata folder); default = \"" + outLoc + "\".").build());
		options.addOption(Option.builder("s").argName("source name").hasArg().desc("Name of source to write data to; default = \"" + srcName + "\".").build());
		options.addOption(Option.builder("b").argName("points per block").hasArg().desc("Number of points per block; default = " + Long.toString(blockPts) + ".").build());
		options.addOption(Option.builder("dt").argName("samp interval msec").hasArg().desc("Sampling period in msec; default = " + Long.toString(sampInterval) + ".").build());
		options.addOption(Option.builder("t").argName("trim time sec").hasArg().desc("Trim (ring-buffer loop) time (sec); this is only used when writing data to local folder; specify 0 for indefinite; default = " + Double.toString(trimTime) + ".").build());
		options.addOption(Option.builder("w").argName("write mode").hasArg().desc("Type of CT write connection; one of " + possibleWriteModes + "; default = " + writeMode.name() + ".").build());
		options.addOption(Option.builder("host").argName("host[:port]").hasArg().desc("Host:port when writing to CT via FTP, HTTP, HTTPS.").build());
		options.addOption(Option.builder("u").argName("username,password").hasArg().desc("Comma-delimited username and password when writing to CT via FTP or HTTPS.").build());
		options.addOption("x", "debug", false, "Enable CloudTurbine debug output.");

		// 2. Parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {	line = parser.parse( options, args );	}
		catch( ParseException exp ) {	// oops, something went wrong
			System.err.println( "Command line argument parsing failed: " + exp.getMessage() );
			return;
		}

		// 3. Retrieve the command line values
		if (line.hasOption("help")) {			// Display help message and quit
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(120);
			formatter.printHelp( "CTmousetrack", options );
			return;
		}

		outLoc = line.getOptionValue("o",outLoc);
		if (!outLoc.endsWith("\\") && !outLoc.endsWith("/")) {
			outLoc = outLoc + File.separator;
		}
		// Make sure the base output folder location ends in "CTdata"
		if (!outLoc.endsWith("CTdata\\") && !outLoc.endsWith("CTdata/")) {
			outLoc = outLoc + "CTdata" + File.separator;
		}

		srcName = line.getOptionValue("s",srcName);

		blockPts = Long.parseLong(line.getOptionValue("b",Long.toString(blockPts)));

		sampInterval = Long.parseLong(line.getOptionValue("dt",Long.toString(sampInterval)));

		trimTime = Double.parseDouble(line.getOptionValue("t",Double.toString(trimTime)));

		// Type of output connection
		String writeModeStr = line.getOptionValue("w",writeMode.name());
		boolean bMatch = false;
		for (CTWriteMode wm : CTWriteMode.values()) {
			if (wm.name().toLowerCase().equals(writeModeStr.toLowerCase())) {
				writeMode = wm;
				bMatch = true;
			}
		}
		if (!bMatch) {
			System.err.println("Unrecognized write mode, \"" + writeModeStr + "\"; write mode must be one of " + possibleWriteModes);
			System.exit(0);
		}
		if (writeMode != CTWriteMode.LOCAL) {
			// User must have specified the host
			// If FTP or HTTPS, they may also specify username/password
			serverHost = line.getOptionValue("host",serverHost);
			if (serverHost.isEmpty()) {
				System.err.println("When using write mode \"" + writeModeStr + "\", you must specify the server host.");
				System.exit(0);
			}
			if ((writeMode == CTWriteMode.FTP) || (writeMode == CTWriteMode.HTTPS)) {
				String userpassStr = line.getOptionValue("u","");
				if (!userpassStr.isEmpty()) {
					// This string should be comma-delimited username and password
					String[] userpassCSV = userpassStr.split(",");
					if (userpassCSV.length != 2) {
						System.err.println("When specifying a username and password for write mode \"" + writeModeStr + "\", separate the username and password by a comma.");
						System.exit(0);
					}
					serverUser = userpassCSV[0];
					serverPassword = userpassCSV[1];
				}
			}
		}

		debug = line.hasOption("debug");

		System.err.println("CTmousetrack parameters:");
		System.err.println("\tsource = " + srcName);
		System.err.println("\tpoints per block = " + blockPts);
		System.err.println("\tsample interval = " + sampInterval + " msec");
		System.err.println("\ttrim time = " + trimTime + " sec");

		try {
			//
			// Setup CTwriter
			//
			CTwriter ctw = null;
			CTinfo.setDebug(debug);
			if (writeMode == CTWriteMode.LOCAL) {
				ctw = new CTwriter(outLoc + srcName,trimTime);
				System.err.println("\tdata will be written to local folder \"" + outLoc + "\"");
			} else if (writeMode == CTWriteMode.FTP) {
				CTftp ctftp = new CTftp(srcName);
				try {
					ctftp.login(serverHost, serverUser, serverPassword);
				} catch (Exception e) {
					throw new IOException( new String("Error logging into FTP server \"" + serverHost + "\":\n" + e.getMessage()) );
				}
				ctw = ctftp; // upcast to CTWriter
				System.err.println("\tdata will be written to FTP server at " + serverHost);
			} else if (writeMode == CTWriteMode.HTTP) {
				// Don't send username/pw in HTTP mode since they will be unencrypted
				CThttp cthttp = new CThttp(srcName,"http://"+serverHost);
				ctw = cthttp; // upcast to CTWriter
				System.err.println("\tdata will be written to HTTP server at " + serverHost);
			} else if (writeMode == CTWriteMode.HTTPS) {
				CThttp cthttp = new CThttp(srcName,"https://"+serverHost);
				// Username/pw are optional for HTTPS mode; only use them if username is not empty
				if (!serverUser.isEmpty()) {
					try {
						cthttp.login(serverUser, serverPassword);
					} catch (Exception e) {
						throw new IOException( new String("Error logging into HTTP server \"" + serverHost + "\":\n" + e.getMessage()) );
					}
				}
				ctw = cthttp; // upcast to CTWriter
				System.err.println("\tdata will be written to HTTPS server at " + serverHost);
			}
			ctw.setBlockMode(blockPts>1,blockPts>1);
			ctw.autoFlush(0);                          // no autoflush
			ctw.autoSegment(1000);

			// screen dims
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			double width = screenSize.getWidth();
			double height = screenSize.getHeight();
			
			// use Map for consolidated putData
			Map<String,Object>cmap = new LinkedHashMap<String,Object>();
			
			// loop and write some output
			for(int i=0; i<1000000; i++) {						// go until killed
				ctw.setTime(System.currentTimeMillis());				
				cmap.clear(); 
				
				Point mousePos = MouseInfo.getPointerInfo().getLocation();
				
				cmap.put("x", (float)(mousePos.getX()/width));					// normalize
				cmap.put("y", (float)((height-mousePos.getY())/height));		// flip Y (so bottom=0)
				ctw.putData(cmap);
				
				if(((i+1)%blockPts)==0) {
					ctw.flush();
					System.err.print(".");
				}
				try { Thread.sleep(sampInterval); } catch(Exception e) {};
			}
			ctw.flush(); 	// wrap up
		} catch(Exception e) {
			System.err.println("CT exception: "+e);
			e.printStackTrace();
		} 
	}
}
