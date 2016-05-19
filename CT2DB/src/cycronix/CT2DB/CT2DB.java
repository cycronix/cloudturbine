
/*
   Copyright 2014 Cycronix

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

package cycronix.CT2DB;

// MJM 6/2014: adapt to put to Dropbox
// rbnbFile (adapted from FileWatch)
//watch a folder of files and if any updates, send its contents to Dropbox
//Matt Miller 9/2010

// 11/2011:  added retain-state for Folder, Filter text fields

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioButton;
import android.util.Log;
import cycronix.CT2DB.R;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;

public class CT2DB extends Activity {

    // Replace this with your app key and secret assigned by Dropbox.
    // Note that this is a really insecure way to do this, and you shouldn't
    // ship code which contains your key & secret in such an obvious way.
    // Obfuscation is good.
	final static private String APP_KEY = "xxxxxxxxxx";
	final static private String APP_SECRET = "yyyyyyyyyyy";

    
	private static final float updateInc = (float)1.0;		// update interval (sec)
	private static boolean Running=false;
	
	private static String folderName = "CTdata";		// default
	private static String sourceName = "dbCapture";
	private static String fileFilter = "*.zip";
	private static final String TAG = "CT2DB";
	private static String PREFS_NAME="CT2DB_preferences";
	
	private static int iterCount=0;
	TextView tv;
	private Timer myTimer;
	private static long lastMod = 0;
	private static boolean catchUp = true;			// catch up (resend) old data
	private RadioButton stopBtn, runBtn, quitBtn;
	private RadioButton retainBtn;
	private EditText sourceNameET, folderNameET, fileFilterET;
	private int ucount=0;		// upload counter
	private DBauthenticate dba=null;
    DropboxAPI<AndroidAuthSession> mApi;

	//---------------------------------------------------------------------------------	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);

    	getSettings(); // Restore preferences

    	setContentView(R.layout.main);
    	tv = (TextView)findViewById(R.id.myText);
    	if(tv != null) tv.setText("Not Started.");

    	// get GUI objects
    	sourceNameET = (EditText)findViewById(R.id.sourceName);
    	folderNameET = (EditText)findViewById(R.id.folderName);
    	fileFilterET = (EditText)findViewById(R.id.fileFilter);
    	sourceNameET.setText(sourceName);
    	folderNameET.setText(folderName);
    	fileFilterET.setText(fileFilter);

    	listFiles();		// initial list status (after folderNameET set)

    	// connect to dropbox
    	dba = new DBauthenticate(this, APP_KEY, APP_SECRET);

    	myTimer = new Timer();
    	myTimer.schedule(new TimerTask() {
    		@Override public void run() { TimerMethod(); }
    	}, 0,(int)(updateInc*1000.)); 		// check interval

    	// start local webserver
//    	CTserver cts = new CTserver(8000);
    	
    	runBtn = (RadioButton)findViewById(R.id.RunButton);
    	stopBtn = (RadioButton)findViewById(R.id.StopButton);
    	quitBtn = (RadioButton)findViewById(R.id.QuitButton);
    	retainBtn = (RadioButton)findViewById(R.id.RetainButton);
    
    	runBtn.setOnClickListener(new OnClickListener() {
    		@Override
    		public void onClick(View v) {
    			if(!Running) {
    				pLog("Starting up...");
    				putSettings();
    				Running = true;
    				ucount = 0;
    			}
    		}
    	}
    	);
    	stopBtn.setOnClickListener(new OnClickListener() {
    		@Override
    		public void onClick(View v) {
    			if(Running) {
    				pLog("Shutting down...");
    				//	            		stopSource();
    				Running = false;
    				putSettings();
    			}
    		}
    	}   	
    			);
    	quitBtn.setOnClickListener(new OnClickListener() {
    		@Override
    		public void onClick(View v) {
    			pLog("Quitting App...");
    			Running=false;
    			stopBtn.setChecked(true);
    			putSettings();
    			finish();
    		}
    	}   	
    	);
    }
 
    @Override
    protected void onDestroy() {
  	  	Log.d(TAG,"onDestroy");
        super.onDestroy();
        myTimer.cancel();
       putSettings();		 // Save user preferences

    }  
    
    private void getSettings() {
    	// Restore preferences
    	SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
    	folderName = settings.getString("folderName", folderName);
    	fileFilter = settings.getString("fileFilter", fileFilter);
    	sourceName = settings.getString("sourceName", sourceName);
    }
    
    private void putSettings() {
        // Save user preferences
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("sourceName", sourceNameET.getText().toString());
        editor.putString("folderName", folderNameET.getText().toString());
        editor.putString("fileFilter", fileFilterET.getText().toString());
        editor.apply();
    }
    
	//---------------------------------------------------------------------------------	
	private void FileWatch() {
	
		java.io.File file=null;
		
		try {			
	    	fileFilter = fileFilterET.getText().toString();
	    	String subfilter=fileFilter;
	    	
//	    	pLog("folderName: "+folderName);
	    	boolean dosuffix=false;
	    	boolean doall=false;
	    	if(fileFilter.equals("") || fileFilter.equals("*")) {
	    		doall = true;
	    	}
	    	else if(fileFilter.contains("*")) {	// lazy file filtering, either *.suffix or all
	    		dosuffix=true;
	    		int windex = fileFilter.lastIndexOf("*");
	    		if(windex < (fileFilter.length()-1)) {
	    			dosuffix = true;
	    			subfilter = fileFilter.substring(windex+1);
	    		}
	    	}
	    
			File[] listOfFiles = listFiles();					// get updated list of files
			
			String fileName;
			catchUp = !retainBtn.isChecked();				// if delete, then catch-up (and delete) old files
			if(!catchUp && lastMod==0) lastMod = listOfFiles[0].lastModified() - 1;
			
			for(int i=0; i<listOfFiles.length; i++) {
				if(!Running) return;		// bail
				
				file = listOfFiles[i]; 
			
				if(!file.isFile()) continue;
				fileName = file.getName();
//				pLog("fileName: "+fileName+", filter: "+fileFilter);
				if(!doall) {
					if(dosuffix) {
						if(!fileName.endsWith(subfilter)) continue;
					}
					else if(!fileName.equals(subfilter)) continue;
				}

				long fileLength = file.length();
				if(fileLength <= 0) continue;
//				Log.d(TAG,"file: "+file+", size: "+fileLength);
	
				if(file.lastModified() > lastMod) {
					pLog("Put file: "+file.getName()+", size: "+fileLength);
					putDBfile(file.getAbsolutePath(), !retainBtn.isChecked());		// async, no way to determine success here...
					ucount++;
					
					long fmod = file.lastModified();
					if(fmod > lastMod) lastMod = file.lastModified();	
					else pLog("oops, backwards going time, ignored!");
			    }
			}
		} catch (java.lang.Exception e) {
			Log.e(TAG,Log.getStackTraceString(e));
		}
		
	}

	//---------------------------------------------------------------------------------
	// listFiles:  get list of files, log status
	File[] listFiles() {
    	folderName = folderNameET.getText().toString();
    	String folderPath = Environment.getExternalStorageDirectory() + "/" + folderName;
    	
		File folder = new File(folderPath);
		if(!folder.exists()) {
			pLog("OOPS no such folder: "+folder);
			return null;
		}
		File[] listOfFiles = folder.listFiles();
		if(listOfFiles == null) {
			pLog("OOPS no such folder: "+folder);
			return null;
		}
		else {
			try {
			tv.setText(folderPath+"\nfile count:   "+listOfFiles.length+"\nupload count: "+ucount);
			} catch(Exception e) {};
		}
		
		// ensure sorted by modification time
		Arrays.sort(listOfFiles, new Comparator<File>(){
		    public int compare(File f1, File f2)
		    {
		        return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
		    } });
		
		return listOfFiles;
	}
	
	//---------------------------------------------------------------------------------
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
//	    inflater.inflate(R.layout.rbnb_menu, menu);
	    return true;
	}

	//---------------------------------------------------------------------------------	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}

	//---------------------------------------------------------------------------------	
	private void TimerMethod()
	{
		//This method is called directly by the timer
		//and runs in the same thread as the timer.

		//We call the method that will work with the UI
		//through the runOnUiThread method.
		this.runOnUiThread(Timer_Tick);
	}

//---------------------------------------------------------------------------------
	private Runnable Timer_Tick = new Runnable() {
		public void run() {
		//This method runs in the same thread as the UI.    	       
			if(Running) {
//			if(runBtn.isChecked()) {
				stopBtn.setChecked(false); runBtn.setChecked(true);
				FileWatch();
				iterCount++;
//				tv.setText("Running: "+(int)(iterCount*updateInc));
			} else {
				if(iterCount > 0) {
					pLog("Stopped.");
					stopBtn.setChecked(true); runBtn.setChecked(false);
				}
				iterCount=0;
			}
		}
	};
 
//---------------------------------------------------------------------------------
	private void pLog(String msg) {
		tv.setText(msg);
		Log.d(TAG,msg);
	}
	
//---------------------------------------------------------------------------------
	
	private void putDBfile(String fpath, boolean deleteFlag) throws Exception {
		File inputFile = new File(fpath);
    	String sName = sourceNameET.getText().toString();
    	if(sName.length() <= 0) sName = TAG;
    	
        DBupload upload = new DBupload(this, mApi, sName, inputFile, deleteFlag);
        try {
        	upload.execute();
        } catch(Exception e) {
        	Running=false;			// whoa
        }
        
		pLog("Uploaded: " + inputFile.getName()+" to: "+sName);
	}
	
//---------------------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        mApi = dba.resume();
        if(mApi == null) {
             showToast("Couldn't authenticate with Dropbox");
             Log.i(TAG, "Error authenticating");
        }
    }

//---------------------------------------------------------------------------------
    private void showToast(String msg) {
        Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        error.show();
    }
};
 

