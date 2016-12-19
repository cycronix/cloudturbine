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
import java.util.TimerTask;

import javax.imageio.ImageIO;

import cycronix.ctlib.CTwriter;

public class ScreencapTask extends TimerTask {
	
	public CTscreencap cts = null;
	public CTwriter ctw = null;
	public int numScreenCaps = 0;   // number of screen captures that have been performed
	
	// Constructor
	public ScreencapTask(CTscreencap ctsI) {
		cts = ctsI;
		ctw = cts.ctw;
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
			Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
			BufferedImage screenCap = robot.createScreenCapture(screenRect);
			// Add mouse cursor to the image
			int mouse_x = MouseInfo.getPointerInfo().getLocation().x;
			int mouse_y = MouseInfo.getPointerInfo().getLocation().y;
			Graphics2D graphics2D = screenCap.createGraphics();
			graphics2D.drawImage(cts.cursor_img, mouse_x, mouse_y, null);
			// Write the image to CloudTurbine file
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(screenCap, "jpg", baos);
			byte[] jpeg = baos.toByteArray();
			baos.close();
			System.out.print(".");
			if ((numScreenCaps%20)==0) {
				System.err.print("\n");
			}
			if ( !cts.bShutdown && (ctw != null) ) {
				ctw.putData(cts.channelName,jpeg);
			}
		} catch (Exception e) {
			System.err.println("\nError processing screen capture:\n" + e);
			e.printStackTrace();
			return;
		}
		
		long capTime = System.currentTimeMillis() - startTime;
		if (capTime > cts.capturePeriodMillis) {
			System.err.println("\nWARNING: screen capture takes " + capTime + " msec, which is longer than the desired period of " + cts.capturePeriodMillis + " msec");
		}
		
	}
	
}
