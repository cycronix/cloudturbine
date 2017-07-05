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
	private static boolean cacheProfile = false;
	
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
/*
	@Override
	public int compareTo(File compareFile) {
		System.err.println("CTFile.compareTo: "+compareFile.getPath());
		CTFile ctfile = new CTFile(compareFile.getPath());
		return ((ctfile.fileTime() > ctfile.fileTime()) ? 1 : -1);
	}
*/	
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
		long startTime = System.nanoTime();

		if(cacheProfile) CTinfo.debugPrint("listFiles for: "+myPath+", fileType: "+fileType);
		switch(fileType) {
		case ZIP:				// top level file.zip
			if(zipMap==null || zipMap.size()==0) ZipMap(myZipFile);		// delayed zipmap build?
			if(zipMap==null) return null;		// couldn't create (IO error?)
			
			Object[] sfiles = zipMap.keySet().toArray();				// need to concat dupes?
			clist = new CTFile[sfiles.length];
			CTinfo.debugPrint(cacheProfile,"case ZIP: "+myPath+", sfiles.length: "+sfiles.length+", time: "+((System.nanoTime()-startTime)/1000000.));
			for(int i=0; i<sfiles.length; i++) {
				String[] files = zipMap.get(sfiles[i]);
//				System.err.println("ZIP file: "+sfiles[i]+", files.len: "+files.length);
				clist[i] = new CTFile((String) sfiles[i], files, myZipFile);	
			}
			
//			CTinfo.debugPrint(cacheProfile,"unsorted zips, length: "+clist.length+", time: "+((System.nanoTime()-startTime)/1000000.));
			// Arrays.sort is expensive as it is called lots at lowest level loop
//			Arrays.sort(clist, fileTimeComparator);			// zip files in order of write, not guaranteed time-sorted
//			Arrays.sort(clist, fileNameComparator);			// zip files in order of write, not guaranteed time-sorted
			// zipMap is pre-sorted as TreeMap with folderTimeComparator
			CTinfo.debugPrint(cacheProfile,"sorted zips:"+", time: "+((System.nanoTime()-startTime)/1000000.)+", clist.size: "+clist.length);
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
			
			// clist built from myFiles which is side-effect of listFiles-type-ZIP above.  Sorted TreeMap.
//			Arrays.sort(clist, fileTimeComparator);			// zip files in order of write, not guaranteed time-sorted
//			for(CTFile c:clist) System.err.println(c);
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
			    	if(file.getName().startsWith("_")) return false;			// "_" is CT-hidden prefix
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
//		long t1 = System.nanoTime();

		CTFile[] filelist = this.listFiles();
//		long t2 = System.nanoTime();
//		System.err.println("listFolders dt1: "+(t2-t1)/1000000.);
	
//		t1 = System.nanoTime();

		if(filelist == null) {
//			System.err.println("CTFile: Empty folder-list!");
			return null;
		}
		CTFile[] folderlist = new CTFile[filelist.length];
		int count=0;
		// following is expensive
		for(CTFile f:filelist) if(f.isDirectory() && f.containsFile(ctmap)) folderlist[count++]=f;
		CTFile[] prunedlist = new CTFile[count];
		
//		t2 = System.nanoTime();
//		System.err.println("listFolders dt2: "+(t2-t1)/1000000.);
		
//		t1 = System.nanoTime();
		System.arraycopy(folderlist, 0, prunedlist, 0, count);
//		Arrays.sort(prunedlist, fileTimeComparator);		// listFiles already sorted
			
//		t2 = System.nanoTime();
//		System.err.println("listFolders dt3: "+(t2-t1)/1000000.);
		
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
//				String checkname = files[files.length-1].getName();
//				System.err.println("isFileFolder, files.length: "+files.length+", checkname: "+checkname+", isTimeFile: "+isTimeFile(checkname) );
//				if(files!=null && files.length>0 && !isTimeFile(checkname)) return true;
//				else							  return false;		// needs testing
				
				// more robust:  if any time files in folder, this is NOT a file folder
				if(files==null) return false;
				for(int i=0; i<files.length; i++) if(isTimeFile(files[i].getName())) return false;
				return true;
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

	//---------------------------------------------------------------
	// cache functions
	
//	private byte[] myData=null;				// cache?
//	static private TreeMap<String, byte[]> DataCache = new TreeMap<String, byte[]>();		// cache (need logic to cap size)
	// cache limits, max entries, max filesize any entry, jvm size at which to dump old cache...
	private static final int MAX_ENTRIES = 100000;			// limit total entries in data cache	(10K led to "too many open files"?)
	private static final int MAX_FILESIZE = 20000000;		// 20MB.  max size any individual entry
//	private static final int MAX_JVMSIZE = 2000000000;		// 200MB. max overall JVM memory use at which to dump old entries  (2GB?)
	private static final double MAX_MEMUSE = 0.9;			// fraction available JVM memory to use before limiting cache
	private static final int MAX_FILES = 100;				// max number open zip files
	
	// ZipMapCache has small effect versus rebuilding map every time (versus caching zipmap file object itself)
	static private Map<String, Map<String, String[]>>ZipMapCache = new LinkedHashMap<String, Map<String, String[]>>() {
		 protected boolean removeEldestEntry(Map.Entry  eldest) {
			 	CTinfo.debugPrint(cacheProfile,"ZipCache stats, size: "+size());
	            return size() >  MAX_ENTRIES;
	         }
	};		
	
	static private LinkedHashMap<String, byte[]> DataCache = new LinkedHashMap<String, byte[]>() {
		 protected boolean removeEldestEntry(Map.Entry  eldest) {
			 	Runtime runtime = Runtime.getRuntime();
			 	long usedmem = runtime.totalMemory() - runtime.freeMemory();
			 	long availableMem = runtime.maxMemory(); 
			 	double usedMemFraction = (double)usedmem / (double)availableMem;
			 	CTinfo.debugPrint(cacheProfile, "DataCache stats, usedmem: "+usedmem+", size: "+size()+", availableMem: "+availableMem+", usedMemPerc: "+usedMemFraction);
//	            return ((size() >  MAX_ENTRIES) || (usedmem > MAX_JVMSIZE));
	            return ((size() >  MAX_ENTRIES) || (usedMemFraction > MAX_MEMUSE));
	         }
	};		

	static private LinkedHashMap<String, ZipFile> ZipFileCache = new LinkedHashMap<String, ZipFile>() {
		 protected boolean removeEldestEntry(Map.Entry  eldest) {
//			 System.err.println("ZipMapCache size: "+size());
	            return ((size() >  MAX_FILES));
	         }
	};		
	
	// cache open zipfiles, lots of child-folders reference parent zip, (re)opening ZipFile takes CPU time
	private ZipFile cachedZipFile(String myZipFile) throws Exception {
		ZipFile thisZipFile = ZipFileCache.get(myZipFile);
		if(thisZipFile == null) {
			CTinfo.debugPrint(cacheProfile,"OPEN ZIPFILE");
			thisZipFile = new ZipFile(myZipFile);
			ZipFileCache.put(myZipFile, thisZipFile);
		}
		return thisZipFile;
	}
	
	//---------------------------------------------------------------
	/**
	 * CTfile reader.  uses parent File read if native file, else reads data from within zip-entry
	 * @return byte[] data read
	 */

	byte[] read() {
//		String cacheKey = myZipFile+":"+myPath;
		String cacheKey = myPath;			// 6/2017:  myPath now unique full path into zip?
		long startTime = System.nanoTime();

//		String cacheKey = myPath;	// myPath includes zipfile name?  Nope, inconsistent but myPath for zip-entry is not full time-path
//		System.err.println("cacheKey: "+cacheKey+", myPath: "+myPath);
		byte[] data = DataCache.get(cacheKey);					// need to add Source (folder) to myPath. now: Tstamp/Chan
		if(data != null) {
//			CTinfo.debugPrint(cacheProfile, "DataCache hit: "+cacheKey+", cacheSize: "+DataCache.size()+", dt: "+((System.nanoTime()-startTime)/1000000.));
			return data;					// use cached data
		}
		else CTinfo.debugPrint(cacheProfile, "DataCache miss: "+cacheKey+", cacheSize: "+DataCache.size()+", dt: "+((System.nanoTime()-startTime)/1000000.));
		
//		if(myData!=null) return myData;		// cache
		switch(fileType) {
		case ZIP:
		case ZENTRY:
			return null;
//			if(isZip || isEntry) return null;
		case ZFILE:			// zipoutput
//		if(isFile) {		
			try {
				CTinfo.debugPrint(cacheProfile, "zip ckp1 dt: "+((System.nanoTime()-startTime)/1000000.)+", myZipFile: "+myZipFile);
				ZipFile thisZipFile = cachedZipFile(myZipFile);
//				ZipFile thisZipFile = new ZipFile(myZipFile);
//				CTinfo.debugPrint(cacheProfile, "zip ckp1b dt: "+((System.nanoTime()-startTime)/1000000.));

				// note:  myPath for zip-entry is not full-path as it is with other CTFile...  <---FIXED and adjusted right below!
				String mypathfs = myPath.replace('\\','/');		// myPath with fwd slash
				
				// strip leading file path to get just the zip-entry part!  always will be time/name format.
				String[] subDirs = mypathfs.split(Pattern.quote("/"));	
				if(subDirs.length >= 2) mypathfs = subDirs[subDirs.length-2] + "/" + subDirs[subDirs.length-1];
				else					System.err.println("WARNING!!!  Unexpected zip-entry format: "+mypathfs);
//				CTinfo.debugPrint(cacheProfile, "zip ckpc dt: "+((System.nanoTime()-startTime)/1000000.));

				ZipEntry ze = thisZipFile.getEntry(mypathfs);			// need fullpath!
//				CTinfo.debugPrint(cacheProfile, "zip ckp2 dt: "+((System.nanoTime()-startTime)/1000000.)+", ze: "+ze);

				if(ze == null) {
//					thisZipFile.close();
//					CTinfo.debugPrint(cacheProfile, "zip NULL ZE: "+((System.nanoTime()-startTime)/1000000.));
					throw new IOException("Null ZipEntry, zipfile: "+myZipFile+", entry: "+mypathfs);
				}
//				CTinfo.debugPrint(cacheProfile, "zip ckp1 dt: "+((System.nanoTime()-startTime)/1000000.)+", ze: "+ze.getName());
				int zsize = (int)ze.getSize();
				data = new byte[zsize];
				InputStream zis = thisZipFile.getInputStream(ze);
				int len, nread=0;
				while ((len = zis.read(data,nread,zsize-nread)) > 0) nread+=len;
				//		    		System.err.println("zip nread: "+nread+", ze.size: "+ze.getSize());
				zis.close();
//				thisZipFile.close();
				CTinfo.debugPrint(cacheProfile, "zip ckp2 dt: "+((System.nanoTime()-startTime)/1000000.));

			} catch(Exception e) {
				System.err.println("CTFile.read: "+e /* +", zipfile: "+myZipFile+", entry: "+myPath */);
//				e.printStackTrace();
			}
			break;
		default:		// conventional file
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
		
		CTinfo.debugPrint(cacheProfile, "DataCache put: "+cacheKey+", datasize: "+data.length+", cacheLen: "+DataCache.size());
		if(data!=null && data.length <= MAX_FILESIZE) DataCache.put(cacheKey, data);		// only cache small files
		return data;
	}
	
	//---------------------------------------------------------------------------------	
	// ZipMap:  create index of zipped folder/files.  
	// Map keys are timestamp-folders, map values are string-arrays of channels per folder
	// this probably should be a class with constructor...

	private void ZipMap(String zipfile) {
		zipMap = ZipMapCache.get(myPath);
		if(zipMap != null) {
			CTinfo.debugPrint(cacheProfile,"ZipCache hit: "+myPath);
			return;					
		}
		else CTinfo.debugPrint(cacheProfile,"ZipCache miss: "+myPath);
		
		zipMap = new TreeMap<String, String[]>(folderTimeComparator);		// make a new one, sorted by folder time
		try{		//get the zip file content

			ZipFile zfile = cachedZipFile(zipfile);		// this can throw exception being created in RT
			Enumeration<? extends ZipEntry> zenum = zfile.entries();
			int numEntries = zfile.size();		// convert to array, easier loop control
			String[] entry = new String[numEntries];
//			System.err.println("Building ZipMap for: "+myPath+", numEntries: "+numEntries);

			for(int i=0; i<numEntries; i++) entry[i] = zenum.nextElement().getName();
			Arrays.sort(entry);				// sort so that following add-logic gets all channels in same timestamp folder
			
			String thisfolder=null;
			ArrayList<String>flist=null;

			// loop thru zipfile entries, add them to TreeMap
			for(int i=0; i<numEntries; i++) {
//				System.err.println("ZipMap entry["+i+"]: "+entry[i]);
				String[] spentry = entry[i].split("/");				// Java 1.6 compat 

				String folder = spentry[0];
				if(spentry.length > 1) {							// Java 1.6 compat
					if(!folder.equals(thisfolder)) {			// new subfolder (presumes sorted!!)
						if(thisfolder!=null) zipMap.put(thisfolder, flist.toArray(new String[flist.size()]));
						thisfolder = folder;
						flist=new ArrayList<String>();
//						System.err.println("zipmap add new subfolder: "+entry[i]);
						flist.add(entry[i]);			// Java 1.6 compat
					}
					else {
						if(thisfolder!=null) zipMap.put(thisfolder, flist.toArray(new String[flist.size()]));
//						System.err.println("zipmap add to existing subfolder: "+entry[i]);
						flist.add(entry[i]);			// Java 1.6 compat
					} 
				}
				else {
					zipMap.put(folder, null);
				}	
			}
			if(flist!=null && flist.size()>0 && thisfolder!=null) 					// wrap up
				zipMap.put(thisfolder, flist.toArray(new String[flist.size()]));

//			zfile.close();
			
			CTinfo.debugPrint(cacheProfile,"ZipCache put: "+myPath);
			// pre-sort zipMap here
			
			ZipMapCache.put(myPath, zipMap);			// cache
			
//		} catch(IOException ex) { System.err.println("ZipMap Exception on zipfile: "+zipfile); ex.printStackTrace(); }
		} catch(Exception ex) { 
			zipMap = null;
			System.err.println("ZipMap Exception on zipfile: "+zipfile+", exception: "+ex.getMessage()); 
//			ex.printStackTrace(); 
		}

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
//  		System.err.println("CTFile fileTime: "+fname);
  		// special logic for TFOLDER (e.g. camera time.jpg files)
//  		System.err.println("CTFile/fileTime, fname: "+fname+", ftype: "+fileType);
  		if(fileType==FileType.TFOLDER || fileType==FileType.TFILE) return tfolderTime(fname);
	
  		return CTinfo.fileTime(fname);		// generalize
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
//		System.err.println("tfolderTime, TFILE: "+fname);
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
							catch(Exception e4) {
								try { 				
									SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");	
									ftime = sdf.parse(fname).getTime();
									sdflast=sdf;
								} catch(Exception e5) { sdflast=null; };
							}
						};
					};
				};
		}
//		System.err.println("tfolderTime, fileTime("+fname+"): "+ftime);
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
  	Comparator<String> folderTimeComparator = new Comparator<String>() {
  	    public int compare(String filea, String fileb) {
  	    	int la = filea.length();
  	    	int lb = fileb.length();
  	    	if		(la>lb) return  1;
  	    	else if (la<lb) return -1;
  	    	else
  	    	return	( filea.compareTo(fileb) );
  	    }
  	};
  	
  
    //---------------------------------------------------------------------------------	
  	// To sort by file time
  	Comparator<CTFile> fileTimeComparator = new Comparator<CTFile>() {
  	    public int compare(CTFile filea, CTFile fileb) {
  	    	return( Double.compare(filea.fileTime(), fileb.fileTime()) );
  	    }
  	};
  	
    //---------------------------------------------------------------------------------	
  	// To sort by file name
  	Comparator<CTFile> fileNameComparator = new Comparator<CTFile>() {
  	    public int compare(CTFile filea, CTFile fileb) {
  	    	int la = filea.myPath.length();
  	    	int lb = fileb.myPath.length();
  	    	if		(la>lb) return  1;
  	    	else if (la<lb) return -1;
  	    	else
  	    	return	( filea.myPath.compareTo(fileb.myPath) );
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
