/*
Copyright 2016-2017 Erigo Technologies LLC

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

package erigo.filepump;

/*
 * FilePump
 * 
 * Graphical user interface wrapper around the FilePumpWorker class, which
 * writes small files at a user-specified rate into a user-specified directory.
 * 
 * This application can also be run headless by specifying values using the
 * command line arguments and also specifying the "-x" argument to
 * automatically run.
 *
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import erigo.filepump.FilePumpSettings.FileMode;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class FilePump implements ActionListener {
	
	// The class that actually performs the file pumping
	private FilePumpWorker filePumpWorker = null;
	private Thread filePumpThread = null;
	
	// GUI components
	public boolean bShowGUI = true;
	private JFrame filePumpGuiFrame = null;
	private JLabel outputFolderLabel = null;
	private JLabel filesPerSecLabel = null;
	private JLabel totNumFilesLabel = null;
	private JLabel modeLabel = null;
	private JLabel fileCountLabel = null;
	private JButton actionButton = null;
	
	public FilePumpSettings pumpSettings = null;
	
	public boolean bPumpRunning = false;
	
	public int fileCount = 0;
	
	public static void main(String[] argsI) {
		
		boolean local_bShowGUI = true;
		
		String initial_outputFolder = ".";
	    double initial_filesPerSec = 1.0;
	    int initial_totNumFiles = 1000;
	    String initial_mode_str = "local";
	    String initial_ftpHost;
	    String initial_ftpUser;
		String initial_ftpPassword;
		
		//
		// Parse command line arguments
		//
		// We use the Apche Commons CLI library to handle command line
		// arguments. See https://commons.apache.org/proper/commons-cli/usage.html
		// for examples, although note that we use the more up-to-date form
		// (Option.builder) to create Option objects.
		//
		// 1. Setup command line options
		//
		Options options = new Options();
		// Example of a Boolean option (i.e., only the flag, no argument goes with it)
		options.addOption("h", "help", false, "Print this message");
		// The following example is for: -outputfolder <folder>    Location of output files
		Option outputFolderOption = Option.builder("outputfolder")
                .argName("folder")
                .hasArg()
                .desc("Location of output files; this folder must exist (it will not be created); default = \"" + initial_outputFolder + "\"")
                .build();
		options.addOption(outputFolderOption);
		Option filesPerSecOption = Option.builder("fps")
                .argName("filespersec")
                .hasArg()
                .desc("Desired file rate, files/sec; default = " + initial_filesPerSec)
                .build();
		options.addOption(filesPerSecOption);
		Option totNumFilesOption = Option.builder("totnum")
                .argName("num")
                .hasArg()
                .desc("Total number of output files; use -1 for unlimited number; default = " + initial_totNumFiles)
                .build();
		options.addOption(totNumFilesOption);
		Option outputModeOption = Option.builder("mode")
                .argName("mode")
                .hasArg()
                .desc("Specifies output interface, one of <local|ftp|sftp>; default = " + initial_mode_str)
                .build();
		options.addOption(outputModeOption);
		Option ftpHostOption = Option.builder("ftphost")
                .argName("host")
                .hasArg()
                .desc("Host name, for FTP or SFTP")
                .build();
		options.addOption(ftpHostOption);
		Option ftpUsernameOption = Option.builder("ftpuser")
                .argName("user")
                .hasArg()
                .desc("Username, for FTP or SFTP")
                .build();
		options.addOption(ftpUsernameOption);
		Option ftpPasswordOption = Option.builder("ftppass")
                .argName("password")
                .hasArg()
                .desc("Password, for FTP or SFTP")
                .build();
		options.addOption(ftpPasswordOption);
		Option autoRunOption = new Option("x", "Automatically run at startup");
		options.addOption(autoRunOption);
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
	    	formatter.printHelp( "FilePump", options );
	    	return;
	    }
	    if (line.hasOption("x")) {
	    	local_bShowGUI = false;
	    }
	    // Where to write the files to
	    initial_outputFolder = line.getOptionValue("outputfolder",initial_outputFolder);
	    // How many files per second the pump should output
	    try {
	    	initial_filesPerSec = Double.parseDouble(line.getOptionValue("fps",""+initial_filesPerSec));
	    } catch (NumberFormatException nfe) {
	    	System.err.println("\nError parsing \"fps\" (it should be a floating point value):\n" + nfe);
	    	return;
	    }
	    // Total number of files to write out; -1 indicates unlimited
	    try {
	    	initial_totNumFiles = Integer.parseInt(line.getOptionValue("totnum",""+initial_totNumFiles));
	    } catch (NumberFormatException nfe) {
	    	System.err.println("\nError parsing \"totnum\" (it should be an integer):\n" + nfe);
	    	return;
	    }
	    // Specifies how files will be written out
	    initial_mode_str = line.getOptionValue("mode",initial_mode_str);
	    if ( !initial_mode_str.equals("local") && !initial_mode_str.equals("ftp") && !initial_mode_str.equals("sftp") ) {
	    	System.err.println(new String("\nUnrecognized mode, \"" + initial_mode_str + "\""));
	    	return;
	    }
	    // FTP hostname
	    initial_ftpHost = line.getOptionValue("ftphost","");
	    // FTP username
		initial_ftpUser = line.getOptionValue("ftpuser","");
		// FTP password
		initial_ftpPassword = line.getOptionValue("ftppass","");
	    
		// Create the FilePump object
		new FilePump(local_bShowGUI, initial_outputFolder, initial_filesPerSec, initial_totNumFiles, initial_mode_str, initial_ftpHost, initial_ftpUser, initial_ftpPassword);
		
	}
	
	/*
	 * Constructor
	 * 
	 */
	public FilePump(boolean bShowGUI_I, String default_outputFolderI, double default_filesPerSecI, int default_totNumFilesI, String default_mode_strI, String default_ftpHostI, String default_ftpUserI, String default_ftpPasswordI) {
		
		bShowGUI = bShowGUI_I;
		
		FileMode temp_FileMode = null;
		if (default_mode_strI.equals("local")) {
			temp_FileMode = FileMode.LOCAL_FILESYSTEM;
	    } else if (default_mode_strI.equals("ftp")) {
	    	temp_FileMode = FileMode.FTP;
	    } else if (default_mode_strI.equals("sftp")) {
	    	temp_FileMode = FileMode.SFTP;
	    }
		
		// Save the arguments as final variables so they can be used
		// in the call to createAndShowGUI() in the inner class below.
		final String default_outputFolder = default_outputFolderI;
		final double default_filesPerSec = default_filesPerSecI;
		final int default_totNumFiles = default_totNumFilesI;
		final FileMode default_mode = temp_FileMode;
		final String default_ftpHost = default_ftpHostI;
		final String default_ftpUser = default_ftpUserI;
		final String default_ftpPassword = default_ftpPasswordI;
		
		//
		// Either launch the GUI or automatically start writing output files
		//
		
		if (bShowGUI) {
			// For thread safety: Schedule a job for the event-dispatching
			// thread
			// to create and show this application's GUI.
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					createAndShowGUI(default_outputFolder, default_filesPerSec, default_totNumFiles,
							default_mode, default_ftpHost, default_ftpUser, default_ftpPassword);
				}
			});
		} else {
			// Add a shutdown hook to catch Ctrl+c
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					exit();
				}
			});
			// Create FilePumpSettings
			pumpSettings = new FilePumpSettings(default_outputFolder, default_filesPerSec, default_totNumFiles,
					default_mode, default_ftpHost, default_ftpUser, default_ftpPassword);
			// Start FilePumpWorker
			String errStr = pumpSettings.canPumpRun();
			if (!errStr.isEmpty()) {
				System.err.println("\nUnable to start FilePump due to the following error:\n" + errStr);
				return;
			}
			bPumpRunning = true;
			// Start pump
			filePumpWorker = new FilePumpWorker(this, pumpSettings,false);
			filePumpThread = new Thread(filePumpWorker);
			filePumpThread.start();
		}
		
	}
	
	private void createAndShowGUI(String default_outputFolderI, double default_filesPerSecI, int default_totNumFilesI, FileMode default_modeI, String default_ftpHostI, String default_ftpUserI, String default_ftpPasswordI) {

		// Make sure we have nice window decorations.
		JFrame.setDefaultLookAndFeelDecorated(true);

		// Create the GUI components
		GridBagLayout framegbl = new GridBagLayout();
		filePumpGuiFrame = new JFrame("FilePump");
		GridBagLayout gbl = new GridBagLayout();
		JPanel guiPanel = new JPanel(gbl);
		outputFolderLabel = new JLabel(" ");
		Dimension preferredSize = new Dimension(200, 20);
		outputFolderLabel.setPreferredSize(preferredSize);
		filesPerSecLabel = new JLabel("1");
		totNumFilesLabel = new JLabel("unlimited");
		modeLabel = new JLabel("file system folder");
		fileCountLabel = new JLabel("0");
		actionButton = new JButton("Start");
		actionButton.setBackground(Color.GREEN);
		actionButton.addActionListener(this);

		filePumpGuiFrame.setFont(new Font("Dialog", Font.PLAIN, 12));
		guiPanel.setFont(new Font("Dialog", Font.PLAIN, 12));

		int row = 0;

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;

		// ROW 1
		JLabel label = new JLabel("Output directory");
		gbc.insets = new Insets(15, 15, 0, 5);
		Utility.add(guiPanel, label, gbl, gbc, 0, row, 1, 1);
		gbc.insets = new Insets(15, 0, 0, 15);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 100;
		Utility.add(guiPanel, outputFolderLabel, gbl, gbc, 1, row, 1, 1);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		++row;

		// ROW 2
		label = new JLabel("Files/sec");
		gbc.insets = new Insets(15, 15, 0, 5);
		Utility.add(guiPanel, label, gbl, gbc, 0, row, 1, 1);
		gbc.insets = new Insets(15, 0, 0, 15);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 100;
		Utility.add(guiPanel, filesPerSecLabel, gbl, gbc, 1, row, 1, 1);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		++row;
		
		// ROW 3
		label = new JLabel("Tot num files");
		gbc.insets = new Insets(15, 15, 0, 5);
		Utility.add(guiPanel, label, gbl, gbc, 0, row, 1, 1);
		gbc.insets = new Insets(15, 0, 0, 15);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 100;
		Utility.add(guiPanel, totNumFilesLabel, gbl, gbc, 1, row, 1, 1);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		++row;

		// ROW 4
		label = new JLabel("Mode");
		gbc.insets = new Insets(15, 15, 0, 5);
		Utility.add(guiPanel, label, gbl, gbc, 0, row, 1, 1);
		gbc.insets = new Insets(15, 0, 0, 15);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 100;
		Utility.add(guiPanel, modeLabel, gbl, gbc, 1, row, 1, 1);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		++row;

		// ROW 5
		label = new JLabel("File count");
		gbc.insets = new Insets(15, 15, 0, 5);
		Utility.add(guiPanel, label, gbl, gbc, 0, row, 1, 1);
		gbc.insets = new Insets(15, 0, 0, 15);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 100;
		Utility.add(guiPanel, fileCountLabel, gbl, gbc, 1, row, 1, 1);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		++row;

		// ROW 6: command button
		gbc.insets = new Insets(15, 15, 15, 15);
		gbc.anchor = GridBagConstraints.CENTER;
		Utility.add(guiPanel, actionButton, gbl, gbc, 0, row, 2, 1);
		gbc.anchor = GridBagConstraints.WEST;
		++row;

		// Now add guiPanel to filePumpGuiFrame
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 100;
		gbc.weighty = 100;
		gbc.insets = new Insets(0, 0, 0, 0);
		Utility.add(filePumpGuiFrame, guiPanel, framegbl, gbc, 0, 0, 1, 1);
		
		// Add menu
		JMenuBar menuBar = createMenu();
		filePumpGuiFrame.setJMenuBar(menuBar);
		
		// Display the window.
		filePumpGuiFrame.pack();

		filePumpGuiFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		filePumpGuiFrame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				exit();
			}
		});
		
		// Create a settings dialog box
		pumpSettings = new FilePumpSettings(filePumpGuiFrame, default_outputFolderI, default_filesPerSecI, default_totNumFilesI, default_modeI, default_ftpHostI, default_ftpUserI, default_ftpPasswordI);
		
		// Initialize information displayed on the GUI front panel
		updateMainFrame();
		
		filePumpGuiFrame.setVisible(true);
		
	}
	
	public JMenuBar createMenu() {
		JMenuBar menuBar = new JMenuBar();
		JMenu menu = new JMenu("File");
		menuBar.add(menu);
		JMenuItem menuItem = new JMenuItem("Settings...");
		menu.add(menuItem);
		menuItem.addActionListener(this);
		menuItem = new JMenuItem("Create \"end.txt\" file");
		menu.add(menuItem);
		menuItem.addActionListener(this);
		menuItem = new JMenuItem("Exit");
		menu.add(menuItem);
		menuItem.addActionListener(this);
		return menuBar;
	}
	
	/*
	 * Update the main frame based on current settings
	 */
	private void updateMainFrame() {
		outputFolderLabel.setText(new String("\"" + pumpSettings.getOutputFolder() + "\""));
		filesPerSecLabel.setText(Double.toString(pumpSettings.getFilesPerSec()));
		if ( (pumpSettings.getTotNumFiles() == Integer.MAX_VALUE) || (pumpSettings.getTotNumFiles() == -1) ) {
			totNumFilesLabel.setText("unlimited");
		} else {
			totNumFilesLabel.setText(Integer.toString(pumpSettings.getTotNumFiles()));
		}
		if (pumpSettings.getMode() == FilePumpSettings.FileMode.LOCAL_FILESYSTEM) {
			modeLabel.setText("Write to local filesystem");
			if ( (pumpSettings.getOutputFolder() != null) && (!pumpSettings.getOutputFolder().isEmpty()) ) {
				modeLabel.setText("Write to \"" + pumpSettings.getOutputFolder() + "\"");
			}
		} else if (pumpSettings.getMode() == FilePumpSettings.FileMode.FTP) {
			modeLabel.setText("FTP");
			if ( (pumpSettings.getFTPHost() != null) && (!pumpSettings.getFTPHost().isEmpty()) && (pumpSettings.getOutputFolder() != null) && (!pumpSettings.getOutputFolder().isEmpty()) ) {
				modeLabel.setText("FTP to " + pumpSettings.getFTPHost() + ", path = \"" + pumpSettings.getOutputFolder() + "\"");
			}
		} else if (pumpSettings.getMode() == FilePumpSettings.FileMode.SFTP) {
			modeLabel.setText("SFTP");
			if ( (pumpSettings.getFTPHost() != null) && (!pumpSettings.getFTPHost().isEmpty()) && (pumpSettings.getOutputFolder() != null) && (!pumpSettings.getOutputFolder().isEmpty()) ) {
				modeLabel.setText("SFTP to " + pumpSettings.getFTPHost() + ", path = \"" + pumpSettings.getOutputFolder() + "\"");
			}
		}
		fileCountLabel.setText("0");
	}
	
	public void actionPerformed(ActionEvent eventI) {
		
		Object source = eventI.getSource();

		if (source == null) {
			return;
		} else if (eventI.getActionCommand().equals("Settings...")) {
			// Turn off pump if it is currently running
			resetGUI_EDT();
			stopFilePumpWorkerThread();
			// Let user edit settings
			pumpSettings.popupSettingsDialog();
			if (pumpSettings.getBClickedOK()) {
				String errStr = pumpSettings.canPumpRun();
				if (!errStr.isEmpty()) {
					JOptionPane.showMessageDialog(filePumpGuiFrame, errStr, "Settings error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				// Update the main frame with the new settings
				updateMainFrame();
			}
		} else if (eventI.getActionCommand().equals("Create \"end.txt\" file")) {
			// Turn off pump if it is currently running
			resetGUI_EDT();
			stopFilePumpWorkerThread();
			// Create an "end.txt" file in the output directory
			// Make sure we can do this
			String errStr = pumpSettings.canPumpRun();
			if (!errStr.isEmpty()) {
				JOptionPane.showMessageDialog(filePumpGuiFrame, new String("Settings error:\n" + errStr), "Settings error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			filePumpWorker = new FilePumpWorker(this, pumpSettings,true);
			filePumpThread = new Thread(filePumpWorker);
			filePumpThread.start();
		} else if (eventI.getActionCommand().equals("Exit")) {
			exit();
		} else if (source == actionButton) {
			// Turn on or off streaming data
			if (actionButton.getText().equals("Start")) {
				// User wants to start outputting files; make sure we can do this
				String errStr = pumpSettings.canPumpRun();
				if (!errStr.isEmpty()) {
					JOptionPane.showMessageDialog(filePumpGuiFrame, new String("Settings error:\n" + errStr), "Settings error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				bPumpRunning = true;
				// Change label to "Stop"
				actionButton.setText("Stop");
				actionButton.setBackground(Color.RED);
				// Start pump
				filePumpWorker = new FilePumpWorker(this, pumpSettings,false);
				filePumpThread = new Thread(filePumpWorker);
				filePumpThread.start();
			} else {
				resetGUI_EDT();
				stopFilePumpWorkerThread();
			}
		}
		
	}
	
	/*
	 * Update the file count label
	 * 
	 * This method doesn't need to be called on the Event Dispatch Thread;
	 * a call to updateNumFiles_EDT which is running on the EDT will be scheduled.
	 * 
	 */
	public void updateNumFiles_nonEDT(int countI) {
		fileCount = countI;
		if (!bShowGUI) {
			System.err.print(".");
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					updateNumFiles_EDT();
				}
			});
		}
	}
	
	/*
	 * Update the file count label
	 * 
	 * THIS METHOD SHOULD BE EXECUTED ON THE EVENT DISPATCH THREAD.
	 * 
	 */
	public void updateNumFiles_EDT() {
		if (EventQueue.isDispatchThread()) {
			fileCountLabel.setText(Integer.toString(fileCount));
		}
	}
	
	/*
	 * Reset the GUI.
	 * 
	 * This method doesn't need to be called on the Event Dispatch Thread;
	 * a call to the resetGUI method which is running on the EDT will be scheduled.
	 * 
	 */
	public void resetGUI_nonEDT() {
		if (bShowGUI) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					resetGUI_EDT();
				}
			});
		}
	}
	
	/*
	 * Reset the GUI.
	 * 
	 * THIS METHOD SHOULD BE EXECUTED ON THE EVENT DISPATCH THREAD.
	 * 
	 */
	private void resetGUI_EDT() {
		if (EventQueue.isDispatchThread()) {
			// Signal FilePumpWorker to turn off
			bPumpRunning = false;
			fileCount = 0;
			updateNumFiles_EDT();
			// Change label back to "Start"
			actionButton.setText("Start");
			actionButton.setBackground(Color.GREEN);
		}
	}
	
	/*
	 * Stop the FilePumpWorker thread.
	 */
	private void stopFilePumpWorkerThread() {
		bPumpRunning = false;
		if ( (filePumpThread != null) && (filePumpThread.isAlive()) ) {
			try {
				filePumpThread.join(2000);
				if (filePumpThread.isAlive()) {
					// The thread is still alive; interrupt it and wait again.
					filePumpThread.interrupt();
					filePumpThread.join(5000);
				}
			} catch (InterruptedException e) {
				// Nothing to do
			}
		}
	}
	
	/*
	 * Stop the program.
	 */
	private void exit() {
		if (bShowGUI) {
			filePumpGuiFrame.setVisible(false);
		}
		stopFilePumpWorkerThread();
		System.err.println("FilePump cleanup done; exit.");
		// For some reason, calling exit when we are running headless doesn't terminate the program, it just keeps on;
		// therefore, we will only call exit when using GUI
		if (bShowGUI) {
			System.exit(0);
		}
	}
	
}
