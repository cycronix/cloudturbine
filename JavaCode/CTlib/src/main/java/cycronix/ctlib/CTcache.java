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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * CloudTurbine utility class that provides caching storage and access functions
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2017/07/05
 * 
*/

class CTcache {

	//---------------------------------------------------------------
	// cache functions
	
//	private byte[] myData=null;				// cache?
//	static private TreeMap<String, byte[]> DataCache = new TreeMap<String, byte[]>();		// cache (need logic to cap size)
	// cache limits, max entries, max filesize any entry, jvm size at which to dump old cache...
	private static final int MAX_ENTRIES = 100000;			// limit total entries in data cache (was 10000)
	private static final int MAX_FILESIZE = 20000000;		// 20MB.  max size any individual entry
//	private static final int MAX_JVMSIZE = 2000000000;		// 200MB. max overall JVM memory use at which to dump old entries  (2GB?)
	private static final double MAX_MEMUSE = 0.5;			// fraction available JVM memory to use before limiting cache (was 
	private static final int MAX_ZIPFILES = 1;				// max number open zip files  (was 100, 1 helps a lot on CThdf)
	private static final int MAX_ZIPMAPS = 10000;			// max number constructed ZipMaps (was 100000 @ moderate impact memuse)
	private static final int MAX_CTFILES = 10000;			// max number cached CTFiles (was 100000)
	
	private static boolean cacheProfile = false;
	public static Object cacheLock = new Object();
	private String rootFolder = "";		
	private String sourceName = "";

	public CTcache(String rfolder) {
		rootFolder = rfolder;
	}
	
	// ZipMapCache has small effect versus rebuilding map every time (versus caching zipmap file object itself)
	static Map<String, Map<String, String[]>> ZipMapCache = Collections.synchronizedMap(
//	static Map<String, Map<String, String[]>> ZipMapCache = 
			new LinkedHashMap<String, Map<String, String[]>>() {
				protected synchronized boolean removeEldestEntry(Map.Entry  eldest) {
//					CTinfo.debugPrint("ZipMapCache size: "+size());
					return size() >  MAX_ZIPMAPS;
				}
			}
			);

	// DataCache supplants OS/disk caching for full-file reads
	static Map<String, byte[]> DataCache = Collections.synchronizedMap(
			new LinkedHashMap<String, byte[]>() {
				protected boolean removeEldestEntry(Map.Entry  eldest) {
					Runtime runtime = Runtime.getRuntime();
					long usedmem = runtime.totalMemory() - runtime.freeMemory();
					long availableMem = runtime.maxMemory(); 
					double usedMemFraction = (double)usedmem / (double)availableMem;
//					System.err.println("DataCache stats, usedmem: "+usedmem+", size: "+size()+", availableMem: "+availableMem+", usedMemPerc: "+usedMemFraction);
					boolean trimCache = (size() >  MAX_ENTRIES) || (usedMemFraction > MAX_MEMUSE);
					//			 	System.err.println("trimCache: "+trimCache);
					return trimCache;
				}
			}
			);


	// ZipFileCache caches open zip files; these take significant overhead to open/close on each use
	 static Map<String, ZipFile> ZipFileCache =  //Collections.synchronizedMap(
			new LinkedHashMap<String, ZipFile>() {
				protected boolean removeEldestEntry(Map.Entry<String, ZipFile>  eldest) {
//					System.err.println("ZipFileCache size: "+size());

					// explicitly loop through close and release entries (return true not reliable?)
					if(size() > MAX_ZIPFILES) {
						synchronized(cacheLock) {		// don't close while in use
							for (Map.Entry<String, ZipFile> entry : ZipFileCache.entrySet()) {
								try{ entry.getValue().close(); } catch(Exception e){};  	// explicit close
								//			 			System.err.println("ZipFileCache remove key: "+entry.getKey());
								ZipFileCache.remove(entry.getKey());						// explicit remove
								if(size() <= MAX_ZIPFILES) break;
							}
						}
						//	System.err.println("ZipFileCache >remove size: "+ZipFileCache.size()+", max_files: "+MAX_ZIPFILES);
					}
					return false;
					//	            return ((size() >  MAX_FILES));
				}
			};
//			);

	// ZipFileCache getter
	static synchronized ZipFile cachedZipFile(String myZipFile) throws Exception {
		ZipFile thisZipFile = ZipFileCache.get(myZipFile);
		if(thisZipFile == null) {
//			System.err.println("OPEN ZIPFILE: "+myZipFile);
			thisZipFile = new ZipFile(myZipFile);					// a new file is opened here!
			ZipFileCache.put(myZipFile, thisZipFile);
		}
//		else
//			CTinfo.debugPrint(cacheProfile,"ZipCache HIT: "+myZipFile);

		return thisZipFile;
	}
	
	//--------------------------------------------------------------------------------------------------------

	// TO DO:  getter/setters/config/reset methods for each cache type
	
	Map<String, CTFile> CTFileCache = Collections.synchronizedMap(new LinkedHashMap<String, CTFile>() {
        @Override
		protected boolean removeEldestEntry(Map.Entry<String,CTFile>  eldest) {
			Runtime runtime = Runtime.getRuntime();
			long usedmem = runtime.totalMemory() - runtime.freeMemory();
			long availableMem = runtime.maxMemory(); 
			double usedMemFraction = (double)usedmem / (double)availableMem;

			boolean trimCache = (size() >  MAX_CTFILES) || (usedMemFraction > MAX_MEMUSE);
//			System.err.println("CTFileCache stats, usedmem: "+usedmem+", size: "+this.size()+", availableMem: "+availableMem+", usedMemPerc: "+usedMemFraction+", trimCache: "+trimCache);
			return trimCache;
			
		}
	}
	);
	
	// CTFileCache getter
	synchronized CTFile cachedCTFile(String myPath, String myZipFile) throws Exception {
		String cacheKey = myPath;
		if(myZipFile != null) cacheKey = myZipFile + File.separator + myPath;
//		cacheKey = cacheKey.replace(".zip",  "");
//		System.err.println("CacheKey: "+cacheKey);
		CTFile thisCTFile = CTFileCache.get(cacheKey);
		if(thisCTFile == null) {
			CTinfo.debugPrint("MISS CTFileCache myPath: "+myPath+", myzip: "+myZipFile+", cacheSize: "+CTFileCache.size());
			if(myZipFile==null || myPath.equals(myZipFile)) 
				thisCTFile = new CTFile(myPath);					// a new file is opened here!
			else {
				thisCTFile = new CTFile(myPath,myZipFile);		// create a new zip-entry
			}
			CTFileCache.put(cacheKey, thisCTFile);
		}
		else {
			CTinfo.debugPrint("HIT cachedCTFile, myPath: "+myPath+", myZipFile: "+myZipFile+", cacheSize: "+CTFileCache.size());
		}
		return thisCTFile;
	}
	
	//--------------------------------------------------------------------------------------------------------
	// buildIndices:  custom walkFileTree but skipping over subfolders
	
	static private HashMap<String,ArrayList<TimeFolder>> fileListByChanA = new HashMap<String,ArrayList<TimeFolder>>();	// temp builder map, compact to fileListByChan... ?		
	protected HashMap<String,TimeFolder[]> fileListByChan = new HashMap<String,TimeFolder[]>();				
//	static HashMap<String,CTFile[]> fileListByChan = new HashMap<String,CTFile[]>();				
	
	public void buildIndices(String sName) {
		System.err.println("Indexing source: "+sName+"...");

		long startTime = System.nanoTime();		
		sourceName = sName;		
		
		CTFile[] listOfFolders = new CTFile(rootFolder).listFiles();
		buildTimeFolders(listOfFolders, 0);			// this builds fileListByChanA
		
//		System.err.println("fileListByChanA.size: "+fileListByChanA.size());
		// sort here?
		
		// convert arraylist to old-style array[].  Investigate carrying the arraylist() throughout.  (memory penalty?)
		fileListByChan.clear();  		// fresh list
		for(String c:fileListByChanA.keySet()) {
			ArrayList<TimeFolder> tfs = fileListByChanA.get(c);
			int tsize = tfs.size();
			TimeFolder[] TF = new TimeFolder[tsize];
			for(int i=0; i<tsize; i++) TF[i] = tfs.get(i);
			fileListByChan.put(c, TF);
			fileListByChanA.put(c, null);					// free old memory as we construct new list
		}
		fileListByChanA.clear(); 	// converted.
		
		System.err.println("Indexing done, channels: "+fileListByChan.size()+", time: "+(((int)((System.nanoTime()-startTime)/1000000.))/1000.)+" (s)");
//		fileListByChanA = null;
	}

	private boolean buildTimeFolders(CTFile[] listOfFolders, int recursionLevel) {
		if(listOfFolders == null) return false;				// fire-wall

		for(int i=0; i<listOfFolders.length; i++) {			// fwd search thru sorted folder list
			CTFile folder = listOfFolders[i];

			if(folder.isDirectory()) {
				CTFile[] listOfFiles = folder.listFiles();
				if(!buildTimeFolders(listOfFiles, recursionLevel+1)) return false;	// pop recursion stack
			}
			else {
				String fname = folder.getName();
				//				if(!ctmap.checkName(fname)) continue;			// cache every channel here vs skip?
				double ftime = folder.fileTime();
				String chankey = chan2key(sourceName + File.separator + fname);
//				System.err.println("rootFolder: "+rootFolder+", fname: "+fname+", chankey: "+chankey);
				ArrayList<TimeFolder>tf = fileListByChanA.get(chankey);
				if(tf == null) {
					tf = new ArrayList<TimeFolder>();
					fileListByChanA.put(chankey, tf);			// got one!
				}
				tf.add(new TimeFolder(folder,ftime));
//				System.err.println("add chanKey: "+chankey+", isize: "+fileListByChanA.get(chankey).size());
			}
		}
		return true;					// all done, success
	}

//--------------------------------------------------------------------------------------------------------
//convert chan path to reliable key
	public String chan2key(String chan) {
		String key = chan.replace(rootFolder, "");				// strip leading rootFolder if present
		key = key.replace(File.separator, "/");
		key = key.replace("//", "/");
		if(key.startsWith("/")) key = key.substring(1);
//		System.err.println("chan2key, chan: "+chan+", key: "+key+", rootFolder: "+rootFolder);
//		Thread.dumpStack();
		return key;
	}
	
	//--------------------------------------------------------------------------------------------------------
	// utility class to store time/folder pairs
	// NOTE:  the size of this class has string influence on size of CTFileCache for large CTdata archives

	private String tmpdir = System.getProperty("java.io.tmpdir");			// for decode ref
	
	public TimeFolder newTimeFolder(CTFile file, double time) {
		return new TimeFolder(file, time);
	}
	
	public class TimeFolder implements Comparable<TimeFolder> {
		private byte[] myPathBytes=null;
		private double folderTime;
		private byte[] myZipFileBytes=null;
		
		public TimeFolder(CTFile file, double time) {
			String myPath = file.getMyPath();
			folderTime = time;
			String myZipFile = file.getMyZipFile();
			if(myZipFile!=null) {
//				String trimZip = myZipFile.replace(".zip",  "/");
				String trimZip = myZipFile.replace(".zip",  File.separator);			// oops this was broke on Windows
				myPath = myPath.replace(trimZip, "");			// shorten myPath (saves space)
				myZipFile = myZipFile.replace(".zip",  "");
//				System.err.println("myZipFile: "+myZipFile+", rootFolder: "+rootFolder+", startswith: "+(myZipFile.startsWith(rootFolder)));
				if(myZipFile.startsWith(rootFolder))
					myZipFile = myZipFile.replace(rootFolder, "");	// stingy
			} 
			else myPath = myPath.replace(rootFolder,  "");
			
			CTFileCache.put(myPath, file);			// stock up FileCache here?

			myPathBytes = encodeUTF8(myPath);				// efficiently encode to class-storage
			myZipFileBytes = encodeUTF8(myZipFile);
//			System.err.println("Encode: myPath: "+myPath+", myZipFile; "+myZipFile);
		};
		
		public CTFile getCTFile() {
			String myPath = decodeUTF8(myPathBytes);
			String myZipFile = decodeUTF8(myZipFileBytes);
//			System.err.println("Decode: myPath: "+myPath+", myZipFile; "+myZipFile);
			
			try {
				CTFile ctfile=null;
				if(myZipFile==null) ctfile = cachedCTFile(rootFolder + myPath, null);
//				else				ctfile = cachedCTFile(myPath, rootFolder + myZipFile+".zip");
				else	{
					if(myZipFile.startsWith(tmpdir))
						ctfile = cachedCTFile(myPath, myZipFile+".zip");			// if absolute path presume gzip/temp folder?
					else
						ctfile = cachedCTFile(myPath, rootFolder + myZipFile+".zip");
				}

//				System.err.println("+++++++CTfile: "+ctfile);
				return ctfile;
			} catch(Exception e) {
				System.err.println("TimeFolder getCTFile exception: "+e);
				return null;
			}
		}
		public double getTime() {
			return folderTime;
		}
		
		@Override
		public int compareTo(TimeFolder compareFile) {
			return ((this.folderTime > compareFile.folderTime) ? 1 : -1);
		}
		
		// convert to/from String to byte[] for compact cache memory use
		private final Charset UTF8_CHARSET = Charset.forName("UTF-8");
		String decodeUTF8(byte[] bytes) {
			if(bytes==null) return null;
		    return new String(bytes, UTF8_CHARSET);
		}
		byte[] encodeUTF8(String string) {
			if(string==null) return null;
		    return string.getBytes(UTF8_CHARSET);
		}
	}
}
