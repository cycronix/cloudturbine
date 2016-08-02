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
 
/*
 * CloudTurbine Accelerometer Capture Utility
 * Matt Miller
 * Cycronix
 * 8/28/2015
 */

package ctandroid;

import cycronix.ctlib.*;

import com.example.ctandroid.R;

import android.hardware.Sensor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
// import android.os.StrictMode;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class CTandroidACL extends Activity {
	final String TAG="CTandroidACL";

//	String sourceFolder = Environment.getExternalStorageDirectory() + "/CTdata/AndroidAccel";		// folder to write data
	int mrate = 10;								// msec sampling interval (~100Hz is max possible on Android)
	int nmax = 1000000;							// fail-safe quit
	int blockDur = 1000;						// block-mode interval (msec)
	
	boolean Running=false;
	TextView tv;

	String sourceFolder = "CTdata/AndroidACL";		// FTP remote folder (not local sdcard)
	String ftpHost = "";
	String ftpUser = "";
	String ftpPassword = "";
	int rateIndex = 2;								// slow, medium, fast msec intervals
	int[] rateList = { 5000, 2000, 1000, 500 };		// block-intervals (msec)
	
	SharedPreferences sharedPref;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		initInputs();
		
//		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build(); 
//		StrictMode.setThreadPolicy(policy); 
	    RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioGroup1);        
	    radioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() 
	    {
	        @Override
	        public void onCheckedChanged(RadioGroup group, int checkedId) {
	    		RadioGroup rg = ((RadioGroup) findViewById(R.id.radioGroup1));
	    		rateIndex = rg.indexOfChild(rg.findViewById(checkedId));
	        }
	    });
	    
		Button btnStart = (Button) findViewById(R.id.Start);
		btnStart.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getInputs();
				Toast.makeText(CTandroidACL.this, "Writing files to "+sourceFolder+"...", Toast.LENGTH_SHORT).show();
				Running=true;
				new collectAccel().execute();
			}
		});  

		Button btnStop = (Button) findViewById(R.id.Stop);
		btnStop.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Toast.makeText(CTandroidACL.this, "Stopping...", Toast.LENGTH_SHORT).show();
				Running=false;
			}
		}); 

		Button btnQuit = (Button) findViewById(R.id.Quit);
		btnQuit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveInputs();
				Toast.makeText(CTandroidACL.this, "Exiting...", Toast.LENGTH_SHORT).show();
				Running=false;
				finish();
			}
		}); 

//		((TextView)findViewById(R.id.Status)).setText("Folder: "+sourceFolder);
		tv = (TextView)findViewById(R.id.Status);
	}

	// this needs to run Async so network IO (i.e. FTP) will run without throwing exception
	private class collectAccel extends AsyncTask<Void, Void, Void> {
		CTftp ctw;
		int icount = 0;
		SensorManager sensorManager=null;
		SensorEventListener mListener=null;
		
		collectAccel() {}
		
		@Override
		protected Void doInBackground(Void... voids) {
			try {
				ctw = new CTftp(sourceFolder);
				ctw.login(ftpHost,ftpUser,ftpPassword);			// needs to be from GUI or anononymous ftp...
 
				ctw.setZipMode(true);					// bundle to zip files
				ctw.autoFlush(rateList[rateIndex]);		// bundle at intervals
				ctw.setDebug(false);
				ctw.setBlockMode(true);			// block data with one timestamp per glump (at each flush)
			
				sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
				HandlerThread mHandlerThread = new HandlerThread("AccelListener");
				mHandlerThread.start();
				Handler handler = new Handler(mHandlerThread.getLooper());
				//	sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 1000*mrate);

				// Listener Method for Accelerometer updates
				mListener = new SensorEventListener() {
 
					@Override
					public void onSensorChanged(SensorEvent event) {
//						Log.i(TAG,"onSensorChanged, icount: "+icount+", nsamp: "+event.values.length);
						tv.post(new Runnable() { public void run() { tv.setText("Running: "+icount); } });	// can't tv.setText directly; not on same thread
						synchronized (this) {
							try { 
								if(Running && (icount < nmax)) {
									ctw.setTime();
									if(event.values.length > 0) ctw.putData("ax.f32", event.values[0]);	// event.values.length > 1 for batch mode
									if(event.values.length > 1) ctw.putData("ay.f32", event.values[1]);
									if(event.values.length > 2) ctw.putData("az.f32", event.values[2]);
									icount++;
									
									if(icount%10==0)		// can't tv.setText directly; not on same thread
										tv.post(new Runnable() { public void run() { tv.setText("Running: "+icount); } });	
								} else {		// all done
									tv.post(new Runnable() { public void run() { tv.setText("Stopped: "+icount); } });
									ctw.flush(true);
									sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
								}
							} catch(Exception e) {
								Log.e(TAG,"Exception onSensorChanged: "+e);
							}
						}
					}

					@Override
					public void onAccuracyChanged(Sensor sensor, int accuracy) {
					}
				};

				// register listener and start
//				sensorManager.registerListener(mListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 1000*mrate, 1000*batchDur, handler);
				sensorManager.registerListener(mListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 1000*mrate, handler);
			} catch(Exception e) {
				Log.e(TAG,"Exception collectAccel: "+e);
			}
			
			return null;
		}
	}
	
	private void initInputs() {
		sharedPref = getPreferences(Context.MODE_PRIVATE);
		ftpHost = sharedPref.getString("Host", ftpHost);
		ftpUser = sharedPref.getString("User", ftpUser);
		ftpPassword = sharedPref.getString("Password", ftpPassword);
		sourceFolder = sharedPref.getString("Folder", sourceFolder);
		rateIndex = sharedPref.getInt("Rate", rateIndex);
		
		((TextView)findViewById(R.id.Host)).setText(ftpHost);
		((TextView)findViewById(R.id.User)).setText(ftpUser);
		((TextView)findViewById(R.id.Password)).setText(ftpPassword);
		((TextView)findViewById(R.id.Folder)).setText(sourceFolder);
				
		if(rateIndex < 0 || rateIndex >= rateList.length) rateIndex = 0;
		((RadioButton) ((RadioGroup) findViewById(R.id.radioGroup1)).getChildAt(rateIndex)).setChecked(true);
	}
	
	private void getInputs() {
		sourceFolder = ((EditText) findViewById(R.id.Folder)).getText().toString();
		ftpHost = ((EditText) findViewById(R.id.Host)).getText().toString();
		ftpUser = ((EditText) findViewById(R.id.User)).getText().toString();
		ftpPassword = ((EditText) findViewById(R.id.Password)).getText().toString();
		RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioGroup1);
		rateIndex = radioGroup.indexOfChild(findViewById(radioGroup.getCheckedRadioButtonId()));
		saveInputs();
	}
	
	// save preferences
	private void saveInputs() {
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString("Host", ftpHost);
		editor.putString("User", ftpUser);
		editor.putString("Password", ftpPassword);
		editor.putString("Folder", sourceFolder);
		editor.putInt("Rate", rateIndex);
		editor.commit();
	}
	
}
