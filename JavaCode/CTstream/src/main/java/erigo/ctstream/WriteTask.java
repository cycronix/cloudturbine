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

import cycronix.ctlib.CTftp;
import cycronix.ctlib.CTinfo;
import cycronix.ctlib.CTwriter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * WriteTask is a Runnable object which takes byte arrays from the queues of the various
 * DataStream objects and writes them to CT.
 *
 * WriteTask makes sure that data is sent to CT in time order.  This avoids the problem,
 * for instance, where we call flush() to seal off a block and then the next setTime/putData
 * calls are for a time that would have been in that (now closed) block.
 *
 * Flushing data is handled one of two ways:
 *
 * 1. If one DataStream is the "master" (and note, there can at most be only one master DataStream):
 *    bManualFlush = true
 *    send packets from all DataStreams to CT in time sequence order;
 *    every time we send a packet of data from the "master" DataStream to CT, call CTwriter.flush()
 *
 * 2. If there is no "master" DataStream:
 *    bManualFlush = false
 *    send packets from all DataStreams to CT in time sequence order;
 *    call CTwriter.flush() at the period defined by CTstream.flushMillis
 *
 * When there is no master DataStream, we could potentially use auto-flush, and in some sense this
 * would be easier; but code could get messy turning auto-flush on/off as a "master" DataStream
 * is turned off/on.  Thus, whether there is a master DataStream or not, in all cases we handle
 * flushing.
 *
 * @author John P. Wilson
 * @version 05/09/2017
 */

public class WriteTask implements Runnable {
	
	private CTstream cts = null;
	private CTwriter ctw = null;			// To write data to CT
	private Thread writeTaskThread = null;  // The thread running in this WriteTask
	public boolean bIsRunning = true;
	
	/**
	 * Constructor
	 * 
	 * @param  ctsI  The CTstream object.
	 * @throws IOException if there is any problem opening CTwriter
	 */
	public WriteTask(CTstream ctsI) throws IOException {
		cts = ctsI;
		CTinfo.setDebug(cts.bDebugMode);
		// Make sure outputFolder ends in a file separator
		String outFolderToUse = cts.outputFolder;
		if (!cts.outputFolder.endsWith(File.separator)) {
			outFolderToUse = cts.outputFolder + File.separator;
		}
		if (!cts.bFTP) {
			ctw = new CTwriter(outFolderToUse + cts.sourceName);
		} else {
			CTftp ctftp = new CTftp(outFolderToUse + cts.sourceName);
			try {
				ctftp.login(cts.ftpHost, cts.ftpUser, cts.ftpPassword);
				ctw = ctftp; // upcast to CTWriter
			} catch (Exception e) {
				throw new IOException( new String("Error logging into FTP server \"" + cts.ftpHost + "\":\n" + e.getMessage()) );
			}
		}
		ctw.setZipMode(cts.bZipMode);
		ctw.autoSegment(cts.numBlocksPerSegment);
	}

	/**
	 * Launch CT writes in a separate thread, executing in this class's run() method.
	 */
	public void start() {
		if (writeTaskThread != null) {
			System.err.println("\nERROR in WriteTask: writeTaskThread is not null");
			return;
		}
		writeTaskThread = new Thread(this);
		writeTaskThread.start();
	}

	/**
	 * Shut down this WriteTask
	 */
	public void stop() {
		bIsRunning = false;
		// shut down the separate thread executing in the run() method
		try {
			// System.err.println("\nWait for WriteTask to stop");
			writeTaskThread.join(500);
			if (writeTaskThread.isAlive()) {
				// WriteTask must be in a blocking wait; interrupt it
				writeTaskThread.interrupt();
				writeTaskThread.join(500);
			}
			// if (!writeTaskThread.isAlive()) {
			// 	   System.err.println("WriteTask has stopped");
			// }
		} catch (InterruptedException ie) {
			System.err.println("Caught exception trying to stop WriteTask:\n" + ie);
		}
		// shut down CTwriter
		if (ctw != null) {
			// System.err.println("Close CTwriter");
			ctw.close();
			ctw = null;
		}
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
		long nextAudioTime = Long.MAX_VALUE;
		byte[] nextAudioValue = null;
		while (bIsRunning) {
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
			if ( (nextAudioValue == null) && (cts.audioStream != null) && (cts.audioStream.queue != null) ) {
				TimeValue tv = cts.audioStream.queue.poll();
				if ( (tv != null) && (tv.value != null) ) {
					nextAudioTime = tv.time;
					nextAudioValue = tv.value;
				}
			}
			if ( (nextScreencapValue == null) && (nextWebcamValue == null) && (nextAudioValue == null) ) {
				// Nothing to send to CT
				// Sleep for a bit but then we'll still go through the remainder of this function
				// (ie, don't execute "continue") because we'll check down below if there is data
				// that should be flushed.
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			// See if there's data to send to CT
			try {
				// Put the oldest available data next
				long[] timeVals = new long[3];
				timeVals[0] = nextScreencapTime;
				timeVals[1] = nextWebcamTime;
				timeVals[2] = nextAudioTime;
				Arrays.sort(timeVals);
				long oldestTime = timeVals[0];
				if ( (nextScreencapTime != Long.MAX_VALUE) && (nextScreencapTime == oldestTime) && (nextScreencapValue != null) ) {
					ctw.setTime(nextScreencapTime);
					ctw.putData(cts.screencapStream.channelName,nextScreencapValue);
					System.out.print("P");
					numPuts += 1;
					if ((numPuts % 40) == 0) {
						System.err.print("\n");
					}
					bDataToBeFlushed = true;
					if (cts.screencapStream.bManualFlush) {
						bDataToBeFlushed = false;
						timeOfLastFlushMillis = System.currentTimeMillis();
						ctw.flush(true); // gapless
						System.out.print("F");
					}
					nextScreencapTime = Long.MAX_VALUE;
					nextScreencapValue = null;
				} else if ( (nextWebcamTime != Long.MAX_VALUE) && (nextWebcamTime == oldestTime) && (nextWebcamValue != null) ) {
					ctw.setTime(nextWebcamTime);
					ctw.putData(cts.webcamStream.channelName,nextWebcamValue);
					System.out.print("P");
					numPuts += 1;
					if ((numPuts % 40) == 0) {
						System.err.print("\n");
					}
					bDataToBeFlushed = true;
					if (cts.webcamStream.bManualFlush) {
						bDataToBeFlushed = false;
						timeOfLastFlushMillis = System.currentTimeMillis();
						ctw.flush(true); // gapless
						System.out.print("F");
					}
					nextWebcamTime = Long.MAX_VALUE;
					nextWebcamValue = null;
				} else if ( (nextAudioTime != Long.MAX_VALUE) && (nextAudioTime == oldestTime) && (nextAudioValue != null) ) {
					ctw.setTime(nextAudioTime);
					ctw.putData(cts.audioStream.channelName,nextAudioValue);
					System.out.print("P");
					numPuts += 1;
					if ((numPuts % 40) == 0) {
						System.err.print("\n");
					}
					bDataToBeFlushed = true;
					if (cts.audioStream.bManualFlush) {
						bDataToBeFlushed = false;
						timeOfLastFlushMillis = System.currentTimeMillis();
						ctw.flush(true); // gapless
						System.out.print("F");
					}
					nextAudioTime = Long.MAX_VALUE;
					nextAudioValue = null;
				}
			} catch (Exception e) {
				if (!bIsRunning) {
					break;
				} else {
					System.err.println("CTwriter exception:\n" + e);
				}
			}
			// If no DataStreams are specified as "manual flush" (where CTwriter.flush() is called after data is
			// sent to CT) then we need to periodically flush data to CT.  This is similar to auto-flush.
			// Only do this if there are no streams which are manually flushed.
			int numManualFlush = numManualFlushStreams();
			if (numManualFlush > 1) {
				// ERROR - we should have at most 1 DataStream which specifies manual flush
				// Launch a separate thread to shut down CTstream
				System.err.println("\nERROR: WriteTask has detected that more than one DataStream is specified as manual flush.\n");
				Runnable shutdownRunnable = new Runnable() {
					public void run() {
						cts.stopCapture();
					}
				};
				Thread shutdownThread = new Thread(shutdownRunnable);
				shutdownThread.start();
				break;
			}
			if ( bDataToBeFlushed && (numManualFlush == 0) ) {
				// no manual flush DataStreams; see if it is time to do a flush
				long timeMillis = System.currentTimeMillis();
				if (cts.flushMillis <= (timeMillis-timeOfLastFlushMillis)) {
					try {
						// System.err.println("Flush data: cts.flushMillis=" + cts.flushMillis + ", timeOfLastFlushMillis=" + timeOfLastFlushMillis + ", current time=" + timeMillis);
						ctw.flush();
						System.out.print("F");
						bDataToBeFlushed = false;
					} catch (IOException e) {
						System.err.println("WriteTask: caught exception on flush:\n" + e);
						e.printStackTrace();
					}
					timeOfLastFlushMillis = timeMillis;
				}
			}
		}
		// System.err.println("WriteTask is exiting");
	}

	/**
	 * Determine the number of "manual flush" DataStreams which are in use; there should be at most 1.
	 * @return the number of manual flush streams
	 */
	private int numManualFlushStreams() {
		int numManualFlush = 0;
		if ( (cts.screencapStream != null) && (cts.screencapStream.queue != null) && (cts.screencapStream.bManualFlush) ) {
			++numManualFlush;
		}
		if ( (cts.webcamStream != null) && (cts.webcamStream.queue != null) && (cts.webcamStream.bManualFlush) ) {
			++numManualFlush;
		}
		if ( (cts.audioStream != null) && (cts.audioStream.queue != null) && (cts.audioStream.bManualFlush) ) {
			++numManualFlush;
		}
		return numManualFlush;
	}
	
}
