import java.io.File;
import java.io.IOException;

import cycronix.ctlib.*;

/**
 * CloudTurbine stitch sessions together under a source
 * Note hierarchy:  CTdata/Source/Session/[Segment/]Block/Point
 * Warning: Renames given source/session folders!  (Manually pre-copy for no-clobber).
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 02/22/2017
 * 
*/

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

public class CTstitch {

	public static void main(String[] args) {

		String sourceFolder = "CTsource";
		if(args.length > 0) sourceFolder = args[0];
		long priorTime = 0;
		boolean init = true;
		
		try {
			CTreader ctr = new CTreader(sourceFolder);	// set CTreader to rootFolder

			// loop and read what source(s) and chan(s) are available
			for(String session:ctr.listSources()) {
				System.err.println("CTstitch, checking session: "+session);

				// parse baseTime from source
				String[] parseTime = session.split(File.separator);
				try { Long.parseLong(parseTime[0]); }		// sanity check:  make sure numeric
				catch(NumberFormatException e) {
					System.err.println("Error: Non-numeric session time: "+session);
					System.exit(0);
				}

				String sessionRoot = session;
				if(parseTime.length > 1) sessionRoot = parseTime[0];		// skip possible segment layer folders
				String sessionPath = sourceFolder + File.separator + sessionRoot;
				
				if(init) {
					// JPW, change from using sessionPath to sessionRoot (ie, use relative path, not including root folder)
					priorTime = 1 + (long)(ctr.newTime(sessionRoot)*1000.);		// sec -> msec
				}
				else {		// prior source
					String newSession = sourceFolder + File.separator + priorTime;
					if(sessionPath.equals(newSession)) System.err.println("Not renaming, identical already: "+newSession);
					else {
						System.err.println("Renaming: "+sessionPath+" to: "+ newSession);
						File file = new File(sessionPath);
						File file2 = new File(newSession);
						if (file2.exists()) System.err.println("Warning, file exists (skipping); "+newSession);
						else {
							if(file.renameTo(file2)) {
								// JPW, change from using newSession to priorTime (ie, use relative path, not including root folder)
								priorTime = 1 + (long)(ctr.newTime(Long.toString(priorTime))*1000.);	// newest time from renamed source (sec -> msec)
							}
							else		throw new IOException("Failed to rename: "+sessionPath+" to: "+newSession);
						}
					}
				}

				init = false;
				if(priorTime < 1000) throw new IOException("unreasonable session time: "+priorTime);		// fail safe
			}
		}
		catch(Exception e) {
			System.err.println("CT exception: "+e);
			e.printStackTrace();
		} 
	}

}


