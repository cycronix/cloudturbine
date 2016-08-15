
import cycronix.ctlib.*;

//CloudTurbine test many sources
//MJM 08/08/2016

public class CTbigtest {
	static int totalSamps = 14*24*60*60;	// total duration of test (e.g. 14 days)
	static int sampInterval = 1000;			// delta-time between samples (msec)
	static int blockInterval = 10000;		// block interval (msec)
	static int Nchan = 3;					// number of channels
	static int Nsource = 4;					// number of sources (e.g. 400)
	static int rateFactor = 100;			// run faster than RT (e.g. 100)
	static long baseTime = 1460000000000L;	// base start time (round number)
	static int blocksPerSegment = 1000;		// new segment every this many blocks (default=100)

	private static class RunSource
	implements Runnable {
		CTwriter source;
		String sname="";
		int snum=0;
		double trimTime = 1. + totalSamps*sampInterval/1000.;	// automatically trim files older than this (sec relative to newest)
		int sampsPerSegment = blocksPerSegment * blockInterval / sampInterval;
		
		public RunSource(int iname) {
			snum = iname;
			try {
				sname = "S"+iname;
//				source = new CTwriter(sname,trimTime);			// TO DO:  test ring buffer trim
				source = new CTwriter(sname);
				source.setBlockMode(true,true);		
				source.autoFlush(blockInterval, blocksPerSegment);			
			} catch(Exception e) {
				System.err.println("RunSource constructor exception: "+e);
			}
		}

		public void run() {
			int sampcnt=0;
			int segcnt=0;
//			long iTime = System.currentTimeMillis();
			long iTime = baseTime;							// nominal starting time
			System.err.println("Source started: "+sname);
			do {
				try {
					long thisTime = iTime+sampcnt*sampInterval;			// msec
					source.setTime(thisTime);							// nominal time 
					for(int i=0; i<Nchan; ++i) {
						source.putData("c"+i+".f64",(double)(i+sampcnt+snum*1000));
					}
					if(((++sampcnt) % sampsPerSegment) == 0) {			// periodic updates
							segcnt++;
							if(snum==0) System.err.println("Samples: "+sampcnt+"/"+totalSamps+" (Segment: "+segcnt+")");
					}
					Thread.sleep(sampInterval/rateFactor);
				} catch (Exception e) {
					System.err.println("Exception on RunSource run: "+e);
				}
			}
			while(sampcnt<totalSamps);
		}
	}

	public static void main(String[] args) {

		if(args.length > 0) Nsource = Integer.parseInt(args[0]);
		for(int i=0; i<Nsource; i++) {
			RunSource R = new RunSource(i);
			Thread t = new Thread(R);
		    t.start();
		}
		System.err.println("all sources started, sleeping..");
		try{ Thread.sleep(10000000); } catch(Exception ee){};
		System.err.println("done sleeping, buhbye");

	}

}

