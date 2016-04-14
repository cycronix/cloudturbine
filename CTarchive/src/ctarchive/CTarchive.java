package ctarchive;

//---------------------------------------------------------------------------------	
//CTarchive:  save DT data to CT files
//Matt Miller, Cycronix

import com.rbnb.sapi.*;
import com.rbnb.utility.*;
import cycronix.ctlib.CTwriter;

/**
* A class to save DT data to CT files
* <p>
* @author Matt Miller (MJM), Cycronix
* @version 2014/04/14
* 
*/

/*
* Copyright 2014 Cycronix
* All Rights Reserved
*
*   Date      By	Description
* MM/DD/YYYY
* ----------  --	-----------
* 04/14/2014  MJM	Created.
*/

public class CTarchive {
    private String rbnbServer="localhost:3333";  // RBNB server to connect
    private String sinkName="CTarchive";          // name of this sink
    private Sink sink=null;                      // sink connection
    private String pickup="Fdrop/*";             // pickup subscription
    private String dropFolder="CTarchive";       // folder to write output
    private double flushTime=5.;                  // msec to sleep between requests
    private double trimTime=0.;					// loop (archive) time (sec)
    private boolean startOld=false;				// start at oldest (pick up old data)
    
    public CTarchive(String[] args) {
    	//parse args
    	try {
    		ArgHandler ah=new ArgHandler(args);
    		if (ah.checkFlag('h')) {
    			throw new Exception("");
    		}
    		if (ah.checkFlag('a')) {
    			rbnbServer=ah.getOption('a');
    			if (rbnbServer==null) throw new Exception("Must specify rbnb server with -a");
    		}
    		if (ah.checkFlag('n')) {
    			sinkName=ah.getOption('n');
    		}
    		if (ah.checkFlag('p')) {
    			pickup=ah.getOption('p');
    		}
    		if (ah.checkFlag('d')) {
    			dropFolder=ah.getOption('d');
    		}
    		if (ah.checkFlag('f')) {
    			flushTime=Double.parseDouble(ah.getOption('f'));
    		}
    		if (ah.checkFlag('o')) {
    			startOld=true;
    		}
    		if (ah.checkFlag('t')) {
    			String st=ah.getOption('t');
    			if (st!=null) trimTime=Double.parseDouble(st);
    		}

    	} catch (Exception e) {
    		System.err.println("Usage:  java CTarchive");
    		System.err.println("\t-h\tprint this usage guide");
    		System.err.println("\t-a\t<rbnb host:port> \tdefault: "+rbnbServer);
    		System.err.println("\t-n\t<sink name>      \tdefault: "+sinkName);
    		System.err.println("\t-p\t<pickup channel> \tdefault: "+pickup);
    		System.err.println("\t-d\t<drop folder>    \tdefault: "+dropFolder);
    		System.err.println("\t-o\t<startOldest>    \tdefault: "+startOld);
    		System.err.println("\t-f\t<flushTime (sec)>\tdefault: "+flushTime);
    		System.err.println("\t-t\t<trimTime (sec)>   \tdefault: 0 (open-ended)");
    		RBNBProcess.exit(0);
    	}
    } //end constructor
    
    public static void main(String[] args) {
    	(new CTarchive(args)).exec();
    } //end method main
    
    // loop handling file drops
    public void exec() {
    	int maxtry=0;
    	double rtime = 0.;

    	CTwriter ctw = null;
    	try {
    		ctw = new CTwriter(dropFolder,trimTime);
    		ctw.setZipMode(true);
    		ctw.setDebug(false);
    		ctw.autoFlush(flushTime);				// autoflush @ flushTime
    	} catch(Exception e) {
    		System.err.println("Error setting up CTwriter");
    		System.exit(0);
    	}

    	while(true) {   // recovery from exception
    		try {
    			//make rbnb connection
    			sink=new Sink();
    			sink.OpenRBNBConnection(rbnbServer,sinkName);

    			// subscribe to pickup
    			ChannelMap cmr=new ChannelMap();
    			ChannelMap cm=new ChannelMap();
    			cmr.Add(pickup);	
    			System.err.println("CTarchive monitoring: '"+pickup+"'");
    			double ttime = 0.;
    			
    			//loop handling items that show up 
    			while (true) {
    				// try sleepy poll for next.  duration=0 so single point per fetch (may miss fast data)
    				if(rtime > 0.) {
//    					System.err.println("request: "+cmr+", at next: "+rtime);
    					sink.Request(cmr,rtime,0.,"next");			// not 100% reliable? (can get dupes)
    				}
    				else {
    					if(startOld) sink.Request(cmr,0.,0.,"oldest");
    					else		 sink.Request(cmr,0.,0.,"newest");
    				}

    				cm=sink.Fetch(-1); 		// block? (doesn't block for request "next")
    				
    				if(cm.NumberOfChannels() == 0) {
    					long wmsec = (long) (((flushTime*1000.))/2.);		// sleep 1/2 or 1/4 flushTime
    					if(wmsec < 10) wmsec = 10;
    					Thread.sleep(wmsec);  // take a break;
//    					System.err.println("ZERO CHANS");
    					continue;
    				}
//    				System.err.println("GOT CHAN");
    				
    				ttime = cm.GetTimeStart(0);			// merge multi chan times to first
    				if(ttime == rtime) {
//    					System.err.println("skipping dupe time at: "+rtime);
    					continue;		// skip dupes
    				}
					ctw.setTime(ttime);
    				rtime = ttime;				// keep rtime up to date with very latest
    				
    				//extract file(s) from fetch
    				for (int i=0;i<cm.NumberOfChannels();i++) {
    					String cname = cm.GetName(i);
//    					double ttime = cm.GetTimeStart(i);
    					int ftype = cm.GetType(i);
//        				if(i==0) System.err.println("Got("+i+"): "+cm.GetName(i)+", at time: "+cm.GetTimeStart(i));

    					cname = cname.substring(cname.lastIndexOf('/')+1);  // relative name

    					String sdata=null;			// for now, single string "numeric" value per output
    					switch(ftype) {
    					case ChannelMap.TYPE_FLOAT32:	sdata = Float.toString(cm.GetDataAsFloat32(i)[0]); break;
    					case ChannelMap.TYPE_FLOAT64:	sdata = Double.toString(cm.GetDataAsFloat64(i)[0]); break;
    					case ChannelMap.TYPE_INT32:		sdata = Integer.toString(cm.GetDataAsInt32(i)[0]); break;
    					case ChannelMap.TYPE_INT64:		sdata = Long.toString(cm.GetDataAsInt64(i)[0]); break;
    					case ChannelMap.TYPE_STRING:	sdata = cm.GetDataAsString(i)[0];	break;
    					default:						sdata = new String(cm.GetData(i));	break;
    					}

    					//	    			System.err.println("CTarchive write: "+cname+", time: "+ttime);
//    					ctw.setTime(ttime);
    					ctw.putData(cname, sdata);
    				}


    				maxtry=0;					// got a good one, reset maxtry
    			}
    		} catch (Exception e) {
    			try{ ctw.flush(); } catch(Exception ee){};
    			sink.CloseRBNBConnection();
    			e.printStackTrace();
    			if(++maxtry < 10) System.err.println(sinkName+" exception. Restart "+maxtry+"/10");
    			else              System.exit(-1);
    		}
    	} // loop back to exception recovery point
    }//end exec method 
}//end CTarchive class
