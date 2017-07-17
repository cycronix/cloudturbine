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
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map.Entry;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * A class to read data from CloudTurbine folder/files.
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2014/03/06
 * 
*/

//---------------------------------------------------------------------------------	
//CTreader:  read CloudTurbine files stored by timestamp folders
//adapted from CTread, generalize without DT specific reference
//Matt Miller, Cycronix
//02/18/2014

//---------------------------------------------------------------------------------	

public class CTreader {
	private static String rootFolder = null;
	private CTcrypto ctcrypto=null;		// optional encryption class
	private static boolean readProfile = false;

//---------------------------------------------------------------------------------	
 // constructor for CTread.get() method
	public CTreader() {
		// Use the default root folder
		rootFolder = "CTdata";
	}
	
	public CTreader(String fname) {
		rootFolder = new String(fname);
	}

	public void setFolder(String fname) {
		rootFolder = fname;
	}

	public String getFolder() {
		return rootFolder;
	}

	/**
	 * Set debug mode.  Deprecated, see CTinfo.setDebug()
	 * @param dflag boolean true/false debug mode
	 */
	@Deprecated
	public void setDebug(boolean dflag) {
		CTinfo.setDebug(dflag);
//		debug = dflag;
//		CTinfo.debugPrint("debug: "+debug);
	}
	
	boolean timeOnly=false;
	public void setTimeOnly(boolean tflag) {		// clumsy...  
		timeOnly = tflag;							// doesn't work for blockdata where time is derived from block-interval/points
	}
	
	/**
	 * Set encryption password, none if null.
	 * @param password 
	 */
	public void setPassword(String password) throws Exception {
		ctcrypto = new CTcrypto(password);
	}
	
	/**
	 * Set encryption password, none if null.
	 * @param password			decryption password
	 * @param optionalDecrypt	if true, return non-encrypted data if decryption fails.  Otherwise fail with exception.
	 */
	public void setPassword(String password, boolean optionalDecrypt) throws Exception {
		ctcrypto = new CTcrypto(password, optionalDecrypt);
	}
	
//---------------------------------------------------------------------------------	   
//get:  direct-fetch time+data method
// note:	this uses multi-channel ctmap internally, but only returns one channel (ctdata)
//			retain ctmap as a cache?  prefetch from ctmap, fill in balance from files...
		
	public CTdata getData(String source, String chan, double tget, double tdur, String tmode) throws Exception {
		CTmap ctmap= new CTmap(chan);
		String sourceFolder;

		if(source == null) 	sourceFolder = rootFolder;
		else				sourceFolder = rootFolder+File.separator+source;

		try {
			ctmap = getDataMap(ctmap, sourceFolder, tget, tdur, tmode);		// time units = seconds
		} 
		catch(Exception e) {
			e.printStackTrace();
			System.err.println("CTreader/getData oops, exception: "+e+", ctmap: "+ctmap);
			throw e;
		}

		//		CTdata tdata = ctmap.getTimeData(chan, ctmap.refTime, tdur, tmode);
		CTdata tdata = ctmap.get(chan);		// already trimmed in getDataMap
		return tdata;	
	}
		
//---------------------------------------------------------------------------------	
// timeLimits:  get oldest, newest time limits in one-pass from cache
	
	public double[] timeLimits(String sourceFolder, String chan) throws Exception {
		double[] tlimits = new double[]{0,0};
		boolean newWay = true;
		if(!newWay) {
			tlimits[0] = oldTime(sourceFolder, chan);
			tlimits[1] = newTime(sourceFolder, chan);
			return tlimits;
		}

		String thisChanKey = chan2key(sourceFolder + File.separator + chan);			// this is single channel function

		CTFile[] listOfFiles = CTcache.fileListByChan.get(thisChanKey);				// get existing cached limits
		if(listOfFiles == null || listOfFiles.length==0) 
			listOfFiles = flatFileList(rootFolder + File.separator + sourceFolder, new CTmap(chan), thisChanKey, true);
		
		tlimits[0] = listOfFiles[0].baseTime();							// oldest
//		tlimits[0] = listOfFiles[0].fileTime();							// oldest
		tlimits[1] = listOfFiles[listOfFiles.length-1].fileTime();		// newest
		return tlimits;
	}
	
//---------------------------------------------------------------------------------	
// oldTime:  find oldest time for this source (neglects block-duration)
	
	// NOTE: sourceFolder is NOT full path (ie, isn't prepended by rootFolder)
	public double oldTime(String sourceFolder) {
//		System.err.println("oldTime sourceFolder: "+sourceFolder);
		return oldTime(sourceFolder, (CTmap)null);
	}
	
	// NOTE: sourceFolder is NOT full path (ie, isn't prepended by rootFolder)
	public double oldTime(String sourceFolder, String chan) {
		return oldTime(sourceFolder, new CTmap(chan));
	}
	
	// NOTE: sourceFolder is NOT full path (ie, isn't prepended by rootFolder)
	//       in this method we prepend sourceFolder with rootFolder (if it is defined)
	public double oldTime(String sourceFolder, CTmap ctmap) {
		String sourceFolder_fullpath = sourceFolder;
		if (rootFolder != null) {
			if(sourceFolder.startsWith(File.separator))
					sourceFolder_fullpath = new String(rootFolder + sourceFolder);
			else	sourceFolder_fullpath = new String(rootFolder + File.separator + sourceFolder);
		}
		CTFile basefolder = new CTFile(sourceFolder_fullpath);
		CTFile[] listOfFolders = basefolder.listFiles();
//		System.err.println("oldTime, sfolder_fullpath: "+sourceFolder_fullpath+", listlen: "+listOfFolders.length);
		if(listOfFolders == null) return 0.;
		return oldTime(listOfFolders, ctmap);
	}
	
	// NOTE: Each folder in listOfFolders should already be prepended with the rootFolder
	private double oldTime(CTFile[] listOfFolders, CTmap ctmap) {
		if(listOfFolders == null) return 0.;
		if(!listOfFolders[0].isDirectory() && listOfFolders[0].baseTime()!=0) {
//			CTinfo.debugPrint
//			System.err.println("oldTime, listOfFolders.baseTime: "+listOfFolders[0].baseTime()+", listlen: "+listOfFolders.length+", l0: "+listOfFolders[0].getAbsolutePath());
//			return(listOfFolders[0]).fileTime();  // presume list of files
			return(listOfFolders[0]).baseTime();  // presume list of files
		}

//		return fileTime(listOfFolders[0]);
		double ftime = 0.;		// default is now
		
		for(int idx=0; idx<listOfFolders.length; idx++) {		// FF to actual data
			CTFile[] files = listOfFolders[idx].listFiles();
			if(files == null) continue;
			if(files.length == 0) continue;

			for(int j=0; j<files.length; j++) {	
//				System.err.println("oldTime check file: "+files[j].getPath());
				if(files[j].isDirectory()) 	ftime = oldTime(new CTFile[] {files[j]}, ctmap);	// recurse
				else {
//					System.err.println("fileTime: "+files[j].fileTime()+", baseTime: "+files[j].baseTime());
					if(ctmap == null || listOfFolders[idx].containsFile(ctmap)) ftime = files[j].baseTime();
//					if(ctmap == null || listOfFolders[idx].containsFile(ctmap)) ftime = files[j].fileTime();	// first file is oldest
				}
				if(ftime > 0.) return ftime;
			}
		}
//		System.err.println("oldTime got ftime: "+ftime);
		return ftime;
	}

	// fast oldTime:  crawl down first (oldest) folder branch to bottom file time
	private double oldTime(CTFile baseFolder) {
//		System.err.println("oldTime baseFolder: "+baseFolder.getPath()+", isDir: "+baseFolder.isDirectory());
		if(!baseFolder.isDirectory()) return baseFolder.fileTime();  

		double ftime = 0.;		// default is now
		CTFile[] files = baseFolder.listFiles();
		if(files.length == 0) return baseFolder.fileTime();		// catch empty folder case
		CTFile file = files[0];

		if(new File(file.getAbsolutePath()).isDirectory()) {
//			System.err.println("recurse: "+file.getAbsolutePath());
			ftime = oldTime(file);	// recurse if actual folder
		}
//		if(file.super.isDirectory()) 	ftime = oldTime(file);	// recurse
		else {
//			System.err.println("fileTime: "+file.fileTime()+", baseTime: "+file.baseTime());
			ftime = file.baseTime();
		}
		if(ftime > 0.) return ftime;

//		System.err.println("oldTime got ftime: "+ftime);
		return ftime;
	}

//---------------------------------------------------------------------------------	
// newTime:  find newest time for this source	(neglects block-duration)
//	CTFile newFile = null;
	
	// NOTE: sourceFolder is NOT full path (ie, isn't prepended by rootFolder)
	public double newTime(String sourceFolder) {
//		System.err.println("newTime: "+sourceFolder);
		return newTime(sourceFolder, (CTmap)null);
	}
	
	// NOTE: sourceFolder is NOT full path (ie, isn't prepended by rootFolder)
	public double newTime(String sourceFolder, String chan) {
		return newTime(sourceFolder, new CTmap(chan));
	}
	
	// NOTE: sourceFolder is NOT full path (ie, isn't prepended by rootFolder)
	//       in this method we prepend sourceFolder with rootFolder (if it is defined)
	public double newTime(String sourceFolder, CTmap ctmap) {
		String sourceFolder_fullpath = sourceFolder;
		if (rootFolder != null) {
			if(sourceFolder.startsWith(File.separator))
				sourceFolder_fullpath = new String(rootFolder + sourceFolder);
			else	sourceFolder_fullpath = new String(rootFolder + File.separator + sourceFolder);
		}
		CTFile basefolder = new CTFile(sourceFolder_fullpath);
		CTFile[] listOfFolders = basefolder.listFiles();
		if(listOfFolders == null) return 0.;
		return(newTime(listOfFolders, ctmap));
	}

 	// NOTE: Each folder in listOfFolders should already be prepended with the rootFolder
	private double newTime(CTFile[] listOfFolders, CTmap ctmap) {
		if(listOfFolders == null) return 0.;
		if(!listOfFolders[0].isDirectory() && listOfFolders[0].baseTime()!=0) {
			if(ctmap == null) return(listOfFolders[listOfFolders.length-1]).fileTime();  // presume (prefiltered!) list of files
			else {
				System.err.println("ERROR, unimplemented filtered file list newTime");
				return 0.;				// TO DO:  implement this case when needed
			}
		}
		
		//double ftime = (double)System.currentTimeMillis()/1000.;		// default is now
//		newFile = null;
		double ftime = 0.;
		
		int idx=listOfFolders.length-1;
		for(; idx>=0; idx--) {		// rewind to actual data
			CTFile[] files = listOfFolders[idx].listFiles();
			if(files == null) continue;
			if(files.length == 0) continue;

			int j = files.length-1;
			for(; j>=0; j--) {
				if(files[j].isTFILE()) ftime = files[j].fileTime();		// skip recursion, use the file-as-folder for TFILE
				else if(files[j].isDirectory()) ftime = newTime(new CTFile[] {files[j]}, ctmap);	// recurse
				else {
//					if(ctmap == null || listOfFolders[idx].containsFile(ctmap) || ctmap.checkName(files[j].getName())) ftime = files[j].fileTime();
					if(ctmap == null || ctmap.checkName(files[j].getName())) ftime = files[j].fileTime();		// at this point this is a file not a dir
				}
//				System.err.println("newTime check file: "+files[j].getMyPath()+", isTFILE: "+files[j].isTFILE()+", fileType: "+files[j].fileType+", ftime: "+ftime);
				if(ftime > 0.) return ftime;
			}
//			System.err.println("newTime skip past folder: "+listOfFolders[idx].getAbsolutePath());
		}
		
		return ftime;
	}

	//---------------------------------------------------------------------------------	
	// listChans:  build list of channels all folders under source folder 

	public ArrayList<String> listChans(String sfolder) {
		return listChans(sfolder, false);		// default to fastSearch=false
	}
	
	// fastSearch galumps thru chans if lots
	public ArrayList<String> listChans(String sfolder, boolean fastSearch) {
		long startTime = System.nanoTime();

//		if(sfolder.indexOf(File.separator)<0) {
		if(!sfolder.startsWith(rootFolder)) {
			sfolder = rootFolder + File.separator + sfolder;		// auto-fullpath
//			CTinfo.debugPrint("listChans: adding rootfolder to sfolder: "+sfolder);
		}
		
		CTFile sourceFolder = new CTFile(sfolder);
		ArrayList<String> ChanList = new ArrayList<String>();		// for registration
		ChanList.clear();										// ChanList built in listFiles()
		CTFile[] listOfFiles = sourceFolder.listFiles();
		if(listOfFiles == null || listOfFiles.length < 1) {
			CTinfo.debugPrint("listChans empty source: "+sourceFolder);
			return null;
		}
		CTinfo.debugPrint(readProfile, "listChans for sfolder: "+sfolder+", fastSearch: "+fastSearch+", nfile: "+listOfFiles.length);

		int last = listOfFiles.length-1;
		int expedite = 1;
//		if(fastSearch) expedite=expediteLimit(sfolder, listOfFiles.length, 100);		// segments (was 1000)
//		if(fastSearch) expedite=last;		// fast search: do first and last segment (no, single root time at this level)

		int i=0;
		while(true) {
			CTFile thisFile = listOfFiles[i];
//			CTinfo.debugPrint(readProfile, "listChans, check folder: "+thisFile.getAbsolutePath()+", isDir: "+thisFile.isDirectory());

			if(thisFile.isDirectory() && thisFile.fileTime()!=0) {
//				System.err.println("RootTime: "+i+"/"+last+", file: "+thisFile.getAbsolutePath());
				buildChanList(thisFile, ChanList, fastSearch);			// side effect is to add to ChanList
			}
			if(i>=last) break;
			i+=expedite;
			if(i>last) 	i = last;		// make sure first and last are checked
		} 

		Collections.sort(ChanList);
//		CTinfo.debugPrint(readProfile,"listChans sFolder: "+sfolder+", length: "+ChanList.size()+", time: "+((System.nanoTime()-startTime)/1000000.)+" ms, Memory Used MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024));

		return ChanList;
	}

	//---------------------------------------------------------------------------------	
	// buildChanList:  build list of channels in all folders (private utility of registerChans)
	
	private void buildChanList(CTFile sourceFolder, ArrayList<String>ChanList, boolean fastSearch) {

		CTFile[] listOfFiles = sourceFolder.listFiles();
		if(listOfFiles == null) return;
//		CTinfo.debugPrint("buildChanList, folder: "+sourceFolder+", listOfFiles.length: "+listOfFiles.length);
		
		int expedite = 1;
		if(fastSearch) expedite=expediteLimit(sourceFolder.getName(), listOfFiles.length, 100);		// blocks (was 10000)
		
		int i=0;
		int last = listOfFiles.length-1;
		if(last<0) {
			System.err.println("Warning, buildChanList, no files: "+sourceFolder);
			return;
		}
		
		while(true) {
			CTFile thisFile=listOfFiles[i];
//			if(thisFile.isFile() && thisFile.length() <= 0) continue;
			if(thisFile.isDirectory() && thisFile.fileTime()>0) {
//				System.err.println("Folder: "+i+"/"+last+", file: "+thisFile.getAbsolutePath());
//				buildChanList(thisFile,ChanList,fastSearch&&!thisFile.isFileFolder());		// recursive, no expedite channel name list themselves
				buildChanList(thisFile,ChanList,fastSearch);		// recursive, defer isFileFolder check until in folder (is slow)
			}
			else {
				if(thisFile.length() > 0) {
//					System.err.println("File: "+i+"/"+last+", file: "+thisFile.getAbsolutePath());

					// side effect:  build ChanList for registration
					String fname = listOfFiles[i].getName();
					if(ChanList.indexOf(fname) < 0 && !fname.endsWith(".tmp"))  {		// MJM 1/9/17:  skip tmp files
						ChanList.add(fname);	// add if not already there
						expedite = 1;													// no expedite channels within a time folder
						//	CTinfo.debugPrint("Chanlist.add: "+fname);
					}
				}
			}
			
			if(i>=last) break;
			i+=expedite;				// expedite (skip over) time folders
			if(i>last) 	i = last;		// make sure first and last are checked
		}
	}
	
	//---------------------------------------------------------------------------------	
	private int expediteLimit(String sfolder, int flen, int limit) {
		if(flen > 2*limit) {
			CTinfo.debugPrint("Expedited channel list for source "+sfolder+"! nfiles: "+flen+", limited to: "+limit);
			return(flen/limit);			// limit to at most 10 per level ?!
		}
		else			return 1;
	}
	
	//---------------------------------------------------------------------------------	
	// listSources:  build list of sources (folders at top level rootFolder)
/*
	public ArrayList<String> listSources() {
		ArrayList<String> SourceList = new ArrayList<String>();		// for registration
		CTFile[] listOfFolders = new CTFile(rootFolder).listFiles();
		CTinfo.debugPrint("rootFolder: "+rootFolder+", listOfFolders: "+listOfFolders);
		if(listOfFolders == null || listOfFolders.length < 1) return null;

		for(int i=0; i<listOfFolders.length; i++) {
			CTFile thisFolder=listOfFolders[i];
//			System.err.println("listSources: "+thisFolder.getName()+", dir: "+thisFolder.isDirectory());
			if(!thisFolder.isDirectory()) continue;			// this isn't a channel folder
			if(thisFolder.fileTime() > 0.) continue;		// its a timestamped container folder
			SourceList.add(listOfFolders[i].getName());
		}	

//		if(SourceList.size() == 0) SourceList.add(new File(rootFolder).getName());		// root source
		return SourceList;
	}
*/	
	public boolean checkRoot() {
		return new File(rootFolder).exists();
	}
	
	@Deprecated
	public ArrayList<String> listSourcesRecursive() throws IOException {
		return listSources();
	}
	
	public ArrayList<String> listSources() throws IOException {
		final ArrayList<String> SourceList = new ArrayList<String>();		// for registration
		final Path rootPath = new CTFile(rootFolder).toPath();
		final int nroot = rootPath.getNameCount();
		EnumSet<FileVisitOption> opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);	// follow symbolic links

		// walk folders under rootPath (ignore root level files)
		File listFile[] = rootPath.toFile().listFiles();
		if(listFile == null) {
			return new ArrayList<String>();		// return empty list
//			throw new IOException("listSources, no such folder: "+rootPath);
		}
		
		for(File file:listFile) {
			if(file.isDirectory()) {
				Files.walkFileTree(file.toPath(), opts, 10, new SimpleFileVisitor<Path>() {		// max depth 10

					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
							throws IOException
					{
						// System.err.println("walkFileTree file: "+file);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult preVisitDirectory(final Path dir, BasicFileAttributes attrs)
							throws IOException
					{
						for(File file:dir.toFile().listFiles()) {
							//							if(file.isFile()) {			// folder with at least one file is a candidate source
//							if((new CTFile(file.getName()).fileTime())>0.) {		// folder with a "timed" folder/file is a candidate source
							if((new CTFile(file.getPath()).fileTime())>0.) {	// folder with "timed" folder/file is candidate source (getPath to distinguish TFILE's)
								if ( dir.equals( rootPath ) ) return FileVisitResult.CONTINUE;
								int npath = dir.getNameCount();
								String thisPath = dir.getName(npath-1).toString();
								for(int i=(npath-2); i>=nroot; i--) thisPath = dir.getName(i) + File.separator + thisPath; 	// skip rootPath

								if(thisPath.length() >0) {
									SourceList.add(thisPath);		// only add if not a time-number name
								}

								return FileVisitResult.SKIP_SUBTREE;
							} 
						}
						return FileVisitResult.CONTINUE;			// folder without any files
					}
				});
			}
		}

		Collections.sort(SourceList);
		return SourceList;
	}
    
	//---------------------------------------------------------------------------------	
	// do the file checking and return CTmap channel map of Time-Data
	
	public CTmap getDataMap(CTmap ctmap, String rootfolder, double getftime, double duration, String rmode) throws Exception {
		long startTime = System.nanoTime();
		String thisChanKey = chan2key(rootfolder + File.separator + ctmap.getName(0));			// this is single channel function
		try {
			// get updated list of folders
			CTFile[] oldList = CTcache.fileListByChan.get(thisChanKey);
			boolean fileRefresh = false;
			if(oldList == null || oldList.length==0 || !rmode.equals("absolute") || (getftime+duration) > oldList[oldList.length-1].fileTime()) fileRefresh = true;
			CTinfo.debugPrint(readProfile,"GET getDataMap!, thisChan: "+thisChanKey+", getftime: "+getftime+", duration: "+duration+", rmode: "+rmode+", fileRefresh: "+fileRefresh+", fileListByChan.length: "+((oldList!=null)?oldList.length:0));
			CTFile[] listOfFiles = flatFileList(rootfolder, ctmap, thisChanKey, fileRefresh);
			if(listOfFiles == null || listOfFiles.length < 1) return ctmap;
					
			if(rmode.equals("registration")) {				// handle registration
				System.err.println("unexpected registration request!");
				return ctmap;
			}
			else if(rmode.equals("oldest")) {				// convert relative to absolute time
				getftime = oldTime(listOfFiles, null) + getftime;
				CTinfo.debugPrint("getDataMap, oldTime: "+getftime);
				rmode = "absolute";
			}
			else if(rmode.equals("newest")) {
				getftime = newTime(listOfFiles, null) - duration - getftime;			// listOfFolders filtered, don't waste effort re-filtering ctmap
				CTinfo.debugPrint("newest getftime: "+getftime);
				rmode = "absolute";
			}
			else if(rmode.equals("after")) {
				double newtime = newTime(listOfFiles, null);
				double tdur = newtime - getftime;
				if(tdur > duration) getftime = newtime - duration;		// galump if nec to get most recent duration
				getftime += 0.000001;									// microsecond after (no overlap)
				CTinfo.debugPrint("after duration: "+duration);
				rmode = "absolute";
			}

			int ifound = fileSearch(listOfFiles, getftime);				// found is at or before getftime
			if(rmode.equals("prev")) ifound = ifound - 1;
			
//			System.err.println("FOUND chan: "+thisChan+", ftime: "+getftime+", index: "+found+", size: "+listOfFiles.length+", searchTime-foundTime: "+(getftime-listOfFiles[found].fileTime()));
			if(duration==0 && rmode.equals("absolute")) {
				getFile(listOfFiles[ifound], ctmap);
			}
			else {
//				int istart = found>0?found-1:found;			// ??
				int istart = ifound;
				double endtime = getftime + duration;

				// one-pass, gather list of candidate folders
				for(int i=istart; i<listOfFiles.length; i++) {						// find range of eligible folders 
					CTFile folder = listOfFiles[i];
					if(i>1) {													// after end check
						double priorftime = listOfFiles[i-1].fileTime();		// go 1 past to bracket "next/prev" points in candidate list
						if(priorftime > endtime) break;							// done	
					}
//					CTinfo.debugPrint("CTreader checking folder["+i+"]: "+folder.getMyPath()+", start: "+getftime+", end: "+endtime);
					getFile(folder, ctmap);		// individual file
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}

		// loop thru ctmap chans, prune ctdata to timerange (vs ctreader.getdata, ctplugin.CT2PImap)
//		if(!recurse) 
			ctmap.trim(getftime,  duration, rmode);	
		
//		CTinfo.debugPrint(readProfile,"GOT getDataMap("+thisChanKey+"), rmode: "+rmode+", time: "+((System.nanoTime()-startTime)/1000000.)+" ms, Memory Used MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024));

		return ctmap;				// last folder
	}
		
	//--------------------------------------------------------------------------------------------------------
	// get data from CTFile
	private long getFile(CTFile file, CTmap cm) throws Exception {
		long hasdata = 0;

		if(file.isFile()) {
			String fileName =  file.getName();
			if(!cm.checkName(fileName)) return 0;		// not a match 
			byte[] data = null;
			boolean getdata = !timeOnly || !fileName.toLowerCase().endsWith(".jpg");		// timeOnly only works for images at this point
			if(getdata) data = file.read();

			if(ctcrypto!=null &&  (data != null && data.length>0)) { 
				try { data = ctcrypto.decrypt(data); } catch(Exception ee) {
					System.err.println("WARNING:  could not decrypt: "+fileName);
					throw ee;
				}
			}

			if(timeOnly || (data != null && data.length>0)) { 
				if(file.isTFILE()) fileName = file.getName();
				cm.add(fileName, new CTdata(file.fileTime(), data, file));			// squirrel away CTfile ref for timerange info??
				if(data != null) hasdata+=data.length;
				long dlen = data!=null?data.length:0;
//				CTinfo.debugPrint("Put file: "+file.getPath()+", size: "+dlen+", from zipFile: "+file.getMyZipFile()+", ftime: "+file.fileTime());
			}
		} 
		return hasdata;
	}
	
	//--------------------------------------------------------------------------------------------------------
	// flattenFolders:  make list of time/folder as one-deep list
	
	private class TimeFolder implements Comparable<TimeFolder> {
		public CTFile folderFile;
		public double folderTime;
		
		public TimeFolder(CTFile file, double time) {
			folderFile = file;
			folderTime = time;
		};
		
		@Override
		public int compareTo(TimeFolder compareFile) {
			return ((this.folderTime > compareFile.folderTime) ? 1 : -1);
		}
	}
	
	//--------------------------------------------------------------------------------------------------------
	
//	private synchronized CTFile[] flatFileList(CTFile baseFolder, CTmap ictmap, String thisChan, boolean fileRefresh) throws Exception {
	// beware: this will multi-thread
	private CTFile[] flatFileList(String baseFolder, CTmap ictmap, String chanKey, boolean fileRefresh) throws Exception {
//		long startTime = System.nanoTime();

		CTFile[] cachedList = CTcache.fileListByChan.get(chanKey);
//		CTinfo.debugPrint(readProfile,"flatFileList, refresh: "+fileRefresh+", chanKey: "+chanKey+", OldList.size: "+((cachedList==null)?(0):(cachedList.length)));

		final ArrayList<TimeFolder>fflist = new ArrayList<TimeFolder>();
		final CTmap ctmap = ictmap;
		double iendTime = 0.;
		if(cachedList != null && cachedList.length>0) {
			iendTime = cachedList[cachedList.length-1].fileTime();
		}
		final double endTime = iendTime;

		// initially build full flatFileList, subsequent calls update it by adding to oldList
				
		if(fileRefresh) {
			CTFile[] listOfFolders = new CTFile(baseFolder).listFiles();
			if(listOfFolders==null) return null;
			
			// trim oldest if nec
//			double oldestTime = listOfFolders[0].fileTime();      	    // ref oldTime is oldest of any chan
			double oldestTime = oldTime(listOfFolders[0]);			// this at least skips containsFile call, any chan oldest
//			System.err.println("oldestTime: "+oldestTime+", list0: "+(oldList!=null?oldList[0].fileTime():0));
			if(cachedList != null && cachedList.length > 1 && (oldestTime > cachedList[0].fileTime())) {
				int ichk;
				for(ichk=0; ichk<cachedList.length; ichk++) {
					if(oldestTime <= cachedList[ichk].fileTime()) break;
				}
				if(ichk>0) {
//					System.err.println("oldTRIM "+ctmap.getName(0)+", from: "+oldList.length+", by N: "+ichk+", oldestTime: "+oldestTime+", oldList[0]: "+oldList[0].fileTime());
					CTFile[] tmpList = new CTFile[cachedList.length - ichk];
					for(int j=ichk,k=0; j<cachedList.length; j++,k++) tmpList[k] = cachedList[j];	// salvage old cache > updated oldestTime
					cachedList = tmpList;
					CTcache.fileListByChan.put(chanKey, cachedList);
				}
			};
			
//			long t1 = System.nanoTime();
			CTfileList(listOfFolders, fflist, endTime, ctmap);			// add any new files to end of list
						
//			CTinfo.debugPrint(readProfile,"CTfileList chan: "+chanKey+", dt: "+((System.nanoTime()-t1)/1000000.));
		}
		
//		System.err.println("flatFileList time: "+((System.nanoTime()-startTime)/1000000.)+" ms, New Points: "+fflist.size());
//		System.err.println("new ffllist.size: "+fflist.size()+", cacheList.size: "+((cachedList==null)?0:cachedList.length));
		if(fflist.size()==0) return cachedList;		// nothing new, save some work
		
		Collections.sort(fflist);
		CTFile[] ffarray;
		if(cachedList != null) {
			ffarray = new CTFile[fflist.size() + cachedList.length];
			for(int i=0; i<cachedList.length; i++) ffarray[i] = cachedList[i];
			for(int i=0, j=cachedList.length; i<fflist.size(); i++,j++) ffarray[j] = fflist.get(i).folderFile;		// concatenate old + new
		}
		else {
			ffarray = new CTFile[fflist.size()];
			for(int i=0; i<ffarray.length; i++) {
				ffarray[i] = fflist.get(i).folderFile;
//				System.err.println("i: "+i+", file: "+fflist.get(i).folderFile.getPath()+", time: "+(long)fflist.get(i).folderTime);
			}
		}
		
//		CTinfo.debugPrint(readProfile,"flatFileList update, chankey: "+chanKey+", listLen: "+ffarray.length+", time: "+((System.nanoTime()-startTime)/1000000.)+" ms, Memory Used MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024));
		CTcache.fileListByChan.put(chanKey, ffarray);
		
		return ffarray;
	}
	
	//--------------------------------------------------------------------------------------------------------
	// CTfileList:  custom walkFileTree but skipping over subfolders
	
	private boolean CTfileList(CTFile[] listOfFolders, ArrayList<TimeFolder>fflist, double endTime, CTmap ctmap) {
		return CTfileList(listOfFolders, fflist, endTime, ctmap, 0);
	}
	
	private boolean CTfileList(CTFile[] listOfFolders, ArrayList<TimeFolder>fflist, double endTime, CTmap ctmap, int recursionLevel) {
		if(listOfFolders == null) return false;					// fire-wall
		long startTime = System.nanoTime();
		boolean pdebug=false;
//		if(recursionLevel <= 2) pdebug = true;
		
		if(pdebug) System.err.println("CTfileList, listOfFolders.length: "+listOfFolders.length);
		for(int i=listOfFolders.length-1; i>=0; i--) {			// reverse search thru sorted folder list
			if(pdebug) System.err.println("i: "+i+", time1: "+((System.nanoTime()-startTime)/1000000.));
			CTFile folder = listOfFolders[i];
			
			if(folder.isDirectory()) {
				if(pdebug) System.err.println("time2a: "+((System.nanoTime()-startTime)/1000000.));

				CTFile[] listOfFiles = folder.listFiles();
				if(pdebug) System.err.println("CTfileList recurse into folder:"+folder.getName()+", time: "+((System.nanoTime()-startTime)/1000000.));

				if(!CTfileList(listOfFiles, fflist, endTime, ctmap, recursionLevel+1)) {
					if(pdebug) System.err.println("pop out of subfolder, time: "+((System.nanoTime()-startTime)/1000000.));
					return false;		// pop recursion stack
				}
			}
			else {
				if(pdebug) System.err.println("time2b: "+((System.nanoTime()-startTime)/1000000.));

				String fname = folder.getName();
				if(!ctmap.checkName(fname)) continue;			// cache every channel here vs skip?

				if(pdebug) System.err.println("time3: "+((System.nanoTime()-startTime)/1000000.));
				double ftime = folder.fileTime();			

				if(ftime>endTime) {		// time here may equal end of prior block???
					fflist.add(new TimeFolder(folder,ftime));		
					if(pdebug) System.err.println("CTfileList GOT time: "+ftime+", endTime: "+endTime+", checkTime: "+(ftime-endTime)+", newLen: "+fflist.size()+", file: "+folder.getPath()+", fflist.size: "+fflist.size());
				}
				else {
					if(pdebug) System.err.println("CTfileList SKIP time: "+ftime+", endTime: "+endTime+", checkTime: "+(ftime-endTime)+", newLen: "+fflist.size()+", file: "+folder.getPath());
					return false;
				}
				if(pdebug) System.err.println("time4: "+((System.nanoTime()-startTime)/1000000.));

				return true;			// keep going
			}
		}
		return true;					// all done, success
	}

	//--------------------------------------------------------------------------------------------------------
	// binary search for file at or before timestamp
    private int fileSearch(CTFile[] fileList, double ftime) {
        int start = 0;
        int end = fileList.length - 1;
        int mid=0;
        while (start < end) {
            mid = (start + end) / 2;
//        	System.err.println("start: "+start+", end: "+end+", mid: "+mid+", ftime: "+ftime+", thistime: "+fileList[mid].fileTime()+", thisfile: "+fileList[mid].getPath());

            if (ftime == fileList[mid].fileTime()) {
                return mid;
            }
            if (ftime < fileList[mid].fileTime()) {
                end = mid - 1;

            } else {
            	start = mid + 1;
            }
        }
        
        int ifound = start;
        while(ifound > 0 && (ftime<fileList[ifound].fileTime())) ifound--;				// make sure at or BEFORE
        
//        System.err.println("found: "+ifound+", start: "+start+", end: "+end+", mid: "+mid+", searchtime: "+ftime+", gottime-ftime: "+(fileList[mid].fileTime()-ftime));
        return ifound;
    }
	
	//--------------------------------------------------------------------------------------------------------
//	private HashMap<String,CTFile[]> fileListByChan = new HashMap<String,CTFile[]>();				// provide way to reset?
//	private putFileListByChan(String chan)
	// this should be its own Class with oldest, newest, etc properties...
	// convert chan path to reliable key
    private String chan2key(String chan) {
    	String key = chan.replace(rootFolder, "");				// strip leading rootFolder if present
    	key = key.replace(File.separator, "/");
    	key = key.replace("//", "/");
    	if(key.startsWith("/")) key = key.substring(1);
//    	System.err.println("chan2key, chan: "+chan+", key: "+key);
    	return key;
    }
}



