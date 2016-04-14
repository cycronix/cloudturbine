package cycronix.ctlib;

//---------------------------------------------------------------------------------	
// CTftp:  push files to FTP server
// Matt Miller, Cycronix
// 06/22/2015


import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.net.ftp.FTPClient;

/**
 * CloudTurbine utility class that extends CTFile class to write via FTP versus local filesystem
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2015/06/01
 * 
*/

public class CTftp extends CTwriter {

	private FTPClient client = new FTPClient();
	private String loginDir = "";
	private String currentDir = "";
	
	//------------------------------------------------------------------------------------------------
	// constructor

	public CTftp(String dstFolder) throws IOException {
		super(dstFolder);
	}
	
	public CTftp(String dstFolder, double itrimTime) throws IOException {
		super(dstFolder);
		throw new IOException("CTftp does not yet support file trim");
	}
	
	//------------------------------------------------------------------------------------------------
	
	public void login(String host, String user, String pw) throws Exception {
		client.connect(host);
		boolean success = client.login(user, pw); 
		if(!success) {
			throw new IOException("FTP login failed, host: "+host+", user: "+user);
		}
		client.setFileType(FTPClient.BINARY_FILE_TYPE);
		
		loginDir = client.printWorkingDirectory();
		if(debug) System.err.println("FTP login, u: "+user+", pw: "+pw);
	}

	public void logout() {
		try { 
			client.logout(); 
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// over-ride CTwriter method to replace file-writes with FTP write
	protected void writeToStream(String pathname, byte[] bdata) throws IOException {
		try { 
			int delim = pathname.lastIndexOf('/');
			String filename = pathname.substring(delim+1);
			String filepath = pathname.substring(0,delim);
			
			if(!filepath.equals(currentDir)) {		// create or change to new directory if changed from previous
				if(pathname.startsWith("/")) client.changeWorkingDirectory("/");
				else						 client.changeWorkingDirectory(loginDir);	// new dirs relative to loginDir
				ftpCreateDirectoryTree(client,filepath);								// mkdirs as needed, leaves working dir @ new
				currentDir = filepath;
			}
//			boolean success = client.storeFile(filename, fis); 
			OutputStream ostream = client.storeFileStream(filename+".tmp");
			if(debug) System.err.println("ftp pathname: "+pathname+", filename: "+filename+", filepath: "+filepath);
			if(ostream==null) {
				throw new IOException("Unable to FTP file: " + client.getReplyString());
			}

			ostream.write(bdata);
			ostream.close();
			if(!client.completePendingCommand())
				throw new IOException("Unable to FTP file: " + client.getReplyString());
			
			if(!client.rename(filename+".tmp", filename)) 
				throw new IOException("Unable to rename file: " + client.getReplyString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	* utility to create an arbitrary directory hierarchy on the remote ftp server 
	* @param client
	* @param dirTree  the directory tree only delimited with / chars.  No file name!
	* @throws Exception
	*/
	private static void ftpCreateDirectoryTree( FTPClient client, String dirTree ) throws IOException {
		boolean dirExists = true;
		//tokenize the string and attempt to change into each directory level.  If you cannot, then start creating.
		String[] directories = dirTree.split("/");
		for (String dir : directories ) {
			if (!dir.isEmpty() ) {
				if (dirExists) dirExists = client.changeWorkingDirectory(dir);
				if (!dirExists) {
					if (!client.makeDirectory(dir))
						throw new IOException("Unable to create remote directory '" + dir + "'.  error='" + client.getReplyString()+"'");
					if (!client.changeWorkingDirectory(dir)) 
						throw new IOException("Unable to change into newly created remote directory '" +dir+ "'. error='" + client.getReplyString()+"'");
				}
			}
		}     
	}
	
}
