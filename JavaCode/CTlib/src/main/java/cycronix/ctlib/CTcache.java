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
import java.util.Iterator;
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
//			CTinfo.debugPrint("MISS CTFileCache myPath: "+myPath+", myzip: "+myZipFile+", cacheSize: "+CTFileCache.size());
			if(myZipFile==null || myPath.equals(myZipFile)) 
				thisCTFile = new CTFile(myPath);					// a new file is opened here!
			else {
				thisCTFile = new CTFile(myPath,myZipFile);		// create a new zip-entry
			}
			CTFileCache.put(cacheKey, thisCTFile);
		}
		else {
//			CTinfo.debugPrint("HIT cachedCTFile, myPath: "+myPath+", myZipFile: "+myZipFile+", cacheSize: "+CTFileCache.size());
		}
		return thisCTFile;
	}
	
	//--------------------------------------------------------------------------------------------------------
	// buildIndices:  custom walkFileTree but skipping over subfolders
	
	static 
	private HashMap<String,ArrayList<TimeFolder>> fileListByChanA = new HashMap<String,ArrayList<TimeFolder>>();	// temp builder map, compact to fileListByChan... (ArrayList -> Array[])	
	protected HashMap<String,TimeFolder[]> fileListByChan = new HashMap<String,TimeFolder[]>();				
//	static HashMap<String,CTFile[]> fileListByChan = new HashMap<String,CTFile[]>();				
	
	// update indices past newest-known Index-time
	synchronized
	public void updateIndices(String sName) {
		buildIndices(sName, sourceNewTime(sName));
	}
	
	//--------------------------------------------------------------------------------------------------------
	// Build global fileListByChan
	
	synchronized  	 // don't let multi-threaded CTreaders collide
	public void buildIndices(String sName, double endTime) {
		if(endTime == 0) System.err.println("Indexing source: "+sName+"...");  		// notify full rebuild
//		else System.err.println("Updating Indices for source: "+sName+"...");
		
		sourceName = sName;		
		CTFile ctsource = new CTFile(rootFolder + File.separator + sourceName);
		CTFile[] listOfFolders = ctsource.listFiles(); 	// mjm 1/26/19: limit to source

		buildTimeFolders(listOfFolders, endTime, 0);			// this builds fileListByChanA
//		trimIndices(sName, oldTime(ctsource));					// check & trim for missing old files

//		System.err.println("fileListByChanA.size: "+fileListByChanA.size()+", fileListByChan.size: "+fileListByChan.size());
		if(fileListByChanA.size() == 0) return; 		// notta
				
		// convert arraylist to old-style array[].  Investigate carrying the arraylist() throughout.  (memory penalty?)
//		for(String c:fileListByChan.keySet()) System.err.println("<flbc.key: "+c);

		Iterator<Map.Entry< String, ArrayList<TimeFolder>>> itr = fileListByChanA.entrySet().iterator(); 
		while(itr.hasNext())   	// loop through channel-entries
		{ 
			Map.Entry<String, ArrayList<TimeFolder>> entry = itr.next(); 
			String c = entry.getKey();
			ArrayList<TimeFolder> tfs = entry.getValue();
			
//		for(String c:fileListByChanA.keySet()) {
//			ArrayList<TimeFolder> tfs = fileListByChanA.get(c);
			if(tfs == null || tfs.size()==0) continue;
			Collections.sort(tfs); 				// sort here (if reverse-search for new time-files)
			int tsize = tfs.size();
			
			if(endTime == 0) {
				TimeFolder[] TF = new TimeFolder[tsize];
				for(int i=0; i<tsize; i++) TF[i] = tfs.get(i);
				fileListByChan.put(c, TF);
//				fileListByChanA.put(c, null);					// free old memory as we construct new list
			}
			else {
				TimeFolder[] tfc = fileListByChan.get(c);
				//				if(tfc == null || tfc.length==0) continue;
				int tsize2 = 0;
				if(tfc != null && tfc.length>0) tsize2 = tfc.length;
				TimeFolder[] TF = new TimeFolder[tsize+tsize2];
				for(int i=0; i<tsize2; i++) TF[i] = tfc[i];
				for(int i=0, j=tsize2; i<tsize; i++, j++) TF[j] = tfs.get(i); 		// append
				fileListByChan.put(c, TF);
//				fileListByChanA.put(c, null);					// free old memory as we construct new list
			}
		}
		fileListByChanA.clear(); 	// converted.
		
		trimIndices(sName, oldTime(ctsource));					// check & trim for missing old files

//		for(String c:fileListByChan.keySet()) System.err.println(">flbc.key: "+c);
	}

	//--------------------------------------------------------------------------------------------------------
	// build index of channel/timefolders.  only check for new-arrivals if endTime>0
	
	private boolean buildTimeFolders(CTFile[] listOfFolders, double endTime, int recursionLevel) {
		if(listOfFolders == null) return false;				// fire-wall

		for(int i=listOfFolders.length-1; i>=0; i--) {			// reverse search thru sorted folder list
			CTFile folder = listOfFolders[i];

			if(folder.isDirectory()) {
				CTFile[] listOfFiles = folder.listFiles();
				if(!buildTimeFolders(listOfFiles, endTime, recursionLevel+1)) return false;	// pop recursion stack
			}
			else {
				// check for new-arrivals here; i.e. if(ftime<=oldEndTime), pop to exit 
//				double ftime = folder.fileTime();  	// check ftime at leaf node
				double ftime = folder.baseTime();  	// check ftime at leaf node

//				System.err.println(folder.getName()+": ftime: "+ftime+", vs endTime: "+endTime);
				if(ftime <= endTime) return false;
				
				String fname = folder.getName();
				//				if(!ctmap.checkName(fname)) continue;			// cache every channel here vs skip?
				String chankey = chan2key(sourceName + File.separator + fname);
				ArrayList<TimeFolder>tf = fileListByChanA.get(chankey);

				if(tf == null) {
					tf = new ArrayList<TimeFolder>();
					fileListByChanA.put(chankey, tf);			// got one!
//					System.err.println("NEW chanKey: "+chankey);
				}
				
//				if(chankey.contains("thumb")) System.err.println("ADD chanKey: "+chankey+", tf: "+((tf==null)?(0):(tf.size())));
//				tf.add(new TimeFolder(folder,ftime));
				fileListByChanA.get(chankey).add(new TimeFolder(folder,ftime));
			}
		}
		
		return true;					// all done, success
	}

	//--------------------------------------------------------------------------------------------------------
	// trim entries from Index that are older than oldest existing source file
	
	 private void trimIndices(String source, double oldestFileTime) {
		Iterator<Map.Entry<String, TimeFolder[]>> itr = fileListByChan.entrySet().iterator(); 
        
//		System.err.println("trimIndices: "+source+", oldestTime: "+oldestFileTime);
		// ex: key: JiffyCam/cam0/thumb.jpg, source: JiffyCam/cam0 (here source lacks rootFolder prefix)
		while(itr.hasNext())   	// loop through channel-entries
		{ 
			Map.Entry<String, TimeFolder[]> entry = itr.next(); 
			String key = entry.getKey();
			if(key.startsWith(source)) {						// filter to target source
				TimeFolder[] tf = entry.getValue();
//				if(tf == null || tf.length==0) continue; 	// ??
				double t = tf[0].folderTime;
				if(t < oldestFileTime) {
					int ichk;
					for(ichk=1; ichk<tf.length; ichk++) {
						if(tf[ichk].getTime() >= oldestFileTime) break;
					}
					if(ichk == tf.length) { 	// all gone
						itr.remove();
//						System.err.println("POOF: "+key);
					}
					else {
//						System.err.println("trimming old files, source: "+source+", ntrim: "+ichk);
						TimeFolder[] tmpList = new TimeFolder[tf.length - ichk];
						for(int j=ichk,k=0; j<tf.length; j++,k++) tmpList[k] = tf[j];	// salvage old cache > updated oldestTime
						tf = tmpList;
						fileListByChan.put(key, tf);
					}
				}
			}
		} 
	}
	
	//--------------------------------------------------------------------------------------------------------
	// return most recent time any channel per source
	
	double sourceNewTime(String source) {
		double endTime = 0;
		
		Iterator<Map.Entry<String, TimeFolder[]>> itr = fileListByChan.entrySet().iterator(); 
        
		// ex: key: JiffyCam/cam0/thumb.jpg, source: CTdata/JiffyCam/cam2
		String src = source.replace(rootFolder + File.separator, "");
//		System.err.println("sourceEndTime, source: "+source+", iter.len: "+fileListByChan.size());
        while(itr.hasNext()) 
        { 
             Map.Entry<String, TimeFolder[]> entry = itr.next(); 
             String key = entry.getKey();
             if(key.startsWith(src)) {
            	 TimeFolder[] tf = entry.getValue();
            	 if(tf == null || tf.length==0) continue;  		// ??
            	 double t = tf[tf.length-1].folderTime;
            	 if(t > endTime) {
 //           		 System.err.println("sourceEndTime, src: "+src+", key: "+key+", old endtime: "+endTime+", new endtime: "+t);
            		 endTime = t;
            	 }
             }
        } 
			
		return endTime;
	}
	
	//--------------------------------------------------------------------------------------------------------
	// return oldest time any channel per source from TimeFolder index
	
	double sourceOldTime(String source) {
		double oldTime = 0;
		
		Iterator<Map.Entry<String, TimeFolder[]>> itr = fileListByChan.entrySet().iterator(); 
        
		// ex: key: JiffyCam/cam0/thumb.jpg, source: CTdata/JiffyCam/cam2
		String src = source.replace(rootFolder + File.separator, "");
//		System.err.println("sourceEndTime, source: "+source+", iter.len: "+fileListByChan.size());
        while(itr.hasNext()) 
        { 
             Map.Entry<String, TimeFolder[]> entry = itr.next(); 
             String key = entry.getKey();
             if(key.startsWith(src)) {
            	 TimeFolder[] tf = entry.getValue();
            	 if(tf == null || tf.length==0) continue;  		// ??
            	 double t = tf[0].folderTime;
            	 if(oldTime==0 || t < oldTime) {
 //           		 System.err.println("sourceOldTime, src: "+src+", key: "+key+", old endtime: "+oldTime+", new oldtime: "+t);
            		 oldTime = t;
            	 }
             }
        } 
			
		return oldTime;
	}
	
	//--------------------------------------------------------------------------------------------------------
	// fast oldTime:  crawl down first (oldest) folder branch to bottom file time
	
	private double oldTime(CTFile baseFolder) {
//		System.err.println("oldTime baseFolder: "+baseFolder.getPath()+", isDir: "+baseFolder.isDirectory());
		if(!baseFolder.isDirectory()) return baseFolder.fileTime();  

		double ftime = 0.;		// default is now
		CTFile[] files = baseFolder.listFiles();
		if(files==null || files.length == 0) return baseFolder.fileTime();		// catch empty folder case
//		System.err.println("baseFolder: "+baseFolder.getName()+", files.len: "+files.length);
		
		int idx = 0;
		for(; idx<files.length; idx++) { // cycle past empty base folders
			CTFile f = files[idx];
			if(f == null) break;
			if(!f.isDirectory()) break;			// not a folder
			CTFile[] fl = f.listFiles();
			if(fl== null || fl.length != 0) break;
		}
//		System.err.println("oldTime base: "+baseFolder.getName()+", idx: "+idx);
		CTFile file = files[idx];

		if(new File(file.getAbsolutePath()).isDirectory()) {
//			System.err.println("recurse: "+file.getAbsolutePath());
			ftime = oldTime(file);	// recurse if actual folder
		}
		else {
//			System.err.println("oldTime fileTime: "+file.fileTime()+", baseTime: "+file.baseTime());
			ftime = file.baseTime();
		}
		if(ftime > 0.) return ftime;

//		System.err.println("oldTime got ftime: "+ftime);
		return ftime;
	}
	
	//--------------------------------------------------------------------------------------------------------
	public ArrayList<String> listChans(String source) {
		String src = source.replace(rootFolder + File.separator, "");

		updateIndices(src);
//		buildIndices(src, 0);  // full rebuild?

		ArrayList<String> chanList = new ArrayList<String>();
		
		Iterator<Map.Entry<String, TimeFolder[]>> itr = fileListByChan.entrySet().iterator(); 
        
//		System.err.println("listChans, source: "+source+", src: "+src+", iter.len: "+fileListByChan.size());
        while(itr.hasNext()) 
        { 
             Map.Entry<String, TimeFolder[]> entry = itr.next(); 
             String key = entry.getKey();
             if(key.startsWith(src)) {
            	 String chan = key.replace(src+File.separator, "");		// strip leading source
            	 chanList.add(chan);
//            	 System.err.println("chan: "+chan);
             }
        } 
        
        return chanList;
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
	// NOTE:  the size of this class has strong influence on size of CTFileCache for large CTdata archives

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
