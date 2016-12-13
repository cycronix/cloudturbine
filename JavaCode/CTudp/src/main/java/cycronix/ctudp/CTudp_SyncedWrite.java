package cycronix.ctudp;

//---------------------------------------------------------------------------------	
//CTudp:  capture UDP packets to CT files
//Matt Miller, Cycronix

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;

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

public class CTudp_SyncedWrite {
	
	// params common to all sockets:
	String multiCast=null;			// multicast address
	boolean zipMode=true;
	boolean debug=false;
	double autoFlush=1.;			// flush interval (sec)				
	double trimTime;				// trimtime (sec)	
	String srcName 	= 	new String("CTudp");
	ByteArrayOutputStream[] UDPbuf;
	static int numSock = 0;

	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] arg) {
		new CTudp_SyncedWrite(arg);
	}
	
	//--------------------------------------------------------------------------------------------------------
	public CTudp_SyncedWrite(String[] arg) {
		String[] 	chanName	=	new String[32];
		int			ssNum[]		=	new int[32];
		int	dt = 0;
		int numChan = 0;
		
		try {
	//	    System.err.println("");
			ArgHandler ah=new ArgHandler(arg);
			if (ah.checkFlag('h') || arg.length==0) {
				throw new Exception("show usage");
			}
			if (ah.checkFlag('s')) {
				String nameL = ah.getOption('s');
				if (nameL != null) srcName = nameL;
			}
			if (ah.checkFlag('c')) {
				String chanNameL = ah.getOption('c');
				if (chanNameL != null) {
					chanName = chanNameL.split(",");
					numChan = chanName.length;
				}
			}
			if (ah.checkFlag('m')) {
				String maddr = ah.getOption('m');
				if (maddr != null) multiCast = maddr;
			}
			if (ah.checkFlag('p')) {
				String nss=ah.getOption('p');
				if (nss!=null) {
					String[] ssnums = nss.split(",");
					numSock=ssnums.length;
					for(int i=0; i<numSock; i++) ssNum[i]=Integer.parseInt(ssnums[i]);
				}
			}
			if (ah.checkFlag('d')) {
				String sdt=ah.getOption('d');
				if (sdt!=null) dt = Integer.parseInt(sdt);		// msec
			}
			if (ah.checkFlag('f')) {
				String saf=ah.getOption('f');
				if (saf!=null) autoFlush=Double.parseDouble(saf);
			}
			if (ah.checkFlag('t')) {
				String st=ah.getOption('t');
				if (st!=null) trimTime=Double.parseDouble(st);
			}
			if (ah.checkFlag('x')) {
				debug = true;
			}
		} catch (Exception e) {
			String msgStr = e.getMessage();
			if ( (msgStr == null) || (!msgStr.equalsIgnoreCase("show usage")) ) {
			    System.err.println("Exception parsing arguments:");
			    e.printStackTrace();
			}
			System.err.println("CTudp");
			System.err.println(" -h                     : print this usage info");
			System.err.println(" -x                     : debug mode");
			System.err.println(" -s <source name>       : name of source to write packets to");
			System.err.println("                default : " + srcName);
			System.err.println(" -c <channel name>      : name of channel to write packets to");
			System.err.println("                default : " + chanName[0]);
			System.err.println(" -p <UDP port>          : socket number to listen for UDP packets on");
			System.err.println("                default : "+ssNum[0]);
			System.err.println(" -m <multicast addr>     : multicast UDP address (224.0.0.1 to 239.255.255.255)");
			System.err.println("                default : none");
			System.err.println(" -d <fixed dt>          : fixed delta-time (msec) between frames (dt=0 for arrival-times");
			System.err.println("                default : "+dt);
			System.err.println(" -f <flush (sec)>       : flush interval (sec) (amount of data per zipfile)");
			System.err.println("                default : "+autoFlush);
			System.err.println(" -t <trimTime (sec)>  : trim (ring-buffer loop) time (sec) (trimTime=0 for indefinite)");
			System.err.println("                default : "+trimTime);
			System.exit(0);
		}
		
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

		// start a thread for each port
		UDPbuf = new ByteArrayOutputStream[numSock];
		new UDPread0(ssNum[0], srcName, chanName, dt).start();			// base port (pace-setter)
		for(int i=1; i<numSock; i++) new UDPread1(i, ssNum[i]).start();	// optional extra ports
	}
	
	//--------------------------------------------------------------------------------------------------------
	private class UDPread0 extends Thread {
		
		private int port = 0;
		private String srcName;
		private String[] chanName;
		private DatagramSocket ds=null; 				//listen for data here
		private MulticastSocket ms=null;
		private int dt=0;
		
		UDPread0(int iport, String isrcName, String[] ichanName, int idt) {
			port = iport;
			srcName = isrcName;
			chanName = ichanName;

			try {				//open port for incoming UDP
				if(multiCast != null) {
					System.err.println("Multicast address: "+multiCast);
					ms = new MulticastSocket(port);
					ms.joinGroup(InetAddress.getByName(multiCast));
				}
				else {
					ds = new DatagramSocket(port); 
//					ds.setSoTimeout(10);			//  non-blocking timeout
				}
			} catch (Exception e) { e.printStackTrace(); }
		}
		
		public void run() {
			try {
				DatagramPacket dp=new DatagramPacket(new byte[65536],65536);

				// setup CTwriter
				CTwriter ctw = new CTwriter(srcName,trimTime);	
				ctw.setZipMode(zipMode);
				CTinfo.setDebug(debug);
				ctw.autoFlush(autoFlush);		// auto flush to zip once per interval (sec) of data

				long oldtime = 0;
				long time = 0;

				UDPbuf[0] = new ByteArrayOutputStream(65536);

				while (true) {	
					if(ms!=null) ms.receive(dp);			// multicast	
					else 		 ds.receive(dp);			// unicast

					int packetSize = dp.getLength();
					if (packetSize > 0) {
						if(dt==0 || time==0) 	time = System.currentTimeMillis();
						else 		time += dt;							// only first channel paces
						if(time == oldtime) ++time;		// no dupes

						System.err.println("CTudp bytes: "+dp.getLength()+", sender port: "+dp.getPort()+", t: "+time);
						ctw.setTime(time);

//						byte[] data=new byte[dp.getLength()];		// truncate array to actual data got
//						System.arraycopy(dp.getData(),dp.getOffset(),data,0,dp.getLength());
						UDPbuf[0].write(dp.getData(),dp.getOffset(),dp.getLength());

						try {
							synchronized(UDPbuf) {
								for(int i=0; i<numSock; i++) {
									if(UDPbuf.length>0)	{	// write data from other threads
										ctw.putData(chanName[i], UDPbuf[i].toByteArray());	
										UDPbuf[i].reset();
									}
								}
							}
						} catch(Exception e) {
							e.printStackTrace();			// dont give up on putData exceptions
						}
						oldtime = time;
					}	
				}
			} catch (Exception e) { e.printStackTrace(); }
		}
	}

	//--------------------------------------------------------------------------------------------------------
	private class UDPread1 extends Thread {
		private DatagramSocket ds=null; 				//listen for data here
		private int threadNum;
		
		UDPread1(int ithreadNum, int port) {
			threadNum = ithreadNum;
			try {				//open port for incoming UDP
				ds = new DatagramSocket(port); 
			} catch (Exception e) { e.printStackTrace(); }
		}
		
		public void run() {
			try {
				DatagramPacket dp=new DatagramPacket(new byte[65536],65536);
				UDPbuf[threadNum] = new ByteArrayOutputStream(65536);
				
				while (true) {
					ds.receive(dp);			// unicast
					int packetSize = dp.getLength();
					if (packetSize > 0) {
						System.err.println("CTudp bytes: "+dp.getLength()+", sender port: "+dp.getPort());

//						byte[] data=new byte[dp.getLength()];		// truncate array to actual data got
//						System.arraycopy(dp.getData(),dp.getOffset(),data,0,dp.getLength());
						synchronized(this) {
							UDPbuf[threadNum].write(dp.getData(),dp.getOffset(),packetSize);
						}
					}	
				}
			} catch (Exception e) { e.printStackTrace(); }
		}
	}
} //end class CTudp
