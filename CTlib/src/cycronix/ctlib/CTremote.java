package cycronix.ctlib;

//---------------------------------------------------------------------------------	
// CTremote:  push files to FTP or Dropbox server
// Matt Miller, Cycronix
// 01/12/2016


import java.io.IOException;

/**
 * CloudTurbine utility class that extends CTFile class to write via choice of remote connection
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2016/01/12
 * 
*/

public class CTremote extends CTwriter {

	private remoteSelect rSelect;
	private CTftp 		ctftp=null;
	private CTdropbox 	ctdbx=null;
	private CTwriter	ctw=null;
	
	public enum remoteSelect {
	    FILE, FTP, DROPBOX
	}

	//------------------------------------------------------------------------------------------------
	// constructor

	public CTremote(String dstFolder, remoteSelect _rSelect) throws IOException {
		super(dstFolder);
		rSelect = _rSelect;
		switch(rSelect) {
		case FTP:  		ctftp = new CTftp(dstFolder); 		break;
		case DROPBOX:	ctdbx = new CTdropbox(dstFolder); 	break;
		case FILE:		ctw = 	new CTwriter(dstFolder); 	break;		// drop-thru
		}
	}
	
	//------------------------------------------------------------------------------------------------
	
	public void login(String host, String user, String pw) throws Exception {
		switch(rSelect) {
		case FTP:		ctftp.login(host, user, pw);	break;
		case DROPBOX:	ctdbx.login(host, user, pw);	break;
		case FILE:		break;		// no-op
		}
	}

	public void logout() throws Exception {
		switch(rSelect) {
		case FTP:		ctftp.logout();		break;
		case DROPBOX:	ctdbx.logout ();	break;
		case FILE:		break;		// no-op
		}
	}

	// over-ride CTwriter method to replace file-writes with FTP write
	protected void writeToStream(String pathname, byte[] bdata) throws IOException {
		switch(rSelect) {
		case FTP:		ctftp.writeToStream(pathname, bdata);	break;
		case DROPBOX:	ctdbx.writeToStream(pathname, bdata);	break;
		case FILE:		ctw.writeToStream(pathname, bdata); 	break;
		}
	}
	
}
