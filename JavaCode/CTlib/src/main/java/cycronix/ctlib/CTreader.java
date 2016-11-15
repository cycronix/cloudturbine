package cycronix.ctlib;

//---------------------------------------------------------------------------------	
// CTreader:  read CloudTurbine files stored by timestamp folders
// adapted from CTread, generalize without DT specific reference
// Matt Miller, Cycronix
// 02/18/2014

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import static java.nio.file.FileVisitResult.*;
import java.nio.file.FileVisitOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * A class to read data from CloudTurbine folder/files.
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2014/03/06
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

//---------------------------------------------------------------------------------	

public class CTreader {
	private static String rootFolder = null;

//---------------------------------------------------------------------------------	
 // constructor for CTread.get() method
	public CTreader() {}

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
	@Deprecated
	public void setTimeOnly(boolean tflag) {		// clumsy...  
//		timeOnly = tflag;							// doesn't work for blockdata where time is derived from block-interval/points
	}
	
//---------------------------------------------------------------------------------	   
//get:  direct-fetch time+data method
// note:	this uses multi-channel ctmap internally, but only returns one channel (ctdata)
//			retain ctmap as a cache?  prefetch from ctmap, fill in balance from files...
	
//	private Map<String, CTFile> CTFileMap = new TreeMap<String, CTFile>();		// cache
	
	public CTdata getData(String source, String chan, double tget, double tdur, String tmode) {
		CTmap ctmap= new CTmap(chan);
		CTFile sourceFolder;

		if(source == null) 	sourceFolder = new CTFile(rootFolder);
		else				sourceFolder = new CTFile(rootFolder+File.separator+source);

		try {
			ctmap = getDataMap(ctmap, sourceFolder, tget, tdur, tmode);		// time units = seconds
		} 
		catch(Exception e) {
			System.err.println("oops, exception: "+e+", ctmap: "+ctmap);
		}

		//		CTdata tdata = ctmap.getTimeData(chan, ctmap.refTime, tdur, tmode);
		CTdata tdata = ctmap.get(chan);		// already trimmed in getDataMap
		return tdata;	
	}
		
//---------------------------------------------------------------------------------	
// oldTime:  find oldest time for this source (neglects block-duration)
	
	public double oldTime(String sourceFolder) {
		return oldTime(sourceFolder, (CTmap)null);
	}
	
	public double oldTime(String sourceFolder, String chan) {
		return oldTime(sourceFolder, new CTmap(chan));
	}
	
	public double oldTime(String sourceFolder, CTmap ctmap) {
		CTFile rootfolder = new CTFile(sourceFolder);
		CTFile[] listOfFolders = rootfolder.listFiles();
		if(listOfFolders == null) return 0.;
		return oldTime(listOfFolders, ctmap);
	}
	
	private double oldTime(CTFile[] listOfFolders, CTmap ctmap) {
		if(listOfFolders == null) return 0.;

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
	
//---------------------------------------------------------------------------------	
// newTime:  find newest time for this source	(neglects block-duration)
//	CTFile newFile = null;
	public double newTime(String sourceFolder) {
		return newTime(sourceFolder, (CTmap)null);
	}
	
	public double newTime(String sourceFolder, String chan) {
		return newTime(sourceFolder, new CTmap(chan));
	}
	
 	public double newTime(String sourceFolder, CTmap ctmap) {
		CTFile rootfolder = new CTFile(sourceFolder);
		CTFile[] listOfFolders = rootfolder.listFiles();
		if(listOfFolders == null) return 0.;
		return(newTime(listOfFolders, ctmap));
	}
	
	private double newTime(CTFile[] listOfFolders, CTmap ctmap) {
		if(listOfFolders == null) return 0.;

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
//				System.err.println("newTime check file: "+files[j].getMyPath());
				if(files[j].isDirectory()) ftime = newTime(new CTFile[] {files[j]}, ctmap);	// recurse
				else {
//					System.err.println("newTime got fileTime: "+files[j].fileTime()+", containsFile: "+listOfFolders[idx].containsFile(ctmap));
//					if(ctmap == null || containsFile(files, ctmap)) ftime = files[jdx].fileTime();
					if(ctmap == null || listOfFolders[idx].containsFile(ctmap)) ftime = files[j].fileTime();
				}
			
				if(ftime > 0.) return ftime;
			}
//			System.err.println("newTime skip past folder: "+listOfFolders[idx].getAbsolutePath());
		}
		
//		System.err.println("newTime got ftime: "+ftime);
		return ftime;
	}

	//---------------------------------------------------------------------------------	
	// listChans:  build list of channels all folders under source folder 

	public ArrayList<String> listChans(String sfolder) {
		if(sfolder.indexOf(File.separator)<0) {
			sfolder = rootFolder + File.separator + sfolder;		// auto-fullpath
			CTinfo.debugPrint("adding rootfolder to sfolder: "+sfolder);
		}
		
		CTFile sourceFolder = new CTFile(sfolder);
		ArrayList<String> ChanList = new ArrayList<String>();		// for registration
		ChanList.clear();										// ChanList built in listFiles()
		CTFile[] listOfFiles = sourceFolder.listFiles();
		if(listOfFiles == null || listOfFiles.length < 1) return null;

		int expedite=expediteLimit(sfolder, listOfFiles.length, 1000);		// segments
//		expedite = 1;	// ???
		for(int i=0; i<listOfFiles.length;i+=expedite) {
			CTFile thisFile = listOfFiles[i];
			if(!thisFile.isDirectory() || thisFile.fileTime()==0) continue;		// this isn't a channel-folder
			buildChanList(thisFile, ChanList);			// side effect is to add to ChanList
		}

		Collections.sort(ChanList);
		return ChanList;
	}

	//---------------------------------------------------------------------------------	
	// buildChanList:  build list of channels in all folders (private utility of registerChans)

	private void buildChanList(CTFile sourceFolder, ArrayList<String>ChanList) {
		CTFile[] listOfFiles = sourceFolder.listFiles();
		if(listOfFiles == null) return;
//		System.err.println("buildChanList, folder: "+sourceFolder+", listOfFiles.length: "+listOfFiles.length);
		
		int expedite=expediteLimit(sourceFolder.getName(), listOfFiles.length, 10000);		// blocks
		for(int i=0; i<listOfFiles.length; i+=expedite) {
			CTFile thisFile=listOfFiles[i];
			if(thisFile.isFile() && thisFile.length() <= 0) continue;
//			CTinfo.debugPrint("buildChanList(), thisFile: "+thisFile+", isFile: "+thisFile.isFile()+", len: "+thisFile.length());
			if(thisFile.isDirectory() && thisFile.fileTime()>0) {
//				CTinfo.debugPrint("buildChanList got folder: "+thisFile+", recurse: "+thisFile);
				buildChanList(thisFile,ChanList);		// recursive ?
			}
			else {
				// side effect:  build ChanList for registration
				String fname = listOfFiles[i].getName();
				if(ChanList.indexOf(fname) < 0)  {
					ChanList.add(fname);	// add if not already there
					CTinfo.debugPrint("Chanlist.add: "+fname);
				}
			}
		}
	}
	
	//---------------------------------------------------------------------------------	
	private int expediteLimit(String sfolder, int flen, int limit) {
		if(flen > 2*limit) {
			System.err.println("Expedited channel list for source "+sfolder+"! nfiles: "+flen+", limited to: "+limit);
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
				Files.walkFileTree(file.toPath(), opts, 4, new SimpleFileVisitor<Path>() {

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
							if((new CTFile(file.getName()).fileTime())>0.) {		// folder with a "timed" folder/file is a candidate source
								if ( dir.equals( rootPath ) ) return FileVisitResult.CONTINUE;
								int npath = dir.getNameCount();
								String thisPath = dir.getName(npath-1).toString();
								for(int i=(npath-2); i>=nroot; i--) thisPath = dir.getName(i) + File.separator + thisPath; 	// skip rootPath

								if(thisPath.length() >0) {
									SourceList.add(thisPath);		// only add if not a time-number name
								}

								return FileVisitResult.SKIP_SUBTREE;
							} }
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
	
	public CTmap getDataMap(CTmap ctmap, String rootfolder, double getftime, double duration, String rmode) {
		return(getDataMap(ctmap, new CTFile(rootfolder), getftime, duration, rmode));
	}
	
	public CTmap getDataMap(CTmap ctmap, CTFile rootfolder, double getftime, double duration, String rmode) {
		return getDataMap(ctmap, rootfolder, getftime, duration, rmode, false);
	}
	
	// NoTrim for recursive calls
	private CTmap getDataMap(CTmap ctmap, CTFile rootfolder, double getftime, double duration, String rmode, boolean recurse) {
		CTinfo.debugPrint("getDataMap!, rootfolder: "+rootfolder+", getftime: "+getftime+", duration: "+duration+", rmode: "+rmode+", chan[0]: "+ctmap.getName(0)+", ctmap.size: "+ctmap.size());
		try {
			// get updated list of folders
			CTFile[] listOfFolders = rootfolder.listFolders(ctmap);	// folders pruned to ctmap chans only
//			System.err.println("getDataMap, listOfFolders.length: "+listOfFolders.length);
//			System.err.println("getDataMap, old: "+oldTime(listOfFolders, ctmap)+", new: "+newTime(listOfFolders,ctmap));

			if(listOfFolders == null || listOfFolders.length < 1) return ctmap;

			if(rmode.equals("registration")) {				// handle registration
				System.err.println("unexpected registration request!");
				return ctmap;
			}
			else if(rmode.equals("oldest")) {				// convert relative to absolute time
				getftime = oldTime(listOfFolders, ctmap) + getftime;
				CTinfo.debugPrint("getDataMap, oldTime: "+getftime);
				rmode = "absolute";
			}
			else if(rmode.equals("newest")) {
				getftime = newTime(listOfFolders, ctmap) - duration - getftime;
				CTinfo.debugPrint("newest getftime: "+getftime);
				rmode = "absolute";
			}
			else if(rmode.equals("after")) {
				double newtime = newTime(listOfFolders, ctmap);
				double tdur = newtime - getftime;
				if(tdur > duration) getftime = newtime - duration;		// galump if nec to get most recent duration
				getftime += 0.000001;									// microsecond after (no overlap)
				CTinfo.debugPrint("after duration: "+duration);
				rmode = "absolute";
			}
			
			// pre-gather all file sub-folders (i.e. folders in zip files) as linear list.
			// sort/search on that, with references back to zip/folder/files

			double endtime = getftime + duration;
			int gotdata = 0;

			// one-pass, gather list of candidate folders
			for(int i=0; i<listOfFolders.length; i++) {						// find range of eligible folders 
				CTFile folder = listOfFolders[i];
				CTinfo.debugPrint("CTreader checking folder: "+folder.getMyPath()+", start: "+getftime+", end: "+endtime);
//				if(!recurse) {		// push thru all lower-level recursive checks
					if(i>1) {													// after end check
						double priorftime;	
//						if(duration == 0. && !rmode.equals("next")) 	
//									priorftime = listOfFolders[i].fileTime();	// can't be any following if duration is zero
//						else 		priorftime = listOfFolders[i-1].fileTime();	// go 2 past to be sure get "next" points in candidate list?				
						priorftime = listOfFolders[i-1].fileTime();	// go 2 past to bracket "next/prev" points in candidate list
						if(priorftime > endtime) break;							// done	
					}

					int ichk = i+1;		
					if(rmode.equals("prev")) ichk = i+2;	// include prior frame for "prev" request
					if(ichk<listOfFolders.length) {								// before start check
						if(listOfFolders[ichk].fileTime() < getftime) continue;	// keep looking
					}
//				}
				
				CTinfo.debugPrint("CTreader got candidate folder: "+folder.getMyPath());
				if(!folder.isFileFolder()) {						// folder-of-folders
					if(folder.fileType == CTFile.FileType.FILE) {
						CTinfo.debugPrint("CTreader diving into subfolder: "+folder+", ctmap.size: "+ctmap.size()+", duration: "+duration);
						getDataMap(ctmap, folder, getftime, duration, rmode, true);	// recurse getDataMap() instead? 
					} else {
						CTinfo.debugPrint("CTreader gathering zip entries from: "+folder);
						CTFile[] listOfFiles = folder.listFiles();		// exhaustive search one-deep (bleh)
						for(int j=0; j<listOfFiles.length; j++) gotdata += gatherFiles(listOfFiles[j], ctmap);
						CTinfo.debugPrint("got zip folder: "+folder+", gotdata: "+gotdata);
					}
				}
				else {
					gotdata += gatherFiles(folder, ctmap);		// data-file folder
					CTinfo.debugPrint("gathered file folder: "+folder+", gotdata: "+gotdata);
				}
			}

//			CTinfo.debugPrint("ctmap: "+ctmap.getName(0)+", total data: "+ctmap.datasize());
		} catch (Exception e) {
			e.printStackTrace();
		}

		// loop thru ctmap chans, prune ctdata to timerange (vs ctreader.getdata, ctplugin.CT2PImap)
		if(!recurse) ctmap.trim(getftime,  duration, rmode);	
		return ctmap;				// last folder
	}
		
	//--------------------------------------------------------------------------------------------------------
	// gatherFiles:  gather all files in folder into CTmap

	private long gatherFiles(CTFile folder, CTmap cm) throws Exception {
		if(folder == null) {
			System.err.println("gatherFiles, null folder!");
			return 0;
		}
		double ftime = folder.fileTime();
		long hasdata = 0;

		CTFile[] listOfFiles = folder.listFiles();			// get list of files in folder
		if(listOfFiles == null) return 0;
		for(int j=0; j<listOfFiles.length; j++) {
			CTFile file = listOfFiles[j];
			if(file.isFile()) {
				String fileName =  file.getName();
				if(!cm.checkName(fileName)) continue;		// not a match 
				byte[] data = null;
				if(!timeOnly) data = file.read();
 				if(timeOnly || (data != null && data.length>0)) { 
					if(file.isTFILE()) fileName = file.getName();
					cm.add(fileName, new CTdata(ftime, data, file));			// squirrel away CTfile ref for timerange info??
					if(data != null) hasdata+=data.length;
					long dlen = data!=null?data.length:0;
					CTinfo.debugPrint("Put file: "+file.getPath()+", size: "+dlen+", "+"ftime: "+ftime+", from zipFile: "+file.getMyZipFile());
				}
			} 
		}
		return hasdata;
	}
/*	
	//--------------------------------------------------------------------------------------------------------
	// containsFile:  see if folder contains a file (channel) in ctmap

	private boolean containsFile(CTFile[] listOfFiles, CTmap cm)  {
//		System.err.println("containsFile: "+listOfFiles);
		if(listOfFiles == null) return false;
		for(int j=0; j<listOfFiles.length; j++) {
			CTFile file = listOfFiles[j];
//			System.err.println(file.getName()+", check: "+cm.checkName(file.getName()));
			if(file.isFile()) {
				String fileName =  file.getName();
				if(cm.checkName(fileName)) return true;		// got a match 
			} 
		}
		return false;
	}
*/	
}



