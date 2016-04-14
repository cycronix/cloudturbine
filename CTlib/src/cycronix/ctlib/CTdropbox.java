package cycronix.ctlib;

//---------------------------------------------------------------------------------	
// CTdropbox:  push files to Dropbox server
// Matt Miller, Cycronix
// 01/11/2016


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.dropbox.core.*;
import com.dropbox.core.v2.*;

/**
 * CloudTurbine utility class that extends CTFile class to write via Dropbox versus local filesystem
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2016/01/11
 * 
*/
 
public class CTdropbox extends CTwriter {

//    static final String ACCESS_TOKEN = "MIaRzGXMocQAAAAAAALwsB7h5p_zW-U5hjuTvGtFQpIwNlpRtrE8QhaKIBDFVpiC";
    static final String ACCESS_TOKEN = "MIaRzGXMocQAAAAAAALxVENtAUWBFNROatiYPZlCtmCvR3jJ-mlpZ-24mDWq_C7Q";
	private DbxClientV2 client;
	private ExecutorService executor = Executors.newFixedThreadPool(10);

	//------------------------------------------------------------------------------------------------
	// constructor

	public CTdropbox(String dstFolder) throws IOException {
		super(dstFolder);
	}
	
	public CTdropbox(String dstFolder, double itrimTime) throws IOException {
		super(dstFolder);
		throw new IOException("CTdropbox does not yet support file trim");
	}
	
	//------------------------------------------------------------------------------------------------
	
	public void login(String host, String user, String pw) throws Exception {
		synchronized(this) {
			try{
				System.err.println("Dropbox login, client: "+client+", u: "+user+", pw: "+pw);

				// Create Dropbox client
				DbxRequestConfig config = new DbxRequestConfig("Cycronix/CloudTurbine", "en_US");
				client = new DbxClientV2(config, ACCESS_TOKEN);

				// Get current account info
				DbxUsers.FullAccount account = client.users.getCurrentAccount();
		        System.err.println("dbx logged into account:  "+account.name.displayName);
		        
			} catch(Exception e) {
				System.err.println("Dropbox login Exception: "+e.getMessage());
				throw e;
			}
		}
	}

	public void logout() throws Exception {
		throw new IOException("CTdropbox does not yet support logout");
	}

	// over-ride CTwriter method to replace file-writes with FTP write
	protected void writeToStream(String pathname, byte[] bdata) throws IOException {
		if(!pathname.startsWith("/")) pathname = "/"+pathname;		// dropbox requires leading slash
        executor.execute(new UploadThread(pathname, bdata));
	}
	
	private class UploadThread implements Runnable {

	    private String pathname;
	    private byte[] bdata;
	    
	    public UploadThread(String pathname, byte[] bdata){
	        this.pathname=pathname;
	        this.bdata = bdata;
	    }

	    @Override
	    public void run() {
			try { 
				System.err.println("Dropbox start upload: "+pathname);
	            InputStream in = new ByteArrayInputStream( bdata );
	        	DbxFiles.UploadBuilder uplb = client.files.uploadBuilder(pathname);
	        	uplb.mode(DbxFiles.WriteMode.overwrite);
	        	uplb.run(in);
//	        	System.err.println("Dropbox uploaded done.");
			} catch (Exception e) {
				e.printStackTrace();
			}
	    }
	}
	
}
