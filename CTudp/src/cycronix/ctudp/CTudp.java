package cycronix.ctudp;

//---------------------------------------------------------------------------------	
//CTudp:  capture UDP packets to CT files
//Matt Miller, Cycronix

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import cycronix.ctlib.*;

/*
* Copyright 2014 Cycronix
* All Rights Reserved
*
*   Date      By	Description
* MM/DD/YYYY
* ----------  --	-----------
* 03/09/2014  MJM	Created.
* 05/20/2014  MJM	Added multi-port feature.
*/

//---------------------------------------------------------------------------------	

public class CTudp {
	
	// params common to all sockets:
	String multiCast=null;			// multicast address
	boolean zipMode=true;
	boolean debug=false;
	double autoFlush=1.;			// flush interval (sec)	
	long autoFlushMillis;			// flush interval (msec)
	double trimTime;				// trimtime (sec)	
	String srcName 	= 	new String("CTudp");
	static int numSock = 0;
	CTwriter ctw;
	
	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] arg) {
		new CTudp(arg);
	}
	
	//--------------------------------------------------------------------------------------------------------
	public CTudp(String[] arg) {
		String[] 	chanName	=	new String[32];
		int			ssNum[]		=	new int[32];
		double			dt[] = new double[32];
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
				if (sdt!=null) {
					String[] ssdt = sdt.split(",");
					for(int i=0; i<ssdt.length; i++) dt[i]=Double.parseDouble(ssdt[i]);
				}
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
		
		// setup CTwriter
		try {
			ctw = new CTwriter(srcName,trimTime);	
			ctw.setZipMode(zipMode);
			ctw.setDebug(debug);
			autoFlushMillis = (long)(autoFlush*1000.);
//			ctw.autoFlush(autoFlush);		// auto flush to zip once per interval (sec) of data
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

		// start a thread for each port
		for(int i=0; i<numSock; i++) {
			System.err.println("start thread for port: "+ssNum[i]+", chan: "+chanName[i]);
			new UDPread(ssNum[i], chanName[i], dt[i]).start();
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
		private DatagramSocket ds=null; 				//listen for data here
		private MulticastSocket ms=null;
		private double dt=0;
		
		UDPread(int iport, String ichanName, double idt) {
			port = iport;
			chanName = ichanName;
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
								ctw.setTime((long)time);
								ctw.putData(chanName, data);
//								long thisTime = System.currentTimeMillis();
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
