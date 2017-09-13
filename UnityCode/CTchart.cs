/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

// Stripchart -OR- Cross-plot X-Y chart from CloudTurbine data
// Matt Miller, Cycronix, 6-16-2017

public class CTchart : MonoBehaviour {
	private string Server = "http://localhost:8000";
	private string Source = "CTmousetrack";
	private string Chan1 = "x";
	private string Chan2 = "y";
	private string Mode = "StripChart";
	private int numChan=0;						// NumDims: 1=stripchart, 2=xyplot

	public int MaxPts = 500;					// max points in data "trail"
	public float pollInterval = 0.05f;			// polling interval for new data (sec)

	private Queue<Vector2> 	xybuf = new Queue<Vector2>();
	private Queue<float> 	ybuf = new Queue<float>();

	private double gotTime = 0;
	private CTchartOptions chartOptions = null;

	private GameObject Chart1;
	private GameObject Chart2;

	private LineRenderer lineR1;
	private LineRenderer lineR2;

	void Start()
	{
		foreach (Transform child in transform) {
			if (child.name == "Chart1") {
				Chart1 = child.gameObject;
				Chart1.AddComponent<LineRenderer> ();
				lineR1 = Chart1.GetComponent<LineRenderer>();
			}
			if (child.name == "Chart2"){
				Chart2 = child.gameObject;
				Chart2.AddComponent<LineRenderer> ();
				lineR2 = Chart2.GetComponent<LineRenderer>();
			}
		}

		setLineProps (lineR1, Color.blue);
		setLineProps (lineR2, Color.red);

		StartCoroutine("getData");
	}

	void Update() {

		if (chartOptions == null) {
			foreach (Transform child in transform) {
				if (child.name == "ChartOptions") {
//					Debug.Log ("Found chartOptions: " + child.name);
					chartOptions = child.gameObject.GetComponent<CTchartOptions>();
				}
			}
		} else {
//			Debug.Log ("chartOptions Chan1: " + chartOptions.Chan1+", chartOptions.Mode: "+chartOptions.Mode);
			Server = chartOptions.Server;
			Source = chartOptions.Source;
			Chan1 = chartOptions.Chan1;
			Chan2 = chartOptions.Chan2;
			Mode = chartOptions.Mode;
			MaxPts = chartOptions.MaxPts;
		}

		UpdateLine ();
	}

	void setLineProps (LineRenderer lineR, Color linecolor) {
		lineR.positionCount = MaxPts;
		lineR.loop = false;
		lineR.useWorldSpace = false;
		lineR.widthMultiplier = 0.02f;
		lineR.material.color = linecolor;
		lineR.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
		lineR.receiveShadows = false;
	}

	// fetch XY values from CloudTurbine, store in FIFO (queue)
	IEnumerator getData()
	{
		while (true) {
			yield return new WaitForSeconds (pollInterval);	

			// first time is newest single point, then interval after last gotTime
			string urlparams = "?f=d";
			if (gotTime > 0) urlparams += "&t=" + (gotTime+0.001) + "&d=10000";	

			// two channels = two HTTP GETs
			WWW www1=null;
			WWW www2=null;

			// figure out xplot and chart1/2 situation
			if (Chan2.Length > 0) 	numChan = 2;
			else					numChan = 1;
//			Debug.Log ("Mode: " + Mode + ", numChan: " + numChan);

			// notta 
			if (Chan1.Length == 0) {
				lineR1.positionCount = 0;
				ybuf.Clear ();
				yield return null;
			}
			if (Chan2.Length == 0 || Mode=="CrossPlot") {
				lineR2.positionCount = 0;
			}

			string url1 = Server + "/CT/" + Source + "/" + Chan1 + urlparams;
			www1 = new WWW(url1);

			string url2 = Server + "/CT/" + Source + "/" + Chan2 + urlparams;
			if (numChan > 1) www2 = new WWW (url2);
				
			yield return www1;
			if(numChan > 1) yield return www2;

			if (!string.IsNullOrEmpty (www1.error)) {
//				gotTime = 0;
			}
			else {
				try {
					// fetch time-interval info from header (vs timestamps)
					Dictionary<string,string> whead = www1.responseHeaders;
					double htime = 0, hdur = 0;
					try {
						if (whead.ContainsKey ("time")) 	htime = double.Parse (whead ["time"]);
						if (whead.ContainsKey ("duration"))	hdur = double.Parse (whead ["duration"]);
					} catch (Exception) {
						Debug.Log ("Exception on htime parse!");
					}
					gotTime = htime + hdur;
//					Debug.Log("url1: "+url1+", gotTime: "+gotTime+", hdur: "+hdur);

					// parse data into value queues
					string[] xvals = www1.text.Split ('\n');
//					Debug.Log("CTchart xvals size: "+xvals.Length);

					if(numChan> 1) {
						string[] yvals = www2.text.Split ('\n');

						int maxCount = Math.Min (xvals.Length, yvals.Length);
						for (int i = 0; i < maxCount; i++) {
							try {
								xybuf.Enqueue (new Vector2(float.Parse(xvals [i]), float.Parse(yvals [i])));
							} catch(Exception) {};		// possible NotFound results in non-float text
						}
					}
					else {
						for(int i=0; i<xvals.Length; i++) {
							try {
								ybuf.Enqueue (float.Parse(xvals[i]));
							} catch(Exception) {};
						}

					}
				} catch (FormatException) {
					Debug.Log ("Error parsing values!");
					gotTime = 0;
				}
			} 

			www1.Dispose ();  	
			www1 = null;
			if (numChan > 1) {
				www2.Dispose ();
				www2 = null;
			}
		}
	}

	// Async update the LineRenderer with queued plot-points
	void UpdateLine()
	{
//		Debug.Log ("CTchart UpdateLine, xycount: " + xybuf.Count + ", ybuf.count: " + ybuf.Count);
		while (xybuf.Count > MaxPts) xybuf.Dequeue();		// limit size of queue
		while (ybuf.Count  > MaxPts) ybuf.Dequeue();

		// TO DO:  scale data (now presumes normalized 0-1 data range)

		if (Mode == "CrossPlot") {								// crossplot
			Vector2[] xv = xybuf.ToArray ();
			lineR1.positionCount = xv.Length;
			for (int i = 0; i < xv.Length; i++) {
				lineR1.SetPosition (i, new Vector3 (xv[i].x - 0.5f, xv [i].y - 0.5f, -0.6f));
			}
		} else {										// single-chan stripchart (per chart object)
			if (numChan == 1) {
				float[] yv = ybuf.ToArray ();
				lineR1.positionCount = yv.Length;
				float x1 = -0.5f;
				float dx = 1.0f / (MaxPts - 1);
				for (int i = 0; i < yv.Length; i++) {
//					float y = yv [i] - 0.5f;			// mousetrack
					float y = (yv [i] / 65536.0f);		// audio
					lineR1.SetPosition (i, new Vector3 (x1, y, -0.6f));
					x1 += dx;
				}
			}
			if (numChan == 2) {
				Vector2[] xv = xybuf.ToArray ();
				lineR1.positionCount = xv.Length;
				lineR2.positionCount = xv.Length;
				float x1 = -0.5f;
				float dx = 1.0f / (MaxPts - 1);
				for (int i = 0; i < xv.Length; i++) {
					lineR1.SetPosition (i, new Vector3 (x1, xv[i].x - 0.5f, -0.6f));
					lineR2.SetPosition (i, new Vector3 (x1, xv[i].y - 0.5f, -0.6f));
					x1 += dx;
				}
			}
		}
	}
		
}
