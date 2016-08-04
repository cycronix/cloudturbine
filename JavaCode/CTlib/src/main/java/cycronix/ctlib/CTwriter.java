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
import java.util.Arrays;
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

	protected boolean zipFlag=true;
	protected boolean blockMode=false;
	protected boolean byteSwap=false;		// false: Intel little-endian, true: Java/network big-endian
	
	private boolean gzipFlag=false;
	private long fTime=0;
	private long rootTime=0;
	private long autoFlush=Long.MAX_VALUE;	// auto-flush subfolder time-delta (msec)
	private long lastFtime=0;
	private long thisFtime=0;
	private long prevFtime=0;				// prior frame time (for autoflush)
	private double trimTime=0.;				// trim delta time (sec relative to last flush)
	private int compressLevel=1;			// 1=best_speed, 9=best_compression
	private boolean timeRelative=true;		// if set, writeData to relative-timestamp subfolders
	private long blockDuration=0;			// if non-zero, blockMode block-duration (msec)
	
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
		trimTime=itrimTime;
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
	 * Set debug mode.  Deprecated, see CTinfo.setDebug()
	 * @param dflag boolean true/false debug mode
	 */
	@Deprecated
	public void setDebug(boolean dflag) {
		CTinfo.setDebug(dflag);
//		debug = dflag;
	}

	/**
	 * Automatically flush data (no-op in non-zipfile mode)
	 * @param iautoflush interval (msec) at which to flush data to new zip file
	 */
	@Deprecated
	public void autoFlush(long iautoflush) {
		if(iautoflush == 0) autoFlush = Long.MAX_VALUE;
		else				autoFlush = iautoflush;						// msec
	}
	
	/** 
	 * Automatically flush data (no-op in non-zipfile mode)
	 * @param iautoflush interval (sec) at which to flush data to new zip file
	 */
	@Deprecated
	public void autoFlush(double iautoflush) {
		autoFlush = (long)(iautoflush * 1000.);			// convert to msec
	}
		
	/**
	 * set whether to use time-relative mode  
	 * @param trflag flag true/false (default: false)
	 */
	public void setTimeRelative(boolean trflag) {
		timeRelative = trflag;
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
		setBlockMode(bflag, false, 0L);
	}
	public void setBlockMode(boolean bflag, boolean zflag) {
		setBlockMode(bflag, zflag, 0L);
	}
	public void setBlockMode(boolean bflag, boolean zflag, double iautoFlush) {
		setBlockMode(bflag, zflag, (long)(iautoFlush * 1000.));
	}
	public void setBlockMode(boolean bflag, boolean zflag, long iautoFlush) {
		blockMode = bflag;
		zipFlag = zflag;
		if(iautoFlush == 0) autoFlush = Long.MAX_VALUE;
		else				autoFlush = iautoFlush;				// msec
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
	 * set whether output files are to be zipped.  
	 * @param zipflag zip files true/false (default: true)
	 */
	@Deprecated
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
		flush(false,0L);
	}
	
	public void flush(boolean gapless) throws IOException {
		flush(gapless,0L);
	}

	// flush(blockTime) for case of single-putData array with non-zero duration
	// Note: blockTime should be time of last point in block, e.g. blockStart + blockInterval*(blockSize-1)/blockSize
	public void flush(long blockTime) throws IOException {
		flush(false,blockTime);
	}

	/**
	 * Write collected entries to .zip file
	 * @param blockTime sets time-interval of multi-point data block.  N/A for single-point data.
	 * @throws IOException
	 */
//	public void flush(boolean gapless) throws IOException {		// sync slows external writes?
	public synchronized void flush(boolean gapless, long blockTime) throws IOException {		
		try {
			long bcount = blockData.size();								// zero-based block counter:
			if(bcount > 1 && blockTime > 0) thisFtime = blockTime;		

			// if data has been queued in blocks, write it out once per channel before normal flush
			for(Entry<String, ByteArrayOutputStream>e: blockData.entrySet()) {		// entry keys are by name; full block per channel per flush
				CTinfo.debugPrint("flush block: "+e.getKey()+" at time: "+thisFtime);
//				writeData(thisFtime, e.getKey(), e.getValue().toByteArray());
				writeData(prevFtime, e.getKey(), e.getValue().toByteArray());	// prior data, prior ftime
			}
			blockData.clear();

			if(zos != null) {		// zip mode writes once per flush; non-zip files written every update
				zos.close();	zos = null;
				writeToStream(destName, baos.toByteArray());
			}
			
			if(trimTime > 0 && rootTime > 0) {			// trim old data (trimTime=0 if ftp Mode)
//				double trim = ((double)rootTime/1000.) - trimTime;			// relative??
				double trim = ((double)System.currentTimeMillis()/1000.) - trimTime;
				CTinfo.debugPrint("trimming at: "+trim);
				dotrim(trim);			
			}

			lastFtime = thisFtime;								// remember last time flushed
			
			// default next folder-rootTime next point for gapless time
			if(gapless && blockMode && (bcount>1) && (thisFtime>rootTime)) {
				long dt = Math.round((double)(thisFtime-rootTime)/(bcount-1));
				rootTime = thisFtime+dt;			
			}
			else			
				rootTime = 0;						// reset to new root folder
			
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
			
			CTinfo.debugPrint("writeToStream: "+fname+", bytes: "+bdata.length);
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
		// TO DO?:  deprecate following, use addData vs putData/blockmode?
		if(blockMode) {				// in block mode, delay putData logic until full queue
			CTinfo.debugPrint("addData at rootTime: "+rootTime+", thisFtime: "+thisFtime);
			addData(outName, bdata);
			return;
		}
		
		// fTime:  manually set time (0 if use autoTime)
		// thisFtime:  this frame-entry time, set to fTime each entry
		// rootTime:  parent folder (zip) time, set to match first add/put frame-entry time
		prevFtime = thisFtime;
		thisFtime = fTime;
		if(thisFtime == 0) thisFtime = System.currentTimeMillis();

		CTinfo.debugPrint("putData: "+outName+", thisFtime: "+thisFtime+", rootTime: "+rootTime+", fTime: "+fTime);

		if(lastFtime == 0) lastFtime = thisFtime;				// initialize
		else if((thisFtime - lastFtime) >= autoFlush) {
			CTinfo.debugPrint("putData autoFlush at: "+thisFtime+" ********************************");
			flush();
		}
		if(rootTime == 0) rootTime = thisFtime;

		writeData(thisFtime, outName, bdata);
	}

	//------------------------------------------------------------------------------------------------
	// queue data for blockMode
	private HashMap<String, ByteArrayOutputStream>blockData = new HashMap<String, ByteArrayOutputStream>();

	private synchronized void addData(String name, byte[] bdata) throws Exception {
		prevFtime = thisFtime;
		thisFtime = fTime;
		if(thisFtime == 0) thisFtime = System.currentTimeMillis();

		if(lastFtime == 0) lastFtime = thisFtime;				// initialize	
		else if((thisFtime - lastFtime) >= autoFlush) {			// autoFlush prior data (at prior thisFtime!)
			CTinfo.debugPrint("addData autoFlush at: "+thisFtime+" ********************************");
			flush();
		}
		if(rootTime == 0) rootTime = thisFtime;
		
		CTinfo.debugPrint("addData: "+name+", thisFtime: "+thisFtime+", rootTime: "+rootTime+", fTime: "+fTime);

		try {
			ByteArrayOutputStream bstream = blockData.get(name);
			if(bstream == null) {
				bstream = new ByteArrayOutputStream();
				blockData.put(name, bstream);
			}
			bstream.write(bdata);		// append blockData into named hashmap byteArray entry
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//------------------------------------------------------------------------------------------------
	// separate parts of putData so blockMode flush can call without being recursive
	
//	private void writeData(long time, String outName, byte[] bdata) throws Exception {		// sync makes remote writes pace slow???
	private synchronized void writeData(long time, String outName, byte[] bdata) throws Exception {
		CTinfo.debugPrint("writeData: "+outName+" at time: "+time+", zipFlag: "+zipFlag+", rootTime: "+rootTime);
		try {
			if(zipFlag) {
				if(zos == null) {    			
					destName = destPath + File.separator + rootTime  + ".zip";
					CTinfo.debugPrint("create destName: "+destName);
					baos = new ByteArrayOutputStream();
					zos = new ZipOutputStream(baos);
					zos.setLevel(compressLevel);			// 0,1-9: NO_COMPRESSION, BEST_SPEED to BEST_COMPRESSION
				}
//				zos.setLevel(compressLevel);			// 0,1-9: NO_COMPRESSION, BEST_SPEED to BEST_COMPRESSION

				if(timeRelative) time = time - rootTime;		// relative timestamps
				
				String name = time+"/"+outName;			// always use subfolders in zip files
				ZipEntry entry = new ZipEntry(name);
				try {
					zos.putNextEntry(entry);
				} catch(IOException e) {
					CTinfo.debugPrint("zip entry exception: "+e);
					throw e;
				}

				zos.write(bdata); 
				zos.closeEntry();		// note: zip file not written until flush() called

				CTinfo.debugPrint("PutZip: "+name+" to: "+destName);
			}
			else {
				// put first putData to rootFolder 
				String dpath;
				//				System.err.println("rootTime: "+rootTime+", time: "+time);
				if(rootTime == time && !timeRelative) {		// top-level folder unless new time?
					dpath = destPath + File.separator + time;
					if(!blockMode) flush();	// absolute, non-zip, non-block data flush to individual top-level time-folders?
				} else	{
					if(timeRelative) time = time - rootTime;		// relative timestamps
					dpath = destPath + File.separator + rootTime +  File.separator + time;
				}

				new File(dpath).mkdirs();
				destName = dpath + File.separator + outName;
				writeToStream(destName, bdata);
				//				FileOutputStream fos = new FileOutputStream(destName);
				//				fos.write(bdata);
				//				fos.close();

				CTinfo.debugPrint("writeData: "+outName+" to: "+destName);
			}
		} catch(Exception e) {
			System.err.println("writeData exception: "+e);
			throw e;
		}
	}

	//------------------------------------------------------------------------------------------------
	// putData:  put data in various forms to (zip) file
	// these handle various binary formats in, non-blockmode data is written as String format out
	// use putData(byte[]) for raw binary data output
	
	/**
	 * putData as String
	 * @param outName parameter name
	 * @param data parameter data
	 * @throws Exception
	 */
	public void putData(String outName, String data) throws Exception {
		if(blockMode)	addData(outName, data);						
		else			putData(outName, data.getBytes());
	}
	private void addData(String outName, String data) throws Exception {
		System.err.println("addData; outName: "+outName+"; blockData: "+blockData.get(outName)+"; data: "+data);
//		if(blockData.get(outName) != null) 	addData(outName, (","+data).getBytes());	// comma delimit multi-string addData
//		else					 			addData(outName, data.getBytes());
		addData(outName,(data+",").getBytes());
	}
	
	/** 
	 * putData as double
	 * @param outName parameter name
	 * @param data parameter data
	 * @throws Exception
	 */
	public void putData(String outName, double data) throws Exception {	
		if(outName.endsWith(".f64")) {			// enforce binary mode
			if(blockMode)	addData(outName, data);		
			else			putData(outName, orderedByteArray(8).putDouble(data).array());
		}
		else {
			long ldata = (long)data;
			if(data==ldata) putData(outName, Long.valueOf(ldata).toString());	// trim trailing zeros
			else			putData(outName, Double.valueOf(data).toString());
		}
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
		if(outName.endsWith(".f32")) {			// enforce binary mode
			if(blockMode)	addData(outName, data);		
			else			putData(outName, orderedByteArray(4).putFloat(data).array());
		}
		else {
			long ldata = (long)data;
			if(data==ldata) putData(outName, Long.valueOf(ldata).toString());	// trim trailing zeros
			else			putData(outName, Float.valueOf(data).toString());
		}
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
		if(outName.endsWith(".i64")) {			// enforce binary mode
			if(blockMode)	addData(outName, data);		
			else			putData(outName, orderedByteArray(8).putLong(data).array());
		}
		else				putData(outName, Long.valueOf(data).toString());

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
		if(outName.endsWith(".i32")) {			// enforce binary mode
				if(blockMode)	addData(outName, data);		
				else			putData(outName, orderedByteArray(4).putInt(data).array());
		}
		else					putData(outName, Integer.valueOf(data).toString());
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
		if(outName.endsWith(".i16")) {			// enforce binary mode
			if(blockMode)	addData(outName, data);		
			else			putData(outName, orderedByteArray(2).putShort(data).array());
		}
		else				putData(outName, Short.valueOf(data).toString());
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
	// do the file trimming
	/**
	 * Trim (delete) files older than spec.  Note this will be done automatically if trimTime provided in constructor.
	 * @param oldTime time point at which to delete older files
	 */
	public boolean dotrim(double oldTime) {
		
		// validity checks (beware unwanted deletes!)
		if(destPath == null || destPath.length() < 6) {
			System.err.println("CTtrim Error, illegal parent folder: "+destPath);
			return false;
		}
		if(oldTime < 1.e9 || oldTime > 1.e12) {
			System.err.println("CTtrim time error, must specify full-seconds time since epoch.  oldTime: "+oldTime);
			return false;
		}
		
		File rootFolder = new File(destPath);
		
		File[] flist = rootFolder.listFiles();
		Arrays.sort(flist);						// make sure sorted 

		for(File file:flist) {
			double ftime = fileTime(file);
//			System.err.println("CTtrim file: "+file.getAbsolutePath()+", ftime: "+ftime+", oldTime: "+oldTime+", diff: "+(ftime-oldTime));
			if(ftime <= 0) continue;				// not a CT time file
			if(ftime >= oldTime) return true;		// all done (sorted)
			System.err.println("CTtrim delete: "+file.getAbsolutePath());
			boolean status = file.delete();		// only trims top-level zip files (need recursive, scary)
			if(!status) System.err.println("CTtrim warning, failed to delete: "+file.getAbsolutePath());
		}
		
		return true;
	}
	
	//------------------------------------------------------------------------------------------------
	//fileTime:  parse time from file name, units: full-seconds (dupe from CTreader, should consolidate)
	private double fileTime(File file) {
		String fname = file.getName();
		if(fname.endsWith(".zip")) {
			fname = fname.split("\\.")[0];		// grab first part
		}
		
		double msec = 1;
		double ftime = 0.;
		if(fname.length() > 10) msec = 1000.;
		try {
			ftime = (double)(Long.parseLong(fname)) / msec;
		} catch(NumberFormatException e) {
			//				System.err.println("warning, bad time format, folder: "+fname);
			ftime = 0.;
		}
		return ftime;
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
