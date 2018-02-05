/*
Copyright 2017 Cycronix

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

CTudp

Capture UDP packets to CT files.  Will optionally split up and
save the constituent channels from CSV strings.

Matt Miller, Cycronix
John Wilson, Erigo Technologies

 */

package cycronix.ctudp;

import java.io.IOException;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.commons.cli.*;
import cycronix.ctlib.*;

public class CTudp {
	
	// params common to all sockets:
	String multiCast=null;			// multicast address
	boolean zipMode=true;           // ZIP data?
	boolean packMode=false;         // turn on pack mode?
	boolean debug=false;            // turn on debug?
	double autoFlush=1.;			// flush interval (sec)	
	long autoFlushMillis;			// flush interval (msec)
	double trimTime=0.;				// trimtime (sec)
	long blocksPerSegment = 0;      // number of blocks per segment; 0 = no segment layer
	String srcName = new String("CTudp");
	static int numSock = 0;
	CTwriter ctw;
	double exceptionVal = 0.0;      // When splitting up a given CSV string and an expected double val is bogus, use this value in its place
	
	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] arg) {
		new CTudp(arg);
	}
	
	//--------------------------------------------------------------------------------------------------------
	public CTudp(String[] arg) {
		String[] chanName = new String[32];
		String defaultChanName = "udpchan";
		String[] csvChanNames = null;
		int ssNum[] = new int[32];
		int defaultPort = 4445;
		double dt[] = new double[32];
		double defaultDT = 0.0;
		int numChan = 0;

		// For communicating with UDP server; we send a "keep alive" heartbeat message to this server
		// and will receive UDP packets from this server
		DatagramSocket clientSocket = null;  // This socket will be shared by UDPread and UDPHeartbeatTask classes
		InetAddress udpserverIP = null;
		int udpserverPort = -1;
		int heartbeatPeriod_msec = -1;
		boolean bCSVTest = false; // If the "-csvtest" option has been specified along with the "-udpserver" option, then the heartbeat message itself is a CSV string that can be used for testing CTudp in loopback mode.

		// Optional local UDP server that can be started to serve test data
		int testserverPort = -1;
		int testserverPeriod_msec = -1;

		//
		// Argument processing using Apache Commons CLI
		//
		// 1. Setup command line options
		Options options = new Options();
		options.addOption("h", "help", false, "Print this message.");
		options.addOption("pack", false, "Blocks of data should be packed?  default = " + Boolean.toString(packMode) + ".");
		options.addOption(Option.builder("s").argName("source name").hasArg().desc("Name of source to write packets to; default = \"" + srcName + "\".").build());
		options.addOption(Option.builder("c").argName("channel name").hasArg().desc("Name of channel to write packets to; default = \"" + defaultChanName + "\".").build());
		options.addOption(Option.builder("csplit").argName("channel name(s)").hasArg().desc("Comma-separated list of channel names; split an incoming CSV string into a series of channels with the given names; supported channel name suffixes and their associated data types: .txt (string), .csv or no suffix (numeric), .f32 (32-bit floating point), .f64 (64-bit floating point).").build());
		options.addOption(Option.builder("e").argName("exception val").hasArg().desc("If a CSV string is being parsed (using the -csplit option) and there is an error saving a string component as a floating point value, use this \"exception value\" in its place; default = " + Double.toString(exceptionVal) + ".").build());
		options.addOption(Option.builder("m").argName("multicast address").hasArg().desc("Multicast UDP address (224.0.0.1 to 239.255.255.255).").build());
		options.addOption(Option.builder("p").argName("UDP port").hasArg().desc("Port number to listen for UDP packets on; default = " + Integer.toString(defaultPort) + ".").build());
		options.addOption(Option.builder("d").argName("delta-Time").hasArg().desc("Fixed delta-time (msec) between frames; specify 0 to use arrival-times; default = " + Double.toString(defaultDT) + ".").build());
		options.addOption(Option.builder("f").argName("autoFlush").hasArg().desc("Flush interval (sec); amount of data per zipfile; default = " + Double.toString(autoFlush) + ".").build());
		options.addOption(Option.builder("t").argName("trim-Time").hasArg().desc("Trim (ring-buffer loop) time (sec); specify 0 for indefinite; default = " + Double.toString(trimTime) + ".").build());
		options.addOption(Option.builder("udpserver").argName("IP,port,period_msec").hasArg().desc("Talk to a UDP server; send a periodic keep-alive message to the given IP:port at the specified period and receive packets from this server; not to be used with the \"-p\" option.").build());
		options.addOption(Option.builder("testserver").argName("port,period_msec").hasArg().desc("Start a UDP server on the local machine to serve CSV strings at the specified period with the format specified by the \"-csplit\" option (if no \"-csplit\" option has been specified, a simple text message is output). The test server waits for a message from the client before starting packet flow. This feature can be used along with the \"-udpserver\" option for local/loopback testing (NOTE: make sure the server ports match).").build());
		options.addOption(Option.builder("bps").argName("blocks_per_seg").hasArg().desc("Number of blocks per segment; specify 0 for no segments; default = " + Long.toString(blocksPerSegment) + ".").build());
		options.addOption("x", "debug", false, "Debug mode.");

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
			formatter.printHelp( "CTudp", options );
			return;
		}

		srcName = line.getOptionValue("s",srcName);

		String chanNameL = line.getOptionValue("c",defaultChanName);
		chanName = chanNameL.split(",");
		numChan = chanName.length;

		if (line.hasOption("csplit")) {
			chanNameL = line.getOptionValue("csplit");
			csvChanNames = chanNameL.split(",");
			if (numChan > 1) {
				System.err.println("Error: don't use the \"-csplit\" option when receiving packets from multiple UDP ports.");
				System.exit(0);
			}
			// Make sure that the channel names either have no suffix or will end in .txt, .csv, .f32, .f64
			for(int i=0; i<csvChanNames.length; ++i) {
				int dotIdx = csvChanNames[i].lastIndexOf('.');
				if ( (dotIdx > -1) && (!csvChanNames[i].endsWith(".txt")) && (!csvChanNames[i].endsWith(".csv")) && (!csvChanNames[i].endsWith(".f32")) && (!csvChanNames[i].endsWith(".f64")) ) {
					System.err.println("Error: illegal channel name specified in the \"-csplit\" list: " + csvChanNames[i]);
					System.err.println("\tAccepted channel names: channels with no suffix or with .txt, .csv, .f32 or .f64 suffixes.");
					System.exit(0);
				}
			}
		}

		String exceptionValStr = line.getOptionValue("e",Double.toString(exceptionVal));
		try {
			exceptionVal = Double.parseDouble(exceptionValStr);
		} catch (NumberFormatException nfe) {
			System.err.println("Error parsing the given exception value (\"-e\" flag).");
			System.exit(0);
		}

		multiCast = line.getOptionValue("m",multiCast);

		String nss=line.getOptionValue("p", Integer.toString(defaultPort));
		String[] ssnums = nss.split(",");
		numSock=ssnums.length;
		for(int i=0; i<numSock; i++) ssNum[i]=Integer.parseInt(ssnums[i]);

		String sdt=line.getOptionValue("d",Double.toString(defaultDT));
		String[] ssdt = sdt.split(",");
		for(int i=0; i<ssdt.length; i++) dt[i]=Double.parseDouble(ssdt[i]);

		autoFlush = Double.parseDouble(line.getOptionValue("f",""+autoFlush));
		
		trimTime = Double.parseDouble(line.getOptionValue("t",Double.toString(trimTime)));

		blocksPerSegment = Long.parseLong(line.getOptionValue("bps",Long.toString(blocksPerSegment)));

		if (line.hasOption("pack")) {
			packMode = true;
		}

		debug = line.hasOption("debug");

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
		}

		if (line.hasOption("testserver")) {
			String testserverStr = line.getOptionValue("testserver");
			// Parse the port,period_msec from this string
			String[] testserverConfigCSV = testserverStr.split(",");
			if (testserverConfigCSV.length != 2) {
				System.err.println("Error: the \"-testserver\" argument must contain 2 parameters: port,period_msec");
				System.exit(0);
			}
			try {
				testserverPort = Integer.parseInt(testserverConfigCSV[0]);
				if (testserverPort <= 0) {
					throw new Exception("Invalid port number");
				}
			} catch (Exception e) {
				System.err.println("Error: the \"-testserver\" port must be an integer greater than 0.");
				System.exit(0);
			}
			try {
				testserverPeriod_msec = Integer.parseInt(testserverConfigCSV[1]);
				if (testserverPeriod_msec <= 0) {
					throw new Exception("Invalid period");
				}
			} catch (Exception e) {
				System.err.println("Error: the \"-testserver\" period_msec must be an integer greater than 0.");
				System.exit(0);
			}
		}

		if(numSock != numChan) {
			System.err.println("Error:  must specify same number of channels and ports!");
			System.exit(0);
		}
		if(multiCast!=null && numSock > 1) {
			System.err.println("Error: can only have one multicast socket!");
			System.exit(0);
		}
		if(numSock == 0) numSock=1;			// use defaults
		
		System.err.println("Source name: " + srcName);
		if (udpserverIP == null) {
			for (int i = 0; i < numSock; i++) {
				System.err.println("Channel[" + i + "]: " + chanName[i]);
				System.err.println("UDPport[" + i + "]: " + ssNum[i]);
			}
		}
		if (csvChanNames != null) {
			System.err.println("\nIncoming csv strings will be split into the following channels:");
			for(int i=0; i<(csvChanNames.length-1); ++i) {
				System.err.print(csvChanNames[i] + ",");
			}
			System.err.println(csvChanNames[ csvChanNames.length-1 ]);
		}

		//
		// If user has requested it, start the test UDP server
		//
		if (testserverPort > -1) {
			UDPserver svr = null;
			try {
				svr = new UDPserver(testserverPort,testserverPeriod_msec,csvChanNames);
			} catch (SocketException se) {
				System.err.println("Error: caught exception trying to start test server:\n" + se);
				System.exit(0);
			}
			svr.start();
		}

		//
		// If user requested it, initialize communication with the UDP server
		//
		if (udpserverIP != null) {
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

		//
		// setup CTwriter
		//
		try {
			ctw = new CTwriter(srcName,trimTime);
			ctw.setBlockMode(packMode,zipMode);
			CTinfo.setDebug(debug);
			ctw.autoSegment(blocksPerSegment);
			autoFlushMillis = (long)(autoFlush*1000.);
			// ctw.autoFlush(autoFlush);		// auto flush to zip once per interval (sec) of data
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

		//
		// start a thread for each port
		// if we are talking to a UDP server, there is only 1 instance of UDPread
		//
		if (clientSocket != null) {
			System.err.println("Talk to UDP server at " + udpserverIP + ":" + udpserverPort);
			new UDPread(clientSocket, chanName[0], csvChanNames, dt[0]).start();
		} else {
			for (int i = 0; i < numSock; i++) {
				System.err.println("start thread for port: " + ssNum[i] + ", chan: " + chanName[i]);
				new UDPread(ssNum[i], chanName[i], csvChanNames, dt[i]).start();
			}
		}
	}
	
	//--------------------------------------------------------------------------------------------------------
	// note: multi-channel auto-flush with dt=spec can cause inconsistent times in zipfiles.
	//		 suggest manual-flush with check on t>tflush each thread.
	//		 this will also keep multi-channels semi-synced (eliminate drift) at expense of occasional jitter/gaps
	
	double flushTime = 0;
	long firstFlush = 0;								// sync multi-channels to first at start
	private class UDPread extends Thread {

		private String chanName;
		private String[] csvChanNames = null;
		private DatagramSocket ds=null; 				//listen for data here
		private MulticastSocket ms=null;
		private double dt=0;

		UDPread(DatagramSocket clientSocketI, String chanNameI, String[] csvChanNamesI, double dtI) {
			ds = clientSocketI;
			chanName = chanNameI;
			csvChanNames = csvChanNamesI;
			dt = dtI;
		}

		UDPread(int portI, String chanNameI, String[] csvChanNamesI, double dtI) {
			chanName = chanNameI;
			csvChanNames = csvChanNamesI;
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
						synchronized(ctw) {
							if(dt==0) 				time = System.currentTimeMillis();
							else if(time==0) {
								if(firstFlush == 0) time = firstFlush = System.currentTimeMillis();
								else				time = firstFlush;
							}
							else 					time += dt;				// auto pace
						}
						if(time < flushTime) {
							System.err.println("------------autoFlush skootch chan: "+chanName+", time: "+time+" -> "+flushTime);
							time = flushTime;		// no backwards-going times
						}
						
						if(time <= oldtime) 	time=oldtime+1;			// no dupes
						oldtime = time;

						if(debug) System.err.println("CTudp chan: "+chanName+", bytes: "+packetSize+", t: "+time+", flushTime: "+flushTime);

						byte[] data=new byte[packetSize];		// truncate array to actual data got
						System.arraycopy(dp.getData(),dp.getOffset(),data,0,packetSize);

						try {
							synchronized(ctw) {
								ctw.setTime((long) time);
								if (csvChanNames == null) {
									// Put data for the default ("-c") channel
									// This data is saved as byte array, which doesn't get packed
									// Only do this if we aren't splitting up/saving the individual CSV components
									ctw.putData(chanName, data);
								} else {
									// Split the incoming csv string up and save each channel
									// (this is the "-csplit" option)
									String csvStr = new String(data);
									String[] chanDataStr = csvStr.split(",");
									if (chanDataStr.length != csvChanNames.length) {
										System.err.println("Received string with incorrect number of csv entries (" + chanDataStr.length + "), was expecting " + csvChanNames.length);
									} else {
										for (int i=0; i<csvChanNames.length; ++i) {
											// When we parsed the command line args, we made sure that the channel names
											// will either have no suffix or will end in .txt, .csv, .f32, .f64
											// - if chan name ends in .f64, put data as double
											// - if chan name ends in .f32, put data as float
											// - if chan name doesn't have a suffix or it ends in .txt or it ends in .csv, put data as string
											String dataStr = chanDataStr[i];
											if ( (!csvChanNames[i].endsWith(".f64")) && (!csvChanNames[i].endsWith(".f32")) ) {
												// Put data as String, let CT sort out the data type depending on what the channel name extension is
												ctw.putData(csvChanNames[i], dataStr);
											} else if (csvChanNames[i].endsWith(".f32")) {
												// Put data as float
												try {
													float dataNumF = Float.parseFloat(dataStr);
													ctw.putData(csvChanNames[i], dataNumF);
												} catch (NumberFormatException nfe) {
													// Error parsing the data as float, put the default exceptionVal instead
													ctw.putData(csvChanNames[i], (float)exceptionVal);
												}
											} else if (csvChanNames[i].endsWith(".f64")) {
												// Put data as double
												try {
													double dataNumD = Double.parseDouble(dataStr);
													ctw.putData(csvChanNames[i], dataNumD);
												} catch (NumberFormatException nfe) {
													// Error parsing the data as double, put the default exceptionVal instead
													ctw.putData(csvChanNames[i], exceptionVal);
												}
											}
										}
									}
								}
								// long thisTime = System.currentTimeMillis();
								if((time - flushTime) > autoFlushMillis) {
									System.err.println("---CTudp flush: "+chanName+", t: "+time);
									flushTime = time;
									ctw.flush();
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
			byte[] sendData = "hello from CTudp".getBytes();
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, heartbeatIP, heartbeatPort);
			try {
				clientSocket.send(sendPacket);
			} catch (IOException e) {
				System.err.println("UDPHeartbeatTask.run(): error sending DatagramPacket:\n" + e);
			}
			System.err.println("---Heartbeat @ " + System.currentTimeMillis());
		}
	} // end private class UDPHeartbeatTask

	//
	// Receive a client connection and then start sending data to that client
	//
	// The current implementation supports just one client connection.  This could be extended
	// to support multiple clients without too much work, but note the following:
	// - each client InetAddress and port will need to be saved
	// - need to support multiple clients from the same InetAddress (as long as the ports are different);
	//      to implement this, use something like Google's Guava Multimap
	//      https://github.com/google/guava/wiki/NewCollectionTypesExplained#multimap
	//      For example, use the ArrayListMultimap as demonstrated here:
	//      http://tomjefferys.blogspot.com/2011/09/multimaps-google-guava.html
	// - since clients occasionally provide a heartbeat "keep-alive" message, make sure
	//      to not add multiple instances of the same InetAddress and port
	// - the DatagramSocket.receive() and DatagramSocket.send() calls will need to be in separate
	//      threads; from what I have Googled on the subject, this should be OK
	//
	private class UDPserver extends Thread {

		private DatagramSocket serverSocket = null;
		private int port = -1;
		private int period_msec = 0;
		private int desired_period_msec = 0;
		private byte[] receiveData = new byte[1024];
		private byte[] sendData = new byte[1024];
		private String[] csvChanNames = null;
		private int numChans = 0;

		public UDPserver(int portI, int period_msecI, String[] csvChanNamesI) throws SocketException {
			port = portI;
			period_msec = period_msecI;
			desired_period_msec = period_msecI;
			csvChanNames = csvChanNamesI;
			if (csvChanNames != null) {
				numChans = csvChanNames.length;
			}
			serverSocket = new DatagramSocket(port);
			System.err.println("Test UDP server started at port " + port);
		}

		public void run() {
			//
			// Wait for a client connection
			//
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			try {
				serverSocket.receive(receivePacket);
			} catch (IOException e) {
				System.err.println("Test server caught exception waiting for client to connect to the test server:\n" + e);
				System.err.println("EXITING test server.");
				return;
			}
			// We ignore the incoming data (it isn't used for anything)
			// String sentence = new String( receivePacket.getData());
			InetAddress clientIPAddress = receivePacket.getAddress();
			int clientPort = receivePacket.getPort();

			//
			// Now that we have a client connection, send them UDP packets at a periodic rate
			//
			long prev_loop_end_time = -1;  // used for adjusting the sleep period to maintain the user's desired data period
			while (true) {
				// Generate the new message
				String msg = "message from UDP server";
				if (numChans > 0) {
					// Generate a CSV string, following the format of the given csvChanNames
					// If there is a channel named "time.txt", save ISO-formatted date/time string in it
					String datetimestr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
					// All numeric data channels will contain random data
					// For the first numeric channel: enter a number which goes from 0-60 with occasional "bogus" field
					double dataVal = (double) LocalDateTime.now().getSecond() + Math.random();
					String dataValStr = String.format("%.3f", dataVal);
					// Occasionally put in a bogus string for first numeric channel to test how CTudp parses it
					if ((LocalDateTime.now().getSecond() == 10) || (LocalDateTime.now().getSecond() == 40)) {
						dataValStr = "bogus";
					}
					boolean bUsedDataValStr = false;
					StringBuffer msgBuf = new StringBuffer();
					for (int i = 0; i < numChans; ++i) {
						if (i > 0) {
							msgBuf.append(",");
						}
						if (csvChanNames[i].equals("time.txt")) {
							// Add ISO date/time string
							msgBuf.append(datetimestr);
						} else if (csvChanNames[i].endsWith(".txt")) {
							// Add random text string
							msgBuf.append(UUID.randomUUID().toString());
						} else {
							// Add numeric value
							if (!bUsedDataValStr) {
								// The first numeric channel will contain our 0-60 value
								msgBuf.append(dataValStr);
								bUsedDataValStr = true;
							} else {
								// The channel value is equal to its integer order in the channel list plus some random noise
								msgBuf.append((float)(i+1) + (float) Math.random() * 0.5);
							}
						}
					}
					msg = msgBuf.toString();
				}
				byte[] sendData = msg.getBytes();

				//
				// Send the message out to the connected client
				//
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIPAddress, clientPort);
				try {
					serverSocket.send(sendPacket);
				} catch (IOException e) {
					System.err.println("Test server caught exception trying to send data to UDP client:\n" + e);
				}

				//
				// Sleep for the needed period
				//
				try {
					// System.err.println("period_msec = " + period_msec);
					Thread.sleep(period_msec);
				} catch (InterruptedException e) {
					// Nothing to do
				}

				// Adjust the sleep time to keep the loop period close to the user's desired data period
				if (prev_loop_end_time == -1) {
					prev_loop_end_time = System.currentTimeMillis();
					// prev_loop_end_time = -1;  // uncomment this to never adjust the sleep time
				} else {
					long current_time = System.currentTimeMillis();
					long loop_DT = current_time - prev_loop_end_time;
					if (loop_DT > (desired_period_msec+2)) {
						// period_msec is too high, reduce it but don't go lower than 50% of the original desired value
						period_msec = period_msec - 1;
						if (period_msec < (int)(desired_period_msec/2.0)) {
							period_msec = (int)(desired_period_msec/2.0);
						}
					} else if (loop_DT < (desired_period_msec-2)) {
						// period_msec is too low, increase it, but don't go higher than 2 x the original desired value
						period_msec = period_msec + 1;
						if (period_msec > (int)(desired_period_msec*2.0)) {
							period_msec = (int)(desired_period_msec*2.0);
						}
					}
					prev_loop_end_time = current_time;
				}
			}
		}

	} // end private class UDPserver

} //end class CTudp
