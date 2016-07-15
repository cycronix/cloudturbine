// simple CloudTurbine CTwriter example

import cycronix.ctlib.*;

public class CTsource {
	public static void main(String[] args) {
		int nsamp = 100;					// number of data samples per chan
		int nchan = 1;						// number of channels 
		int blockPts=10;					// flush once per N data points

		String outputSource="CTsource";		// name of output source folder
		if(args.length > 0) outputSource = args[0];
		
		try {
			CTwriter ctw = new CTwriter("CTexample/"+outputSource);		// new CTwriter at root/source folder

			ctw.setBlockMode(true); 									// pack into binary blocks? (linear timestamps per block)
			ctw.setTimeRelative(false);									// use relative timestamps?
			ctw.setZipMode(true);
//			ctw.autoFlush(10.); 										// auto-flush every N sec 
			
			long iTime = System.currentTimeMillis();
			iTime = 1460000000000L;										// nice even start time
			// loop and write some output
			for(int i=1; i<=nsamp; i++) {								// output to nfile
				ctw.setTime(iTime);										// nominal integer (1000msec) time increment 
				iTime+=1000;
				for(int j=0; j<nchan; j++) ctw.putData("c"+j, new Double(i+j));	// nchan per file
				if((i%blockPts)==0) ctw.flush();								// flush one per N
			}
			ctw.flush();				// clean up

		} catch(Exception e) {
			System.err.println("Exception: "+e);
		} 
		System.err.println("Done.  Output nsamp: "+nsamp+", nchan: "+nchan+", points/block: "+blockPts);
	}
}
