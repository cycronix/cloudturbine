package cycronix.ctudp;

//---------------------------------------------------------------------------------	
//CTudp:  capture UDP packets to CT files
//Matt Miller, Cycronix

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import org.apache.commons.cli.*;

import cycronix.ctlib.*;

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

public class CTudp {
	
	// params common to all sockets:
	String multiCast=null;			// multicast address
	boolean zipMode=true;
	boolean debug=false;
	double autoFlush=1.;			// flush interval (sec)	
	long autoFlushMillis;			// flush interval (msec)
	double trimTime=0.;				// trimtime (sec)	
	String srcName 	= 	new String("CTudp");
	static int numSock = 0;
	CTwriter ctw;
	double exceptionVal = 0.0;       // When splitting up a given CSV string and an expected double val is bogus, use this value in its place
	
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

		//
		// Argument processing using Apache Commons CLI
		//
		// 1. Setup command line options
		Options options = new Options();
		options.addOption("h", "help", false, "Print this message.");
		options.addOption(Option.builder("s").argName("source name").hasArg().desc("Name of source to write packets to; default = \"" + srcName + "\".").build());
		options.addOption(Option.builder("c").argName("channel name").hasArg().desc("Name of channel to write packets to; default = \"" + defaultChanName + "\".").build());
		options.addOption(Option.builder("csplit").argName("channel name(s)").hasArg().desc("Comma-separated list of channel names; split an incoming CSV string into a series of channels with the given names; supported channel name suffixes and their associated data types: .txt (string), .csv or no suffix (numeric), .f64 (64-bit floating point).").build());
		options.addOption(Option.builder("e").argName("exception val").hasArg().desc("If a CSV string is being parsed (using the -csplit option) and there is an error saving one of the string components as a 64-bit floating point value, use this exception value in its place; default = " + Double.toString(exceptionVal) + ".").build());
		options.addOption(Option.builder("m").argName("multicast address").hasArg().desc("Multicast UDP address (224.0.0.1 to 239.255.255.255).").build());
		options.addOption(Option.builder("p").argName("UDP port").hasArg().desc("Port number to listen for UDP packets on; default = " + Integer.toString(defaultPort) + ".").build());
		options.addOption(Option.builder("d").argName("delta-Time").hasArg().desc("Fixed delta-time (msec) between frames; specify 0 to use arrival-times; default = " + Double.toString(defaultDT) + ".").build());
		options.addOption(Option.builder("f").argName("autoFlush").hasArg().desc("Flush interval (sec); amount of data per zipfile; default = " + Double.toString(autoFlush) + ".").build());
		options.addOption(Option.builder("t").argName("trim-Time").hasArg().desc("Trim (ring-buffer loop) time (sec); specify 0 for indefinite; default = " + Double.toString(trimTime) + ".").build());
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
			// Make sure that the channel names either have no suffix or will end in .txt, .csv, .f64
			for(int i=0; i<csvChanNames.length; ++i) {
				int dotIdx = csvChanNames[i].lastIndexOf('.');
				if ( (dotIdx > -1) && (!csvChanNames[i].endsWith(".txt")) && (!csvChanNames[i].endsWith(".csv")) && (!csvChanNames[i].endsWith(".f64")) ) {
					System.err.println("Error: illegal channel name specified in the \"-csplit\" list: " + csvChanNames[i]);
					System.err.println("\tAccepted channel names: channels with no suffix or with .txt, .csv or .f64 suffixes.");
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

		debug = line.hasOption("debug");

		if(numSock != numChan) {
			System.err.println("Error:  must specify same number of channels and ports!");
			System.exit(0);
		}
		if(multiCast!=null && numSock > 1) {
			System.err.println("Error:  can only have one multicast socket!");
			System.exit(0);
		}
		if(numSock == 0) numSock=1;			// use defaults
		
		System.err.println("Source name: " + srcName);
		for(int i=0; i<numSock; i++) {
			System.err.println("Channel["+i+"]: " + chanName[i]);
			System.err.println("UDPport["+i+"]: " + ssNum[i]);
		}
		if (csvChanNames != null) {
			System.err.println("\nIncoming csv strings will be split into the following channels:");
			for(int i=0; i<(csvChanNames.length-1); ++i) {
				System.err.print(csvChanNames[i] + ",");
			}
			System.err.println(csvChanNames[ csvChanNames.length-1 ]);
		}
		
		// setup CTwriter
		try {
			ctw = new CTwriter(srcName,trimTime);	
			ctw.setZipMode(zipMode);
			CTinfo.setDebug(debug);
			autoFlushMillis = (long)(autoFlush*1000.);
//			ctw.autoFlush(autoFlush);		// auto flush to zip once per interval (sec) of data
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

		// start a thread for each port
		for(int i=0; i<numSock; i++) {
			System.err.println("start thread for port: "+ssNum[i]+", chan: "+chanName[i]);
			new UDPread(ssNum[i], chanName[i], csvChanNames, dt[i]).start();
		}
	}
	
	//--------------------------------------------------------------------------------------------------------
	// note: multi-channel auto-flush with dt=spec can cause inconsistent times in zipfiles.
	//		 suggest manual-flush with check on t>tflush each thread.
	//		 this will also keep multi-channels semi-synced (eliminate drift) at expense of occasional jitter/gaps
	
	double flushTime = 0;
	long firstFlush = 0;								// sync multi-channels to first at start
	private class UDPread extends Thread {
		
		private int port = 0;
		private String chanName;
		private String[] csvChanNames = null;
		private DatagramSocket ds=null; 				//listen for data here
		private MulticastSocket ms=null;
		private double dt=0;
		
		UDPread(int iport, String ichanName, String[] csvChanNamesI, double idt) {
			port = iport;
			chanName = ichanName;
			csvChanNames = csvChanNamesI;
			dt = idt;
			
			try {				//open port for incoming UDP
				if(multiCast != null) {
					System.err.println("Multicast address: "+multiCast);
					ms = new MulticastSocket(port);
					ms.joinGroup(InetAddress.getByName(multiCast));
				}
				else {
					ds = new DatagramSocket(port); 
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
								//
								// Put data for the default ("-c") channel
								// Data is always saved as a byte array
								//
								ctw.setTime((long)time);
								ctw.putData(chanName, data);
								//
								// If requested, split the incoming csv string up and save each channel
								// (this is the "-csplit" option)
								//
								if (csvChanNames != null) {
									String csvStr = new String(data);
									String[] chanDataStr = csvStr.split(",");
									if (chanDataStr.length != csvChanNames.length) {
										System.err.println("Received string with incorrect number of csv entries (" + chanDataStr.length + "), was expecting " + csvChanNames.length);
									} else {
										for (int i=0; i<csvChanNames.length; ++i) {
											// When we parsed the command line args, we made sure that the channel names
											// will either have no suffix or will end in .txt, .csv, .f64
											// - if chan name ends in .f64, put data as double
											// - if chan name doesn't have a suffix or it ends in .txt or it ends in .csv, put data as string
											String dataStr = chanDataStr[i];
											if (!csvChanNames[i].endsWith(".f64")) {
												// Put data as String, let CT sort out the data type depending on what the channel name extension is
												ctw.putData(csvChanNames[i], dataStr);
											} else {
												// Put data as double
												try {
													double dataNum = Double.parseDouble(dataStr);
													ctw.putData(csvChanNames[i], dataNum);
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
	}
} //end class CTudp
