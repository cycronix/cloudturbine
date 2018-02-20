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

package erigo.ctstream;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.*;

import erigo.ctstream.CTstream.CTWriteMode;

/**
 *
 * Dialog for CTstream settings.
 *
 * These settings are mainly for CTwriter or overall program settings.
 *
 * @author John P. Wilson
 * @version 02/19/2018
 *
 */

public class CTsettings extends JDialog implements ActionListener,ItemListener {
    
	private static final long serialVersionUID = 1L;
	
	private static final String[] flushIntervalStrings = { "0.1sec", "0.2s", "0.5s", "1s", "2s", "5s", "10s", "30s", "1min", "2min", "5min", "10min" };
	public static final Long[] flushIntervalLongs = { new Long(100), new Long(200), new Long(500), new Long(1000), new Long(2000), new Long(5000), new Long(10000), new Long(30000), new Long(60000), new Long(120000), new Long(300000), new Long(600000) };
	
	private static final String[] numBlocksPerSegmentStrings = { "No segments", "10", "20", "60", "120", "300", "600", "1800", "3600" };
	public static final Long[] numBlocksPerSegmentLongs = { new Long(0), new Long(10), new Long(20), new Long(60), new Long(120), new Long(300), new Long(600), new Long(1800), new Long(3600) };
	
	CTstream ctStream = null;
	JFrame parentFrame = null;
	
	// Backup copy of the settings
	private String orig_outputFolder;
	private String orig_sourceName;
	private String orig_screencapChannelName;
	private String orig_webcamChannelName;
	private String orig_audioChannelName;
	private String orig_textChannelName;
	private boolean orig_bEncrypt;
	private String orig_encryptionPassword;
	private CTWriteMode orig_writeMode;
	private String orig_serverHost;
	private String orig_serverUser;
	private String orig_serverPassword;
	private long orig_autoFlushMillis;
	private long orig_numBlocksPerSegment;
	private boolean orig_bDebugMode;
	// private boolean orig_bIncludeMouseCursor;
	private boolean orig_bStayOnTop;
	
	// Dialog components
	private JTextField sourceNameTF = null;
	private JTextField screencapChannelNameTF = null;
	private JTextField webcamChannelNameTF = null;
	private JTextField audioChannelNameTF = null;
	private JTextField textChannelNameTF = null;
	private JCheckBox bEncryptCheckB = null;
	private JLabel encryptionPasswordLabel = null;
	private JPasswordField encryptionPasswordTF = null;
	private ButtonGroup writeModeBG = new ButtonGroup();
	private JRadioButton localRB = null;
	private JRadioButton ftpRB = null;
	private JRadioButton httpRB = null;
	private JRadioButton httpsRB = null;
	// controls related to different modes of writing output (e.g., local, FTP, etc.)
	private JLabel outputFolderLabel = null;
	private JTextField outputFolderTF = null;
	private JButton browseButton = null;
	private JLabel serverHostLabel = null;
	private JTextField serverHostTF = null;
	private JLabel serverUserLabel = null;
	private JTextField serverUserTF = null;
	private JLabel serverPasswordLabel = null;
	private JPasswordField serverPasswordTF = null;
	private JComboBox<String> flushIntervalComboB = null;
	private JComboBox<String> numBlocksPerSegmentComboB = null;
	private JCheckBox bDebugModeCheckB = null;
	private JCheckBox bStayOnTopCheckB = null;
	private JButton okButton = null;
	private JButton cancelButton = null;
	
	/*
	 * Constructor
	 * 
	 * Create the dialog but don't display it (gets displayed when user selects "Settings..." menu item)
	 * 
	 */
	public CTsettings(CTstream ctStreamI, JFrame parentFrameI) {
		
		super(parentFrameI,"CTstream Settings",true);
		
		ctStream = ctStreamI;
		parentFrame = parentFrameI;
		
		setFont(new Font("Dialog", Font.PLAIN, 12));
		GridBagLayout gbl = new GridBagLayout();
		JPanel guiPanel = new JPanel(gbl);
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		
		// Create GUI components
		sourceNameTF = new JTextField(25);
		screencapChannelNameTF = new JTextField(10);
		webcamChannelNameTF = new JTextField(10);
		audioChannelNameTF = new JTextField(10);
		textChannelNameTF = new JTextField(10);
		bEncryptCheckB = new JCheckBox("Encrypt data");
		bEncryptCheckB.addItemListener(this);
		encryptionPasswordLabel = new JLabel("Password",SwingConstants.LEFT);
		encryptionPasswordTF = new JPasswordField(15);
		localRB = new JRadioButton("Local");
		writeModeBG.add(localRB);
		localRB.addActionListener(this);
		ftpRB = new JRadioButton("FTP");
		writeModeBG.add(ftpRB);
		ftpRB.addActionListener(this);
		httpRB = new JRadioButton("HTTP");
		writeModeBG.add(httpRB);
		httpRB.addActionListener(this);
		httpsRB = new JRadioButton("HTTPS");
		writeModeBG.add(httpsRB);
		httpsRB.addActionListener(this);
		outputFolderLabel = new JLabel("Output folder",SwingConstants.LEFT);
		outputFolderTF = new JTextField(25);
		browseButton = new JButton("Browse...");
		browseButton.addActionListener(this);
		serverHostLabel = new JLabel("Host",SwingConstants.LEFT);
		serverHostTF = new JTextField(20);
		// httpServerHintLabel = new JLabel("e.g., http://localhost:8000",SwingConstants.LEFT);
		// httpServerHintLabel.setFont(new Font(httpServerHintLabel.getFont().getName(),Font.ITALIC, httpServerHintLabel.getFont().getSize()));
		// httpServerHintLabel.setForeground(Color.red);
		serverUserLabel = new JLabel("Username",SwingConstants.LEFT);
		serverUserTF = new JTextField(15);
		serverPasswordLabel = new JLabel("Password",SwingConstants.LEFT);
		serverPasswordTF = new JPasswordField(15);
		flushIntervalComboB = new JComboBox<String>(flushIntervalStrings);
		flushIntervalComboB.setPrototypeDisplayValue("XXXXXXXXXXXXXXXXXXX");
		numBlocksPerSegmentComboB = new JComboBox<String>(numBlocksPerSegmentStrings);
		numBlocksPerSegmentComboB.setPrototypeDisplayValue("XXXXXXXXXXXX");
		flushIntervalComboB.setEditable(false);
		bDebugModeCheckB = new JCheckBox("Turn on CT debug");
		bStayOnTopCheckB = new JCheckBox("Keep UI on top");
		okButton = new JButton("OK");
		okButton.addActionListener(this);
		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(this);
		
		// Add components to the guiPanel
		int row = 0;
		
		// source name
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		JLabel tempLabel = new JLabel("Source name",SwingConstants.LEFT);
		gbc.insets = new Insets(10,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,sourceNameTF,gbl,gbc,1,row,1,1);
		row++;
		
		// screencap channel name
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		tempLabel = new JLabel("Screencap channel name",SwingConstants.LEFT);
		gbc.insets = new Insets(10,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,screencapChannelNameTF,gbl,gbc,1,row,1,1);
		row++;

		// webcam channel name
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		tempLabel = new JLabel("Webcam channel name",SwingConstants.LEFT);
		gbc.insets = new Insets(10,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,webcamChannelNameTF,gbl,gbc,1,row,1,1);
		row++;
		
		// audio channel name
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		tempLabel = new JLabel("Audio channel name",SwingConstants.LEFT);
		gbc.insets = new Insets(10,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,audioChannelNameTF,gbl,gbc,1,row,1,1);
		row++;

		// text channel name
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		tempLabel = new JLabel("Text channel name",SwingConstants.LEFT);
		gbc.insets = new Insets(10,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,textChannelNameTF,gbl,gbc,1,row,1,1);
		row++;

		// data encryption option
		GridBagLayout panel_gbl = new GridBagLayout();
		JPanel encryptionPanel = new JPanel(panel_gbl);
		GridBagConstraints panel_gbc = new GridBagConstraints();
		panel_gbc.anchor = GridBagConstraints.WEST;
		panel_gbc.fill = GridBagConstraints.NONE;
		panel_gbc.weightx = 0;
		panel_gbc.weighty = 0;
		panel_gbc.insets = new Insets(0,0,0,10);
		Utility.add(encryptionPanel,bEncryptCheckB,panel_gbl,panel_gbc,0,0,1,1);
		panel_gbc.insets = new Insets(0,0,0,5);
		Utility.add(encryptionPanel,encryptionPasswordLabel,panel_gbl,panel_gbc,1,0,1,1);
		panel_gbc.insets = new Insets(0,0,0,0);
		Utility.add(encryptionPanel,encryptionPasswordTF,panel_gbl,panel_gbc,2,0,1,1);
		gbc.insets = new Insets(10,15,0,15);
		Utility.add(guiPanel,encryptionPanel,gbl,gbc,0,row,3,1);
		row++;

		// write mode
		JPanel writeModeRBPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,15,0));
		writeModeRBPanel.add(localRB);
		writeModeRBPanel.add(ftpRB);
		writeModeRBPanel.add(httpRB);
		writeModeRBPanel.add(httpsRB);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		// Since we have set the FlowLayout hgap to 15, don't have any padding on left or right
		gbc.insets = new Insets(10,0,0,0);
		Utility.add(guiPanel,writeModeRBPanel,gbl,gbc,0,row,3,1);
		row++;
		
		// panel containing the write output mode parameters (folder, host, username, password)
		panel_gbl = new GridBagLayout();
		JPanel writeModePanel = new JPanel(panel_gbl);
		panel_gbc = new GridBagConstraints();
		panel_gbc.anchor = GridBagConstraints.WEST;
		panel_gbc.fill = GridBagConstraints.NONE;
		panel_gbc.weightx = 0;
		panel_gbc.weighty = 0;
		int panel_row = 0;
		// Panel row 1: output folder
		panel_gbc.insets = new Insets(0,0,0,10);
		Utility.add(writeModePanel,outputFolderLabel,panel_gbl,panel_gbc,0,panel_row,1,1);
		panel_gbc.insets = new Insets(0,0,0,10);
		panel_gbc.fill = GridBagConstraints.HORIZONTAL;
		panel_gbc.weightx = 100;
		panel_gbc.weighty = 0;
		Utility.add(writeModePanel,outputFolderTF,panel_gbl,panel_gbc,1,panel_row,1,1);
		panel_gbc.fill = GridBagConstraints.NONE;
		panel_gbc.weightx = 0;
		panel_gbc.weighty = 0;
		Utility.add(writeModePanel,browseButton,panel_gbl,panel_gbc,2,panel_row,1,1);
		panel_row++;
		// Panel row 2: host
		panel_gbc.insets = new Insets(4,0,0,10);
		Utility.add(writeModePanel,serverHostLabel,panel_gbl,panel_gbc,0,panel_row,1,1);
		// JPW 2018-02-20  No longer use httpServerHintLabel
		// put serverHostTF and httpServerHintLabel in their own panel
		// FlowLayout panelLayout = new FlowLayout(FlowLayout.LEFT,0,0);
		// JPanel hostPanel = new JPanel(panelLayout);
		// hostPanel.add(serverHostTF);
		// // add some empty space between the text field and the label
		// hostPanel.add(Box.createRigidArea(new Dimension(5, 0)));
		// hostPanel.add(httpServerHintLabel);
		// panel_gbc.insets = new Insets(4,0,0,0);
		// Utility.add(writeModePanel,hostPanel,panel_gbl,panel_gbc,1,panel_row,2,1);
		panel_gbc.insets = new Insets(4,0,0,0);
		Utility.add(writeModePanel,serverHostTF,panel_gbl,panel_gbc,1,panel_row,1,1);
		panel_row++;
		// Panel row 3: username
		panel_gbc.insets = new Insets(7,0,0,10);
		Utility.add(writeModePanel,serverUserLabel,panel_gbl,panel_gbc,0,panel_row,1,1);
		panel_gbc.insets = new Insets(7,0,0,0);
		Utility.add(writeModePanel,serverUserTF,panel_gbl,panel_gbc,1,panel_row,1,1);
		panel_row++;
		// Panel row 4: password
		panel_gbc.insets = new Insets(7,0,0,10);
		Utility.add(writeModePanel,serverPasswordLabel,panel_gbl,panel_gbc,0,panel_row,1,1);
		panel_gbc.insets = new Insets(7,0,0,0);
		Utility.add(writeModePanel,serverPasswordTF,panel_gbl,panel_gbc,1,panel_row,1,1);
		// Add the panel to guiPanel
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 0;
		gbc.insets = new Insets(0,50,0,15);
		Utility.add(guiPanel,writeModePanel,gbl,gbc,0,row,3,1);
		row++;
		
		// flush interval
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,15,0,10);
		tempLabel = new JLabel("Flush interval",SwingConstants.LEFT);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,flushIntervalComboB,gbl,gbc,1,row,1,1);
		row++;
		
		// num blocks per segment
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,15,0,10);
		tempLabel = new JLabel("Num blocks per segment",SwingConstants.LEFT);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,numBlocksPerSegmentComboB,gbl,gbc,1,row,1,1);
		row++;
		
		// debug checkbox
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,15,0,15);
		Utility.add(guiPanel,bDebugModeCheckB,gbl,gbc,0,row,3,1);
		row++;
		
		// stay on top checkbox
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,15,0,15);
		Utility.add(guiPanel,bStayOnTopCheckB,gbl,gbc,0,row,3,1);
		row++;
		
		// OK/Cancel command buttons
		// Put the command buttons in a JPanel so they are all the same size
        JPanel buttonPanel = new JPanel(new GridLayout(1,2,15,0));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        // Don't have the buttons resize if the dialog is resized
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.ipadx = 20;
        gbc.insets = new Insets(15,25,15,25);
        gbc.anchor = GridBagConstraints.CENTER;
        Utility.add(guiPanel,buttonPanel,gbl,gbc,0,row,3,1);
		
		// Add guiPanel to the content pane of the parent JFrame
		gbl = new GridBagLayout();
		getContentPane().setLayout(gbl);
		gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 100;
		gbc.weighty = 100;
		gbc.insets = new Insets(0,0,0,0);
		Utility.add(getContentPane(),guiPanel,gbl,gbc,0,0,1,1);
		
		pack();
		
		// Handle the close operation in the windowClosing() method of the
		// registered WindowListener object.  This will get around
		// JFrame's default behavior of automatically hiding the window when
		// the user clicks on the '[x]' button.
		setDefaultCloseOperation(
			javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		
		addWindowListener(
			new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					cancelAction();
				}
			});

	}
	
	public void popupSettingsDialog() {
		
		// Squirrel away copies of the current data
		orig_sourceName = ctStream.sourceName;
		orig_screencapChannelName = ctStream.screencapStreamName;
		orig_webcamChannelName = ctStream.webcamStreamName;
		orig_audioChannelName = ctStream.audioStreamName;
		orig_textChannelName = ctStream.textStreamName;
		orig_bEncrypt = ctStream.bEncrypt;
		orig_encryptionPassword = ctStream.encryptionPassword;
		orig_writeMode = ctStream.writeMode;
		orig_outputFolder = ctStream.outputFolder;
		orig_serverHost = ctStream.serverHost;
		orig_serverUser = ctStream.serverUser;
		orig_serverPassword = ctStream.serverPassword;
		orig_autoFlushMillis = ctStream.flushMillis;
		orig_numBlocksPerSegment = ctStream.numBlocksPerSegment;
		orig_bDebugMode = ctStream.bDebugMode;
		// orig_bIncludeMouseCursor = ctStream.bIncludeMouseCursor;
		orig_bStayOnTop = ctStream.bStayOnTop;
		
		// Initialize data in the dialog
		sourceNameTF.setText(ctStream.sourceName);
		screencapChannelNameTF.setText(ctStream.screencapStreamName);
		webcamChannelNameTF.setText(ctStream.webcamStreamName);
		audioChannelNameTF.setText(ctStream.audioStreamName);
		textChannelNameTF.setText(ctStream.textStreamName);
		bEncryptCheckB.setSelected(ctStream.bEncrypt);
		encryptionPasswordTF.setText(ctStream.encryptionPassword);
		encryptionPasswordLabel.setEnabled(ctStream.bEncrypt);
		encryptionPasswordTF.setEnabled(ctStream.bEncrypt);
		// settings associated with write mode
		if (ctStream.writeMode == CTWriteMode.LOCAL) {
			localRB.setSelected(true);
			setOutputModeControls(CTWriteMode.LOCAL);
		} else if (ctStream.writeMode == CTWriteMode.FTP) {
			ftpRB.setSelected(true);
			setOutputModeControls(CTWriteMode.FTP);
		} else if (ctStream.writeMode == CTWriteMode.HTTP) {
			httpRB.setSelected(true);
			setOutputModeControls(CTWriteMode.HTTP);
		} else if (ctStream.writeMode == CTWriteMode.HTTPS) {
			httpsRB.setSelected(true);
			setOutputModeControls(CTWriteMode.HTTPS);
		}
		outputFolderTF.setText(ctStream.outputFolder);
		serverHostTF.setText(ctStream.serverHost);
		serverUserTF.setText(ctStream.serverUser);
		serverPasswordTF.setText(ctStream.serverPassword);
		int selectedIdx = Arrays.asList(flushIntervalLongs).indexOf(ctStream.flushMillis);
		if (selectedIdx == -1) {
			selectedIdx = 1;
		}
		flushIntervalComboB.setSelectedIndex(selectedIdx);
		selectedIdx = Arrays.asList(numBlocksPerSegmentLongs).indexOf(ctStream.numBlocksPerSegment);
		if (selectedIdx == -1) {
			selectedIdx = 0;
		}
		numBlocksPerSegmentComboB.setSelectedIndex(selectedIdx);
		bDebugModeCheckB.setSelected(ctStream.bDebugMode);
		bStayOnTopCheckB.setSelected(ctStream.bStayOnTop);
		
		// Pop up the dialog centered on the parent frame
		pack();
		setLocationRelativeTo(parentFrame);
		setVisible(true); // this method won't return until setVisible(false) is called
		
	}
	
	/*
	 * Respond to the state of bEncryptCheckB changing:
	 * Enable or disable the corresponding fields based on the state of the checkbox.
	 */
	@Override
	public void itemStateChanged(ItemEvent eventI) {
		
		Object source = eventI.getSource();
		
		if (source == null) {
			return;
		} else if (source == bEncryptCheckB) {
			boolean bChecked = bEncryptCheckB.isSelected();
			encryptionPasswordLabel.setEnabled(bChecked);
			encryptionPasswordTF.setEnabled(bChecked);
		}
		
	}
	
	/*
	 * Handle callbacks from buttons and the FTP/HTTP checkboxes.
	 * 
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 * 
	 */
	@Override
	public void actionPerformed(ActionEvent eventI) {
		
		Object source = eventI.getSource();
		
		if (source == null) {
			return;
		} else if (eventI.getActionCommand().equals("OK")) {
			okAction();
		} else if (eventI.getActionCommand().equals("Cancel")) {
			cancelAction();
		} else if (source == localRB) {
			// User has specified to write output data to local filesystem.
			setOutputModeControls(CTWriteMode.LOCAL);
		} else if (source == ftpRB) {
			// User has specified to write output data to an FTP server.
			setOutputModeControls(CTWriteMode.FTP);
		} else if (source == httpRB) {
			// User has specified to write output data to an HTTP server.
			setOutputModeControls(CTWriteMode.HTTP);
		} else if (source == httpsRB) {
			// User has specified to write output data to an HTTPS server.
			setOutputModeControls(CTWriteMode.HTTPS);
		} else if (eventI.getActionCommand().equals("Browse...")) {
			// Code largely taken from http://stackoverflow.com/questions/4779360/browse-for-folder-dialog
			JFileChooser fc = new JFileChooser();
			String currentPath = ".";
			String currentOutputFolder = outputFolderTF.getText();
			File currentOutputFolderFileObj = new File(currentOutputFolder);
			if (!currentOutputFolder.isEmpty() && currentOutputFolderFileObj.exists()) {
				currentPath = currentOutputFolder;
			}
			fc.setCurrentDirectory(new java.io.File(currentPath));
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int returnVal = fc.showSaveDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
			    File selectedFolder = fc.getSelectedFile();
				String fullPathStr = null;
				try {
					fullPathStr = selectedFolder.getCanonicalPath();
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
				if (fullPathStr.endsWith(File.separator)) {
					fullPathStr = fullPathStr.substring(0,fullPathStr.length()-1);
				}
				// The selected folder should exist
				if (!selectedFolder.exists()) {
					// Folder doesn't exist; could be caused by a MacOS "bug" whereby it doubly-adds the final folder
					int finalSlashIdx = fullPathStr.lastIndexOf(File.separator);
					if (finalSlashIdx > 0) {
						String finalFolderPart = fullPathStr.substring(finalSlashIdx + 1);
						if (fullPathStr.length() > 2*finalFolderPart.length()) {
							String priorFolderPart = fullPathStr.substring(finalSlashIdx - finalFolderPart.length(), finalSlashIdx);
							if (finalFolderPart.equals(priorFolderPart)) {
								// We've got a duplicate ending!
								fullPathStr = fullPathStr.substring(0, finalSlashIdx);
							}
						}
					}
				}
				outputFolderTF.setText(fullPathStr);
			}
		}
		
	}

	/*
	 * Set the enabled state and visibility of controls related to the output write mode.
	 *
	 * This method should be called on the Swing Event Dispatch Thread (EDT).
	 */
	private void setOutputModeControls(CTWriteMode writeModeI) {
		boolean bEnableFolder = true;
		boolean bEnableHost = true;
		boolean bEnableUsernamePassword = true;
		if (writeModeI == CTWriteMode.LOCAL) {
			bEnableFolder = true;
			bEnableHost = false;
			bEnableUsernamePassword = false;
			serverHostLabel.setText("Host");
		} else if (writeModeI == CTWriteMode.FTP) {
			bEnableFolder = false;
			bEnableHost = true;
			bEnableUsernamePassword = true;
			serverHostLabel.setText("Host");
		} else if (writeModeI == CTWriteMode.HTTP) {
			bEnableFolder = false;
			bEnableHost = true;
			bEnableUsernamePassword = false;
			serverHostLabel.setText("Host:Port");
		} else if (writeModeI == CTWriteMode.HTTPS) {
			bEnableFolder = false;
			bEnableHost = true;
			bEnableUsernamePassword = true;
			serverHostLabel.setText("Host:Port");
		}
		outputFolderLabel.setEnabled(bEnableFolder);
		outputFolderTF.setEnabled(bEnableFolder);
		browseButton.setEnabled(bEnableFolder);
		serverHostLabel.setEnabled(bEnableHost);
		serverHostTF.setEnabled(bEnableHost);
		serverUserLabel.setEnabled(bEnableUsernamePassword);
		serverUserTF.setEnabled(bEnableUsernamePassword);
		serverPasswordLabel.setEnabled(bEnableUsernamePassword);
		serverPasswordTF.setEnabled(bEnableUsernamePassword);
	}
	
	/*
	 * User has hit the OK button; save and check data.
	 */
	public void okAction() {
		
		// Save data from the dialog
		ctStream.sourceName = sourceNameTF.getText().trim();
		ctStream.screencapStreamName = screencapChannelNameTF.getText().trim();
		ctStream.webcamStreamName = webcamChannelNameTF.getText().trim();
		ctStream.audioStreamName = audioChannelNameTF.getText().trim();
		ctStream.textStreamName = textChannelNameTF.getText().trim();
		ctStream.bEncrypt = bEncryptCheckB.isSelected();
		char[] encryptPasswordCharArray = encryptionPasswordTF.getPassword();
		ctStream.encryptionPassword = new String(encryptPasswordCharArray).trim();
		// set the output mode
		if (localRB.isSelected()) {
			ctStream.writeMode = CTWriteMode.LOCAL;
		} else if (ftpRB.isSelected()) {
			ctStream.writeMode = CTWriteMode.FTP;
		} else if (httpRB.isSelected()) {
			ctStream.writeMode = CTWriteMode.HTTP;
		} else if (httpsRB.isSelected()) {
			ctStream.writeMode = CTWriteMode.HTTPS;
		}
		ctStream.outputFolder = outputFolderTF.getText().trim();
		ctStream.serverHost = serverHostTF.getText().trim();
		// server name should not end in "/"
		if (ctStream.serverHost.endsWith("/")) {
			ctStream.serverHost = ctStream.serverHost.substring(0,ctStream.serverHost.length()-1);
		}
		ctStream.serverUser = serverUserTF.getText().trim();
		char[] serverPasswordCharArray = serverPasswordTF.getPassword();
		ctStream.serverPassword = new String(serverPasswordCharArray).trim();
		ctStream.flushMillis = flushIntervalLongs[flushIntervalComboB.getSelectedIndex()];
		ctStream.numBlocksPerSegment = numBlocksPerSegmentLongs[numBlocksPerSegmentComboB.getSelectedIndex()];
		ctStream.bDebugMode = bDebugModeCheckB.isSelected();
		ctStream.bStayOnTop = bStayOnTopCheckB.isSelected();
		if (ctStream.bStayOnTop) {
			parentFrame.setAlwaysOnTop(true);
		} else {
			parentFrame.setAlwaysOnTop(false);
		}
		
		// Check data
		String errStr = ctStream.canCTrun();
		if (!errStr.isEmpty()) {
			// Was a problem with the data the user entered
			JOptionPane.showMessageDialog(this, errStr, "CTstream settings error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		// Pop down the dialog
		setVisible(false);
		
	}
	
	/*
	 * User has hit the Cancel button.  Restore original data.
	 */
	public void cancelAction() {
		
		// Restore original values
		ctStream.outputFolder = orig_outputFolder;
		ctStream.sourceName = orig_sourceName;
		ctStream.screencapStreamName = orig_screencapChannelName;
		ctStream.webcamStreamName = orig_webcamChannelName;
		ctStream.audioStreamName = orig_audioChannelName;
		ctStream.textStreamName = orig_textChannelName;
		ctStream.bEncrypt = orig_bEncrypt;
		ctStream.encryptionPassword = orig_encryptionPassword;
		ctStream.writeMode = orig_writeMode;
		ctStream.serverHost = orig_serverHost;
		ctStream.serverUser = orig_serverUser;
		ctStream.serverPassword = orig_serverPassword;
		ctStream.flushMillis = orig_autoFlushMillis;
		ctStream.numBlocksPerSegment = orig_numBlocksPerSegment;
		ctStream.bDebugMode = orig_bDebugMode;
		// ctStream.bIncludeMouseCursor = orig_bIncludeMouseCursor;
		ctStream.bStayOnTop = orig_bStayOnTop;
		
		// Pop down the dialog
		setVisible(false);
	}
	
}
