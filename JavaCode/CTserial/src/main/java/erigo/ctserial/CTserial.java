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

package erigo.ctserial;

//
// CTserial
//
// Capture comma-separated strings of serial datat, parse the data
// and send to CloudTurbine.
//
// CTserial includes much of the code and structure from Matt Miller's (Cycronix) CTudp.
//
// Using jSerialComm cross-platform serial communications library.
//     http://fazecast.github.io/jSerialComm/
//     https://github.com/Fazecast/jSerialComm
//     https://www.youtube.com/watch?v=cw31L_OwX3A
//
// Copyright 2016 Erigo
// All Rights Reserved
//
//   Date      By	Description
// MM/DD/YYYY
// ----------  --	-----------
// 12/07/2016  JPW	Created.
// 12/16/2016  JPW  Added "simulate" mode
//

import java.io.InputStream;
import java.io.IOException;
import java.util.Scanner;
import org.apache.commons.cli.*;
import cycronix.ctlib.*;
import com.fazecast.jSerialComm.SerialPort;

public class CTserial {
	
	boolean zipMode = true;
	boolean debug = false;
	double autoFlushDefault = 1.;	  // default flush interval (sec)
	long autoFlushMillis;			  // flush interval (msec)
	double trimTime = 0.;			  // trimtime (sec)
	String srcName = new String("CTserial");
	CTwriter ctw;
	boolean bShutdown = false;
	boolean bShutdownDone = false;
	
	//
	// Main class
	//
	public static void main(String[] arg) {
		new CTserial(arg);
	}
	
	//
	// Constructor
	//
	public CTserial(String[] arg) {
		
		// Specify a shutdown hook to catch Ctrl+c
        Runtime.getRuntime().addShutdownHook(new Thread() {
        	@Override
            public void run() {
        		// Flag that it is time to shut down
            	bShutdown = true;
            	for (int i=0; i<10; ++i) {
            		if (bShutdownDone) {
            			// Main loop has shut down
            			break;
            		}
            		try {
            			Thread.sleep(1000);
            		} catch (Exception e) {
            			// Nothing to do
            		}
            	}
            }
        });
		
		String[] chanNames = null;
		double dt = 0.0;
		
		// Argument processing using Apache Commons CLI
		// 1. Setup command line options
		Options options = new Options();
		options.addOption("h", "help", false, "Print this message");
		options.addOption(Option.builder("s").argName("source name").hasArg().desc("name of source to write serial packets to").build());
		options.addOption(Option.builder("c").argName("channel name(s)").hasArg().desc("name of channel(s) to write data to (comma-separated list)").build());
		options.addOption(Option.builder("p").argName("serial port").hasArg().desc("port number to listen for serial packets on").build());
		options.addOption(Option.builder("d").argName("delta-time").hasArg().desc("fixed delta-time (msec) between frames (dt=0 for arrival-times)").build());
		options.addOption(Option.builder("f").argName("autoFlush").hasArg().desc("flush interval (sec) (amount of data per zipfile)").build());
		options.addOption(Option.builder("t").argName("trim-Time").hasArg().desc("trim (ring-buffer loop) time (sec) (trimTime=0 for indefinite)").build());
		options.addOption("b", "time_in_str", false, "the first entry in the received CSV string is the data time");
		options.addOption("x", "debug", false, "debug mode");
		options.addOption("z", "sim_mode", false, "turn on simulate mode (don't read serial port)");
        
		// 2. Parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {
			line = parser.parse( options, arg );
		} catch( ParseException exp ) {
			System.err.println("Command line argument parsing failed: " + exp.getMessage());
			// Signal the shutdown hook to just shut down
			bShutdownDone = true;
			return;
		}
		
		//
		// 3. Retrieve the command line values
		//
		
		if (line.hasOption("help")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(120);
			formatter.printHelp( "CTserial", options );
			// Signal the shutdown hook to just shut down
			bShutdownDone = true;
			return;
		}
		
		srcName = line.getOptionValue("s",srcName);
		
		String chanNameL = line.getOptionValue("c","serialchan");
		chanNames = chanNameL.split(","); 
		
		String portStr = line.getOptionValue("p", "COM1");
		
		String dtStr = line.getOptionValue("d","0");				
		dt = Double.parseDouble(dtStr);
		
		// Auto-flush time
		double autoFlush = Double.parseDouble(line.getOptionValue("f",""+autoFlushDefault));
		autoFlushMillis = (long)(autoFlush*1000.);
		
		trimTime = Double.parseDouble(line.getOptionValue("t","0"));
		
		boolean bFirstValIsTime = line.hasOption("time_in_str");
		// Can't have both bFirstValIsTime==true and dt!=0
		if ( bFirstValIsTime && (dt != 0) ) {
			System.err.println("Not able to use both \"time_in_str\" and \"delta-time\" options at the same time.");
			// Signal the shutdown hook to just shut down
			bShutdownDone = true;
			return;
		}
		
		boolean bSimulateMode = line.hasOption("sim_mode");
		if ( bSimulateMode && (dt == 0) ) {
			System.err.println("Must specify \"delta-time\" when in simulate mode.");
			// Signal the shutdown hook to just shut down
			bShutdownDone = true;
			return;
		}
		
		debug = line.hasOption("debug");
		
		// setup CTwriter
		try {
			ctw = new CTwriter(srcName,trimTime);	
			ctw.setZipMode(zipMode);
			CTinfo.setDebug(debug);
		} catch(Exception e) {
			System.err.println("Caught exception trying to setup CTwriter:");
			e.printStackTrace();
			// Signal the shutdown hook to just shut down
			bShutdownDone = true;
			return;
		}

		System.err.print("Source name: " + srcName + "\nSerial port: " + portStr + "\nChannels: ");
		for(int i=0; i<(chanNames.length-1); ++i) {
			System.err.print(chanNames[i] + ",");
		}
		System.err.println(chanNames[ chanNames.length-1 ]);
		if (bSimulateMode) {
			System.err.println("IN SIMULATE MODE");
		}
		
		try {
			new SerialRead(portStr, chanNames, dt, bFirstValIsTime,bSimulateMode).start();
		} catch (Exception e) {
			System.err.println("Caught exception:\n" + e);
			return;
		}
	}
	
	//--------------------------------------------------------------------------------------------------------
	// note: multi-channel auto-flush with dt=spec can cause inconsistent times in zipfiles.
	//		 suggest manual-flush with check on t>tflush each thread.
	//		 this will also keep multi-channels semi-synced (eliminate drift) at expense of occasional jitter/gaps
	
	double flushTime = 0; // time of last flush
	long firstFlush = 0; // sync multi-channels to first at start
	private class SerialRead extends Thread {
		
		private String portStr;
		private String[] chanNames;
		private double dt = 0;
		private boolean bFirstValIsTime = false;
		private SerialPort serPort = null;
		private boolean bSimulateMode = false;
		
		SerialRead(String portStrI, String[] chanNamesI, double dtI, boolean bFirstValIsTimeI, boolean bSimulateModeI) throws Exception {
			portStr = portStrI;
			chanNames = chanNamesI;
			dt = dtI;
			bFirstValIsTime = bFirstValIsTimeI;
			bSimulateMode = bSimulateModeI;
			
			// Open serial port (if not in simulate mode)
			if (!bSimulateMode) {
				serPort = SerialPort.getCommPort(portStr);
				serPort.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);
				if(!serPort.openPort()) {
					throw new Exception("Serial port \"" + portStr + "\" could not be opened.");
				}
			}
		}
		
		public void run() {
			InputStream inputStream = null;
			Scanner scanner = null;
			if (!bSimulateMode) {
				inputStream = serPort.getInputStream();
				scanner = new Scanner(inputStream);
			}
			
			try {
				double oldtime = 0;
				double time = 0;
				if(flushTime == 0) flushTime = System.currentTimeMillis();
				
				int expectedNumCSVEntries = chanNames.length;
				if (bFirstValIsTime) {
					// Add one more for the time given as the first entry
					expectedNumCSVEntries = chanNames.length + 1;
				}
				
				// Variables for simulate mode
				int loopIdx = 0;
				int loopIdxDontReset = 0;
				
				while(!bShutdown && ( bSimulateMode || scanner.hasNextLine() ) ) {
					if ( bSimulateMode && ((loopIdx%50) == 0) ) {
						// To make "jumps" in the simulated data
						loopIdx = 0;
					}
					String line = null;
					if (!bSimulateMode) {
						line = scanner.nextLine();
					} else {
						int new_time = (int)(loopIdxDontReset*dt);
						line = new String("");
						if (bFirstValIsTime) {
						    line = new String(Integer.toString(new_time));
						}
						for (int i=0; i<chanNames.length; ++i) {
							// Include a comma before next entry?
							if ( (i>0) || ( (i==0) && (bFirstValIsTime) ) ) {
								line = line + ",";
							}
							// Have different data for even and odd channels
							if ((i%2)==0) {
								// even channel
								line = line + String.format("%7.3f", (i+1)*Math.sin((double)loopIdx));
							} else {
								// odd channel
								line = line + String.format("%6.3f", 0.123*(i+1)*loopIdx);
							}
						}
					}
					if ( (line == null) || (line.trim().isEmpty()) ) {
						continue;
					}
					// Make sure this new line has the correct number of entries
					String[] chanDataStr = line.split(",");
					
					if (chanDataStr.length != expectedNumCSVEntries) {
						if (debug) {
							System.err.println("Received string with incorrect number of entries:\n" + line);
						}
					} else {
						synchronized(ctw) {
							if (bFirstValIsTime) {
								// Use the first value in the serial string as the time
								try {
									time = Double.parseDouble(chanDataStr[0]);
								} catch (NumberFormatException nfe) {
									System.err.println("Got bogus time in serial string: " + chanDataStr[0] + "; skip line");
									continue;
								}
							} else if (dt==0) {
								time = System.currentTimeMillis();
							} else if (time==0) {
								if(firstFlush == 0) time = firstFlush = System.currentTimeMillis();
								else				time = firstFlush;
							} else {
								time += dt;	// automatically paced data, just add dt with each loop
							}
						}
						if(time < flushTime) {
							System.err.println("------------autoFlush skootch time: " + time + " -> " + flushTime);
							time = flushTime; // no backwards-going times
						}
						
						if (time <= oldtime) {
							time=oldtime+1; // no dupes
						}
						oldtime = time;
						
						//if(debug) {
							System.err.println("CTserial t: " + time + ", last flush time: " + flushTime + ", data string: " + line);
						//}
						
						try {
							synchronized(ctw) {
								ctw.setTime((long)time);
								for (int i=0; i<chanNames.length; ++i) {
									// If this entry is numeric, putData as a number
									String dataStr = chanDataStr[i];
									double dataNum = 0.0;
									try {
										dataNum = Double.parseDouble(dataStr);
										ctw.putData(chanNames[i], dataNum);
									} catch (NumberFormatException nfe) {
										// Put data as String
										ctw.putData(chanNames[i], dataStr);
									}
								}
								if ((time - flushTime) > autoFlushMillis) {
									// time to flush
									System.err.println("---CTserial flush: t: " + String.format("%.3f",time/1000.0));
									flushTime = time;
									ctw.flush();
								}
							}
						} catch(Exception e) {
							e.printStackTrace(); // don't give up on putData exceptions
						}
						// For simulated data
						++loopIdx;
						++loopIdxDontReset;
						if (bSimulateMode) {
							Thread.sleep((int)dt);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (!bSimulateMode) {
				// Close input streams
				try {
					System.err.println("Close InputStream...");
					inputStream.close();
				} catch (IOException e) {
					System.err.println("Caught exception closing InputStream");
					e.printStackTrace();
				}
				System.err.println("Close Scanner...");
				scanner.close();
			}
			
			bShutdownDone = true;
		}
	}
	
} //end class CTserial
