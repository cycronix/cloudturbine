/*
Copyright 2018 Cycronix
 
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

/**
 * CTadmin:  CloudTurbine administration utility
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 11/11/2016
 * 
*/


package ctadmin;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import cycronix.ctlib.CTinfo;
import cycronix.ctlib.CTreader;
import ctpack.CTpack;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Callback;

//-----------------------------------------------------------------------------------------------------------------

public class CTadmin extends Application {
	
	Stage stage;
	CTreader myCTreader=null;
	static String CTlocation = "";				// CT root folder
	static String CTopen = "";					// Open selection (can be CTlocation or child-source)
	String CTopenMessage = "CTadmin";
	static boolean debug=false;
	
	public static void main(String[] args) {
		if(args.length > 0) CTopen = new File(args[0]).getAbsolutePath();
		CTinfo.setDebug(debug);
		Application.launch(CTadmin.class, args);
	}

	@Override
	public void start(Stage istage) {
		stage = istage;
		refreshTree();
	}

	//-----------------------------------------------------------------------------------------------------------------

	private void refreshTree() {
		try {
			System.gc();  	// force garbage collect (e.g. close files from pack)
			
			TreeMap<String,String>tree = new TreeMap<String,String>();

			if(CTopen.equals(".")) 	CTlocation = System.getProperty("user.dir");
			else					CTlocation = CTopen;
			System.err.println("CTadmin, path: "+CTlocation);
			CTopenMessage = "File/Open CloudTurbine Location";		// default unless treetable is built

			File CTfile = new File(CTlocation);
			if(!CTfile.exists()) {
				CTlocation = "";
				updateTree(tree);
				return;
			}

			CTinfo.debugPrint("refreshTree, CTlocation: "+CTlocation);
			if(CTlocation!=null && CTlocation.length()>0 /* && myCTreader==null */) 
				myCTreader = new CTreader(CTlocation);				// default startup open
			
			CTinfo.debugPrint("refreshTree, myCTreader: "+myCTreader);
			if(myCTreader != null) {
				ArrayList<String> CTsources = myCTreader.listSources();
		
				if(CTsources.size()==0) {		// check if this path is source itself (vs rootfolder)
					String CTlocationFullPath = CTfile.getAbsolutePath();  				// work with absolute path
//					String[] tmp = CTlocationFullPath.split("/"); 
					String[] tmp = CTlocationFullPath.split(Pattern.quote(File.separator)); 
					if(tmp.length > 1) {
//						CTlocationFullPath = CTlocationFullPath.substring(0,CTlocationFullPath.lastIndexOf('/'));	// try parent folder
						CTlocationFullPath = CTlocationFullPath.substring(0,CTlocationFullPath.lastIndexOf(File.separatorChar));	// try parent folder
						myCTreader = new CTreader(CTlocationFullPath);
						String testSource = tmp[tmp.length-1];
						System.err.println("testSource: "+testSource);
						for(String src:myCTreader.listSources()) {		// brute force check on existence
							System.err.println("src: "+src);
							if(src.equals(testSource)) {
								CTsources.add(testSource);
								CTlocation = CTlocationFullPath;
								break;
							}
						}
					}
				}
				
				if(CTsources.size()==0) {
					CTopenMessage = "No CT sources found at: "+CTlocation;
					CTlocation = "";					// trigger empty treeView
					myCTreader = null;
				}
				
				//  parse source paths into treeMap
				for (String path : CTsources) {
					CTinfo.debugPrint("add src: "+path);
//					String[] tmp = path.split("/", 2); 
					String[] tmp = path.split(Pattern.quote(File.separator), 2); 
					if(tmp.length > 1) 	treeput(tree, tmp[0], tmp[1]);
					else				treeput(tree, tmp[0], null);
				}
				//	        treeprint(tree,""); 
			}
			
			updateTree(tree);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//-----------------------------------------------------------------------------------------------------------------
	// converts CT treemap to javafx UI tree.
	// this is where CT data is filled into display fields
	
	private void convertTree(TreeItem<CTsource>root, String srcpath, TreeMap<String,String> tree) {
	    if (tree == null || tree.isEmpty())
	        return;
	    
	    CTinfo.debugPrint("convertTree, srcpath: "+srcpath);
	    for (Entry<String, String> src : tree.entrySet()) {
//	    	System.err.println("src key: "+src.getKey()+", value: "+(TreeMap)((Map.Entry)src).getValue()+", srcpath: "+srcpath);
	    	TreeItem<CTsource>srcitem;
    		String sourcePath = srcpath + File.separator + src.getKey();
    		String fullPath = CTlocation + sourcePath;
    		String folderPath = CTlocation + srcpath + File.separator;
    		
	    	if(src.getValue() == null) {		// leaf node (source)
	    		CTinfo.debugPrint("convertTree src: "+src.getKey()+", sourcePath: "+sourcePath+", fullPath: "+fullPath+", srcpath: "+srcpath);

				long diskSize = CTinfo.diskUsage(fullPath, 4096);		// this can take a while for large number of files
//				long dataSize = CTinfo.dataUsage(fullPath);
				long dataSize = CTinfo.diskSize;	// shortcut, fetch side-effect from prior diskUsage() call (cluge for speed)

				SimpleDateFormat format = new SimpleDateFormat("MMM dd, yyyy, HH:mm:ss");
				// JPW, in next 2 calls, changed from fullPath to sourcePath (ie, don't use full path)
				double oldTime = myCTreader.oldTime(sourcePath);
				double newTime = myCTreader.newTime(sourcePath);
				double duration = newTime - oldTime;
				String newTimeStr = format.format((long)(newTime*1000.));

				srcitem = new TreeItem<>(new CTsource(src.getKey(),dataSize,diskSize,duration,newTimeStr,folderPath), new ImageView(new Image(getClass().getResourceAsStream("cticon.png"))));
				ArrayList<String>chans = myCTreader.listChans(fullPath, true);		// fastSearch=true for speed
				
				// tack on channel list
				if(chans!=null && chans.size()>0) {
					for(String chan:chans) {
						CTinfo.debugPrint("chan: "+chan);
						srcitem.getChildren().add(new TreeItem(new CTsource(chan,true,folderPath), new ImageView(new Image(getClass().getResourceAsStream("file.png")))));
					}
				}
	    	} else {
//	    		System.err.println("add folder, src.key: "+src.getKey()+", srcpath: "+srcpath);
	    		srcitem = new TreeItem<>(new CTsource(src.getKey(),folderPath), new ImageView(new Image(getClass().getResourceAsStream("folder.png"))));
//	    		srcitem.setExpanded(true);
	    	}
	    	
			root.getChildren().add(srcitem);
	        convertTree(srcitem, srcpath+File.separator+src.getKey(), (TreeMap)((Map.Entry)src).getValue());	// recurse
	    }
	}
	
	//-----------------------------------------------------------------------------------------------------------------

	private void updateTree(TreeMap<String,String> tree) {
		
//		final TreeItem<CTsource> root = new TreeItem<>(new CTsource("CT"));
		String rootName = new File(CTlocation).getName();
		CTinfo.debugPrint("updateTree, rootName: "+rootName);
//		String rootParent = "";
//		if(CTlocation.length()>0) rootParent = CTlocation.substring(0,CTlocation.lastIndexOf(File.separator)+1);

		final TreeItem<CTsource> root = new TreeItem<>(new CTsource(rootName,CTlocation));
		root.setExpanded(true);
		convertTree(root, "", tree);		// recursive tree walk	
	
		stage.setTitle("CTadmin");
		final Scene scene = new Scene(new Group());
		scene.setFill(Color.LIGHTGRAY);

		Group sceneRoot = (Group) scene.getRoot();
		VBox vbox = new VBox();
		
		// Source
		TreeTableColumn<CTsource, String> sourceColumn = new TreeTableColumn<>("Source");
		sourceColumn.setPrefWidth(150);
		sourceColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<CTsource, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().getName()));
		
		// DataSpace
		TreeTableColumn<CTsource, String> dataSpaceColumn = new TreeTableColumn<>("Size");
		dataSpaceColumn.setPrefWidth(80);
		dataSpaceColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<CTsource, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().getDataSpace()));

		// DiskSpace
		TreeTableColumn<CTsource, String> diskSpaceColumn = new TreeTableColumn<>("DiskUse");
		diskSpaceColumn.setPrefWidth(80);
		diskSpaceColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<CTsource, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().getDiskSpace()));
		diskSpaceColumn.setVisible(false);

		// OldTime
		TreeTableColumn<CTsource, String> durationColumn = new TreeTableColumn<>("Duration");
		durationColumn.setPrefWidth(160);
		durationColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<CTsource, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().getDuration()));
		durationColumn.setVisible(true);
		
		// NewTime
		TreeTableColumn<CTsource, String> newTimeColumn = new TreeTableColumn<>("Modified");
		newTimeColumn.setPrefWidth(160);
		newTimeColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<CTsource, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().getNewTime()));

		// add tree table node
		TreeTableView<CTsource> treeTable = new TreeTableView<>(root);
		treeTable.setShowRoot((myCTreader!=null && CTlocation != null && CTlocation.length()>0));
		treeTable.getColumns().setAll(sourceColumn, dataSpaceColumn, diskSpaceColumn, durationColumn, newTimeColumn);
		treeTable.setTableMenuButtonVisible(true);
		treeTable.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);

		treeTable.setPlaceholder(new Label(CTopenMessage));
		
		// clugey trick to get proportionally different column widths:
		sourceColumn.setMaxWidth( 1f * Integer.MAX_VALUE * 50 ); // 30% width
		dataSpaceColumn.setMaxWidth( 1f * Integer.MAX_VALUE * 20 ); // 20% width
		diskSpaceColumn.setMaxWidth( 1f * Integer.MAX_VALUE * 20 ); // 20% width
		durationColumn.setMaxWidth( 1f * Integer.MAX_VALUE * 20 ); // 50% width
		newTimeColumn.setMaxWidth( 1f * Integer.MAX_VALUE * 30 ); // 50% width

		// context menu for rows
		setContextMenuByRow(treeTable);
		
		// setup scene and menubar
		vbox.setVgrow(treeTable, Priority.ALWAYS);	// make sure window grows to include bottom of tree
		vbox.getChildren().addAll(buildMenuBar(stage), treeTable);
		sceneRoot.getChildren().add(vbox);
		stage.setScene(scene);

		// track window size
		scene.widthProperty().addListener( 
				new ChangeListener() {
					public void changed(ObservableValue obs, Object old, Object newValue) {
						treeTable.setPrefWidth((Double)newValue);
					}
				});

		scene.heightProperty().addListener( 
				new ChangeListener() {
					public void changed(ObservableValue obs, Object old, Object newValue) {
						treeTable.setPrefHeight((Double)newValue);
					}
				});
		
		CTinfo.debugPrint("about to stage.show");
		stage.setOnCloseRequest(e -> Platform.exit());		// close app on window exit
		stage.show();
		CTinfo.debugPrint("stage.show done!");
	}

	//-----------------------------------------------------------------------------------------------------------------
	void Warning(String warning) {
		Alert alert = new Alert(AlertType.WARNING);
		alert.setTitle("Warning");
		alert.setHeaderText(null);
		alert.setContentText(warning);
		System.err.println("Warning: "+warning);
		alert.showAndWait();
	}
	
	//-----------------------------------------------------------------------------------------------------------------
	void setContextMenuByRow(TreeTableView<CTsource> treeTable) {
		// context menu for rows
		treeTable.setRowFactory(
				new Callback<TreeTableView<CTsource>, TreeTableRow<CTsource>>() {
					@Override
					public TreeTableRow<CTsource> call(TreeTableView<CTsource> tableView) {
						final TreeTableRow<CTsource> row = new TreeTableRow<>();
						final ContextMenu rowMenu = new ContextMenu();
						
						// Rename
						MenuItem renameItem = new MenuItem("Rename...");
						renameItem.setOnAction(new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent event) {
								if(!row.getItem().isSource()) {
									Warning("Cannot rename channel");
									return;
								}

								String thisFile = row.getItem().getName();
								String thisFolderPath = row.getItem().getFolderPath();
								if(thisFolderPath.equals(CTlocation)) {
									Warning("Cannot rename root folder");
									return;
								}
								CTinfo.debugPrint("Rename: "+thisFolderPath + thisFile);
								
								TextInputDialog dialog = new TextInputDialog(thisFile);
								dialog.setTitle("CT Rename Source");
								dialog.setHeaderText("Rename CT Source: "+thisFile);
								dialog.setContentText("New source name:");
								dialog.setGraphic(new ImageView(this.getClass().getResource("cticon.png").toString()));
								
								// Traditional way to get the response value.
								Optional<String> result = dialog.showAndWait();
								if (result.isPresent()){
									String newName = result.get();
								    File oldFile = new File(thisFolderPath + thisFile);	// doesn't follow subdirs
								    File newFile = new File(thisFolderPath + newName);
								    System.err.println("Rename: "+oldFile.getPath()+", to: "+newFile.getPath());
								    boolean status = oldFile.renameTo(newFile);
//								    if(status) 	refreshTree();
								    if(status)  {
						                TreeItem<CTsource> treeItem = row.getTreeItem();
						                CTsource ctsrc = treeItem.getValue();
						                ctsrc.setName(newName);
						                treeItem.setValue(null);
						                treeItem.setValue(ctsrc);
						                
						                treeTable.getSelectionModel().clearSelection();
								    }
								    else 		Warning("Failed to rename: "+thisFile);
								}
							}
						});
						
						// Repack
						MenuItem repackItem = new MenuItem("Repack...");
						repackItem.setOnAction(new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent event) {
								if(!row.getItem().isSource()) {
									Warning("Cannot repack channel");
									return;
								}

								String thisSource = row.getItem().getName();
								String thisFolderPath = row.getItem().getFolderPath();
								String fullPath = new File(thisFolderPath + thisSource).getAbsolutePath();
								String rootFolder = new File(fullPath).getParent();
								String repackSource = thisSource + ".pack";

								Alert alert = new Alert(AlertType.CONFIRMATION);
								alert.setTitle("Repack Confirmation");
								alert.setHeaderText(null);
								alert.setContentText("Confirm Repack: "+fullPath+" to: "+repackSource);

								Optional<ButtonType> result = alert.showAndWait();
								if (result.get() == ButtonType.OK) {
									new CTpack(new String[] {"-1","-o"+repackSource, "-r"+rootFolder, thisSource});
									refreshTree();
									System.err.println("Repack: source: "+thisSource+", dest: "+repackSource+", rootFolder: "+rootFolder);
								}
								else {
									System.err.println("Cancel Repack");
								}
							}
						});

						// Delete
						MenuItem removeItem = new MenuItem("Delete...");
						removeItem.setOnAction(new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent event) {
								if(!row.getItem().isSource()) {
									Warning("Cannot delete channel");
									return;
								}
								String thisFile = row.getItem().getName();
								String thisFolderPath = row.getItem().getFolderPath();

								if(thisFolderPath.equals(CTlocation)) {
									Warning("Cannot delete root folder");
									return;
								}
								
								Alert alert = new Alert(AlertType.CONFIRMATION);
								alert.setTitle("Delete Confirmation");
								alert.setHeaderText(null);
								String fullPath = new File(thisFolderPath + thisFile).getAbsolutePath();
								alert.setContentText("Confirm Delete: "+fullPath);

								Optional<ButtonType> result = alert.showAndWait();
								if (result.get() == ButtonType.OK){
									System.err.println("Delete: "+thisFolderPath + thisFile);
									myCTreader.clearFileListCache();   // dump open files
									System.gc();	// force release of files (otherwise delete may fail on windows)
									
									Path directory = Paths.get(fullPath);
									try {
										Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
										   @Override
										   public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
										       Files.delete(file);
										       return FileVisitResult.CONTINUE;
										   }

										   @Override
										   public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
										       Files.delete(dir);
										       return FileVisitResult.CONTINUE;
										   }
										});
									} catch (IOException e) {
										Warning("File Deletion Error: "+e);
										e.printStackTrace();
										return;
									}
									
					                TreeItem<CTsource> treeItem = row.getTreeItem();
									treeItem.getParent().getChildren().remove(treeItem);
					                treeTable.getSelectionModel().clearSelection();
//								    refreshTree();
								} else {
									System.err.println("Cancel Delete");
								}
							}
						});

						rowMenu.getItems().addAll(renameItem, repackItem, removeItem);

						// only display context menu for non-null items:
//						if(row.getItem()!=null && row.getItem().isSource())	// always null at factory call!?
							
						row.contextMenuProperty().bind(
								Bindings.when(Bindings.isNotNull(row.itemProperty()))
								.then(rowMenu)
								.otherwise((ContextMenu)null));
							
						return row;
					}
				});
	
	}
	
	//-----------------------------------------------------------------------------------------------------------------
	// updateMenuBar

	private MenuBar buildMenuBar(Stage primaryStage) {
		BorderPane root = new BorderPane();

		MenuBar menuBar = new MenuBar();
		menuBar.prefWidthProperty().bind(primaryStage.widthProperty());
		root.setTop(menuBar);
		
		// File Menu
		Menu fileMenu = new Menu("File");

		// File/Open
		MenuItem openMenuItem = new MenuItem("Open...");
		fileMenu.getItems().add(openMenuItem);
		openMenuItem.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				DirectoryChooser directoryChooser = new DirectoryChooser();
                File selectedDirectory = directoryChooser.showDialog(primaryStage);
                if(selectedDirectory != null){
                	CTopen = selectedDirectory.getAbsolutePath();
//					myCTreader = new CTreader(CTlocation);
					refreshTree();
                }
			}
		});
	
		// File/Refresh
		MenuItem refreshMenuItem = new MenuItem("Refresh");
		fileMenu.getItems().add(refreshMenuItem);
		refreshMenuItem.setOnAction(actionEvent -> refreshTree());

		// File/Exit
		MenuItem exitMenuItem = new MenuItem("Exit");
		fileMenu.getItems().add(exitMenuItem);
		exitMenuItem.setOnAction(actionEvent -> Platform.exit());

		menuBar.getMenus().addAll(fileMenu);
		
		return menuBar;
	}

	//-----------------------------------------------------------------------------------------------------------------
	// CTsource:  a data structure for holding treeTableView row info
	
	public class CTsource {
		private String name;
		private String dataspace="";
		private String diskspace="";
		public String newTime="";
		public String duration="";
		public String folderpath="";
		private boolean ischannel=false;
		
		private CTsource(String name, long dataspace, long diskspace, double duration, String newTime, String folderPath) {
			this.name = name;
			this.dataspace = readableFileSize(dataspace);
			this.diskspace = readableFileSize(diskspace);
			long iduration = (long)duration;
			long days = iduration / 86400;
			long hours = (iduration % 86400) / 3600;
			long minutes = (iduration % 3600) / 60;
			long seconds = iduration % 60;
//			System.err.println("source: "+name+", duration: "+duration+", days: "+days+", hours: "+hours+", minutes: "+minutes+", seconds: "+seconds);
			if(days >= 1) {
				this.duration = String.format("%d Days, %02d:%02d:%02d", days, hours, minutes, seconds);
			}
			else if(hours >= 1) {
				this.duration = String.format("%02d:%02d:%02d H:M:S", hours, minutes, seconds);
			} 
			else if(minutes >= 1) {
				this.duration = String.format("%02d:%02d M:S", minutes, seconds);
			} 
			else {
				this.duration = (((double)(Math.round(duration*1000.)))/1000.)+" S";	// round to msec resolution
			}
			
			this.newTime = newTime;
			this.folderpath = folderPath;
//			System.err.println("new CTsource SRC, fullPath: "+fullPath);
		}
		
//		private CTsource(String name, long dataspace, long diskspace) {
//			new CTsource(name, dataspace, diskspace, 0, "");
//		}
		
		private CTsource(String name, boolean ischan, String folderPath) {
			this.name = name;
			this.ischannel=ischan;
			this.folderpath = folderPath;
//			System.err.println("new CTsource CHAN, fullPath: "+fullPath);
		}
		
		private CTsource(String name, String folderPath) {
			this.name = name;
			this.folderpath = folderPath;
//			System.err.println("new CTsource FOLDER, fullPath: "+fullPath);
		}
		
		public String getName() 				{ return name; 		}
		public void setName(String name)		{ this.name = name;	}
		public String getDataSpace() 			{ return dataspace; 	}
		public String getDiskSpace() 			{ return diskspace; 	}
		public String getNewTime() 				{ return newTime; 	}
		public String getDuration()				{ return duration; }
	    public boolean isSource() 				{ return !ischannel; }
	    public String getFolderPath()			{ return folderpath;	}
	}
	
	public static String readableFileSize(long size) {
	    if(size <= 0) return "0";
	    final String[] units = new String[] { "B", "kB", "MB", "GB", "TB" };
	    int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
	    return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}
	
	//-----------------------------------------------------------------------------------------------------------------

	private static void treeput(TreeMap structure, String root, String rest) {
		String[] tmp;
//		if(rest != null) tmp = rest.split("/", 2);
		if(rest != null) tmp = rest.split(Pattern.quote(File.separator), 2);
		else{
			structure.put(root,null);
			return;
		}
		
	    TreeMap rootDir = (TreeMap) structure.get(root);

	    if (rootDir == null) {
	        rootDir = new TreeMap();
	        structure.put(root, rootDir);
	    }
	    if (tmp.length == 1) { // path end
	        rootDir.put(tmp[0], null);
	    } else {
	        treeput(rootDir, tmp[0], tmp[1]);
	    }
	}
	private static void treeprint(TreeMap map, String delimeter) {
	    if (map == null || map.isEmpty())
	        return;
	    for (Object m : map.entrySet()) {
	        System.out.println(delimeter + "-" + ((Map.Entry)m).getKey());
	        treeprint((TreeMap)((Map.Entry)m).getValue(), " |" + delimeter);
	    }
	}
}

