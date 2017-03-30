/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package cycronix.ctlib;

import java.io.File;

//---------------------------------------------------------------------------------	
// CTftp:  push files to FTP server
// Matt Miller, Cycronix
// 06/22/2015


import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;

/**
 * CloudTurbine utility class that extends CTwriter class to write via FTP versus local filesystem
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2015/06/01
 * 
*/

public class CTftp extends CTwriter {

	private FTPClient client = null;
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
		login(host, user, pw, false);
	}
	
	public void login(String host, String user, String pw, boolean secure) throws Exception {
		if(secure) 	client = new FTPSClient(true);
		else		client = new FTPClient();
		
		client.connect(host);
		boolean success = client.login(user, pw); 
		if(!success) {
			throw new IOException("FTP login failed, host: "+host+", user: "+user);
		}
		client.setFileType(FTPClient.BINARY_FILE_TYPE);
		
		loginDir = client.printWorkingDirectory();
		CTinfo.debugPrint("FTP login, u: "+user+", pw: "+pw+", loginDir: "+loginDir);
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
			if(!File.separator.equals("/")) {		// replace Windows back-slash with slash for hopefully universal FTP syntax
				pathname = pathname.replace(File.separator.charAt(0), '/');
			}
			int delim = pathname.lastIndexOf('/');
			String filename = pathname.substring(delim+1);
			String filepath = pathname.substring(0,delim);
			
			if(!filepath.equals(currentDir)) {		// create or change to new directory if changed from previous
				if(pathname.startsWith("/")) client.changeWorkingDirectory("/");
				else						 client.changeWorkingDirectory(loginDir);	// new dirs relative to loginDir
				ftpCreateDirectoryTree(client,filepath);								// mkdirs as needed, leaves working dir @ new
				currentDir = filepath;
			}

			CTinfo.debugPrint("ftp pathname: "+pathname+", filename: "+filename+", filepath: "+filepath);

			OutputStream ostream = client.storeFileStream(filename+".tmp");
			//			OutputStream ostream = client.storeFileStream(filename);				// try without tmp file
			if(ostream==null) {
				System.err.println("CTftp, bad FTP connection, try again: "+client.getReplyString());
				ostream = client.storeFileStream(filename+".tmp");		// try again?
				if(ostream == null) {
					String ereply = client.getReplyString();
					System.err.println("CTftp, bad FTP connection, throw exception: "+ereply);
					client.deleteFile(filename+".tmp");		// try not to orphan empty tmp file
					throw new IOException("Bad FTP connection, file: " +filename+", status: "+ ereply);
				}
			}

			ostream.write(bdata);
			ostream.close();
			if(!client.completePendingCommand()) {
				String ereply = client.getReplyString();
				client.deleteFile(filename+".tmp");		// try not to orphan empty tmp file
				throw new IOException("Unable to FTP file: " + ereply);
			}
			
			if(!client.rename(filename+".tmp", filename))		// rename to actual filename
				throw new IOException("Unable to rename file: " +filename+", status: "+ client.getReplyString());

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
				if (dirExists) {
					dirExists = client.changeWorkingDirectory(dir);
				}

				if (!dirExists) {
					try {
						if (!client.makeDirectory(dir)) {
							throw new IOException("Unable to create remote directory: " + dir + ", error=" + client.getReplyString());
						}
						if (!client.changeWorkingDirectory(dir)) {
							throw new IOException("Unable to change into newly created remote directory: " +dir+ ", error=" + client.getReplyString());
						}
					}
					catch(IOException ioe) {
//						System.err.println("ftpCreateDir exception on dirTree: "+dirTree+", dir: "+dir+", error: "+ioe.getMessage());
						throw ioe;
					}
				}
			}     
		}
	}
}
