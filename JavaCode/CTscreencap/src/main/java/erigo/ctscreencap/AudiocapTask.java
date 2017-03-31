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

package erigo.ctscreencap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import cycronix.ctlib.CTwriter;

/**
 * 
 * Capture audio stream, write to CT.
 * @author Matt Miller, Cycronix
 *
 */
public class AudiocapTask {

	protected boolean running;
	int frequency = 8000; 				//8000, 22050, 44100
	public Thread captureThread = null; // JPW, so we know when the run() method is done
	
	// constructor
	public AudiocapTask(CTscreencap cts, CTwriter ctw, long flushMillis) {

		try {		
			final AudioFormat format = getFormat();

			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
			final TargetDataLine line = (TargetDataLine)AudioSystem.getLine(info);

			line.open(format);
			line.start();
			Runnable runner = new Runnable() {
				double flushInterval = flushMillis / 1000.;
				long oldTime = 0;
				int bufferSize = (int)Math.round(format.getSampleRate() * format.getFrameSize() * flushInterval);		// bytes per flushMillis
				byte buffer[] = new byte[bufferSize];

				public void run() {
					running = true;
					try {
						// long nextTime = System.currentTimeMillis();
						long nextTime = cts.getNextTime();
//						ctw.preflush(nextTime);		// pre-flush to establish initial audio blockTime?
						ctw.setTime(nextTime);		// establish start time of first audio block
						
						while (running) {
							int count = line.read(buffer, 0, buffer.length); // blocking call to read a buffer's worth of audio samples
							// if(!audioThreshold(buffer, 100)) continue;		// drop whole buffer if below threshold?
							// webscan not up to task of handling empty data in RT
							// JPW 2017-02-10 synchronize calls to the common CTwriter object using a common CTscreencap.ctwLockObj object
							synchronized(cts.ctwLockObj) {
								// long time = System.currentTimeMillis();
								long time = cts.getNextTime();
								/**
								 * 
								 * The following code adjusts the time to keep audio data
								 * output at regular/periodic intervals.  However, we've
								 * noticed 2 problems doing this:
								 * 
								 * 1. It makes sense to do this when the sound card is very
								 *    steady/regular in its acquisition (and thus this type
								 *    of adjustment should seldom occur).  We've noticed this
								 *    isn't always the case, however, so there's no need to
								 *    bother with this type of adjustment.
								 * 
								 * 2. With CTscreencap, we are coordinating CT writes between
								 *    this audio channel and the image channel.  Making time
								 *    adjustments here can screw up what block the image
								 *    is written to.  Consider the following example; for
								 *    simplicity we use relative times here; also, assume
								 *    flushMillis for the audio channel is 1000 msec.
								 *    a) WriteTask has an image to write to CT; it calls
								 *       cts.getNextTime() and gets the time 1010; it sets
								 *       this time and writes the image to CT.
								 *    b) AudiocapTask has a new audio buffer to write to
								 *       CT.  It calls cts.getNextTime(), which returns
								 *       1030.  AudiocapTask adjusts this time back to
								 *       1000.  It sets this time, puts the audio buffer
								 *       data and flushes.
								 *    The problem in this example is that the Block time
								 *    should be from 0 - 1000 (corresponding to what
								 *    AudiocapTask has specified) but WriteTask has already
								 *    written an image at time 1010; this image will show up
								 *    in this Block even though the Block time should only
								 *    be 0 - 1000.  AudiocapTask has skootched back time
								 *    and flushed the Block which now contains an image
								 *    which is now technically in the future.
								 *
								 *	if(oldTime != 0) {		// consistent timing if close
								 *		long dt = time - oldTime;
								 *		if (Math.abs(flushMillis - dt) < (flushMillis/10)) {
								 *			time = oldTime + flushMillis;
								 *			// We've adjusted the time, save this time for everyone to reference
								 *			cts.setLastCTtime(time);
								 *		}
								 *	}
								 */
								if (count > 0) {
									ctw.setTime(time);
									ctw.putData(cts.audioChannelName, addWaveHeader(buffer));
									ctw.flush(true);		// gapless
								}

								oldTime = time;
							}
						}
						System.err.println("Closing AudiocapTask");
						line.close();
					} catch (Exception e) {
						System.err.println("I/O problems: " + e);
						System.exit(-1);
					}
				}
			};

			captureThread = new Thread(runner);
			captureThread.start();
		} 
		catch (LineUnavailableException e) {
			System.err.println("Line unavailable: " + e);
			System.exit(-2);
		}
		catch(Exception e) {
			System.err.println("CT error: "+e);
			System.exit(-1);
		}
	}

	public void shutDown() {
		running = false;
	}
	
	private AudioFormat getFormat() {
		float sampleRate = frequency;
		int sampleSizeInBits = 16;
		int channels = 1;
		boolean signed = true;
		boolean bigEndian = false;
		return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
	}

	private byte[] addWaveHeader(byte[] dataBuffer) throws IOException {
		byte RECORDER_BPP = 16;
		long totalAudioLen = dataBuffer.length;
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

		byte[] waveBuffer = new byte[header.length + dataBuffer.length];
		System.arraycopy(header, 0, waveBuffer, 0, header.length);	

		System.arraycopy(dataBuffer, 0, waveBuffer, header.length, dataBuffer.length);

		return waveBuffer;
	}
	
	// check if audio buffer has sound volume above threshold
	boolean audioThreshold(byte[] bytes, int threshold) {
		// to turn bytes to shorts as either big endian or little endian. 
		short[] shorts = new short[bytes.length/2];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
		for(int i=0; i<shorts.length; i++) if(shorts[i] > threshold) return true;
		System.err.println("Audio below threshold!");
		return false;
	}
}
