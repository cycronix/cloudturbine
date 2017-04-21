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

package erigo.ctscreencap;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.github.sarxos.webcam.Webcam;					// webcam option (MJM)
import com.github.sarxos.webcam.WebcamResolution;

import cycronix.ctlib.CTwriter;
import cycronix.ctlib.CTftp;
import cycronix.ctlib.CTinfo;
import cycronix.ctlib.CTreader;

/**
 * 
 * Periodically perform a screen capture and save it to CloudTurbine as an image.
 * Optionally, capture audio along with the screen captures and save it to
 * CloudTurbine as a series of ".wav" files.
 * 
 * Classes involved in this application:
 * -------------------------------------
 * 1. CTscreencap: main class; manages the GUI and program flow
 * 2. CTsettings: creates and manages a dialog to allow the user to edit settings
 * 3. DefineCaptureRegion: NO LONGER USED; we previously used this class to allow the
 *       user to select the region of the screen they wish to capture; this is now
 *       done dynamically by the user moving and resizing the main application frame
 * 4. ScreencapTimerTask: the run() method in this class is called by the periodic
 *       Timer to take a screen capture; what this class does is create an instance
 *       of ScreencapTask and run it in a separate Thread
 * 5. ScreencapTask: generates a single screen capture and puts it on the queue
 * 6. TimeValue: these are the object put on the queue; each object contains
 *       a screen capture image and the time at which the screen capture was taken
 * 7. AudiocapTask: capture and save audio to CloudTurbine as ".wav" files
 * 8. WriteTask: grab images off the queue and write them to CT; this is executed
 *       in a separate Thread
 * 9. Utility: contains utility methods
 * 
 * How the program works:
 * ----------------------
 * When the user clicks Start on the GUI, startCapture() gets the screen captures going
 * by doing the following:
 * 1. create an instance of CTwriter
 * 2. setup a new queue
 * 3. create a new Timer object and a ScreencapTimerTask object (the ScreencapTimerTask
 *       object is the TimerTask which the Timer periodically calls)
 * 4. create a WriteTask object and execute it in a separate Thread
 * Here's what happens: the Timer periodically calls ScreencapTimerTask.run(); this run()
 *       method determines the region of the screen to capture, creates an instance of
 *       ScreencapTask and executes it in a separate Thread (executing ScreencapTask in
 *       its own Thread prevents ScreencapTimerTask.run() from bogging down);
 *       ScreencapTask.run() takes the screen capture and puts it on the queue; the Thread
 *       running in WriteTask.run() takes screen captures off the queue and writes them to
 *       CloudTurbine
 *
 * CTscreencap constructor does the following:
 * -------------------------------------------
 * 1. Setup a shutdown hook to capture Ctrl+c to cleanly shut down the program
 * 2. Parse command line arguments
 * 3. NO LONGER USED: Have user specify the region of the screen they wish to capture
 * 4. If the user has requested to capture the whole screen (or if the GraphicsDevice
 *    does not support translucency) then go ahead and start screen capture; otherwise,
 *    pop up the GUI.
 * 
 * If the user isn't capturing the entire screen, then (depending on the
 * support offered by the GraphicsDevice) the JFrame that pops up will
 * either be a Shaped window containing a "cut out" section (the user will
 * be able to reach through this cut out area and manipulate windows behind
 * the JFrame) or else contain a translucent panel (i.e., opacity less than
 * 1.0).  The "cut out" section or the translucent section define the image
 * capture region.  Note that the Java 1.8 Javadoc for java.awt.Frame.setOpacity()
 * specifies:
 * 
 *  *  The following conditions must be met in order to set the opacity value less than 1.0f:
 *  *
 *  *      The TRANSLUCENT translucency must be supported by the underlying system
 *  *      The window must be undecorated (see setUndecorated(boolean) and Dialog.setUndecorated(boolean))
 *  *      The window must not be in full-screen mode (see GraphicsDevice.setFullScreenWindow(Window)) 
 *  *
 *  *  If the requested opacity value is less than 1.0f, and any of the above conditions are not met, the window opacity will not change, and the IllegalComponentStateException will be thrown. 
 * 
 * We see this behavior immediately upon CTscreencap startup under Mac OS if the JFrame is "decorated"
 * (an IllegalComponentStateException exception is thrown right away).  Surprisingly, for some unknown
 * reason, the transparency works with a *decorated* JFrame under Windows OS - however, we don't want
 * to count on that.  To be Java compliant, we won't use a decorated window.  This creates the challenge
 * of how do we support moving and resizing the window (without decoration on the window, there is no
 * inherent way to move and resize the window)?  We do this by creating our own simple window manager:
 * CTscreencap implements MouseMotionListener by defining mouseDragged() and mouseMoved().
 * 
 * "Continue" mode
 * ---------------
 * To produce a video with several different "takes", CTscreencap supports "Continue" mode.  After
 * CTscreencap has started, when the user is finished with the scene they can "Stop", make any
 * necessary adjustments and then "Continue".  CTscreencap sets the next timestamp equal to
 * (the last timestamp + 1) after starting up in continue mode.
 * 
 * All timestamps (for both WriteTask and AudiocapTask) are created in getNextTime().  If the user
 * is in standard (not continue) mode, getNextTime() returns System.currentTimeMillis();
 * in continue mode, this function will calculate the appropriate timestamp to use to
 * maintain seamless video/audio capture.
 * 
 * @author John P. Wilson, Matt J. Miller
 * @version 04/12/2017
 *
 */
public class CTscreencap implements ActionListener,ChangeListener,MouseMotionListener {
	
	// Encoded byte array of the cursor image data from "Cursor_NoShadow.png"
	public static String encoded_cursor_noshadow = "iVBORw0KGgoAAAANSUhEUgAAABQAAAAgCAYAAAASYli2AAACa0lEQVR42q2WT2jSYRjHR+QtwX/oREVBhIYxGzJNyEOIXTp0yr8HYTRQlNz8O6dgoJ4FT2ZeJAw8CEkdhBYMFHfypLBDsZ3CUYcijErn7+l9BCPZbJrvF97L+/D7/N7neZ8/74rVan27QlngdDpfUQWKxWJwuVwvqQG73S6srq7C1tbWMyrAwWAAnU4HhEIhbG9vZ6kAUe12GwQCAbjd7jQVIOro6Ah4PB7j9XpjVICow8ND4HA4jM/ne0IFiKrX68DlcpmdnZ3HVICoWq02dn93d9dBBYiqVCrA5/NHoVDoIRUgqlQqYUoh9D4VICqfz2NFnUej0btUgKhsNgsymWy4t7e3SQWIymQyoFAofsVisVtUgKh4PA4qlepHIpFQUQEyDAOBQADW1ta+E7hsaeAESmoe1tfXvxH3RUsDUaPRCPsoaLXaL8lkkrcQ8OTkBFqt1oXVaDRAo9GAXq//TKA35gaSmgY2m81g3GYti8Xybm7g8fHxuK7JKTj/ldgHBwdweno6tWcymXBMPF8YWK1WgcVigd/vv7CvVqv7CwHL5TJ2F4bMlhzph9Dv9//YhsMhSKVSjKdrLmCxWMSZMiJJ+wgNBoPhU6FQmDplKpUCs9n8/kpgLpcDkUh0HgwGH0wMHo/nKaYEJvFEvV4PbxvLTzkTmE6nQSKRDMPh8L2/DeRGr2N3aTabU6e02Wxgt9tfzwTK5fJBJBK5c5mRfPjG4XBMxZEML1AqlT8vpaFhf3//9qy/YUdBF8/OzsZze2NjA9fXmd2bFPbNq9KAXMIHnU43noKkdl+QUFxb6mmBaWI0Gj/+y5OJfgOMmgOC3DbusQAAAABJRU5ErkJggg==";
	
	public final static double DEFAULT_FPS = 5.0;         // default frames/sec
	public final static Double[] FPS_VALUES = {0.1,0.2,0.5,1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0};
	public final static double AUTO_FLUSH_DEFAULT = 1.0;  // default auto-flush in seconds
	
	//
	// Settings
	//
	// Setting made in the main GUI panel
	public double framesPerSec;					// how many frames to capture per second
	public long capturePeriodMillis;			// capture period in milliseconds; calculated from framesPerSec
	public float imageQuality = 0.70f;			// Image quality; 0.00 - 1.00; higher numbers correlate to better quality/less compression
	public boolean bChangeDetect = false;		// detect and record only images that change (more CPU, less storage)
	public boolean bFullScreen = false;			// capture the full screen?
	public boolean bAudioCapture = false;		// record synchronous audio?
	public Rectangle regionToCapture = null;	// The region to capture
	// Settings made in the Settings dialog, CTsettings
	public CTsettings ctSettings = null;		// GUI to view/edit settings
	public String outputFolder = "CTdata";		// location of output files
	public String sourceName = "CTscreencap";	// output source name
	public String channelName = "image.jpg";	// output image channel name; must end in ".jpg" or ".jpeg"
	public String audioChannelName="audio.wav";	// output audio channel name; must end in ".wav"
	public boolean bFTP = false;				// Are we in FTP mode?
	public String ftpHost = "";					// FTP hostname
	public String ftpUser = "";					// FTP username
	public String ftpPassword = "";				// FTP password
	public boolean bZipMode = true;				// output ZIP files?
	public long autoFlushMillis;				// flush interval (msec)
	public long numBlocksPerSegment = 0;		// number of blocks per segment; defaults to 0 (no segments)
	public boolean bDebugMode = false;			// run CT in debug mode?
	public boolean bIncludeMouseCursor = true;	// include the mouse cursor in the screencap image?
	public boolean bStayOnTop = false;			// keep the CTscreencap UI on top of all other windows on the desktop
	// Other settings and flags
	public BufferedImage cursor_img = null;		// cursor to add to the screen captures
	public boolean bChangeDetected = false;		// flag to force image capture on event (MJM)
	public boolean bJustDisplayUsage = false;	// Are we only displaying usage information and then quitting?
	public boolean bPreview = false;			// display live preview image in its own window? (MJM)

	public boolean bWebCam = false;				// webcam (vs screencap) option (MJM)
	public Webcam webcam = null;
	
	// To control CT shutdown
	public boolean bShutdown = false;
	public boolean bCallExitFromShutdownHook = true;
	
	// Queue of screen capture objects waiting to be written to CT
	// public BlockingQueue<byte[]> queue = null;
	public BlockingQueue<TimeValue> queue = null;
	
	// Objects which do "work"
	public CTwriter ctw = null;					// CloudTurbine writer object
	public Timer screencapTimer = null;			// Periodic Timer object
	public ScreencapTimerTask screencapTimerTask = null;	// TimerTask executed each time the periodic Timer expires
	private AudiocapTask audioTask = null;		// audio-capture task (optional)
	private WriteTask writeTask = null;			// This task takes screen captures off the queue and writes them to CT
	private Thread writeTaskThread = null;		// The thread running in WriteTask
	
	public Object ctwLockObj = new Object();	// Lock object used by the synchronized blocks in AudiocapTask and WriteTask
	
	// Variables dealing with "continue" mode, where screen captures resume, picking up in time just where we left off;
	// kind of like a seamless "pause"
	public boolean bContinueMode = false;		// Are we in continue mode?
	public long firstCTtime = 0;				// First timestamp sent to CTwriter.setTime(<time>)
	public long lastCTtime = 0;					// Last timestamp sent to CTwriter.setTime(<time>)
	public long continueWallclockInitTime = 0;	// Wallclock time when continue starts up.
	
	// Main panel GUI objects
	public JFrame guiFrame = null;				// JFrame which contains translucent panel which defines the capture region
	public JPanel guiPanel = null;				// top-level panel that will contain all UI components
	public int guiFrameOrigHeight = -1;			// used when clicking the Checkbox to go between capturing the region defined by capturePanel and full screen
	public JPanel controlsPanel = null;			// child of guiPanel; contains the controls at the top of the GUI
	public JPanel capturePanel = null;			// translucent panel which defines the region to capture
	private JCheckBox changeDetectCheck = null;	// checkbox to turn on/off "change detect"
	private JCheckBox fullScreenCheck = null;	// checkbox to turn on/off doing full screen capture
	private JCheckBox audioCheck = null;		// checkbox to turn on/off audio capture
	public JButton startStopButton = null;		// One button to Start and then Stop screen captures
	public JButton continueButton = null;		// Clicking this is just like clicking "Start" except we pick up in time
												// just where we left off; kind of like a seamless "pause"
	
	// Since translucent panels can only be contained within undecorated Frames (see comments in header above)
	// and since undecorated Frames don't support moving/resizing, we implement our own basic "window manager"
	// by catching mouse move and drag events (see mouseDragged() and mouseMoved() methods below).  The following
	// members are used in this implementation.
	private final static int NO_COMMAND			= 0;
	private final static int MOVE_FRAME			= 1;
	private final static int RESIZE_FRAME_NW	= 2;
	private final static int RESIZE_FRAME_N		= 3;
	private final static int RESIZE_FRAME_NE	= 4;
	private final static int RESIZE_FRAME_E		= 5;
	private final static int RESIZE_FRAME_SE	= 6;
	private final static int RESIZE_FRAME_S		= 7;
	private final static int RESIZE_FRAME_SW	= 8;
	private final static int RESIZE_FRAME_W		= 9;
	private int mouseCommandMode = NO_COMMAND;
	private Point mouseStartingPoint = null;
	private Rectangle frameStartingBounds = null;
	
	/**
	 *
	 * Main function; creates and instance of CTscreencap
	 * 
	 * @param argsI  Command line arguments
	 */
	public static void main(String[] argsI) {
		new CTscreencap(argsI);
	}
	
	/**
	 * 
	 * CTscreencap constructor
	 * 
	 * @param argsI  Command line arguments
	 */
	public CTscreencap(String[] argsI) {
		
		//
		// Specify a shutdown hook to catch Ctrl+c
		//
		final CTscreencap temporaryCTS = this;
        Runtime.getRuntime().addShutdownHook(new Thread() {
        	@Override
            public void run() {
        		if (!bJustDisplayUsage && bCallExitFromShutdownHook) {
        			temporaryCTS.exit(true);
        		}
            }
        });
        
        // Create a String version of FPS_VALUES
        String FPS_VALUES_STR = new String(FPS_VALUES[0].toString());
        for (int i=1; i<FPS_VALUES.length; ++i) {
        	FPS_VALUES_STR = FPS_VALUES_STR + "," + FPS_VALUES[i].toString();
        }
        
        // Create a String version of CTsettings.flushIntervalLongs
        String FLUSH_VALUES_STR = String.format("%.1f",CTsettings.flushIntervalLongs[0]/1000.0);
        for (int i=1; i<CTsettings.flushIntervalLongs.length; ++i) {
        	// Except for the first flush value (handled above, first item in the string) the rest of these
        	// are whole numbers, so we will print them out with no decimal places
        	FLUSH_VALUES_STR = FLUSH_VALUES_STR + "," + String.format("%.0f",CTsettings.flushIntervalLongs[i]/1000.0);
        }
		
		//
		// Parse command line arguments
		//
		// 1. Setup command line options
		//
		Options options = new Options();
		// Boolean options (only the flag, no argument)
		options.addOption("h", "help", false, "Print this message.");
		options.addOption("nm", "no_mouse_cursor", false, "Don't include mouse cursor in output screen capture images.");
		options.addOption("nz", "no_zip", false, "Turn off ZIP output.");
		options.addOption("x", "debug", false, "Use debug mode.");
		options.addOption("cd", "change_detect", false, "Detect and record only changed images (default="+bChangeDetect+").");
		options.addOption("a", "audio_cap", false, "Record audio (default="+bAudioCapture+").");
		options.addOption("fs", "full_screen", false, "Automatically start capturing the full screen (default="+bFullScreen+").");
		options.addOption("t", "UI_on_top", false, "CTscreencap UI will stay on top of all other windows (default=" + bStayOnTop + ").");
		options.addOption("p", "preview", false, "Display live preview image");
		options.addOption("w", "webcam", false, "Capture WebCam (vs screencap)");

		// Command line options that include a flag
		// For example, the following will be for "-outputfolder <folder>   (Location of output files...)"
		Option outputFolderOption = Option.builder("o")
                .longOpt("outputfolder")
				.argName("folder")
                .hasArg()
                .desc("Location of output files (source is created under this folder); default = \"" + outputFolder + "\".")
                .build();
		options.addOption(outputFolderOption);
		Option filesPerSecOption = Option.builder("fps")
                .argName("framespersec")
                .hasArg()
                .desc("Desired frame rate (frames/sec); default = " + DEFAULT_FPS + "; accepted values = " + FPS_VALUES_STR + ".")
                .build();
		options.addOption(filesPerSecOption);
		Option autoFlushOption = Option.builder("f")
				.argName("autoFlush")
				.hasArg()
				.desc("Flush interval (sec); amount of data per block; default = " + Double.toString(AUTO_FLUSH_DEFAULT) + "; accepted values = " + FLUSH_VALUES_STR + ".")
				.build();
		options.addOption(autoFlushOption);
		Option sourceNameOption = Option.builder("s")
				.argName("source name")
				.hasArg()
				.desc("Name of output source; default = \"" + sourceName + "\".")
				.build();
		options.addOption(sourceNameOption);
		Option chanNameOption = Option.builder("c")
				.argName("channelname")
				.hasArg()
				.desc("Name of output image channel; must end in \".jpg\" or \".jpeg\"; default = \"" + channelName + "\".")
				.build();
		options.addOption(chanNameOption);
		Option imgQualityOption = Option.builder("q")
				.argName("imagequality")
				.hasArg()
				.desc("Image quality, 0.00 - 1.00 (higher numbers are better quality/less compression); default = " + Float.toString(imageQuality) + ".")
				.build();
		options.addOption(imgQualityOption);
		
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
	    	bJustDisplayUsage = true;
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.setWidth(160);
	    	formatter.printHelp( "CTscreencap", options );
	    	return;
	    }
	    
	    if(line.hasOption("preview")) bPreview = true;			// MJM
	    if(line.hasOption("webcam")) bWebCam = true;			// MJM

	    // Source name
	    sourceName = line.getOptionValue("s",sourceName);
	    // Channel name
	    channelName = line.getOptionValue("c",channelName);
	    // Auto-flush time
	    try {
	    	double autoFlush = Double.parseDouble(line.getOptionValue("f",""+AUTO_FLUSH_DEFAULT));
	    	autoFlushMillis = (long)(autoFlush*1000.);
	    	boolean bGotMatch = false;
	    	for (int i=0; i<CTsettings.flushIntervalLongs.length; ++i) {
	    		if (autoFlushMillis == CTsettings.flushIntervalLongs[i]) {
	    			bGotMatch = true;
	    			break;
	    		}
	    	}
	    	if (!bGotMatch) {
	    		throw new NumberFormatException("bad input value");
	    	}
	    } catch (NumberFormatException nfe) {
	    	System.err.println("\nAuto flush time must be one of the following values: " + FLUSH_VALUES_STR);
	    	return;
	    }
	    // ZIP output files?
		// Only way to turn off ZIP is using this command line flag
	    bZipMode = !line.hasOption("no_zip");
	    // Include cursor in output screen capture images?
	    bIncludeMouseCursor = !line.hasOption("no_mouse_cursor");
	    // Where to write the files to
	    outputFolder = line.getOptionValue("outputfolder",outputFolder);
	    // How many frames (i.e., screen dumps) to capture per second
	    try {
	    	framesPerSec = Double.parseDouble(line.getOptionValue("fps",""+DEFAULT_FPS));
	    	if (framesPerSec <= 0.0) {
	    		throw new NumberFormatException("value must be greater than 0.0");
	    	}
	    	// Make sure framesPerSec is one of the accepted values
	    	Double userVal = framesPerSec;
	    	if (!Arrays.asList(FPS_VALUES).contains(userVal)) {
	    		throw new NumberFormatException(new String("framespersec value must be one of: " + FPS_VALUES_STR));
	    	}
	    } catch (NumberFormatException nfe) {
	    	System.err.println("\nError parsing \"fps\"; it must be one of the accepted floating point values:\n" + nfe);
	    	return;
	    }
	    // Run CT in debug mode?
	    bDebugMode = line.hasOption("debug");
	    // changeDetect mode? MJM
	    bChangeDetect = line.hasOption("change_detect");
	    // audio capture mode? MJM
	    bAudioCapture = line.hasOption("audio_cap");
	    // Capture the full screen?
	    bFullScreen = line.hasOption("full_screen");
	    // Keep CTscreencap UI on top of all other windows?
	    bStayOnTop = line.hasOption("UI_on_top");
	    // Image quality
	    String imageQualityStr = line.getOptionValue("q",""+imageQuality);
	    try {
	    	imageQuality = Float.parseFloat(imageQualityStr);
	    	if ( (imageQuality < 0.0f) || (imageQuality > 1.0f) ) {
	    		throw new NumberFormatException("");
	    	}
	    } catch (NumberFormatException nfe) {
	    	System.err.println("\nimage quality must be a number in the range 0.0 <= x <= 1.0");
	    	return;
	    }
	    
	    // Decode the String corresponding to binary cursor data; produce a BufferedImage with it
	 	Base64.Decoder byte_decoder = Base64.getDecoder();
	 	byte[] decoded_cursor_bytes = byte_decoder.decode(encoded_cursor_noshadow);
	 	try {
	 		cursor_img = ImageIO.read(new ByteArrayInputStream(decoded_cursor_bytes));
	 	} catch (IOException ioe) {
	 		System.err.println("Error creating BufferedImage from cursor data:\n" + ioe);
	 		return;
	 	}
	    
	    //
	    // THIS IS OLDER CODE WHERE THE USER SPECIFIED THE REGION TO CAPTURE AHEAD OF TIME;
	    // WE NOW DO THIS DYNAMICALLY: THE USER PUTS A FRAME AROUND THE REGION TO CAPTURE
	    // AND CAN CHANGE IT AS THE PROGRAM RUNS
	    //
	    // Determine if the GraphicsDevice supports translucency.
	    // This code is from https://docs.oracle.com/javase/tutorial/uiswing/misc/trans_shaped_windows.html
	    //
	    // If translucent windows aren't supported, capture the entire screen.
	    // Otherwise, have the user draw a rectangle around the area they wish to capture.
	    //
	    /*
        GraphicsEnvironment graphEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice graphDev = graphEnv.getDefaultScreenDevice();
        if (!graphDev.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
            System.err.println("Translucency is not supported; capturing the entire screen.");
            regionToCapture = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        } else {
        	// Have user specify region to capture by drawing a rectangle with the mouse
    	    // Create the GUI on the event-dispatching thread
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                	DefineCaptureRegion dcr = new DefineCaptureRegion(temporaryCTS);
                    // Display the window; since this is a modal dialog, this method won't
                	// return until the dialog is hidden
                    dcr.setVisible(true);
                }
            });
        }
        // Wait until user has specified the region to capture before proceeding.
        // (This is a bit hokey just waiting until regionToCapture has been defined, but it works.)
        while (regionToCapture == null) {
        	try {
        		Thread.sleep(500);
        	} catch (Exception e) {
        		// Nothing to do
        	}
        }
        */
	    
	    //
	    // Automatically start capturing the full screen in the following situations:
	    // 1. The user has requested to do this via the -f command line option
	    // 2. If the GraphicsDevice does not support translucency
	    //       (see https://docs.oracle.com/javase/tutorial/uiswing/misc/trans_shaped_windows.html)
	    // Otherwise, pop up a frame where the user dynamically specifies what to capture.
	    //
	 	
	 	if(bWebCam) {
			webcam = Webcam.getDefault();
			webcam.setViewSize(WebcamResolution.VGA.getSize());
//			webcam.setDriver(new JmfDriver());
			webcam.open();
	 	}
	 	
	 	if (bFullScreen) {
	    	regionToCapture = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
	    	System.err.println("Capturing the entire screen; click Ctrl+c when finished\n");
	    } else {
	    	GraphicsEnvironment graphEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
	        GraphicsDevice graphDev = graphEnv.getDefaultScreenDevice();
	        if (!graphDev.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
	        	bFullScreen = true;
	            System.err.println("Translucency is not supported; capturing the entire screen; click Ctrl+c when finished\n.");
	            regionToCapture = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
	        } else {
	        	// Check to see if Shaped windows are supported
	        	// (see https://docs.oracle.com/javase/tutorial/uiswing/misc/trans_shaped_windows.html)
	        	final boolean bShapedWindowSupported = graphDev.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT);
	        	// For thread safety: Schedule a job for the event-dispatching thread to create and show the GUI
	        	SwingUtilities.invokeLater(new Runnable() {
	        	    public void run() {
	        	    	temporaryCTS.createAndShowGUI(bShapedWindowSupported);
	        	    }
	        	});
	        }
	    }
	    
	    // If we are in full screen mode, start capture right away
	    if (bFullScreen) {
	    	startCapture();
	    }
		
	}
	
	/**
	 * getNextTime
	 * 
	 * Calculate the next time to assign to a data point to be sent to CT.
	 * The time to use depends on whether or not we are in continue mode.
	 * 
	 * @return The next time to assign to CT data.
	 */
	public synchronized long getNextTime() {
		long nextTime = System.currentTimeMillis();
		if (bContinueMode) {
			if (firstCTtime == 0) {
				// Starting a new video segment ("continue" mode)
				if (bFTP) {
					// Since we can't interrogate the remote source to determine
					// the last timestamp, pickup at 1msec after the last timestamp
					// we sent to CTwriter.
					firstCTtime = lastCTtime + 1;
				} else {
					// MJM 2017-02-23
					// Pickup at 1msec after the last timestamp observed in the output source folders;
					// this avoids a problem if user has manually deleted/changed CT disk folders
					// JPW, changed to not use full path in newTime call
					// firstCTtime = 1 + (long)((new CTreader().newTime(outputFolder + sourceName)) * 1000.);
					firstCTtime = 1 + (long)((new CTreader(outputFolder).newTime(sourceName)) * 1000.);
				}
				// Note the current wall clock time
				continueWallclockInitTime = System.currentTimeMillis();
			}
			nextTime = firstCTtime + (System.currentTimeMillis() - continueWallclockInitTime);
			// Should we reject a backward going time?
			// o When writing to local files (ie, *not* FTP mode) note that the user
			//   may have manually deleted/adjusted folders and so it may appear that
			//   the source time is going backward from the standpoint of the last
			//   timestamp we actually wrote out (lastCTtime); it is OK to write out
			//   a "backward going timestamp" in this case.
			// o When we are in FTP mode, there's no way to know the latest time
			//   in the output source folders, thus it seems best to simply reject
			//   what looks to be a backward going time.
// NO, let audioTask logic cover sane timestamps - MJM 4/4/17
//			if ( (bFTP) && (nextTime < lastCTtime) ) {
//				System.err.println("\ngetNextTime: detected backward moving time; just return lastCTtime");
//				nextTime = lastCTtime;
//			}
		}
		// Squirrel away this time
		lastCTtime = nextTime;
		return nextTime;
	}
	
	/**
	 * 
	 * setLastCTtime
	 * 
	 * FUNCTION NO LONGER USED
	 * 
	 * Set lastCTtime to the given time.
	 * 
	 * @param timeI Set lastCTtime to this time
	 */
	/**
	 * public synchronized void setLastCTtime(long timeI) {
	 * 	// Only save the time if it is moving forward
	 * 	if (timeI > lastCTtime) {
	 * 		lastCTtime = timeI;
	 * 	}
	 * }
	 */
	
	/**
	 * Method to Start screen capture
	 */
	private void startCapture() {
		
		// Firewalls
		// By the time we get to this function, ctw should be null (ie, no currently active CT connection)
		if (ctw != null)				{ System.err.println("ERROR in startCapture(): CTwriter object is not null; returning"); return; }
		if (screencapTimer != null)		{ System.err.println("ERROR in startCapture(): Timer object is not null; returning"); return; }
		if (screencapTimerTask != null)	{ System.err.println("ERROR in startCapture(): ScreencapTimerTask object is not null; returning"); return; }
		if (audioTask != null)			{ System.err.println("ERROR in startCapture(): AudiocapTask object is not null; returning"); return; }
		if (writeTask != null)			{ System.err.println("ERROR in startCapture(): WriteTask object is not null; returning"); return; }
		if (queue != null)				{ System.err.println("ERROR in startCapture(): LinkedBlockingQueue object is not null; returning"); return; }
		
		// Display current time
		String currTimeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		long currTime = System.currentTimeMillis();
		String datetimeStr = new String(currTimeStr + " (" + currTime + ")");
		if (bContinueMode) {
			System.err.println("\n" + datetimeStr + ": Continue periodic screen capture from where we last left off");
		} else {
			System.err.println("\n" + datetimeStr + ": Start new periodic screen capture");
		}
		
		bShutdown = false;
		
		// Setup CTwriter
		try {
			CTinfo.setDebug(bDebugMode);
			// Make sure outputFolder ends in a file separator
			String outFolderToUse = outputFolder;
		    if (!outputFolder.endsWith(File.separator)) {
		    	outFolderToUse = outputFolder + File.separator;
		    }
		    if (!bFTP) {
				ctw = new CTwriter(outFolderToUse + sourceName);
			} else {
				CTftp ctftp = new CTftp(outFolderToUse + sourceName);
				try {
					ctftp.login(ftpHost,ftpUser,ftpPassword);
					// upcast to CTWriter
					ctw = ctftp;
				} catch (Exception e) {
					System.err.println("Error logging into FTP server \"" + ftpHost + "\":\n" + e.getMessage());
					return;
				}
			}
			if (!bAudioCapture) {
				// if no audio, auto-flush on video
				ctw.autoFlush(autoFlushMillis);
			}
			ctw.setZipMode(bZipMode);
			ctw.autoSegment(numBlocksPerSegment);
		} catch (IOException ioe) {
			System.err.println("Error trying to create CloudTurbine writer object:\n" + ioe);
			return;
		}
		
		// Setup the asynchronous event queue to store by arrays
		// queue = new LinkedBlockingQueue<byte[]>();
		queue = new LinkedBlockingQueue<TimeValue>();
		
		// Setup periodic screen captures
		startScreencapTimer();
		
		//
		// Start audio capture (if requested by the user)
		//
		if (bAudioCapture) {
			// start audio capture (MJM)
			audioTask = new AudiocapTask(this, ctw, autoFlushMillis);
		}
		
		//
		// Create a new WriteTask which continually grabs images off the queue and writes them to CT
		// Run this in a new thread
		//
		if(!bAudioCapture) {				// if audioCapture, images written out in AudiocapTask thread
			writeTask = new WriteTask(this);
			writeTaskThread = new Thread(writeTask);
			writeTaskThread.start();
		}
	}
	
	/**
	 * Method to Stop screen capture
	 */
	private void stopCapture() {
		
		String currTimeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		long currTime = System.currentTimeMillis();
		String datetimeStr = new String(currTimeStr + " (" + currTime + ")");
		System.err.println("\n\n" + datetimeStr + ": Stop screen capture");
		
		// Flag that it is time to shut down
    	bShutdown = true;
    	
    	// shut down WriteTask
    	if (writeTaskThread != null) {
    		try {
    			System.err.println("Wait for WriteTask to stop");
    			writeTaskThread.join(500);
    			if (writeTaskThread.isAlive()) {
    				// WriteTask must be waiting to take another screencap off the queue;
    				// interrupt it
    				writeTaskThread.interrupt();
    				writeTaskThread.join(500);
    			}
    			if (!writeTaskThread.isAlive()) {
    				System.err.println("WriteTask has stopped");
    			}
    		} catch (InterruptedException ie) {
    			System.err.println("Caught exception trying to stop WriteTask:\n" + ie);
    		}
    		writeTaskThread = null;
    		writeTask = null;
    	}
    	
		// shut down audio
    	stopAudiocapTask();
		
		// shut down CTwriter
		if (ctw != null) {
			System.err.println("Close CTwriter");
			ctw.close();
			ctw = null;
		}
		
		// shut down the periodic Timer
		stopScreencapTimer();
		
		if (queue != null) {
			queue.clear();
			queue = null;
		}
		
	}
	
	/**
	 * Convenience method to start a new screencapTimer
	 * 
	 * Setup a Timer to periodically call ScreencapTimerTask.run().  See the
	 * notes in the top header for how this fits into the overall program.
	 */
	private void startScreencapTimer() {
		// First, make sure any existing screencapTimer is finished
		stopScreencapTimer();
		// Now start the new Timer
		capturePeriodMillis = (long)(1000.0 / framesPerSec);
		screencapTimer = new Timer();
		screencapTimerTask = new ScreencapTimerTask(this);
		screencapTimer.schedule(screencapTimerTask, 0, capturePeriodMillis);
	}
	
	/**
	 * Convenience method to stop the currently running screencapTimer
	 */
	private void stopScreencapTimer() {
		if (screencapTimer != null) {
			screencapTimer.cancel();
			screencapTimer.purge();
			screencapTimer = null;
			screencapTimerTask = null;
		}
	}
	
	/**
	 * stopAudiocapTask
	 * 
	 * Terminate audio capture
	 */
	private void stopAudiocapTask() {
		if (audioTask == null) {
			System.err.println("AudiocapTask is already stopped");
			return;
		}
		System.err.println("Wait for AudiocapTask to stop");
		audioTask.shutDown();
		try {
			// Wait for the audioTask thread to finish
			Thread audioTaskThread = audioTask.captureThread;
			audioTaskThread.join(2*autoFlushMillis); // audio capture isn't necessarily precise, so wait up to 2*autoFlushMillis
			if (audioTaskThread.isAlive()) {
    			// AudiocapTask must be held up; interrupt it
				audioTaskThread.interrupt();
				audioTaskThread.join(500);
    		}
    		if (!audioTaskThread.isAlive()) {
    			System.err.println("AudiocapTask has stopped");
    		}
		} catch (InterruptedException ie) {
    		System.err.println("Caught exception trying to stop AudiocapTask:\n" + ie);
    	}
		audioTask = null;
	}
	
	/**
	 * Pop up the GUI
	 * 
	 * This method should be run in the event-dispatching thread.
	 * 
	 * The GUI is created in one of two modes depending on whether Shaped
	 * windows are supported on the platform:
	 * 
	 * 1. If Shaped windows are supported then guiPanel (the container to
	 *    which all other components are added) is RED and capturePanel is
	 *    inset a small amount to this panel so that the RED border is seen
	 *    around the outer edge.  A componentResized() method is defined
	 *    which creates the hollowed out region that was capturePanel.
	 * 2. If Shaped windows are not supported then guiPanel is transparent
	 *    and capturePanel is translucent.  In this case, the user can't
	 *    "reach through" capturePanel to interact with GUIs on the other
	 *    side.
	 * 
	 * @param  bShapedWindowSupportedI  Does the underlying GraphicsDevice support the
	 * 									PERPIXEL_TRANSPARENT translucency that is
	 * 									required for Shaped windows?
	 */
	private void createAndShowGUI(boolean bShapedWindowSupportedI) {
		
		// No window decorations for translucent/transparent windows
		// (see note below)
		// JFrame.setDefaultLookAndFeelDecorated(true);
		
		//
		// Create GUI components
		//
		GridBagLayout framegbl = new GridBagLayout();
		guiFrame = new JFrame("CTscreencap");
		// To support a translucent window, the window must be undecorated
		// See notes in the class header up above about this; also see
		// http://alvinalexander.com/source-code/java/how-create-transparenttranslucent-java-jframe-mac-os-x
        guiFrame.setUndecorated(true);
        // Use MouseMotionListener to implement our own simple "window manager" for moving and resizing the window
        guiFrame.addMouseMotionListener(this);
        guiFrame.setBackground(new Color(0,0,0,0));
        guiFrame.getContentPane().setBackground(new Color(0,0,0,0));
		GridBagLayout gbl = new GridBagLayout();
		guiPanel = new JPanel(gbl);
		// if Shaped windows are supported, make guiPanel red;
		// otherwise make it transparent
		if (bShapedWindowSupportedI) {
			guiPanel.setBackground(Color.RED);
		} else {
			guiPanel.setBackground(new Color(0,0,0,0));
		}
		guiFrame.setFont(new Font("Dialog", Font.PLAIN, 12));
		guiPanel.setFont(new Font("Dialog", Font.PLAIN, 12));
		GridBagLayout controlsgbl = new GridBagLayout();
		// *** controlsPanel contains the UI controls at the top of guiFrame
		controlsPanel = new JPanel(controlsgbl);
		controlsPanel.setBackground(new Color(211,211,211,255));
		startStopButton = new JButton("Start");
		startStopButton.addActionListener(this);
		startStopButton.setBackground(Color.GREEN);
		continueButton = new JButton("Continue");
		continueButton.addActionListener(this);
		continueButton.setEnabled(false);
		JLabel fpsLabel = new JLabel("frames/sec",SwingConstants.LEFT);
		JComboBox<Double> fpsCB = new JComboBox<Double>(FPS_VALUES);
		int tempIndex = Arrays.asList(FPS_VALUES).indexOf(new Double(framesPerSec));
		fpsCB.setSelectedIndex(tempIndex);
		fpsCB.addActionListener(this);
		// The popup doesn't display over the transparent region;
		// therefore, just display a few rows to keep it within controlsPanel
		fpsCB.setMaximumRowCount(3);
		JLabel imgQualLabel = new JLabel("image qual",SwingConstants.LEFT);
		// The slider will use range 0 - 1000
		JSlider imgQualSlider = new JSlider(JSlider.HORIZONTAL,0,1000,(int)(imageQuality*1000.0));
		// NOTE: The JSlider's initial width was too large, so I'd like to set its preferred size
		//       to try and constrain it some; also need to set its minimum size at the same time,
		//       or else when the user makes the GUI frame smaller, the JSlider would pop down to
		//       a really small minimum size.
		imgQualSlider.setPreferredSize(new Dimension(120,30));
		imgQualSlider.setMinimumSize(new Dimension(120,30));
		imgQualSlider.setBackground(controlsPanel.getBackground());
		imgQualSlider.addChangeListener(this);
		// Add labels to the slider
		// JPW 2017-02-24 Use separate labels instead of a label table set in the slider
		// Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
		// labelTable.put( new Integer(100), new JLabel("Low") );
		// labelTable.put( new Integer(900), new JLabel("High") );
		// imgQualSlider.setLabelTable( labelTable );
		// imgQualSlider.setPaintLabels(true);
		changeDetectCheck = new JCheckBox("Change detect",bChangeDetect);
		changeDetectCheck.setBackground(controlsPanel.getBackground());
		changeDetectCheck.addActionListener(this);
		fullScreenCheck = new JCheckBox("Full Screen");
		fullScreenCheck.setBackground(controlsPanel.getBackground());
		fullScreenCheck.addActionListener(this);
		audioCheck = new JCheckBox("Audio",bAudioCapture);
		audioCheck.setBackground(controlsPanel.getBackground());
		audioCheck.addActionListener(this);
		// *** capturePanel
		capturePanel = new JPanel();
		if (!bShapedWindowSupportedI) {
			// Only make capturePanel translucent (ie, semi-transparent) if we aren't doing the Shaped window option
			capturePanel.setBackground(new Color(0,0,0,16));
		} else {
			capturePanel.setBackground(new Color(0,0,0,0));
		}
		capturePanel.setPreferredSize(new Dimension(500,400));
		boolean bMacOS = false;
		String OS = System.getProperty("os.name", "generic").toLowerCase();
		if ((OS.indexOf("mac") >= 0) || (OS.indexOf("darwin") >= 0)) {
			bMacOS = true;
		}
		/**
		 * 
		 * Only have the CTscreencap UI stay on top of all other windows
		 * if bStayOnTop is true (set by command line flag).
		 * 
		 *	if (!bMacOS) {
		 *		// On Mac, we haven't found a way to allow the user to "reach through"
		 *		// capturePanel to interact with windows beneath the GUI; on
		 *		// Windows and Linux this seems to work OK, so in those cases we
		 *		// will always keep guiFrame on top
		 *		guiFrame.setAlwaysOnTop(true);
		 *	}
		 */
		if (bStayOnTop) {
			guiFrame.setAlwaysOnTop(true);
		}
        
		//
		// Add components to the GUI
		//
		
		int row = 0;
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		
		//
		// First row: the controls panel
		//
		gbc.insets = new Insets(0, 0, 0, 0);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 0;
		Utility.add(guiPanel, controlsPanel, gbl, gbc, 0, row, 1, 1);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		++row;
		
		// Add controls to the controls panel
		int panelrow = 0;
		// (i) Start/Continue buttons
		GridBagLayout panelgbl = new GridBagLayout();
		JPanel subPanel = new JPanel(panelgbl);
		GridBagConstraints panelgbc = new GridBagConstraints();
		panelgbc.anchor = GridBagConstraints.WEST;
		panelgbc.fill = GridBagConstraints.NONE;
		panelgbc.weightx = 0;
		panelgbc.weighty = 0;
		subPanel.setBackground(controlsPanel.getBackground());
		panelgbc.insets = new Insets(0, 0, 0, 5);
		Utility.add(subPanel, startStopButton, panelgbl, panelgbc, 0, 0, 1, 1);
		panelgbc.insets = new Insets(0, 0, 0, 0);
		Utility.add(subPanel, continueButton, panelgbl, panelgbc, 1, 0, 1, 1);
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = new Insets(5, 0, 0, 0);
		Utility.add(controlsPanel, subPanel, controlsgbl, gbc, 0, panelrow, 2, 1);
		++panelrow;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.NONE;
		// (ii) frames/sec control
		gbc.insets = new Insets(5, 0, 0, 0);
		Utility.add(controlsPanel, fpsLabel, controlsgbl, gbc, 0, panelrow, 1, 1);
		gbc.insets = new Insets(5, 10, 0, 10);
		Utility.add(controlsPanel, fpsCB, controlsgbl, gbc, 1, panelrow, 1, 1);
		++panelrow;
		// (iii) image quality slider
		gbc.insets = new Insets(-5, 0, 0, 0);
		Utility.add(controlsPanel, imgQualLabel, controlsgbl, gbc, 0, panelrow, 1, 1);
		panelgbl = new GridBagLayout();
		subPanel = new JPanel(panelgbl);
		panelgbc = new GridBagConstraints();
		panelgbc.anchor = GridBagConstraints.WEST;
		panelgbc.fill = GridBagConstraints.NONE;
		panelgbc.weightx = 0;
		panelgbc.weighty = 0;
		subPanel.setBackground(controlsPanel.getBackground());
		JLabel sliderLabelLow = new JLabel("Low",SwingConstants.LEFT);
		JLabel sliderLabelHigh = new JLabel("High",SwingConstants.LEFT);
		panelgbc.insets = new Insets(-5, 5, 0, 5);
		Utility.add(subPanel, sliderLabelLow, panelgbl, panelgbc, 0, 0, 1, 1);
		panelgbc.insets = new Insets(0, 0, 0, 0);
		Utility.add(subPanel, imgQualSlider, panelgbl, panelgbc, 1, 0, 1, 1);
		panelgbc.insets = new Insets(-5, 5, 0, 0);
		Utility.add(subPanel, sliderLabelHigh, panelgbl, panelgbc, 2, 0, 1, 1);
		gbc.insets = new Insets(0, 0, 0, 0);
		Utility.add(controlsPanel, subPanel, controlsgbl, gbc, 1, panelrow, 1, 1);
		++panelrow;
		// (iv) Change detect / Full screen / Audio checkboxes
		panelgbl = new GridBagLayout();
		subPanel = new JPanel(panelgbl);
		panelgbc = new GridBagConstraints();
		panelgbc.anchor = GridBagConstraints.WEST;
		panelgbc.fill = GridBagConstraints.NONE;
		panelgbc.weightx = 0;
		panelgbc.weighty = 0;
		subPanel.setBackground(controlsPanel.getBackground());
		panelgbc.insets = new Insets(0, 0, 0, 0);
		Utility.add(subPanel, changeDetectCheck, panelgbl, panelgbc, 0, 0, 1, 1);
		Utility.add(subPanel, fullScreenCheck, panelgbl, panelgbc, 1, 0, 1, 1);
		Utility.add(subPanel, audioCheck, panelgbl, panelgbc, 2, 0, 1, 1);
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = new Insets(-5, 0, 3, 0);
		Utility.add(controlsPanel, subPanel, controlsgbl, gbc, 0, panelrow, 2, 1);
		
		//
		// Second row: the translucent/transparent capture panel
		//
		if (bShapedWindowSupportedI) {
			// Doing the Shaped window; set capturePanel inside guiPanel
			// a bit so the red from guiPanel shows at the edges
			gbc.insets = new Insets(5, 5, 5, 5);
		} else {
			// No shaped window; have capturePanel fill the area
			gbc.insets = new Insets(0, 0, 0, 0);
		}
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 100;
		gbc.weighty = 100;
		Utility.add(guiPanel, capturePanel, gbl, gbc, 0, row, 1, 1);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		++row;
		
		//
		// Add guiPanel to guiFrame
		//
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 100;
		gbc.weighty = 100;
		gbc.insets = new Insets(0, 0, 0, 0);
		Utility.add(guiFrame, guiPanel, framegbl, gbc, 0, 0, 1, 1);
		
		//
		// Add menu
		//
		JMenuBar menuBar = createMenu();
		guiFrame.setJMenuBar(menuBar);
		
		//
		// If Shaped windows are supported, the region defined by capturePanel
		// will be "hollowed out" so that the user can reach through guiFrame
		// and interact with applications which are behind it.
		//
		// NOTE: This doesn't work on Mac OS (we've tried, but nothing seems
		//       to work to allow a user to reach through guiFrame to interact
		//       with windows behind).  May be a limitation or bug on Mac OS:
		//       https://bugs.openjdk.java.net/browse/JDK-8013450
		//
		if (bShapedWindowSupportedI) {
			guiFrame.addComponentListener(new ComponentAdapter() {
				// As the window is resized, the shape is recalculated here.
				@Override
				public void componentResized(ComponentEvent e) {
					// Create a rectangle to cover the entire guiFrame
					Area guiShape = new Area(new Rectangle(0, 0, guiFrame.getWidth(), guiFrame.getHeight()));
					// Create another rectangle to define the hollowed out region of capturePanel
					guiShape.subtract(new Area(new Rectangle(capturePanel.getX(), capturePanel.getY()+23, capturePanel.getWidth(), capturePanel.getHeight())));
					guiFrame.setShape(guiShape);
				}
			});
		}
		
		//
		// Final guiFrame configuration details and displaying the GUI
		//
		guiFrame.pack();
		
		guiFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		
		guiFrame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				exit(false);
			}
		});
		
		// Center on the screen
		guiFrame.setLocationRelativeTo(null);
		
		//
		// Set the taskbar/dock icon; note that Mac OS has its own way of doing it
		//
		if (bMacOS) {
			// The following solution from:
			// http://stackoverflow.com/questions/11253772/setting-the-default-application-icon-image-in-java-swing-on-os-x
			// https://gist.github.com/bchapuis/1562406
			try {
				InputStream imageInputStreamLarge = guiFrame.getClass().getResourceAsStream("/Icon_128x128.png");
				BufferedImage bufferedImageLarge = ImageIO.read(imageInputStreamLarge);
				// Here's the call we really want to make, but we will do this via reflection
		        // com.apple.eawt.Application.getApplication().setDockIconImage( bufferedImageLarge );
				Class<?> util = Class.forName("com.apple.eawt.Application");
			    Method getApplication = util.getMethod("getApplication", new Class[0]);
			    // The following is the equivalent of invoking: com.apple.eawt.Application.getApplication()
			    Object application = getApplication.invoke(util);
			    Class<?> params[] = new Class[1];
			    params[0] = Image.class;
			    Method setDockIconImage = util.getMethod("setDockIconImage", params);
			    // The following is the equivalent of invoking: com.apple.eawt.Application.getApplication().setDockIconImage(bufferedImageLarge)
			    setDockIconImage.invoke(application, bufferedImageLarge);
		    }
		    catch (Exception excepI) {
		    	System.err.println("Exception thrown trying to set icon: " + excepI);
		    }
		} else {
			// The following has been tested under Windows 10 and Ubuntu 12.04 LTS
			try {
				InputStream imageInputStreamLarge = guiFrame.getClass().getResourceAsStream("/Icon_128x128.png");
				BufferedImage bufferedImageLarge = ImageIO.read(imageInputStreamLarge);
				InputStream imageInputStreamMed = guiFrame.getClass().getResourceAsStream("/Icon_64x64.png");
				BufferedImage bufferedImageMed = ImageIO.read(imageInputStreamMed);
				InputStream imageInputStreamSmall = guiFrame.getClass().getResourceAsStream("/Icon_32x32.png");
				BufferedImage bufferedImageSmall = ImageIO.read(imageInputStreamSmall);
				List<BufferedImage> iconList = new ArrayList<BufferedImage>();
				iconList.add(bufferedImageLarge);
				iconList.add(bufferedImageMed);
				iconList.add(bufferedImageSmall);
				guiFrame.setIconImages(iconList);
			} catch (IOException excepI) {
				System.err.println("Exception thrown trying to set icon: " + excepI);
			}
		}
		
		ctSettings = new CTsettings(this,guiFrame);
		
		guiFrame.setVisible(true);
		
	}
	
	/**
	 * Create menu for the GUI
	 * 
	 * @return  The new menu bar
	 */
	private JMenuBar createMenu() {
		JMenuBar menuBar = new JMenuBar();
		JMenu menu = new JMenu("File");
		menuBar.add(menu);
		JMenuItem menuItem = new JMenuItem("Settings...");
		menu.add(menuItem);
		menuItem.addActionListener(this);
		menuItem = new JMenuItem("Exit");
		menu.add(menuItem);
		menuItem.addActionListener(this);
		return menuBar;
	}
	
	/**
	 * Callback for some UI controls (the frames/sec combo box, change detect checkbox
	 * and the Start/Stop button) as well as the menu items.
	 * 
	 * @param eventI The ActionEvent which has occurred.
	 */
	@Override
	public void actionPerformed(ActionEvent eventI) {
		Object source = eventI.getSource();
		if (source == null) {
			return;
		} else if (source instanceof JComboBox) {
			JComboBox<?> fpsCB = (JComboBox<?>)source;
			double framesPerSecNew = ((Double)fpsCB.getSelectedItem()).doubleValue();
			if (ctw == null) {
				// No screen capture currently taking place;
				// just save the new value
				framesPerSec = framesPerSecNew;
			} else if ( (ctw != null) && (framesPerSecNew != framesPerSec) ) {
				// Start new periodic screen captures using the new rate
				framesPerSec = framesPerSecNew;
				System.err.println("\nRestarting screen captures at new rate: " + framesPerSec + " frames/sec");
				startScreencapTimer();
			}
			// Even when "change detect" is turned on, we always save an image at a rate which is
			// the slower of 1.0fps or the current frame rate; this is kind of a "key frame" of
			// sorts.  Because of this, the "change detect" checkbox is meaningless when frames/sec
			// is 1.0 and lower.
			if (framesPerSec <= 1.0) {
				changeDetectCheck.setEnabled(false);
				// A nice side benefit of processing JCheckBox events using an Action listener
				// is that calling "changeDetectCheck.setSelected(false)" will NOT fire an
				// event; thus, we maintain our original value of bChangeDetect.
				changeDetectCheck.setSelected(false);
			} else {
				changeDetectCheck.setEnabled(true);
				changeDetectCheck.setSelected(bChangeDetect);
			}
		} else if ((source instanceof JCheckBox) && (((JCheckBox)source) == changeDetectCheck)) {
			if (changeDetectCheck.isSelected()) {
				bChangeDetect = true;
			} else {
				bChangeDetect = false;
			}
		} else if ((source instanceof JCheckBox) && (((JCheckBox)source) == fullScreenCheck)) {
			if (fullScreenCheck.isSelected()) {
				bFullScreen = true;
				// Set regionToCapture to be the full screen
				regionToCapture = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
				// Save the original height
				guiFrameOrigHeight = guiFrame.getHeight();
				// Shrink the GUI down to just display controlsPanel
				Rectangle guiFrameBounds = guiFrame.getBounds();
				Rectangle updatedGUIFrameBounds = new Rectangle(guiFrameBounds.x,guiFrameBounds.y,guiFrameBounds.width,controlsPanel.getHeight()+22);
				guiFrame.setBounds(updatedGUIFrameBounds);
			} else {
				bFullScreen = false;
				// Expand the GUI to display capturePanel
				Rectangle guiFrameBounds = guiFrame.getBounds();
				int updatedHeight = guiFrameOrigHeight;
				if (guiFrameOrigHeight == -1) {
					updatedHeight = controlsPanel.getHeight()+450;
				}
				Rectangle updatedGUIFrameBounds = new Rectangle(guiFrameBounds.x,guiFrameBounds.y,guiFrameBounds.width,updatedHeight);
				guiFrame.setBounds(updatedGUIFrameBounds);
			}
		} else if ((source instanceof JCheckBox) && (((JCheckBox)source) == audioCheck)) {
			if (audioCheck.isSelected()) {
				// Start audio capture
				bAudioCapture = true;
				if (ctw != null) {
					System.err.println("\nStart audio capture");
					if (audioTask != null) {
						// Appears that it is already running
						System.err.println("ERROR: audio is already running?");
					} else {
						audioTask = new AudiocapTask(this, ctw, autoFlushMillis);
					}
					// Turn autoFlush off (by making it huge)
					ctw.autoFlush(Long.MAX_VALUE);
				}
			} else {
				// Shut down audio capture
				bAudioCapture = false;
				if (ctw != null) {
					System.err.println("\nShut down audio capture");
					stopAudiocapTask();
					// Turn autoFlush on (since we won't be flushing in AudiocapTask)
					ctw.autoFlush(autoFlushMillis);
				}
			}
		} else if (eventI.getActionCommand().equals("Start")) {
			// Make sure all needed values have been set
			String errStr = ctSettings.canCTrun();
			if (!errStr.isEmpty()) {
				JOptionPane.showMessageDialog(guiFrame, errStr, "CTscreencap settings error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			((JButton)source).setText("Starting...");
			bContinueMode = false;
			firstCTtime = 0;
			continueWallclockInitTime = 0;
			startCapture();
			((JButton)source).setText("Stop");
			((JButton)source).setBackground(Color.RED);
			continueButton.setEnabled(false);
		} else if (eventI.getActionCommand().equals("Stop")) {
			((JButton)source).setText("Stopping...");
			stopCapture();
			((JButton)source).setText("Start");
			((JButton)source).setBackground(Color.GREEN);
			continueButton.setEnabled(true);
		} else if (eventI.getActionCommand().equals("Continue")) {
			// This is just like "Start" except we pick up in time just where we left off
			bContinueMode = true;
			firstCTtime = 0;
			continueWallclockInitTime = 0;
			startCapture();
			startStopButton.setText("Stop");
			startStopButton.setBackground(Color.RED);
			continueButton.setEnabled(false);
		} else if (eventI.getActionCommand().equals("Settings...")) {
			boolean bBeenRunning = false;
			if (startStopButton.getText().equals("Stop")) {
				bBeenRunning = true;
			}
			// Turn off screencap (if it is running)
			stopCapture();
			startStopButton.setText("Start");
			startStopButton.setBackground(Color.GREEN);
			// Only want to enable the Continue button if the user had in fact been running
			if (bBeenRunning) {
				continueButton.setEnabled(true);
			}
			// Let user edit settings; the following function will not
			// return until the user clicks the OK or Cancel button.
			ctSettings.popupSettingsDialog();
		} else if (eventI.getActionCommand().equals("Exit")) {
			exit(false);
		}
	}
	
	/**
	 * Callback for the UI JSlider which adjusts the image quality
	 * 
	 * @param eventI The ChangeEvent that has occurred.
	 */
	@Override
	public void stateChanged(ChangeEvent eventI) {
		Object sourceObj = eventI.getSource();
		if (!(sourceObj instanceof JSlider)) {
			return;
		}
		JSlider source = (JSlider)sourceObj;
		// The following code will update imageQuality as the user is
		// moving the slider; to have imageQuality update only when
		// the user lets go of the slider, surround this code in an
		// "if" block as follows:
        //    if (!source.getValueIsAdjusting()) {
		//        ... code ...
		//    }
        float currentVal = (float)source.getValue();
        imageQuality = currentVal/1000.0f;
        bChangeDetected = true; // force image display even if unchanged (MJM)
        // System.err.println("\nimageQuality = " + imageQuality);
	}
	
	/**
	 * Exit the application
	 * 
	 * @param bCalledFromShutdownHookI  Was this method called from the shutdown hook?
	 */
	private void exit(boolean bCalledFromShutdownHookI) {
		stopCapture();
		System.err.println("\nExit CTscreencap\n");
		// If we *are* called from the shutdown hook, don't exit
		// (Java support for the shutdown hook must include its own
		//  code to call exit and if we call exit here, that gets
		//  screwed up.)
		if (!bCalledFromShutdownHookI) {
			// When we execute System.exit(0), the shutdown hook is called;
			// set bCallExitFromShutdownHook = false so that CTscreencap.exit()
			// isn't called from the shutdown hook.
			bCallExitFromShutdownHook = false;
			System.exit(0);
		}
	}
	
	/**
	 * Utility function which reads the specified image file and returns
	 * a String corresponding to the encoded bytes of this image.
	 *
	 * Sample code for calling this method to generate an encoded string representation of a cursor image:
	 * String cursorStr = null;
	 * try {
	 *     cursorStr = getEncodedImageString("cursor.png","png");
	 *     System.err.println("Cursor string:\n" + cursorStr + "\n");
	 * } catch (IOException ioe) {
	 *     System.err.println("Caught exception generating encoded string for cursor data:\n" + ioe);
	 * }
	 *
	 * A buffered image can be created from this encoded String as follows; this is done in the
	 * CTscreencap constructor:
	 * Base64.Decoder byte_decoder = Base64.getDecoder();
	 * byte[] decoded_cursor_bytes = byte_decoder.decode(cursorStr);
	 * try {
	 *     BufferedImage cursor_img = ImageIO.read(new ByteArrayInputStream(decoded_cursor_bytes));
	 * } catch (IOException ioe) {
	 *     System.err.println("Error creating BufferedImage from cursor data:\n" + ioe);
	 * }
	 *
	 * ScreencapTask includes code to draw this BufferedImage into the screencapture image
	 * using Graphics2D (the reason we do this is because the screencapture doesn't include
	 * the cursor).
	 * 
	 * @return A String corresponding to the encoded bytes of the specified image.
	 * 
	 * @param fileNameI		Name of the image file
	 * @param formatNameI	Format of the image file ("png", for instance)
	 * 
	 * @throws IOException if there are problems reading or writing the image or if there is an error flushing the ByteArrayOutputStream
	 */
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
	
	/**
	 * 
	 * mouseDragged
	 * 
	 * Implement the mouseDragged method defined by interface MouseMotionListener.
	 * 
	 * This method implements the "guts" of our homemade window manager; this method
	 * handles moving and resizing the frame.
	 * 
	 * Why have we implemented our own window manager?  Since translucent panels
	 * can only be contained within undecorated Frames (see comments in the top
	 * header above) and since undecorated Frames don't support moving/resizing,
	 * we implement our own basic "window manager" by catching mouse move and drag
	 * events.
	 * 
	 * @author John P. Wilson
	 * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
	 */
	@Override
	public void mouseDragged(MouseEvent mouseEventI) {
		// System.err.println("mouseDragged: " + mouseEventI.getX() + "," + mouseEventI.getY());
		// Keep the screen capture area to at least a minimum width and height
		boolean bDontMakeThinner = false;
		boolean bDontMakeShorter = false;
		if (capturePanel.getHeight() < 20) {
			bDontMakeShorter = true;
		}
		if (capturePanel.getWidth() < 20) {
			bDontMakeThinner = true;
		}
		Point currentPoint = mouseEventI.getLocationOnScreen();
		int currentPosX = currentPoint.x;
		int currentPosY = currentPoint.y;
		int deltaX = 0;
		int deltaY = 0;
		if ( (mouseCommandMode != NO_COMMAND) && (mouseStartingPoint != null) ) {
			deltaX = currentPosX - mouseStartingPoint.x;
			deltaY = currentPosY - mouseStartingPoint.y;
		}
		int oldFrameWidth = guiFrame.getBounds().width;
		int oldFrameHeight = guiFrame.getBounds().height;
		if (mouseCommandMode == MOVE_FRAME) {
			Point updatedGUIFrameLoc = new Point(frameStartingBounds.x+deltaX,frameStartingBounds.y+deltaY);
			guiFrame.setLocation(updatedGUIFrameLoc);
		} else if (mouseCommandMode == RESIZE_FRAME_NW) {
			int newFrameWidth = frameStartingBounds.width-deltaX;
			int newFrameHeight = frameStartingBounds.height-deltaY;
			if ( (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) || (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) ) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x+deltaX,frameStartingBounds.y+deltaY,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_N) {
			int newFrameWidth = frameStartingBounds.width;
			int newFrameHeight = frameStartingBounds.height-deltaY;
			if (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x,frameStartingBounds.y+deltaY,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_NE) {
			int newFrameWidth = frameStartingBounds.width+deltaX;
			int newFrameHeight = frameStartingBounds.height-deltaY;
			if ( (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) || (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) ) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x,frameStartingBounds.y+deltaY,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_E) {
			int newFrameWidth = frameStartingBounds.width+deltaX;
			int newFrameHeight = frameStartingBounds.height;
			if (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x,frameStartingBounds.y,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_SE) {
			int newFrameWidth = frameStartingBounds.width+deltaX;
			int newFrameHeight = frameStartingBounds.height+deltaY;
			if ( (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) || (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) ) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x,frameStartingBounds.y,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_S) {
			int newFrameWidth = frameStartingBounds.width;
			int newFrameHeight = frameStartingBounds.height+deltaY;
			if (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x,frameStartingBounds.y,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_SW) {
			int newFrameWidth = frameStartingBounds.width-deltaX;
			int newFrameHeight = frameStartingBounds.height+deltaY;
			if ( (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) || (bDontMakeShorter && (newFrameHeight < oldFrameHeight)) ) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x+deltaX,frameStartingBounds.y,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else if (mouseCommandMode == RESIZE_FRAME_W) {
			int newFrameWidth = frameStartingBounds.width-deltaX;
			int newFrameHeight = frameStartingBounds.height;
			if (bDontMakeThinner && (newFrameWidth < oldFrameWidth)) {
				return;
			}
			Rectangle updatedGUIFrameBounds = new Rectangle(frameStartingBounds.x+deltaX,frameStartingBounds.y,newFrameWidth,newFrameHeight);
			guiFrame.setBounds(updatedGUIFrameBounds);
		} else {
			// See if we need to go into a particular command mode
			mouseStartingPoint = null;
			frameStartingBounds = null;
			mouseCommandMode = getGUIFrameCommandMode(mouseEventI.getPoint());
			if (mouseCommandMode != NO_COMMAND) {
				mouseStartingPoint = mouseEventI.getLocationOnScreen();
				frameStartingBounds = guiFrame.getBounds();
			}
		}
	}
	
	/**
	 * 
	 * mouseMoved
	 * 
	 * Implement the mouseMoved method defined by interface MouseMotionListener.
	 * 
	 * This method is part of our homemade window manager; specifically, this method
	 * handles setting the appropriate mouse cursor based on where the user has
	 * positioned the mouse on the JFrame window.
	 * 
	 * Why have we implemented our own window manager?  Since translucent panels
	 * can only be contained within undecorated Frames (see comments in the top
	 * header above) and since undecorated Frames don't support moving/resizing,
	 * we implement our own basic "window manager" by catching mouse move and drag
	 * events.
	 * 
	 * @author John P. Wilson
	 * @see java.awt.event.MouseMotionListener#mouseMoved(java.awt.event.MouseEvent)
	 */
	@Override
	public void mouseMoved(MouseEvent mouseEventI) {
		// System.err.println("mouseMoved: " + mouseEventI.getX() + "," + mouseEventI.getY());
		mouseCommandMode = NO_COMMAND;
		// Set mouse Cursor based on the current mouse position
		int commandMode = getGUIFrameCommandMode(mouseEventI.getPoint());
		switch (commandMode) {
			case NO_COMMAND:		guiFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
									break;
			case MOVE_FRAME:		guiFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
									break;
			case RESIZE_FRAME_NW:	guiFrame.setCursor(new Cursor(Cursor.NW_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_N:	guiFrame.setCursor(new Cursor(Cursor.N_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_NE:	guiFrame.setCursor(new Cursor(Cursor.NE_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_E:	guiFrame.setCursor(new Cursor(Cursor.E_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_SE:	guiFrame.setCursor(new Cursor(Cursor.SE_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_S:	guiFrame.setCursor(new Cursor(Cursor.S_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_SW:	guiFrame.setCursor(new Cursor(Cursor.SW_RESIZE_CURSOR));
									break;
			case RESIZE_FRAME_W:	guiFrame.setCursor(new Cursor(Cursor.W_RESIZE_CURSOR));
									break;
			default:				guiFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
									break;
		}
	}
	
	/**
	 * getGUIFrameCommandMode
	 * 
	 * Based on the given mouse position, determine what the corresponding mouse
	 * "command mode" should be.  For instance, if the mouse is near the upper-
	 * right corner of the window, this method will return RESIZE_FRAME_NE
	 * because the mouse is in position to resize the frame from that corner.
	 * 
	 * Several offsets are defined in variables to specify if the cursor is
	 * in a special region of the window for which moving or resizing can
	 * take place:
	 * 
	 *  	borderThickness:	a thickness all around the entire window, specifies
	 *  						a "frame" around the window; if the mouse is within
	 *  						this distance of the edge of the window, then this
	 *  						method will return one of the following "resize"
	 *  						commands, indicating that the mouse cursor is in
	 *  						position to resize the JFrame along that edge:
	 *  							RESIZE_FRAME_NW
	 *								RESIZE_FRAME_N
	 *								RESIZE_FRAME_NE
	 *								RESIZE_FRAME_E
	 *								RESIZE_FRAME_SE
	 *								RESIZE_FRAME_S
	 *								RESIZE_FRAME_SW
	 *								RESIZE_FRAME_W
	 *		cornerActiveLength:	the accepted vertical or horizontal distance from
	 *							a window corner in order to be considered within
	 *							the "active" region of that corner; for example,
	 *							let's say the cursor is along the top edge of the
	 *							JFrame, 15 pixels from the upper right (NE) corner
	 *							and cornerActiveLength = 20; in this case, the
	 *							cursor is within the "active" area for that corner
	 *							and this method would return RESIZE_FRAME_NE;
	 *							if instead the cursor was 21 pixels from the corner
	 *							along the top edge of the JFrame, the cursor would
	 *							not be considered within the active area for the
	 *							corner but is instead in the active area for resizing
	 *							at the top of the window and this method will
	 *							return RESIZE_FRAME_N
	 *		menubarHeight:		thickness at the top of the window which we
	 *							consider to be the menu bar region; if the mouse
	 *							is located within this region at the top of the
	 *							window but not within borderThickness of the very
	 *							edge of the JFrame, then this method will return
	 *							MOVE_FRAME, indicating that the mouse cursor is
	 *							in position to move the JFrame
	 * 
	 * @author John P. Wilson
	 * @param  mousePosI  Mouse position
	 */
	private int getGUIFrameCommandMode(Point mousePosI) {
		int borderThickness = 5;
		int cornerActiveLength = 20;
		int menubarHeight = 20;
		Rectangle frameBounds = guiFrame.getBounds();
		int frameWidth = frameBounds.width;
		int frameHeight = frameBounds.height;
		int cursorX = mousePosI.x;
		int cursorY = mousePosI.y;
		if ( (cursorY <= cornerActiveLength) && ( (cursorX <= borderThickness) || ( (cursorY <= borderThickness) && (cursorX <= cornerActiveLength) ) )  ) {
			// Mouse is in the upper left corner of the window
			return RESIZE_FRAME_NW;
		} else if ( (cursorY <= borderThickness) && (cursorX > cornerActiveLength) && (cursorX < (frameWidth-cornerActiveLength)) ) {
			// Mouse is near the top of the window
			return RESIZE_FRAME_N;
		} else if ( (cursorY <= cornerActiveLength) && ( (cursorX >= (frameWidth-borderThickness)) || ( (cursorY <= borderThickness) && (cursorX >= (frameWidth-cornerActiveLength)) ) )  ) {
			// Mouse is in the upper right corner of the window
			return RESIZE_FRAME_NE;
		} else if ( (cursorY <= menubarHeight) && (cursorX > cornerActiveLength) && (cursorX < (frameWidth-cornerActiveLength)) ) {
			// Mouse is in the top region of the window (in the menu bar region) but not near one of the corners
			return MOVE_FRAME;
		} else if ( (cursorY > cornerActiveLength) && (cursorY < (frameHeight-cornerActiveLength)) && (cursorX >= (frameWidth-borderThickness)) ) {
			// Mouse is on the right side of the window
			return RESIZE_FRAME_E;
		} else if ( (cursorY >= (frameHeight-cornerActiveLength)) && ( (cursorX >= (frameWidth-borderThickness)) || ( (cursorY >= (frameHeight-borderThickness)) && (cursorX >= (frameWidth-cornerActiveLength)) ) )  ) {
			// Mouse is in the lower right corner of the window
			return RESIZE_FRAME_SE;
		} else if ( (cursorY >= (frameHeight-borderThickness)) && (cursorX > cornerActiveLength) && (cursorX < (frameWidth-cornerActiveLength)) ) {
			// Mouse is near the bottom of the window
			return RESIZE_FRAME_S;
		} else if ( (cursorY >= (frameHeight-cornerActiveLength)) && ( (cursorX <= borderThickness) || ( (cursorY >= (frameHeight-borderThickness)) && (cursorX <= cornerActiveLength) ) )  ) {
			// Mouse is in the lower left corner of the window
			return RESIZE_FRAME_SW;
		} else if ( (cursorY > cornerActiveLength) && (cursorY < (frameHeight-cornerActiveLength)) && (cursorX <= borderThickness) ) {
			// Mouse is on the left side of the window
			return RESIZE_FRAME_W;
		}
		return NO_COMMAND;
	}
	
}
