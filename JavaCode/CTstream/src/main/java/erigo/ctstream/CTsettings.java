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

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 *
 * Dialog so the user can edit CTstream settings.
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
	private String orig_channelName;
	private String orig_audioChannelName;
	private boolean orig_bFTP;
	private String orig_ftpHost;
	private String orig_ftpUser;
	private String orig_ftpPassword;
	private long orig_autoFlushMillis;
	private long orig_numBlocksPerSegment;
	private boolean orig_bDebugMode;
	private boolean orig_bIncludeMouseCursor;
	private boolean orig_bStayOnTop;
	
	// Dialog components
	private JTextField outputFolderTF = null;
	private JTextField sourceNameTF = null;
	private JTextField channelNameTF = null;
	private JTextField audioChannelNameTF = null;
	private JCheckBox bFTPCheckB = null;
	private JLabel ftpHostLabel = null;
	private JTextField ftpHostTF = null;
	private JLabel ftpUserLabel = null;
	private JTextField ftpUserTF = null;
	private JLabel ftpPasswordLabel = null;
	private JPasswordField ftpPasswordTF = null;
	private JComboBox<String> flushIntervalComboB = null;
	private JComboBox<String> numBlocksPerSegmentComboB = null;
	private JCheckBox bDebugModeCheckB = null;
	private JCheckBox bIncludeMouseCursorCheckB = null;
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
		outputFolderTF = new JTextField(25);
		sourceNameTF = new JTextField(25);
		channelNameTF = new JTextField(10);
		audioChannelNameTF = new JTextField(10);
		bFTPCheckB = new JCheckBox("Use FTP");
		bFTPCheckB.addItemListener(this);
		ftpHostLabel = new JLabel("Host",SwingConstants.LEFT);
		ftpHostTF = new JTextField(20);
		ftpUserLabel = new JLabel("Username",SwingConstants.LEFT);
		ftpUserTF = new JTextField(15);
		ftpPasswordLabel = new JLabel("Password",SwingConstants.LEFT);
		ftpPasswordTF = new JPasswordField(15);
		flushIntervalComboB = new JComboBox<String>(flushIntervalStrings);
		flushIntervalComboB.setPrototypeDisplayValue("XXXXXXXXXXXXXXXXXXX");
		numBlocksPerSegmentComboB = new JComboBox<String>(numBlocksPerSegmentStrings);
		numBlocksPerSegmentComboB.setPrototypeDisplayValue("XXXXXXXXXXXX");
		flushIntervalComboB.setEditable(false);
		bDebugModeCheckB = new JCheckBox("Turn on CT debug");
		bIncludeMouseCursorCheckB = new JCheckBox("Include cursor in screen capture");
		bStayOnTopCheckB = new JCheckBox("Keep UI on top");
		okButton = new JButton("OK");
		okButton.addActionListener(this);
		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(this);
		
		// Add components to the guiPanel
		int row = 0;
		
		// ROW 1 - output folder
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		JLabel tempLabel = new JLabel("Output folder",SwingConstants.LEFT);
		gbc.insets = new Insets(15,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(15,0,0,15);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 0;
		Utility.add(guiPanel,outputFolderTF,gbl,gbc,1,row,1,1);
		row++;
		
		// ROW 2 - source name
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		tempLabel = new JLabel("Source name",SwingConstants.LEFT);
		gbc.insets = new Insets(10,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,sourceNameTF,gbl,gbc,1,row,1,1);
		row++;
		
		// ROW 3 - channel name
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		tempLabel = new JLabel("Image channel name",SwingConstants.LEFT);
		gbc.insets = new Insets(10,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,channelNameTF,gbl,gbc,1,row,1,1);
		row++;
		
		// ROW 4 - audio channel name
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
		
		// ROW 5 - FTP checkbox
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,15,0,15);
		Utility.add(guiPanel,bFTPCheckB,gbl,gbc,0,row,2,1);
		row++;
		
		// ROW 6 - panel containing the FTP parameters (host, username, password)
		GridBagLayout panel_gbl = new GridBagLayout();
		JPanel ftpPanel = new JPanel(panel_gbl);
		GridBagConstraints panel_gbc = new GridBagConstraints();
		panel_gbc.anchor = GridBagConstraints.WEST;
		panel_gbc.fill = GridBagConstraints.NONE;
		panel_gbc.weightx = 0;
		panel_gbc.weighty = 0;
		int panel_row = 0;
		// Panel row 1: host
		panel_gbc.insets = new Insets(0,0,0,10);
		Utility.add(ftpPanel,ftpHostLabel,panel_gbl,panel_gbc,0,panel_row,1,1);
		panel_gbc.insets = new Insets(0,0,0,0);
		Utility.add(ftpPanel,ftpHostTF,panel_gbl,panel_gbc,1,panel_row,1,1);
		panel_row++;
		// Panel row 2: username
		panel_gbc.insets = new Insets(2,0,0,10);
		Utility.add(ftpPanel,ftpUserLabel,panel_gbl,panel_gbc,0,panel_row,1,1);
		panel_gbc.insets = new Insets(2,0,0,0);
		Utility.add(ftpPanel,ftpUserTF,panel_gbl,panel_gbc,1,panel_row,1,1);
		panel_row++;
		// Panel row 3: password
		panel_gbc.insets = new Insets(2,0,0,10);
		Utility.add(ftpPanel,ftpPasswordLabel,panel_gbl,panel_gbc,0,panel_row,1,1);
		panel_gbc.insets = new Insets(2,0,0,0);
		Utility.add(ftpPanel,ftpPasswordTF,panel_gbl,panel_gbc,1,panel_row,1,1);
		// Add the panel to guiPanel
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(2,50,0,15);
		Utility.add(guiPanel,ftpPanel,gbl,gbc,0,row,2,1);
		row++;
		
		// ROW 7 - flush interval
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
		
		// ROW 8 - num blocks per segment
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
		
		// ROW 9 - debug checkbox
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,15,0,15);
		Utility.add(guiPanel,bDebugModeCheckB,gbl,gbc,0,row,2,1);
		row++;
		
		// ROW 10 - include mouse cursor checkbox
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,15,0,15);
		Utility.add(guiPanel,bIncludeMouseCursorCheckB,gbl,gbc,0,row,2,1);
		row++;
		
		// ROW 11 - stay on top checkbox
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,15,0,15);
		Utility.add(guiPanel,bStayOnTopCheckB,gbl,gbc,0,row,2,1);
		row++;
		
		// ROW 12 - OK/Cancel command buttons
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
        Utility.add(guiPanel,buttonPanel,gbl,gbc,0,row,2,1);
		
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
	
	/*
	 * Determine if appropriate settings have been made such that we can start streaming data.
	 */
	public String canCTrun() {
		
		// Check outputFolder
		if ( (ctStream.outputFolder == null) || (ctStream.outputFolder.length() == 0) ) {
			return "You must specify an output directory.";
		}
		
		// Check sourceName
		if ( (ctStream.sourceName == null) || (ctStream.sourceName.length() == 0) ) {
			return "You must specify a source name.";
		}
		
		// Check image channelName
		if ( (ctStream.channelName == null) || (ctStream.channelName.length() == 0) ) {
			return "You must specify an image channel name";
		}
		if (ctStream.channelName.contains(" ")) {
			return "You must specify an image channel name that does not contain embedded spaces";
		}
		// Check the filename extension on the image channel name; should be .jpg or .jpeg
		int dotIdx = ctStream.channelName.lastIndexOf('.');
		if ( (dotIdx == -1) || (dotIdx == 0) || (dotIdx == (ctStream.channelName.length()-1)) ) {
			return "The image channel name must end in \".jpg\" or \".jpeg\"";
		}
		String filenameExt = ctStream.channelName.substring(dotIdx).toLowerCase();
		if ( !filenameExt.equals(".jpg") && !filenameExt.equals(".jpeg") ) {
			return "The image channel name must end in \".jpg\" or \".jpeg\"";
		}
		
		// Check audio channelName
		if ( (ctStream.audioChannelName == null) || (ctStream.audioChannelName.length() == 0) ) {
			return "You must specify an audio channel name";
		}
		if (ctStream.audioChannelName.contains(" ")) {
			return "You must specify an audio channel name that does not contain embedded spaces";
		}
		// Check the filename extension on the audio channel name; should be .wav
		dotIdx = ctStream.audioChannelName.lastIndexOf('.');
		if ( (dotIdx == -1) || (dotIdx == 0) || (dotIdx == (ctStream.audioChannelName.length()-1)) ) {
			return "The audio channel name must end in \".wav\"";
		}
		filenameExt = ctStream.audioChannelName.substring(dotIdx).toLowerCase();
		if (!filenameExt.equals(".wav")) {
			return "The audio channel name must end in \".wav\"";
		}
		
		// Check FTP parameters, when using FTP
		if (ctStream.bFTP) {
			if ( (ctStream.ftpHost == null) || (ctStream.ftpHost.length() == 0) ) {
				return "You must specify the FTP host";
			}
			if (ctStream.ftpHost.contains(" ")) {
				return "The FTP host name must not contain embedded spaces";
			}
			if ( (ctStream.ftpUser == null) || (ctStream.ftpUser.length() == 0) ) {
				return "You must specify the FTP username";
			}
			if (ctStream.ftpUser.contains(" ")) {
				return "The FTP username must not contain embedded spaces";
			}
			if ( (ctStream.ftpPassword == null) || (ctStream.ftpPassword.length() == 0) ) {
				return "You must specify the FTP password";
			}
		}
		
		if (ctStream.autoFlushMillis < flushIntervalLongs[0]) {
			return new String("Flush interval must be greater than or equal to " + flushIntervalLongs[0]);
		}
		
		return "";
	}
	
	public void popupSettingsDialog() {
		
		// Squirrel away copies of the current data
		orig_outputFolder = ctStream.outputFolder;
		orig_sourceName = ctStream.sourceName;
		orig_channelName = ctStream.channelName;
		orig_audioChannelName = ctStream.audioChannelName;
		orig_bFTP = ctStream.bFTP;
		orig_ftpHost = ctStream.ftpHost;
		orig_ftpUser = ctStream.ftpUser;
		orig_ftpPassword = ctStream.ftpPassword;
		orig_autoFlushMillis = ctStream.autoFlushMillis;
		orig_numBlocksPerSegment = ctStream.numBlocksPerSegment;
		orig_bDebugMode = ctStream.bDebugMode;
		orig_bIncludeMouseCursor = ctStream.bIncludeMouseCursor;
		orig_bStayOnTop = ctStream.bStayOnTop;
		
		// Initialize data in the dialog
		outputFolderTF.setText(ctStream.outputFolder);
		sourceNameTF.setText(ctStream.sourceName);
		channelNameTF.setText(ctStream.channelName);
		audioChannelNameTF.setText(ctStream.audioChannelName);
		bFTPCheckB.setSelected(ctStream.bFTP);
		ftpHostTF.setText(ctStream.ftpHost);
		ftpUserTF.setText(ctStream.ftpUser);
		ftpPasswordTF.setText(ctStream.ftpPassword);
		ftpHostLabel.setEnabled(ctStream.bFTP);
		ftpHostTF.setEnabled(ctStream.bFTP);
		ftpUserLabel.setEnabled(ctStream.bFTP);
		ftpUserTF.setEnabled(ctStream.bFTP);
		ftpPasswordLabel.setEnabled(ctStream.bFTP);
		ftpPasswordTF.setEnabled(ctStream.bFTP);
		int selectedIdx = Arrays.asList(flushIntervalLongs).indexOf(ctStream.autoFlushMillis);
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
		bIncludeMouseCursorCheckB.setSelected(ctStream.bIncludeMouseCursor);
		bStayOnTopCheckB.setSelected(ctStream.bStayOnTop);
		
		// Pop up the dialog centered on the parent frame
		pack();
		setLocationRelativeTo(parentFrame);
		setVisible(true); // this method won't return until setVisible(false) is called
		
	}
	
	/*
	 * Respond to the state of bUseFTPCheckB changing:
	 * Enable or disable FTP setting fields given the state of the "Use FTP" checkbox
	 */
	@Override
	public void itemStateChanged(ItemEvent eventI) {
		
		Object source = eventI.getSource();
		
		if (source == null) {
			return;
		} else if (source == bFTPCheckB) {
			boolean bChecked = bFTPCheckB.isSelected();
			ftpHostLabel.setEnabled(bChecked);
			ftpHostTF.setEnabled(bChecked);
			ftpUserLabel.setEnabled(bChecked);
			ftpUserTF.setEnabled(bChecked);
			ftpPasswordLabel.setEnabled(bChecked);
			ftpPasswordTF.setEnabled(bChecked);
		}
		
	}
	
	/*
	 * Handle button callbacks
	 * 
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 * 
	 */
	@Override
	public void actionPerformed(ActionEvent eventI) {
		
		Object source = eventI.getSource();
		
		if (source == null) {
			return;
		} else if (eventI.getActionCommand().equals("Browse...")) {
			// This was for a "Browse" button to locate a directory, but since
			// we're using this for filesystems and FTP, don't bother
			// Code largely taken from http://stackoverflow.com/questions/4779360/browse-for-folder-dialog
			JFileChooser fc = new JFileChooser();
			fc.setCurrentDirectory(new java.io.File("."));
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int returnVal = fc.showSaveDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
			    File selectedFolder = fc.getSelectedFile();
			    try {
					outputFolderTF.setText(selectedFolder.getCanonicalPath());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else if (eventI.getActionCommand().equals("OK")) {
			okAction();
		} else if (eventI.getActionCommand().equals("Cancel")) {
			cancelAction();
		} 
		
	}
	
	/*
	 * User has hit the OK button; save and check data.
	 */
	public void okAction() {
		
		// Save data from the dialog
		ctStream.outputFolder = outputFolderTF.getText().trim();
		ctStream.sourceName = sourceNameTF.getText().trim();
		ctStream.channelName = channelNameTF.getText().trim();
		ctStream.audioChannelName = audioChannelNameTF.getText().trim();
		ctStream.bFTP = bFTPCheckB.isSelected();
		ctStream.ftpHost = ftpHostTF.getText().trim();
		ctStream.ftpUser = ftpUserTF.getText().trim();
		char[] passwordCharArray = ftpPasswordTF.getPassword();
		ctStream.ftpPassword = new String(passwordCharArray).trim();
		ctStream.autoFlushMillis = flushIntervalLongs[flushIntervalComboB.getSelectedIndex()];
		ctStream.numBlocksPerSegment = numBlocksPerSegmentLongs[numBlocksPerSegmentComboB.getSelectedIndex()];
		ctStream.bDebugMode = bDebugModeCheckB.isSelected();
		ctStream.bIncludeMouseCursor = bIncludeMouseCursorCheckB.isSelected();
		ctStream.bStayOnTop = bStayOnTopCheckB.isSelected();
		if (ctStream.bStayOnTop) {
			parentFrame.setAlwaysOnTop(true);
		} else {
			parentFrame.setAlwaysOnTop(false);
		}
		
		// Check data
		String errStr = canCTrun();
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
		ctStream.channelName = orig_channelName;
		ctStream.audioChannelName = orig_audioChannelName;
		ctStream.bFTP = orig_bFTP;
		ctStream.ftpHost = orig_ftpHost;
		ctStream.ftpUser = orig_ftpUser;
		ctStream.ftpPassword = orig_ftpPassword;
		ctStream.autoFlushMillis = orig_autoFlushMillis;
		ctStream.numBlocksPerSegment = orig_numBlocksPerSegment;
		ctStream.bDebugMode = orig_bDebugMode;
		ctStream.bIncludeMouseCursor = orig_bIncludeMouseCursor;
		ctStream.bStayOnTop = orig_bStayOnTop;
		
		// Pop down the dialog
		setVisible(false);
	}
	
}
