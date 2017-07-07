package cycronix.ctlib;

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

public class CTcache {

	//---------------------------------------------------------------
	// cache functions
	
//	private byte[] myData=null;				// cache?
//	static private TreeMap<String, byte[]> DataCache = new TreeMap<String, byte[]>();		// cache (need logic to cap size)
	// cache limits, max entries, max filesize any entry, jvm size at which to dump old cache...
	private static final int MAX_ENTRIES = 100000;			// limit total entries in data cache	(10K led to "too many open files"?)
	private static final int MAX_FILESIZE = 20000000;		// 20MB.  max size any individual entry
//	private static final int MAX_JVMSIZE = 2000000000;		// 200MB. max overall JVM memory use at which to dump old entries  (2GB?)
	private static final double MAX_MEMUSE = 0.8;			// fraction available JVM memory to use before limiting cache
	private static final int MAX_ZIPFILES = 1000;			// max number open zip files
	
	private static boolean cacheProfile = false;

	// ZipMapCache has small effect versus rebuilding map every time (versus caching zipmap file object itself)
	static Map<String, Map<String, String[]>>ZipMapCache = new LinkedHashMap<String, Map<String, String[]>>() {
		 protected synchronized boolean removeEldestEntry(Map.Entry  eldest) {
			 	CTinfo.debugPrint(cacheProfile, "ZipMapCache size: "+size());
	            return size() >  MAX_ZIPFILES;
	         }
	};		
	
	// DataCache supplants OS/disk caching for full-file reads
	static LinkedHashMap<String, byte[]> DataCache = new LinkedHashMap<String, byte[]>() {
		 protected synchronized boolean removeEldestEntry(Map.Entry  eldest) {
			 	Runtime runtime = Runtime.getRuntime();
			 	long usedmem = runtime.totalMemory() - runtime.freeMemory();
			 	long availableMem = runtime.maxMemory(); 
			 	double usedMemFraction = (double)usedmem / (double)availableMem;
			 	CTinfo.debugPrint(cacheProfile, "DataCache stats, usedmem: "+usedmem+", size: "+size()+", availableMem: "+availableMem+", usedMemPerc: "+usedMemFraction);
	            return ((size() >  MAX_ENTRIES) || (usedMemFraction > MAX_MEMUSE));
	         }
	};		

	// ZipFileCache caches open zip files; these take significant overhead to open/close on each use
	static LinkedHashMap<String, ZipFile> ZipFileCache = new LinkedHashMap<String, ZipFile>() {
		 protected synchronized boolean removeEldestEntry(Map.Entry<String, ZipFile>  eldest) {
//			 	System.err.println("ZipFileCache size: "+size());
			 	
			 	// explicitly loop through close and release entries (return true not reliable?)
			 	if(size() > MAX_ZIPFILES) {
			 		for (Map.Entry<String, ZipFile> entry : ZipFileCache.entrySet()) {
			 			try{ entry.getValue().close(); } catch(Exception e){};  	// explicit close
//			 			System.err.println("ZipFileCache remove key: "+entry.getKey());
			 			ZipFileCache.remove(entry.getKey());						// explicit remove
			 			if(size() <= MAX_ZIPFILES) break;
			 		}
//				 	System.err.println("ZipFileCache >remove size: "+ZipFileCache.size());
			 	}
			 	return false;
//	            return ((size() >  MAX_FILES));
	         }
	};		
	
	// ZipFileCache getter
	static synchronized ZipFile cachedZipFile(String myZipFile) throws Exception {
		ZipFile thisZipFile = ZipFileCache.get(myZipFile);
		if(thisZipFile == null) {
			CTinfo.debugPrint(cacheProfile,"OPEN ZIPFILE");
//			System.err.println("OPEN ZIPFILE: "+myZipFile);
			thisZipFile = new ZipFile(myZipFile);
			ZipFileCache.put(myZipFile, thisZipFile);
		}
		return thisZipFile;
	}
	
	static HashMap<String,CTFile[]> fileListByChan = new HashMap<String,CTFile[]>();				// provide way to reset?
	
	// TO DO:  getter/setters/config/reset methods for each cache type
}
