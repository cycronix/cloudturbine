/*
Copyright 2017 Cycronix

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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A DataStream for capturing audio data.
 *
 * @author Matt Miller, Cycronix
 * @version 2017-05-08
 *
 */
public class AudioStream extends DataStream implements Runnable {

	private int frequency = 8000; 		// audio sample frequency (8000, 22050, 44100)
	private AudioFormat format = null;	// audio format
	private TargetDataLine line = null;	// audio line
	private Thread audioThread = null;	// audio capture is performed in this thread, which executes in the run() method

	/**
	 * AudioStream constructor
	 *
	 * @param ctsI          CTstream object
	 * @param channelNameI  Channel name
	 */
	// constructor
	public AudioStream(CTstream ctsI, String channelNameI) {
		super(false);
		channelName = channelNameI;
		cts = ctsI;
		bCanPreview = false;
		bManualFlush = true;
	}

	/**
	 * Implementation of the abstract start() method from DataStream
	 */
	public void start() throws Exception {
		if (queue != null) { throw new Exception("ERROR in AudioStream.start(): LinkedBlockingQueue object is not null"); }
		// Make sure we can open the audio line
		try {
			format = getFormat();
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
			line = (TargetDataLine) AudioSystem.getLine(info);
			line.open(format);
			line.start();
		} catch (LineUnavailableException e) {
			throw new Exception("Audio stream error, could not open the audio line:\n" + e.getMessage());
		}
		bIsRunning = true;
		// Make sure there is no other audio capture running
		stopAudio();
		queue = new LinkedBlockingQueue<TimeValue>();
		// start the audio capture
		audioThread = new Thread(this);
		audioThread.start();
	}

	/**
	 * Implementation of the abstract stop() method from DataStream
	 */
	public void stop() {
		super.stop();
		stopAudio();
	}

	/**
	 * Implementation of the abstract update() method from DataStream
	 *
	 * This method is called when there has been some real-time change to the UI settings that may affect this class
	 */
	public void update() {
		super.update();
		if (!bIsRunning) {
			// Not currently running; just return
			return;
		}
		// For AudioStream, there are no other real-time settings we need to adjust for; just return
		return;
	}

	/**
	 * Acquire audio data and put it on the data queue.
	 */
	public void run() {
		double flushInterval = cts.flushMillis / 1000.;
		int bufferSize = (int) Math.round(format.getSampleRate() * format.getFrameSize() * flushInterval);    // bytes per flushMillis
		int bufferAlloc = bufferSize * (int) (Math.max(1., flushInterval) / flushInterval);                    // allocate 1sec min
		byte buffer[] = new byte[4 * bufferAlloc];                                                            // generous buffer to avoid overflow?
		int numFlushes = 0;
		try {
			System.err.println("\n\nWARNING AudioStream: SHOULD WE CALL ctw.setTime() TO ESTABLISH START OF FIRST BLOCK?\n");
			// ctw.setTime(cts.getNextTime());        // establish start time of first audio block
			while (bIsRunning) {
				int count = line.read(buffer, 0, bufferSize); // blocking call to read a buffer's worth of audio samples
				// Slurp up any extra; limit the number of bytes to slurp up so buffer doesn't overflow
				int numExtraBytesToSlurpUp = line.available();
				// System.err.println("\ngot " + count + " audio bytes; there are still " + numExtraBytesToSlurpUp + " bytes available; buffer size = " + buffer.length);
				if (numExtraBytesToSlurpUp > (buffer.length - count)) {
					// To not overflow the buffer: limit number of extra bytes to slurp up
					numExtraBytesToSlurpUp = buffer.length - count;
				}
				if (numExtraBytesToSlurpUp > 0) {
					// slurp up extra bytes that are available
					count += line.read(buffer, count, numExtraBytesToSlurpUp);
				}
				// drop whole buffer if below threshold?
				// if(!audioThreshold(buffer, 100)) continue;
				// webscan not up to task of handling empty data in RT

				// Save the audio data
				if (count > 0) {
					try {
						queue.put(new TimeValue(cts.getNextTime(), addWaveHeader(buffer, count)));
						if (cts.bPrintDataStatusMsg) { System.err.print("a"); }
					} catch (Exception e) {
						if (bIsRunning) {
							System.err.println("\nAudioStream: exception thrown adding data to queue:\n" + e);
							e.printStackTrace();
						}
						break;
					}
				}
			}
			// System.err.println("Stopping audio capture");
		} catch (Exception e) {
			System.err.println("\nAudioStream I/O problem:");
			e.printStackTrace();
			System.err.println("Exiting from audio capture");
		}
	}

	/**
	 * Stop a running audio capture
	 */
	private void stopAudio() {
		if (audioThread == null) {
			return;
		}
		try {
			// Wait for the thread to finish
			audioThread.join(4* cts.flushMillis); // audio capture isn't necessarily precise, so wait up to 4*flushMillis
			if (audioThread.isAlive()) {
				// the thread must be held up; interrupt it
				audioThread.interrupt();
				audioThread.join(500);
			}
			if (!audioThread.isAlive()) {
				// System.err.println("AudioStream has stopped");
			}
		} catch (InterruptedException ie) {
			System.err.println("Caught exception trying to stop AudioStream:\n" + ie);
		}
		audioThread = null;
		// close the audio line
		line.close();
	}

	/**
	 * Specify the audio format: sample rate, sample size, number of channels, byte format
	 * @return the AudioFormat object
	 */
	private AudioFormat getFormat() {
		float sampleRate = frequency;
		int sampleSizeInBits = 16;
		int channels = 1;
		boolean signed = true;
		boolean bigEndian = false;
		return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
	}

	/**
	 * Add a ".wav" header to the audio data.
	 *
	 * @param dataBuffer    the raw audio data
	 * @param totalAudioLen length of the audio data
	 * @return the updated audio data including ".wav" header
	 * @throws IOException
	 */
	private byte[] addWaveHeader(byte[] dataBuffer, int totalAudioLen) throws IOException {
		byte RECORDER_BPP = 16;
		// long totalAudioLen = dataBuffer.length;
		long totalDataLen = totalAudioLen + 36;
		long longSampleRate = frequency;
		int channels = 1;
		long byteRate = RECORDER_BPP * frequency * channels/8;

		byte[] header = new byte[44];

		header[0] = 'R';  // RIFF/WAVE header
		header[1] = 'I';
		header[2] = 'F';
		header[3] = 'F';
		header[4] = (byte) (totalDataLen & 0xff);
		header[5] = (byte) ((totalDataLen >> 8) & 0xff);
		header[6] = (byte) ((totalDataLen >> 16) & 0xff);
		header[7] = (byte) ((totalDataLen >> 24) & 0xff);
		header[8] = 'W';
		header[9] = 'A';
		header[10] = 'V';
		header[11] = 'E';
		header[12] = 'f';  // 'fmt ' chunk
		header[13] = 'm';
		header[14] = 't';
		header[15] = ' ';
		header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
		header[17] = 0;
		header[18] = 0;
		header[19] = 0;
		header[20] = 1;  // format = 1
		header[21] = 0;
		header[22] = (byte) channels;
		header[23] = 0;
		header[24] = (byte) (longSampleRate & 0xff);
		header[25] = (byte) ((longSampleRate >> 8) & 0xff);
		header[26] = (byte) ((longSampleRate >> 16) & 0xff);
		header[27] = (byte) ((longSampleRate >> 24) & 0xff);
		header[28] = (byte) (byteRate & 0xff);
		header[29] = (byte) ((byteRate >> 8) & 0xff);
		header[30] = (byte) ((byteRate >> 16) & 0xff);
		header[31] = (byte) ((byteRate >> 24) & 0xff);
		header[32] = (byte) (channels * 16 / 8);  // block align
		header[33] = 0;
		header[34] = RECORDER_BPP;  // bits per sample
		header[35] = 0;
		header[36] = 'd';
		header[37] = 'a';
		header[38] = 't';
		header[39] = 'a';
		header[40] = (byte) (totalAudioLen & 0xff);
		header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
		header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
		header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

		byte[] waveBuffer = new byte[header.length + totalAudioLen];
		System.arraycopy(header, 0, waveBuffer, 0, header.length);	

		System.arraycopy(dataBuffer, 0, waveBuffer, header.length, totalAudioLen);

		return waveBuffer;
	}

	/**
	 * Check if audio buffer has sound volume above threshold
	 *
	 * @param bytes		the audio data
	 * @param threshold	desired threshhold
	 * @return false if all audio data is below the given threshhold
	 */
	private boolean audioThreshold(byte[] bytes, int threshold) {
		// to turn bytes to shorts as either big endian or little endian. 
		short[] shorts = new short[bytes.length/2];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
		for(int i=0; i<shorts.length; i++) if(shorts[i] > threshold) return true;
		System.err.println("Audio below threshold!");
		return false;
	}
}
