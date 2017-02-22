import java.io.File;
import java.io.IOException;

import cycronix.ctlib.*;

/**
 * CloudTurbine stitch segments together under a source
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 02/22/2017
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

public class CTstitch {

	public static void main(String[] args) {

		String rootFolder = "CTdata";
		if(args.length > 0) rootFolder = args[0];
		long newTime = 0;
		long priorTime = 0;
		
		try {
			CTreader ctr = new CTreader(rootFolder);	// set CTreader to rootFolder

			// loop and read what source(s) and chan(s) are available
			for(String source:ctr.listSources()) {
				System.err.println("CTstitch, checking source: "+source);

				// parse baseTime from source
				String[] parseTime = source.split(File.separator);
				try { Long.parseLong(parseTime[0]); }		// sanity check:  make sure numeric
				catch(NumberFormatException e) {
					System.err.println("Error: Non-numeric baseTime: "+source);
					System.exit(0);
				}

				String rootSource = source;
				if(parseTime.length > 1) rootSource = parseTime[0];		// skip possible segment layer folders
				String sourcePath = rootFolder + File.separator + rootSource;
				newTime = 1 + (long)(ctr.newTime(sourcePath)*1000.);		// sec -> msec
				if(newTime < 1000) throw new IOException("unreasonable newTime: "+newTime);
				
				if(priorTime>0) {		// prior source
					String newBase = rootFolder + File.separator + priorTime;
					if(sourcePath.equals(newBase)) System.err.println("Not renaming, identical already: "+newBase);
					else {
						System.err.println("Renaming: "+sourcePath+" to: "+ newBase);
						File file = new File(sourcePath);
						File file2 = new File(newBase);
						if (file2.exists()) System.err.println("Warning, file exists (skipping); "+newBase);
						else {
							if(!file.renameTo(file2)) throw new IOException("Failed to rename: "+sourcePath+" to: "+newBase);
						}
					}
				}

				priorTime = newTime;
			}
		}
		catch(Exception e) {
			System.err.println("CT exception: "+e);
			e.printStackTrace();
		} 
	}

}


