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

// Simple video display (no options)
// Matt Miller, Cycronix, 7-6-2017

public class CTvideo : MonoBehaviour {
	public string url = "http://localhost:8000/CT/CTstream/webcam.jpg";
	public float pollInterval = 0.1f;			// polling interval for new data (sec)
	private Boolean showImage = false;
	private Texture startTexture;

	// Use this for initialization
	void Start () {
		startTexture = GetComponent<Renderer> ().material.GetTexture ("_MainTex");
		StartCoroutine("DownloadImage");
	}

	IEnumerator DownloadImage()
	{
		while (true) {
			yield return new WaitForSeconds (pollInterval);

			if (showImage) {
//				Debug.Log ("ShowImage");
				WWW www = new WWW (url);
				yield return www;

				Texture2D tex = new Texture2D (www.texture.width, www.texture.height, TextureFormat.DXT1, false);
				www.LoadImageIntoTexture (tex);
				GetComponent<Renderer> ().material.mainTexture = tex;

				www.Dispose ();
				www = null;
			} else {
//				Debug.Log ("ClearImage");
				GetComponent<Renderer> ().material.mainTexture = startTexture;
			}
		}
	}

	public void OnMouseDown() {
//		Debug.Log ("MouseDown");
		showImage = !showImage;		// toggle
	}
}
