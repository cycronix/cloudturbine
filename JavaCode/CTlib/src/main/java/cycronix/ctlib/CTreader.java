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
	
//	private Map<String, CTFile> CTFileMap = new TreeMap<String, CTFile>();		// cache
	
	public CTdata getData(String source, String chan, double tget, double tdur, String tmode) throws Exception {
		CTmap ctmap= new CTmap(chan);
		CTFile sourceFolder;

		if(source == null) 	sourceFolder = new CTFile(rootFolder);
		else				sourceFolder = new CTFile(rootFolder+File.separator+source);

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
		
		String sourceFolder_fullpath = sourceFolder;
		if (rootFolder != null) sourceFolder_fullpath = new String(rootFolder + File.separator + sourceFolder);
		String thisChan = sourceFolder_fullpath + File.separator + chan;			// this is single channel function
		if(!File.separator.equals("/")) thisChan = thisChan.replace(File.separator, "/");
		thisChan = thisChan.replace("//", "/");										// firewall trim double-slash in key
		
//		System.err.println("timeLimits fileListByChan, thisChan: "+thisChan+", rootFolder: "+rootFolder+", sfull: "+sourceFolder_fullpath+", chan: "+chan+", sourceFolder: "+sourceFolder);
		CTFile[] listOfFiles = fileListByChan.get(thisChan);				// get existing cached limits
//		if(listOfFiles!=null)System.err.println("<<<<listOfFiles.length: "+listOfFiles.length+", for chan: "+thisChan);
		if(listOfFiles == null || listOfFiles.length==0) {
			CTFile basefolder = new CTFile(sourceFolder_fullpath);
			listOfFiles = flatFileList(basefolder, new CTmap(chan), thisChan, true);
//			System.err.println(">listOfFiles.length: "+listOfFiles.length);
		}
		
		tlimits[0] = listOfFiles[0].fileTime();							// oldest
		tlimits[1] = listOfFiles[listOfFiles.length-1].fileTime();		// newest
//		System.err.println("timeLimits, chan: "+thisChan+", listOfFiles.length: "+listOfFiles.length+", tlimits[0]: "+tlimits[0]+", tlimits[1]:"+tlimits[1]);
		return tlimits;
	}
	
//---------------------------------------------------------------------------------	
// oldTime:  find oldest time for this source (neglects block-duration)
	
	// NOTE: sourceFolder is NOT full path (ie, isn't prepended by rootFolder)
	public double oldTime(String sourceFolder) {
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

		boolean newWay=false;		// until update flatFileList to track oldest
		if(newWay) {
			try {
				String thisChan = sourceFolder_fullpath + File.separator + ctmap.getName(0);			// this is single channel function
				CTFile[] listOfFiles = flatFileList(basefolder, ctmap, thisChan, true);
				if(listOfFiles != null && listOfFiles.length>0) return listOfFiles[0].fileTime();
				else return 0.;
			} 
			catch(Exception e) {		// should throw
				System.err.println("OOPS, oldTime/flatFileList Exception: "+e);
				return 0.;
			}
		}
		
		CTFile[] listOfFolders = basefolder.listFiles();
		if(listOfFolders == null) return 0.;
		return oldTime(listOfFolders, ctmap);
	}
	
	// NOTE: Each folder in listOfFolders should already be prepended with the rootFolder
	private double oldTime(CTFile[] listOfFolders, CTmap ctmap) {
		if(listOfFolders == null) return 0.;
		if(!listOfFolders[0].isDirectory()) return(listOfFolders[0]).fileTime();  // presume list of files

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

		System.err.println("oldTime got ftime: "+ftime);
		return ftime;
	}

//---------------------------------------------------------------------------------	
// newTime:  find newest time for this source	(neglects block-duration)
//	CTFile newFile = null;
	
	// NOTE: sourceFolder is NOT full path (ie, isn't prepended by rootFolder)
	public double newTime(String sourceFolder) {
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

		boolean newWay=false;		// explicit newTime still gets robust old way search
		if(newWay) {
			try {
				String thisChan = sourceFolder_fullpath + File.separator + ctmap.getName(0);			// this is single channel function
//				System.err.println("newTime, source: "+sourceFolder_fullpath+", chan: "+thisChan+", sourceFolder: "+sourceFolder+", getName: "+ctmap.getName(0));
				CTFile[] listOfFiles = flatFileList(basefolder, ctmap, thisChan, true);
				if(listOfFiles != null && listOfFiles.length>0) return listOfFiles[listOfFiles.length-1].fileTime();
				else return 0.;
			} 
			catch(Exception e) {		// should throw
				System.err.println("OOPS, newTime/flatFileList Exception: "+e);
				e.printStackTrace();
				return 0.;
			}
		}
		else {
			CTFile[] listOfFolders = basefolder.listFiles();
			if(listOfFolders == null) return 0.;
			return(newTime(listOfFolders, ctmap));
		}
	}
	
 	// NOTE: Each folder in listOfFolders should already be prepended with the rootFolder
	private double newTime(CTFile[] listOfFolders, CTmap ctmap) {
		if(listOfFolders == null) return 0.;
		if(!listOfFolders[0].isDirectory()) {
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
		if(sfolder.indexOf(File.separator)<0) {
			sfolder = rootFolder + File.separator + sfolder;		// auto-fullpath
			CTinfo.debugPrint("adding rootfolder to sfolder: "+sfolder);
		}
		
		CTFile sourceFolder = new CTFile(sfolder);
		ArrayList<String> ChanList = new ArrayList<String>();		// for registration
		ChanList.clear();										// ChanList built in listFiles()
		CTFile[] listOfFiles = sourceFolder.listFiles();
		if(listOfFiles == null || listOfFiles.length < 1) return null;
		
		int last = listOfFiles.length-1;
		int expedite = 1;
//		if(fastSearch) expedite=expediteLimit(sfolder, listOfFiles.length, 100);		// segments (was 1000)
//		if(fastSearch) expedite=last;		// fast search: do first and last segment (no, single root time at this level)

		int i=0;
		while(true) {
			CTFile thisFile = listOfFiles[i];
			if(thisFile.isDirectory() && thisFile.fileTime()!=0) {
//				System.err.println("RootTime: "+i+"/"+last+", file: "+thisFile.getAbsolutePath());
				buildChanList(thisFile, ChanList, fastSearch);			// side effect is to add to ChanList
			}
			if(i>=last) break;
			i+=expedite;
			if(i>last) 	i = last;		// make sure first and last are checked
		} 

		Collections.sort(ChanList);
		return ChanList;
	}

	//---------------------------------------------------------------------------------	
	// buildChanList:  build list of channels in all folders (private utility of registerChans)
	
	private void buildChanList(CTFile sourceFolder, ArrayList<String>ChanList, boolean fastSearch) {
		CTFile[] listOfFiles = sourceFolder.listFiles();
		if(listOfFiles == null) return;
//		System.err.println("buildChanList, folder: "+sourceFolder+", listOfFiles.length: "+listOfFiles.length);
		
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
						expedite = 1;													// no expedite channel list themselves
						//	CTinfo.debugPrint("Chanlist.add: "+fname);
					}
				}
			}
			
			if(i>=last) break;
			i+=expedite;
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
		return(getDataMap(ctmap, new CTFile(rootfolder), getftime, duration, rmode));
	}
	
	public CTmap getDataMap(CTmap ctmap, CTFile rootfolder, double getftime, double duration, String rmode) throws Exception {
		return getDataMap(ctmap, rootfolder, getftime, duration, rmode, false);
	}
	
//	private HashMap<String,CTFile[]> fileListByChan = new HashMap<String,CTFile[]>();				// provide way to reset?
	
	private CTmap getDataMap(CTmap ctmap, CTFile rootfolder, double getftime, double duration, String rmode, boolean recurse) throws Exception {
		long startTime = System.nanoTime();
		String thisChan = rootfolder + File.separator + ctmap.getName(0);			// this is single channel function
		try {
			// get updated list of folders
			CTFile[] oldList = fileListByChan.get(thisChan);
			boolean fileRefresh = false;
			if(oldList == null || oldList.length==0) fileRefresh = true;
			if(!rmode.equals("absolute") || (getftime+duration) > oldList[oldList.length-1].fileTime()) fileRefresh = true;
//			if(oldList!=null) System.err.println("oldList.size: "+oldList.length+", oldList.newTime: "+oldList[oldList.length-1].fileTime()+", thisChan: "+thisChan);
			CTinfo.debugPrint("getDataMap!, rootfolder: "+rootfolder+", getftime: "+getftime+", duration: "+duration+", rmode: "+rmode+", chan[0]: "+ctmap.getName(0)+", fileRefresh: "+fileRefresh);
			
			CTFile[] listOfFiles = flatFileList(rootfolder, ctmap, thisChan, fileRefresh);
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

			int ifound = fileSearch(listOfFiles, getftime);			// found is at or before getftime
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
						double priorftime;				
						priorftime = listOfFiles[i-1].fileTime();	// go 2 past to bracket "next/prev" points in candidate list
						if(priorftime > endtime) break;							// done	
					}
					CTinfo.debugPrint("CTreader checking folder["+i+"]: "+folder.getMyPath()+", start: "+getftime+", end: "+endtime);
					getFile(folder, ctmap);		// individual file
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}

		// loop thru ctmap chans, prune ctdata to timerange (vs ctreader.getdata, ctplugin.CT2PImap)
		if(!recurse) ctmap.trim(getftime,  duration, rmode);	
		
		CTinfo.debugPrint("getDataMap("+thisChan+") time: "+((System.nanoTime()-startTime)/1000000.)+" ms, Memory Used MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024));

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
				CTinfo.debugPrint("Put file: "+file.getPath()+", size: "+dlen+", from zipFile: "+file.getMyZipFile()+", ftime: "+file.fileTime());
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
	private HashMap<String,CTFile[]> fileListByChan = new HashMap<String,CTFile[]>();				// provide way to reset?

	// this should be its own Class with oldest, newest, etc properties...
	
//	private synchronized CTFile[] flatFileList(CTFile baseFolder, CTmap ictmap, String thisChan, boolean fileRefresh) throws Exception {
	// beware: this will multi-thread
	private CTFile[] flatFileList(CTFile baseFolder, CTmap ictmap, String thisChan, boolean fileRefresh) throws Exception {
//		long startTime = System.nanoTime();

		CTFile[] cachedList = fileListByChan.get(thisChan);
//		System.err.println("flatFileList, refresh: "+fileRefresh+", chan: "+thisChan+", OldList.size: "+((cachedList==null)?(0):(cachedList.length)));

		final ArrayList<TimeFolder>fflist = new ArrayList<TimeFolder>();
		final CTmap ctmap = ictmap;
		double iendTime = 0.;
		if(cachedList != null && cachedList.length>0) {
			iendTime = cachedList[cachedList.length-1].fileTime();
		}
		final double endTime = iendTime;

		// initially build full flatFileList, subsequent calls update it by adding to oldList
				
		if(fileRefresh) {
			CTFile[] listOfFolders = baseFolder.listFiles();
			if(listOfFolders==null) return null;
			
			// trim oldest if nec
//			double oldestTime = listOfFolders[0].fileTime();      	    // ref oldTime is oldest of any chan
			double oldestTime = oldTime(listOfFolders[0]);			// this at least skips containsFile call, any chan oldest
//			System.err.println("oldestTime: "+oldestTime+", list0: "+(oldList!=null?oldList[0].fileTime():0));
			if(cachedList != null && (oldestTime > cachedList[0].fileTime())) {
				int ichk;
				for(ichk=0; ichk<cachedList.length; ichk++) {
					if(oldestTime <= cachedList[ichk].fileTime()) break;
				}
				if(ichk>0) {
//					System.err.println("oldTRIM "+ctmap.getName(0)+", from: "+oldList.length+", by N: "+ichk+", oldestTime: "+oldestTime+", oldList[0]: "+oldList[0].fileTime());
					CTFile[] tmpList = new CTFile[cachedList.length - ichk];
					for(int j=ichk,k=0; j<cachedList.length; j++,k++) tmpList[k] = cachedList[j];
					cachedList = tmpList;
					fileListByChan.put(thisChan, cachedList);
				}
			};
			
			CTfileList(listOfFolders, fflist, endTime, ctmap);			// add any new files to end of list
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
		
//		System.err.println("flatFileList update fileList key: "+thisChan);
		fileListByChan.put(thisChan, ffarray);
		return ffarray;
	}
	
	//--------------------------------------------------------------------------------------------------------
	// CTfileList:  custom walkFileTree but skipping over subfolders
	
	private boolean CTfileList(CTFile[] listOfFolders, ArrayList<TimeFolder>fflist, double endTime, CTmap ctmap) {
		
//		System.err.println("CTfileList, listOfFolders.length: "+listOfFolders.length);
		for(int i=listOfFolders.length-1; i>=0; i--) {			// reverse search thru sorted folder list
			CTFile folder = listOfFolders[i];
			
			if(folder.isDirectory()) {
//				System.err.println("CTfileList recurse into folder:"+folder.getName());
				if(!CTfileList(folder.listFiles(), fflist, endTime, ctmap)) return false;		// pop recursion stack
			}
			else {
				String fname = folder.getName();
				double ftime = folder.fileTime();			
				if(!ctmap.checkName(fname)) continue;

				if(ftime>endTime) {		// time here may equal end of prior block???
					fflist.add(new TimeFolder(folder,ftime));		
//					System.err.println("CTfileList GOT time: "+ftime+", endTime: "+endTime+", checkTime: "+(ftime-endTime)+", newLen: "+fflist.size()+", file: "+folder.getPath()+"fflist.size: "+fflist.size());
				}
				else {
//					System.err.println("CTfileList SKIP time: "+ftime+", endTime: "+endTime+", checkTime: "+(ftime-endTime)+", newLen: "+fflist.size()+", file: "+folder.getPath());
					return false;
				}
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
        
//      System.err.println("found: "+ifound+", start: "+start+", end: "+end+", mid: "+mid+", searchtime: "+ftime+", gottime-ftime: "+(fileList[mid].fileTime()-ftime));

        return ifound;
    }
	
}



