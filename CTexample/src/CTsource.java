// simple CloudTurbine CTwriter example

import cycronix.ctlib.*;

public class CTsource {
	public static void main(String[] args) {
		int nsamp = 1000;					// number of data samples per chan
		int nchan = 3;						// number of channels 
		int blockPts=100;					// flush once per N data points
		boolean binaryMode=false;			// binary mode blocks versus text (string) format

		String outputSource="CTsource";		// name of output source folder
		if(args.length > 0) outputSource = args[0];
		
		try {
			CTwriter ctw = new CTwriter("CTexample/"+outputSource);		// new CTwriter at root/source folder

			ctw.setBlockMode(binaryMode); 								// pack into binary blocks? (linear timestamps per block)
//			ctw.autoFlush(10.); 										// auto-flush every N sec 
			long iTime = System.currentTimeMillis();
			
			// loop and write some output
			for(int i=1; i<=nsamp; i++) {										// output to nfile
				ctw.setTime(iTime+=1000);										// nominal integer (1000msec) time increment 
				for(int j=0; j<nchan; j++) ctw.putData("c"+j, new Double(i+j));	// nchan per file
				if((i%blockPts)==0) ctw.flush();								// flush one per N
			}
			ctw.flush();				// clean up

		} catch(Exception e) {
			System.err.println("Exception: "+e);
		} 
		System.err.println("Done.  Output nsamp: "+nsamp+", nchan: "+nchan+", points/block: "+blockPts+", binaryMode: "+binaryMode);
	}
}
