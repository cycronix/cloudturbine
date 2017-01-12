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

package erigo.filepump;

/*
 * Copyright 2015-2016 Erigo Technologies LLC
 */

/*
 * FilePumpWorker
 * 
 * This class is the real work-horse of the application.  It writes small files
 * at a user-specified rate into a specified directory.
 * 
 * The file names are of the format:
 * 
 *    <epoch_time_in_millis>_<sequence_num>.txt
 * 
 * where <epoch_time_in_millis> is the time the file was created and
 * <sequence_num> is the sequence number of this file in the test.  The
 * sequence number sequentially increments over consecutive files.  This
 * index is a simple way (other than the file creation time also found
 * in the file name) to specify the file order.
 * 
 * To make the files unique and avoid deduplication by third-party file
 * sharing services, the file contains a 6-digit random number.
 * 
 * To support FTP and SFTP connections, we use Apache commons libraries.
 * 
 * FTP-related code in this class was taken from Matt Miller (Cycronix) class
 * cycronix.ctlib.CTftp.  The original writeToStream() method has been
 * renamed writeToFTP() and tweaked for the present purpose.
 * 
 * SFTP code relies on the the Apache Commons VFS library.  See writeToSFTP()
 * for details.
 * 
 */

import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
// import org.apache.commons.vfs2.Selectors;
// import org.apache.commons.vfs2.impl.DefaultFileReplicator;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;

public class FilePumpWorker implements Runnable {
    
	private FilePump pumpGUI = null;
	private FilePumpSettings pumpSettings = null;
	
	public int file_index = 0;
	
	private Boolean debug = false;
	
	// Variables dealing with FTP writes
	private FTPClient ftpClient = null;
	private String loginDir = "";
	private String currentDir = "";
	
	// SFTP variables
	StandardFileSystemManager manager = null;
    FileSystemOptions fileSystemOptions = null;
    String baseConnectionStr = null;
    
    public FilePumpWorker(FilePump pumpGUII, FilePumpSettings pumpSettingsI) {
    	pumpGUI = pumpGUII;
    	pumpSettings = pumpSettingsI;
    }
    
    public void run() {
    	
    	String output_dir_name = pumpSettings.getOutputFolder();
    	double files_per_sec = pumpSettings.getFilesPerSec();
    	int total_num_files = pumpSettings.getTotNumFiles();
    	FilePumpSettings.FileMode mode = pumpSettings.getMode();
    	String ftpHost = pumpSettings.getFTPHost();
    	String ftpUsername = pumpSettings.getFTPUser();
    	String ftpPassword = pumpSettings.getFTPPassword();
        
        double desired_period = 1 / files_per_sec;
        double sleep_time_millis = desired_period * 1000;
        long time_used_in_last_filename = 0;
        Random random_generator = new Random();
        int random_range = 999999;
        
        if (mode == FilePumpSettings.FileMode.LOCAL_FILESYSTEM) {
        	System.err.println("\nWrite files to " + output_dir_name);
        } else if (mode == FilePumpSettings.FileMode.FTP) {
        	ftpClient = new FTPClient();
        	try {
        		login(ftpHost, ftpUsername, ftpPassword);
        	} catch (Exception e) {
        		System.err.println("Caught exception connecting to FTP server:\n" + e);
        		return;
        	}
        	// Make sure we are only using "/" in output_dir_name
    		output_dir_name = output_dir_name.replace('\\', '/');
        	System.err.println("\nFTP files: host = " + ftpHost + ", username = " + ftpUsername + ", folder = " + output_dir_name);
        } else if (mode == FilePumpSettings.FileMode.SFTP) {
        	// Make sure output_dir_name starts with an "/"
        	if (output_dir_name.charAt(0) != '/') {
        		output_dir_name = "/" + output_dir_name;
        	}
        	manager = new StandardFileSystemManager();
    		try {
    			manager.init();
    			// Just use the default logger
    			// manager.setTemporaryFileStore(new DefaultFileReplicator(new File("C:\\TEMP")));
                fileSystemOptions = createDefaultFileSystemOptions();
    		} catch (Exception e) {
    			System.err.println("Caught exception setting up Apache Commons VFS manager for SFTP:\n" + e);
    			e.printStackTrace();
    			return;
    		}
    		// Make sure we are only using "/" in output_dir_name
    		output_dir_name = output_dir_name.replace('\\', '/');
    		// Create the base connection String
    		// For example, for username "fooUser" and password "fooPW" trying to connect to 192.168.2.56 and put files in folder FooFolder:
    		//     sftp://fooUser:fooPW@192.168.2.56/FooFolder
    		// Note that up above we made sure that output_dir_name starts with "/"
    		baseConnectionStr = "sftp://" + ftpUsername + ":" + ftpPassword + "@" + ftpHost + output_dir_name;
    		System.err.println("\nSFTP files: host = " + ftpHost + ", username = " + ftpUsername + ", folder = " + output_dir_name);
        }
        
        //
        // Setup a periodic timer to update the file count on the GUI
        //
        TimerTask timerTask = new FileCountTimerTask(pumpGUI,this);
        // run timer task as daemon thread
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(timerTask, 0, 5*1000);
        
        while (pumpGUI.bPumpRunning) {
        	
        	long start_time = System.currentTimeMillis();
        	
        	// Create the next file
        	// Always have time move forward
        	// NOTE: The computer's clock being adjusted backward could activate this code
        	if (start_time <= time_used_in_last_filename) {
        		while (true) {
        			try {
                	    Thread.sleep(1);
                	} catch (InterruptedException ie) {
                		// nothing to do
                	}
        			start_time = System.currentTimeMillis();
        			if (start_time > time_used_in_last_filename) {
        				break;
        			}
        		}
        	}
        	++file_index;
        	String filename = Long.toString(start_time) + "_" + Integer.toString(file_index) + ".txt";
        	int random_num = (int)((double)random_range * random_generator.nextDouble());
        	if (mode == FilePumpSettings.FileMode.FTP) {
        		writeToFTP(output_dir_name, filename, random_num);
        	} else if (mode == FilePumpSettings.FileMode.SFTP) {
        		writeToSFTP(filename, random_num);
        	} else {
        		File full_filename = new File(output_dir_name,filename);
        		FileWriter fw;
				try {
					fw = new FileWriter(full_filename,false);
				} catch (IOException e) {
					System.err.println("Caught IOException trying to create the FileWriter:\n" + e + "\n");
					e.printStackTrace();
					break;
				}
        		PrintWriter pw = new PrintWriter(fw);
        		// Write out a random number to the file
        		pw.format("%06d\n",random_num);
        		pw.close();
        	}
        	
        	if ( (!pumpGUI.bPumpRunning) || (file_index == total_num_files) ) {
        		break;
        	}
        	
        	// Sleep
        	try {
        		long actual_sleep_amount = (long)Math.round(sleep_time_millis);
        		if (actual_sleep_amount > 0) {
        	        Thread.sleep(actual_sleep_amount);
        		}
        	} catch (InterruptedException ie) {
        		// nothing to do
        	}
        	
        	// Check how we are doing on timing and adjust the sleep time if needed
        	long stop_time = System.currentTimeMillis();
        	double time_err_secs = desired_period - (double)(stop_time - start_time)/1000.0;
        	// Adjust sleep_time_millis based on this timing error
        	sleep_time_millis = sleep_time_millis + 0.25*time_err_secs*1000.0;
        	// Smallest sleep time is 0
        	if (sleep_time_millis < 0) {
        		sleep_time_millis = 0.0;
        	}
        	
        	time_used_in_last_filename = start_time;
        	
        }
        
        if (mode == FilePumpSettings.FileMode.FTP) {
        	logout();
        } else if (mode == FilePumpSettings.FileMode.FTP) {
        	manager.close();
        }
        
        timer.cancel();
        
        // If we are exiting because the requested number of files have been
        // reached (ie, exiting of our own volition as opposed to someone else
        // canceling the run), then reset the user interface
        if (file_index == total_num_files) {
        	pumpGUI.resetGUI_nonEDT();
        }
        
        if (!pumpGUI.bShowGUI) {
        	System.err.print("\n");
        }
        System.err.println("Exiting FilePumpWorker; wrote out " + file_index + " files.");
    	
    }
    
    public void login(String host, String user, String pw) throws Exception {
		ftpClient.connect(host);
		boolean success = ftpClient.login(user, pw); 
		if(!success) {
			throw new IOException("FTP login failed, host: "+host+", user: "+user);
		}
		ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
		
		loginDir = ftpClient.printWorkingDirectory();
		
		if(debug) System.err.println("FTP login, u: "+user+", pw: "+pw);
	}
    
	public void logout() {
		try { 
			ftpClient.logout(); 
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
    
	//
	// Create file on the FTP server in the directory desired by the user and then
	// write content to that file.
	//
	// This function is based on code from Matt Miller's (Cycronix) class
	// cycronix.ctlib.CTftp.  The original method was named writeToStream();
	// this has been renamed writeToFTP() and tweaked for the present purpose.
	//
	// For other FTP options:
	// http://www.codejava.net/java-se/networking/ftp/java-ftp-file-upload-tutorial-and-example
	//
	protected void writeToFTP(String filepath, String filename, int random_num) {
		
		try {
			if(!filepath.equals(currentDir)) {		// create or change to new directory if changed from previous
				if(filepath.startsWith("/")) ftpClient.changeWorkingDirectory("/");
				else						 ftpClient.changeWorkingDirectory(loginDir);	// new dirs relative to loginDir
				ftpCreateDirectoryTree(ftpClient,filepath);								// mkdirs as needed, leaves working dir @ new
				currentDir = filepath;
			}
            // boolean success = ftpClient.storeFile(filename, fis); 
			// JPW: No need to create a temporary file in our case 
			// OutputStream ostream = ftpClient.storeFileStream(filename+".tmp");
			OutputStream ostream = ftpClient.storeFileStream(filename);
			if(debug) System.err.println("filepath: " + filepath + ", filename: " + filename);
			if(ostream==null) {
				throw new IOException("Unable to FTP file: " + ftpClient.getReplyString());
			}
            
			// Write content to file
    		PrintWriter pw = new PrintWriter(ostream);
    		pw.format("%06d\n",random_num);
			pw.close();
			
			if(!ftpClient.completePendingCommand())
				throw new IOException("Unable to FTP file: " + ftpClient.getReplyString());
            
			// We didn't create temporary file up above, so no need to rename it
			// if(!ftpClient.rename(filename+".tmp", filename)) 
			// 	throw new IOException("Unable to rename file: " + ftpClient.getReplyString());
            
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
	
	//
	// Create file on the SFTP server in the directory desired by the user and then
	// write content to that file.
	//
	// We use the Apache Commons VFS library to perform this.
	// http://commons.apache.org/
	// http://commons.apache.org/proper/commons-vfs/
	//
	// VFS requirements are listed at http://commons.apache.org/proper/commons-vfs/download.html
	// Requires the following libraries:
	// 1. Commons VFS: http://commons.apache.org/proper/commons-vfs/download_vfs.cgi
	// 2. Commons Logging: http://commons.apache.org/proper/commons-logging/download_logging.cgi
	// 2. JSCH: http://www.jcraft.com/jsch/
	//
	// Examples using VFS to perform SFTP can be found at (note that these examples are almost all identical):
	// http://stackoverflow.com/questions/21399561/sftp-upload-download-exist-and-move-using-apache-commons-vfs
	// http://www.memorylack.com/2011/06/apache-commons-vfs-for-sftp.html
	// http://thinkinginsoftware.blogspot.com/2012/01/commons-vfs-sftp-from-java-simple-way.html
	//
	// In contrast to these example, in our code here we directly write to the output file using an
	// OutputStream and thus don't need to have a local copy of the file.
	//
	// An alternate approach would be to perform a secure copy (SCP, which is the
	// same as SFTP) using the JSCH open source library.
	//
	
	protected void writeToSFTP(String filename, int random_num) {
		
		String connectionStr = baseConnectionStr + "/" + filename;
        if(debug) System.err.println(connectionStr);
		
		try {
            // Create remote file object
            FileObject remoteFile = manager.resolveFile(connectionStr, fileSystemOptions);
            FileContent fc = remoteFile.getContent();
            OutputStream ostream = fc.getOutputStream();
			if(ostream==null) {
				throw new IOException("Error opening output stream to SFTP");
			}
			// Write content to file
    		PrintWriter pw = new PrintWriter(ostream);
    		pw.format("%06d\n",random_num);
			pw.close();
			// Cleanup
			if (remoteFile != null) remoteFile.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		/**
		// Here's an alternate approach that creates a local file and then copies the
		// local file to remote directory
		// Create the local file
		File local_file = new File("C:\\TEMP\\TEST",filename);
		try {
		    FileWriter fw = new FileWriter(local_file,false);
		    PrintWriter pw = new PrintWriter(fw);
			// Write out the index and a random number to the file
			pw.format("%06d\n",random_num);
			pw.close();
		} catch (Exception e) {
        	e.printStackTrace();
        }
		// Copy the local file to remote SFTP server
        try {
            // Create local file object
            FileObject localFile = manager.resolveFile(local_file.getAbsolutePath());
            // Create remote file object
            FileObject remoteFile = manager.resolveFile(connectionStr, fileSystemOptions);
            // Copy local file to SFTP server
            remoteFile.copyFrom(localFile, Selectors.SELECT_SELF);
        } catch (Exception e) {
        	e.printStackTrace();
        }
        **/
		
	}
    
    protected FileSystemOptions createDefaultFileSystemOptions() throws FileSystemException {
    	
        FileSystemOptions opts = new FileSystemOptions();
        
        // SSH Key checking
        SftpFileSystemConfigBuilder.getInstance().setStrictHostKeyChecking(opts, "no");
        
        // VFS file system root:
        // setting this parameter false = cause VFS to choose File System's Root as VFS's root
        // setting this parameter true = cause VFS to choose user's home directory as VFS's root
        SftpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(opts, true);
        
        // Timeout is count by Milliseconds
        SftpFileSystemConfigBuilder.getInstance().setTimeout(opts, 20000);
        
        return opts;
    }
	
}
