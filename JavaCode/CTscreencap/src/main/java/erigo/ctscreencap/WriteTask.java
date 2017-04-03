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

package erigo.ctscreencap;

/**
 * WriteTask is a Runnable object which takes screen capture byte arrays off the
 * blocking queue (CTscreencap.queue) and writes them to CT.
 *
 * @author John P. Wilson
 * @version 02/06/2017
 *
 */

public class WriteTask implements Runnable {
	
	public CTscreencap cts = null;
	
	/**
	 * 
	 * Constructor
	 * 
	 * @param  ctsI  The CTscreencap object.
	 */
	public WriteTask(CTscreencap ctsI) {
		cts = ctsI;
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
			if (cts.bShutdown) {
				break;
			}
			byte[] jpegByteArray = null;
			long currentTime=0;  
			try {
				// We used to queue just the byte array of the JPG,
				// but now queue TimeValue objects which is a combo
				// of the JPG and time
				// jpegByteArray = queue.take();
				TimeValue tv = cts.queue.take();
				currentTime = tv.time;
				jpegByteArray = tv.value;
			} catch (InterruptedException e) {
				if (cts.bShutdown) {
					break;
				} else {
					System.err.println("Caught exception working with the LinkedBlockingQueue:\n" + e);
				}
			}
			if (jpegByteArray != null) {
				try {
					// JPW 2017-02-10 synchronize calls to the common CTwriter object using a common CTscreencap.ctwLockObj object
					synchronized(cts.ctwLockObj) {
//						long nextTime = cts.getNextTime();
						long nextTime = currentTime;				// MJM 4/3/17:  use queued time
						cts.ctw.setTime(nextTime);
						cts.ctw.putData(cts.channelName,jpegByteArray);
					}
				} catch (Exception e) {
					if (cts.bShutdown) {
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
