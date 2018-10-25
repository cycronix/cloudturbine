/*
Copyright 2017-2018 Cycronix

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

UDP2CT

Capture UDP packets, parse and saves the data to CT files.
Writes data to 2 output sources:
1. GamePlay/<model_color>: contains the "CTstates.json" channel with JSON data for use in CT/Unity
2. [OPTIONAL] Sensors/<model_color>: contains a variety of channels parsed from the received UDP packets

The details of parsing UDP packets and creating the CTstates channel is handled by specific
helper classes.  Currently, the only helper class is XPlanePacketParser3, which parses XPlane-
specific UDP classes.

The original version of this application was based on CTudp.java, developed by Cycronix.

Matt Miller, Cycronix
John Wilson, Erigo Technologies

*/

package cycronix.udp2ct;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import org.apache.commons.cli.*;

import cycronix.ctlib.*;

public class UDP2CT {
	
	// params common to all sockets:
	String multiCast=null;			     // multicast address
	boolean zipMode=true;                // ZIP data?
	boolean packMode=true;               // turn on pack mode?
	boolean debug=false;                 // turn on debug?
	boolean udp_debug=false;             // turn on UDP packet parsing debug?
	double autoFlush=1.;			     // flush interval (sec)
	long autoFlushMillis;                // flush interval (msec)
	double trimTime=0.;				     // trimtime (sec)
	long blocksPerSegment = 0;           // number of blocks per segment; 0 = no segment layer
	CTwriter ctgamew = null;             // CloudTurbine writer connection for CT/Unity game output source
	CTwriter ctsensorw = null;           // CloudTurbine writer connection for variety of output sensor channels
	String modelColor = "Blue";          // Color of the model in CT/Unity; must be one of: Red, Blue, Green, Yellow
	String modelType = "Biplane";        // Model type for CT/Unity; must be one of: Primplane, Ball, Biplane
	String outLoc = new String("." + File.separator + "CTdata");    // Location of the base output data folder; only used when writing out CT data to a local folder
	String sessionName = "";             // Optional session name to be prefixed to the source name
	boolean bSavePacketDataToCT = true;  // Save parsed and processed data from the UDP packet to the Sensors output source?

	// Specify the CT output connection
	enum CTWriteMode { LOCAL, FTP, HTTP, HTTPS }   // Modes for writing out CT data
	CTWriteMode writeMode = CTWriteMode.LOCAL;     // The selected mode for writing out CT data
	public String serverHost = "";				   // Server (FTP or HTTP/S) host:port
	public String serverUser = "";				   // Server (FTP or HTTPS) username
	public String serverPassword = "";			   // Server (FTP or HTTPS) password

	enum ModelColor { Red, Blue, Green, Yellow }
	enum ModelType { Primplane, Ball, Biplane }
	
	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] arg) {
		new UDP2CT(arg);
	}
	
	//--------------------------------------------------------------------------------------------------------
	public UDP2CT(String[] arg) {

		int defaultPort = 4445;
		double defaultDT = 0.0;

		// For communicating with UDP server; we send a "keep alive" heartbeat message to this server
		// and will receive UDP packets from this server
		DatagramSocket clientSocket = null;  // This socket will be shared by UDPread and UDPHeartbeatTask classes
		InetAddress udpserverIP = null;
		int udpserverPort = -1;
		int heartbeatPeriod_msec = -1;

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
		options.addOption(Option.builder("s").argName("session name").hasArg().desc("Optional session name; if specified, this name is prefixed to the source path.").build());
		options.addOption(Option.builder("m").argName("multicast address").hasArg().desc("Multicast UDP address (224.0.0.1 to 239.255.255.255).").build());
		options.addOption(Option.builder("p").argName("UDP port").hasArg().desc("Port number to listen for UDP packets on; default = " + Integer.toString(defaultPort) + ".").build());
		options.addOption(Option.builder("d").argName("delta-Time").hasArg().desc("Fixed delta-time (msec) between frames; specify 0 to use arrival-times; default = " + Double.toString(defaultDT) + ".").build());
		options.addOption(Option.builder("f").argName("autoFlush").hasArg().desc("Flush interval (sec); amount of data per zipfile; default = " + Double.toString(autoFlush) + ".").build());
		options.addOption(Option.builder("t").argName("trim-Time").hasArg().desc("Trim (ring-buffer loop) time (sec); this is only used when writing data to local folder; specify 0 for indefinite; default = " + Double.toString(trimTime) + ".").build());
		options.addOption(Option.builder("udpserver").argName("IP,port,period_msec").hasArg().desc("Talk to a UDP server; send a periodic keep-alive message to the given IP:port at the specified period and receive packets from this server; not to be used with the \"-p\" option.").build());
		options.addOption(Option.builder("bps").argName("blocks per seg").hasArg().desc("Number of blocks per segment; specify 0 for no segments; default = " + Long.toString(blocksPerSegment) + ".").build());
		options.addOption(Option.builder("mc").argName("model color").hasArg().desc("Color of the Unity model; must be one of: Red, Blue, Green, Yellow; default = " + modelColor + ".").build());
		options.addOption(Option.builder("mt").argName("model type").hasArg().desc("Type of the Unity model; must be one of: Primplane, Ball, Biplane; default = " + modelType + ".").build());
		options.addOption(Option.builder("w").argName("write mode").hasArg().desc("Type of CT write connection; one of " + possibleWriteModes + "; default = " + writeMode.name() + ".").build());
		options.addOption(Option.builder("host").argName("host[:port]").hasArg().desc("Host:port when writing to CT via FTP, HTTP, HTTPS.").build());
		options.addOption(Option.builder("u").argName("username,password").hasArg().desc("Comma-delimited username and password when writing to CT via FTP or HTTPS.").build());
		options.addOption("xpack", false, "Don't pack blocks of data in the Sensors output source; the default (without this command line flag) is to pack this source.");
		options.addOption("xs", "no_sensors_out", false, "Don't save UDP packet details to the \"Sensors\" source.");
		options.addOption("xu", "udp_debug", false, "Enable UDP packet parsing debug output.");
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
			formatter.printHelp( "UDP2CT", options );
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
		if (!sessionName.isEmpty()) {
			if (!sessionName.endsWith("\\") && !sessionName.endsWith("/")) {
				sessionName = sessionName + File.separator;
			}
		}

		multiCast = line.getOptionValue("m",multiCast);

		String portStr=line.getOptionValue("p", Integer.toString(defaultPort));
		int portNum = Integer.parseInt(portStr);

		String sdt=line.getOptionValue("d",Double.toString(defaultDT));
		double dt = Double.parseDouble(sdt);

		autoFlush = Double.parseDouble(line.getOptionValue("f",""+autoFlush));
		
		trimTime = Double.parseDouble(line.getOptionValue("t",Double.toString(trimTime)));

		blocksPerSegment = Long.parseLong(line.getOptionValue("bps",Long.toString(blocksPerSegment)));

		packMode = !line.hasOption("xpack");

		bSavePacketDataToCT = !line.hasOption("no_sensors_out");

		udp_debug = line.hasOption("udp_debug");

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

		// Parameters when talking to a UDP server
		// Can't specify both "-p" and "-udpserver"
		if ( line.hasOption("p") && line.hasOption("udpserver") ) {
			System.err.println("Specify either \"-p\" (to listen on the given port(s)) or \"-udpserver\" (to talk to UDP server), not both.");
			System.exit(0);
		}
		if (line.hasOption("udpserver")) {
			String udpserverStr = line.getOptionValue("udpserver");
			// Parse the server,port,period_msec from this string
			String[] udpserverConfigCSV = udpserverStr.split(",");
			if (udpserverConfigCSV.length != 3) {
				System.err.println("Error: the \"-udpserver\" argument must contain 3 parameters: IP,port,period_msec");
				System.exit(0);
			}
			try {
				udpserverIP = InetAddress.getByName(udpserverConfigCSV[0]);
			} catch (UnknownHostException e) {
				System.err.println("Error processing the \"-udpserver\" server name:\n" + e);
				System.exit(0);
			}
			try {
				udpserverPort = Integer.parseInt(udpserverConfigCSV[1]);
				if (udpserverPort <= 0) {
					throw new Exception("Invalid port number");
				}
			} catch (Exception e) {
				System.err.println("Error: the \"-udpserver\" port must be an integer greater than 0.");
				System.exit(0);
			}
			try {
				heartbeatPeriod_msec = Integer.parseInt(udpserverConfigCSV[2]);
				if (heartbeatPeriod_msec <= 0) {
					throw new Exception("Invalid period");
				}
			} catch (Exception e) {
				System.err.println("Error: the \"-udpserver\" period_msec must be an integer greater than 0.");
				System.exit(0);
			}
			// Initialize communication with the UDP server
			try {
				// This DatagramSocket will be shared by UDPread and UDPHeartbeatTask classes
				clientSocket = new DatagramSocket();
			} catch (SocketException e) {
				System.err.println("Error creating DatagramSocket:\n" + e);
				System.exit(0);
			}
			Timer time = new Timer();
			UDPHeartbeatTask heartbeatTask = new UDPHeartbeatTask(clientSocket, udpserverIP, udpserverPort);
			time.schedule(heartbeatTask, 0, heartbeatPeriod_msec);
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
		// setup 2 instances of CTwriter:
		// 1. ctgamew:   this source will only contain the "CTstates.json" output channel, used by
		//               the CT/Unity game; since this source is a text channel, we don't want this source to be packed
		// 2. ctsensorw: output source for data unpacked from the captured UDP packetes; it is up to the parser class
		//               being employed as to what channels are written to this source
		//
		autoFlushMillis = (long)(autoFlush*1000.);
		System.err.println("Model: " + modelType);
		// If sessionName isn't blank, it will end in a file separator
		String srcName = sessionName + "GamePlay" + File.separator + modelColor;
		System.err.println("Game source: " + srcName);
		// NB, 2018-09-27: force bPack false for the GamePlay source;
		//                 this source will only contain a String channel,
		//                 and as of right now CT *will* pack String
		//                 channels (but we don't want this channel packed)
		ctgamew = createCTwriter(srcName,false);
		if (!bSavePacketDataToCT) {
			System.err.println("Sensor data will not be written out");
		} else {
			// If sessionName isn't blank, it will end in a file separator
			srcName = sessionName + "Sensors" + File.separator + modelColor;
			System.err.println("Sensor data source: " + srcName);
			ctsensorw = createCTwriter(srcName, packMode);
		}

		//
		// Start UDPread
		//
		if (clientSocket != null) {
			System.err.println("Talk to UDP server at " + udpserverIP + ":" + udpserverPort);
			new UDPread(this, clientSocket, dt).start();
		} else {
			System.err.println("Capture UDP packets on port: " + portNum);
			new UDPread(this, portNum, dt).start();
		}
	}

	//--------------------------------------------------------------------------------------------------------
	// Create CTwriter object
	private CTwriter createCTwriter(String srcName, boolean bPack) {
		CTwriter ctw = null;
		try {
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
			} else if (writeMode == CTWriteMode.HTTP) {
				// Don't send username/pw in HTTP mode since they will be unencrypted
				CThttp cthttp = new CThttp(srcName,"http://"+serverHost);
				ctw = cthttp; // upcast to CTWriter
				System.err.println("    data will be written to HTTP server at " + serverHost);
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
				System.err.println("    data will be written to HTTPS server at " + serverHost);
			}
			ctw.setBlockMode(bPack,zipMode);
			ctw.autoSegment(blocksPerSegment);
			// ctw.autoFlush(xp.autoFlush);		// auto flush to zip once per interval (sec) of data
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		return ctw;
	}

	//---------------------------------------------------------------------------------------------
	//
	// Get minimum altitude for a given model; for example, balls should be set to half their height
	// (0.25 in CTrollaball) so that a model doesn't sink into the ground when resting on the
	// game table.

	public float getAltOffset() {
		float altOffset = 0.0f;
		if (modelType.equals("Ball")) {
			altOffset = 0.25f;
		} else if ( (modelType.equals("Biplane")) || (modelType.equals("Primplane")) ) {
			altOffset = 0.6f;
		}
		return altOffset;
	}

	//---------------------------------------------------------------------------------------------

	final protected static char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 3];
		int v;
		for ( int j = 0; j < bytes.length; j++ ) {
			v = bytes[j] & 0xFF;
			hexChars[j * 3] = hexArray[v >>> 4];
			hexChars[j * 3 + 1] = hexArray[v & 0x0F];
			hexChars[j * 3 + 2] = ' ';
		}
		return new String(hexChars);
	}
	
	//--------------------------------------------------------------------------------------------------------
	// note: multi-channel auto-flush with dt=spec can cause inconsistent times in zipfiles.
	//		 suggest manual-flush with check on t>tflush each thread.
	//		 this will also keep multi-channels semi-synced (eliminate drift) at expense of occasional jitter/gaps

	private class UDPread extends Thread {

	    private UDP2CT udp2ct = null;
		private double flushTime = 0;
		private long firstFlush = 0;
		private DatagramSocket ds=null; 				//listen for data here
		private MulticastSocket ms=null;
		private double dt=0;

		UDPread(UDP2CT udp2ctI, DatagramSocket clientSocketI, double dtI) {
		    udp2ct = udp2ctI;
			ds = clientSocketI;
			dt = dtI;
		}

		UDPread(UDP2CT udp2ctI, int portI, double dtI) {
            udp2ct = udp2ctI;
			dt = dtI;

			// open port for incoming UDP
			try {
				if(multiCast != null) {
					System.err.println("Multicast address: "+multiCast);
					ms = new MulticastSocket(portI);
					ms.joinGroup(InetAddress.getByName(multiCast));
				}
				else {
					ds = new DatagramSocket(portI);
				}
			} catch (Exception e) { e.printStackTrace(); }
		}
		
		public void run() {
			try {
				DatagramPacket dp=new DatagramPacket(new byte[65536],65536);
				double oldtime = 0;
				double time = 0;
				if(flushTime == 0) flushTime = System.currentTimeMillis();

				while (true) {	
					if(ms!=null) ms.receive(dp);			// multicast	
					else 		 ds.receive(dp);			// unicast

					int packetSize = dp.getLength();
					if (packetSize > 0) {
						if(dt==0) 				time = System.currentTimeMillis();
						else if(time==0) {
							if(firstFlush == 0) time = firstFlush = System.currentTimeMillis();
							else				time = firstFlush;
						}
						else 					time += dt;				// auto pace
						if(time < flushTime) {
							System.err.println("\n------------autoFlush skootch time: "+time+" -> "+flushTime);
							time = flushTime;		// no backwards-going times
						}
						
						if(time <= oldtime) 	time=oldtime+1;			// no dupes
						oldtime = time;

						if(debug) System.err.println("UDP2CT, new packet: bytes: "+packetSize+", t: "+time+", flushTime: "+flushTime);

						byte[] data=new byte[packetSize];		// truncate array to actual data got
						System.arraycopy(dp.getData(),dp.getOffset(),data,0,packetSize);

						try {
							ctgamew.setTime((long) time);
							if (bSavePacketDataToCT) {
								ctsensorw.setTime((long) time);
							}
							// Look at the first 5 bytes to determine what type of UDP packet this is
							String header = new String(Arrays.copyOf(data,5));
							UnityPlayer pp = null;
							switch (header) {
								case "DATA*":
									pp = new XPlanePacketParser3(udp2ct,ctsensorw,time,data,bSavePacketDataToCT,udp_debug);
									break;
								case "MOUSE":
									pp = new MouseParser(udp2ct,ctsensorw,time,data,bSavePacketDataToCT,udp_debug);
									break;
								default:
									System.err.println("\nUnknown UDP packet type, header = " + header);
									break;
							}
							if (pp != null) {
								String unityStr = pp.createUnityString();
								if (!unityStr.isEmpty()) {
									String chanName = "CTstates.json";    // write out JSON data
									ctgamew.putData(chanName, unityStr);
								}
							}
							if((time - flushTime) > autoFlushMillis) {
								if (debug || udp_debug) {
									System.err.println("---Flush at time " + time);
								} else {
									System.err.print(" F");
								}
								flushTime = time;
								ctgamew.flush();
								if (bSavePacketDataToCT) {
									ctsensorw.flush();
								}
							}
						} catch(Exception e) {
							e.printStackTrace();			// dont give up on putData exceptions
						}
					}
				}
			} catch (Exception e) { e.printStackTrace(); }
		}

	} // end private class UDPread

    //
    // Create the JSON String for participating in CTrollaball game
    //
	public String createUnityString(double timeI,float xposI,float altI,float yposI,float pitch_degI,float hding_degI,float roll_degI) {
		PlayerWorldState playerState = new PlayerWorldState(timeI,xposI,altI,yposI,pitch_degI,hding_degI,roll_degI,modelColor,modelType);
		Gson gson = new Gson();
		String unityStr = gson.toJson(playerState);

		// Here's the original (now outdated) CSV format
		/*
			unityStr =
				"#Live:" +
				String.format("%.5f", timeI) +
				":" +
				modelColor +
				"\n" +
				modelColor +
				";" +
				modelType +
				";1;(" +
				String.format("%.4f", xposI) +
				"," +
				String.format("%.4f", altI) +
				"," +
				String.format("%.4f", yposI) +
				");(" +
				String.format("%.4f", pitch_degI) +
				"," +
				String.format("%.4f", hding_degI) +
				"," +
				String.format("%.4f", roll_degI) +
				")\n";
		*/

		return unityStr;
	}

	//
	// Class to issue a UDP "heartbeat" message to a UDP server
	// The run method can be called periodically in order to send a keep-alive message to a UDP server
	//
	private class UDPHeartbeatTask extends TimerTask {

		private DatagramSocket clientSocket = null;
		private InetAddress heartbeatIP = null;
		private int heartbeatPort = 0;

		public UDPHeartbeatTask(DatagramSocket clientSocketI, InetAddress heartbeatIPI, int heartbeatPortI) {
			clientSocket = clientSocketI;
			heartbeatIP = heartbeatIPI;
			heartbeatPort = heartbeatPortI;
		}

		public void run() {
			byte[] sendData = "hello from UDP2CT".getBytes();
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, heartbeatIP, heartbeatPort);
			try {
				clientSocket.send(sendPacket);
			} catch (IOException e) {
				System.err.println("UDPHeartbeatTask.run(): error sending DatagramPacket:\n" + e);
			}
			System.err.println("---Heartbeat @ " + System.currentTimeMillis());
		}
	} // end private class UDPHeartbeatTask

} //end class UDP2CT
