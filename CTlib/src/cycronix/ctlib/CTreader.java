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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
	private static boolean debug = false;

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

	public void setDebug(boolean dflag) {
		debug = dflag;
		if(debug) System.err.println("debug: "+debug);
	}
	
	boolean timeOnly=false;
	public void setTimeOnly(boolean tflag) {		// clumsy...  
//		timeOnly = tflag;							// doesn't work for blockdata where time is derived from block-interval/points
	}
	
//---------------------------------------------------------------------------------	   
//get:  direct-fetch time+data method
		
	public CTdata getData(String chan, double tget, double tdur, String tmode) {
		return getData(null, chan, tget, tdur, tmode);
	}
	
//---------------------------------------------------------------------------------	   
//get:  direct-fetch time+data method
// note:	this uses multi-channel ctmap internally, but only returns one channel (ctdata)
//			retain ctmap as a cache?  prefetch from ctmap, fill in balance from files...
	
//	private Map<String, CTFile> CTFileMap = new TreeMap<String, CTFile>();		// cache
	
	public CTdata getData(String source, String chan, double tget, double tdur, String tmode) {
		CTmap ctmap= new CTmap(chan);
//		CTFile sourceFolder = CTFileMap.get(source);
		CTFile sourceFolder;
//		if(sourceFolder == null) {
			//		CTFile sourceFolder;
			if(source == null) 	sourceFolder = new CTFile(rootFolder);
			else				sourceFolder = new CTFile(rootFolder+File.separator+source);
//			if(source != null) CTFileMap.put(source, sourceFolder);
			//		else				sourceFolder = new CTFile(rootFolder+"/"+source);
//		}
//		else{
//			if(debug) System.err.println("cache hit on getData source: "+source);
//		}
		try {
			ctmap = getDataMap(ctmap, sourceFolder, tget, tdur, tmode);		// time units = seconds
		} 
		catch(Exception e) {
			System.err.println("oops, exception: "+e+", ctmap: "+ctmap);
		}

		//			System.err.println("getTimeData: tget: "+tget);
//		CTdata tdata = ctmap.getTimeData(chan, lastGotTime, tdur);			// global lastGotTime can collide?
		CTdata tdata = ctmap.getTimeData(chan, ctmap.refTime, tdur, tmode);

		// if packed-data type data, trim tdata to spec time range (absolute time)
//		if(tdata != null) tdata = tdata.timeRange(wordSize(fileType(chan)), lastGotTime, tdur);	
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
				if(files[j].isDirectory()) ftime = oldTime(files, ctmap);	// recurse
				if(ftime > 0.) {
					if(debug) System.err.println("oldTime, ftime: "+ftime+", folderTime: "+listOfFolders[idx].fileTime());
					return listOfFolders[idx].fileTime();		// return parent folder containing file as oldTime (could precede block-endTime)
				}
			}

			if(files[0].length() <= 0) continue;
			if((ctmap != null) && !containsFile(files, ctmap)) continue;		// only folders with chan(s)
			
			ftime = listOfFolders[idx].fileTime();			// try
			if(ftime == 0.) continue;						// oldest is first timed file
			break;
		}
		return ftime;
	}
	
//---------------------------------------------------------------------------------	
// newTime:  find newest time for this source	(neglects block-duration)
	CTFile newFile = null;
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
		newFile = null;
		double ftime = 0.;
		
		int idx=listOfFolders.length-1;
		for(; idx>=0; idx--) {		// rewind to actual data
			CTFile[] files = listOfFolders[idx].listFiles();
			if(files == null) continue;
			if(files.length == 0) continue;

			for(int j=0; j<files.length; j++) {
				if(files[j].isDirectory()) {
					ftime = newTime(files, ctmap);	// recurse
				}
				if(ftime > 0.) return ftime;
			}

			if((ctmap != null) && !containsFile(files, ctmap)) continue;	// only folders with chan(s)

			for(int j=0; j<files.length; j++) {
				if(files[j].isDirectory()) {
					double trytime = files[j].fileTime();
					if(trytime > ftime) {
						ftime = trytime;
						newFile = files[j];		// for efficient external ref (cluge)
					}
				}
			}
			double trytime = listOfFolders[idx].fileTime();				// try
			if(trytime > ftime) ftime = trytime;
			if(ftime == 0.) continue;
			break;
		}
		return ftime;
	}

	//---------------------------------------------------------------------------------	
	// listChans:  build list of channels all folders

	public ArrayList<String> listChans(String sfolder) {
		if(sfolder.indexOf(File.separator)<0) {
			sfolder = rootFolder + File.separator + sfolder;		// auto-fullpath
			if(debug) System.err.println("adding rootfolder to sfolder: "+sfolder);
		}
//		System.err.println("listChans: "+sfolder);
		
		CTFile sourceFolder = new CTFile(sfolder);

		ArrayList<String> ChanList = new ArrayList<String>();		// for registration
		ChanList.clear();										// ChanList built in listFiles()
		CTFile[] listOfFiles = sourceFolder.listFiles();
//		if(debug) System.err.println("listChans, sfolder: "+sfolder+", listOfFiles: "+listOfFiles);
		if(listOfFiles == null || listOfFiles.length < 1) return null;

		for(int i=0; i<listOfFiles.length; i++) {
			CTFile thisFile = listOfFiles[i];
//			if(debug) System.err.println(i+" listChans: "+thisFile+", dir: "+thisFile.isDirectory()+", fileType: "+thisFile.fileType);
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
//		if(debug) System.err.println("buildChanList, folder: "+sourceFolder+", listOfFiles.length: "+listOfFiles.length);

		for(int i=0; i<listOfFiles.length; i++) {
			CTFile thisFile=listOfFiles[i];
//			if(debug) System.err.println("buildChanList(), thisFile: "+thisFile+", isFile: "+thisFile.isFile()+", len: "+thisFile.length());
			if(thisFile.isFile() && thisFile.length() <= 0) continue;
			if(thisFile.isDirectory() && thisFile.fileTime()>0) {
//				if(debug) System.err.println("buildChanList got folder: "+thisFile+", recurse: "+thisFile);
				buildChanList(thisFile,ChanList);		// recursive ?
			}
			else {
				// side effect:  build ChanList for registration
				String fname = listOfFiles[i].getName();
				if(ChanList.indexOf(fname) < 0)  {
					ChanList.add(fname);	// add if not already there
//					if(debug) System.err.println("Chanlist.add: "+fname);
				}
			}
		}
	}
	
	//---------------------------------------------------------------------------------	
	// listSources:  build list of sources (folders at top level rootFolder)

	public ArrayList<String> listSources() {
		ArrayList<String> SourceList = new ArrayList<String>();		// for registration
		CTFile[] listOfFolders = new CTFile(rootFolder).listFiles();
		if(debug) System.err.println("listSources, rootFolder: "+rootFolder+", listOfFolders: "+listOfFolders);
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
	
	public ArrayList<String> listSourcesRecursive() throws IOException {
		final ArrayList<String> SourceList = new ArrayList<String>();		// for registration
		Path rootPath = new CTFile(rootFolder).toPath();
		final int nroot = rootPath.getNameCount();

		Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException
			{
//				System.err.println("walkFileTree file: "+file);
				return FileVisitResult.CONTINUE;
			}
			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException e)
					throws IOException
			{
				if (e == null) {
//					System.err.println("walkFileTree dir: "+dir);
					String thisFolder = dir.getFileName().toString();
			  		try {
			  			Long.parseLong(thisFolder);
			  		} catch(NumberFormatException en) {
//			  			System.err.println("dir: "+dir+",rootFolder: "+rootFolder+", nroot: "+nroot);
			  			int npath = dir.getNameCount();
			  			String thisPath = dir.getName(npath-1).toString();
//			  			for(int i=(npath-2); i>0; i--) thisPath = dir.getName(i) + File.separator + thisPath; 
			  			for(int i=(npath-2); i>=nroot; i--) thisPath = dir.getName(i) + File.separator + thisPath; 	// skip rootPath

			  			if(thisPath.length() >0) {
			  				SourceList.add(thisPath);		// only add if not a time-number name
//							System.err.println("walkFileTree add: "+thisPath);
			  			}
			  		}
			  		
					return FileVisitResult.CONTINUE;
				} else {
					// directory iteration failed
					throw e;
				}
			}
		});
		
		Collections.sort(SourceList);
		return SourceList;
	}
	
	//---------------------------------------------------------------------------------	
	// do the file checking and return CTmap channel map of Time-Data

	double lastGotTime=0;			// global for use in timeRange (cluge)
	public CTmap getDataMap(CTmap ctmap, String rootfolder, double getftime, double duration, String rmode) {
		return(getDataMap(ctmap, new CTFile(rootfolder), getftime, duration, rmode));
	}
	
	private CTmap getDataMap(CTmap ctmap, CTFile rootfolder, double getftime, double duration, String rmode) {
		return(getDataMap(ctmap, rootfolder, getftime, duration, rmode, null));
	}
	
	private CTmap getDataMap(CTmap ctmap, CTFile rootfolder, double getftime, double duration, String rmode, CTFile prevFolder) {
		if(debug) System.err.println("getDataMap, rootfolder: "+rootfolder+", getftime: "+getftime+", duration: "+duration+", rmode: "+rmode+", chan[0]: "+ctmap.getName(0)+", ctmap.size: "+ctmap.size());
		try {
			// get updated list of folders
			CTFile[] listOfFolders = rootfolder.listFolders();	// folders only here
			if(listOfFolders == null || listOfFolders.length < 1) return ctmap;
			
			int ioffset=0;
			boolean nextflag=false;
			if(rmode.startsWith("next")) {		// no longer implemented (bleh)
				nextflag = true;
				if(rmode.length()>4) ioffset = Integer.parseInt(rmode.substring(4));	// optional offset index (not used)
			};				// single step fwd/rvs
			boolean prevflag=false;
			if(rmode.startsWith("prev")) {
				prevflag = true;
				if(rmode.length()>4) ioffset = Integer.parseInt(rmode.substring(4));
			}
			
			if(rmode.equals("registration")) {				// handle registration
				System.err.println("unexpected registration request!");
				return ctmap;
			}
			else if(rmode.equals("oldest")) {				// convert relative to absolute time
				getftime = oldTime(listOfFolders, ctmap) + getftime;
				rmode = "absolute";
			}
			else if(rmode.equals("newest")) {
				getftime = newTime(listOfFolders, ctmap) - duration - getftime;
				if(debug) System.err.println("newest getftime: "+getftime);
				rmode = "absolute";
			}
			else if(rmode.equals("after")) {
				double newtime = newTime(listOfFolders, ctmap);
				double tdur = newtime - getftime;
				if(tdur > duration) getftime = newtime - duration;		// galump if nec to get most recent duration
				getftime += 0.000001;									// microsecond after (no overlap)
//				else if(tdur < duration) duration = tdur;				// or just let it run off end?
				if(debug) System.err.println("after duration: "+duration);
				rmode = "absolute";
			}
			
			// pre-gather all file sub-folders (i.e. folders in zip files) as linear list.
			// sort/search on that, with references back to zip/folder/files

			double endtime = getftime + duration;
			if(prevflag) {
				endtime = getftime;
				getftime = endtime - duration;
			}
			ctmap.refTime = getftime;		// for outside ref when newest/oldest update time (cluge)

			// special case:  prevflag past EOF
			if(prevflag && getftime > listOfFolders[listOfFolders.length-1].fileTime()) {
				gatherFiles(listOfFolders[listOfFolders.length-1], ctmap);
				return ctmap;
			}
			
			// (1) first-pass, make a new listOfFolders with candidates
			ArrayList<CTFile> FileList = new ArrayList<CTFile>();
			for(int i=0; i<listOfFolders.length; i++) {						// find range of eligible folders 
				CTFile folder = listOfFolders[i];
//				if(debug) System.err.println("getDataMap, try folder: "+folder.getName()+", ftime: "+folder.fileTime()+", getftime: "+getftime);
				if(i>1) {													// after end check
					double priorftime;		
//					if(duration==0) priorftime = listOfFolders[i].fileTime();	// duration=0 means at-or-before
//					else			
						priorftime = listOfFolders[i-1].fileTime();	// go 2 past to be sure get "next" points in candidate list?
					
//					if(debug) System.err.println("break, ftime["+i+"]: "+listOfFolders[i].fileTime()+", priorftime: "+priorftime+", endtime: "+endtime);
					if(priorftime > endtime) break;												// done	
				}

				int ichk = (prevflag?i+2:i+1);
				if(ichk<listOfFolders.length) {								// before start check
					if(listOfFolders[ichk].fileTime() < getftime) continue;	// keep looking
				}
				
//				if(debug) System.err.println("--candidate folder: "+folder.getName()+", isFileFolder: "+folder.isFileFolder()+", ftime: "+folder.fileTime());
				if(!folder.isFileFolder()) {		// folder-of-folders
					CTFile[] listOfFiles = folder.listFiles();
					for(int j=0; j<listOfFiles.length; j++) {
//					for(CTFile file:folder.listFiles()) {				
//						if(listOfFiles[j==0?0:j-1].fileTime() > endtime) break;	// redundant limit logic to above; TO DO:  recursive any-depth	
//						ichk = (prevflag?j+2:j+1);
//						if(ichk<listOfFiles.length) {								// before start check
//							if(listOfFiles[ichk].fileTime() < getftime) continue;	// keep looking
//						}
						FileList.add(listOfFiles[j]);
					}
				}
				else	FileList.add(folder);		// data-file folder
			}
				
			// (2) second-pass, extract requested data from arraylist of candidate files
			int nfile = FileList.size();
			if(debug) System.err.println("2nd pass, FileList.size: "+nfile);

			int gotdata = 0;
			// just gather all file data from prospective folders, let CTdata/timeRange sub-set, rely on caching...
			boolean gatherAll=true;
			if(CTmap.fileType(ctmap.getName(0)) == 's') gatherAll=false;
			if(debug) System.err.println("gatherAll: "+gatherAll);
			if(gatherAll) {
				for(int i=0; i<nfile; i++) gotdata += gatherFiles(FileList.get(i), ctmap);					
			}
			else {
				for(int i=0; i<nfile; i++) {
					CTFile folder = FileList.get(i);
					//				System.err.println("2nd pass, folder: "+folder.getName()+", filetime: "+folder.fileTime());
					double ftime = folder.fileTime();

					int nextidx = (i<(nfile-1))?(i+1):i;
					double nextftime = FileList.get(nextidx).fileTime();	// ref next folder for embedded binary arrays

					// single point
					if(duration == 0.) {			
						if(nextflag) {
							if(ftime > getftime) {								// after 
								for(int j=i+ioffset; j<nfile; j++) {			// skootch forward to find channel
									if(gatherFiles(FileList.get(j), ctmap) > 0) break;
								}
							}
						}
						else {	
							if(ftime >= getftime) {								// at-or-before 
								int sidx = i;
								if(prevflag || (ftime > getftime)) sidx = i-1;	// strictly before
								//							int sidx = prevflag?(i-1):i;		
								if(prevflag && sidx<0) break;					// none available
								if(sidx<0) sidx = 0;;			

								for(int j=sidx-ioffset; j>=0; j--) {			// skootch to prior folder
									if(gatherFiles(FileList.get(j), ctmap) > 0) break;
								}
							}
						}

						if(ctmap.hasData()) break;								// got it
					}

					// interval of data
					else {			// could check for and use nextftime only for binary data?
						//					if((nextftime >= getftime) && (ftime<=endtime)) 	// bleh:  this drops data of interest for block-mode times at end of block
						if(ftime < getftime) continue;		// this can drop data of interest??
						if(debug) System.err.println("ftime: "+ftime+", getftime: "+getftime+", nextftime: "+nextftime+", endtime: "+endtime);
						// just gather more than enough data and let CTdata.timeRange extract subset of interest
						gatherFiles(folder, ctmap);
						if(ftime > endtime) break;		// done
					}

				}	
			}

			if(debug) System.err.println("ctmap: "+ctmap.getName(0)+", gotdata: "+gotdata);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return(ctmap);			// last folder
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
//		if(debug) System.err.println("gatherFiles: "+folder.getName()+", listOfFiles.length: "+listOfFiles.length);
		if(listOfFiles == null) return 0;
		for(int j=0; j<listOfFiles.length; j++) {
			CTFile file = listOfFiles[j];
//			if(debug) System.err.println("check file: "+file.getName()+", isFile: "+file.isFile());
			if(file.isFile()) {
				String fileName =  file.getName();
//				if(debug) System.err.println("check fileName: "+fileName);
				if(!cm.checkName(fileName)) continue;		// not a match 
				byte[] data = null;
				if(!timeOnly) data = file.read();
//				if(debug) System.err.println("fileName: "+fileName+", data.length: "+data.length);
 				if(timeOnly || (data != null && data.length>0)) { 
//					cm.add(fileName, new CTdata(ftime, data));
					if(file.isTFILE()) fileName = file.getName();
					cm.add(fileName, new CTdata(ftime, data, file));			// squirrel away CTfile ref for timerange info??
					if(data != null) hasdata+=data.length;
//					cm.add( fileName,processW(ftime, data, swapFlag, fileType(fileName)) );
					long dlen = data!=null?data.length:0;
					if(debug) System.err.println("Put file: "+folder+"/"+fileName+", size: "+dlen+", "+new Date((long)(1000*ftime))+", from zipFile: "+file.getMyZipFile());
//					if(debug) System.err.println("myZipFile: "+file.getMyZipFile()+", myPath: "+file.getMyPath());
					// extract rootTime from myZipFile, add to CTdata object... then in CTdata TimeRange use for dt in block data
				}
			} 
		}
		return hasdata;
	}
	
	//--------------------------------------------------------------------------------------------------------
	// containsFile:  see if folder contains a file (channel) in ctmap

	private boolean containsFile(CTFile[] listOfFiles, CTmap cm)  {
//		System.err.println("containsFile: "+listOfFiles);
		if(listOfFiles == null) return false;
		for(int j=0; j<listOfFiles.length; j++) {
			CTFile file = listOfFiles[j];
//			System.err.println("containsFile: "+file.getName()+", check: "+cm.checkName(file.getName()));
			if(file.isFile()) {
				String fileName =  file.getName();
				if(cm.checkName(fileName)) return true;		// got a match 
			} 
		}
		return false;
	}
	
}



