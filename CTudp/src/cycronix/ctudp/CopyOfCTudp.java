package cycronix.ctudp;

//---------------------------------------------------------------------------------	
//CTudp:  capture UDP packets to CT files
//Matt Miller, Cycronix

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;

import cycronix.ctlib.*;

/*
* Copyright 2014 Cycronix
* All Rights Reserved
*
*   Date      By	Description
* MM/DD/YYYY
* ----------  --	-----------
* 03/09/2014  MJM	Created.
*/

//---------------------------------------------------------------------------------	

public class CopyOfCTudp {
	static DatagramSocket[] ds = new DatagramSocket[32]; //listen for data here
	MulticastSocket ms = null;
	
	String 			srcName 	= 	new String("CTudp");
	static String[] chanName	=	new String[32];
	static int		ssNum[]		=	new int[32];
	
	static int 		dt=0;		// automatic delta-time (msec)
	
	int numSock=0;
	int numChan=0;
	int numDt=0;
	
	String multiCast=null;
	
	boolean zipMode=true;
	boolean debug=false;
	double autoFlush=1.;			// flush interval (sec)				
	double trimTime;				// trimtime (sec)
	
	//--------------------------------------------------------------------------------------------------------
	public static void main(String[] arg) {
		chanName[0] = "CTudp";
		ssNum[0] = 4444;
		new CopyOfCTudp(arg).start();
	}
	
	//--------------------------------------------------------------------------------------------------------
	public CopyOfCTudp(String[] arg) {
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
		
		try {				//open port for incoming UDP
			if(multiCast != null) {
				System.err.println("Multicast address: "+multiCast);
				ms = new MulticastSocket(ssNum[0]);
				ms.joinGroup(InetAddress.getByName(multiCast));
			}
			else {
				for(int i=0; i<numSock; i++) {
					ds[i] = new DatagramSocket(ssNum[i]); 
					ds[i].setSoTimeout(10);			//  non-blocking timeout
				}
			}
		} catch (Exception e) { e.printStackTrace(); }
                
	}
	
	//--------------------------------------------------------------------------------------------------------
	public void start() {
		try {
			DatagramPacket dp=new DatagramPacket(new byte[65536],65536);
			
			// setup CTwriter
			CTwriter ctw = new CTwriter(srcName,trimTime);	
			ctw.setZipMode(zipMode);
			ctw.setDebug(debug);
			ctw.autoFlush(autoFlush);		// auto flush to zip once per interval (sec) of data

			long[] oldtime = new long[32];
			long[] time = new long[32];
			
			while (true) {			// presumes continuous stream to force flushes...
				for(int i=0; i<numSock; i++) {
					
					if(ms!=null) ms.receive(dp);			// multicast	
					else {
						try {
							ds[i].receive(dp);				// unicast
						} catch(SocketTimeoutException e) {
							continue;
						};
					}
					
					int packetSize = dp.getLength();
					if (packetSize > 0) {
						if(time[i] == 0) time[i] = System.currentTimeMillis();
						if(dt==0 || i>0) time[i] = System.currentTimeMillis();
						else 			 time[i] += dt;							// only first channel paces
						
						if(time[i] == oldtime[i]) ++time[i];		// no dupes

//						if(debug) System.err.println("CTudp bytes: "+dp.getLength()+", t: "+time[i]+", sender port: "+dp.getPort());
						ctw.setTime(time[i]);

						byte[] data=new byte[dp.getLength()];		// truncate array to actual data got
						System.arraycopy(dp.getData(),dp.getOffset(),data,0,dp.getLength());
						try {
							ctw.putData(chanName[i], data);
						} catch(Exception e) {
							e.printStackTrace();			// dont give up on putData exceptions
						}
						oldtime[i] = time[i];
					}
				}	
			}
		} catch (Exception e) { e.printStackTrace(); }
	}

} //end class CTudp
