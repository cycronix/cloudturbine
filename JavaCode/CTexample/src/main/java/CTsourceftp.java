
import java.io.File;
import cycronix.ctlib.*;

/**
 * CloudTurbine demo source with FTP
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2015/06/22
 * 
*/

/*
* Copyright 2015 Cycronix
* All Rights Reserved
*
*   Date      By	Description
* MM/DD/YYYY
* ----------  --	-----------
* 06/22/2015  MJM	Created.
*/
 
public class CTsourceftp {
	public static void main(String[] args) {
		String dstFolder = "";
		if(args.length > 0) dstFolder = args[0];
		else				dstFolder = "Documents/Test/FTP/CTperf";

		String host = "localhost";
		String user = "user";		// need to parse as args
		String pw = "pw";
		
		try {
			// setup CTwriter
//			CTwriter ctw = new CTwriter(dstFolder);
			CTftp ctftp = new CTftp(dstFolder);
			ctftp.login(host,user,pw);
			CTwriter ctw = ctftp;			// upcast to common reference
			
			ctw.setZipMode(true);			// bundle to zip files
//			ctw.autoFlush(200);				// automatically flush at this interval (msec)
			CTinfo.setDebug(true);
			
			long time = System.currentTimeMillis();
			long dt = 500;
			
			// loop and write some output
			for(int i=0; i<10; i++) {
				ctw.setTime(time);				
				ctw.putData("foo", i);		// two channels of data
				ctw.putData("bar", i*2);
				ctw.flush();				// flush (zip) them
				System.err.println("flushed: "+i);
				Thread.sleep(dt);
				time += dt;
			}
		} catch(Exception e) {
			System.err.println("CTsourceftp exception: "+e);
			e.printStackTrace();
		} 
	}
}
