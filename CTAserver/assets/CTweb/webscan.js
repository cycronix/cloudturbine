/*
Copyright 2014 Cycronix

WebScan V1.0 was developed under NASA Contract NAS4-00047 
and the U.S. Government retains certain rights.

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
 * WebScan
 * Matt Miller, Cycronix
 * 02/2014 - 06/2014
 * 
 * v0.1: Initial prototype release
 * v0.5: Initial playback control buttons
 * v0.6: Drag screen to scroll time
 * V0.9: Save configuration
 * V1.0: rtSync tweeks, mDur RT lookback for sporadic sources
 * V1.0: webscan generics
 * V2.0: incorporate video/generic display objects...
 * V2.0A2:  add scroll bar, reorganize display
 * V2.0B1:	merged javascript files for easier delivery
 */

//----------------------------------------------------------------------------------------	
// globals of control variables
var myName="webscan";
//var servletRoot="/CT";			// "CT" works only for CTserver	
var servletRoot="/RBNB";			// "/RBNB" or "/CT" ("RBNB" works for both CTserver and WebTurbine)
var serverAddr="";			// cross domain server...
//var serverAddr="";				// "" for local access

var tDelay=1000;					// initial data fetch interval (msec)
var doRate=true;					// set true to support UI rate selection

var debug=false;					// turn on some console.log prints
//var extraFetch=0.;

// some globals for callback functions
var channels = new Array();			// list of selectable channels (all plots)
var intervalID=0;					// timer id for start/stop
var intervalID2=0;					// timer id for start/stop (fast 10x rate)
var noRebuild=false;				// defer rebuild during smartphone selections
var plots = new Array();			// array of plots
//var plotbox = new Array();			// array of plot-boxes (generic display)
var doFill=false;					// fill under-line
var doSmooth=true;					// smooth plot lines
var serverTimes=true;				// server-side timestamps, or client time-of-day
//var refTime=null;					// refTime for fetch ("oldest", "newest", "absolute")
var inProgress=0;
//var LEtime=0;						// left edge (oldest) time
//var plotTime=0;						// master plot display time
var lastreqTime=0;					// right edge most recent request time
var lastgotTime=0;					// right edge (newest) time
var oldgotTime=0;					// left edge (oldest) time
var oldestTime=0;					// oldest available time (refTime="oldest")
var newgotTime=0;					// right edge (newest) time
var newestTime=0;					// newest available time (refTime="newest")
var stepDir=0;						// flag which way stepping
var refreshInProgress=false;		// flag full-refresh collection cycle
var isTouchSupported=false;
var singleStep=false;				// set based on RTrate/View ratio
var rtSync=0;						// RT time-adjust client-server lag
var isImage=false;					// latest plot is image?
var numCol=0;						// numcols per plot row (0=auto)

top.rtflag=0;						// RT state (for eavesdroppers)
top.plotTime=0;						// sec
top.plotDuration=0;					// sec

var PAUSE=0;						// play mode pseudo-constants
var RT=1;
var PLAY=2;

//----------------------------------------------------------------------------------------
//webscan:  top level main method

function webscan() {
	if(debug) console.log('start');
	HTML5check();
	
	myName = window.location.host + window.location.pathname;		// store cookies unique to full URL path
//	console.debug("getCookie("+myName+"): "+getCookie(myName));
	
	if(!doRate) {
		document.getElementById("myUpdate").style.display = 'none';		// hide RTrate select
		document.getElementById("myUpdateLabel").style.display = 'none';		// hide RTrate select
		var dt = getURLValue('dt');
		if(dt != null) { tDelay=parseInt(dt); setConfig('dt',tDelay);	}
	}

	if(getURLValue('debug') == 'true')		 debug=true;
	if(getURLValue('reset') == 'true') 		 resetConfig();						// reset to defaults
	else if(getURLValue('reload') == 'true') { reloadConfig(); return; }		// use previous cookie-save
//	else if(getURLValue('n') == null) 		 { reloadConfig(); return; }		// default:  reload previous?
	else									 urlConfig();						// use url-params

	setTime(new Date().getTime());

	fetchChanList();					// build source select pull down
	if(plots.length == 0) setPlots(1);	// start with one empty plot
//	goEOF();							// end of file to start
	setPlay(PAUSE,0);					// Pause to start

	//refresh on resize after 1sec delay (avoid thrash)

	var timeOut=null;
	window.onresize = function() {
		if(timeOut != null) clearTimeout(timeOut);
		timeOut = setTimeout( function(){ rebuildPage(); },200); 
	};

	buildCharts();					// build  stripcharts
	setTimeout(function(){buildCharts();}, 500); 		// rebuild after init? (for chartscan, complete channel lists)
//	goEOF();						// start at newest data
	getLimits(1,1);					// establish time limits
	setTimeout(function(){ goBOF();}, 1000); 		// make sure data is shown at startup

}

//----------------------------------------------------------------------------------------
// HTML5check:  check for HTML5 browser

function HTML5check() {
	var test_canvas = document.createElement("canvas") //try and create sample canvas element
	var canvascheck=(test_canvas.getContext)? true : false //check support for getContext() canvas element method
	if(canvascheck==false) {
		alert("Warning: HTML5 Browser Required");
	}
}

//----------------------------------------------------------------------------------------
//GetURLValue:  get URL munge parameter

function getURLValue(key) {
	return getURLParam(window.location.search.substring(1), key);
}

function getURLParam(uri, key) {
	if(uri==null || typeof(uri)=='undefined') return null;
	var value=null;
	var VariableArray = uri.split(/[?&]+/);
	for(var i = 0; i < VariableArray.length; i++){
		var KeyValuePair = VariableArray[i].split('=');
		if(KeyValuePair[0] == key){
			value = unescape(KeyValuePair[1]);
		}
	}
//	if(value) console.log('getURLParam: '+key+' = '+value);
	return value;
}

//----------------------------------------------------------------------------------------
//setURLParam:  update or add query string parameter to URL

function setURLParam(uri, key, value) {
	var evalue = escape(''+value);
	if(uri==null) uri="";
	var newuri = uri;
	var re = new RegExp("([?|&])" + key + "=.*?(&|$)", "i");
	separator = uri.indexOf('?') !== -1 ? "&" : "?";
	if (uri.match(re)) 	{
		if(value == null)		newuri = uri.replace(re,'');
		else					newuri = uri.replace(re, '$1' + key + "=" + evalue + '$2');
	} else if(value != null)	newuri = uri + separator + key + "=" + evalue;
//	window.location.href = newuri;
	return newuri;
}

//----------------------------------------------------------------------------------------
//setCookie, getCookie:  W3C cookie setting with expiration

function clearCookies() {
    var cookies = document.cookie.split(";");

    for (var i = 0; i < cookies.length; i++) {
    	var cookie = cookies[i];
    	var eqPos = cookie.indexOf("=");
    	var name = eqPos > -1 ? cookie.substr(0, eqPos) : cookie;
    	document.cookie = name + "=;expires=Thu, 01 Jan 1970 00:00:00 GMT";
    }
}

function setCookie(c_name,value,exdays)
{
	if(typeof(exdays)=='undefined') exdays=365;
	var exdate=new Date();
	exdate.setDate(exdate.getDate() + exdays);
	var c_value=escape(value) + ((exdays==null) ? "" : "; expires="+exdate.toUTCString());
	document.cookie=c_name + "=" + c_value;
}

function getCookie(c_name)
{
	var c_value = document.cookie;
	var c_start = c_value.indexOf(" " + c_name + "=");
	if (c_start == -1) c_start = c_value.indexOf(c_name + "=");
	if (c_start == -1) c_value = null;
	else {
		c_start = c_value.indexOf("=", c_start) + 1;
		var c_end = c_value.indexOf(";", c_start);
		if (c_end == -1) c_end = c_value.length;
		c_value = unescape(c_value.substring(c_start,c_end));
	}
	return c_value;
}

//----------------------------------------------------------------------------------------
//setConfig:  update configuration with key=value.  store in cookie.

function setConfig(key,value) {
	setCookie(myName, setURLParam(getCookie(myName),key,value));
//	console.log('setConfig, key: '+key+', value: '+value+', cookie: '+getCookie(myName));
}

function getConfig(param) {
	var cookie = getCookie(myName);
	return getURLParam(cookie,param);
}

function reloadConfig() {
	stopRT();			// stop RT
	configParams(getCookie(myName));
	window.location.href = location.protocol + '//' + location.host + location.pathname + getCookie(myName);
}

function configParams(src) {
	var dt 		 = getURLParam(src,'dt');	if(dt != null) setRate(dt);						setConfig('dt',tDelay);
	var nplot 	 = getURLParam(src,'n');  	if(nplot != null) setPlots(parseInt(nplot));	setConfig('n', nplot);
	var uncol 	 = getURLParam(src,'c');  	if(uncol != null) setCols(parseInt(uncol));		setConfig('c', numCol);
	var fill 	 = getURLParam(src,'f');	setFill(fill=="true");							setConfig('f', fill=="true");
	var smooth 	 = getURLParam(src,'s');	setSmooth(smooth=="true");						setConfig('s', smooth=="true");
	var duration = getURLParam(src,'v');	if(duration != null) setDuration(duration);		setConfig('v', duration);
	
//	console.debug('configParams, tDelay: '+tDelay+", nplot: "+nplot);
	
	for(var i=0; i<nplot; i++) {
		for(var j=0; j<10; j++) {
			var chan = getURLParam(src,'p'+i+''+j);		
			setConfig('p'+i+''+j,chan);
			if(chan != null) plots[i].addParam(chan);
		}
	}
	setPlay(PAUSE,0);			// (re)start paused state
}

function urlConfig() {
	resetConfig();
	configParams(window.location.search.substring(1));
}

function resetConfig() {
	clearCookies();
	setCookie(myName,"",-1);
}

//----------------------------------------------------------------------------------------
// setRate:  initialize and set UI selection to match param

function setRate(dt) {
	tDelay = parseInt(dt);
	var el = document.getElementById('myUpdate');		// msec
	for(var i=0; i<el.options.length; i++) {
		if(dt == el.options[i].value) {		// enforce consistency
			el.options[i].selected=true;
			break;
		}
	}
}

//----------------------------------------------------------------------------------------
//setPlots:  initialize and create nplot plots

function setPlots(nplot) {
	if(nplot == plots.length) return;		// notta
	setConfig('n',nplot);

	if(nplot > plots.length) {
		for(var i=plots.length; i<nplot; i++) {
			plots.push(new plotbox({doFill:doFill,doSmooth:doSmooth}));
		}
	} else {
		for(var i=nplot; i<plots.length; i++) {
			if(plots[i]) plots[i].clear();		// clear charts (if defined, not on IE?)
			for(var j=0;j<10;j++) setConfig('p'+i+''+j,null);		// remove from config
		}
		plots.splice(nplot,plots.length-nplot);					// rebuild list
	}
	var el = document.getElementById('nplot');
	for(var i=0; i<el.options.length; i++) {
		if(nplot == el.options[i].value) {		// enforce consistency
			el.options[i].selected=true;
			break;
		}
	}
}

//----------------------------------------------------------------------------------------
//setCols:  initialize numCols

function setCols(ncol) {
	numCol = ncol;
	var el = document.getElementById('Ncol');
	for(var i=0; i<el.options.length; i++) {
		if(ncol == el.options[i].value) {		// enforce consistency
			el.options[i].selected=true;
			break;
		}
	}
}

//----------------------------------------------------------------------------------------
//fetchData:  Use AJAX to fetch data

function fetchData(param, plotidx, duration, time, refTime) {		// duration (msec)
	if((typeof(param) == 'undefined') || param == null) return;			// undefined
	
	if(debug) console.log('fetchData, param: '+param+', duration: '+duration+', time: '+time+", refTime: "+refTime);
//	if(inProgress >= 2) return;		// skip fetching if way behind?
	
	var munge="";
	isImage = endsWith(param, ".jpg");	// this is a global, affects logic based on last-plot (still issue with mixed stripcharts/images)
	// audio with video: fetch as binary
	var isAudio = ((plots[plotidx].type=='video') && 
			(endsWith(param, ".pcm") || endsWith(param, ".mp3")));			// FFMPEG s16sle, or MP3 audio
	
	if(isImage) {									
		munge = "?dt=b";						// binary fetch
		if(refTime) munge += ("&r="+refTime);
	} 
	else if(isAudio) {	
		munge = "?dt=b";						// binary fetch
		if(top.rtflag == PAUSE) munge += ("&d="+0.2);					// fix binary audio duration?
		else					munge += ("&d="+duration/1000.);		// rt playback presumes duration increment steps...
		if(refTime) munge += ("&r="+refTime);
	}
	else {
		if(serverTimes) munge = "?dt=s&f=b";	// requires RBNB >= V3.3B2
		else			munge = "?dt=s";
		munge += ("&d="+duration/1000.);
		if(refTime) { munge += ("&r="+refTime); }
		if(refTime == "absolute") lastreqTime = time + duration;		// right edge time (only update on stripcharts)
	}
	
	if(refTime == "absolute" || refTime == "next" || refTime == "prev") munge+=("&t="+time/1000.);
	var url = serverAddr + servletRoot+"/"+escape(param)+munge;
	
	if(isImage) setImage(url, param, plotidx, time, refTime);
	else if(isAudio) {
		if(endsWith(param,".mp3")) 	setAudioMP3(url);
		else						setAudio(url);
	}
	else {
		AjaxGet(setParamValue, url, arguments);		// 'arguments' javascript keyword
		inProgress++;
//		if(debug) console.debug('fetchData inProgress: '+inProgress)
		document.body.style.cursor = 'wait';
	}
}

//----------------------------------------------------------------------------------------
// utility function
function endsWith(str, suffix) {
    return str.indexOf(suffix, str.length - suffix.length) !== -1;
}

//----------------------------------------------------------------------------------------
// setImage:  make and display image

function setImage(url, param, pidx, time, refTime) {	
	if(debug) console.log("fetch image: "+url);
	plots[pidx].display.setImage(url);
	if(top.rtflag!=RT) {
		plots[pidx].display.setTime(url);		// double-fetch for time (inefficient), jitters with rtPlay
	}
	if(refTime == "oldest") {
//		plots[pidx].display.setTime(url);
//		oldestTime = oldgotTime = lastgotTime = plots[pidx].display.getTold();			// needs work
//		setTime(oldestTime);			// getDuration for consisted "right-edge" time?
	}
	else if (refTime == "newest") {
//		plots[pidx].display.setTime(url);
//		newestTime = newgotTime = lastgotTime = plots[pidx].display.getTnew();			// needs work
//		setTime(newestTime);
	}
	
}

//----------------------------------------------------------------------------------------
// setAudio:  make and play audio request

//var ascan = null;
function setAudio(url) {
	if(debug) console.log("fetch audio: "+url);
//	if(ascan == null) ascan = new audioscan();		// save recreation every time?
	
	var audioRequest = new XMLHttpRequest();
	audioRequest.open('GET', url, true); 				//	open the request...
	audioRequest.responseType = 'arraybuffer';			// binary response
    audioRequest.onreadystatechange = function () {
        if (audioRequest.readyState == 4) {
			if(audioRequest.status==200) {
				if(audioRequest.response.byteLength > 1) {
					var buffer = new Int16Array(audioRequest.response);
//					console.debug('getAudio, len: '+buffer.length);
					var floats = new Array();
					for(var i=0; i<buffer.length; i++) floats[i] = buffer[i] / 32768.;
					new audioscan().playPcmChunk(floats);
				}
//				else console.debug('invalid pcm audio response buffer');
			}
			else {		// ERROR Handling
				if(debug) console.log('Error on audio fetch! '+url+', status: '+audioRequest.status);
				if(intervalID) goPause();
			}
        }
    }
	audioRequest.send();		// send the request
}

function setAudioMP3(url) {
	if(debug) console.log("fetch audioMP3: "+url);
//	if(ascan == null) ascan = new audioscan();		// save recreation every time?
	
	var audioRequest = new XMLHttpRequest();
	audioRequest.open('GET', url, true); 				//	open the request...
	audioRequest.responseType = 'arraybuffer';			// binary response
    audioRequest.onreadystatechange = function () {
        if (audioRequest.readyState == 4) {
			if(audioRequest.status==200) {
				if(audioRequest.response.byteLength > 1) {
					new audioscan().playMp3Chunk(audioRequest.response);
				}
//				else console.debug('invalid pcm audio response buffer');
			}
			else {		// ERROR Handling
				if(debug) console.log('Error on audio MP3 fetch! '+url+', status: '+xmlhttp.status);
				if(intervalID) goPause();
			}
        }
    }
	audioRequest.send();		// send the request
}

//----------------------------------------------------------------------------------------
//setParamValue:  set value on callback from Ajax 

function setParamValue(text, url, args) {
	var param = args[0];		// args passed thru == args of fetchData
	var pidx = args[1];
	var duration = args[2];
	var refTime = args[4];
	var now=new Date().getTime();
	var time=now, value;
	var datavals = text.split("\n");
	datavals = datavals.filter(function(s) { return s.length > 0; } );
	var nval = datavals.length;		// presume last is blank?

	if(debug) console.log("setParamValue url: "+url+", nval: "+nval);
	
	var audiobuf = new Array();			// foo
	for(var i=0; i<nval; i++) {
		if(serverTimes) {
			var timeval = datavals[i].split(",");
//			ttime = Math.round(1000.*parseFloat(timeval[0]));		// sec -> msec (integer)
			ttime = 1000.*parseFloat(timeval[0]);					// sec -> msec (float)
			value = parseFloat(timeval[1]);
			if(plots[pidx].type == "video") {						// add integer-encoded audio to video
				if(endsWith(param,".i16")) audiobuf.push(value/32768.);
				if(endsWith(param,".i32")) audiobuf.push(value/2147483648.);
			}
		} else {
			ttime = Math.round(now - (duration * (nval-(i+1)) / nval));
			value = parseFloat(datavals[i]);
		}
		if(isNaN(ttime)) continue;			// check for blank, bad time val
		else			 time = ttime;
		if(time < oldgotTime) oldgotTime=time;
		if(time > newgotTime) newgotTime=time;
//		if(debug) console.log('setParamValue, value: '+value+', param: '+param+', pidx: '+pidx);
//		if(!endsWith(param,".i16"))			// foo try skipping plot to check on speed
		if(plots[pidx] && (plots[pidx].type == "stripchart")) {
			plots[pidx].addValue(param,time,value);
		}
	}
	
	if(debug) console.debug("pidx: "+pidx+", type: "+plots[pidx].type+", singleStep: "+singleStep);
//	console.debug("fetchdata! nval: "+nval+", t0: "+parseFloat(datavals[0].split(",")[0])+", t1: "+ttime/1000.+", url: "+url);

	if(singleStep && (param == plots[pidx].params[plots[pidx].params.length-1])) {			// last param this plot
//		plots[pidx].setPrecision(param);			// defunct				// precision set to last param in plot group
//		if(lastreqTime > 0) plots[pidx].render(lastreqTime);		// animation off, update incrementally
		if(debug) console.debug('singleStep render, lastreqTime: '+lastreqTime+", tend: "+time);
		if(lastreqTime > 0) plots[pidx].render(lastreqTime);		// animation off, update incrementally
		else				plots[pidx].render(0);					// use last point got
	} 

	rtSync = now-time;						// for RT time-adjust
	inProgress--;
//	if(debug) console.debug('got data inProgress: '+inProgress);

	if(inProgress < 0) inProgress=0;		// failsafe
	if(inProgress == 0) document.body.style.cursor = 'default';

	if(nval > 0) {
		lastgotTime = time;
		if(refTime=="oldest") { setTime(time);	document.getElementById('TimeSelect').value=0; 	  oldestTime=oldgotTime; }	
		if(refTime=="newest") { setTime(time);	document.getElementById('TimeSelect').value=100;  newestTime=newgotTime; }	
		
		if(audiobuf.length > 0) {
			as = new audioscan(22050);
			as.playPcmChunk(audiobuf);		
		}
	}
}

//----------------------------------------------------------------------------------------
//rtCollection:  start real-time data collection

//async logic flow from here:
//rtCollection -> fetchData -> AjaxGet -> setParamValue

var playDelay=0;
function rtCollection(time) {
	stopRT();
	inProgress = 0;		// reset
	if(time != 0) playDelay = (new Date().getTime() - time);		// playback mode
	else 	   	  playDelay = 0.;
	rtSync = 0;

//	console.debug('rtCollection, time: '+time);
	// fetch data on interval
	function doRT(mDur,dt) {
		var anyplots=false;
//		console.debug('doRT, inProgress: '+inProgress);
		if(inProgress > 0) return;		// ease up?

		updatePauseDisplay(top.rtflag);
		var tfetch = playTime();
		var dfetch = mDur;
		var pDur = 1000.*getDuration();
		if(mDur == 0) dfetch = playDuration();
		if(debug) console.log("doRT, mDur: "+mDur+", tfetch: "+tfetch+", dfetch: "+dfetch+", pDur: "+pDur);

		if(dfetch <= 0) return;		// nothing new

		if(dfetch >= pDur && singleStep==false) {
			if(debug) console.debug('singleStep!');
			singleStep=true;				// switch gears if falling behind
			for(var j=0; j<plots.length; j++) plots[j].stop();
		} else if(dfetch<pDur && singleStep==true)	{
			if(debug) console.debug('resume scrolling!');
			for(var j=0; j<plots.length; j++) plots[j].start();
			singleStep=false;
			dfetch = pDur;		// restart with full screen 
		}
		if(debug) console.debug('dfetch: '+dfetch+', pDur: '+pDur+', singleStep: '+singleStep);
		
		for(var j=0; j<plots.length; j++) {
//			if(plots[j].type != 'stripchart') continue;		// only stripcharts
//			plots[j].setDelay(playDelay+2000);				// +1000 for right-edge hide
//			plots[j].setDelay(playDelay+dt);				// +dt for right-edge hide

//			if(!singleStep) 
			plots[j].setDelay(playDelay+dt);			// use playDelay, no edge adjust

			for(var i=0; i<plots[j].params.length; i++) {
				var param = plots[j].params[i];
				if(endsWith(param,".jpg")) continue;
//				console.debug("param: "+param);
				anyplots=true;
				// following factor 1.2*dfetch helps avoid jitter-gaps but overlaps audio data...
//				console.debug("dfetch: "+dfetch+", time: "+(tfetch-dfetch));
				fetchData(plots[j].params[i], j, 1.0*dfetch, tfetch-dfetch, "absolute");		// fetch new data (async)  // try gapless logic
//				fetchData(plots[j].params[i], j, 1.5*dt, tfetch-dt, "absolute");	// fetch new data (async)  // *factor for no gap
//				fetchData(plots[j].params[i], j, 1.5*dt, tfetch, "absolute");		// fetch new data (async)  // *factor for no gap
			}
//			if(playDelay!=0 & tfetch > newestTime) goPause();
		}
		
		if(!anyplots || (tfetch>newestTime && top.rtflag!=RT)) {
			if(debug) console.log('no stripcharts, stopping monitor');
			clearInterval(intervalID);		// notta to do
			intervalID = 0;
			if(intervalID2==0) goPause();
		}
		setTime(tfetch);	// for display and eavesdroppers 
	}

	mDur = 1000.*getDuration();
	intervalID2=1;			// tentative to keep refresh from setPlay(PAUSE)
	doRT(mDur,0);			// once quick, full-refresh
//	goEOF();
	
	var dt = tDelay;
	if(dt > mDur) dt = mDur;			// refresh at least once per screen
	if(dt <= 100) dt = 100; 
	
	intervalID = setInterval(function() {doRT(0,dt);}, dt);
	
	// faster video updates:
	intervalID2 = setInterval(
		function() {
//			ptime = new Date().getTime()-playDelay;		// don't affect playDelay() logic...
			ptime = playTime();	

			anyvideo = false;
			for(var j=0; j<plots.length; j++) {
				if(plots[j].type != 'video') continue;		// non-video go 1/10 nominal rate
				anyvideo = true;
				fetchData(plots[j].params[0], j, 0, ptime, "absolute");		// RT->playback mode
			}
			if(!anyvideo || (ptime>newestTime && top.rtflag!=RT)) {	
				if(debug) console.log('no video, stopping monitor');
				clearInterval(intervalID2);		// notta to do
				intervalID2 = 0;
				if(intervalID==0) goPause();
			}
			else if(!intervalID) setTime(ptime);		// let stripchart set time if available
		}, 
		tDelay/10);				// 10x rate
}	

//----------------------------------------------------------------------------------------

function playTime() {		// time at which to fetch (msec)
//	console.debug('stepDir: '+stepDir);
	return(new Date().getTime() - playDelay);	
}

var oldDuration = 0;
function playDuration() {							// duration to fetch (msec)
//	var pdur = playTime() - getTime();				// get everything from from last pos to playtime
	var playtime = playTime();
	var pdur = playtime - lastgotTime;			// get everything from from last pos to playtime
													// ISSUE:  this presumes all (stripchart) chans at same lastgotTime
													// consider book-keeping this on per channel basis?

	var mdur = 1000*getDuration();		// msec
	if(pdur > mdur) pdur = mdur;					// limit to one screen worth
	if(pdur < 0) pdur = mdur;					// initialization glitch?
	if(debug) console.log('pdur: '+pdur+", playTime: "+playtime+", lastgotTime: "+lastgotTime+", mdur: "+mdur+", playDelay: "+playDelay);
	return pdur;
}

// slow down if nec (called from vidscan.js)
function adjustPlayDelay(inc) {
	if(debug) console.debug('adjustPlayDelay, inc: '+inc+', playDelay: '+playDelay);
	playDelay += inc;				// nah
}

//----------------------------------------------------------------------------------------
// waitDone:  wait until inProgress flag unset
// untested, not sure if this works!
function waitDone(maxWait) {
	if(inProgress && maxWait>0) { 	// wait til done
		setTimeout(function(){waitDone(--maxWait);}, 100); 
		return; 
	}
}

//----------------------------------------------------------------------------------------
//refreshCollection:  refresh data, single step or continuous

function refreshCollection(onestep, time, reftime) {
//	onestep=false for refilling plot and continuing with RT data 
	refreshInProgress=true;
	if(debug) console.log('refreshCollection: reftime: '+reftime);

//	if(reftime=="absolute") time = getTime() - 1000.*getDuration();		// adjust RE time to LE time
	setPlay(PAUSE,0);										// pause RT
	refreshCollection2(100, onestep, time, reftime);		// fetch & restart after pause complete
}

function refreshCollection2(maxwait, onestep, time, reftime) {
	refreshInProgress=true;
//	if(inProgress && maxwait>=0) { 		// wait til paused
	if(inProgress) { 		// wait til paused
		setTimeout(function(){refreshCollection2(--maxwait, onestep, time, reftime);}, 100); 
		return; 
	}	
//	console.log('refreshCollection2: reftime: '+reftime);

	var duration = document.getElementById("myDuration");
	var fetchdur = 1000. * parseFloat(duration.options[duration.selectedIndex].value);
	if(resetMode) fetchDur=0;
	
	// check for going past EOF, BOF
	var now = new Date().getTime();
	oldgotTime = now;		// init
	newgotTime = 0;
	
	lastreqTime = 0;
	if(reftime == "absolute") {
		lastreqTime = time + fetchdur;					// time=left-edge, lastreqTime=right-edge time

//		console.debug('get time: '+time+', oldestTime: '+oldestTime+', now: '+now+',t+f: '+(lastreqTime)+', fetchdur: '+fetchdur);
		if(lastreqTime > now) 		{ time = 0; reftime="newest"; lastreqTime=0; }
		else if(time < oldestTime) 	{ time = 0; reftime="oldest"; lastreqTime=0; }
	}

	for(var j=0; j<plots.length; j++) {				// get data once each plot
		plots[j].dropdata();
		for(var i=0; i<plots[j].params.length; i++) {
//			console.debug('refresh fetch time: '+time+", dur: "+fetchdur);
			fetchData(plots[j].params[i], j, fetchdur, time, reftime);	// fetch new data (async)
		}	
	}	

	refreshCollection3(100,onestep,time,reftime);		// queue restart
}

function refreshCollection3(maxwait, onestep, time, reftime) {
	if(inProgress) { 	// wait til done
		setTimeout(function(){refreshCollection3(--maxwait, onestep, time, reftime);}, 100); 
		return; 
	}	
//	console.log('refreshCollection3: reftime: '+reftime);

//	if(reftime == "oldest") oldestTime = oldgotTime;	// ref
//	if(reftime == "newest") newestTime = newgotTime;	// ref

	if(!resetMode) {
		if(onestep) {
			for(var j=0; j<plots.length; j++) {
				if(debug) console.debug('refresh render: '+lastreqTime);
				plots[j].render(lastreqTime);	// see the data	
//				plots[j].stop();				// nec?
			}
			if(reftime != "newest") updatePauseDisplay(PAUSE);
		}
		else {
//			console.log('setPlayRT time: '+time);
			setPlay(RT,getTime());					// go (restarts RT collection + plots)
		}
	}
	
	if(!onestep) {
//		singleStep=true;					// foo, non-smooth
//		rtCollection(getTime());
//		setPause(false,getTime());			// loses data on plot.start?
	}

	document.body.style.cursor = 'default';
	refreshInProgress=false;
	resetMode=false;
}

//----------------------------------------------------------------------------------------
//AjaxGet:  Ajax request helper func

function AjaxGet(myfunc, url, args) {
	if(debug) console.log('AjaxGet: '+url);
//	var pidx = args[1];
	var time = args[3];

	var xmlhttp=new XMLHttpRequest();

	xmlhttp.onreadystatechange=function() {
		if (xmlhttp.readyState==4) {
			if(xmlhttp.status==200) {
				if(debug) console.log("xmlhttp got: "+url);
				myfunc(xmlhttp.responseText, url, args);
			}
			else {		// ERROR Handling
				if(debug) console.log('Error on data fetch! '+url+', status: '+xmlhttp.status+", rtflag: "+top.rtflag);

				inProgress=0;		// no deadlock
				document.body.style.cursor = 'default';		// done with this fetch
//				console.log('Error: '+url);
//				if(top.rtflag!=RT)	goPause();			// stop if playback mode?
				if(intervalID) { 	//  no warn on shutdown
					if(top.rtflag==RT) {
						// adjustPlayDelay(1000);			// drop back
					} 
					else if(time > newgotTime)				// keep going (over gaps)
						goPause();						
				}
				if(xmlhttp.status == 410) {			// quiet about "gone" data oor
//					if(oldestTime==0 && stepDir<0) goBOF();	// peg to oldest only if BOF unknown
					return;
				}	
			}
		}
	};
	xmlhttp.open("GET",url,true);
	xmlhttp.onerror = function() { goPause(); alert('xmlhttp error'); };
	xmlhttp.send();
}

//----------------------------------------------------------------------------------------	
//fetchChanList:  build channel list from DT source

function fetchChanList() {
	channels = new Array();
	AjaxGet(parseWT,serverAddr+servletRoot,"chanList");
}

//----------------------------------------------------------------------------------------	
//parseWT:  parse links from WebTurbine HTML page

function parseWT(page,url,selel) {
//	console.debug('parseWT: url: '+url);
	var el = document.createElement('div');
	el.innerHTML = page;
	var x = el.getElementsByTagName('a');
	for(var i=1; i<x.length; i++) {		// skip href[0]="..."
		var opt = x.item(i).textContent;	// not .text
		if(opt == '_Log/') continue;		// skip log text chans

		if(endsWith(opt, "/")) {
			AjaxGet(parseWT,url+"/"+opt,"chanList");		// chase down multi-part names
		} else {										// Channel
			var fullchan = url+opt;
			fullchan = fullchan.substring(fullchan.indexOf(servletRoot)+servletRoot.length+1);
			fullchan = fullchan.split("//").join("/");		// replace any double-slash with single
//			console.log('got: url: '+url+', txt: '+opt+', fullchan: '+fullchan);
//			var chanparts = (url+opt).split('/');
//			var fullchan = chanparts[2];		// skip '/RBNB/' prefix (2 slashes)
//			for(var j=3; j<chanparts.length; j++) fullchan = fullchan + '/' + chanparts[j];
			channels.push(fullchan);			// for plot selects
		}
	}

	if(channels.length > 0)	buildChanLists();   // inefficient, called multiple times per update...
}

//----------------------------------------------------------------------------------------
//endsWidth:  utility function 
function endsWith(str, suffix) {
	return str.indexOf(suffix, str.length - suffix.length) !== -1;
}

//----------------------------------------------------------------------------------------
//durationSelect:  handle duration select

function durationSelect(cb) {
	setSingleStep();
	var secondsPerPlot = parseFloat(cb.options[cb.selectedIndex].value);
	for(var i=0; i<plots.length; i++) plots[i].setDuration(secondsPerPlot);
	rePlay();
//	if(top.rtflag==PAUSE && !isImage && oldestTime > 0) 
//		refreshCollection(true,getTime()-1000.*getDuration(),"absolute");	// auto-refill plots to full duration??
	
	top.plotDuration = secondsPerPlot;		// global for eavesdroppers
	setConfig('v',secondsPerPlot);
}

function getDuration() {
	var duration = document.getElementById("myDuration");
	return 	duration.options[duration.selectedIndex].value;
}

function setDuration(spp) {		// seconds per plot
	var sel = document.getElementById("myDuration");
	for (var i=0; i<sel.options.length; i++) {
		if (sel.options[i].value == spp) {
			sel.selectedIndex = i;
		}
	}
	for(var i=0; i<plots.length; i++) plots[i].setDuration(spp);
	top.plotDuration = spp;		// global for eavesdroppers
}

//----------------------------------------------------------------------------------------
//updateSelect:  handle update-rate select

function updateSelect() {
	if(!doRate) return;
	var update = document.getElementById("myUpdate");
	update.onchange = function() {
		setSingleStep();
		tDelay = parseFloat(this.options[this.selectedIndex].value);
//		if(!isPause()) rtCollection(0);	
		setConfig('dt',tDelay);
	};
}

//----------------------------------------------------------------------------------------    
//nplotSelect:  onchange nplot select

function nplotSelect(cb) {
	nplot = cb.options[cb.selectedIndex].value;
	setPlots(nplot);
	noRebuild=false;		// no hang
	rebuildPage();
}

//----------------------------------------------------------------------------------------    
// serverSelect:  select data server address

function serverSelect(cb) {
	var x=document.getElementById("myServer");
//	console.debug('serverSelect: '+x.value);
	serverAddr = x.value;
	if(serverAddr.substring(0,3) != "http") serverAddr = "http://"+serverAddr;
}

//----------------------------------------------------------------------------------------    
//ncolSelect:  onchange ncol select

function ncolSelect(cb) {
	numCol = cb.options[cb.selectedIndex].value;
	setConfig('c',numCol);
	noRebuild=false;		// no hang
	rebuildPage();
}

//----------------------------------------------------------------------------------------	
//runStopUpdate:  pause operation

function runstopUpdate() {
	var paused = isPause();
//	if(debug) console.log('runstopUpdate');
	if(paused) setPlay(PAUSE,0);
	else {
		setPlay(RT,0);
		refreshCollection(false,0,"newest");		// update plots
	}
}

//----------------------------------------------------------------------------------------	
//setPlay:  0=pause, 1=RT, 2=playback

function setPlay(mode, time) {
	top.rtflag = mode;				
	if(mode==PAUSE) singleStep=false;
	else			setSingleStep();
//	console.debug('setPlay: mode: '+mode+', time: '+time+', singleStep: '+singleStep);
	/*
	if(time >= 0) {			// cluge: PAUSE doesn't change RT/> display mode
		if(mode==RT) 	document.getElementById('RTlab').innerHTML = 'RT';
		else			document.getElementById('RTlab').innerHTML = '>';
	}
	 */	
	if(mode==PAUSE) {				// stop RT
		stopRT();
		for(var i=0; i<plots.length; i++) plots[i].stop(); 
		inProgress=0;			// make sure not spinning
		document.body.style.cursor = 'default';		
	}
	else {
//		rtCollection(time);
//		goRT();		// infinite recursion danger, refreshCollection calls setPause()
//		console.debug('singlestep: '+singleStep);
		if(!singleStep)	// no restart animation if singlestep mode
			for(var i=0; i<plots.length; i++) plots[i].start();
		rtCollection(time);
	}

	updatePauseDisplay(mode);
}

function getPlayMode() {
	return top.rtflag;
}

function updatePauseDisplay(mode) {
	if(mode==PAUSE) 	document.getElementById('||').checked=true;
	else if(mode==RT) 	document.getElementById('RT').checked=true;
	else if(mode==PLAY) document.getElementById('>').checked=true;
}

//----------------------------------------------------------------------------------------	
//rePlay:  re-start at current time & mode (pause/play/RT)

function rePlay() {
	var mode = PAUSE;
	if(document.getElementById('RT').checked) mode = RT;
	else if(document.getElementById('>').checked) mode = PLAY;

	if(mode==PAUSE && !isImage && oldestTime > 0) {
		refreshCollection(true,getTime()-1000.*getDuration(),"absolute");	// auto-refill plots to full duration??
	}
	else if(mode==PLAY) {
		playFwd();
	}
	else if(mode==RT) {
		refreshCollection(false,0,"newest");	// this auto-fills now			
	}
}

//----------------------------------------------------------------------------------------	
//stopRT:  clear RT timer

function stopRT() {
	if(debug) console.log("stopRT.");
	if(intervalID != 0) clearInterval(intervalID);
	if(intervalID2 != 0) clearInterval(intervalID2);
	intervalID = intervalID2 = 0;
	document.getElementById('||').checked = true;
}

//----------------------------------------------------------------------------------------	
//setSingleStep:  set flag if small incremental view update (more efficient)

function setSingleStep() {
	singleStep = false;		// default
	return;					// nah
	
//	if(stepDir > 1) { singleStep = false; return; }		// playback mode animation always
	var update = document.getElementById("myUpdate");
	var updateInterval = parseFloat(update.options[update.selectedIndex].value)/1000.;		// sec
	var duration  = document.getElementById("myDuration");
	var viewDuration = parseFloat(duration.options[duration.selectedIndex].value);
	var ratio = updateInterval / viewDuration;
	
	if(ratio < 0.001) singleStep = true;		// e.g. 1sec updates at 10 min => ratio 0.0016
	if(duration <= 0.1) singleStep = true;
	
//	if(duration < 1) singleStep = true;			// try
}

//----------------------------------------------------------------------------------------	
//isPause:  return true if paused

function isPause() {
	return 	document.getElementById('||').checked;
}

//----------------------------------------------------------------------------------------	
//isRT:  true/false if in pause state

function isRT() {
	return !document.getElementById('||').checked;
}

//----------------------------------------------------------------------------------------	
//smoothCheck:  set/unset smooth option

function smoothCheck(cb) {
	if(cb) doSmooth = cb.checked;
	for(var i=0; i<plots.length; i++) { 
		plots[i].setSmooth(doSmooth); plots[i].render(lastreqTime); 
	}
	setConfig('s',doSmooth);
}  

function setSmooth(smooth) {
	doSmooth = smooth;
	cb = document.getElementById('smooth');
	cb.checked = smooth;
	for(var i=0; i<plots.length; i++) plots[i].setSmooth(doSmooth); 
}

//----------------------------------------------------------------------------------------	
//fillCheck:  set/unset fill option

function fillCheck(cb) {

	if(cb) doFill = cb.checked;
	for(var i=0; i<plots.length; i++) { 
		plots[i].setFill(doFill); plots[i].render(lastreqTime); 
	}
	setConfig('f',doFill);
} 

function setFill(fill) {
	doFill = fill;
	cb = document.getElementById('fill');
	cb.checked = fill; 
	for(var i=0; i<plots.length; i++) plots[i].setFill(doFill); 
}

//----------------------------------------------------------------------------------------
//resetParams:  set variables to match UI values (needed for FireFox refresh)

function resetParams() {
	var el = document.getElementById('nplot');
	var nplot = el.options[el.selectedIndex].value;
	if(nplot != plots.length) setPlots(nplot);

	var doSmooth = document.getElementById('smooth').checked;
	var doFill = document.getElementById('fill').checked;

	for(var i=0; i<plots.length; i++) { 
		plots[i].setFill(doFill); 
		plots[i].setSmooth(doSmooth);
	}

	if(doRate) {
		var update = document.getElementById("myUpdate");		// msec
		tDelay = parseFloat(update.options[update.selectedIndex].value);
	}
//	runstopUpdate();
}

//----------------------------------------------------------------------------------------
//rebuildPage:  reconstruct and restart data collection 

function rebuildPageWait(maxWait) {
	if((inProgress || refreshInProgress) && maxWait>0) { 						// wait til prior update done
//		console.debug('inProgress: '+inProgress+', refreshInProgress: '+refreshInProgress);
		setTimeout(function(){rebuildPageWait(--maxWait);}, 100); 
		return; 
	}
	rebuildPage();
}

function rebuildPage() {
	if(noRebuild) return;								// notta
	if(debug) console.log('rebuildPage!');
	
	stopRT();
	buildCharts();
	resetParams();						// ensure buttons match parameter values

	if(!isPause()) 	goRT();
//	else			rePlay();
	
//	setTime(getTime());
//	console.debug('rebuildPage, old: '+oldestTime+", new: "+newestTime);
}

//----------------------------------------------------------------------------------------
//setTime:  update screen display of current time position

function setTimeNoSlider(time) {
	if(time == 0 || isNaN(time)) return;		// uninitialized
	d = new Date(time);		// msec
	var dstring = d.toUTCString();
	dstring = dstring.split(", ")[1];		// string leading "Day, "
	document.getElementById("timestamp").innerHTML = dstring;
	top.plotTime = time / 1000.;		// global, units=sec
}

function setTime(time) {
	if(time == 0 || isNaN(time)) return;		// uninitialized

//	console.debug('setTime: '+time);
	setTimeNoSlider(time);

//	d = new Date(time);		// msec
//	var dstring = d.toUTCString();
//	dstring = dstring.split(", ")[1];		// string leading "Day, "
//	document.getElementById("timestamp").innerHTML = dstring;
//	top.plotTime = time / 1000.;		// global, units=sec
	
	// set time slider
	setTimeSlider(time);
//	getLimits();		// make sure limits are known...
//	var mDur = 0.
//	if(!isImage) mDur = 1000.*getDuration();		// duration msec	
//	setTimeSliderPercent(100. * (time - oldestTime - mDur) / (newestTime - oldestTime - mDur));
}

function setTimeSlider(time) {
	var el = document.getElementById('TimeSelect');
	var mDur = 0.
	if(!isImage) mDur = 1000.*getDuration();		// duration msec	
	var percent = 100. * (time - oldestTime - mDur) / (newestTime - oldestTime - mDur);
	el.value = percent;
}

function getTime() {
//	console.debug('getTime: '+top.plotTime);
	return top.plotTime * 1000.;		//  msec
}

var lastSet=0;
function timeSelect(value) {
	if(inProgress) return;
	var now = new Date().getTime();
	if((now - lastSet) < 100) return;			// ease up if busy
	lastSet = now;
	goTime(value);
//	console.debug("timeSelect: "+value);
}

var resetMode=false;
function resetLimits(pplot) {
//	resetMode=true;
//	goBOF();
//	goEOF();
//	oldestTime = new Date().getTime();  newestTime=0;		// force reset
	fetchData(plots[pplot].params[0], pplot, 0., 0., "newest");	
	fetchData(plots[pplot].params[0], pplot, 0., 0., "oldest");	
//	document.getElementById('TimeSelect').value = 100;		// peg slider
}

// get data at limits (new, old)
// should have a time-only fetch version (f=t)
function getLimits(forceFlagOld, forceFlagNew) {
	var otime = getTime();
//	if(debug) console.debug("getLimits in, oldestTime: "+oldestTime+", newestTime: "+newestTime);
	if(newestTime == 0 || newestTime < oldestTime || forceFlagNew) {
		fetchData(plots[0].params[0], 0, 0, 0, "newest");
//		goEOF();
	}
	if(oldestTime == 0 || oldestTime > newestTime || forceFlagOld) {
		fetchData(plots[0].params[0], 0, 0, 0, "oldest");
//		goBOF();	// should be on first channel only, timestamp only, no display
	}

//	if(debug) console.debug("getLimits out, oldestTime: "+oldestTime+", newestTime: "+newestTime);
	if(!forceFlagOld && !forceFlagNew) setTime(otime);
}

//----------------------------------------------------------------------------------------
//buildCharts:  build canvas and smoothie charts

function buildCharts() {
	if(debug) console.log('buildCharts: '+plots.length);

	var emsg = 'Your browser does not support HTML5 canvas';
//	var graphDiv = document.getElementById('graphs');

	// clean up
	var graphs=document.getElementById("graphs");
	while(graphs.firstChild) graphs.removeChild(graphs.firstChild);	// clear old		

	var Wg = graphs.clientWidth;		// fixed value all plots

	// create each plot
	var nparam = 0;		// count active params
//	var ncol = 2;		// number of columns (need from UI)
	var ncol = numCol;
	if(ncol == 0) {	 // auto
		switch(plots.length) {	
		case 4:		case 6:		case 8:		case 10:	case 14:	ncol = 2;	break;
		case 9:		case 12:	case 15:							ncol = 3;	break;
		case 16:													ncol = 4;	break;
		default:													ncol = 1;	break;
		}
	}
	if(ncol > plots.length) ncol = plots.length;
	var nrow = Math.ceil(plots.length / ncol);		
	var iplot = 0;
	for(var irow=0; irow<nrow; irow++) {
		var row  = graphs.insertRow(-1);		// add rows as needed

		for(var icol=0; icol<ncol && iplot<plots.length; icol++,iplot++) {
//			console.log('iplot: '+iplot+', irow: '+irow+', icol: '+icol);

//			for(var i=0; i<plots.length; i++) {	
//			var row  = graphs.insertRow(i);		// was -1 // foo
			var cell1=row.insertCell(-1);		// was 0

			// plotDiv child of graphDiv	
			var plotTable = document.createElement('table');
//			plotTable.setAttribute("border","1");
			cell1.appendChild(plotTable);

			var prow = plotTable.insertRow(0);
			var pcell0 = prow.insertCell(0); 
			pcell0.style.padding = 0;

			// + addchan button
			addChanBox(iplot, pcell0);					

			//  create label for each param
			for(var j=0; j<plots[iplot].params.length; j++) {
				nparam++;						
				// create label element above plot
				var node = document.createElement('label');
				node.innerHTML = plots[iplot].params[j];
				node.id = 'label'+j;
				node.style.color = plots[iplot].color(j);
				node.style.padding = '0 6px';
				pcell0.appendChild(node);	
				setConfig('p'+iplot+''+j,plots[iplot].params[j]);
			}

			// x clearPlot button
			if(plots[iplot].params.length > 0) {		// only if any curves to clear
				addClearBox('clear'+iplot, clearPlotSelect, 'x Clear', pcell0);
			}

			// create a canvas for each plot box
			prow = plotTable.insertRow(1);
			var pcell1 = prow.insertCell(0); 

			var canvas = document.createElement('canvas');
			canvas.innerHTML = emsg; 
			canvas.id = 'plot'+iplot; 
			canvas.setAttribute("class", "canvas");
			addListeners(canvas);		// add mouse click event listeners

//			canvas.width = Wg-15; 			// width used in setting chart duration
			canvas.width = Wg/ncol-15; 			// width used in setting chart duration

//			canvas.width = graphs.clientWidth - 15;
			Hg = (graphs.clientHeight / nrow) - pcell0.offsetHeight - 20;
			canvas.height = Hg;		// ensure same for all

			canvas.align="center";
			pcell1.appendChild(canvas);

			// associate smoothie chart with canvas
//			console.log('webscan addCanvas: '+canvas.id);
			plots[iplot].addCanvas(canvas);

//			vidscan(canvas, 'NASATV/image.jpg');		// foo debug
		}
	}

	buildChanLists();		// re-initialize (overkill?)
	updateSelect();			// update-interval selection menu
	
	durationSelect(document.getElementById("myDuration"));	    // plot duration selection menu		(NEED?)

//	inProgress=0;			// failsafe 
//	setPause(false,0);		// auto pause (less perplexing) 
}	

//----------------------------------------------------------------------------------------
//drag-plot utilities

//figure out if mouse or touch events supported
isTouchSupported = 'ontouchstart' in window;
var startEvent = isTouchSupported ? 'touchstart' : 'mousedown';
var moveEvent = isTouchSupported ? 'touchmove' : 'mousemove';
var endEvent = isTouchSupported ? 'touchend' : 'mouseup';
var outEvent = isTouchSupported ? 'touchcancel' : 'mouseout';

//var clickEvent = isTouchSupported ? 'click touchend touchcancel mouseup' : 'click';		// ?

function addListeners(c) {
	c.addEventListener(startEvent,mouseDown, false); 	
	c.addEventListener(endEvent,  mouseUp,   false);	
	c.addEventListener(outEvent,  mouseOut,   false);	 
//	c.addEventListener(moveEvent, mouseMove, false);	
//	c.addEventListener(clickEvent, mouseClick, false);			// ?
//	c.addEventListener('dblclick', mouseDblClick, false);
}

var rect1x=0;
var rect;
var startMoveTime=0;
var thiswin=0;
function mouseDown(e) {
//	console.log('mouseDown');
	e.preventDefault();		// stop scrolling
	mouseIsMove=false;		// not yet
//	console.log('mousedown, e: '+e.target.id);
	setPlay(PAUSE,0);
	if(isTouchSupported) rect1x = e.touches[0].clientX;
	else 				 rect1x = e.clientX;
	startMoveTime = getTime();
	this.addEventListener(moveEvent, mouseMove);
	thiswin = this;		// for mouseout
}

function mouseOut(e) {
//	console.log('mouseOut');
	if(thiswin) {
		thiswin.removeEventListener(moveEvent, mouseMove);
		thiswin=0;		// avoid thrash
	}
	mouseIsMove=false;
//	mouseUp(e);
}

function mouseUp(e) {
//	console.log('mouseUp');
	if(getTime() == startMoveTime) {
		var rect = e.target.getBoundingClientRect();
		var relx = rect1x / (rect.right - rect.left);
		if(relx >= 0.5) refreshCollection(true,startMoveTime,"next");
		else			refreshCollection(true,startMoveTime,"prev");
		setTime(getTime());
	}
	if(thiswin) {
		thiswin.removeEventListener(moveEvent, mouseMove);
		thiswin=0;		// avoid thrash
	}
	mouseIsMove=false;
}

var lastMove=0;
var mouseIsMove=false;
function mouseMove(e) {
//	console.log('mouseMove');
	e.preventDefault();		// stop scrolling
	var now = Date().now;
	if((now - lastMove) < 100) return;	// limit update rate

	if(!refreshInProgress && !inProgress) {
		lastMove = now;
		mouseIsMove=true;

		var rect = e.target.getBoundingClientRect();
		var rectw = rect.right - rect.left;			// box width
		var eclientX;
		if(isTouchSupported) eclientX = e.touches[0].clientX;
		else				 eclientX = e.clientX;
		var relstep = (rect1x - eclientX)/rectw;
		
		stepDir= 0;		// no side effects
		var mDur = 1000.*getDuration();		// duration msec
		var inc = relstep * mDur;			// msec
//		console.log('rect1x: '+rect1x+', eclientX: '+eclientX+', relstep: '+relstep+', inc: '+inc);
		if(Math.abs(relstep) < 0.01) return;				// too small to bother

		var newTime = Math.round(startMoveTime + inc);
		if(getTime() != newTime) {
			refreshCollection(true,newTime-mDur,"absolute");
			setTime(newTime);			// was cmt out...
		}
	}
}

//----------------------------------------------------------------------------------------
//buildChanLists:  build channel select option list for all plots

function buildChanLists() {
//	console.debug('buildChanLists!, channels.length: '+channels.length);
	for(var j=0; j<plots.length; j++) {
		var add = document.getElementById('add'+j);
//		if(add.optgroup) add.optgroup.length = 0;	// reset
		add.options.length = 0;		// reset
		var ogl=add.getElementsByTagName('optgroup');
		for (var i=ogl.length-1;i>=0;i--) add.removeChild(ogl[i]);

		var elo = document.createElement("option");
		elo.value = elo.textContent = '+ Chan';
		add.appendChild(elo);
		var mysrc=''; 	var elg='';
		var listlen = channels.length;
		for(var i=0; i<listlen; ) {	
			var elo = document.createElement("option");
			var src = channels[i].split('/');
			var chan = src.pop();
//			src = src.pop()+'/';				// source limited to one level up
			src = src.join('/')+'/';
			if(src != mysrc) {		// new source group
				elo.value = '';
				elg = document.createElement("optgroup");
				elg.label = src;
				add.appendChild(elg);
				mysrc = src;
			}
			else {					// add channel to source group
				elo.value = channels[i];
//				elo.value = src + chan;
				elo.textContent = chan;
				elg.appendChild(elo);
				i++;
			}
		}
		add.width='100px';
		add.style="width: 100px";
	}
}

//----------------------------------------------------------------------------------------
//addChanBox:  add checkbox to plot

function addChanBox(idx, el) {
	var input = document.createElement('select');
	input.id = 'add'+idx;
	input.style.width = '100px';		// try
	el.appendChild(input);
	input.addEventListener('mousedown', pauseRebuild);
	input.addEventListener('change', addChanSelect);
}

//----------------------------------------------------------------------------------------
//pauseRebuild() 

function pauseRebuild() {
	noRebuild = true;
}

//----------------------------------------------------------------------------------------
//addClearBox:  add checkbox to plot

function addClearBox(id, cb, lab, el) {
	var input = document.createElement('button');
	input.id = id;
	input.textContent = lab;
	el.appendChild(input);
	input.addEventListener('click', clearPlotSelect);	
}

//----------------------------------------------------------------------------------------
//addChanSelect:  add parameter to selected plot

function addChanSelect() {
	if(!this.options) return;		// some browsers
//	var nline = totalLines();
	noRebuild=true;					// no rebuild charts during selection

	var chan = this.options[this.selectedIndex].value;
	if(chan == '+ Chan') return;	// firewall: not a real selection

	if(chan=='' || endsWith(chan,'/')) {
		this.selectedIndex = 0;
		return;			// not a channel
	}

	var pplot = parseInt(this.id.replace('add',''));
//	plots[pplot].addLine(chan);
	plots[pplot].addParam(chan);
	noRebuild = false;
	
	if(pplot==0 && 				// only reset limits on first param of first plot
			plots[pplot].params.length == 1) {
		resetLimits(pplot);
		rebuildPageWait(20);
//		goEOF();
		goBOF();
	}
	else rebuildPageWait(20);
}

//----------------------------------------------------------------------------------------
//totalLines:  total active params being plotted

function totalLines() {
	var nline=0;
	for(var i=0; i<plots.length; i++) nline += plots[i].params.length;
	return nline;
}

//----------------------------------------------------------------------------------------
//clearPlotSelect:  clear selected plot

function clearPlotSelect(cb) {
	var pplot = parseInt(this.id.replace('clear',''));
	plots[pplot].clear();													// clear timeseries from plot
	for(var j=0;j<10;j++) setConfig('p'+pplot+''+j,null);					// remove from config
	plots.splice(pplot,1,new plotbox({doFill:doFill,doSmooth:doSmooth}));	// empty new plot

	noRebuild = false;
	rebuildPage();
}

//----------------------------------------------------------------------------------------	
//goFuncs:  playback data controls

function goBOF() {
	stepDir= -1;
	if(debug) console.log("goBOF");
//	goTime(0);		// more robust limit checks?
	refreshCollection(true,0,"oldest");
//	setTimeout(function(){ setTime(oldestTime); }, 200);
//	console.log("goBOF, oldestTime: "+oldestTime);
}

function goReverse() {
}

function goStepRvs() {
	stepDir= -1;
	var mDur = 1000.*getDuration();		// duration msec
	var inc = -1 * mDur;
	refreshCollection(true,getTime()-mDur+inc, "absolute");
	setTime(getTime() + inc);		// move back 1x duration from left-edge time
}

function goPause() {
	stepDir= 0;
	setPlay(PAUSE,-1);
}

function goStepFwd() {	
	stepDir= 1;
	var mDur = 1000.*getDuration();		// duration msec	
	var inc = 1 * mDur;				// msec
	refreshCollection(true,getTime()-mDur+inc,"absolute");	// move fwd 1x from left-edge time
	setTime(getTime() + inc);
}

function playFwd() {
	getLimits(0,0);		// make sure limits are known...

//	var dt = new Date().getTime() - getTime();
//	console.log('dt: '+dt);
//	if(Math.abs(dt) < 10000 || document.getElementById('RTlab').innerHTML=='RT') {
	if(false) {			// turn off auto-RT for now...
		goRT();		// auto-RT if close to EOF
	} else {
		stepDir= 2;		// this affects playTime() to use RT-playDelay to advance playback clock
		setPlay(PLAY,getTime());
		// starts charts with first-fetch = mdur followed by tDelay updates without restarting charts...
	}
}

function goEOF() {
	stepDir= 1;
	if(debug) console.log("goEOF");
	refreshCollection(true,0,"newest");
//	document.getElementById('RTlab').innerHTML = 'RT';
//	timeOut = setTimeout( function(){ if(isPause()) document.getElementById('RTlab').innerHTML = '>'; },10000); 
}

function goRT() {
	getLimits(0,0);			// for RT playback
	goTime(100);
	setTimeout(function(){ goRT2(); }, 500);		// wait for limits update?
}

function goRT2() {
	refreshCollection(false,0,"newest");
}

var maxwaitTime=0;
function goTime(percentTime) {
	if(percentTime==0) {
		getLimits(1,0);		// force new limits
		refreshCollection(true,0,"oldest");
	}
	else if (percentTime == 100) {
		getLimits(0,1);		// force new limits
		refreshCollection(true,0,"newest");
	}
	else {
		getLimits(0,0);				// make sure limits are known...
		++maxwaitTime;
//		if(debug) console.debug("goTime newestTime: "+newestTime);
		if(newestTime==0 && maxwaitTime<50) {		// hopefully doesn't happen, obscure problems if lumber on
//		if(newestTime==0) {		// hopefully doesn't happen, obscure problems if lumber on

			if(debug) console.debug("waiting for limits to be set...");
//			setTimeout(function() { goTime2(percentTime); }, 500);
			setTimeout(function() { goTime(percentTime); }, 100);		// possible infinite loop?
		}
		else goTime2(percentTime);
	}
}

function goTime2(percentTime) {
//	if(debug) console.debug("goTime2: "+percentTime+", old: "+oldestTime+", new: "+newestTime);
	maxwaitTime=0;
	if(newestTime == 0) {		// hopefully doesn't happen, obscure problems if lumber on
//		alert('Warning, unknown limits, unable to set time position');			
		return;
	}
	gotime = oldestTime + percentTime * (newestTime - oldestTime) / 100.;
	var mDur = 0.
	if(!isImage) mDur = 1000.*getDuration();		// duration msec
	if(mDur > (newestTime - oldestTime)) mDur = newestTime - oldestTime;
	gotime = oldestTime + percentTime * (newestTime - oldestTime - mDur) / 100.;
	if(debug) console.debug("goTime: "+gotime+", percent: "+percentTime+", oldestTime: "+oldestTime+", newestTime: "+newestTime+", mDur: "+mDur);
	refreshCollection(true,gotime,"absolute");	// go to derived absolute time
	setTime(gotime+mDur);
}

//----------------------------------------------------------------------------------------	
/**
 * StripChart Utilities
 * Matt Miller, Cycronix
 * 11/2013
 */
//----------------------------------------------------------------------------------------	

//----------------------------------------------------------------------------------------	
// PLOT Object Definition
// Wrapper around SmoothieChart (smoothie.js)
// was stripchart.js
//----------------------------------------------------------------------------------------

function plot() {
	this.params = new Array();
	this.lines = {};
	this.horizGrids = 10;					// grid lines per plot width
	this.vertGrids = 4;						// grid lines per plot height
	this.width = 800;						// adjustable plot width (pixels)
	this.fillStyle = 'rgba(0,0,0,0.1)';		// under-line fill alpha
	this.doFill=false;						// under-line fill?
	this.doSmooth=false;					// bezier curve interpolate?
//	this.maxChartPts = 10000;		// keep points beyond visible limits up to this amount
									// ref:  86400 is one day at 1Hz
	duration=0;								// book keeping
	this.oldest=0;							// reference
	
	// over-ride defaults if provided 
	for (var n in arguments[0]) { this[n] = arguments[0][n]; }
//	console.log('plot doFill: '+this.doFill+', doSmooth: '+this.doSmooth);
	
	var interpolate;		// note: smooth is per chart (fill is per-line)
	if(doSmooth) interpolate = 'bezier';
	else		 interpolate = 'linear';
	
	// create smoothie chart
	if(debug) console.log('new chart');
	this.chart = new SmoothieChart({
		yRangeFunction:myYRangeFunction,
		interpolation:interpolate,			// linear, bezier
		grid:{ 
			fillStyle:'#ffffff', 
			strokeStyle:'#cccccc', 
			sharpLines:false, 
			verticalSections:this.vertGrids 
		},
		labels:{ 
			fillStyle:'#000000', 
			fontSize:16, 
			precision:1 
		},
//		timestampFormatter:SmoothieChart.timeFormatter
		timestampFormatter:myTimeFormatter,
		timerangeFormatter:myRangeFormatter
	});

	this.chart.options.scaleSmoothing = 0.25;		// default 0.125
	
// now handled by refreshCollection RBNB data fetch
//	this.chart.options['maxDataSetLength'] = this.maxChartPts;		// trial
//	this.chart.resetBounds = false;
	
	//----------------------------------------------------
	// set short range value with letter suffix
	function myRangeFormatter(val) {
	    var aval = Math.abs(val);
	      
		var prec=5;		// digits of precision
		if(aval == 0 || isNaN(aval)) prec=0;
		else if(aval > 10) 	prec=0;
		else if(aval > 1)  	prec=1;
		else 				prec = Math.ceil(-Math.log(aval)/Math.LN10);
		if(prec < 0 || prec > 5) Precision=5;
	      
	    valStr = parseFloat(val).toFixed(prec);
	    if(aval >= 1000000000)	 valStr = parseFloat(val/1000000000).toFixed(prec) + "G";
	    else if(aval >= 1000000) valStr = parseFloat(val/1000000).toFixed(prec) + "M";
	    else if(aval >= 1000) 	 valStr = parseFloat(val/1000).toFixed(prec) + "K";					
	    return valStr;
	}
	
	//----------------------------------------------------
	// Sample timestamp formatting function
	function myTimeFormatter(date) {
		function pad2(n) { return (n < 10 ? '0' : '') + n; }
		var y= pad2(date.getUTCFullYear() - 2000);
		var M= pad2(date.getUTCMonth()+1);
		var d= pad2(date.getUTCDate());
		var h= pad2(date.getUTCHours());
		var m= pad2(date.getUTCMinutes());
		var s= pad2(date.getUTCSeconds());
//		console.debug('duration: '+duration);
		var now = new Date().getTime();		// msec since 1970
		var then = date.getTime();
		var longAgo = (now - then) > 86400000;	// 1 day (msec)
		if(longAgo && duration > 864000)		return M+'/'+d+'/'+y;		// 10 days (duration=sec)
		else if(longAgo && duration >= 600) 	return M+'/'+d+'/'+y+' '+h+':'+m;
		else if(longAgo)						return M+'/'+d+'/'+y+' '+h+':'+m+':'+s;
		else if(duration >= 600)				return h+':'+m;		// trust sec==00 if min>=10
		else									return h+':'+m+':'+s;
	};
	
	// add a time series line to plot
	this.addLine = function(param) {
		if(this.lines[param]) {
//			alert("Duplicate Chan: "+param);
			return;
		}
		var fill=undefined;		// add lines per current fill state
		if(this.doFill) fill = this.fillStyle;
		this.params.push(param);
		var line = new TimeSeries();
		this.lines[param] = line;
		var coloridx = Object.keys(this.lines).length-1;
		if(debug) console.log('addTimeSeries');
		this.chart.addTimeSeries(line, { 
			lineWidth:1.4,
			strokeStyle:this.color(coloridx),
			fillStyle:fill 
		});
	};
	
	// append a data value to timeseries
	this.addValue = function(param, time, value) {
		if((value!=undefined) && !isNaN(value)) { 	// possible with slow initial fetch
			var line = this.lines[param];
			if(false) {		// try faster append without sort
				line.data.push([time, value]);		// try faster push 
				line.maxValue = isNaN(line.maxValue) ? value : Math.max(line.maxValue, value);
				line.minValue = isNaN(line.minValue) ? value : Math.min(line.minValue, value);
			} else {
				line.append(time, value);	// smoothie func
			}
			this.chart.now = time;		// for playback time render()
		}
	};
	
	this.getData = function(param) {
		return line = this.lines[param].data;		// array of time,value tuples
	}
	
	// tweak precision min/max label (ToDo: handle multiple lines/chart)
	this.setPrecision = function(param) {
		return;		// defunct with timerangeFormatter
		var line = this.lines[param];
		var Precision=5;
		var minV=Math.abs(line.minValue);
		var maxV=Math.abs(line.maxValue);
		var limitV = (minV > maxV) ? maxV : minV;
		if(limitV == 0 || isNaN(limitV)) Precision=0;
		else if(limitV > 10) Precision=0;
		else if(limitV > 1)  Precision=1;
		else 				 Precision = Math.ceil(-Math.log(limitV)/Math.LN10);
		if(Precision < 0 || Precision > 5) Precision=5;
		this.chart.options.labels.precision=Precision;	
	};
	
	// associate a canvas with this chart
	this.addCanvas = function(element) {
		this.width = element.width;
		this.canvas = element;
		this.chart.streamTo(element,1000);  // 1 sec delay
	};
	
	this.setDelay = function(delay) {
		this.chart.delay = delay;
	};
	
	this.stop = function() {			// no go
		this.chart.stop();
	};
	
	this.start = function() {			// re go
		this.dropdata();				// eliminate old-data glitch?
		this.chart.start();
	};
	
	this.render = function(etime) {
		if(typeof this.canvas == 'undefined') return;		// notta to do
//		console.debug('this.chart: '+this.chart+', this.canvas: '+this.canvas);
		var chartnow = (etime>0)?etime:this.chart.now;
//		console.debug('cnow: '+this.chart.now+', etime: '+etime);
		this.chart.options.scaleSmoothing = 1.0;	// one-step jump to new scale
		for(var key in this.lines) this.lines[key].resetBounds();
		this.chart.render(this.canvas, chartnow);
		this.chart.options.scaleSmoothing = 0.25;   		// was 0.125
	};
	
	this.dropdata = function() {
		this.chart.stop();
		for(var j=0; j<this.chart.seriesSet.length; j++) {
			var ts = this.chart.seriesSet[j].timeSeries;
//			this.chart.seriesSet[j].timeSeries.dropOldData(new Date().getTime(), 2);
			ts.data.splice(0, ts.data.length);		// delete all data
		};
	};
	
	this.clear = function() {			// stop interval timers
		this.chart.stop();
		for(var j=0; j<this.chart.seriesSet.length; j++) 
			this.chart.removeTimeSeries(this.chart.seriesSet[j]);
	};
	
	this.nuke = function() {			// nuke charts and lines?
		this.clear();
		for(var i=0; i<this.lines.length; i++) delete this.lines[i];
//		delete this.lines;
		delete this.chart;
	};
	
	// adjust plot width to meet duration
	this.setDuration = function(secondsPerPlot) {
		duration = secondsPerPlot;
		var millisPerPixel = 1000 * secondsPerPlot / this.width;
		var millisPerLine = millisPerPixel * this.width / this.horizGrids;
//		console.debug('millisPerPixel: '+millisPerPixel);
		this.chart.options['millisPerPixel'] = millisPerPixel;
		this.chart.options.grid['millisPerLine'] = millisPerLine;
	};
	
	// set plot interpolate option
	this.setSmooth = function(dosmooth) {
		this.doSmooth = dosmooth;
		if(dosmooth) this.chart.options.interpolation = 'bezier';
		else		 this.chart.options.interpolation = 'linear';
	};
	
	// set plot fill options all lines this plot
	this.setFill = function(dofill) {
		this.doFill = dofill;
		var fill = undefined;
		if(dofill) fill = this.fillStyle;
		for(var j=0; j<this.chart.seriesSet.length; j++) {
			this.chart.seriesSet[j].options.fillStyle=fill;
		}
	};
	
	// myYRangeFunction:  custom y-range function
	function myYRangeFunction(range) {
//		console.log('myrange: '+range.min+', '+range.max);
//		return({min: range.min, max: range.max});		// foo
		var vmin = roundHumane(range.min,0);
		var vmax = roundHumane(range.max,1);
		if((vmin*vmax>0) && (vmin/vmax <= 0.25)) vmin = 0.;
		return {min: vmin, max: vmax};
	} 
	
	// roundHumane: nicely round numbers up for human beings (adapted from smoothieChart)
	// Eg: 180.2 -> 200, 3.5 -> 5, 8.9 -> 10
	function roundHumane(value, up) {
		if(value == 0) return 0;						// notta to do
		var ln10 = Math.log(10);
		var sign=(value>0)?1:-1;
		value = Math.abs(value);
		var mag = Math.floor(Math.log(value) / ln10);	// magnitude of the value
		var magPow = Math.pow(10, mag);
		var magMsd = Math.ceil(value / magPow);			// most significant digit
	
		// promote/demote MSD to 1, 2, 5
		var gobig = (up && sign>0 || !up && sign<0)?1:0;					
		if (magMsd > 5.0) 		{ if(gobig) 	magMsd = 10.0;	else 	magMsd = 5.0; }
		else if (magMsd > 2.0)	{ if(gobig) 	magMsd = 5.0;	else	magMsd = 2.0; }
		else if (magMsd > 1.0) 	{ if(gobig)		magMsd = 2.0;	else	magMsd = 1.0; }
		return sign * magMsd * magPow;
	}
}

//----------------------------------------------------------------------------------------
// plot.prototype.color:  get color from array, limit length

plot.prototype.color = function(idx) {
	var colors = new Array('#0000ff','#ff0000','#00dd00','#880088','#000000','#808080');	// ~RGB
	if(idx < colors.length) return colors[idx];
	else					return colors[colors.length-1];
};

//----------------------------------------------------------------------------------------	
/**
 * PlotBox Wrapper Object
 * Matt Miller, Cycronix
 * 03/2014
 */
//----------------------------------------------------------------------------------------	

//----------------------------------------------------------------------------------------	
// PLOTBOX Object Definition
// Wrapper around stripcharts, video, gages, etc 
//----------------------------------------------------------------------------------------

function plotbox() {
	this.params = new Array();
	this.type = null;
	this.display = null;
	this.canvas = null;
	this.doFill=false;						// under-line fill?
	this.doSmooth=false;					// bezier curve interpolate?
	
	// add a parameter to this plot
	this.addParam = function(param) {
		
		if		(endsWith(param, ".jpg")) 							paramtype = 'video';
		else if	(endsWith(param,".mp3") || endsWith(param,".wav")) 	paramtype = 'audio';
		else														paramtype = 'stripchart';
		if(this.type == null) this.type = paramtype; 			// only set type on first param

//		console.log('addParam: '+param+", type: "+this.type);

		switch(this.type) {
			case 'stripchart': 
				if(paramtype == 'video') {
					alert("cannot add video to stripchart");
					return;
				}
				if(this.display == null) this.display = new plot({doFill:doFill,doSmooth:doSmooth});	
				this.display.addLine(param);
				break;
			case 'video':	
				if(this.display == null) this.display = new vidscan(param);	
				else if(paramtype == 'video') {		// presume a stripchart added to video is audio
					alert("only one video per plot");	
					return;
				}
				break;	
			case 'audio':		
				if(this.display == null) this.display = new audioscan(param);	
				else if(paramtype == 'audio') {		// presume a stripchart added to video is audio
					alert("only one audio per plot");	
					return;
				}
				break;	
		}
		this.params.push(param);
	}

	// utility function
	function endsWith(str, suffix) {
	    return str.indexOf(suffix, str.length - suffix.length) !== -1;
	}
	
	this.clear = function() {
		switch(this.type) {
			case 'stripchart':	this.display.clear();	break;
		}
	}
	
	this.addValue = function(param, time, value) {
		switch(this.type) {
			case 'stripchart':	this.display.addValue(param, time, value);	break;
		}
	}
	
	this.render = function(etime) {
		switch(this.type) {
			case 'stripchart':	this.display.render(etime);		break;
		}
	}
	
	this.start = function() {
//		console.log("plotbox.start, display: "+this.display);
		switch(this.type) {
			case 'stripchart':	this.display.start();		break;
//			case 'video':		this.display.vidPlay();		break;
		}
	}
	
	this.stop = function() {
		switch(this.type) {
			case 'stripchart':	this.display.stop();		break;
//			case 'video':		this.display.vidStop();		break;
		}
	}
	
	this.setDelay = function(delay) {
		switch(this.type) {
			case 'stripchart':	this.display.setDelay(delay);	break;
		}
	}
	
	this.dropdata = function() {
		switch(this.type) {
			case 'stripchart':	this.display.dropdata();	break;
		}		
	}
	
	this.setDuration = function(secondsPerPlot) {
		switch(this.type) {
			case 'stripchart':	this.display.setDuration(secondsPerPlot);	break;
		}	
	}
	
	this.setSmooth = function(dosmooth) {
		switch(this.type) {
			case 'stripchart':	this.display.setSmooth(dosmooth);	break;
		}
	};
	
	this.setFill = function(dofill) {
		switch(this.type) {
			case 'stripchart':	this.display.setFill(dofill);	break;
		}
	}
	
	this.addCanvas = function(element) {
		this.canvas = element;
		if(this.display != null) {
//			console.log('plotbox addCanvas: '+element.id);
			this.display.addCanvas(element);
		}
	}
	
	this.color = function(idx) {	
		var colors = new Array('#0000ff','#ff0000','#00dd00','#880088','#000000','#808080');	// ~RGB
		if(idx < colors.length) return colors[idx];
		else					return colors[colors.length-1];
	}
}

//----------------------------------------------------------------------------------------	
// AudioScan:  audio replay functions
//----------------------------------------------------------------------------------------	

//---------------------------------------------------------------------------------	
var audioContext=null;
var audioAlert=true;
function audioscan() {
//	console.debug('new audioscan');
	this.rate = 22050;			// hard code audio rate for now.  22050 is slowest Web Audio supports
	
	if(audioContext == null) {
		var contextClass = (window.AudioContext || window.webkitAudioContext || window.mozAudioContext || window.oAudioContext || window.msAudioContext);
		if (contextClass) audioContext = new contextClass();
	}	
	if(audioContext == null) {
		if(audioAlert) {
//			audioAlert=false;		// one time alert
			alert('no audio context available with this browser');
		}
		goPause();
		return;
	}
	
	var source = audioContext.createBufferSource();
	source.connect(audioContext.destination);
	
    this.playPcmChunk = function(data) {
		var audio=new Float32Array(data);
		var audioBuffer = audioContext.createBuffer(1, audio.length , this.rate);
		audioBuffer.getChannelData(0).set(audio);
		source.buffer = audioBuffer;
		source.start(0);
    }
    
    this.playMp3Chunk = function(data) {
    	audioContext.decodeAudioData(data, this.playPcmChunk);
    }
    
	this.playAudio = function(audio) {		
//		audio = bytesToAudio(audio);
		if(audio.length == 0) return;		// notta
		var audio=new Float32Array(data);
		var audioBuffer = audioContext.createBuffer(1, audio.length , this.rate);
		audioBuffer.getChannelData(0).set(audio);
//		var audioBuffer = audioContext.createBuffer(1, audio.length , this.rate);
//		audioBuffer.getChannelData(0).set(audio);
		source.buffer = audioBuffer;
		source.start(0);
/*		
		audioContext.decodeAudioData(audio,function(buffer){
			source.buffer = buffer;
			source.start(0);
		},alert('decodeAudio Error'));
*/
	}
	
	//----------------------------------------------------------------------------------------
	// bytesToAudio:  convert byte array of shorts to audio array (scaled floats)

	function bytesToAudio(barray) {
		var n = barray.length / 2;
//		console.debug('n: '+n);
		var sarray = new Float32Array();
		for(i=0, j=0; i<n; i++,j+=2) {
			sarray[i] = (barray[j] + barray[j+1]*256.) / 32768.;
		}

		return sarray;
	}
	
}

/*
var audioNode = audioContext.createJavaScriptNode(4096, 1, 1);

audioNode.onaudioprocess = function(event) {
                           if (decodedAudioBuffer.length >= bufferSize) {
                                  var decoded = decodedAudioBuffer.splice(0, bufferSize);
                                  var samples = new Float32Array(bufferSize);
                                  for (var i=0; i<decoded.length; i++) {
                                         samples[i] = decoded[i];
                                  }

                                  samples = resampler.resample(samples);
                                  var output = event.outputBuffer.getChannelData(0);
                                    for (var i = 0; i < output.length; i++) {
                                      output[i] = samples[i];
                                    }

                           }
};

audioNode.connect(audioContext.destination);
*/

//----------------------------------------------------------------------------------------	
/**
 * VidScan
 * Matt Miller, Cycronix
 * 11/2013
 * 
 * V0.1: Initial prototype release
 * V0.9: Sync with webscan plotTime
 * V2.0: adapt to webscan embedded video plots
 */
//----------------------------------------------------------------------------------------	

//----------------------------------------------------------------------------------------
// vidscan:  main function

function vidscan(param) {
//	console.log('new vidscan: '+param);
	this.video_inprogress = false;
	
// globals
	Tnew=0;
	Told=0;
	this.canvas=null;
	
	this.img=new Image();
	this.img.onload = imgload.bind(this);
	this.img.onerror = imgerror.bind(this);
	
//    getTlimit("/CT/"+param);			// initialize time limits
//    getTlimit(serverAddr+servletRoot+"/"+param);			// initialize time limits


//  ----------------------------------------------------------------------------------------    
//  addCanvas:  set canvas object

    this.addCanvas = function(element) {
    	this.canvas = element;
    	canvas = element;		// foo
//    	console.log('vidscan addCanvas: '+this.canvas.id);
    }

//  ----------------------------------------------------------------------------------------    
//  imgerror:  handle image load error

    function imgerror() {
    	this.video_inprogress=false;
    	console.debug('vidscan imgerror, playmode: '+getPlayMode());
    	if(getPlayMode()==1) adjustPlayDelay(1000);		// try slowing down
    	else				 goPause();					//  stop!
    }

//  ----------------------------------------------------------------------------------------    
    var lastload=0;
    this.setImage = function(imgurl) {
//    	console.log("vidscan setImage, inprogress: "+video_inprogress);
		var now = new Date().getTime();
//    	if((imgurl.indexOf("r=newest") != -1) || (imgurl.indexOf("r=oldest") != -1)) { // no wait check on limits requests
//		console.log('inprogress: '+this.video_inprogress+', now-last: '+(now-lastload));

		if((now-lastload)>500) {		// checks to avoid deadlock
//			console.debug('reset video_inprogress');
    		this.video_inprogress = false;
    	}
		else if(this.video_inprogress) {		// don't overwhelm 
//    		console.debug('video busy, skipping');
    		return;						
    	}
		
//		if(imgurl.indexOf("r=newest") != -1) getTnew(imgurl.split("?")[0]);		// async
//		if(imgurl.indexOf("r=oldest") != -1) getTold(imgurl.split("?")[0]);
		lastload = now;
		this.video_inprogress=true;
		this.img.src = imgurl;													// this is what initiates network fetch
//    	AjaxGetImage(imgurl,this.img);
    }

    this.setTime = function(imgurl) {
//    	console.log("image.setTime: "+imgurl);
    	AjaxGetV(imageSetTime, imgurl+"&dt=s&f=t");
    	function imageSetTime(txt) {
    		stime = Math.floor(1000*parseFloat(txt));
    		if	   (imgurl.indexOf("r=newest") != -1) Tnew = newestTime = stime;
    		else if(imgurl.indexOf("r=oldest") != -1) Told = oldestTime = stime;
    		setTimeNoSlider(stime);
//    		console.log("image setTime: "+stime+", url: "+imgurl+", oldestTime: "+oldestTime);
    	}
    }
    
//  ----------------------------------------------------------------------------------------    
//  imgload:  draw upon image load

    function imgload() {
//    	console.log('imgload: '+this.img);
    	this.video_inprogress = false;

    	ratiox = this.img.width / this.canvas.width;
    	ratioy = this.img.height / this.canvas.height;
    	if(ratiox > ratioy) {
    		w = this.canvas.width;
    		h = this.img.height / ratiox;
    		x = 0;
    		y = (this.canvas.height - h) / 2;
    	}
    	else {
    		h = this.canvas.height;
    		w = this.img.width / ratioy;
    		x = (this.canvas.width - w) / 2;
    		y = 0;
    	}
    	
    	var ctx = this.canvas.getContext('2d');
		ctx.clearRect(0,0,this.canvas.width,this.canvas.height); 		// clear old image
    	ctx.drawImage(this.img,x,y,w,h);
//    	console.log('imgload done');
//    	nreq = 0;			// reset
    }

//  ----------------------------------------------------------------------------------------    

    this.getTold = function() { return Told; }
    this.getTnew = function() { return Tnew; }


//  ----------------------------------------------------------------------------------------    
//  getTlimit:  get time limit (Tnew - Tstart)  msec
    
    function getTlimit() {
    	getTlimit(mysrc, mysrc+"?r=newest&f=t&dt=s");
    }
    
    function getTlimit(mysrc) {
    	Told = Tnew = 0;				// reset until update
    	getTnew(mysrc);
    	getTold(mysrc);
    }

    function getTnew(mysrc) {
    	AjaxGetV(setNew, mysrc+"?r=newest&f=t&dt=s");
    	function setNew(txt) {
    		Tnew = Math.floor(1000*parseFloat(txt));
//    		console.log("Tnew: "+Tnew);
    	}
    }
    
    function getTold(mysrc) {
    	AjaxGetV(setOld, mysrc+"?r=oldest&f=t&dt=s");
    	function setOld(txt) {
    		Told = Math.floor(1000*parseFloat(txt));
//    		console.log("Told: "+Told);
    	}
    }
    
//  ----------------------------------------------------------------------------------------
//  AjaxGet:  Ajax request helper func

    function AjaxGetV(myfunc, url, args) {	
    	var xmlhttp=new XMLHttpRequest();

    	xmlhttp.onreadystatechange=function() {
    		if (xmlhttp.readyState==4) {
    			if(xmlhttp.status==200) {
    				myfunc(xmlhttp.responseText, url, args);
    			}
    			else {
//  				console.log('Error: '+url);
// following alert can be deadly lock-up loop on ipad:  add status field to display?
//    				alert('Error on data fetch! '+url+', status: '+xmlhttp.status);
    			}
    		}
    	};
    	xmlhttp.open("GET",url,true);
    	xmlhttp.send();
    }


//  ----------------------------------------------------------------------------------------
// AjaxGetImage:  long way around to get image header with timestamp
// this is not reliable and portable:  partial images chrome/Win8, no images android/std-browser...
// go with much simpler/reliable img.src=foo, deal with timestamps separately.
// this is also more compatible with webturbine...
    var Tlast=0;
    nreq=0;
    AjaxGetImage = function(url,img) { 
//		if(url.indexOf("r=newest") != -1) getTnew(url);		// update
//		if(url.indexOf("r=oldest") != -1) getTold(url);
		
//    	console.log('AjaxGetImage, nreq: '+nreq);
//    	if(this.nreq > 2) return;		// drop frames if getting behind...
    	var xmlhttp=new XMLHttpRequest();
    	xmlhttp.onreadystatechange=function() {
//			console.log("Ajax readstatechange: "+xmlhttp.readyState);
    		if (xmlhttp.readyState==4) {
    			if(xmlhttp.status==200) {
//    				console.log("AjaxGetImage: "+url);
    				var wurl = window.URL || window.webkitURL;
    				var tstamp = this.getResponseHeader("time");
//    				console.log('image header: '+tstamp);
    		    	img.src = wurl.createObjectURL(new Blob([this.response], {type: "image/jpeg"}));
    		    	if(tstamp != null) {
    		    		var T = Math.floor(1000*parseFloat(tstamp));
    		    		if(T > Tnew || (url.indexOf("r=newest") != -1)) Tnew = T;
    		    		if((Told==0) || (T < Told) || (url.indexOf("r=oldest") != -1)) Told = T;
        		    	if(T == Tlast) console.log("warning, duplicate time: "+T);
//        		    	console.log("T: "+T+", Tlast: "+Tlast);
        		    	Tlast = T;
    		    	}
    		    	else {	// update time limits if not provided in HTTP header
    		    		if(url.indexOf("r=newest") != -1) getTnew(url);
    		    		if(url.indexOf("r=oldest") != -1) getTold(url);
    		    	}
    			}
    			else {
    				console.log('Warning, xmlhttp.status: '+xmlhttp.status);
    				goPause();		// stop!
    				// following alert can be deadly lock-up loop on ipad:  add status field to display?
//    				alert('Error on image fetch! '+url+', status: '+xmlhttp.status);
    			}
    		}
    	};
    	xmlhttp.open("GET",url,true);
    	xmlhttp.responseType = 'blob';
    	xmlhttp.send();
//    	nreq++;
    }
}

