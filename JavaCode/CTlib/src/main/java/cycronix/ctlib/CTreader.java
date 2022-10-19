/*
Copyright 2018 Cycronix
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
    http://www.apache.org/licenses/LICENSE-2.0
 
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package cycronix.ctlib;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;

import cycronix.ctlib.CTcache.TimeFolder;
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
	private CTcache CTcache;								// one cache per CTreader
	private boolean readError = false;						// flag refresh cache

//---------------------------------------------------------------------------------	
 // constructor for CTread.get() method
	/**
	 * Constructor.  sets CTreader to look for sources in default rootFolder: "CTdata"
	 */
	public CTreader() {
		// Use the default root folder
		rootFolder = "CTdata";
		CTcache = new CTcache(rootFolder);
	}
	
	/**
	 * Constructor
	 * @param fname Root folder to read CT sources.  Of form: "CTdata"
	 */
	public CTreader(String fname) {
		rootFolder = new String(fname);
		CTcache = new CTcache(rootFolder);
	}

	/**
	 * set source root folder
	 * @param fname Root folder to read CT sources.  Of form: "CTdata"
	 */
	public void setFolder(String fname) {
		rootFolder = fname;
	}

	/**
	 * get source root folder
	 * @return rootFolder 
	 */
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
	 * @param password cryto password
	 * @throws Exception on error
	 */
	public void setPassword(String password) throws Exception {
		ctcrypto = new CTcrypto(password);
	}
	
	/**
	 * Set encryption password, none if null.
	 * @param password			decryption password
	 * @param optionalDecrypt	if true, return non-encrypted data if decryption fails.  Otherwise fail with exception.
	 * @throws Exception on error
	 */
	public void setPassword(String password, boolean optionalDecrypt) throws Exception {
		ctcrypto = new CTcrypto(password, optionalDecrypt);
	}
	
//---------------------------------------------------------------------------------	   
//get:  direct-fetch time+data method
// note:	this uses multi-channel ctmap internally, but only returns one channel (ctdata)
//			retain ctmap as a cache?  prefetch from ctmap, fill in balance from files...
		
	/**
	 * Get CT data 
	 * @param source Source folder to read CT files.  e.g. "mysource"
	 * @param chan Channel name to get, e.g. "chan1"
	 * @param tget Time to get (seconds since epoch)
	 * @param tdur Duration to get (seconds)
	 * @param tmode Fetch mode, options: "absolute", "oldest", "newest", "after", "prev"
	 * @return CTdata object containing data
	 * @throws Exception on error
	 */
	public CTdata getData(String source, String chan, double tget, double tdur, String tmode) throws Exception {
		CTmap ctmap= new CTmap(chan);
//		String sourceFolder;
//		if(source == null) 	sourceFolder = rootFolder;
//		else				sourceFolder = rootFolder+File.separator+source;

		try {
//			ctmap = getDataMap(ctmap, sourceFolder, tget, tdur, tmode);		// time units = seconds
			ctmap = getDataMap(ctmap, source, tget, tdur, tmode);		// time units = seconds
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
	/**
	 * Get oldest, newest time limits in one-pass from cache
	 * @param sourceFolder source Source folder to read CT files.  e.g. "mysource"
	 * @param chan Channel name to check, e.g. "chan1"
	 * @return two-element double[] array with oldest, newest times
	 * @throws Exception on error
	 */
	public double[] timeLimits(String sourceFolder, String chan) throws Exception {
		double[] tlimits = new double[]{0,0};
		boolean newWay = true;
		if(!newWay) {
			tlimits[0] = oldTime(sourceFolder, chan);
			tlimits[1] = newTime(sourceFolder, chan);
			return tlimits;
		}

		String thisChanKey = CTcache.chan2key(sourceFolder + File.separator + chan);			// this is single channel function
		TimeFolder[] listOfFiles = CTcache.fileListByChan.get(thisChanKey);				// get existing cached limits
		if(listOfFiles == null || listOfFiles.length==0) {
			CTcache.buildIndices(sourceFolder, 0);
			listOfFiles = CTcache.fileListByChan.get(thisChanKey);				// try again after rebuilding indexes
            if(listOfFiles == null) return tlimits;                     // avoid null ptr exception if empty (mjm 3/24/22)
//			listOfFiles = flatFileList(rootFolder + File.separator + sourceFolder, new CTmap(chan), thisChanKey, true);
		}
		
		tlimits[0] = listOfFiles[0].getCTFile().baseTime();							// oldest
//		tlimits[0] = listOfFiles[0].fileTime();							// oldest
		tlimits[1] = listOfFiles[listOfFiles.length-1].getCTFile().fileTime();		// newest
		return tlimits;
	}
	
//---------------------------------------------------------------------------------	
// oldTime:  find oldest time for this source (neglects block-duration)
	/**
	 * Get oldest time for source
	 * @param sourceFolder path to source (not full path, ie not prepended by rootFolder)
	 * @return oldest time for this source
	 */
	public double oldTime(String sourceFolder) {
//		System.err.println("oldTime sourceFolder: "+sourceFolder);
		return oldTime(sourceFolder, (CTmap)null);
	}
	
	/**
	 * Get oldest time for source and chan
	 * @param sourceFolder path to source (not full path, ie not prepended by rootFolder)
	 * @param chan Channel name
	 * @return oldest time
	 */
	// NOTE: sourceFolder is NOT full path (ie, isn't prepended by rootFolder)
	public double oldTime(String sourceFolder, String chan) {
		return oldTime(sourceFolder, new CTmap(chan));
	}
	
	/**
	 * Get oldest time for any chan in CTmap
	 * @param sourceFolder Source
	 * @param ctmap Channel Map
	 * @return oldest time
	 */
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
/*
	// fast oldTime:  crawl down first (oldest) folder branch to bottom file time
	private double oldTime(CTFile baseFolder) {
//		System.err.println("oldTime baseFolder: "+baseFolder.getPath()+", isDir: "+baseFolder.isDirectory());
		if(!baseFolder.isDirectory()) return baseFolder.fileTime();  

		double ftime = 0.;		// default is now
		CTFile[] files = baseFolder.listFiles();
		if(files==null || files.length == 0) return baseFolder.fileTime();		// catch empty folder case
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
*/
//---------------------------------------------------------------------------------	
// newTime:  find newest time for this source	(neglects block-duration)
//	CTFile newFile = null;
	/**
	 * Get newest time for source
	 * @param sourceFolder name of source
	 * @return newest time
	 */
	// NOTE: sourceFolder is NOT full path (ie, isn't prepended by rootFolder)
	public double newTime(String sourceFolder) {
//		System.err.println("newTime: "+sourceFolder);
		return newTime(sourceFolder, (CTmap)null);
	}
	
	/** 
	 * Get newest time for source and chan
	 * @param sourceFolder Source Folder
	 * @param chan Channel name
	 * @return newest time
	 */
	// NOTE: sourceFolder is NOT full path (ie, isn't prepended by rootFolder)
	public double newTime(String sourceFolder, String chan) {
		return newTime(sourceFolder, new CTmap(chan));
	}
	
	/**
	 * Get newest time for channels in CTmap
	 * @param sourceFolder Source folder
	 * @param ctmap Channel Map
	 * @return newest time
	 */
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

 	// NOTE: Each folder in listOfFolders should already be prepended with the rootFolder
	private double newTime(TimeFolder[] listOfFolders) {
		if(listOfFolders == null) return 0.;
		if(!listOfFolders[0].getCTFile().isDirectory() && listOfFolders[0].getCTFile().baseTime()!=0) {
			return(listOfFolders[listOfFolders.length-1]).getTime();  // presume (prefiltered!) list of files
		}
		
		//double ftime = (double)System.currentTimeMillis()/1000.;		// default is now
//		newFile = null;
		double ftime = 0.;
		
		int idx=listOfFolders.length-1;
		for(; idx>=0; idx--) {		// rewind to actual data
			CTFile[] files = listOfFolders[idx].getCTFile().listFiles();
			if(files == null) continue;
			if(files.length == 0) continue;

			int j = files.length-1;
			for(; j>=0; j--) {
				if(files[j].isTFILE()) ftime = files[j].fileTime();		// skip recursion, use the file-as-folder for TFILE
//				else if(files[j].isDirectory()) ftime = newTime(new CTFile[] {files[j]});	// recurse
				else if(files[j].isDirectory()) ftime = newTime(new TimeFolder[] { CTcache.newTimeFolder(files[j],files[j].fileTime())});	// recurse

				else {
					ftime = files[j].fileTime();		// at this point this is a file not a dir
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
	/**
	 * List channels for source
	 * @param sfolder source folder
	 * @return list of channels
	 */
	public ArrayList<String> listChans(String sfolder) {
		return listChans(sfolder, false);		// default to fastSearch=false
	}

	/**
	 * List channels for source (fast search by sampling some (not all) branches of CT files)
	 * @param sfolder Source folder
	 * @param fastSearch boolean fastsearch T/F
	 * @return list of channels
	 */
	// fastSearch galumps thru chans if lots
	public ArrayList<String> listChans(String sfolder, boolean fastSearch) {
		
		if(!fastSearch) {
//			System.err.println("listChans via CTcache!");
			return CTcache.listChans(sfolder);		// alternate: get chanList from Index
		}
		
//		long startTime = System.nanoTime();

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
		if(fastSearch) expedite=expediteLimit(sourceFolder.getName(), listOfFiles.length, 10);	// blocks (was 100) limit is max/level (smaller=faster)
		
		int i=0;
		int last = listOfFiles.length-1;
		if(last<0) {
			CTinfo.debugPrint("Warning, buildChanList, no files: "+sourceFolder);
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
	/**
	 * Check if rootFolder exists
	 * @return T/F
	 */
	public boolean checkRoot() {
		return new File(rootFolder).exists();
	}
	

//	@Deprecated
//	public ArrayList<String> listSourcesRecursive() throws IOException {
//		return listSources();
//	}
	
	/**
	 * List sources with recursive search
	 * @return list of sources
	 * @throws IOException on error
	 */
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
	/**
	 * get CTmap consisting of multiple channels; time-align all chans with first
	 * 
	 * @param ctmap CTmap with channel names, will hold CTdata results
	 * @param source relative path to data source
	 * @param getftime start time of fetch (s)
	 * @param duration duraton of fetch (s)
	 * @param rmode fetch-mode ("newest", "absolute", "oldest")
	 * @return Channel map with data
	 * @throws Exception on error
	 */
	public CTmap getDataMap(CTmap ctmap, String source, double getftime, double duration, String rmode) throws Exception {
		// arg source is relative path source, sourceFolder is abs path
		String sourceFolder;
		if(source == null) 	sourceFolder = rootFolder;
		else				sourceFolder = rootFolder+File.separator+source;
		
//		boolean firstChan = true;
//		double refTime=0, refDuration=0;
		for(String chan : ctmap.keySet()) {
			addChanToDataMap(ctmap, sourceFolder, chan, getftime, duration, rmode); 	// simply loop through all chans

			// if multi-chan request, get first chan, match other chans to its time-interval
			// following logic dicey: if firstChan is single point, then adjusted duration=0, can change finite-duration to at-or-before logic
			/*
			if(firstChan) {
				addChanToDataMap(ctmap, sourceFolder, chan, getftime, duration, rmode);
				if(ctmap.size()==1) return ctmap;
				
				// determine time-range for following channel(s):
				CTdata ctdata = ctmap.get(chan);
				if(ctdata==null) return ctmap;
				double firstTime[] = ctdata.getTime();
				if(firstTime==null || firstTime.length==0) return ctmap;
				CTinfo.debugPrint("getDataMap, firstChan: "+chan+", ngot: "+firstTime.length);
				refTime = firstTime[0];
				refDuration = firstTime[firstTime.length-1] - refTime;
				firstChan = false;
			}
			else {
				addChanToDataMap(ctmap, sourceFolder, chan, refTime, refDuration, "absolute");
			}
			*/
		}
		
		return ctmap;
	}
	
	//---------------------------------------------------------------------------------	
	private CTmap addChanToDataMap(CTmap ctmap, String rootfolder, String chan, double getftime, double duration, String rmode) throws Exception {
//		long startTime = System.nanoTime();	
//		String thisChanKey = chan2key(rootfolder + File.separator + ctmap.getName(0));			// this is single channel function
		String thisChanKey = CTcache.chan2key(rootfolder + File.separator + chan);			// this is single channel function
		
		try {
			// get updated list of folders
//			TimeFolder[] oldList = CTcache.fileListByChan.get(thisChanKey);
//			boolean fileRefresh = false;
//			if(oldList == null || oldList.length==0 || !rmode.equals("absolute") || (getftime+duration) > oldList[oldList.length-1].getTime()) fileRefresh = true;

			TimeFolder[] listOfFiles = null;
			//	listOfFiles = flatFileList(rootfolder, new CTmap(chan), thisChanKey, fileRefresh);  // old way mjm 12/14/20. single/simple call to buildIndices
			String sname = thisChanKey.replace("/"+chan, "");
			CTcache.updateIndices(sname);  	// efficient after-known-time update
			listOfFiles = CTcache.fileListByChan.get(thisChanKey);
			if(listOfFiles == null || listOfFiles.length < 1) return ctmap;
					
			if(rmode.equals("registration")) {				// handle registration
				System.err.println("unexpected registration request!");
				return ctmap;
			}
			else if(rmode.equals("oldest")) {				// convert relative to absolute time
				getftime = listOfFiles[0].getCTFile().baseTime();
//				getftime = CTcache.sourceOldTime(sname);
				CTinfo.debugPrint("getDataMap, oldTime: "+getftime);
				rmode = "absolute";
			}
			else if(rmode.equals("newest")) {
				getftime = newTime(listOfFiles) - duration - getftime;			// listOfFolders filtered, don't waste effort re-filtering ctmap
				CTinfo.debugPrint("newest getftime: "+getftime);
				rmode = "absolute";
			}
			else if(rmode.equals("after")) {
				double newtime =newTime(listOfFiles);

				double tdur = newtime - getftime;
				if(tdur > duration) getftime = newtime - duration;		// galump if nec to get most recent duration
				getftime += 0.000001;									// microsecond after (no overlap)
				CTinfo.debugPrint("after duration: "+duration);
				rmode = "absolute";
			}

			int ifound = fileSearch(listOfFiles, getftime);				// found is at or before getftime
			if(ifound < 0) {
				CTinfo.debugPrint("Not Found! "+chan);
				return null;
			}
			
			if(rmode.equals("prev")) ifound = ifound - 1;
			if(ifound < 0) ifound = 0;									// firewall
			
			CTinfo.debugPrint("FOUND ftime: "+getftime+", index: "+ifound+", size: "+listOfFiles.length);
			if(duration==0 && rmode.equals("absolute")) {
				getFile(listOfFiles[ifound], ctmap);
			}
			else {
				int istart = ifound;
				double endtime = getftime + duration;

				// one-pass, gather list of candidate folders
				for(int i=istart; i<listOfFiles.length; i++) {						// find range of eligible folders 
					TimeFolder folder = listOfFiles[i];
					if(i>1) {													// after end check
						double priorftime = listOfFiles[i-1].getTime();		// go 1 past to bracket "next/prev" points in candidate list
						if(priorftime > endtime) break;							// done	
					}
					CTinfo.debugPrint("CTreader checking folder["+i+"]: "+folder.getCTFile().getPath()+", start: "+getftime+", end: "+endtime);
					getFile(folder, ctmap);		// individual file
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}

		// prune ctdata to timerange (vs ctreader.getdata, ctplugin.CT2PImap)
		ctmap.trim(chan, getftime,  duration, rmode);		// single channel trim by time range
		CTinfo.debugPrint("addCHan, getftime: "+getftime+", duration: "+duration+", rmode: "+rmode);

		return ctmap;				// last folder
	}

	//--------------------------------------------------------------------------------------------------------
	// get data from CTFile
	private long getFile(TimeFolder tfile, CTmap cm) throws Exception {
		long hasdata = 0;
		CTFile file = tfile.getCTFile();
		
		if(file.isFile()) {
			String fileName =  file.getName();
			if(!cm.checkName(fileName)) return 0;		// not a match 
			byte[] data = null;
			boolean getdata = !timeOnly || !fileName.toLowerCase().endsWith(".jpg");		// timeOnly only works for images at this point

			if(getdata) {
				try {
					data = file.read();		// read the entire file in one chunk
				} catch(Exception e) {
					readError = true;
				}
			}

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
//				long dlen = data!=null?data.length:0;
				CTinfo.debugPrint("getFile: "+file.getPath()+", from zipFile: "+file.getMyZipFile()+", ftime: "+file.fileTime());
			}
		} 
		return hasdata;
	}
	
	//--------------------------------------------------------------------------------------------------------
	// flattenFolders:  make list of time/folder as one-deep list

//	private synchronized CTFile[] flatFileList(CTFile baseFolder, CTmap ictmap, String thisChan, boolean fileRefresh) throws Exception {
	// beware: this will multi-thread
	/**
	 * Clear data caches
	 */
	public void clearFileListCache() {
//		System.err.println("Clear File List Cache! size: "+CTcache.fileListByChan.size());
//		CTcache.fileListByChan.clear();
		
		// clear all the other caches too:
		System.err.println("Clear Caches! File,Data,ZipMap size: "+CTcache.CTFileCache.size()+","+CTcache.DataCache.size()+","+CTcache.ZipMapCache.size());
		CTcache.CTFileCache.clear();			// small memory use
		CTcache.ZipFileCache.clear();			// small
		CTcache.DataCache.clear();				// biggest
		CTcache.ZipMapCache.clear();			// big
	}
	
	/**
	 * Clear data caches for given channel key
	 * @param chanKey channel key string
	 */
	public void clearFileListCache(String chanKey) {
//		CTinfo.debugPrint("CLEAR fileListCache! chan: "+chanKey+", size: "+CTcache.fileListByChan.get(chanKey).length);
		CTcache.fileListByChan.put(chanKey, null);
	}

	/**
	 * Pre-cache:  build file index cache from existing CT files
	 * @param source Source to cache
	 * @throws Exception on error
	 */
	public void preCache(String source) throws Exception {
		CTcache.buildIndices(source, 0.0);		// build in place one-pass index...
/*
		ArrayList<String> chans = listChans(source, true);		// ,true for fastSearch
		Thread itt=null;
		int nchan = 0;
		ArrayList<Thread>tlist = new ArrayList<Thread>();
		for(String chan:chans) {
			String chankey = chan2key(source + File.separator + chan);
//			flatFileList(rootFolder + File.separator + source, new CTmap(chan), chankey, true);
			IndexThread it = new IndexThread(rootFolder + File.separator + source, chan, chankey);
			itt = new Thread(it);
			tlist.add(itt);
			itt.start();
//			TimeFolder[] cachedList = CTcache.fileListByChan.get(chankey);
//			System.err.println("CTreader chan indexed: "+chankey+", cacheSize: "+((cachedList==null)?(0):(cachedList.length)));
			nchan++;
		}
		System.err.println("Indexing "+nchan+" channels...");
		for(Thread t:tlist) t.join();
//		itt.join();  // wait on last thread
 */
	}
	
	/**
	 * Build file index cache from all sources
	 * @throws Exception on error
	 */
	public void preCache() throws Exception {
//		System.err.println("Indexing sources...");
		CTcache.fileListByChan.clear();  		// fresh list
		ArrayList<String> sources = listSources();
		for(String s:sources) preCache(s);				// run thru each source
	}
	
	/*
	private class IndexThread implements Runnable {		// implement as thread pool?
	    private String base, key, chan;

	    public IndexThread(String base, String chan, String key) {
	        this.base = base;
	        this.key = key;
	        this.chan = chan;
	    }

	    public void run() {
	    	try {
//	    		System.err.println("Indexing: "+key);
	    		flatFileList(base, new CTmap(chan), key, true);
	    	} catch(Exception e) {
	    		System.err.println("flatFileList exception: "+e);
	    	}
	    }
	}
  */
	
	/*
	//--------------------------------------------------------------------------------------------------------
	synchronized 
	private TimeFolder[] flatFileList(String baseFolder, CTmap ictmap, String chanKey, boolean fileRefresh) throws Exception {
//		long startTime = System.nanoTime();		
		TimeFolder[] cachedList = CTcache.fileListByChan.get(chanKey);
		
//		for(String chan : CTcache.fileListByChan.keySet()) System.err.println("KEY: "+chan);
		System.err.println("flatFileList, refresh: "+fileRefresh+", chanKey: "+chanKey+", OldList.size: "+((cachedList==null)?(0):(cachedList.length)));
//		if(fileRefresh) System.err.println("Indexing: "+chanKey);
		long t1 = System.nanoTime();

		final ArrayList<TimeFolder>fflist = new ArrayList<TimeFolder>();
		final CTmap ctmap = ictmap;
		double iendTime = 0.;
		if(cachedList != null && cachedList.length>0) {
//			iendTime = cachedList[cachedList.length-1].getTime();
			iendTime = CTcache.sourceEndTime(baseFolder);
		}
		else 	CTinfo.debugPrint("Indexing files for chan: "+chanKey+", fileRefresh: "+fileRefresh);

		final double endTime = iendTime;

		// initially build full flatFileList, subsequent calls update it by adding to oldList
				
		if(fileRefresh || readError) {
			if(readError) {
				CTinfo.debugPrint("refresh index cache on read error: "+ctmap.getName(0));
				readError= false;
			}
			CTFile[] listOfFolders = new CTFile(baseFolder).listFiles();
//			System.err.println("flatFileList, baseFolder: "+baseFolder+", flen: "+((listOfFolders==null)?0:listOfFolders.length));
			if(listOfFolders==null || listOfFolders.length==0) {
//				System.err.println("flatFileList, null listOfFolders for: "+baseFolder);
				return null;
			}
			
			// trim oldest if nec
//			double oldestTime = listOfFolders[0].fileTime();      	    // ref oldTime is oldest of any chan
			double oldestTime = oldTime(listOfFolders[0]);			// this at least skips containsFile call, any chan oldest
			if(cachedList != null && cachedList.length > 1 && (oldestTime > cachedList[0].getTime())) {
//				System.err.println("oldestTime: "+oldestTime+", cachedOldest: "+cachedList[0].getTime());

				int ichk;
				for(ichk=0; ichk<cachedList.length; ichk++) {
					if(oldestTime <= cachedList[ichk].getTime()) break;
				}
				if(ichk>0) {
//					System.err.println("oldTRIM "+ctmap.getName(0)+", from: "+oldList.length+", by N: "+ichk+", oldestTime: "+oldestTime+", oldList[0]: "+oldList[0].fileTime());
					TimeFolder[] tmpList = new TimeFolder[cachedList.length - ichk];
					for(int j=ichk,k=0; j<cachedList.length; j++,k++) tmpList[k] = cachedList[j];	// salvage old cache > updated oldestTime
					cachedList = tmpList;
					CTcache.fileListByChan.put(chanKey, cachedList);
				}
			};

			t1 = System.nanoTime();
			CTfileList(listOfFolders, fflist, endTime, ctmap);			// add any new files to end of list				
//			System.err.println(">CTfileList chan: "+chanKey+", dt: "+((System.nanoTime()-t1)/1000000.));
		}
		
		System.err.println("new ffllist.size: "+fflist.size()+", cacheList.size: "+((cachedList==null)?0:cachedList.length));
		if(fflist.size()==0) return cachedList;		// nothing new, save some work

		Collections.sort(fflist);
		TimeFolder[] ffarray;
		if(cachedList != null) {
			ffarray = new TimeFolder[fflist.size() + cachedList.length];
			for(int i=0; i<cachedList.length; i++) ffarray[i] = cachedList[i];
			for(int i=0, j=cachedList.length; i<fflist.size(); i++,j++) ffarray[j] = fflist.get(i);		// concatenate old + new
		}
		else {
			ffarray = new TimeFolder[fflist.size()];
			for(int i=0; i<ffarray.length; i++) {
				ffarray[i] = fflist.get(i);
//				System.err.println("i: "+i+", file: "+fflist.get(i).getCTFile().getPath()+", time: "+(long)fflist.get(i).getTime());
			}
		}
		
		CTcache.fileListByChan.put(chanKey, ffarray); 	// BUG:  ffarray must be single-channel list here!
		System.err.println("flatFileList update, chankey: "+chanKey+", listLen: "+ffarray.length+", fileListByChan.len: "+CTcache.fileListByChan.get(chanKey).length);

//		System.err.println("Indexed: "+chanKey);
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
//		if(pdebug) 
			System.err.println("CTfileList, listOfFolders.length: "+listOfFolders.length+", endTime: "+endTime);
		
//		ArrayList<TimeFolder>fclist = new ArrayList<TimeFolder>();
		
		for(int i=listOfFolders.length-1; i>=0; i--) {			// reverse search thru sorted folder list
//			if(pdebug) System.err.println("i: "+i+", time1: "+((System.nanoTime()-startTime)/1000000.));
			CTFile folder = listOfFolders[i];
			double ftime = folder.fileTime();
			
			if(folder.isDirectory()) {
//				if(pdebug) System.err.println("time2a: "+((System.nanoTime()-startTime)/1000000.));
				if(ftime <= endTime) return false;			

				CTFile[] listOfFiles = folder.listFiles();
				if(pdebug) System.err.println("CTfileList recurse into folder:"+folder.getName()+", time: "+((System.nanoTime()-startTime)/1000000.));

				if(!CTfileList(listOfFiles, fflist, endTime, ctmap, recursionLevel+1)) {
					if(pdebug) System.err.println("pop out of subfolder, time: "+((System.nanoTime()-startTime)/1000000.));
					return false;		// pop recursion stack
				}
			}
			else {
				System.err.println("add folder: "+folder.getName()+" at time: "+ftime);
				String fname = folder.getName();
				if(ctmap.checkName(fname)) fflist.add(CTcache.newTimeFolder(folder,ftime));
			}
		}
		
		return true;					// all done, success
	}
*/
	//--------------------------------------------------------------------------------------------------------
	// binary search for file at or before timestamp
    private int fileSearch(TimeFolder[] fileList, double ftime) {
//    	System.err.println("fileSearch ftime: "+ftime+", f0: "+fileList[0].getTime()+", chan: "+fileList[0].getCTFile().getName());
    	
    	if(ftime < fileList[0].getTime()) return -1;		// quick search for off BOF
    	
        int start = 0;
        int end = fileList.length - 1;
        int mid=0;
        while (start < end) {
            mid = (start + end) / 2;
  //      	System.err.println("start: "+start+", end: "+end+", mid: "+mid+", ftime: "+ftime+", thistime: "+fileList[mid].getTime());

            if (ftime == fileList[mid].getTime()) {
                return mid;
            }
            if (ftime < fileList[mid].getTime()) {
                end = mid - 1;

            } else {
            	start = mid + 1;
            }
        }
        
        int ifound = start;
        while(ifound > 0 && (ftime<fileList[ifound].getTime())) ifound--;				// make sure at or BEFORE
        
//       System.err.println("found: "+ifound+", start: "+start+", end: "+end+", mid: "+mid+", searchtime: "+ftime+", gottime-ftime: "+(fileList[mid].getTime()-ftime));
        return ifound;
    }

}



