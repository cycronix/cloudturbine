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

package erigo.ctscreencap;

import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.TimerTask;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

import cycronix.ctlib.CTwriter;

//
// This class handles the periodic screen capture.
//
// Initial code based on the example found at:
// http://www.codejava.net/java-se/graphics/how-to-capture-screenshot-programmatically-in-java
//

public class ScreencapTask extends TimerTask {
	
	public CTscreencap cts = null;
	public CTwriter ctw = null;
	public int numScreenCaps = 0;   // number of screen captures that have been performed
	public JPEGImageWriteParam jpegParams = null;
	
	// Constructor
	public ScreencapTask(CTscreencap ctsI) {
		cts = ctsI;
		ctw = cts.ctw;
		
		// Setup compression
		jpegParams = new JPEGImageWriteParam(null);
		jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		jpegParams.setCompressionQuality(cts.imageCompression);
	}
	
	// Perform the work of the screen capture
	public void run() {
		
		// Do a check on the amount of time it takes to perform this capture
		long startTime = System.currentTimeMillis();
		
		if ( (cts.bShutdown) || (ctw == null) ) {
			return;
		}
		
		numScreenCaps += 1;
		
		if (numScreenCaps == 1) {
			System.err.print("\n");
		}
		
		try {
			// Get screen image
			Robot robot = new Robot();
			Rectangle captureRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
			BufferedImage screenCap = robot.createScreenCapture(captureRect);
			// Add mouse cursor to the image
			int mouse_x = MouseInfo.getPointerInfo().getLocation().x;
			int mouse_y = MouseInfo.getPointerInfo().getLocation().y;
			Graphics2D graphics2D = screenCap.createGraphics();
			graphics2D.drawImage(cts.cursor_img, mouse_x, mouse_y, null);
			// Write the image to CloudTurbine file
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			//
			// Method 1: To write out an image using the default compression
			//
			// ImageIO.write(screenCap, "jpg", baos);
			//
			// Method 2: To change the default image compression
			//   See the following websites for where I got this code:
			//   http://stackoverflow.com/questions/13204432/java-how-to-set-jpg-quality
			//   http://stackoverflow.com/questions/17108234/setting-jpg-compression-level-with-imageio-in-java
			//
			ImageOutputStream  ios =  ImageIO.createImageOutputStream(baos);
		    Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("jpg");
		    ImageWriter writer = iter.next();
		    writer.setOutput(ios);
		    writer.write(null, new IIOImage(screenCap,null,null),jpegParams);
		    writer.dispose();
			baos.flush();
			byte[] jpegByteArray = baos.toByteArray();
			baos.close();
			System.out.print(".");
			if ((numScreenCaps%20)==0) {
				System.err.print("\n");
			}
			ctw.putData(cts.channelName,jpegByteArray);
		} catch (Exception e) {
			if ( !cts.bShutdown && (ctw != null) ) {
				// Only print out error messages if we know we aren't in shutdown mode
			    System.err.println("\nError processing screen capture:\n" + e);
			    e.printStackTrace();
			}
			return;
		}
		
		long capTime = System.currentTimeMillis() - startTime;
		if (capTime > cts.capturePeriodMillis) {
			System.err.println("\nWARNING: screen capture takes " + capTime + " msec, which is longer than the desired period of " + cts.capturePeriodMillis + " msec");
		}
		
	}
	
}
