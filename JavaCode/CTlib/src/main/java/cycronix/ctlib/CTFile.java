package cycronix.ctlib;

//---------------------------------------------------------------------------------	
//CTFile:  extended File class, list file and/or zip entries
//Matt Miller, Cycronix
//02/18/2014

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
//import java.nio.file.Path;
//import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * CloudTurbine utility class that extends File class to include zip-files
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
* 07/21/2014  MJM	Added caching logic.
*/

//---------------------------------------------------------------------------------	
//CTFile:  extended File class, list file and/or zip entries

class CTFile extends File {
	static final long serialVersionUID = 0;		// to make extends File happy

//	private boolean isZip=false;		// parent zip file.zip
//	private boolean isEntry=false;		// internal zip folder
//	private boolean isFile=false;		// internal zip file 
	private String  myPath=null;
	private String 	myZipFile=null;
	private String[] myFiles=null;
	private Map<String, String[]> zipMap;

	enum FileType {
		FILE,						// regular file or folder
		ZIP,						// parent.zip file
		ZENTRY,						// embedded zip folder
		ZFILE,						// embedded zip file
		TFOLDER,					// time-folder (e.g. 123456789.jpg)
		TFILE						// time-folder-data 
	}
	public FileType fileType = FileType.FILE;		// default is regular file
	
	//---------------------------------------------------------------------------------	
	// constructors
	
	/**
	 * New CTfile with parent .zip or regular file
	 * 
	 * @param path file path to new CTFile
	 */
	CTFile(String path) {
		super(path = convertGzip(path));
//		System.err.println("new CTFile path: "+path);
		myPath = new String(path);

		if(path.endsWith(".zip") || path.endsWith(".gz")) fileType = FileType.ZIP;
		if(isTFILE()) fileType = FileType.TFOLDER;		// need better filter

//		isZip = path.endsWith(".zip") || path.endsWith(".gz");
//		if(isZip) {
		if(fileType == FileType.ZIP) {
			myZipFile = path;
//			ZipMap(path);		// set zipmap at initial .zip build
		}
	}

	// zip File 
	private CTFile(String path, String myzipfile, String mypath) {
		super(path);
		myPath = new String(mypath);		// note:  here myPath is short zip-entry name (inconsistent)
		
		// following to give usable path so that fileTime works.  but other places breaks ??!!
		myPath = myzipfile.substring(0,myzipfile.lastIndexOf('.')) + File.separator + myPath;	// full effective zip entry path for relative timestamps to work

		myZipFile = myzipfile;
//		isFile = true;
		fileType = FileType.ZFILE;
	}

	// zip Folder (entry)
	private CTFile(String path, String[] files, String myzipfile) {
		super(path);
		myZipFile = myzipfile;
		myPath = new String(path);
    	myPath = myzipfile.substring(0,myzipfile.lastIndexOf('.')) + File.separator + myPath;	// full effective zip entry path for relative timestamps to work

    	myFiles = files;				// this can clobber pre-existing files at this path, need to append
		if(files == null) fileType = FileType.ZFILE;
		else			  fileType = FileType.ZENTRY;
//		if(files == null) isFile=true;		// top-level files in zip
//		else			  isEntry=true;
	}

	//---------------------------------------------------------------------------------	
	// convert .gz to .zip
	private static String convertGzip(String path) {
		if(path.endsWith(".gz")) {
			path = gunzip(path);
		}
		return path;
	}
	
	//---------------------------------------------------------------------------------	
	/**
	 * Gets name of parent zipfile for this file
	 * @return name of zipfile
	 */
	String getMyZipFile() {
		return myZipFile;
	}
	
	public String getParent() {
		if(myZipFile!=null) return myZipFile;
		else				return super.getParent();
	}
	
	//---------------------------------------------------------------------------------	
	/**
	 * get path of this file
	 * @return this file path
	 */
	String getMyPath() {
		return myPath;
	}
	
	//---------------------------------------------------------------------------------	
	/**
	 * TFILE:  a "time-file" where time is in file name vs parent folder name
	 * @return is this file a time file?
	 */
	// 
	boolean isTFILE() {
		File pfolder = getParentFile();
//		return (pfolder!=null && (fileTime(pfolder.getName()) == 0) && isFile() 		// any non-Time folder file is TFILE?
		return (isFile() && myPath.endsWith(".jpg") 						// any non-Time folder file is TFILE? (use fullpath of parent)
				&& pfolder!=null && (fileTime(pfolder.getPath())==0));		// for now, only .jpg files work
	}
	//---------------------------------------------------------------------------------	
	/**
	 * Get filename as either native file or zip entry name
	 */
	public String getName() {
		switch(fileType) {
		case ZIP:
			//		if(isZip) {
			//			Path p = Paths.get(myPath).getFileName();		
			String p = fileName(myPath);					// Java 1.6 compat XX
			//			System.err.println("p1: "+p.toString()+", p2: "+fileName(myPath));
			//			return p.toString().replace(".zip","");	
			return p.replace(".zip","");
			//		}
		case ZENTRY:
			//		else if(isEntry) {
//			return myPath;
			return fileName(myPath);
			//		}
		case ZFILE:
			//		else if(isFile) {
			//			System.err.println("p3: "+Paths.get(myPath).getFileName()+", p4: "+fileName(myPath));
			//			return Paths.get(myPath).getFileName().toString();
//			System.err.println("getName ZFILE, myPath: "+myPath+", fileName: "+fileName(myPath));
			return fileName(myPath);					// Java 1.6 compat XX
			//		}
		case TFILE:
			String tp = new File(myPath).getParentFile().getName();
//			System.err.println("getName, myPath: "+myPath+", tp: "+tp);
			return tp+".jpg";			// TO DO:  return other fileTypes 
			
		default:
			//		else 	  
			return super.getName();
		}
	}

	/**
	 * list files in this folder.  list zip-entries if zip file
	 */
	public CTFile[] listFiles() {
		CTFile[] clist = null;
		
//		System.err.println("listFiles for: "+myPath+", fileType: "+fileType);
		switch(fileType) {
		case ZIP:				// top level file.zip
			if(zipMap==null || zipMap.size()==0) ZipMap(myZipFile);		// delayed zipmap build?

			Object[] sfiles = zipMap.keySet().toArray();				// need to concat dupes?
			clist = new CTFile[sfiles.length];

			for(int i=0; i<sfiles.length; i++) {
//				System.err.println("ZIP file: "+sfiles[i]+", zipmap.get: "+zipMap.get(sfiles[i]));
				String[] files = zipMap.get(sfiles[i]);
				clist[i] = new CTFile((String) sfiles[i], files, myZipFile);	
			}

			Arrays.sort(clist, fileTimeComparator);			// zip files in order of write, not guaranteed time-sorted
//			System.err.println("sorted zips:");
//			for(CTFile c:clist) System.err.println(c);
			return clist;

		case ZENTRY:				// embedded zip folder
			if(myFiles==null) return null;
			clist = new CTFile[myFiles.length];

			for(int i=0; i<myFiles.length; i++) {
				//				String fname = Paths.get(myFiles[i]).getName(0).toString();	
				String fname = myFiles[i].split(File.pathSeparator)[0];		// Java 1.6 compat
//				System.err.println("ZENTRY, myFiles["+i+"]: "+myFiles[i]+", fname: "+fname);
				clist[i] = new CTFile(fname,myZipFile,myFiles[i]);
			}
			Arrays.sort(clist, fileTimeComparator);			// zip files in order of write, not guaranteed time-sorted
			return clist;	// return its list of files

		case TFOLDER:
			clist = new CTFile[1];
			clist[0] = new CTFile(super.getPath());		// return own name 
			clist[0].fileType = FileType.TFILE;
			return clist;
			
		default:				// conventional file...
//			File[] flist = super.listFiles();
			File[] flist = super.listFiles(new FileFilter() {
			    @Override
			    public boolean accept(File file) {
			        return !file.isHidden();			// skip hidden files
			    }
			});
			if(flist == null) return null;
			clist = new CTFile[flist.length];
			for(int i=0; i<clist.length; i++) {
				clist[i] = new CTFile(flist[i].getPath());
			}
			Arrays.sort(clist, fileTimeComparator);		// make sure sorted (newTime etc presumes)
			return clist;		// wrap in CTFile class
		}
		
	}

	/**
	 * file-list pruned to only include folders
	 * @return CTfile[] array of folders
	 */
	// file-list pruned to only include folders (inefficient?)
	CTFile[] listFolders() {
		CTFile[] filelist = this.listFiles();
		if(filelist == null) {
//			System.err.println("CTFile: Empty folder-list!");
			return null;
		}
		CTFile[] folderlist = new CTFile[filelist.length];
		int count=0;
		for(CTFile f:filelist) if(f.isDirectory()) folderlist[count++]=f;
		CTFile[] prunedlist = new CTFile[count];
		System.arraycopy(folderlist, 0, prunedlist, 0, count);
//		Arrays.sort(prunedlist, fileTimeComparator);		// listFiles already sorted
		return prunedlist;
	}

	/**
	 * file-list pruned to only include folders containing CTmap channel(s)
	 * @return CTfile[] array of folders
	 */
	// file-list pruned to only include folders (inefficient?)
	CTFile[] listFolders(CTmap ctmap) {
		CTFile[] filelist = this.listFiles();
		if(filelist == null) {
//			System.err.println("CTFile: Empty folder-list!");
			return null;
		}
		CTFile[] folderlist = new CTFile[filelist.length];
		int count=0;
		for(CTFile f:filelist) if(f.isDirectory() && f.containsFile(ctmap)) folderlist[count++]=f;
		CTFile[] prunedlist = new CTFile[count];
		System.arraycopy(folderlist, 0, prunedlist, 0, count);
//		Arrays.sort(prunedlist, fileTimeComparator);		// listFiles already sorted
		return prunedlist;
	}
	
	public boolean isDirectory() {
		switch(fileType) {
		case ZIP:		return true;
		case ZENTRY:	return true;
		case ZFILE:		return false;
		case TFOLDER: 	return true;			// TFOLDER is just a time-named file.jpg
		case TFILE: 	return false;
		default:		return super.isDirectory();
		}
/*
		if(isZip) 			return(true);
		else if(isEntry) 	return(true);
		else if(isFile)		return(false);
		else	  			return super.isDirectory();
*/
	}

	/**
	 * test if this CTFile is a container (folder)
	 * @return true/false 
	 */
	boolean isFileFolder() {
		switch(fileType) {
		case ZIP:		return false;
		case ZENTRY:	return true;
		case ZFILE:		return false;
		case TFOLDER:	return true;
		case TFILE:		return false;
		default:	
			if(super.isDirectory()) {
				File[] files = super.listFiles();
//				if(files!=null && files.length>0 && files[0].isFile()) return true;
				// check last (vs first) file to avoid .DS_Store or other hidden files messing up test (MJM 7/12/16)
				String checkname = files[files.length-1].getName();
//				System.err.println("isFileFolder, files.length: "+files.length+", checkname: "+checkname+", isTimeFile: "+isTimeFile(checkname) );
				if(files!=null && files.length>0 && !isTimeFile(checkname)) return true;
				else							  return false;		// needs testing
			} else return false;
		}
/*		
		if(isZip) 			return false;
		else if(isEntry) 	return true;
		else if(isFile)		return false;
		else{
			if(super.isDirectory()) {
				if(super.listFiles()[0].isFile()) return true;
				else							  return false;		// needs testing
			} else return false;
		}
*/
	}
	
	/**
	 * test if this CTFile is a "file" (non-folder)
	 */
	public boolean isFile() {
		return !isDirectory();
	}

	/**
	 * size of file or zip-entry
	 */
	public long length() {
		long len=0;
		switch(fileType) {
		case ZIP:	
			if(zipMap==null) return 0;
			//		if(isZip) {
			len = zipMap.size();
			break;
			//		}
		case ZENTRY:
			//		else if(isEntry) {
			if(myFiles == null) len = 0;		// myFiles is null if zip has files (not folders) at top level
			else				len = myFiles.length;	
			break;
			//		}
//		case TFILE:				// get actual size?
		case ZFILE:
			//		else if(isFile) 
			len = 1;		// FOO, need number of bytes in entry
			break;
		default:
			//		else 
			len = super.length();
			break;
		}
		return len;
	}

//	private byte[] myData=null;				// cache?
//	static private TreeMap<String, byte[]> DataCache = new TreeMap<String, byte[]>();		// cache (need logic to cap size)
	// cache limits, max entries, max filesize any entry, jvm size at which to dump old cache...
	private static final int MAX_ENTRIES = 10000;			// limit total entries in cache	(10K led to "too many open files"?)
	private static final int MAX_FILESIZE = 20000000;		// 20MB.  max size any individual entry
	private static final int MAX_JVMSIZE = 200000000;		// 200MB. max overall JVM memory use at which to dump old entries
	
	static private LinkedHashMap<String, byte[]> DataCache = new LinkedHashMap<String, byte[]>() {
		 protected boolean removeEldestEntry(Map.Entry  eldest) {
			 	Runtime runtime = Runtime.getRuntime();
			 	long usedmem = runtime.totalMemory() - runtime.freeMemory();
//			 	System.err.println("usedmem: "+usedmem+", size: "+size());
	            return ((size() >  MAX_ENTRIES) || (usedmem > MAX_JVMSIZE));
	         }
	};		// cache (need logic to cap size based on memory vs number of entries)

	/**
	 * CTfile reader.  uses parent File read if native file, else reads data from within zip-entry
	 * @return byte[] data read
	 */
	byte[] read() {
		String cacheKey = myZipFile+":"+myPath;
//		String cacheKey = myPath;	// myPath includes zipfile name?  Nope, inconsistent but myPath for zip-entry is not full time-path
//		System.err.println("cacheKey: "+cacheKey+", myPath: "+myPath);
		byte[] data = DataCache.get(cacheKey);					// need to add Source (folder) to myPath. now: Tstamp/Chan
		if(data != null) {
//			System.err.println("cache hit on data read: "+myPath);
			return data;					// use cached data
		}
		
//		if(myData!=null) return myData;		// cache
		switch(fileType) {
		case ZIP:
		case ZENTRY:
			return null;
//			if(isZip || isEntry) return null;
		case ZFILE:			// zipoutput
//		if(isFile) {		
			try {
				ZipFile zfile = new ZipFile(myZipFile);
				// note:  myPath for zip-entry is not full-path as it is with other CTFile...  <---FIXED and adjusted right below!
				String mypathfs = myPath.replace('\\','/');		// myPath with fwd slash
				
				// strip leading file path to get just the zip-entry part!  always will be time/name format.
				String[] subDirs = mypathfs.split(Pattern.quote("/"));	
				if(subDirs.length >= 2) mypathfs = subDirs[subDirs.length-2] + "/" + subDirs[subDirs.length-1];
				else					System.err.println("WARNING!!!  Unexpected zip-entry format: "+mypathfs);
				
				ZipEntry ze = zfile.getEntry(mypathfs);			// need fullpath!
				if(ze == null) {
					zfile.close();
					throw new IOException("Null ZipEntry, zipfile: "+myZipFile+", entry: "+mypathfs);
				}
				
				//		    		System.err.println("zip read, myZipFile: "+myZipFile+", myPath: "+myPath+", mypathfs: "+mypathfs);
				int zsize = (int)ze.getSize();
				data = new byte[zsize];
				InputStream zis = zfile.getInputStream(ze);
				int len, nread=0;
				while ((len = zis.read(data,nread,zsize-nread)) > 0) nread+=len;
				//		    		System.err.println("zip nread: "+nread+", ze.size: "+ze.getSize());
				zis.close();
				zfile.close();
			} catch(Exception e) {
				System.err.println("oops zipfile read failed: "+e+", zipfile: "+myZipFile+", entry: "+myPath);
				e.printStackTrace();
			}
			break;
//		}
		default:		// conventional file
//		else {		
			long fileLength = this.length();
			data = new byte[(int)(fileLength)];	// read file update
			try {
				java.io.FileInputStream fis = new java.io.FileInputStream(myPath);		
				int len, nread=0;
				while ((len = fis.read(data,nread,(int)(fileLength-nread))) > 0) nread+=len;
				//					System.err.println("file nread: "+nread);
				fis.close();
			} catch(Exception e) {
				System.err.println("oops file read failed: "+e);
			}
			break;
		}
		
//		System.err.println("cache: "+cacheKey+", size: "+data.length);
		if(data!=null && data.length <= MAX_FILESIZE) DataCache.put(cacheKey, data);		// only cache small files
		return data;
	}
	
	//---------------------------------------------------------------------------------	
	// ZipMap:  create index of zipped files
	// this probably should be a class with constructor...
	
	static private Map<String, Map<String, String[]>>ZipCache = new LinkedHashMap<String, Map<String, String[]>>() {
		 protected boolean removeEldestEntry(Map.Entry  eldest) {
	            return size() >  MAX_ENTRIES;
	         }
	};		// cache (need logic to cap size based on memory vs number of entries)
	
	private void ZipMap(String zipfile) {
		zipMap = ZipCache.get(myPath);
		if(zipMap != null) {
//			System.err.println("zip cache hit: "+myPath);
			return;					
		}
		zipMap = new TreeMap<String, String[]>();		// make a new one
		try{		//get the zip file content
//			System.err.println("Building ZipMap for: "+myPath);

			ZipFile zfile = new ZipFile(zipfile);
			Enumeration<? extends ZipEntry> zenum = zfile.entries();

			int numEntries = zfile.size();		// convert to array, easier loop control
			String[] entry = new String[numEntries];
			for(int i=0; i<numEntries; i++) entry[i] = zenum.nextElement().getName();
			Arrays.sort(entry);				// sort so that following add-logic gets all channels in same timestamp folder
			
			String thisfolder=null;
			ArrayList<String>flist=null;

			// loop thru zipfile entries, add them to TreeMap
			for(int i=0; i<numEntries; i++) {
//				Path pentry = Paths.get(entry[i]);
//				System.err.println("ZipMap entry["+i+"]: "+entry[i]);
//				String[] spentry = entry[i].split(File.separator);	// Java 1.6 compat
				String[] spentry = entry[i].split("/");				// Java 1.6 compat 

//				String folder = pentry.getName(0).toString();		
				String folder = spentry[0];
//System.err.println("folder: "+pentry.getName(0).toString()+", sfolder: "+spentry[0]);
//System.err.println("pcount: "+pentry.getNameCount()+", plen: "+spentry.length);
//				if(pentry.getNameCount() > 1) {
				if(spentry.length > 1) {							// Java 1.6 compat
					if(!folder.equals(thisfolder)) {			// new subfolder
						if(thisfolder!=null) zipMap.put(thisfolder, flist.toArray(new String[flist.size()]));
						thisfolder = folder;
						flist=new ArrayList<String>();
//						flist.add(pentry.toString());			// put first entry as value
						flist.add(entry[i]);			// Java 1.6 compat
					}
					else {
						if(thisfolder!=null) zipMap.put(thisfolder, flist.toArray(new String[flist.size()]));
						flist.add(entry[i]);			// Java 1.6 compat
					} 
//System.err.println("pentry: "+pentry.toString()+", entry: "+entry[i]);
				}
				else {
					zipMap.put(folder, null);
				}	
			}
			if(flist!=null && flist.size()>0 && thisfolder!=null) 					// wrap up
				zipMap.put(thisfolder, flist.toArray(new String[flist.size()]));

			zfile.close();
			
			ZipCache.put(myPath, zipMap);			// cache
			
//		} catch(IOException ex) { System.err.println("ZipMap Exception on zipfile: "+zipfile); ex.printStackTrace(); }
		} catch(Exception ex) { System.err.println("ZipMap Exception on zipfile: "+zipfile+", myPath: "+myPath); ex.printStackTrace(); }

	}

	//---------------------------------------------------------------------------------	
	static private String fileName(String path) {
//		System.err.println("path: "+path+", fileName: "+path.substring(path.lastIndexOf(File.separator) + 1));
//		return path.substring(path.lastIndexOf(File.separator) + 1);
		return new File(path).getName();
	}
	
	//---------------------------------------------------------------------------------	
	// unzip a gzip file
    private static String gunzip(String gfile) {
    	if(!gfile.endsWith(".gz")) return gfile;		// make sure is .gz file
    	String zfile = gfile.replace(".gz", "");
 	   	zfile = tempFile(zfile);		// need full path to avoid collisions?
    	File zFile = new File(zfile);
    	if(zFile.exists()) return zfile;				// done

    	byte[] buffer = new byte[65536];
    	try {
    		GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(gfile));
    		FileOutputStream out = new FileOutputStream(zfile);
 
    		int len;
    		while ((len = gzis.read(buffer)) > 0) out.write(buffer, 0, len);

    		gzis.close();
    		out.close();
    		zFile.deleteOnExit();						// auto-cleanup exported files
 
    	} catch (IOException ex){
    		ex.printStackTrace();   
    	}
//		System.err.println("Export "+gfile+" to zip: "+zfile);
    	return zfile;
   } 
    
    // create temporary file.  this still has issue:
    // folder-chain is "orphaned" (not deleted on exit)
    
    static long uniqueID = 0;
    private static String tempFile(String fileName){
    	if(uniqueID == 0) uniqueID = System.currentTimeMillis();
        String tempFile = System.getProperty("java.io.tmpdir") + File.separator + "com.cycronix.CloudTurbine" + File.separator + uniqueID + File.separator + fileName;
        File tfile = new File(tempFile);
        tfile.getParentFile().mkdirs();
        tfile.deleteOnExit();
        return tfile.getAbsolutePath();
   }
    
    
  //---------------------------------------------------------------------------------	
  //fileTime:  parse time from file name, units: full-seconds
    private SimpleDateFormat sdflast=null;
    boolean useFileTime=false;						// required if TFILE == isFile()
    
    /**
     * Parse time from the CTFile name, units: full-seconds
     * @return double time in seconds
     */
  	double fileTime() {
//  		return fileTime(getName());
  		return fileTime(getMyPath());	// provide full pathname (support relative timestamps)
  	}

  	/**
  	 * Parse file-time given string 
  	 * @param fname name of file to parse
  	 * @return double time in seconds
  	 */
  	
  	private double fileTime(String fname) {
//  		System.err.println("fileTime: "+fname);
  		// special logic for TFOLDER (e.g. camera time.jpg files)
  		if(fileType==FileType.TFOLDER) return tfolderTime(fname);
	
  		if(fname.endsWith(".zip")) fname = fname.substring(0,fname.length()-4);		// strip (only) trailing ".zip"
    	
		// new multi-part timestamp logic:  parse path up from file, sum relative times until first absolute fulltime
		String[] pathparts = fname.split(Pattern.quote(File.separator));
		Long sumtime = 0L;
		double ftime = 0.;
		
		for(int i=pathparts.length-1; i>=0; i--) {		// parse right to left
			String thispart = pathparts[i];
			Long thistime = 0L;
			try {
				thistime = Long.parseLong(thispart);
				sumtime += thistime;
			} catch(NumberFormatException e) {
				continue;		// keep looking?
			}
//			System.err.println("***fileTime fname: "+fname+", thispart: "+thispart+", thistime: "+thistime+", sumtime: "+sumtime);

			if(thistime >= 1000000000000L) {	// absolute msec
				ftime = (double)sumtime / 1000.;
//				System.err.println("******msec fileTime: "+ftime);
				return ftime;
			}
			if(thistime >= 1000000000L) {		// absolute sec
				ftime = (double)sumtime;
//				System.err.println("******sec fileTime: "+ftime);
				return ftime;
			}
		}
		
		return 0.;		// not a problem if a non-timestamp (e.g. channel) folder
  	}

    //---------------------------------------------------------------------------------	
  	/**
  	 * Parse file-time given string to find "base" time:
  	 * the time of the parent containing folder or zipfile
  	 * this is used for block-mode timestamps, basetime is start time of block
  	 * and lowest-level file time is end-time of block.
  	 * Warning:  this logic only works for multi-point (packed) blocks
  	 * @param fname name of file to parse
  	 * @return double time in seconds
  	 */
  	double baseTime() {
//  		System.err.println("baseTime, fname: "+getName()+", thisPath: "+getPath()+", getParent: "+getParent()+", wordSize: "+CTinfo.wordSize(getName()));

  		if(CTinfo.wordSize(getName())==1) return fileTime();			// intact (non-packed) data types (NG, need to recognize packed data)
  		  		
  		if(fileType == FileType.FILE) {
  			double tryTime = fileTime(this.getParentFile().getParent());	// grandparent for regular folders (block/point/chan)
  			if(tryTime == 0.) tryTime = fileTime(this.getParent());			// unpacked data, just need parent
  			return tryTime;
//  				return fileTime(this.getParentFile().getParent());		// grandparent for regular folders (block/point/chan)
  		}
  		else	return fileTime(this.getParent());						// zipfile itself for embedded zentries
  	}

    //---------------------------------------------------------------------------------	
  	// special TFILE parsing (e.g. for camera JPEG photos)
  	// clunky logic, needs to be generalized
  	
  	private double tfolderTime(String fname) {
//		System.err.println("TFILE: "+fname);
    	int islash = fname.lastIndexOf(File.separator);		// strip leading path from name
    	if(islash >= 0) fname = fname.substring(islash+1);
    	
    	int idot = fname.lastIndexOf('.');					// strip trailing .suffix
    	if(idot > 0) fname = fname.substring(0,idot);

  		double ftime = 0.;
  		
		if(useFileTime) {
			ftime = this.lastModified();		// simply use actual file modification time?
		} 
		else {
//			fname = fname.replace(".jpg", "");
			fname = fname.replace("dlink", "");

			boolean pslash = fname.lastIndexOf("-") > 15;

			// e.g.  "2014-09-10 16.00.32-1-1"
			//  				System.err.println("entry fname: "+fname);
			fname = fname.replaceFirst("-", ".").replaceFirst("-",".");				// replace first two slashes with dot
			islash = fname.lastIndexOf("-");
			if(islash>=0) {
				fname = fname.replaceFirst("-", ".");
				fname = fname.replace("-", "");
				int nmsec = fname.length() - islash - 1;
				if(nmsec == 1) fname += "00";
				else if(nmsec == 2) fname += "0";
			}
			//  				System.err.println("exit fname:  "+fname);

			// try most recently successful first
			if(sdflast != null) {
				try {
					ftime = sdflast.parse(fname).getTime();
				}
				catch(Exception e) { sdflast = null; }
			}

			// try various formats
			if(sdflast == null)
				try { 				
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH.mm.ss.SSS");
					if(pslash) fname = fname + "00";			// ensure msec if trailing dash syntax 
					ftime = sdf.parse(fname).getTime();
					sdflast=sdf;
				} catch(Exception e1) {
					try {
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH.mm.ss");
						ftime = sdf.parse(fname).getTime();
						sdflast = sdf;
					} catch(Exception e2) {
						try {
							SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
							ftime = sdf.parse(fname).getTime();
							sdflast=sdf;

						} catch(Exception e3) {
							try {				  		// dlink2015081713324201.jpg
								SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSS");
								ftime = sdf.parse(fname).getTime();
//								System.err.println("dlink ftime: "+ftime+", fname: "+fname);
								sdflast=sdf;
							}
							catch(Exception e4) { sdflast=null; };
						};
					};
				};
		}
//		System.err.println("fileTime("+fname+"): "+ftime+", date: "+new Date((long)ftime)+", sdflast: "+sdflast.toString());
		return ftime / 1000.;
  	}
  	
    //---------------------------------------------------------------------------------	
  	private boolean isTimeFile(String fname) {
  		if(fname.endsWith(".zip")) fname = fname.substring(0,fname.length()-4);		// strip (only) trailing ".zip"
		try {
			Long.parseLong(fname);
			return true;
		} catch(NumberFormatException e) {
			return false;
		}
  	}
  	
  	private boolean isTimeFolder(File file) {
  		return isTimeFolder(file.getPath());
  	}
  	private boolean isTimeFolder(CTFile ctfile) {
  		return isTimeFolder(ctfile.getPath());
  	}
  	
  	private boolean isTimeFolder(String fname) {
  		return (fileTime(fname)>0.);
  	}
  	
    //---------------------------------------------------------------------------------	
  	// To sort by file time
  	Comparator<CTFile> fileTimeComparator = new Comparator<CTFile>() {
  	    public int compare(CTFile filea, CTFile fileb) {
  	    	return( Double.compare(filea.fileTime(), fileb.fileTime()) );
  	    }
  	};
  	
  	
	//--------------------------------------------------------------------------------------------------------
	// containsFile:  see if folder contains a file (channel) in ctmap

	public boolean containsFile(CTmap cm) {
		if(!this.isDirectory()) return false;
		
		CTFile[] listOfFiles = this.listFiles();
		if(listOfFiles == null) return false;
		for(int j=0; j<listOfFiles.length; j++) {
			CTFile file = listOfFiles[j];
			if(file.isFile()) {
				String fileName =  file.getName();
//				System.err.println("cm.checkName: "+fileName+", t/f: "+cm.checkName(fileName));
				if(cm.checkName(fileName)) return true;		// got a match 
			} 
			else if(file.containsFile(cm)) return true;		// recurse
		}
		return false;
	}
}
