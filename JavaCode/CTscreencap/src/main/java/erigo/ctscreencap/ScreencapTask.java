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

//
// Generate a screen capture.
//
// The run() method in this class generates a screen capture and puts it
// in the blocking queue managed by CTscreencap.
//
// Initial code based on the example found at:
// http://www.codejava.net/java-se/graphics/how-to-capture-screenshot-programmatically-in-java
//

public class ScreencapTask extends TimerTask implements Runnable {
	
	public CTscreencap cts = null;
	public JPEGImageWriteParam jpegParams = null; // to specify image quality
	public Rectangle captureRect = null;          // the rectangular region to capture
	
	// Constructor
	public ScreencapTask(CTscreencap ctsI) {
		cts = ctsI;
		captureRect = cts.regionToCapture;
		// Setup image quality
		jpegParams = new JPEGImageWriteParam(null);
		jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		jpegParams.setCompressionQuality(cts.imageQuality);
	}
	
	// Perform the work of the screen capture
	public void run() {
		
		// Do a check on the amount of time it takes to perform this capture
		long startTime = System.currentTimeMillis();
		
		// if ( (cts.bShutdown) || (ctw == null) ) {
		if (cts.bShutdown) {
			return;
		}
		
		try {
			// Need to add the mouse cursor to the image
			int mouse_x = MouseInfo.getPointerInfo().getLocation().x;
			int mouse_y = MouseInfo.getPointerInfo().getLocation().y;
			// Get screen image
			Robot robot = new Robot();
			BufferedImage screenCap = robot.createScreenCapture(captureRect);
			if (cts.bIncludeMouseCursor) {
				int cursor_x = mouse_x - captureRect.x;
				int cursor_y = mouse_y - captureRect.y;
				Graphics2D graphics2D = screenCap.createGraphics();
				graphics2D.drawImage(cts.cursor_img, cursor_x, cursor_y, null);
			}
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
			// ctw.putData(cts.channelName,jpegByteArray);
			// Add baos to the asynchronous event queue of to-be-processed objects
			cts.queue.put(jpegByteArray);
		} catch (Exception e) {
			// if ( !cts.bShutdown && (ctw != null) ) {
			if (!cts.bShutdown) {
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
