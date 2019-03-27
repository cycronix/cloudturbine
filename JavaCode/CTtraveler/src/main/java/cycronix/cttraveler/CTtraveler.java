/*
Copyright 2019 Erigo Technologies

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

/*

CTtraveler

Demonstration data generator for displaying a plane with ground collision avoidance
"scan cylinders"; for display in CT/Unity.

This application is based on UDP2CT.java and CTmousetrack.java, originally developed by Cycronix.

John Wilson, Erigo Technologies

version: 2019-03-26

*/

package cycronix.cttraveler;

import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.cli.*;

import cycronix.ctlib.*;

public class CTtraveler {

	boolean zipMode=true;                // ZIP data?
	boolean debug=false;                 // turn on debug?
	double autoFlush=0.1;			     // flush interval (sec)
	double trimTime=0.;				     // trimtime (sec)
	long blocksPerSegment = 0;           // number of blocks per segment; 0 = no segment layer
	String playerName = "Traveler";      // Player name (used as part of the CT source name)
	String modelColor = "Blue";          // Color of the model in CT/Unity; must be one of: Red, Blue, Green, Yellow
	String modelType = "Biplane";        // Model type for CT/Unity; must be one of: Primplane, Ball, Biplane
	String outLoc = new String("." + File.separator + "CTdata");    // Location of the base output data folder; only used when writing out CT data to a local folder
	String sessionName = "Traveler";     // Session name to be prefixed to the source name

	// Variables to specify the CT output connection
	enum CTWriteMode { LOCAL, FTP, HTTP, HTTPS }   // Modes for writing out CT data
	CTWriteMode writeMode = CTWriteMode.HTTP;      // The selected mode for writing out CT data
	public String serverHost = "";				   // Server (FTP or HTTP/S) host:port
	public String serverUser = "";				   // Server (FTP or HTTP/S) username
	public String serverPassword = "";			   // Server (FTP or HTTP/S) password

	enum ModelColor { Red, Blue, Green, Yellow }
	enum ModelType { Primplane, Ball, Biplane }
	
	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] arg) {
		new CTtraveler(arg);
	}
	
	//--------------------------------------------------------------------------------------------------------
	public CTtraveler(String[] arg) {

		long defaultDT = 100;

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
		options.addOption(Option.builder("o").argName("base output dir").hasArg().desc("Base output directory when writing data to local folder (i.e., CTdata location); default = \"" + outLoc + "\".").build());
		options.addOption(Option.builder("s").argName("session name").hasArg().desc("Session name to be prefixed to the source path; default = " + sessionName + ".").build());
		options.addOption(Option.builder("d").argName("delta-Time").hasArg().desc("Fixed delta-time (msec) between frames; default = " + Long.toString(defaultDT) + ".").build());
		options.addOption(Option.builder("f").argName("autoFlush").hasArg().desc("Flush interval (sec); amount of data per zipfile; default = " + Double.toString(autoFlush) + ".").build());
		options.addOption(Option.builder("t").argName("trim-Time").hasArg().desc("Trim (ring-buffer loop) time (sec); this is only used when writing data to local folder; specify 0 for indefinite; default = " + Double.toString(trimTime) + ".").build());
		options.addOption(Option.builder("bps").argName("blocks per seg").hasArg().desc("Number of blocks per segment; specify 0 for no segments; default = " + Long.toString(blocksPerSegment) + ".").build());
		options.addOption(Option.builder("mc").argName("model color").hasArg().desc("Color of the Unity model; must be one of: Red, Blue, Green, Yellow; default = " + modelColor + ".").build());
		options.addOption(Option.builder("mt").argName("model type").hasArg().desc("Type of the Unity model; must be one of: Primplane, Ball, Biplane; default = " + modelType + ".").build());
		options.addOption(Option.builder("w").argName("write mode").hasArg().desc("Type of CT write connection; one of " + possibleWriteModes + "; default = " + writeMode.name() + ".").build());
		options.addOption(Option.builder("host").argName("host[:port]").hasArg().desc("Host:port when writing to CT via FTP, HTTP, HTTPS.").build());
		options.addOption(Option.builder("u").argName("username,password").hasArg().desc("Comma-delimited username and password when writing to CT via FTP, HTTP or HTTPS.").build());
		options.addOption("x", "debug", false, "Enable CloudTurbine debug output.");

		// 2. Parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {	line = parser.parse( options, arg );	}
		catch( ParseException exp ) {	// oops, something went wrong
			System.err.println( "Command line argument parsing failed: " + exp.getMessage() );
			return;
		}
		
		// 3. Retrieve the command line values
		if (line.hasOption("help")) {			// Display help message and quit
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(120);
			formatter.printHelp( "CTtraveler", options );
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

		sessionName = line.getOptionValue("s",sessionName);
		if (sessionName.isEmpty()) {
			System.err.println("You must specify a Session name.");
			System.exit(0);
		}
		if (!sessionName.endsWith("\\") && !sessionName.endsWith("/")) {
			sessionName = sessionName + File.separator;
		}

		String sdt=line.getOptionValue("d",Long.toString(defaultDT));
		long dt = Long.parseLong(sdt);

		autoFlush = Double.parseDouble(line.getOptionValue("f",""+autoFlush));
		if (autoFlush <= 0.0) {
			System.err.println("Auto flush must be a value greater than 0.");
			System.exit(0);
		}
		
		trimTime = Double.parseDouble(line.getOptionValue("t",Double.toString(trimTime)));

		blocksPerSegment = Long.parseLong(line.getOptionValue("bps",Long.toString(blocksPerSegment)));

		debug = line.hasOption("debug");

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
			serverHost = line.getOptionValue("host", serverHost);
			if (serverHost.isEmpty()) {
				System.err.println("When using write mode \"" + writeModeStr + "\", you must specify the server host.");
				System.exit(0);
			}
		}

		String userpassStr = line.getOptionValue("u","");
		if (!userpassStr.isEmpty()) {
			if (writeMode == CTWriteMode.LOCAL) {
				System.err.println("Username and password are not used when writing local CT files.");
				System.exit(0);
			}
			// This string should be comma-delimited username and password
			String[] userpassCSV = userpassStr.split(",");
			if (userpassCSV.length != 2) {
				System.err.println("When specifying a username and password, separate the username and password by a comma.");
				System.exit(0);
			}
			serverUser = userpassCSV[0];
			serverPassword = userpassCSV[1];
		}
		if ( (writeMode == CTWriteMode.HTTP) && (!serverUser.isEmpty()) ) {
			System.err.println("Please note that the username and password are not encrypted when writing CT files via HTTP.");
		}

		// CT/Unity model parameters
		String modelColorRequest = line.getOptionValue("mc",modelColor);
		modelColor = "";
		for (ModelColor mc : ModelColor.values()) {
			if (mc.name().toLowerCase().equals(modelColorRequest.toLowerCase())) {
				modelColor = mc.name();
			}
		}
		if (modelColor.isEmpty()) {
			System.err.println("Unrecognized model color, \"" + modelColorRequest + "\"; model color must be one of:");
			for (ModelColor mc : ModelColor.values()) {
				System.err.println("\t" + mc.name());
			}
			System.exit(0);
		}
		String modelTypeRequest = line.getOptionValue("mt",modelType);
		modelType = "";
		for (ModelType mt : ModelType.values()) {
			if (mt.name().toLowerCase().equals(modelTypeRequest.toLowerCase())) {
				modelType = mt.name();
			}
		}
		if (modelType.isEmpty()) {
			System.err.println("Unrecognized model type, \"" + modelTypeRequest + "\"; model type must be one of:");
			for (ModelType mt : ModelType.values()) {
				System.err.println("\t" + mt.name());
			}
			System.exit(0);
		}

		//
		// Create CTwriter (for writing data to CT format)
		//
		CTwriter ctw = null;
		System.err.println("Model: " + modelType);
		// If sessionName isn't blank, it will end in a file separator
		String srcName = sessionName + "GamePlay" + File.separator + playerName;
		System.err.println("Game source: " + srcName);
		System.err.println("    write out JSON data");
		try {
			ctw = create_CT_source(srcName,true,zipMode);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

		// Setup the data generator
		JsonGenerator jsonGenerator = new MouseDataGenerator(playerName,modelType);

		// Adjust sampInterval to keep the desired sample period
		long msec_adjust = 0;
		while (true) {
			long time_msec = System.currentTimeMillis();

			// Generate updated data
			String unityStr = jsonGenerator.generateJson(time_msec);

			if ( (unityStr != null) && (!unityStr.isEmpty()) ) {
				// Write to CT (note that we use auto-flush so there's no call to flush here)
				ctw.setTime(time_msec);
				try {
					ctw.putData("CTstates.json", unityStr);
				} catch (Exception e) {
					System.err.println("Exception putting data to CT:\n" + e);
					continue;
				}
				System.err.print("m");
			}

			// Automatically adjust sleep time (to try and maintain the desired delta-T)
			if (dt > 0) {
				if ((dt + msec_adjust) > 0) {
					try { Thread.sleep(dt + msec_adjust); } catch(Exception e) {};
				}
				long now_time_msec = System.currentTimeMillis();
				if ( (now_time_msec - time_msec) > (dt + 10) ) {
					msec_adjust = msec_adjust - 1;
				} else if ( (now_time_msec - time_msec) < (dt-10) ) {
					msec_adjust = msec_adjust + 1;
				}
			}
		}
		
	}

	//
	// Create CTwriter
	//
	private CTwriter create_CT_source(String srcName, boolean bAutoFlushI, boolean bZipI) throws Exception {
		CTwriter ctw = null;
		long autoFlushMillis = (long)(autoFlush*1000.); // flush interval (msec)
		CTinfo.setDebug(debug);
		if (writeMode == CTWriteMode.LOCAL) {
			ctw = new CTwriter(outLoc + srcName,trimTime);
			System.err.println("    data will be written to local folder \"" + outLoc + "\"");
		} else if (writeMode == CTWriteMode.FTP) {
			CTftp ctftp = new CTftp(srcName);
			try {
				ctftp.login(serverHost, serverUser, serverPassword);
			} catch (Exception e) {
				throw new IOException( new String("Error logging into FTP server \"" + serverHost + "\":\n" + e.getMessage()) );
			}
			ctw = ctftp; // upcast to CTWriter
			System.err.println("    data will be written to FTP server at " + serverHost);
		} else if ( (writeMode == CTWriteMode.HTTP) || (writeMode == CTWriteMode.HTTPS) ) {
			String protocolStr = writeMode.name().toLowerCase();
			CThttp cthttp = new CThttp(srcName,protocolStr + "://"+serverHost);
			if (!serverUser.isEmpty()) {
				try {
					cthttp.login(serverUser, serverPassword);
					// System.err.println("Username and password have been set.");
				} catch (Exception e) {
					throw new IOException( new String("Error logging into " + writeMode.name() + " server \"" + serverHost + "\":\n" + e.getMessage()) );
				}
			}
			ctw = cthttp; // upcast to CTWriter
			System.err.println("    data will be written to " + writeMode.name() + " server at " + serverHost);
		}
		ctw.setBlockMode(false,bZipI);
		ctw.autoSegment(blocksPerSegment);
		if (bAutoFlushI) {
			ctw.autoFlush(autoFlushMillis);
		}

		return ctw;
	}

} //end class CTtraveler
