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

package erigo.ctstream;

/**
 * WriteTask is a Runnable object which takes screen capture byte arrays off the
 * blocking queue (CTstream.queue) and writes them to CT.
 * 
 * POSSIBLE TO-DO:
 * 
 * This class could be extended to support a more general data management/coordination scheme
 * to manage multiple streams of data, each stored as packets in separate queues. One queue
 * is the "master" queue and the rest of the queues are "slave" queues.  The master queue
 * defines the following:
 * 
 * 	o accepted time range
 * 	o when to flush to CT
 * 
 * For CTstream we would have 2 queues:
 * 	o master queue contains audio packets
 * 	o slave queue contains images
 * 
 * Processing handled as follows (this logic mirrors what is currently implemented in AudiocapTask):
 * 
 * 	while(true)
 * 		keep accumulating data (ie, images) in slave queue
 * 		when packet shows up in the master (audio) queue
 * 			determine [last time, current time] time range from the new audio packet
 * 			filter through the packets in the image queue to determine which ones fall inside this [last time, current time] range
 * 			call setTime/putData for each of the image packets that make it through the filter
 * 			call setTime/putData for the audio packet from master queue
 * 			call flush
 *	end
 *
 * @author John P. Wilson
 * @version 02/06/2017
 *
 */

public class WriteTask implements Runnable {
	
	public CTstream cts = null;

	private boolean bRunning = true;
	
	/**
	 * 
	 * Constructor
	 * 
	 * @param  ctsI  The CTstream object.
	 */
	public WriteTask(CTstream ctsI) {
		cts = ctsI;
	}

	/**
	 * Signal to shut down this WriteTask
	 */
	public void shutDown() {
		bRunning = false;
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
			if (cts.bShutdown || !bRunning) {
				break;
			}
			byte[] jpegByteArray = null;
			long currentTime=0;  
			try {
				// We used to queue just the byte array of the JPG,
				// but now queue TimeValue objects which is a combo
				// of the JPG and time
				// jpegByteArray = queue.take();
				TimeValue tv = cts.screencapStream.queue.take();
				currentTime = tv.time;
				jpegByteArray = tv.value;
			} catch (InterruptedException e) {
				if (cts.bShutdown || !bRunning) {
					break;
				} else {
					System.err.println("Caught exception working with the LinkedBlockingQueue:\n" + e);
				}
			}
			if (jpegByteArray != null) {
				try {
					// JPW 2017-02-10 synchronize calls to the common CTwriter object using a common CTstream.ctwLockObj object
					synchronized(cts.ctwLockObj) {
//						long nextTime = cts.getNextTime();
						long nextTime = currentTime;				// MJM 4/3/17:  use queued time
						cts.ctw.setTime(nextTime);
						cts.ctw.putData(cts.channelName,jpegByteArray);
					}
				} catch (Exception e) {
					if (cts.bShutdown || !bRunning) {
						break;
					} else {
						System.err.println("Caught CTwriter exception:\n" + e);
					}
				}
				System.out.print("Wx");
				numScreenCaps += 1;
				if ((numScreenCaps % 40) == 0) {
					System.err.print("\n");
				}
			}
		}
		
		System.err.println("WriteTask is exiting");
		
	}
	
}
