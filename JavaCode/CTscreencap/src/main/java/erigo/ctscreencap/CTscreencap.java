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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import cycronix.ctlib.*;

/**
 * 
 * Periodically perform a screen capture and save it to CloudTurbine as an image.
 * 
 * Initial code based on the example found at:
 * http://www.codejava.net/java-se/graphics/how-to-capture-screenshot-programmatically-in-java
 * 
 * For drawing right on the computer desktop:
 * http://stackoverflow.com/questions/21604762/drawing-over-screen-in-java
 * https://www.reddit.com/r/learnjava/comments/40pdhv/java_draw_on_screen/
 *  
 * @author JohnW
 *
 */
public class CTscreencap {
	
	// Encoded byte array of the cursor image data from "Cursor_NoShadow.png"
	public static String encoded_cursor_noshadow = "iVBORw0KGgoAAAANSUhEUgAAABQAAAAgCAYAAAASYli2AAACa0lEQVR42q2WT2jSYRjHR+QtwX/oREVBhIYxGzJNyEOIXTp0yr8HYTRQlNz8O6dgoJ4FT2ZeJAw8CEkdhBYMFHfypLBDsZ3CUYcijErn7+l9BCPZbJrvF97L+/D7/N7neZ8/74rVan27QlngdDpfUQWKxWJwuVwvqQG73S6srq7C1tbWMyrAwWAAnU4HhEIhbG9vZ6kAUe12GwQCAbjd7jQVIOro6Ah4PB7j9XpjVICow8ND4HA4jM/ne0IFiKrX68DlcpmdnZ3HVICoWq02dn93d9dBBYiqVCrA5/NHoVDoIRUgqlQqYUoh9D4VICqfz2NFnUej0btUgKhsNgsymWy4t7e3SQWIymQyoFAofsVisVtUgKh4PA4qlepHIpFQUQEyDAOBQADW1ta+E7hsaeAESmoe1tfXvxH3RUsDUaPRCPsoaLXaL8lkkrcQ8OTkBFqt1oXVaDRAo9GAXq//TKA35gaSmgY2m81g3GYti8Xybm7g8fHxuK7JKTj/ldgHBwdweno6tWcymXBMPF8YWK1WgcVigd/vv7CvVqv7CwHL5TJ2F4bMlhzph9Dv9//YhsMhSKVSjKdrLmCxWMSZMiJJ+wgNBoPhU6FQmDplKpUCs9n8/kpgLpcDkUh0HgwGH0wMHo/nKaYEJvFEvV4PbxvLTzkTmE6nQSKRDMPh8L2/DeRGr2N3aTabU6e02Wxgt9tfzwTK5fJBJBK5c5mRfPjG4XBMxZEML1AqlT8vpaFhf3//9qy/YUdBF8/OzsZze2NjA9fXmd2bFPbNq9KAXMIHnU43noKkdl+QUFxb6mmBaWI0Gj/+y5OJfgOMmgOC3DbusQAAAABJRU5ErkJggg==";
	
	public final static double DEFAULT_FPS = 5.0;         // default frames/sec
	public final static double AUTO_FLUSH_DEFAULT = 1.0;  // default auto-flush in seconds
	
	public double capturePeriodMillis;         // period between screen captures (msec)
	public String outputFolder;                // location of output files
	public String sourceName = "CTscreencap";  // output source name
	public String channelName = "image.jpg";   // output channel name
	public boolean bZipMode = true;            // output ZIP files?
	public long autoFlushMillis;               // flush interval (msec)
	boolean bDebugMode = false;                // run CT in debug mode?
	
	public static void main(String[] argsI) {
		new CTscreencap(argsI);
	}
	
	public CTscreencap(String[] argsI) {
		
		//
		// Parse command line arguments
		//
		// We use the Apche Commons CLI library to handle command line
		// arguments. See https://commons.apache.org/proper/commons-cli/usage.html
		// for examples, although note that we use the more up-to-date form
		// (Option.builder) to create Option objects.
		//
		
		//
		// 1. Setup command line options
		//
		Options options = new Options();
		// Example of a Boolean option (i.e., only the flag, no argument goes with it)
		options.addOption("h", "help", false, "Print this message");
		options.addOption("z", "zipfiles", false, "ZIP output files?");
		options.addOption("x", "debug", false, "debug mode");
		// The following example is for: -outputfolder <folder>   (location of output files)
		Option outputFolderOption = Option.builder("outputfolder")
                .argName("folder")
                .hasArg()
                .desc("Location of output files; the source will be created under this folder")
                .build();
		options.addOption(outputFolderOption);
		Option filesPerSecOption = Option.builder("fps")
                .argName("framespersec")
                .hasArg()
                .desc("Desired frame rate, frames/sec; default = " + DEFAULT_FPS)
                .build();
		options.addOption(filesPerSecOption);
		Option autoFlushOption = Option.builder("f")
				.argName("autoFlush")
				.hasArg()
				.desc("flush interval (sec) (amount of data per zipfile)")
				.build();
		options.addOption(autoFlushOption);
		Option sourceNameOption = Option.builder("s")
				.argName("source name")
				.hasArg()
				.desc("name of output source")
				.build();
		options.addOption(sourceNameOption);
		Option chanNameOption = Option.builder("c")
				.argName("channel name(s)")
				.hasArg()
				.desc("name of output channel")
				.build();
		options.addOption(chanNameOption);
		
		//
		// 2. Parse command line options
		//
	    CommandLineParser parser = new DefaultParser();
	    CommandLine line = null;
	    try {
	        line = parser.parse( options, argsI );
	    }
	    catch( ParseException exp ) {
	        // oops, something went wrong
	        System.err.println( "Command line argument parsing failed: " + exp.getMessage() );
	        return;
	    }
	    
	    //
	    // 3. Retrieve the command line values
	    //
	    
	    if (line.hasOption("help")) {
	    	// Display help message and quit
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.printHelp( "CTscreencap", options );
	    	return;
	    }
	    // Source name
	    sourceName = line.getOptionValue("s",sourceName);
	    // Channel name
	    channelName = line.getOptionValue("c",channelName);
	    // Auto-flush time
		double autoFlush = Double.parseDouble(line.getOptionValue("f",""+AUTO_FLUSH_DEFAULT));
		autoFlushMillis = (long)(autoFlush*1000.);
	    // ZIP output files?
	    bZipMode = line.hasOption("zipfiles");
	    // Where to write the files to
	    outputFolder = line.getOptionValue("outputfolder","");
	    // Make sure outputFolder ends in a file separator
	    if (outputFolder.endsWith,) {
	    	
	    }
	    // How many frames (ie, screen dumps) to capture per second
	    double framesPerSec = 0.0;
	    try {
	    	framesPerSec = Double.parseDouble(line.getOptionValue("fps",DEFAULT_FPS));
	    	if (framesPerSec <= 0.0) {
	    		throw new NumberFormatException("value must be greater than 0.0");
	    	}
	    } catch (NumberFormatException nfe) {
	    	System.err.println("\nError parsing \"fps\" (it should be a floating point value):\n" + nfe);
	    	return;
	    }
	    capturePeriodMillis = 1000.0 / framesPerSec;
	    // Run CT in debug mode?
	    bDebugMode = line.hasOption("debug");
		
		//
		// Utility code
		// Get byte array for the mouse cursors
		//
		try {
			String noshadowCursorStr = getEncodedImageString("C:\\TEMP\\Cursor_NoShadow.png","png");
			String wshadowCursorStr = getEncodedImageString("C:\\TEMP\\Cursor_WShadow.png","png");
			System.err.println("Cursor no shadow:\n" + noshadowCursorStr + "\n");
			System.err.println("Cursor with shadow:\n" + wshadowCursorStr + "\n");
		} catch (IOException ioe) {
			System.err.println("Caught exception generating encoded string for cursor data:\n" + ioe);
			return;
		}
		
		// Setup CTwriter
		CTwriter ctw;
		try {
			ctw = new CTwriter(outputFolder + sourceName);
			ctw.setZipMode(bZipMode);
			CTinfo.setDebug(bDebugMode);
			ctw.autoSegment(0);
			// "Block" mode is for packed data (multiple points per output file);
			// we don't want this; in our case, each output file will be one "data point"
			ctw.setBlockMode(false);
			ctw.setTimeRelative(true);
		} catch (IOException ioe) {
			System.err.println("Error trying to create CloudTurbine writer object:\n" + ioe);
			return;
		}
		
		//
		// Decode the String corresponding to binary cursor data
		//
		BufferedImage cursor_img = null;
		Base64.Decoder byte_decoder = Base64.getDecoder();
		byte[] decoded_cursor_bytes = byte_decoder.decode(encoded_cursor_noshadow);
		try {
			cursor_img = ImageIO.read(new ByteArrayInputStream(decoded_cursor_bytes));
		} catch (IOException ioe) {
			System.err.println("Error creating BufferedImage from cursor data:\n" + ioe);
		}
		
		System.err.print("\n");
		try {
			for (int i=0; i<25; ++i) {
				// Get screen image
				Robot robot = new Robot();
				Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
				BufferedImage screenCap = robot.createScreenCapture(screenRect);
				// Add mouse cursor to the image
				int mouse_x = MouseInfo.getPointerInfo().getLocation().x;
				int mouse_y = MouseInfo.getPointerInfo().getLocation().y;
				Graphics2D graphics2D = screenCap.createGraphics();
				graphics2D.drawImage(cursor_img, mouse_x, mouse_y, null);
				// Write the image to CloudTurbine file
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageIO.write(screenCap, "jpg", baos);
				byte[] jpeg = baos.toByteArray();
				baos.close();
				System.out.print(".");
				if (((i+1)%20)==0) {
					System.err.print("\n");
				}
				ctw.putData(channelName,jpeg);
				if (((i+1)%5)==0) {
					ctw.flush();
				}
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			System.err.println("Error processing screen capture:\n" + e);
		}
		
	}
	
	//
	// Read the specified image file and return a String corresponding to
	// the encoded bytes of this image.
	//
	public static String getEncodedImageString(String fileNameI, String formatNameI) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
		BufferedImage img = ImageIO.read(new File(fileNameI));
		boolean bFoundWriter = ImageIO.write(img, formatNameI, baos);
		if (!bFoundWriter) {
			throw new IOException( new String("No image writer found for format \"" + formatNameI + "\"") );
		}
		baos.flush();
		byte[] cursor_bytes = baos.toByteArray();
		Base64.Encoder byte_encoder = Base64.getEncoder();
		return byte_encoder.encodeToString(cursor_bytes);
	}
}
