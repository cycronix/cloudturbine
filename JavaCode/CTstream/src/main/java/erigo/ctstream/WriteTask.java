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
import cycronix.ctlib.CThttp;
import cycronix.ctlib.CTinfo;
import cycronix.ctlib.CTwriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import erigo.ctstream.CTstream.CTWriteMode;

/**
 * WriteTask is a Runnable object which takes byte arrays from the DataStream queues and
 * writes them to CT.  This class handles any number of DataStreams (specifically, all
 * those included in Vector dataStreams from the CTstream class).
 *
 * WriteTask makes sure that data is sent to CT in time order.  This avoids the problem,
 * for instance, where we call flush() to seal off a block and then later setTime/putData
 * calls are for a time that would have been in that (now closed) block.
 *
 * Flushing data is handled one of two ways:
 *
 * 1. If one DataStream is the "master" (note, there can at most be only one master DataStream):
 *    send packets from all DataStreams to CT in time sequence order
 *    every time we send a packet of data from the "master" DataStream to CT, call CTwriter.flush()
 *
 * 2. If there is no "master" DataStream:
 *    send packets from all DataStreams to CT in time sequence order
 *    call CTwriter.flush() at the period defined by CTstream.flushMillis
 *
 * A "master" DataStream is one where bManualFlush is true.
 *
 * When there is no master DataStream, we could potentially use auto-flush, and in some sense this
 * would be easier; but code could get messy turning auto-flush on/off as a "master" DataStream
 * is turned off/on.  Thus, whether there is a master DataStream or not, in all cases we handle
 * flushing in this class.
 *
 * @author John P. Wilson
 * @version 02/16/2018
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
		// NOTE: the output folder (cts.outputFolder) is only for use with the local write option
		if (cts.writeMode == CTWriteMode.LOCAL) {
			// If it isn't blank, make sure outputFolder ends in a file separator
			String outFolderToUse = cts.outputFolder;
			if (!outFolderToUse.isEmpty()) {
				if (!cts.outputFolder.endsWith(File.separator)) {
					outFolderToUse = cts.outputFolder + File.separator;
				}
			}
			ctw = new CTwriter(outFolderToUse + cts.sourceName);
		} else if (cts.writeMode == CTWriteMode.FTP) {
			CTftp ctftp = new CTftp(cts.sourceName);
			try {
				ctftp.login(cts.serverHost, cts.serverUser, cts.serverPassword);
				ctw = ctftp; // upcast to CTWriter
			} catch (Exception e) {
				throw new IOException( new String("Error logging into FTP server \"" + cts.serverHost + "\":\n" + e.getMessage()) );
			}
		} else if (cts.writeMode == CTWriteMode.HTTP) {
			CThttp cthttp = new CThttp(cts.sourceName,cts.serverHost);
			if (!cts.serverUser.isEmpty()) {
				try {
					cthttp.login(cts.serverUser, cts.serverPassword);
				} catch (Exception e) {
					throw new IOException( new String("Error logging into HTTP server \"" + cts.serverHost + "\":\n" + e.getMessage()) );
				}
			}
			ctw = cthttp; // upcast to CTWriter
		}
		ctw.setZipMode(cts.bZipMode);
		ctw.autoSegment(cts.numBlocksPerSegment);
		if (cts.bEncrypt) {
			try {
				ctw.setPassword(cts.encryptionPassword);
			} catch (Exception e) {
				throw new IOException( new String("Error setting CT password:\n" + e.getMessage()) );
			}
		}
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
	 * Write data from the various DataStreams to CloudTurbine.  Start at the oldest available data and
	 * work forward in time.  This avoids getting the "java.io.IOException: OOPS negative CT time (dropping)"
	 * errors from CT and dropping data samples.
	 */
	public void run() {
		int numPuts = 0;
		long timeOfLastFlushMillis = System.currentTimeMillis();
		boolean bDataToBeFlushed = false;
		List<DataStreamSample> samples = new ArrayList<DataStreamSample>();
		while (bIsRunning) {
			// Make sure our List includes a sample from each running DataStream
			// NOTE: we synchronize access to Vector cts.dataStreams
			// At the same time, we'll check the number of "manual flush" streams we've got
			int numManualFlush = 0;
			synchronized (cts.dataStreamsLock) {
				// Check the number of "manual flush" streams we've got
				for (DataStream ds : cts.dataStreams) {
					addSample(ds,samples);
					// Check if this is a "manual flush" stream
					if ( (ds != null) && ds.bIsRunning && ds.bManualFlush ) {
						++numManualFlush;
					}
				}
			}
			// FIREWALL
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
			if (samples.isEmpty()) {
				// Nothing to send to CT
				// Sleep for a bit, but then we'll still go through the remainder of this function
				// (ie, don't do a "continue" here) because down below we'll check if there is data
				// that should be flushed.
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} else {
				// Send the oldest available data to CT first
				long oldestTime = Long.MAX_VALUE;
				int idx_sample_with_oldest_time = -1;
				for (int i = 0; i < samples.size(); ++i) {
					if (samples.get(i).time < oldestTime) {
						idx_sample_with_oldest_time = i;
						oldestTime = samples.get(i).time;
					}
				}
				DataStreamSample dss = samples.get(idx_sample_with_oldest_time);
				try {
					ctw.setTime(dss.time);
					ctw.putData(dss.channelName, dss.value);
					if (cts.bPrintDataStatusMsg) { System.err.print("P"); }
					numPuts += 1;
					if ((numPuts % 40) == 0) {
						if (cts.bPrintDataStatusMsg) { System.err.print("\n"); }
					}
					bDataToBeFlushed = true;
					if (dss.bManualFlush) {
						bDataToBeFlushed = false;
						timeOfLastFlushMillis = System.currentTimeMillis();
						ctw.flush(true); // gapless
						if (cts.bPrintDataStatusMsg) { System.err.print("F"); }
					}
				} catch (Exception e) {
					if (!bIsRunning) {
						// This WriteTask is to be shut down; just break out
						break;
					} else {
						System.err.println("\nCTwriter exception putting data in channel " + dss.channelName + ":\n" + e);
					}
				}
				// We're done with this sample; remove it from the List
				samples.remove(dss);
			}

			// Our pseudo "auto flush" implementation
			// If no DataStreams are specified as "manual flush" (where CTwriter.flush() is called after data is
			// sent to CT) then we need to periodically flush data to CT.
			if ( bDataToBeFlushed && (numManualFlush == 0) ) {
				// no manual flush DataStreams; see if it is time to do a flush
				long timeMillis = System.currentTimeMillis();
				if (cts.flushMillis <= (timeMillis-timeOfLastFlushMillis)) {
					try {
						// System.err.println("Flush data: cts.flushMillis=" + cts.flushMillis + ", timeOfLastFlushMillis=" + timeOfLastFlushMillis + ", current time=" + timeMillis);
						ctw.flush();
						if (cts.bPrintDataStatusMsg) { System.err.print("F"); }
						bDataToBeFlushed = false;
					} catch (IOException e) {
						System.err.println("\nCTwriter exception flushing data:\n" + e);
						e.printStackTrace();
					}
					timeOfLastFlushMillis = timeMillis;
				}
			}
		}
		// System.err.println("WriteTask is exiting");
	}

	/**
	 * Make sure a sample from the given DataStream is included in the given list of samples.
	 * If there isn't, then add one.
	 *
	 * @param dsI		DataStream to take a sample from
	 * @param samplesI	List of samples; we will make sure this list includes a sample from the given DataStream
	 */
	private void addSample(DataStream dsI, List<DataStreamSample> samplesI) {
		// Firewall
		if ( (dsI == null) || (!dsI.bIsRunning) || (dsI.queue == null) || (dsI.queue.isEmpty()) ) {
			// DataStream is not running
			return;
		}
		// See if we have a sample in samplesI from this DataStream; we do this by matching IDs
		for (DataStreamSample dss : samplesI) {
			if (dss.id == dsI.id) {
				// our list includes a sample from this DataStream; we're done
				return;
			}
		}
		// Add a sample from the DataStream to this List
		TimeValue tv = dsI.queue.poll();
		if ( (tv != null) && (tv.value != null) ) {
			// We've got a new sample to add!
			DataStreamSample dss = new DataStreamSample();
			dss.id = dsI.id;
			dss.channelName = dsI.channelName;
			dss.bManualFlush = dsI.bManualFlush;
			dss.time = tv.time;
			dss.value = tv.value;
			samplesI.add(dss);
		}
	}

	/**
	 * Private class to store one sample from one DataStream.
	 */
	private class DataStreamSample {
		public int id = -1;
		public String channelName = "";
		public boolean bManualFlush = false;
		public long time = Long.MAX_VALUE;
		public byte[] value = null;
	}
	
}
