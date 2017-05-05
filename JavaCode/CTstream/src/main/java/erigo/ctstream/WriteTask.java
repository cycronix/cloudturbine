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

import java.io.IOException;

/**
 * WriteTask is a Runnable object which takes byte arrays from the queues of the various
 * DataStream objects and writes them to CT.
 *
 * Need to make sure that data is sent to CT in time order.  This avoids the problem, for instance,
 * where auto-flush seals off a block and then the next setTime/putData calls are for a time that
 * would have been in that block.
 *
 * Flushing data is handled one of two ways:
 *
 * 1. If there is no "master" DataStream: use auto flush; just keep sending packets to CT in time sequence order;
 *    let CT handle flushing.
 *
 * 2. If one DataStream is the "master": keep sending packets to CT in time sequence order; every time we send
 *    data from the "master" DataStream to CT, finish it with a call to CTwriter.flush().
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
 * @version 05/05/2017
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
	 * run()
	 * 
	 * Write data to CloudTurbine.
	 *
	 */
	public void run() {
		int numPuts = 0;
		long timeOfLastFlushMillis = System.currentTimeMillis();
		boolean bDataToBeFlushed = false;
		long nextScreencapTime = Long.MAX_VALUE;
		byte[] nextScreencapValue = null;
		long nextWebcamTime = Long.MAX_VALUE;
		byte[] nextWebcamValue = null;
		while (bRunning) {
			// Make sure we have the next data from each DataStream
			if ( (nextScreencapValue == null) && (cts.screencapStream != null) && (cts.screencapStream.queue != null) ) {
				TimeValue tv = cts.screencapStream.queue.poll();
				if ( (tv != null) && (tv.value != null) ) {
					nextScreencapTime = tv.time;
					nextScreencapValue = tv.value;
				}
			}
			if ( (nextWebcamValue == null) && (cts.webcamStream != null) && (cts.webcamStream.queue != null) ) {
				TimeValue tv = cts.webcamStream.queue.poll();
				if ( (tv != null) && (tv.value != null) ) {
					nextWebcamTime = tv.time;
					nextWebcamValue = tv.value;
				}
			}
			if ( (nextScreencapValue == null) && (nextWebcamValue == null) ) {
				// Nothing to send to CT
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			// See if there's data to send to CT
			try {
				boolean bPutData = false;
				if ( (nextScreencapTime <= nextWebcamTime) && (nextScreencapValue != null) ) {
					cts.ctw.setTime(nextScreencapTime);
					cts.ctw.putData(cts.screencapStream.name,nextScreencapValue);
					bPutData = true;
					bDataToBeFlushed = true;
					if (cts.screencapStream.bManualFlush) {
						bDataToBeFlushed = false;
						timeOfLastFlushMillis = System.currentTimeMillis();
						cts.ctw.flush(true); // gapless
					}
					nextScreencapTime = Long.MAX_VALUE;
					nextScreencapValue = null;
				} else if (nextWebcamValue != null) {
					cts.ctw.setTime(nextWebcamTime);
					cts.ctw.putData(cts.webcamStream.name,nextWebcamValue);
					bPutData = true;
					bDataToBeFlushed = true;
					if (cts.webcamStream.bManualFlush) {
						bDataToBeFlushed = false;
						timeOfLastFlushMillis = System.currentTimeMillis();
						cts.ctw.flush(true); // gapless
					}
					nextWebcamTime = Long.MAX_VALUE;
					nextWebcamValue = null;
				}
				if (bPutData) {
					System.out.print("x");
					numPuts += 1;
					if ((numPuts % 40) == 0) {
						System.err.print("\n");
					}
				}
			} catch (Exception e) {
				if (!bRunning) {
					break;
				} else {
					System.err.println("CTwriter exception:\n" + e);
				}
			}
			// Handle flushing
			// This isn't auto flushing, but pretty close - even if no DataStreams are currently open,
			// if there is still data to be flushed (ie, bDataToBeFlushed is true) then we'll check
			// if it is time to flush.
			if (bDataToBeFlushed) {
				// there must not be any DataStreams that are set for manual flush; see if it is time to do a flush
				long timeMillis = System.currentTimeMillis();
				if (cts.flushMillis <= (timeMillis-timeOfLastFlushMillis)) {
					try {
						// System.err.println("Flush data: cts.flushMillis=" + cts.flushMillis + ", timeOfLastFlushMillis=" + timeOfLastFlushMillis + ", current time=" + timeMillis);
						cts.ctw.flush();
						bDataToBeFlushed = false;
					} catch (IOException e) {
						System.err.println("WriteTask: caught exception on flush:\n" + e);
						e.printStackTrace();
					}
					timeOfLastFlushMillis = timeMillis;
				}
			}
		}
		System.err.println("WriteTask is exiting");
	}
	
}
