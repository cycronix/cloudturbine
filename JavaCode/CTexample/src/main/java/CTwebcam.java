/**
 * CloudTurbine webcam example
 * Sends images via HTTP PUT to CTweb
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 02/13/2018
 * 
*/

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


import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import cycronix.ctlib.CThttp;

public class CTwebcam {

	static private String CTwebhost = "http://localhost:8000";
//	static private String CTwebhost = "http://cloudturbine.net:8000";

	private static final class Capture extends Thread {
		@Override
		public void run() {

			Webcam webcam = Webcam.getDefault();
			webcam.setViewSize(WebcamResolution.VGA.getSize());
			webcam.open();

			JFrame frame=new JFrame();
			frame.setTitle("webcam capture");
			JLabel lbl = new JLabel();
			frame.add(lbl);
			frame.setVisible(true);
			
			// HTTP put setup
			CThttp cth = null;
			try {
				cth = new CThttp("MyCam", CTwebhost);
				cth.login("matt", "pass");
				cth.autoSegment(1000);
				cth.setZipMode(true);
				cth.autoFlush( 0.2);
				cth.setDebug(true);
				System.err.println("Writing to host: "+CTwebhost);
			} catch (Exception e1) {
				e1.printStackTrace();
				return;
			}
			
			while (true) {
				if (!webcam.isOpen()) break; 

				BufferedImage image = webcam.getImage();
				if (image == null) break;

				frame.setSize(image.getWidth(),image.getHeight());
				lbl.setIcon(new ImageIcon(image));
				
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				try {
					ImageIO.write(image, "jpeg", stream);
					cth.putData("webcam.jpg", stream.toByteArray());
				} catch(Exception e) {
					System.err.println("CTwebcam Exception: "+e);
				}
			}
		}
	}

	public static void main(String[] args) throws Throwable {
		if(args.length > 0) CTwebhost = args[0];
		new Capture().start();
		Thread.sleep(5 * 60 * 1000); 	// 5 minutes until auto-exit
		System.exit(1);
	}
	
}
