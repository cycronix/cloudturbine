
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

package cycronix.CTandroid;

// Android version of CTserver
// Serve CT data files 
// Matt Miller 7/2014

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.conn.util.InetAddressUtils;

import ctserver.CTserver;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.util.Log;

public class CTAserver extends Activity {
 
	private static boolean Running=false;
	
	private static String serverPort = "8000";
	private static String dataFolder = "CTdata";		// default
	private static String resourceFolder = "CTweb";
	private static final String TAG = "CTserver";
	private static String PREFS_NAME="CTAServer_prefs";
	
	private RadioButton stopBtn, runBtn, quitBtn;
	private EditText serverPortET, dataFolderET, myipET, statusET, counterET, memoryET;
	private CTserver cts = null;
	private Timer myTimer;
	private Runtime runTime = Runtime.getRuntime();
	
	//---------------------------------------------------------------------------------	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	getSettings(); 	// Restore preferences
    	copyAssets();	// copy assets to external storage
    	
    	setContentView(R.layout.main);
    	statusET = (EditText)findViewById(R.id.myText);
    	if(statusET != null) statusET.setText("Not Started.");
    	statusET.setEnabled(false);
    	myipET = (EditText)findViewById(R.id.myIP);
    	if(myipET != null) myipET.setText(getIPAddress(true));
    	myipET.setEnabled(false);
    	counterET = (EditText)findViewById(R.id.Counter);
    	counterET.setEnabled(false);
    	memoryET = (EditText)findViewById(R.id.Memory);
    	memoryET.setEnabled(false);
    
    	// status timer
    	myTimer = new Timer();
    	myTimer.schedule(new TimerTask() {
    		@Override public void run() { TimerMethod(); }
    	}, 0,1000); 		// check interval
    	
    	// get GUI objects
    	serverPortET = (EditText)findViewById(R.id.serverPort);
    	serverPortET.setText(serverPort);
    	dataFolderET = (EditText)findViewById(R.id.dataFolder);
    	dataFolderET.setText(dataFolder);

    	runBtn = (RadioButton)findViewById(R.id.RunButton);
    	stopBtn = (RadioButton)findViewById(R.id.StopButton);
    	quitBtn = (RadioButton)findViewById(R.id.QuitButton);
    
    	runBtn.setOnClickListener(new OnClickListener() {
    		@Override
    		public void onClick(View v) {
    			if(!Running) {
    		    	try {
    		    		pLog("Starting up...");
    		    		putSettings();
    		    		Running = true;
    		    		int port = Integer.valueOf(serverPortET.getText().toString());
    		    		String resourceFolderPath = Environment.getExternalStorageDirectory() + "/" + resourceFolder;
    		    		String dataFolderPath = dataFolderET.getText().toString();		// full path
    		    		if(!dataFolderPath.startsWith("/")) 							// relative to external storage dir
    		    			dataFolderPath = Environment.getExternalStorageDirectory() + "/" + dataFolderPath;
//    		    		CTserver.debug=true;
    		    		cts = new CTserver(port, resourceFolderPath, dataFolderPath);			// start local webserver
    		    		cts.start();
    		    		serverPortET.setEnabled(false);
    		    		dataFolderET.setEnabled(false);
    		        	pLog("Running!");
    		    	} catch(Exception e) {
    		    		pLog("CTserver Launch Error: "+e);
    		    		finish();
    		    	}
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
    				if(cts != null) cts.stop();		// stop local webserver
    				pLog("Stopped.");
		    		serverPortET.setEnabled(true);
		    		dataFolderET.setEnabled(true);
		    		System.gc();   // force a garbage collection
    			}
    		}
    	} );
    	
    	quitBtn.setOnClickListener(new OnClickListener() {
    		@Override
    		public void onClick(View v) {
    			pLog("Quitting App...");
    			Running=false;
    			stopBtn.setChecked(true);
    			putSettings();
    			finish();
    		}
    	} );
    }
 
    @Override
    protected void onDestroy() {
  	  	Log.d(TAG,"onDestroy");
        super.onDestroy();
       putSettings();		 // Save user preferences

    }  
    
    private void getSettings() {
    	// Restore preferences
    	SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        if(settings == null) {
        	Log.w(TAG,"Could not obtain settings file!");
        	return;
        }
        serverPort = settings.getString("serverPort", serverPort);
    	dataFolder = settings.getString("dataFolder", dataFolder);
    }
    
    private void putSettings() {
        // Save user preferences
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        if(settings == null) {
        	Log.w(TAG,"Could not obtain settings file!");
        	return;
        }
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("ServerPort", serverPortET.getText().toString());
        editor.putString("dataFolder", dataFolderET.getText().toString());
        editor.apply();
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
	int iterCount=0;
	private Runnable Timer_Tick = new Runnable() {	//This method runs in the same thread as the UI.    	       
		public void run() {
			memoryET.setText(""+((runTime.totalMemory()-runTime.freeMemory())/1048576)+" MB");

			if(Running) {
//				stopBtn.setChecked(false); runBtn.setChecked(true);
				iterCount++;
				counterET.setText(""+cts.queryCount());
				statusET.setText("Running...  "+iterCount+" sec");	
			} else {
				if(iterCount > 0) {
					statusET.setText("Stopped.");	
//					stopBtn.setChecked(true); runBtn.setChecked(false);
				}
				iterCount=0;
			}
		}
	};
	//---------------------------------------------------------------------------------
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
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
	private void pLog(String msg) {
		statusET.setText(msg);
		Log.d(TAG,msg);
	}

//---------------------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
    }

//---------------------------------------------------------------------------------
// copy asset resource files to external storage
    
    private void copyAssets() {
    	AssetManager assetManager = getApplicationContext().getAssets();

    	String[] files = null;
    	try {
    		files = assetManager.list(resourceFolder); // folder name
    	} catch (Exception e) {
    		Log.e(TAG, "ERROR: " + e.toString());
    	}

    	File directory = new File(Environment.getExternalStorageDirectory()+"/"+resourceFolder);
    	directory.mkdirs();
    	
    	for (int i = 0; i < files.length; i++) {
    		try {
    			InputStream in = assetManager.open("CTweb/" + files[i]);
    			OutputStream out = new FileOutputStream(Environment.getExternalStorageDirectory() + "/" + resourceFolder +"/" + files[i]);
    			byte[] buffer = new byte[65536];
    			int read;
    			while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
    			in.close();	out.flush(); out.close();
    			Log.d(TAG, "Asset file copied to SD card: "+files[i]);
    		} catch (Exception e) {
    			Log.e(TAG, "ERROR: " + e.toString());
    		}
    	}
    }
 
//---------------------------------------------------------------------------------
    /**
     * Get IP address from first non-localhost interface
     * @param ipv4  true=return ipv4, false=return ipv6
     * @return  address or empty string
     */
    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress().toUpperCase();
                        boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr); 
                        if (useIPv4) {
                            if (isIPv4) 
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 port suffix
                                return delim<0 ? sAddr : sAddr.substring(0, delim);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) { } // for now eat exceptions
        return "Unknown";
    }
    
};
 

