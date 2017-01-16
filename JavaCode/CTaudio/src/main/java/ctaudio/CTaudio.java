
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

package ctaudio;

import javax.swing.*;
import cycronix.ctlib.CTinfo;
import cycronix.ctlib.CTwriter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.sound.sampled.*;

/**
 * CloudTurbine Audio:
 * Record system audio to CT
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 01/16/2017
 * 
*/

public class CTaudio extends JFrame {

	protected boolean running;
	ByteArrayOutputStream out;
	int frequency = 8000; 				//8000, 22050, 44100

	public CTaudio() {
		super("Capture Sound Demo");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		Container content = getContentPane();

		final JButton capture = new JButton("Capture");
		final JButton stop = new JButton("Stop");

		capture.setEnabled(true);
		stop.setEnabled(false);

		ActionListener captureListener = 
				new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				capture.setEnabled(false);
				stop.setEnabled(true);
				//       play.setEnabled(false);
				captureAudio();
			}
		};
		capture.addActionListener(captureListener);
		content.add(capture, BorderLayout.NORTH);

		ActionListener stopListener = 
				new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				capture.setEnabled(true);
				stop.setEnabled(false);
				//       play.setEnabled(true);
				running = false;
			}
		};
		stop.addActionListener(stopListener);
		content.add(stop, BorderLayout.CENTER);

	};

	private void captureAudio() {
		try {
			CTwriter ctw = new CTwriter("CTdata/CTaudio");
			CTinfo.setDebug(false);
			ctw.setBlockMode(true,true);		// pack, zip
			ctw.autoFlush(0);					// no autoflush
			//	 ctw.autoSegment(0);				// no segments

			final AudioFormat format = getFormat();

			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
			final TargetDataLine line = (TargetDataLine)AudioSystem.getLine(info);

			line.open(format);
			line.start();
			Runnable runner = new Runnable() {
				int bufferSize = (int)format.getSampleRate() * format.getFrameSize();		// bytes in 1 sec
				byte buffer[] = new byte[bufferSize];
//				long fcount = 0;
				
				public void run() {
					out = new ByteArrayOutputStream();
					running = true;
					try {
						while (running) {
							int count = line.read(buffer, 0, buffer.length);
							if (count > 0) {
								out.write(buffer, 0, count);
								//	ctw.setTime(startTime + fcount*1000);		// force exactly 1sec intervals?  (gapless but drift?)
								//	fcount++;
								ctw.putData("audio.wav",addWaveHeader(buffer));
								ctw.flush();
							}
						}
						out.close();
					} catch (Exception e) {
						System.err.println("I/O problems: " + e);
						System.exit(-1);
					}
				}
			};
			Thread captureThread = new Thread(runner);
			captureThread.start();
		} 
		catch (LineUnavailableException e) {
			System.err.println("Line unavailable: " + e);
			System.exit(-2);
		}
		catch(Exception e) {
			System.err.println("CT error: "+e);
			System.exit(ABORT);
		}
	}

	private AudioFormat getFormat() {
		float sampleRate = frequency;
		int sampleSizeInBits = 16;
		int channels = 1;
		boolean signed = true;
		boolean bigEndian = false;
		return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
	}

	public static void main(String args[]) {
		JFrame frame = new CTaudio();
		frame.pack();
		frame.show();
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

}
