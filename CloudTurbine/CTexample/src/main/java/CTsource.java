// simple CloudTurbine CTwriter example

import cycronix.ctlib.*;

public class CTsource {
	public static void main(String[] args) {
		int nsamp = 40;					// number of data samples per chan
		int nchan = 1;						// number of channels 
		int blockPts=10;					// flush once per N data points
		long blockInterval=10000;			// 10 sec (10000msec) per block
		
		String outputSource="CTsource";		// name of output source folder
		if(args.length > 0) outputSource = args[0];
		
		try {
			CTwriter ctw = new CTwriter("CTexample/"+outputSource);		// new CTwriter at root/source folder

//			ctw.setBlockMode(true); 									// pack into binary blocks? (linear timestamps per block)
			ctw.setBlockMode(blockInterval); 							// pack into binary blocks? (linear timestamps per block)

			ctw.setTimeRelative(true);									// use relative timestamps?
			ctw.setZipMode(true);
//			ctw.autoFlush(10.); 										// auto-flush every N sec 
			
//			long iTime = System.currentTimeMillis();
			long iTime = 1460000000000L;										// nice even start time (msec)
			ctw.setTime(iTime);										// nominal integer (1000msec) time increment 
			// loop and write some output
			for(int i=1; i<=nsamp; i++) {							// output to nfile
//				ctw.setTime(iTime+=1000);							// msec (un-needed if blockInterval set
				for(int j=0; j<nchan; j++) ctw.putData("c"+j, new Float(i+j));	// nchan per file
//				for(int j=0; j<nchan; j++) ctw.putData("c"+j+".num", ""+(new Float(i+j)));	// try numeric format

				if((i%blockPts)==0) ctw.flush();
//					ctw.flush(blockInterval);		// flush one per N
			}
			ctw.flush();				// clean up

		} catch(Exception e) {
			System.err.println("Exception: "+e);
		} 
		System.err.println("Done.  Output nsamp: "+nsamp+", nchan: "+nchan+", points/block: "+blockPts);
	}
}
