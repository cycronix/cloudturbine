package ctsync;
 

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.sun.nio.file.ExtendedWatchEventModifier;
import com.sun.nio.file.SensitivityWatchEventModifier;

import cycronix.ctlib.*;


/*
 * Cloudturbine file sync
 * basic logic:
 * WatchService recursively registers and catches file update events
 * (some logic to avoid dupes)
 * Files to copy added to linked-list (fileQ)
 * PumpQ async thread chews on fileQ and copies to Stream folder
 */
//------------------------------------------------------------------------------------------------
/**
 * Example to watch a directory (or tree) for changes to files.
 */
 
public class CTsync {
 
    private WatchService watcher;
    private Map<WatchKey,Path> keys;
    private LinkedList<String> fileQ=new LinkedList<String>();
    	
    private static boolean recursive;
	private static boolean debug = false;
	private static boolean zipflag=false;
	private static boolean unzipflag = false;
	private static boolean copyflag = false;
	private static boolean delflag = false;
	
	private static int mgranularity=100;			// msec file timestamp granularity (limits number of time-folders)
	private static int mcheckinterval=20;			// msec check for new files interval 
	private static int mflushinterval=1000;			// rate at which to flush zip files (limits Dropbox update rate)
	private static long mpacetime=0;

//	private static String tempPath=".";				// was "Temp"
	private static String destPath="Stream";
	private static String srcPath="CV";
    private static String outName=null;
    
    private CTwriter CTW;			// writes data to folder/zip files
   
	//------------------------------------------------------------------------------------------------
    /**
     * Creates a WatchService and registers the given directory
     */
    public CTsync() throws IOException {
    	System.err.println("CTsync running...");

		if(zipflag) {
			startZflush(mflushinterval);			// flush zip files at interval
		}
		
		CTW = new CTwriter(destPath);			// create CT writer
		CTinfo.setDebug(debug);
		CTW.setZipMode(zipflag);
		
        new PumpQ().start();					// start the async file queue monitor 
        watchEvents();							// monitor file change events
    }
 
	//------------------------------------------------------------------------------------------------
    //  private static String rootPath="";
    public static void main(String[] args) throws IOException {
    	// parse arguments
    	if (args.length == 0) usage();
    	int dirArg = 0;
    	while(args[dirArg].startsWith("-")) {		// clugey arg parsing
    		if(args[dirArg].equals("-r")) 	recursive = true;
    		if(args[dirArg].equals("-z")) 	zipflag = true;
    		if(args[dirArg].equals("-u")) 	unzipflag = true;
    		if(args[dirArg].equals("-c")) 	copyflag = true;
    		if(args[dirArg].equals("-x")) 	debug = true;
    		if(args[dirArg].equals("-d")) 	delflag = true;
    		if(args[dirArg].equals("-t")) { mpacetime = Long.parseLong(args[++dirArg]); }
    		if(args[dirArg].equals("-n")) { outName = args[++dirArg] ; }
    		dirArg++;
    	}
    	if(!zipflag && !unzipflag) copyflag = true;		// do something
    	
    	if(debug) System.err.println("debug on");
    	// register directory and process its events
    	if(args.length > dirArg) srcPath = args[dirArg++];
    	if(args.length > dirArg) destPath = args[dirArg++];
    	if(args.length > dirArg) mflushinterval = Integer.parseInt(args[dirArg]);
    	if(mflushinterval < 10)  mflushinterval = 10;
    	
    	new CTsync();
    }

    //------------------------------------------------------------------------------------------------
    static void usage() {
    	System.err.println("Usage: java -jar CTsync.jar [-r -c -z -u -x -d -t -n] CVdir StreamDir [mflush]");
    	System.exit(-1);
    }

	//------------------------------------------------------------------------------------------------
	// PumpQ:  async writes files stored in queue (linked list) 
	
    class PumpQ extends Thread {
    	private Path dest = Paths.get(destPath);
    	PumpQ() {};
    	
        public void run() {
			System.err.println("PumpQ start...");

            String nextP;
			String slash = File.separator;
			long omtime = 0;
			
            while(true) {
            	int nfile = fileQ.size();			// number available
            	if(nfile > 0) {
            		// clump file time as groups
        			long mtime = System.currentTimeMillis();
//        			mtime = (mtime / mgranularity) * mgranularity;	//  set granularity
        			String targFolder = null;

        			ArrayList<File>files=new ArrayList<File>();
        			try {
        				for(int i=0; i<nfile; i++) {
        					nextP = fileQ.poll();								// nextP is full-path name
        					if(nextP == null) {
        						System.err.println("PumpQ: null file! nfile: "+nfile);		// shouldn't happen but sporadically does?
        						Thread.sleep(60000);		// DEBUG:  slow down so can see event
        						nfile=0;					// get out of loop
//        						fileQ.clear();
        						break;
        					}
        					File nextF = new File(nextP);
        					if((nextF==null) || (nextF.length()<=0)) {
        						System.err.println("PumpQ: empty file: "+nextF);
        						continue;	// skip empty files
        					}
        					String fname = nextF.getName();						// file name (last part of pathname sequence)
        					if(fname.endsWith(".tmp")) continue;	
        					
 //       					if(debug) System.err.println("ctsync file: "+fname+", copyflag: "+copyflag);
                			mtime = nextF.lastModified();
                			mtime = (mtime / mgranularity) * mgranularity;		//  set granularity
                			
                			// UNZIP
        					if(unzipflag && fname.endsWith(".zip")) unzip(nextP, dest);			// unzip
        					
        					// ZIP
        					else if(zipflag && !fname.endsWith(".zip")) files.add(nextF);		// build list of files for composite zip
        					
        					// COPY
        					else if(copyflag){									// copy updated file to Stream sub-folder
                    			targFolder = destPath+slash+mtime+slash;
        						new File(targFolder).mkdirs();		// ensure destination dir is created
        						if(debug) System.err.println("copy: "+nextP+" to: "+(targFolder+fname));
        						Files.copy(Paths.get(nextP), Paths.get(targFolder+fname), REPLACE_EXISTING, COPY_ATTRIBUTES);
        					}
        				}
        				fileQ=new LinkedList<String>();			// start over?
        				
        				// ZIP
        				if(zipflag && (files.size()>0)) {				// zip collection of files 
        					if(mtime == omtime) mtime++;							// no-clobber 
                			zip(mtime, files);
                			omtime = mtime;
        				}
            		} catch(Exception e) { System.err.println("oops: "+e); e.printStackTrace(); nfile=0; }
            	}
            	else try { Thread.sleep(mcheckinterval); } catch(Exception e) {};		// need better logic, maybe on a timer...
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }
    
//------------------------------------------------------------------------------------------------
// unzip:  unzip archive of files
    
    void unzip(String zipFile, Path dest) {
    	if(debug) System.err.println("unzip: "+zipFile+" to: "+dest);
    	byte[] buffer = new byte[1024];
    	
    	String zipName = new File(zipFile).getName();
    	String outputFolder = dest + File.separator + zipName.replace(".zip",  "");
    	try{
    		//get the zip file content
    		ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
    		ZipEntry ze = zis.getNextEntry();			//get the zipped file list entry
    		
    		while(ze!=null){
    			String fileName = ze.getName();
    			Path pp = Paths.get(fileName);
    			File newFile = new File(outputFolder + File.separator + pp);
    			System.err.println("unzip --> "+ newFile.getAbsoluteFile());
    			
    			new File(newFile.getParent()).mkdirs();			// create folders
    			FileOutputStream fos = new FileOutputStream(newFile);             

    			int len;
    			while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);

    			fos.close();   
    			ze = zis.getNextEntry();
    		}

    		zis.closeEntry();
    		zis.close();
    	} catch(IOException ex) { ex.printStackTrace(); }
    }    
    
//------------------------------------------------------------------------------------------------
// zip:  zip-archive list of files
    
    private Lock lock = new ReentrantLock();
    
    synchronized void zip(long ziptime, ArrayList<File>files) throws IOException {
    	if(files.size() <= 0) return;		// notta
    	
        try {
    		lock.lock();

    		long mtime=0;
    		if(mpacetime>0) mtime = ziptime;

        	for(int i=0; i<files.size(); i++) {
        		File file = files.get(i);

        		if(mpacetime>0 && i>0) mtime += mpacetime;
        		else {
        			mtime = file.lastModified();	
        			mtime = (mtime / mgranularity) * mgranularity;			//  set granularity
        		}
        		
        		String name = outName;						// single name output
        		if(outName == null) name = file.getName();	// file names	
        		
        		if(debug) System.err.println("CTWput mtime: "+mtime+", name: "+name);
    	    	CTW.setTime(mtime);
        		CTW.putData(name, file);
        		if(delflag) {
        			if(debug) System.err.println("delete file: "+file.getName());
        			file.delete();
        		}
        		
    	    	if(debug) System.err.println("Zip: "+name+" to: "+destPath);
        	}
        } catch(Exception e) {
        	System.err.println("zip exception: "+e);
        	e.printStackTrace();
        } finally {
        	lock.unlock();
        }
      }
  
	//------------------------------------------------------------------------------------------------
    public void startZflush(long msec) {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new ZflushTask(), msec, msec);
        if(debug) System.err.println("started zflush timer at: "+msec+" msec");
//        timer.schedule(new ZflushTask(), msec);
	}

    class ZflushTask extends TimerTask {
        public synchronized void run() {
//    		if(zos == null) return;
    		try {
    			try {
    				lock.lock();
    				if(debug) System.err.println("---------FLUSH zip: "+destPath);
    				CTW.flush();
    			} catch(Exception e) { System.err.println("zflush move failed"); e.printStackTrace(); };
    		} finally {
        		lock.unlock();
        	}
        }
    }
    
	//------------------------------------------------------------------------------------------------
    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
//        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
    	// following works better on Mac OS X, which as of Java7 does not use native watchservice.  OS X still slow relative to PC.  See:
    	WatchKey key;
    	// http://stackoverflow.com/questions/9588737/is-java-7-watchservice-slow-for-anyone-else
    	try {		// Windows (only) supports built-in recursive monitoring
    		key = dir.register(watcher, new WatchEvent.Kind[]{ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE}, SensitivityWatchEventModifier.HIGH, ExtendedWatchEventModifier.FILE_TREE);
    	} catch(Exception e) {
    		key = dir.register(watcher, new WatchEvent.Kind[]{ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE}, SensitivityWatchEventModifier.HIGH);
    	}
    	
        keys.put(key, dir);
    }
 
	//------------------------------------------------------------------------------------------------
    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        System.out.format("Recursive register %s ...\n", start);
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
 
	//------------------------------------------------------------------------------------------------
    /**
     * Process all events for keys queued to the watcher
     */
    void watchEvents() {
    	System.err.println("watchEvents...");
    	
    	// register folders to watch
    	try {
            watcher = FileSystems.getDefault().newWatchService();
            keys = new HashMap<WatchKey,Path>();
    		if (recursive) 	registerAll(Paths.get(srcPath));
    		else 			register(Paths.get(srcPath));
    	} catch(IOException ioe) {
    		System.err.println("oops, exception on watchevents register: "+ioe);
    		return;
    	}
        
    	// loop catching change events
        FileTime lastMod=null;
        Path lastFile=null;
        for (;;) {
 
            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();		// wait on next watch key (file change)  // was watcher.take()
            } 
            catch (Exception x) {
            	System.err.println("watcher interupted!");
               return;
           }
 
            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {
                Kind<?> kind = event.kind();
//                System.err.println("watch kind: "+kind);
                
                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                	System.err.println("OVERFLOW!!!");
                    continue;
                }
 
                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);
 
                if(lastMod == null) {	// initialize to first file
                	try {
//                		lastMod = Files.getLastModifiedTime(child);
                	} catch(Exception e){};
                }
                
                // print out event
                if(debug) System.out.format("%s: %s\n", event.kind().name(), child);
 
                // make a rule when to send new file to CloudTurbine
               
                if(kind == ENTRY_MODIFY || kind == ENTRY_CREATE) {
                	if(Files.isRegularFile(child) && Files.isReadable(child) && Files.exists(child)) {
//                		System.out.println("******got one: "+child);
                		boolean isSame = false;
                		try {	// skip duplicate mod-times 
                				// can get multiple modify events per file (over)write
                			FileTime thisMod = Files.getLastModifiedTime(child);
//                			isSame = thisMod.equals(lastMod);

//                			long delta = 1000;
                			if(child.equals(lastFile) && (thisMod.equals(lastMod)))
//                			if(thisMod.toMillis() < (lastMod.toMillis()+delta))	// try time-delta diff
                						isSame = true;
                			else 		isSame = false;
                			
                			lastMod = thisMod;
                			lastFile = child;
 //               			isSame = false;			// foo over-ride to see if files all go
                		} catch(Exception e){};
                		if(!isSame) {
                			putFile(child);
                		}
                	}
                }
                
                // if directory is created, and watching recursively, then register it and its sub-directories
                // this can miss quick create folder/file...
                if (recursive && (kind == ENTRY_CREATE)) {
                	if(debug) System.err.println("register folder: "+child);
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
//                        if (Files.isDirectory(child)) {
                            registerAll(child);
                            //  see if any quick create files already there (watcher may miss):
                            File folder = child.toFile();
                            File[] listOfFiles = folder.listFiles();
                            for(int i=0; i<listOfFiles.length; i++) {
                            	if(debug) System.err.println("new folder, found file: "+listOfFiles[i]);
                            	putFile(listOfFiles[i].toPath());
                            }
                        }
                    } catch (IOException x) {
                    	System.err.println("exception on dir create: "+x);
                        // ignore to keep sample readbale
                    }
                }
            }
 
            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);
 
                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

	//------------------------------------------------------------------------------------------------
	synchronized private void putFile(Path child) {
		try {
			if(debug) System.err.println("putFile: "+child);
//			putRBNB(child);
			String sname = child.toString();
			if(child==null || sname==null) {
				System.err.println("OOPS:  null putFile! (ignored)");
				return;
			}
			if(sname != null && !checkDupe(fileQ, sname))	// no dupes (not full proof if PUTs get ahead of create/mod)
				fileQ.add(sname);
		} catch(Exception e) {
			e.printStackTrace();
			System.err.println("CTsync putFile exception: "+e);
			fileQ=new LinkedList<String>();					// make a new one?
//			System.err.println("fileQ: "+fileQ);
		}
	}
	  
	boolean checkDupe(LinkedList<String> ll, String check) {
		for(String s:ll) {
			if(s.equals(check)) return true;
		}
		return false;
	}

}
