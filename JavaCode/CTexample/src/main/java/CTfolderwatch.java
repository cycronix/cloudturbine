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

// demo code to polling watch single folder for new files

import java.io.File;
import java.util.HashSet;

public class CTfolderwatch {

	public static void main(String[] args) {
		String foldertest = "FolderTest";
		if(args.length>0) foldertest = args[0];
		int nmax = 100;
		if(args.length>1) nmax = Integer.parseInt(args[1]);
		int sleepMsec = 10;
		if(args.length>2) sleepMsec = Integer.parseInt(args[2]);
		
		HashSet<String> hset = new HashSet();
		
		System.err.println("monitoring folder: "+foldertest+", nmax: "+nmax);
		File folder = new File(foldertest);
		
		long t1 = System.nanoTime();
		File[] files=null;
		for(int i=0; i<nmax; i++) {
			long lastTime = System.nanoTime();
			files = folder.listFiles();
			for(File f:files) {
				if(hset.add(f.getName())) {
					if(i!=0 && files.length>1)		// skip initial filescan
						System.err.println("new file: "+f.getName()+", nfiles: "+files.length+", dt(msec): "+(System.nanoTime()-lastTime)/1000000.);
				}
			}
			try{ Thread.sleep(sleepMsec); } catch(Exception e){};
		}
		long t2 = System.nanoTime();
		System.err.println("Folder size: "+files.length+", avg time/list (msec): "+((t2-t1)/nmax)/1000000.);
	}
}
