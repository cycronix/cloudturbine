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

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.stage.DirectoryChooser;

import javax.swing.SwingUtilities;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

/**
 *
 * JDialog to launch CTweb server.  The guts of this dialog is a JavaFX scene.
 *
 * @author John P. Wilson
 * @version 02/19/2018
 *
 */

public class LaunchCTweb extends javax.swing.JDialog {

    private static final long serialVersionUID = 1L;

    private JFXPanel fxPanel = null;
    private GridPane grid = null;
    private CTstream ctStream = null;

    // UI controls
    private TextField dataFolderTF = null;
    private TextField portTF = null;
    private TextField otherOptionsTF = null;

    /**
     * Constrcutor
     *
     * @param ctStreamI      The CTstream object
     * @param parentFrameI   This dialog will be located relative to its parent frame.
     */
    public LaunchCTweb(CTstream ctStreamI, javax.swing.JFrame parentFrameI) {

        super(parentFrameI, "Launch CTweb", true);

        ctStream = ctStreamI;

        GridBagLayout gbl = new GridBagLayout();
        setLayout(gbl);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 100;
        gbc.weighty = 100;
        gbc.insets = new java.awt.Insets(0,0,0,0);
        fxPanel = new JFXPanel();
        Utility.add(this,fxPanel,gbl,gbc,0,0,1,1);

        // For some unknown reason, the GridPane inside the JFXPanel doesn't automatically resize as the dialog is
        // being resized.  Here's an attempt to force the resize, but it doesn't work.
        /*
        addComponentListener(new ComponentAdapter()
        {
            public void componentResized(ComponentEvent e)
            {
                // Dialog has been resized; request new layout for the JFXPanel (needs to be done on the JavaFX thread)
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        if (grid != null) {
                            fxPanel.validate();
                            grid.requestLayout();
                        }
                    }
                });
            }
        });
        */

        // Handle the close operation in the windowClosing() method of the
        // registered WindowListener object.  This will get around
        // JFrame's default behavior of automatically hiding the window when
        // the user clicks on the '[x]' button.
        setDefaultCloseOperation(
                javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

        addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        setVisible(false);
                    }
                });

        setLocationRelativeTo(parentFrameI);

        // The JavaFX UI needs to be created on the JavaFX thread
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                initializeJavaFX();
            }
        });
    }

    /**
     * Create the JavaFX UI.  This method runs on the JavaFX thread.
     */
    private void initializeJavaFX() {
        Scene scene = createJavaFXInterface();
        fxPanel.setScene(scene);
        // Set size, position and visibility of the dialog
        // These need to be set on the JDialog object, and as such should run on Swing's event-dispatching thread (EDT)
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                pack();
                setLocationRelativeTo(getParent());
                setVisible(true); // this method won't return until setVisible(false) is called
            }
        });
    }

    /**
     * This method contains the guts of creating the UI.
     *
     * @return the created Scene.
     */
    private Scene createJavaFXInterface() {
        Group root = new  Group();
        Scene scene = new Scene(root, Color.ALICEBLUE);
        grid = new GridPane();
        // For debug: display grid lines
        // grid.setGridLinesVisible(true);
        // Center this GridPane within its parent
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));
        grid.setMaxWidth(Double.MAX_VALUE);
        root.getChildren().add(grid);

        int row = 0;

        // Row 1: data folder
        Label tempL = new Label("Data folder");
        dataFolderTF = new TextField(ctStream.outputFolder);
        dataFolderTF.setPrefWidth(150);
        Button browseB = new Button("Browse...");
        browseB.setOnAction(this::selectDirAction);
        grid.add(tempL, 0, row, 1, 1);
        grid.add(dataFolderTF, 1, row, 1, 1);
        grid.add(browseB, 2, row, 1, 1);

        // Row 2: Port
        row = row + 1;
        tempL = new Label("Port");
        portTF = new TextField(Integer.toString(ctStream.webScanPort));
        // Don't have this control fill the width
        portTF.setPrefWidth(75);
        portTF.setMaxWidth(Control.USE_PREF_SIZE);
        grid.add(tempL, 0, row, 1, 1);
        // grid.add(portTF, 1, row, 1, 1);
        // Here's a way to specify all the constraints for the portTF node: position, row/col span, hor/ver alignment, growth policy, insets
        grid.add(portTF,1,row);
        grid.setConstraints(portTF,1,row,1,1, HPos.LEFT, VPos.CENTER,Priority.NEVER,Priority.NEVER,new Insets(0,0,0,0));

        // Row 3: other command line options
        row = row + 1;
        tempL = new Label("Other options");
        otherOptionsTF = new TextField(ctStream.otherCTwebOptions);
        otherOptionsTF.setPrefWidth(150);
        grid.add(tempL, 0, row, 1, 1);
        // grid.add(otherOptionsTF, 1, row, 1, 1);
        // Here's a way to specify all the constraints for the otherOptionsTF node: position, row/col span, hor/ver alignment, growth policy, insets
        grid.add(otherOptionsTF,1,row);
        grid.setConstraints(otherOptionsTF,1,row,1,1, HPos.LEFT, VPos.CENTER,Priority.ALWAYS,Priority.NEVER,new Insets(0,0,0,0));

        // Row 4: command button
        row = row + 1;
        Button launchB = new Button("Launch");
        launchB.setOnAction(this::launchAction);
        Button cancelB = new Button("Cancel");
        cancelB.setOnAction(this::cancelAction);
        TilePane tileP = new TilePane();
        tileP.setPadding(new Insets(10, 0, 0, 10));
        tileP.setVgap(5);
        tileP.setHgap(5);
        tileP.setPrefColumns(2);
        tileP.getChildren().add(launchB);
        tileP.getChildren().add(cancelB);
        tileP.setAlignment(Pos.CENTER);
        grid.add(tileP, 0, row, 3, 1);

        // Set constraints on the columns; have second column grow
        ColumnConstraints column1 = new ColumnConstraints();
        ColumnConstraints column2 = new ColumnConstraints(50,150,Double.MAX_VALUE);
        column2.setHgrow(Priority.ALWAYS);
        ColumnConstraints column3 = new ColumnConstraints();
        grid.getColumnConstraints().addAll(column1, column2, column3);

        return scene;
    }

    /**
     * User clicked the "Browse..." button; allow them to select a directory from the file system.
     *
     * @param event  The action event that has occurred.
     */
    private void selectDirAction(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select CT data directory");
        // If the current selection is a Folder, initialize the browser dialog to that Folder
        File currentSel = new File(dataFolderTF.getText().trim());
        if (currentSel.isDirectory()) {
            directoryChooser.setInitialDirectory(currentSel);
        }
        File selectedDirectory = directoryChooser.showDialog(fxPanel.getScene().getWindow());
        if(selectedDirectory != null){
            dataFolderTF.setText(selectedDirectory.getAbsolutePath());
        }
    }

    /**
     * User clicked the Launch button; try to launch the CTweb server.
     *
     * @param event  The action event that has occurred.
     */
    private void launchAction(ActionEvent event) {

        // Data folder
        String folderStr = dataFolderTF.getText().trim();
        // See if this folder exists
        File folderF = new File(folderStr);
        if (!folderF.isDirectory()) {
            // The folder doesn't exist; see if we can create it
            try {
                boolean bSuccess = folderF.mkdirs();
                if (!bSuccess) {
                    throw new Exception("Unable to create folder");
                }
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Data folder");
                alert.setHeaderText(null);
                alert.setContentText("The data folder doesn't exist and could not be created.");
                alert.showAndWait();
            }
        }

        // Port number; should be an integer greater than 0
        String portStr = portTF.getText().trim();
        int portNum = 0;
        try {
            portNum = Integer.parseInt(portStr);
            if (portNum < 1) {
                throw new NumberFormatException("Bad port value");
            }
        } catch (NumberFormatException nfe) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Data server port");
            alert.setHeaderText(null);
            alert.setContentText("The data server port must be an integer greater than 0.");
            alert.showAndWait();
        }
        // Save the port number
        ctStream.webScanPort = portNum;

        // Other options entered by the user
        String otherOptionsStr = otherOptionsTF.getText().trim();
        // Save the other options
        ctStream.otherCTwebOptions = otherOptionsStr;

        // Fill in arguments from the user's settings
        ArrayList<String> argList=new ArrayList();
        argList.add("-p");
        argList.add(Integer.toString(portNum));
        // If there are other options, add them
        if (!otherOptionsStr.isEmpty()) {
            String[] optionStrings = otherOptionsStr.split("\\s+");
            for (String nextStr : optionStrings) {
                argList.add(nextStr);
            }
        }
        // For a discussion of File.getPath(), File.getAbsolutePath() and File.getCanonicalPath() see
        // https://stackoverflow.com/questions/1099300/whats-the-difference-between-getpath-getabsolutepath-and-getcanonicalpath
        // Using getPath() here, which gets the path string that the File object was constructed with.
        argList.add(folderF.getPath());

        // Launch CTweb process
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Convert ArrayList to an array of Strings
                // See example at https://www.tutorialspoint.com/java/util/arraylist_toarray.htm
                String strList[] = new String[argList.size()];
                strList = argList.toArray(strList);
                System.err.println("CTweb arguments:");
                for (String nextStr : strList) {
                    System.err.println("\t" + nextStr);
                }
                /*
                // Launch CTweb directly
                try {
                    // I don't think this ever returns (unless the server dies)
                    ctweb.CTweb.main(strList);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                */
                // Launch CTweb using reflection
                // From https://stackoverflow.com/questions/15582476/how-to-call-main-method-of-a-class-using-reflection-in-java
                try {
                    // We will assume CTweb.jar is located in the same directory as CTstream.jar
                    // For some reason we aren't able to locate CTweb.jar even if "CTweb.jar" is included on
                    // CTstream's Class-Path in the manifest.  I have some notion that the default class
                    // loader is looking *internally* in CTstream.jar rather than at external files
                    // outside of this JAR.  Anyway, if this were working, we could just use the following line:
                    // final Class<?> ctwebClass = Class.forName("ctweb.CTweb");
                    File ctwebFile = new File("./CTweb.jar");
                    URL ctwebURL = ctwebFile.toURI().toURL();
                    URL[] urlList = new URL[1];
                    urlList[0] = ctwebURL;
                    URLClassLoader loader = new URLClassLoader(urlList, this.getClass().getClassLoader());
                    final Class<?> ctwebClass = Class.forName("ctweb.CTweb",true, loader);
                    final Method method = ctwebClass.getMethod("main", String[].class);
                    final Object[] args = new Object[1];
                    args[0] = (String[]) strList;
                    // I don't think this ever returns (unless the server dies)
                    method.invoke(null, args);
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // Shut down the JDialog
        // For thread safety: Schedule a job for Swing's event-dispatching thread (EDT)
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setVisible(false);
            }
        });
    }

    /**
     * User clicked the Cancel button; pop down this dialog.
     *
     * @param event  The action event that has occurred.
     */
    private void cancelAction(ActionEvent event) {
        // Time to shut down this JDialog
        // For thread safety: Schedule a job for Swing's event-dispatching thread (EDT)
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setVisible(false);
            }
        });
    }

}
