/*
Copyright 2017 Erigo Technologies LLC

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

package erigo.cttext;

/**
 * WriteTask is a Runnable object which takes Strings off the
 * blocking queue (CTtext.queue) and writes them to CT.
 *
 * @author John P. Wilson
 * @version 02/10/2017
 *
 */

public class WriteTask implements Runnable {
	
	private CTtext hCTtext = null;
	
	/**
	 * 
	 * Constructor
	 * 
	 * @param  ctTextI  The CTtext object.
	 */
	public WriteTask(CTtext ctTextI) {
		hCTtext = ctTextI;
	}
	
	/**
	 * 
	 * run()
	 * 
	 * This method performs the work of writing screen captures to CloudTurbine.
	 * A while loop takes screen capture byte arrays off the blocking queue and
	 * send them to CT.
	 */
	public void run() {
		
		int numScreenCaps = 0;
		
		while (true) {
			if (!hCTtext.bCTrunning) {
				break;
			}
			String nextStr = null;
			try {
				nextStr = hCTtext.queue.take();
			} catch (InterruptedException e) {
				if (!hCTtext.bCTrunning) {
					break;
				} else {
					System.err.println("Caught exception working with the LinkedBlockingQueue:\n" + e);
				}
			}
			if ((nextStr != null) && (!nextStr.isEmpty())) {
				try {
					// synchronize calls to the common CTwriter object using a common CTtext.ctwLockObj object
					synchronized(hCTtext.ctwLockObj) {
						hCTtext.ctw.setTime(System.currentTimeMillis());
						hCTtext.ctw.putData(hCTtext.ctSettings.getChanName(),nextStr);
						// Flush if we're doing manual flushes (i.e., we *aren't* doing auto flushes)
		    			if (hCTtext.ctSettings.getBManualFlush()) {
		    				hCTtext.ctw.flush();
		    			}
					}
				} catch (Exception e) {
					if (!hCTtext.bCTrunning) {
						break;
					} else {
						System.err.println("Caught CTwriter exception:\n" + e);
					}
				}
				System.out.print("x");
				numScreenCaps += 1;
				if ((numScreenCaps % 40) == 0) {
					System.err.print("\n");
				}
			}
		}
		
		System.err.println("WriteTask is exiting");
		
	}
	
}
