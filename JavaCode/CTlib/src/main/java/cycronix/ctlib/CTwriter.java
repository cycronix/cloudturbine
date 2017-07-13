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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
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

//---------------------------------------------------------------------------------	
//CTwriter:  write data to CloudTurbine-format folders, with zip-option
//Matt Miller, Cycronix
//02/11/2014

public class CTwriter {

	private String destPath=null;
	private ZipOutputStream zos = null;
	private ByteArrayOutputStream baos = null;
	protected String destName;

	protected boolean zipFlag=false;		// zip data files
	protected boolean packFlag=false;		// pack multiple puts of numeric data into bigger blocks
	protected boolean byteSwap=false;		// false: Intel little-endian, true: Java/network big-endian
	
	private boolean gzipFlag=false;			// gzip mode passes each file through gzip on output for "solid" compression
	private long fTime=0;
	private long blockTime=0;				// parent (zip) folder time, sets start of block interval
	private long prevblockTime=0;
	
	private long sourceTime=0;				// top-level absolute time for entire source (msec)
	private long segmentTime=0;				// segment time, block subfolders relative to this.  
	private String baseTimeStr="";			// string representation of sourceTime/segmentTime
	private long blocksPerSegment=0;		// auto new segment every so many blocks (default=off)
	private long blockCount=0;				// keep track of flushes
	private boolean initBaseTime = true;	// first-time startup init flag
	private boolean packFlush=false;		// pack single flush into top level zip
	
	private long autoFlush=Long.MAX_VALUE;	// auto-flush subfolder time-delta (msec)
	private boolean asyncFlush=false;		// async timer to flush each block 
	
	private long lastFtime=0;
	private long thisFtime=0;
	private double trimTime=0.;				// trim delta time (sec relative to last flush)
	private int compressLevel=1;			// 1=best_speed, 9=best_compression
	private boolean timeRelative=true;		// if set, writeData to relative-timestamp subfolders
	private CTcrypto ctcrypto=null;		// optional encryption class
	
	private long timeFactor=1000;		// convert double to long time units (e.g. 1000 ~ msec, 1000000 ~ usec)
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
	 * Set high-resolution timestamps (usec)
	 * @param hiResTime 
	 */
	public void setHiResTime(boolean hiResTime) {
		if(hiResTime) 	timeFactor = 1000000;
		else			timeFactor = 1000;
	}
	
	/**
	 * Set encryption password, none if null.
	 * @param password 
	 */
	public void setPassword(String password) throws Exception {
		ctcrypto = new CTcrypto(password);
	}
	
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
	 * Automatically create data segments.
	 * @param iblocksPerSegment number of blocks (flushes) per segment, 0 means no segments
	 */
	public void autoSegment(long iblocksPerSegment) {
		blocksPerSegment = iblocksPerSegment;
	}
	
	/** 
	 * Automatically flush data blocks.  See {@link #autoFlush(long,boolean)}.
	 * @param timePerBlock interval (sec) at which to flush data to new zip file
	 */
	public void autoFlush(double timePerBlock) {
		autoFlush((long)(timePerBlock*timeFactor), false);
	}
	
	/**
	 * Automatically flush data blocks.  See {@link #autoFlush(long,boolean)}.
	 * @param timePerBlock interval (msec) at which to flush data to new zip file
	 */
	public void autoFlush(double timePerBlock, boolean asyncFlag) {
		autoFlush((long)(timePerBlock*timeFactor), asyncFlag);
	}
	
	/**
	 * Automatically flush data blocks.  See {@link #autoFlush(long,boolean)}.
	 * @param timePerBlock interval (msec) at which to flush data to new zip file
	 */
	public void autoFlush(long timePerBlock) {
		autoFlush(timePerBlock, false);
	}
	
//	Timer flushTimer = new Timer(true);
	/** 
	 * Set auto-flush data to disk.
	 * <p>
	 * Note that async flush runs off a system timer, 
	 * otherwise flushes occur at each putData whenever given time exceeds autoFlush interval.
	 * In non-async mode, there can be a partial-block waiting to be flushed until a cleanup flush() is called.
	 * <p>Recommend {@link #putData(Map)} in multi-channel async mode to ensure autoFlush doesn't split groups of channels.
	 * 
	 * @param timePerBlock Time (msec) between automatically flushed blocks
	 * @param asyncFlag True/False set asynchronous timer flush.  
	 */
	public void autoFlush(long timePerBlock, boolean asyncFlag) {
		asyncFlush = asyncFlag;
		autoFlush = timePerBlock;

		if(timePerBlock == 0 /* || asyncFlag */) 	
				autoFlush = Long.MAX_VALUE;
		else	autoFlush = timePerBlock;				// msec
		
		CTinfo.debugPrint("autoFlush set, timePerBlock: "+timePerBlock+", asyncFlag: "+asyncFlag+", autoFlush: "+autoFlush);
	}
	
	/**
	 * set whether to use time-relative mode  
	 * @param trflag flag true/false (default: true)
	 */
	public void setTimeRelative(boolean trflag) {
		timeRelative = trflag;
	}
	
	/**
	 * Set packed/zipped block mode options.
	 * <p>  
	 * A "block" is comprised of all putData (per channel) up to each <code>flush()</code>. 
	 * The point times in a block are linearly interpolated start-to-end over block.
	 * <p>
	 * The start time of a block is the <code>setTime()</code> at the first <code>putData()</code>.
	 * The end time of a block is the <code>setTime()</code> at the <code>flush()</code>.
	 * <p>
	 * @param pflag true/false set pack mode (default: false)
	 */
	public void setBlockMode(boolean pflag) {
		setBlockMode(pflag, true);
	}
	public void setBlockMode(boolean pflag, boolean zflag) {
		packFlag = pflag;
		zipFlag = zflag;
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
		if(gzipflag) zipFlag = true;			// enforce zip first then gzip for file.zip.gz
	}
	//------------------------------------------------------------------------------------------------
	// various options (too many?) to set compression mode
	/**
	 * set whether output files are to be zipped.  
	 * @param zipflag zip files true/false (default: true)
	 */
	public void setZipMode(boolean zipflag) {
		zipFlag = zipflag;
	}
	
	/** 
	 * set output files to be zipped setting compression level
	 * @param zipflag zip mode true/false (default: true)
	 * @param clevel zip compression level (0=none, 1-fastest, 9-max, 10=auto-gzip).  default=1
	 */
	public void setZipMode(boolean zipflag, int clevel) {
		if(compressLevel == 10) setZipMode(zipflag, 0, true);
		else					setZipMode(zipflag, clevel, false);
	}
	
	private void setZipMode(boolean zipflag, int clevel, boolean gzipflag) {
		zipFlag = zipflag;
		compressLevel = clevel;
		gzipFlag = gzipflag;
		if(gzipflag) zipFlag = true;			// enforce zip first then gzip for file.zip.gz
	}
	
	//------------------------------------------------------------------------------------------------
	// segmentTime:  sets new time segment 
	private void segmentTime(long iSegmentTime) {
		if(initBaseTime) sourceTime = iSegmentTime;		// one-time set overall source time
		segmentTime = iSegmentTime;
		baseTimeStr = "";
		if(blocksPerSegment <= 0) baseTimeStr = File.separator+sourceTime;		// disable segments
		else {
			if(timeRelative) baseTimeStr = File.separator+sourceTime+File.separator+(segmentTime-sourceTime);
			else			 baseTimeStr = File.separator+sourceTime+File.separator+segmentTime;
		}
			 	
//		rebaseFlag = false;					// defer taking effect until full-block on flush
		initBaseTime = false;
		CTinfo.debugPrint("segmentTime: baseTimeStr: "+baseTimeStr+", blocksPerSegment: "+blocksPerSegment);
	}
	
	/**
	 * Force a new time segment
	 */
	public void newSegment() {
		segmentTime(blockTime);
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
		setTime(System.currentTimeMillis() * (timeFactor/1000));	// default
	}

	/**
	 * Set time for subsequent putData().
	 * @param ftime time (msec)
	 */
	public void setTime(long ftime) {
		fTime = ftime;	
		if(initBaseTime) segmentTime(ftime);				// ensure baseTime initialized

		if(blockTime == 0) blockTime = ftime;				// initialize block time in setTime? (vs putData)
	}

	/**
	 * Set time for subsequent putData().
	 * @param ftime time (sec)
	 */
	public void setTime(double ftime) {
		setTime((long)(ftime * timeFactor));				// convert to msec or usec (eventually carry thru sec)
	}

	//------------------------------------------------------------------------------------------------	
   /*
    *
    *   Date      By	Description
    * MM/DD/YYYY
    * ----------  --	-----------
    * 02/06/2014  MJM	Created.
    */
	/**
	 * Write collected entries to block (compacted) file
	 * @throws IOException
	 */
	
	public void flush(boolean gapless) throws IOException {
		flush();									// regular flush
		
		if(gapless) {								// set next block time to match this frame time
			if(thisFtime>blockTime) {
				long bcount = blockData.size();		// zero-based block counter:

				// set blockTime for next block to match end time of current block
				if(bcount > 1) {
					long dt = Math.round((double)(thisFtime-blockTime)/(bcount-1));
					blockTime = thisFtime+dt;
				}
				else	blockTime = thisFtime;
			}
			else	blockTime = 0;					// reset to new block folder	
		}
	}

	// preflush is not needed, simply do an initial setTime() to establish blockTime
/*
	public void preflush(long time) {
		blockTime = time;
		if(initBaseTime) newSegment();
	}
*/
	
	public synchronized void flush() throws IOException {
		try {	
			// if data has been queued in blocks, write it out once per channel before normal flush
			for(Entry<String, ByteArrayOutputStream>e: blockData.entrySet()) {	// entry keys are by name; full block per channel per flush
				long thisTime = timeData.get(e.getKey());			// per packed-channel end-time
				CTinfo.debugPrint("flush block: "+e.getKey()+" at time: "+thisTime);
				writeData(thisTime, e.getKey(), e.getValue().toByteArray());	// prior data, prior ftime
			}
			blockData.clear(); timeData.clear();
			
			if(zos != null) {		// zip mode writes once per flush; non-zip files were written every update
				zos.close();	zos = null;
				if(packFlush) 	{
					destName = destPath + baseTimeStr + ".zip";		// write all data to single zip
					packFlush = false;								// careful:  can't packFlush same source more than once!
				}
				else {
					if(timeRelative) destName = destPath + baseTimeStr + File.separator + (blockTime-segmentTime)  + ".zip";
					else			 destName = destPath + baseTimeStr + File.separator + blockTime  + ".zip";
				}
				CTinfo.debugPrint("flush to destName: "+destName);

				if(baos.size() > 0) writeToStream(destName, baos.toByteArray());	
			}
			
			if(trimTime > 0 && blockTime > 0) {					// trim old data (trimTime=0 if ftp Mode)
				double trim = blockTime/(double)timeFactor - trimTime;	// relative to putData time, 
				// use blockTime (less than thisFtime) as trim will only look at old block-times
				CTinfo.debugPrint("trimming at: "+trim);
				dotrim(trim);			
			}

			lastFtime = thisFtime;				// remember last time flushed	
			blockTime = 0;						// reset to new block folder	
		} 
		catch(Exception e) { 
			System.err.println("flush failed"); 
			e.printStackTrace(); 
			throw new IOException("CT flush failed: " + e.getMessage());
		} 
	}
	
	//------------------------------------------------------------------------------------------------
	/*
    *
    *   Date      By	Description
    * MM/DD/YYYY
    * ----------  --	-----------
    * 09/01/2016  MJM	Created.
    */
	/**
	 * Special case:  pack baseTime/0/0.zip to baseTime.zip for single-block data
	 * @throws IOException
	 */	
	public void packFlush() throws IOException {
		if(!zipFlag /* || !packMode */) throw new IOException("packFlush only works for block/zip files");
		packFlush = true;
		flush();			// flush and close (no more writes this source!)
	}
	
	//------------------------------------------------------------------------------------------------

	// separate writeToStream from flush():  allows CTftp to over-ride this method for non-file writes
	protected void writeToStream(String fname, byte[] bdata) throws IOException {
		try {
			new File(fname).getParentFile().mkdirs();  // mkdir before every file write
//			System.err.println("writeToStream: "+fname);
			
			if(gzipFlag) {		// special case, gzip(zip).  won't yet work with FTP 
				OutputStream fos = new FileOutputStream(new File(fname+".gz"));
				GZIPOutputStream bos = new GZIPOutputStream(new BufferedOutputStream(fos));
				bos.write(bdata);
				bos.close();
			}
			else {				// conventional file (zip or not)
				FileOutputStream fos = new FileOutputStream(fname, false);		// append if there?	
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
		// TO DO?:  deprecate following, use addData vs putData/packMode?
//		if(packMode) {				// in block mode, delay putData logic until full queue
		if(packFlag && CTinfo.wordSize(outName)>1) {	// don't merge byteArrays (keep intact)
			CTinfo.debugPrint("addData at blockTime: "+blockTime+", thisFtime: "+thisFtime);
			addData(outName, bdata);
			return;
		}
		
		// fTime:  manually set time (0 if use autoTime)
		// thisFtime:  this frame-entry time, set to fTime each entry
		// blockTime:  parent folder (zip) time, set to match first add/put frame-entry time
		thisFtime = fTime;
		if(thisFtime == 0) {
			thisFtime = System.currentTimeMillis() * (timeFactor/1000);
			if(initBaseTime) segmentTime(thisFtime);		// catch alternate initialization
		}

		CTinfo.debugPrint("putData: "+outName+", thisFtime: "+thisFtime+", blockTime: "+blockTime+", fTime: "+fTime+", autoFlush: "+autoFlush);

		if(lastFtime == 0) lastFtime = thisFtime;				// initialize
		else if(!asyncFlush && ((thisFtime - lastFtime) >= autoFlush)) {
			CTinfo.debugPrint("putData autoFlush at: "+thisFtime+" ********************************");
			flush();
		}
		if(blockTime == 0) blockTime = thisFtime;

		writeData(thisFtime, outName, bdata);
	}

	//------------------------------------------------------------------------------------------------
	/** Map form of putData.  Does synchronized putData of all map entries; good for ensuring 
	 * no async autoFlush splits channel group between zip files.
	 * @param cmap Map of name,value pairs.  Value class determined via instanceof operator.
	 */
	
	public synchronized void putData(Map<String,Object>cmap) throws Exception {
		for (Map.Entry<String, Object> entry : cmap.entrySet()) {		// loop for all map entries
			Object data = entry.getValue();
			if		(data instanceof Integer) 	putData(entry.getKey(), (Integer)data);
			else if	(data instanceof Float) 	putData(entry.getKey(), (Float)data);
			else if	(data instanceof Double) 	putData(entry.getKey(), (Double)data);
			else if	(data instanceof Short) 	putData(entry.getKey(), (Short)data);
			else if	(data instanceof String) 	putData(entry.getKey(), (String)data);
			else if	(data instanceof Long) 		putData(entry.getKey(), (Long)data);
			else if	(data instanceof Character) putData(entry.getKey(), (Character)data);
			else if	(data instanceof byte[]) 	putData(entry.getKey(), (byte[])data);
			else throw new IOException("putData: unrecognized object type: "+entry.getKey());
		}
	}
	
	//------------------------------------------------------------------------------------------------
	// queue data for packMode
	private HashMap<String, ByteArrayOutputStream>blockData = new HashMap<String, ByteArrayOutputStream>();
	private HashMap<String, Long>timeData = new HashMap<String, Long>();

	private synchronized void addData(String name, byte[] bdata) throws Exception {
		thisFtime = fTime;
		if(thisFtime == 0) thisFtime = System.currentTimeMillis() * (timeFactor/1000);

		if(lastFtime == 0) lastFtime = thisFtime;				// initialize	
		else if(!asyncFlush && ((thisFtime - lastFtime) >= autoFlush)) {			// autoFlush prior data (at prior thisFtime!)
			CTinfo.debugPrint("addData autoFlush at: "+thisFtime+" ********************************");
			flush();
		}
		if(blockTime == 0) blockTime = thisFtime;
		
		CTinfo.debugPrint("addData: "+name+", thisFtime: "+thisFtime+", blockTime: "+blockTime+", fTime: "+fTime);

		try {
			ByteArrayOutputStream bstream = blockData.get(name);
			if(bstream == null) {
				bstream = new ByteArrayOutputStream();
				blockData.put(name, bstream);
			}
			bstream.write(bdata);		// append blockData into named hashmap byteArray entry
			timeData.put(name,  thisFtime);		// prevFtime?
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//------------------------------------------------------------------------------------------------
	// separate parts of putData so packMode flush can call without being recursive
	Timer flushTimer = null;
	
//	private void writeData(long time, String outName, byte[] bdata) throws Exception {		// sync makes remote writes pace slow???
	private synchronized void writeData(long time, String outName, byte[] bdata) throws Exception {
		
		CTinfo.debugPrint("writeData: "+outName+" at time: "+time+", zipFlag: "+zipFlag+", blockTime: "+blockTime);
		
		if(time<blockTime) {
			throw new IOException("OOPS negative CT time (dropping): "+time+", blockTime: "+blockTime);
//			return;			// drop it with warning (no exception?)
		}
		
		try {
			// block/segment logic:
			if(blockTime > prevblockTime) {
				CTinfo.debugPrint("writeData; blockCount: "+blockCount+", blocksPerSegment: "+blocksPerSegment);
				if(initBaseTime) segmentTime(blockTime);		// catch alternate initialization
				else if((blocksPerSegment>0) && ((blockCount%blocksPerSegment)==0)) 	// reset baseTime per segmentInterval
					segmentTime(blockTime);
				prevblockTime = blockTime;
				blockCount++;
				
//				System.err.println("asyncFlush: "+asyncFlush+", autoFlush: "+autoFlush);
				if(asyncFlush) {	// async flush autoFlush_msec after each new block
					if(flushTimer!=null) flushTimer.cancel();			// cancel any overlap
					flushTimer = new Timer(true);
					flushTimer.schedule(
					    new TimerTask() {
					      public void run() { 
					    	  CTinfo.debugPrint("flushTimer!"); 
					    	  try{ flush(); } catch(Exception e){}; }
					    }, autoFlush);
				}
			}
			
//			if(todoBaseTime) setBaseTime(time);				// ensure baseTime initialized
			
			// new mode:  queue time, data arrays.  all time calcs and writes to disk on flush...
			
			if(ctcrypto!=null) {
				try { bdata = ctcrypto.encrypt(bdata);	} catch(Exception ee) {
					System.err.println("WARNING:  could not encrypt: "+outName);
					throw ee;
				}
			}
			
			//  zip mode:  queue up data in ZipOutputStream
			if(zipFlag) {
				if(zos == null) {    			
					baos = new ByteArrayOutputStream();
					zos = new ZipOutputStream(baos);
					zos.setLevel(compressLevel);			// 0,1-9: NO_COMPRESSION, BEST_SPEED to BEST_COMPRESSION
				}
								
				String name = "";
				if(timeRelative) 	name = (time-blockTime) + "/" + outName;	// always use subfolders in zip files
				else				name = time + "/" + outName;
				
				ZipEntry entry = new ZipEntry(name);
				entry.setTime(time / (timeFactor/1000));     	// internal zipentry time; let it match folder-time
				try {
					zos.putNextEntry(entry);
				} catch(IOException e) {
					CTinfo.warnPrint("zip entry exception: "+e);
					return;	
				}
	
				zos.write(bdata); 
				zos.closeEntry();		// note: zip file not written until flush() called

				CTinfo.debugPrint("PutZip: "+name);
			}
			// non zip mode:  write data to outputstream 
			else {
				// put first putData to rootFolder 
				String dpath;

				if(timeRelative) {			// relative timestamps
					dpath = destPath + baseTimeStr + File.separator + (blockTime-segmentTime) +  File.separator + (time - blockTime);
				}
				else {
					dpath = destPath + baseTimeStr + File.separator + blockTime +  File.separator + time;
				}

				destName = dpath + File.separator + outName;
				writeToStream(destName, bdata);
				CTinfo.debugPrint("writeData: "+outName+" to: "+destName);
			}
		} catch(Exception e) {
//			System.err.println("writeData exception: "+e);
//			e.printStackTrace();
			throw e;
		}
	}

	//------------------------------------------------------------------------------------------------
	// putData:  put data in various forms to (zip) file
	// these handle various binary formats in, non-packMode data is written as String format out
	// use putData(byte[]) for raw binary data output
	
	/**
	 * putData as String
	 * @param outName parameter name
	 * @param data parameter data
	 * @throws Exception
	 */
	public void putData(String outName, String data) throws Exception {
		if(packFlag)	addData(outName, data);						
		else			putData(outName, data.getBytes());
	}
	private void addData(String outName, String data) throws Exception {
//		System.err.println("addData; outName: "+outName+"; blockData: "+blockData.get(outName)+"; data: "+data);
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
			if(packFlag)	addData(outName, data);		
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
			if(packFlag)	addData(outName, data);		
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
			if(packFlag)	addData(outName, data);		
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
				if(packFlag)	addData(outName, data);		
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
			if(packFlag)	addData(outName, data);		
			else			putData(outName, orderedByteArray(2).putShort(data).array());
		}
		else				putData(outName, Short.valueOf(data).toString());
	}
	private void addData(String outName, short data) throws Exception {
		String cname = outName;
		if(!cname.endsWith(".i16")) cname += ".i16";		// enforce suffix
		addData(cname, orderedByteArray(2).putShort(data).array());
	}
	
	/**
	 * putData as char
	 * @param outName parameter name
	 * @param data parameter data
	 * @throws Exception
	 */
	public void putData(String outName, char data) throws Exception {
		if(packFlag)	addData(outName, data);			// packed characters
		else			putData(outName, ""+data);		// CSV strings
	}
	private void addData(String outName, char data) throws Exception {
		addData(outName, orderedByteArray(2).putChar(data).array());
	}
	
	// orderedByteArray:  allocate bytebuffer with word order
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
			throw new IOException("CTwriter putData(File) exception: "+e);
//			return;
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
	public boolean dotrim(double oldTime) throws IOException {
		
		// validity checks (beware unwanted deletes!)
		if(destPath == null || destPath.length() < 1) {		// was <6 ?
			throw new IOException("CTtrim Error, illegal parent folder: "+destPath);
		}
		if(oldTime < 1.e9 || oldTime > 1.e12) {
			throw new IOException("CTtrim time error, must specify full-seconds time since epoch.  oldTime: "+oldTime);
		}
		
		File rootFolder = new File(destPath);
		return deleteOldTimes(rootFolder, oldTime);
	}
	
	private boolean deleteOldTimes(File rootFolder, double trimTime) throws IOException {
		final double oldTime = trimTime;

		Path directory = rootFolder.toPath();
		try {
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					double ftime = CTinfo.fileTime(file.toString());
					if(ftime > 0 && ftime < oldTime) {
						CTinfo.debugPrint("delete file: "+file);
						try {
							Files.delete(file);
						} catch(IOException e) {
							throw new IOException("Failed to delete file: "+file);
//							System.err.println("Failed to delete file: "+file);
						}
					}
//					else System.err.println("leave file: "+file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					if(dir.toFile().listFiles().length == 0) {		// only delete empty dirs
						CTinfo.debugPrint("delete dir: "+dir);
						try {
							Files.delete(dir);
						} catch(IOException e) {
							throw new IOException("Failed to delete dir: "+dir);
						}
					}
//					else System.err.println("leave non-empty dir: "+dir);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch(IOException e) {
			throw new IOException("Exception on deleteOldTimes folder: "+rootFolder);
//			return false;
		}

		return true;
	}

	//------------------------------------------------------------------------------------------------
	/**
	 *  cleanup.  for now, just flush.
	 */
	
	public void close() {
		try {
			flush();
			autoFlush(0,false);		// turn off async flush
		} catch(Exception e) {
			System.err.println("Exception on close!");
		}
	}
}
