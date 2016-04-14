package cycronix.ctlib;

//---------------------------------------------------------------------------------	
//CTwriter:  write data to CloudTurbine-format folders, with zip-option
//Matt Miller, Cycronix
//02/11/2014

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A class to write data to CloudTurbine folder/files.
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2015/10/19
 * 
*/

/*
* Copyright 2014 Cycronix
* All Rights Reserved
*
*   Date      By	Description
* MM/DD/YYYY
* ----------  --	-----------
* 02/06/2014  MJM	Created.
*/

//------------------------------------------------------------------------------------------------

public class CTwriter {

	private String destPath=null;
	private ZipOutputStream zos = null;
	private ByteArrayOutputStream baos = null;
	protected String destName;

	protected boolean debug=false;
	protected boolean zipFlag=true;
	protected boolean blockMode=false;
	protected boolean byteSwap=false;		// false: Intel little-endian, true: Java/network big-endian
	
	private boolean gzipFlag=false;
	private long fTime=0;
	private long rootTime=0;
	private long autoFlush=Long.MAX_VALUE;	// auto-flush subfolder time-delta (msec)
	private long lastFtime=0;
	private long thisFtime=0;
	private double trimTime=0.;				// trim delta time (sec relative to last flush)
	CTtrim cttrim = null;
	private int compressLevel=1;			// 1=best_speed, 9=best_compression
	
	//------------------------------------------------------------------------------------------------
	// constructor
	/**
	 * Constructor
	 * @param dstFolder Destination folder to write CT files.  Of form: "rootFolder/sourceFolder"
	 * @throws IOException
	 */
	public CTwriter(String dstFolder) throws IOException {
		destPath = dstFolder;		// only set name, no mkDirs in constructor?  MJM 1/11/16
//		new File(destPath = dstFolder).mkdirs();
	}
	
	/**
	 * Constructor
	 * @param dstFolder Destination folder to write CT files.  Of form: "rootFolder/sourceFolder"
	 * @param itrimTime	Ring-buffer data by automatically trimming files older than this (sec relative to newest)
	 * @throws IOException
	 */
	public CTwriter(String dstFolder, double itrimTime) throws IOException {
		destPath = dstFolder;		// only set name, no mkDirs in constructor?  MJM 1/11/16
//		new File(destPath = dstFolder).mkdirs();
		if((trimTime=itrimTime) > 0.) cttrim = new CTtrim(dstFolder);
	}
	
	//------------------------------------------------------------------------------------------------
	// do not depend on finalize, kicks in at unpredictable garbage collection
	protected void finalize() throws Throwable {
		try {
			flush();        // close open files
		} finally {
			super.finalize();
		}
	}

	//------------------------------------------------------------------------------------------------
	// set state flags
	/**
	 * Set debug mode
	 * @param dflag boolean true/false debug mode
	 */
	public void setDebug(boolean dflag) {
		debug = dflag;
	}

	/**
	 * Automatically flush data (no-op in non-zipfile mode)
	 * @param iautoflush interval (msec) at which to flush data to new zip file
	 */
	public void autoFlush(long iautoflush) {
		if(iautoflush == 0) autoFlush = Long.MAX_VALUE;
		else				autoFlush = iautoflush;						// msec
	}
	
	/** 
	 * Automatically flush data (no-op in non-zipfile mode)
	 * @param iautoflush interval (sec) at which to flush data to new zip file
	 */
	public void autoFlush(double iautoflush) {
		autoFlush = (long)(iautoflush * 1000.);			// convert to msec
	}
		
	/**
	 * Set binary packed block mode output.
	 * <p>  
	 * A "block" is comprised of all putData (per channel) up to each <code>flush()</code>. 
	 * The point times in a block are linearly interpolated start-to-end over block.
	 * <p>
	 * The start time of a block is the <code>setTime()</code> at the first <code>putData()</code>.
	 * The end time of a block is the <code>setTime()</code> at the <code>flush()</code>.
	 * <p>
	 * Known issue:  back-to-back block times may overlap/gap...  needs to be reviewed.
	 * <p>
	 * @param bflag true/false set block mode (default: false)
	 */
	public void setBlockMode(boolean bflag) {
		blockMode = bflag;
	}
	
	/**
	 * Set byte-swap mode for all binary output.
	 * false: Intel little-endian, true: Java/network big-endian
	 * <br>default: false
	 * @param bswap byte swap true/false
	 */
	public void setByteSwap(boolean bswap) {
		byteSwap = bswap;
	}
	
	/**
	 * Gzip (.gz) files in addition to regular zip (.zip)
	 * somewhat experimental
	 * @param gzipflag gzip mode true/false (default: false)
	 */
	public void setGZipMode(boolean gzipflag) {
		gzipFlag = gzipflag;
	}
	
	// various options (too many?) to set compression mode
	/**
	 * set wheter output files are to be zipped.  
	 * @param zipflag zip files true/false (default: true)
	 */
	public void setZipMode(boolean zipflag) {
		zipFlag = zipflag;
	}
	
	/** 
	 * set output files to be zipped setting compression level
	 * @param zipflag zip mode true/false (default: true)
	 * @param clevel zip compression level (1=none, 9-max, 10=auto-gzip).  default=1: best-speed
	 */
	public void setZipMode(boolean zipflag, int clevel) {
		if(compressLevel == 10) setZipMode(zipflag, 0, true);
		else					setZipMode(zipflag, clevel, false);
	}
	
	private void setZipMode(boolean zipflag, int clevel, boolean gzipflag) {
		zipFlag = zipflag;
		compressLevel = clevel;
		gzipFlag = gzipflag;
	}
	
	//------------------------------------------------------------------------------------------------
	// 
	/**
	 * Set time for subsequent <code>putData()</code>.
	 * <p>
	 * With no argument, calls: <code>setTime(System.currentTimeMillis())</code>.
	 * <br>If <code>setTime()</code> not called, time is automatically set to current time each <code>putData()</code>.
	 */
	
	public void setTime() {
		setTime(System.currentTimeMillis());	// default
	}

	/**
	 * Set time for subsequent putData().
	 * @param ftime time (msec)
	 */
	public void setTime(long ftime) {
		fTime = ftime;	
	}

	/**
	 * Set time for subsequent putData().
	 * @param ftime time (sec)
	 */
	public void setTime(double ftime) {
		fTime = (long)(ftime * 1000.);				// convert to msec (eventually carry thru sec)
	}
	
	//------------------------------------------------------------------------------------------------	
	/**
	  * Write collected entries to .zip file.  
	  * <p> No-op for regular (non-zip) files.
    */

   /*
    *
    *   Date      By	Description
    * MM/DD/YYYY
    * ----------  --	-----------
    * 02/06/2014  MJM	Created.
    */
	
	public void flush() throws IOException {
		flush(false);
	}
	
	/**
	 * Write collected entries to .zip file
	 * @param gapless true/false auto-set end time of this zip file to start of next
	 * @throws IOException
	 */
//	public void flush(boolean gapless) throws IOException {		// sync slows external writes?
	public synchronized void flush(boolean gapless) throws IOException {

		try {
			// if data has been queued in blocks, write it out once per channel before normal flush
			for(Entry<String, ByteArrayOutputStream>e: blockData.entrySet()) {		// entry keys are by name; full block per channel per flush
				if(debug) System.err.println("flush block: "+e.getKey()+" at time: "+thisFtime);
				writeData(thisFtime, e.getKey(), e.getValue().toByteArray());
			}
			blockData.clear();

			if(zos != null) {		// zip mode writes once per flush; non-zip files written every update
				zos.close();	zos = null;
				writeToStream(destName, baos.toByteArray());
			}
			
			if(trimTime > 0 && rootTime > 0) {			// trim old data (trimTime=0 if ftp Mode)
//				double trim = ((double)rootTime/1000.) - trimTime;			// relative??
				double trim = ((double)System.currentTimeMillis()/1000.) - trimTime;
				if(debug) System.err.println("trimming at: "+trim);
				cttrim.dotrim(trim);			
			}
			

			lastFtime = thisFtime;								// remember last time flushed
			if(gapless ||  blockMode) rootTime = thisFtime;		// preset next folder-rootTime to end of this for gapless time
			else		rootTime = 0;							// reset to new root folder
			
		} catch(Exception e) { 
			System.err.println("flush failed"); 
			e.printStackTrace(); 
			throw new IOException("FTP flush failed: " + e.getMessage());
		} 
		
	}
	
	//------------------------------------------------------------------------------------------------

	// separate writeToStream from flush():  allows CTftp to over-ride this method for non-file writes
	private boolean firstTime = true;
	protected void writeToStream(String fname, byte[] bdata) throws IOException {
		try {
			if(firstTime==true) {
				firstTime=false;
				new File(destPath).mkdirs();  // mkdir here, not in constructor MJM 1/11/16
			}
			
			if(gzipFlag) {		// special case, gzip(zip).  won't yet work with FTP 
				OutputStream fos = new FileOutputStream(new File(fname+".gz"));
				GZIPOutputStream bos = new GZIPOutputStream(new BufferedOutputStream(fos));
				bos.write(bdata);
				bos.close();
			}
			else {				// conventional file (zip or not)
				FileOutputStream fos = new FileOutputStream(fname);
				fos.write(bdata);
				fos.close();
			}
			
			if(debug) System.err.println("writeToStream: "+fname+", bytes: "+bdata.length);
		} catch(Exception e) { 
			System.err.println("writeToStream failed"); 
			e.printStackTrace(); 
		} 
	}
	
	//------------------------------------------------------------------------------------------------
	/** Core underlying data-writer (all other puts go thru this)
	 * @param outName Parameter Name
	 * @param bdata Byte array of data 
	 */
	public synchronized void putData(String outName, byte[] bdata) throws Exception {
		// TO DO:  deprecate following, use addData vs putData/blockmode
		if(blockMode) {				// in block mode, delay putData logic until full queue
			if(debug) System.err.println("addData at rootTime: "+rootTime+", thisFtime: "+thisFtime);
			addData(outName, bdata);
			return;
		}
		
		// fTime:  manually set time (0 if use autoTime)
		// thisFtime:  this frame-entry time, set to fTime each entry
		// rootTime:  parent folder (zip) time, set to match first add/put frame-entry time
		
		thisFtime = fTime;
		if(thisFtime == 0) thisFtime = System.currentTimeMillis();

		if(debug) System.err.println("putData: "+outName+", thisFtime: "+thisFtime+", rootTime: "+rootTime+", fTime: "+fTime);

		if(lastFtime == 0) lastFtime = thisFtime;				// initialize
		else if((thisFtime - lastFtime) >= autoFlush) {
			if(debug) System.err.println("putData autoFlush at: "+thisFtime+" ********************************");
			flush();
		}
		if(rootTime == 0) rootTime = thisFtime;

		writeData(thisFtime, outName, bdata);
	}

	//------------------------------------------------------------------------------------------------
	// queue data for blockMode
	private HashMap<String, ByteArrayOutputStream>blockData = new HashMap<String, ByteArrayOutputStream>();

	private synchronized void addData(String name, byte[] bdata) throws Exception {
		
		thisFtime = fTime;
		if(thisFtime == 0) thisFtime = System.currentTimeMillis();

		if(lastFtime == 0) lastFtime = thisFtime;				// initialize
		else if((thisFtime - lastFtime) >= autoFlush) {
			if(debug) System.err.println("addData autoFlush at: "+thisFtime+" ********************************");
			flush();
		}
		if(rootTime == 0) rootTime = thisFtime;

/*
		long newTime = fTime;
		if(newTime == 0) newTime = System.currentTimeMillis();
		if((lastFtime!=0) && ((newTime - lastFtime) >= autoFlush)) {
			if(debug) System.err.println("addData autoFlush at: "+thisFtime+" ********************************");
			flush();		// thisFtime used indirectly in flush()
		}
		thisFtime = newTime;
		if(lastFtime == 0) lastFtime = thisFtime;				// initialize
		if(rootTime == 0) rootTime = thisFtime;
*/
		
		if(debug) System.err.println("addData: "+name+", thisFtime: "+thisFtime+", rootTime: "+rootTime+", fTime: "+fTime);

		try {
			ByteArrayOutputStream bstream = blockData.get(name);
			if(bstream == null) {
				bstream = new ByteArrayOutputStream();
				blockData.put(name, bstream);
			}
			bstream.write(bdata);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//------------------------------------------------------------------------------------------------
	// separate parts of putData so blockMode flush can call without being recursive
	
	private synchronized void writeData(long time, String outName, byte[] bdata) throws Exception {
//	private void writeData(long time, String outName, byte[] bdata) throws Exception {		// sync makes remote writes pace slow???

		if(debug) System.err.println("writeData: "+outName+" at time: "+time+", zipFlag: "+zipFlag+", rootTime: "+rootTime);
		try {
			if(zipFlag) {
				if(zos == null) {    			
					destName = destPath + File.separator + rootTime  + ".zip";
					if(debug) System.err.println("create destName: "+destName);
					baos = new ByteArrayOutputStream();
					zos = new ZipOutputStream(baos);
					zos.setLevel(compressLevel);			// 0,1-9: NO_COMPRESSION, BEST_SPEED to BEST_COMPRESSION
				}
//				zos.setLevel(compressLevel);			// 0,1-9: NO_COMPRESSION, BEST_SPEED to BEST_COMPRESSION

				String name = time+"/"+outName;			// always use subfolders in zip files
				ZipEntry entry = new ZipEntry(name);
				try {
					zos.putNextEntry(entry);
				} catch(IOException e) {
					if(debug) System.err.println("zip entry exception: "+e);
					throw e;
				}

				zos.write(bdata); 
				zos.closeEntry();		// note: zip file not written until flush() called

				if(debug) System.err.println("PutZip: "+name+" to: "+destName);
			}
			else {
				// put first putData to rootFolder 
				String dpath;
				//				System.err.println("rootTime: "+rootTime+", time: "+time);
				if(rootTime == time) 		// top-level folder unless new time
					dpath = destPath + File.separator + time;
				else	dpath = destPath + File.separator + rootTime +  File.separator + time;

				new File(dpath).mkdirs();
				destName = dpath + File.separator + outName;
				writeToStream(destName, bdata);
				//				FileOutputStream fos = new FileOutputStream(destName);
				//				fos.write(bdata);
				//				fos.close();

				if(debug) System.err.println("writeData: "+outName+" to: "+destName);
			}
		} catch(Exception e) {
			System.err.println("writeData exception: "+e);
			throw e;
		}
	}

	//------------------------------------------------------------------------------------------------
	// putData:  put data in various forms to (zip) file
	// these handle various binary formats in, everything is written as String format out
	// use putData(byte[]) for raw binary data output
	
	/**
	 * putData as String
	 * @param outName parameter name
	 * @param data parameter data
	 * @throws Exception
	 */
	public void putData(String outName, String data) throws Exception {
		if(blockMode)	addData(outName, data);								// WARNING: blockMode strings don't parse well in CTreader
		else			putData(outName, data.getBytes());
	}
	private void addData(String outName, String data) throws Exception {
		if(blockData.get(outName) != null) 	addData(outName, (","+data).getBytes());	// comma delimit multi-string addData
		else					 			addData(outName, data.getBytes());
	}
	
	/** 
	 * putData as double
	 * @param outName parameter name
	 * @param data parameter data
	 * @throws Exception
	 */
	public void putData(String outName, double data) throws Exception {				
		if(blockMode)	addData(outName, data);		
		else			putData(outName, Double.valueOf(data).toString().getBytes());
	}
	private void addData(String outName, double data) throws Exception {
		String cname = outName;
		if(!cname.endsWith(".f64")) cname += ".f64";		// enforce suffix
		addData(cname, orderedByteArray(8).putDouble(data).array());
	}
	
	/**
	 * putData as float
	 * @param outName parameter name
	 * @param data parameter data
	 * @throws Exception
	 */
	public void putData(String outName, float data) throws Exception {
		if(blockMode)	addData(outName, data);		
		else			putData(outName, Float.valueOf(data).toString().getBytes());
	}
	private void addData(String outName, float data) throws Exception {
		String cname = outName;
		if(!cname.endsWith(".f32")) cname += ".f32";		// enforce suffix
		addData(cname, orderedByteArray(4).putFloat(data).array());
	}
	
	/**
	 * putData as long
	 * @param outName parameter name
	 * @param data parameter data
	 * @throws Exception
	 */
	public void putData(String outName, long data) throws Exception {
		if(blockMode)	addData(outName, data);		
		else			putData(outName, Long.valueOf(data).toString().getBytes());
	}
	private void addData(String outName, long data) throws Exception {
		String cname = outName;
		if(!cname.endsWith(".i64")) cname += ".i64";		// enforce suffix
		addData(cname, orderedByteArray(8).putLong(data).array());
	}
	
	/**
	 * putData as int
	 * @param outName parameter name
	 * @param data parameter data
	 * @throws Exception
	 */
	public void putData(String outName, int data) throws Exception {
		if(blockMode)	addData(outName, data);		
		else			putData(outName, Integer.valueOf(data).toString().getBytes());
	}
	private void addData(String outName, int data) throws Exception {
		String cname = outName;
		if(!cname.endsWith(".i32")) cname += ".i32";		// enforce suffix
		addData(cname, orderedByteArray(4).putInt(data).array());
	}
	
	/**
	 * putData as short
	 * @param outName parameter name
	 * @param data parameter data
	 * @throws Exception
	 */
	public void putData(String outName, short data) throws Exception {
		if(blockMode)	addData(outName, data);		
		else			putData(outName, Integer.valueOf(data).toString().getBytes());
	}
	private void addData(String outName, short data) throws Exception {
		String cname = outName;
		if(!cname.endsWith(".i16")) cname += ".i16";		// enforce suffix
		addData(cname, orderedByteArray(2).putShort(data).array());
	}
	
	private ByteBuffer orderedByteArray(int size) {
		if(byteSwap) return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
		else		 return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
	}
	
	//------------------------------------------------------------------------------------------------
	/**
	 * put disk file to CT (zip) file
	 * @param outName parameter name
	 * @param file File whose contents are to be written
	 * @throws Exception
	 */
	public void putData(String outName, File file) throws Exception {
		byte[] bdata = new byte[(int) file.length()];

		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);				
			int offset = 0;
			int numRead = 0;
			while (offset < bdata.length
					&& (numRead=fis.read(bdata, offset, bdata.length-offset)) >= 0) {
				offset += numRead;
			}
			fis.close();
		} 	
		catch(Exception e) {
			System.err.println("CTwriter putData(File) exception: "+e);
			return;
		}
		finally {
			fis.close();
		}

		putData(outName, bdata);
	}

	//------------------------------------------------------------------------------------------------
	/**
	 *  cleanup.  for now, just flush.
	 */
	
	public void close() {
		try {
			flush();
		} catch(Exception e) {
			System.err.println("Exception on close!");
		}
	}
}
