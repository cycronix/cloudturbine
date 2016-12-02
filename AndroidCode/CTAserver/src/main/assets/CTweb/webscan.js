/*
Copyright 2016 Cycronix

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
 * 02/2014 - 11/2016
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
 * V2.0B3:  support next/prev images
 * V2.0B4:  Tight scaling
 * V2.0B5:  Manual scaling
 * V2.0B6:  Improved popup Options menu
 * V2.0B6a: Reworked touch-controls for IE10/11 (disabled pointer syntax for now)
 * V2.0B7:  Bug fixes... better setTime logic
 * V2.0B8:  Tweaked RT display to fix data-gap potential, ...  Frozen at semi-stable version
 * V2.0B9:	Rework RT play (back) to be play-at-delay logic
 * V2.0B10:	Better wild-point rejection scaling for "Tight" scaling
 * V2.0:	Same as V2.0B10, production release
 * V3b1:	Rework controls/UI, add playRvs
 * V3b2-7:	Streamline, responsive UI, improved RT, image layers
 * V3.0:	Freeze V3b7 as release (11/23/2016)
 */

//----------------------------------------------------------------------------------------	
// globals of control variables
var myName="webscan";
//var servletRoot="/CT";			// "CT" works only for CTserver	
var servletRoot="/RBNB";			// "/RBNB" or "/CT" ("RBNB" works for both CTserver and WebTurbine)
var serverAddr="";					// cross domain server...
//var serverAddr="";				// "" for local access

var tDelay=1000;					// initial data fetch interval (msec)
var doRate=true;					// set true to support UI rate selection
var maxLayer=4;						// max number of image layers

var debug=false;					// turn on some console.log prints
//var extraFetch=0.;

// some globals for callback functions
var channels = new Array();			// list of selectable channels (all plots)
var intervalID=0;					// timer id for start/stop
var intervalID2=0;					// timer id for start/stop (fast 10x rate)
var intervalID3=0;					// adaptive timer id
var noRebuild=false;				// defer rebuild during smartphone selections
var plots = new Array();			// array of plots
//var plotbox = new Array();			// array of plot-boxes (generic display)
var doFill=false;					// fill under-line
var doSmooth=true;					// smooth plot lines
//var refTime=null;					// refTime for fetch ("oldest", "newest", "absolute")
var inProgress=0;
//var LEtime=0;						// left edge (oldest) time
//var plotTime=0;						// master plot display time
var lastreqTime=0;					// right edge most recent request time
var lastgotTime=0;					// right edge (newest) time
var lastmediaTime=0;				// most recent fetched video time
var oldgotTime=0;					// left edge (oldest) time
var oldestTime=0;					// oldest available time (refTime="oldest")
var newgotTime=0;					// right edge (newest) time
var newestTime=0;					// newest available time (refTime="newest")
var stepDir=0;						// flag which way stepping
var refreshInProgress=false;		// flag full-refresh collection cycle
var isTouchSupported=false;
var singleStep=false;				// set based on RTrate/View ratio
var isImage=false;					// latest plot is image?
var numCol=0;						// numcols per plot row (0=auto)
var reScale=true;					// one shot rescale flag
var rtmode=1;						// real-time mode flag (rtmode==1 for latest play-RT MJM 8/24/16)
var playStr="&gt;";					// ">" play mode

top.rtflag=0;						// RT state (for eavesdroppers)
top.plotTime=0;						// sec
top.plotDuration=0;					// sec

var PAUSE=0;						// play mode pseudo-constants
var RT=1;
var PLAY=2;

var scalingMode="Auto";				// scaling "Standard" (1-2-5) or "Tight" or "Auto" (Std increasing only) 

var paramTime = new Array();		// array of times for each parameter

//----------------------------------------------------------------------------------------
//webscan:  top level main method

function webscan(server) {
	if(server) serverAddr = server;
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
		setDivSize();		// redundant?
	};

	buildCharts();					// build  stripcharts
//	setTimeout(function(){buildCharts();}, 500); 	// rebuild after init? (for chartscan, complete channel lists)
	getLimits(1,1);					// establish time limits
//	goEOF();
	setTimeout(function(){ goBOF();}, 1000); 		// make sure data is shown at startup (was goBOF, 1000)

}

//----------------------------------------------------------------------------------------
// set div-heights dynamically so graphs fills space between title and control divs
function setDivSize() {
	var wrap=document.getElementById('wrapper').clientHeight;
	var ch=document.getElementById('dcontrols').clientHeight;
//	document.getElementById('dcontrols').style.bottom="0px";
	var top=document.getElementById('title').clientHeight;
	var bot=document.getElementById('dcontrols').offsetTop;
	document.getElementById('dgraphs').style.top = top+"px";
//	document.getElementById('dgraphs').style.bottom = ch+"px";
	document.getElementById('dgraphs').style.bottom=(wrap-bot)+"px";
//	console.log('resize, top: '+top+', bot: '+bot+', h: '+wrap+', newsize: '+(wrap-bot));
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
//	var myurl = (window.location != window.parent.location) ? window.parent.location : window.location;
//	console.debug('url: '+myurl+', window.location: '+window.location+', substring: '+myurl.search.substring(1)+", key: "+key);
	return getURLParam(myURL().search.substring(1), key);
//	return getURLParam(window.location.search.substring(1), key);
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

// return URL of local window or parent window if embedded iframe
function myURL() {
	var myurl = (window.location != window.parent.location) ? window.parent.location : window.location;
//	console.debug('myURL: '+myurl+', window.location: '+window.location+', window.parent.location: '+window.parent.location);
//	console.debug('top.document.URL: '+top.document.URL);
	return window.location;			// just return iframe url to get params!
//	return myurl;
}

//----------------------------------------------------------------------------------------
//setURLParam:  update or add query string parameter to URL

function setURLParam(uri, key, value) {
	if(!value || value=="") return uri;		// no-op
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
	
	var url = myURL();
	var urlhref = url.protocol + '//' + url.host + url.pathname + getCookie(myName);
	if(debug) console.debug('reloadConfig getCookie: '+getCookie(myName)+', myName: '+myName+', href: '+urlhref);

	url.href = urlhref		
//	window.location.href = location.protocol + '//' + location.host + location.pathname + getCookie(myName);
}

function configParams(src) {
	var dt 		 = getURLParam(src,'dt');	if(dt != null) setRate(dt);						setConfig('dt',tDelay);
	var nplot 	 = getURLParam(src,'n');  	if(nplot != null) setPlots(parseInt(nplot));	setConfig('n', nplot);
	var uncol 	 = getURLParam(src,'c');  	if(uncol != null) setCols(parseInt(uncol));		setConfig('c', numCol);
	var fill 	 = getURLParam(src,'f');	setFill(fill=="true");							setConfig('f', fill=="true");
	var smooth 	 = getURLParam(src,'sm');	setSmooth(smooth=="true");						setConfig('sm', smooth=="true");		// was 's'
	var duration = getURLParam(src,'v');	if(duration != null) setDuration(duration);		setConfig('v', duration);
	var scaling  = getURLParam(src,'sc');	if(scaling != null) setScaling(scaling);		setConfig('sc', scaling);
	var server   = getURLParam(src,'sv'); 	if(server != null) serverAddr = server;			setConfig('sv', serverAddr);

	// RT mode (one-shot param)
	var irtm  	 = getURLParam(src,'rt'); 	if(irtm!=null) rtmode=Number(irtm);
	
//	console.debug('configParams, tDelay: '+tDelay+", nplot: "+nplot+', rtmode: '+rtmode);
	
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
	configParams(myURL().search.substring(1));
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
//setScaling:  initialize scalingMode

function setScaling(scaling) {
	scalingMode = "Standard";
	if(scaling == 't') scalingMode = "Tight";
	else if(scaling == 'm') scalingMode = "Manual";
	else if(scaling == 'a') scalingMode = "Auto"
	var el = document.getElementById('myScaling');
	for(var i=0; i<el.options.length; i++) {
		if(scalingMode == el.options[i].value) {		// enforce consistency
			el.options[i].selected=true;
			break;
		}
	}
}

//----------------------------------------------------------------------------------------
//fetchData:  Use AJAX to fetch data

var refreshCount=0;	

function fetchData(param, plotidx, duration, time, refTime) {		// duration (msec)
	if((typeof(param) == 'undefined') || param == null) return;			// undefined
	
	if(debug) {
		console.log('fetchData, param: '+param+', duration: '+duration+', time: '+time+", refTime: "+refTime);
		console.trace();
	}
//	if(inProgress >= 2) return;		// skip fetching if way behind?
	
	isImage = endsWith(param, ".jpg");	// this is a global, affects logic based on last-plot (still issue with mixed stripcharts/images)
	
	// audio with video: fetch as binary
	var isAudio = ((endsWith(param, ".pcm") || endsWith(param, ".mp3") || endsWith(param, ".wav")));			// FFMPEG s16sle, or MP3 audio
	
	// text type data
	var isText = endsWith(param, ".txt");
	
	if(!isImage && (refTime == "next" || refTime == "prev")) {				// no next/prev with stripcharts?
		refTime = "absolute";
	}
	
	var munge="";			// formulate the url munge params
	
	if(isAudio) {	
		munge = "?dt=b";						// binary fetch
//		if(refTime == "newest" || refTime == "after") munge+="&refresh="+(refreshCount++);		// no browser cache on newest
		if(refTime == "newest" || refTime == "after") munge+="&refresh="+(new Date().getTime());		// no browser cache on newest

		if(endsWith(param,".wav")) munge += ("&d="+duration/1000.); else		// FOO try to get something to play in .wav format			
		
//		if(top.rtflag == PAUSE && duration>200) 
//				munge += ("&d="+0.2);					// limit binary audio duration during scrolling
//		else	
			munge += ("&d="+duration/1000.);		// rt playback presumes duration increment steps...
		
		if(refTime) munge += ("&r="+refTime);
		if(time < 0) time = 0;
		if(refTime!="newest" && refTime != "oldest") munge+=("&t="+time/1000.);		// no relative offsets
		
		var url = serverAddr + servletRoot+"/"+escape(param)+munge;
		if(endsWith(param,".mp3")) 		setAudioByType(url,"mp3");
//		else if(endsWith(param,".wav")) setAudioByType(url,"wav");		
		else	{
//			setAudio(url, duration);
			setAudio(url, param, plotidx, duration, time, refTime);		// single fetch, setParamValue from binary
//			return;
		}
//		if(plots[plotidx].type=='video') return;						// if audio with video, no stripchart
//		if(endsWith(param,".wav")) return;			// FOO, try to get .wav to play
		return;
	}
		
	if(isImage || isText) {									
		munge = "?dt=b";						// binary fetch
		if(refTime) munge += ("&r="+refTime);
//		munge+="&refresh="+(new Date().getTime());			// ensure nocache
	} 
	else  {				
		munge = "?dt=s&f=b";
		munge += ("&d="+duration/1000.);
		if(refTime) { munge += ("&r="+refTime); }
		if(refTime == "absolute") lastreqTime = time + duration;		// right edge time (only update on stripcharts)
	}
	

	if(refTime == "absolute" || refTime == "next" || refTime == "prev" || refTime == "after") munge+=("&t="+time/1000.);
//	if(refTime == "newest" || refTime == "after") munge+="&refresh="+(refreshCount++);		// no browser cache on newest
//	if(refTime == "newest" || refTime == "after" || refTime == "next") 
	if(refTime != "absolute")
		munge+="&refresh="+(new Date().getTime());		// no browser cache on ANY non-absolute time call


	var url = serverAddr + servletRoot+"/"+escape(param)+munge;

	if(isImage) {
//		var alpha = (param == plots[plotidx].params[0]) ? 1: 0.5;
		plots[plotidx].display.setImage(url,param,plots[plotidx].params.indexOf(param));
	} else {	
		if(isText) 	AjaxGet(setParamText, url, arguments);
		else 		AjaxGet(setParamValue, url, arguments);		// 'arguments' javascript keyword
		inProgress++;
//		document.body.style.cursor = 'wait';
	}
}

//----------------------------------------------------------------------------------------
// utility function
function endsWith(str, suffix) {
    return str.indexOf(suffix, str.length - suffix.length) !== -1;
}

//----------------------------------------------------------------------------------------
// setAudio:  make and play audio request

//var ascan = null;
var audioContext = new (window.AudioContext || window.webkitAudioContext)();

function setAudio(url, param, plotidx, duration, time, refTime) {
	if(debug) console.log("setAudio: "+url+", time: "+time+', refTime: '+refTime);
//	if(ascan == null) ascan = new audioscan();		// save recreation every time?

	var audioRequest = new XMLHttpRequest();
	audioRequest.open('GET', url, true); 				//	open the request...
	audioRequest.responseType = 'arraybuffer';			// binary response
	
	var waveHdrLen = 0;
	if(endsWith(param,".wav")) waveHdrLen = 22;			// strip leading header if .wav format (size as int16 vals)
//	console.debug("waveHeader: "+waveHdrLen+", param: "+param);
	
	// TO DO:  consolidate this with AjaxGet logic (largely redundant)
    audioRequest.onreadystatechange = function () {
        if (audioRequest.readyState == 4) {
			if(audioRequest.status==200) {
				if(audioRequest.response.byteLength > 1) {

					var buffer = new Int16Array(audioRequest.response);
					var nval = buffer.length - waveHdrLen;

					var estRate = (buffer.length - waveHdrLen) / (duration/1000.);
					if(estRate > 10000) estRate = 22050;		// simple guess one of two rates
					else				estRate = 8000;
					
					var floats = new Array();
//					var floats = new ArrayBuffer(nval);		// mjm try ArrayBuffer vs Array?
//					var floats = audioContext.createBuffer(1, nval, estRate);

//					if((top.rtflag==PAUSE) && ((nval / estRate) > 0.2)) nval = 0.2 * estRate;		// limit audio snips to 0.2sec if manually scrolling
					for(i=0, j=waveHdrLen; i<nval; i++,j++) floats[i] = buffer[j] / 32768.;
//					for(var i=0; i<nval; i++) floats[i] = buffer[i] / 32768.;
					// pull info out of header if available
					var htime = audioRequest.getResponseHeader("time");		
					if(htime != null) 	htime = 1000 * Number(htime);			// sec -> msec
					else if(refTime=="oldest") 	htime = oldestTime;
					else if(refTime=="newest") 	htime = newestTime;
					else						htime = time;
					
					// Last-Modified time would be handy except it is only integer-seconds resolution
//					var lastmodified = audioRequest.getResponseHeader("Last-Modified");
//					var lastmodtime = new Date(lastmodified).getTime();
//					console.log("setAudio, lastModified: "+lastmodified+', lastmodtime: '+lastmodtime+', htime: '+htime);
					
					var hdur = audioRequest.getResponseHeader("duration");		
					if(hdur != null) 	hdur = 1000 * Number(hdur);				// sec -> msec
					else			  	hdur = duration;
// ??					if(waveHdrLen > 0) hdur = 1000. * floats.length / estRate;	// msec
					
					var holdest = audioRequest.getResponseHeader("oldest");	
					if(holdest != null) oldestTime = 1000 * Number(holdest);	// just set it, worry about multi-chan consistency later...
//					console.debug('setAudio, holdest: '+holdest+', oldestTime: '+oldestTime);

					var hnewest = audioRequest.getResponseHeader("newest");	
//					console.log("setAudio, hnewest: "+hnewest+", newestTime: "+newestTime);
					if(hnewest != null) {
						newestTime = 1000 * Number(hnewest);
						paramTime[param] = newestTime;
					}
					if(paramTime[param]==null) paramTime[param]= newestTime;		// ??

					if(debug) console.log("--gotAudio, len: "+floats.length+", duration: "+duration+", estRate: "+estRate+", headerTime: "+htime+", header duration: "+hdur);
					
					if(duration == 0) {		// no display on limit checks
						if(debug) console.debug("duration0, tstamp: "+htime+", oldestTime: "+oldestTime+", newestTime: "+newestTime);
						if(refTime=="oldest" && htime!=0) { setTime(htime);	document.getElementById('TimeSelect').value=0; 	  oldestTime=htime; }	
						if(refTime=="newest" && htime!=0) { setTime(htime);	document.getElementById('TimeSelect').value=100;  newestTime=htime; }	
					}
					else {
						setParamBinary(floats, url, param, plotidx, hdur, htime, refTime);	
						if(htime && htime>0) setTime(htime+duration);		// htime better be good
					}
					
					// trim audio playback if sliding thru data (after plotting all of it!)
					if((top.rtflag==PAUSE) && ((nval / estRate) > 0.2)) {
						nval = 0.2 * estRate;		// limit audio snips to 0.2sec if manually scrolling
						if(debug) console.log("+++++got: "+buffer.length+", trimmed: "+nval+", nval/estRate: "+(nval/estRate));
						floats = floats.slice(0,nval);
					}

					if(stepDir != -2) new audioscan().playPcmChunk(floats, estRate);		// no reverse-play audio
//					new audioscan().playPcmChunk(audioRequest.response, estRate);

					if(debug) console.debug("time: "+time+", htime: "+htime+", lastreqTime: "+lastreqTime);
				}
//				else console.debug('invalid pcm audio response buffer');
			}
			else {		// ERROR Handling
				if(debug) console.warn('Error on audio fetch! '+url+', status: '+audioRequest.status);
				if(intervalID && singleStep) plots[plotidx].render(time);			// scroll plots if playing
				
				if(intervalID && audioRequest.status != 404) goPause();		// play thru gaps
				if(refTime=="absolute" && time>0) setTime(time);				// move slider thru gaps
				
				inProgress=0;		// no deadlock
//				document.body.style.cursor = 'default';		// done with this fetch
				if(intervalID) { 	//  no warn on shutdown
					if(top.rtflag==RT) return;
					else if(((time+duration) >= newestTime) || (audioRequest.status != 410 && audioRequest.status != 404)) {				// keep going (over gaps)
						if(debug) console.log('stopping on xmlhttp.status: '+audioRequest.status+", time: "+time+", newestTime: "+newestTime);
						goPause();	
					}
				}

			}
        }
    }
	audioRequest.send();		// send the request
	inProgress++;
}

//----------------------------------------------------------------------------------------
// set audio by format of audio file - not fully implemented!
function setAudioByType(url,type) {
	if(debug) console.log("fetch audioMP3: "+url);
//	if(ascan == null) ascan = new audioscan();		// save recreation every time?
	
	var audioRequest = new XMLHttpRequest();
	audioRequest.open('GET', url, true); 				//	open the request...
	audioRequest.responseType = 'arraybuffer';			// binary response
    audioRequest.onreadystatechange = function () {
        if (audioRequest.readyState == 4) {
			if(audioRequest.status==200) {
				if(audioRequest.response.byteLength > 1) {
					if(type=="mp3") new audioscan().playMp3Chunk(audioRequest.response);
					else			new audioscan().playWavChunk(audioRequest.response);
				}
				var tstamp = audioRequest.getResponseHeader("time");
		    	if(tstamp != null) {
		    		var T = Math.floor(1000*parseFloat(tstamp));
		    		if((T > newestTime) || (url.indexOf("r=newest") != -1)) newestTime = T;
		    		if((T < oldestTime) || (url.indexOf("r=oldest") != -1)) oldestTime = T;
    		    	if(debug) console.debug('setAudioByType, header Time: '+T);
    	    		setTimeNoSlider(T);
		    	}

//				else console.debug('invalid pcm audio response buffer');
			}
			else {		// ERROR Handling
				inProgress=0;
				if(debug) console.warn('Error on audio MP3 fetch! '+url+', status: '+audioRequest.status);
				if(intervalID) goPause();
			}
        }
    }
	audioRequest.send();		// send the request
	inProgress++;
}

//----------------------------------------------------------------------------------------
//setParamValue:  set value on callback from Ajax 

function setParamValue(text, url, args) {
	var param = args[0];		// args passed thru == args of fetchData
	var pidx = args[1];
	var duration = args[2];
	var reqtime = args[3];	
	var refTime = args[4];
	var now=new Date().getTime();
	var time=now, value;
	var datavals = text.split("\n");
	datavals = datavals.filter(function(s) { return s.length > 0; } );
	var nval = datavals.length;		// presume last is blank?
	
//	console.debug("duration: "+duration+", getDuration(): "+getDuration());
	if(plots[pidx] && (plots[pidx].type == "stripchart")) {
//		var line = null;
//		if(duration >= getDuration()) {
//			line = plots[pidx].display.lines[param];		// for faster line-creation?  (can't see a difference)
//			line.clear();	// clear old data?
//		}
		if(duration >= getDuration() && top.rtflag==PAUSE) {
			if(debug) console.debug("setParamValue, clear line, top.rtflag: "+top.rtflag);
			plots[pidx].display.lines[param].clear();		// if full-refresh, clear old data
		}

		for(var i=0; i<nval; i++) {
			var timeval = datavals[i].split(",");
			ttime = 1000.*parseFloat(timeval[0]);					// sec -> msec (float)
			value = parseFloat(timeval[1]);

			if(isNaN(ttime)) continue;			// check for blank, bad time val
			else			 time = ttime;
			if(time < oldgotTime) oldgotTime=time;
			if(time > newgotTime) newgotTime=time;
//			if(line) plots[pidx].display.putValue(line,time,value,i);
//			else	 
				plots[pidx].addValue(param,time,value);
		}
	}
	else return;		// notta

	inProgress--;
	if(inProgress < 0) inProgress=0;		// failsafe
	
	if(singleStep && (param == plots[pidx].params[plots[pidx].params.length-1])) {			// last param this plot
		if(debug) console.debug('singleStep render, lastreqTime: '+lastreqTime+", tend: "+time);
		if(lastreqTime > 0) plots[pidx].render(lastreqTime);		// animation off, update incrementally
		else				plots[pidx].render(0);					// use last point got
	} 

//	if(inProgress == 0) document.body.style.cursor = 'default';

	if(nval > 0) {
		lastgotTime = time;
		if(refTime=="oldest") { setTime(time);	document.getElementById('TimeSelect').value=0; 	  oldestTime=oldgotTime; }	
		else if(refTime=="newest") { setTime(time);	document.getElementById('TimeSelect').value=100;  newestTime=newgotTime; }	
		else if(refTime=="next" || refTime=="prev") setTime(time);
		else if(pidx == 0 || plots[0].params.length==0) setTime(time);			// only setTime for plot0
//		setTime(time);		// always set it here?
		if(time > paramTime[param]) paramTime[param] = time;
	}
	else if(pidx == 0 || plots[0].params.length==0) setTime(reqtime);		// REQUEST time to move slider over gaps
	
	if(debug) console.log("setParamValue url: "+url+", nval: "+nval+", lastgotTime: "+lastgotTime);
}

//----------------------------------------------------------------------------------------
//setParamValue:  set value on callback from Ajax 

function setParamText(text, url, args, time) {
	var param = args[0];		// args passed thru == args of fetchData
	var pidx = args[1];
	var duration = args[2];
	var reqtime = args[3];	
	var refTime = args[4];
	
	inProgress--;
	if(inProgress < 0) inProgress=0;		// failsafe
//	if(inProgress == 0) document.body.style.cursor = 'default';

	if(text.length > 0) {
		plots[pidx].setText(text);
		lastmediaTime = time;			// text is considered media (fastRT fetch)
		if(refTime=="oldest") { setTime(time); document.getElementById('TimeSelect').value=0; 	  oldestTime=time; }	
		else if(refTime=="newest") { setTime(time); document.getElementById('TimeSelect').value=100;  newestTime=time; }	
		else if(refTime=="next" || refTime=="prev") setTime(time);
		else if(pidx == 0 || plots[0].params.length==0) setTime(reqtime);	// only setTime for plot0
		if(debug) console.debug('setParamText, url: '+url+', time: '+time+', reqtime: '+reqtime);
	}
	else if(pidx == 0 || plots[0].params.length==0) setTime(reqtime);		// REQUEST vs got time to force slider move
	
	else if(debug) console.log("setParamText NULL text, url: "+url);
}

//----------------------------------------------------------------------------------------
// binary array fetch (Audio)
// note that timestamp is either from header (CT) or must be deduced from reqtime (DT)

function setParamBinary(values, url, param, pidx, duration, reqtime, refTime) {
	var now=new Date().getTime();
	var nval = values.length;		// presume last is blank?
	var dt = duration / values.length;		// deduce timestamps
	var time=reqtime;
	
	inProgress--;
	
//	console.debug("duration: "+duration+", getDuration(): "+getDuration());
	if(plots[pidx] && (plots[pidx].type == "stripchart")) {
//		var line = plots[pidx].display.lines[param];		// for faster line-creation
//		line.clear();				// clear old data?
//		if(duration >= getDuration()) plots[pidx].display.lines[param].clear();		// if full-refresh, clear old data

		for(var i=0; i<nval; i++) {
			time = reqtime + i*dt;
			plots[pidx].addValue(param,time,values[i]);
//			plots[pidx].display.putValue(line,time,value[i],i);
		}
	}
	else 	return;		// notta

	if(debug) console.debug("setParamBinary, nval: "+nval+", over trange: "+duration+", dt: "+dt+', reqtime: '+reqtime+', refTime: '+refTime);
//	if(/* singleStep && */ (param == plots[pidx].params[plots[pidx].params.length-1])) {			// last param this plot
	if(singleStep && (param == plots[pidx].params[plots[pidx].params.length-1])) {			// last param this plot
		if(debug) console.debug('singleStep render(binary), lastreqTime: '+lastreqTime+", tend: "+time);
//		if(lastreqTime > 0) plots[pidx].render(lastreqTime);		// animation off, update incrementally
//		else				
			plots[pidx].render(0);				// use last point got
//			plots[pidx].render(reqtime);		// render at got-time
	} 

//	inProgress--;

	if(inProgress < 0) inProgress=0;		// failsafe
//	if(inProgress == 0) document.body.style.cursor = 'default';
	
//	if((nval > 0) && (refTime!="absolute")) {
//		setTimeNoSlider(time);
//		lastgotTime = time;
//	}
	
	if(nval > 0) {
		var plot0=0;
//		if(pidx==0 || plots[0].params.length==0 && refTime=="absolute") setTime(time);		// only set time for plot0

//		setTime(time);			// always set it here?
		lastgotTime = time;
		if(time > paramTime[param]) paramTime[param] = time;
		if(debug) console.debug("update lastgotTime: "+lastgotTime+", reqtime: "+reqtime+", duration: "+duration);
	}
//	else if(pidx==0 && refTime=="absolute") setTime(reqtime);	// REQUEST time to move slider

	if(debug) console.log("setParamBinary url: "+url+", nval: "+nval+", lastgotTime: "+lastgotTime);
}

//----------------------------------------------------------------------------------------
//rtCollection:  start real-time data collection

//async logic flow from here:
//rtCollection -> fetchData -> AjaxGet -> setParamValue

var playDelay=0;
var playStart=0;
function rtCollection(time) {
	stopRT();
	inProgress = 0;		// reset
	playStart = time;
	if(time != 0 && top.rtflag != RT) 
			playDelay = (new Date().getTime() - time);		// playback mode
//	else 	playDelay = 0.;
	else 	playDelay = (new Date().getTime() - newestTime);

	if(debug) console.debug('rtCollection, time: '+time+', playDelay: '+playDelay);
	
	// stripchart fetch data on interval
	var prevgotTime = 0;
	var prevnewestTime = newestTime;

	function doRT(dt) {
		if(debug) console.debug("doRT!");
		var anyplots=false;
		if(inProgress > 0) return;		// ease up?

		updatePauseDisplay(top.rtflag);
		var pDur = getDuration();		// msec

		var tright = playTime();						// right-edge time 
		var tleft = tright - pDur;						// left-edge time
		if(tleft > lastgotTime) tfetch = tleft;			// fetch from left-edge time
		else					tfetch = lastgotTime;	// unless already have some (gapless)		// this should be on per-param basis!!!!!
		var dfetch = 1.1*dt + (tright - tfetch);		// fetch enough to go past tright
		
		if(debug) console.debug('dfetch: '+dfetch+', pDur: '+pDur+", tfetch: "+tfetch+", tleft: "+tleft+", tright: "+tright+", lastgotTime: "+lastgotTime+", dt: "+dt);

		if(dfetch <= 0) return;		// nothing new
		
		if(singleStep) {										// delayed-start so as not to pre-scroll too much
			for(var j=0; j<plots.length; j++) plots[j].start();			
			singleStep = false;
		}
		
//		console.log("tfetch: "+tfetch+", newestTime: "+newestTime+", tright: "+tright+", top.rtflag: "+top.rtflag);
//		if((tfetch>=newestTime||tfetch<oldestTime) && (top.rtflag!=RT /* && rtmode==0 */)) {		// keep rolling if rtmode!=0
		if((tright>newestTime||tfetch<oldestTime) && (top.rtflag!=RT /* && rtmode==0 */)) {		// keep rolling if rtmode!=0
			if(debug) console.log('EOF, stopping monitor');
			clearInterval(intervalID);		// notta to do
			intervalID = 0;
			if(intervalID2==0) goPause();
			return;
		}
		
		for(var j=0; j<plots.length; j++) {
			plots[j].setDelay(playDelay);
						
			for(var i=0; i<plots[j].params.length; i++) {
				var param = plots[j].params[i];
				
				if(endsWith(param,".jpg") || endsWith(param,".txt")) continue;
//				if(plots[j].type != 'stripchart') continue;		// can have audio param on video plot
				anyplots=true;	
				
				if(debug) console.debug('playDelay: '+playDelay+', tright: '+tright+', newestTime: '+newestTime+', paramTime: '+paramTime[param]+', param: '+param);
				if(top.rtflag==RT) {
					if(!paramTime[param]) paramTime[param] = newestTime;		// failsafe
					if(tright > paramTime[param]) {			// try newest request if get ahead of newest
						AjaxGetParamTimeNewest(param);
						playDelay = new Date().getTime() - paramTime[param];	// increase playDelay if getting ahead
						if(debug) console.debug('New PLAYDELAY: '+playDelay);
					}
				} 
//				else { 
//					console.debug('fetch abs, tfetch: '+tfetch+', dfetch: '+dfetch);
					fetchData(plots[j].params[i], j, dfetch, tfetch, "absolute");		// fetch latest data (async) 
//				}
			}
		}
		
		if(debug) console.debug("anyplots: "+anyplots+", tfetch: "+tfetch+", newestTime: "+newestTime+", top.rtflag: "+top.rtflag+', intervalID2: '+intervalID2);
		if(!anyplots) {
			if(debug) console.log('no stripcharts, stopping monitor');
			clearInterval(intervalID);		// notta to do
			intervalID = 0;
			if(intervalID2==0) goPause();
			return;
		}	
		prevgotTime = lastgotTime;		// for newest/absolute mode request check
	}

	mDur = getDuration();
	singleStep = true;									// initiate scrolling mode
	var dt = tDelay;
	if(dt > mDur) dt = mDur;			// refresh at least once per screen
	if(dt <= 100) dt = 100; 
	intervalID2 = 1;					// so intervalID doesn't pause video before can check		
//	for(var j=0; j<plots.length; j++) plots[j].start();			

	doRT(dt);		// startup faster without delay
	intervalID = setInterval(function() {doRT(dt);}, dt);
	
	// ------------------------------ faster video updates:
	// video RT
	var prevmediaTime = 0;
	var slowdownCount = 0;
	var fastDelay=tDelay/10;
//	playDelay = 0;
	function doRTfast() {
		if(intervalID2==0) return;		// fail-safe
		
		var ptime = playTime(); // + getDuration()/2;	// video at mid-duration of plot (better audio sync)	
//		if(ptime <= lastvideoTime) return;			// hasn't progressed enough to matter
		anyvideo = false;
		for(var j=0; j<plots.length; j++) {
			for(var i=0; i<plots[j].params.length; i++) {
				var param = plots[j].params[i];
//				var param = plots[j].params[0];
				if(!param) continue;
//				if(plots[j].type == 'stripchart') continue;		// stripcharts go 1/10 nominal rate
				if(!endsWith(param,".jpg") && !endsWith(param,".txt")) continue;	// can have mixed .jpg & .wav params!

				anyvideo = true;
				if(debug) console.debug("video fetch ptime: "+ptime+", lastvideoTime: "+lastmediaTime);
//				if(top.rtflag==RT && ptime > newestTime) {						// try newest request if get ahead of newest
				if(top.rtflag==RT && ptime > paramTime[param]) {					// try newest request if get ahead of newest

					if(debug) console.debug('RT fetch newest, slowdownCount: '+slowdownCount+', ptime: '+ptime+', newestTime: '+newestTime);
//					fetchData(plots[j].params[0], j, 0, 0, "newest");			// RT newest mode (overlap/inefficient)	
					AjaxGetParamTimeNewest(param);			// just get newest time without displaying data

					// increase playDelay if getting ahead
					playDelay = new Date().getTime() - paramTime[param];
//					if(playDelay > 10*tDelay) playDelay = 0;			// if too much delay, refetch("newest")
					if(debug) console.debug('New PLAYDELAY: '+playDelay);
				} else {
					if(debug) console.debug('RT fetch absolute, param: '+param+', slowdownCount: '+slowdownCount+', ptime: '+ptime+', paramTime: '+paramTime[param]);
					if(endsWith(param,'.txt'))	fetchData(param, j, 0, 0, "newest");			// text:  always get newest
					else						fetchData(param, j, 0, ptime, "absolute");		// RT->playback 
				}
			}
		}
		
		if(!anyvideo || (ptime>=newestTime && (top.rtflag!=RT))) {	// keep rolling if RT
			if(debug) console.log('no video, stopping monitor');
			clearTimeout(intervalID2);		// notta to do
			intervalID2 = 0;
			if(intervalID==0) goPause();
		}
		else {
			// warning:  a successful fetch above may happen async such that a long wait below happens after first wake-up
			if(lastmediaTime > prevmediaTime) {
				prevmediaTime = lastmediaTime;
				slowdownCount=0;
				intervalID2 = setTimeout(doRTfast,fastDelay);
			} else {
				slowdownCount++;				// ease up if not getting data
				if(debug) console.debug('slowdownCount: '+slowdownCount+', lastmediaTime: '+lastmediaTime+', prevmediaTime: '+prevmediaTime);

				if(slowdownCount < 100) 		intervalID2 = setTimeout(doRTfast,fastDelay=tDelay/10);	// <10s, keep going fast
				else if(slowdownCount < 150)	intervalID2 = setTimeout(doRTfast,fastDelay=tDelay);	// 10s to 1min
				else if(slowdownCount < 740)	intervalID2 = setTimeout(doRTfast,fastDelay=tDelay*2);	// 1min to ~10min
				else if(slowdownCount < 4000)	intervalID2 = setTimeout(doRTfast,fastDelay=tDelay*5);	// 10 min to ~2 hours
				else {
					intervalID2 = 0;
					if(intervalID==0) goPause();	// stop if long-time no data
				}
			}
		}
	}
	doRTfast();
}	

//----------------------------------------------------------------------------------------

var sumLag=0;			// sum/avg lag to set playDelay
var icount=0;
var NCOUNT=20;			// number of updates over which to average sumLag

function playTime() {		// time at which to fetch (msec)
	var now = new Date().getTime();
	
//	if(top.rtflag != RT || rtmode==1) {		// playback mode:  let system-clock drive pace relative to fixed offset
		var ptime = now - playDelay;					// playFwd
//		if(rtmode==1) ptime -= getDuration();		// FOO debug
		if(debug) 
			console.debug('playTime, now: '+now+', playDelay: '+playDelay+', playTime: '+ptime+", newestTime: "+newestTime);
		return ptime;
//	} else {
/*		
		if(debug) 
			console.debug('RT playTime, now: '+now+', playDelay: '+playDelay+', playTime: '+(now-playDelay)+", newestTime: "+newestTime);

		var ncount = NCOUNT;
		if(playDelay == 0) { 			// re-init, playDelay reset at each RT start
			updateNewest();				// this important for DT/WebTurbine that doesn't send newest in HTTP headers
			sumLag=0; icount=0; ncount=1; 
		}	
		
		// find the oldest-newest time of all active plots (laggiest display channel)
		var latestTime = oldestNewest();
		// following finds the oldest-newest only comparing video to non-video (vs all chans)
//		var latestTime = newestTime;
		
//		if(intervalID) latestTime = lastgotTime;
//		if(intervalID2 && lastvideoTime < lastgotTime) latestTime = lastvideoTime;
		var lag = now - latestTime;				// coarse estimate lag
//		if(lag < 0) lag = 0;					// beware inconsistent clocks, now can be less than source data mod time
		sumLag += lag;
		
		icount++;
		if((icount%ncount)==0) {
			var avgLag = 0 + sumLag/ncount;
			if(Math.abs(avgLag) > 100*getDuration()) avgLag = 0;						// way off (at startup?)
//			if(Math.abs(playDelay - avgLag) > 2*getDuration()) 	playDelay = 100;	// way off; jump adjust (was = avgLag)
//			else	
			if(playDelay > avgLag) 	playDelay = 0.7*playDelay + 0.3*avgLag;			// decrease slowly
			else					playDelay = 0.3*playDelay + 0.7*avgLag;			// increase faster
			playDelay = playDelay * 0.9;					// keep it from drifting higher?
//			playDelay += getDuration()/2;					// skootch half a screen older data?
			
			if(debug) console.log("------------update playDelay: "+playDelay);
			if(!playDelay) playDelay = 0;
			sumLag = 0;
		}

		if(debug) console.debug("latestTime: "+latestTime+", now: "+now+", lag: "+lag+", playDelay: "+playDelay);

		return (now - playDelay);						// play RT driven forward by now-clock less some lag
	}
*/
}

var oldDuration = 0;
function playDuration(playtime) {							// duration to fetch (msec)
//	var pdur = playTime() - getTime();				// get everything from from last pos to playtime
//	var playtime = playTime();
	var pdur = playtime - lastgotTime;			// get everything from from last pos to playtime
													// ISSUE:  this presumes all (stripchart) chans at same lastgotTime
													// consider book-keeping this on per channel basis?

	var mdur = 2*getDuration();		// msec
	if(pdur > mdur) {
		if(debug) console.debug("playDuration limiting pdur: "+pdur+", to mdur: "+mdur);
		pdur = mdur;					// limit to one screen worth
	}
	
	if(pdur < 0) pdur = mdur;					// initialization glitch?
	if(debug) console.log('pdur: '+pdur+", playTime: "+playtime+", lastgotTime: "+lastgotTime+", mdur: "+mdur+", playDelay: "+playDelay);
	return pdur;
}

// set playDelay (lag from RT) 
function adjustPlayDelay(inc) {
	console.debug("increasing playDelay: "+playDelay+" -> "+(playDelay+inc));
	playDelay += inc;				
}

function oldestNewest() {
//	updateNewest();					// update every time? (shouldn't help with CTserver - updates newest every header)
	var oldnew = 0;
	for(var j=0; j<plots.length; j++) {
		for(var i=0; i<plots[j].params.length; i++) {
			var pname = plots[j].params[i];
			var t = paramTime[pname];
			if(oldnew == 0) 	oldnew = t;
			else if(t < oldnew) oldnew = t;
			if(debug) console.debug("plot: "+j+", pname: "+ pname+", t: "+t+", oldnew: "+oldnew);
		}
	}
	return oldnew;
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
//stepCollection:  step next/prev data (images only)

function stepCollection(iplot, time, refdir) {
	// find and step image with oldest time
	var idx= 0;
	var param=plots[iplot].params[idx];
	
	if(refdir == "next") {				// find oldest paramTime
		var otime=99999999999999;
		for(var j=0; j<plots.length; j++) {	
			for(var i=0; i<plots[j].params.length; i++) {
				var pname = plots[j].params[i];
				var t = paramTime[pname];
				if(t < 0) continue;		// out of action
				if(endsWith(pname, ".jpg") && t<otime) {
					idx = i;
					iplot = j;
					time = otime = t;
					param =  pname;
				}
			}
		}	
	}
	else {								// find newest paramTime
		var ntime=0;
		for(var j=0; j<plots.length; j++) {	
			for(var i=0; i<plots[j].params.length; i++) {
				var pname = plots[j].params[i];
				var t = paramTime[pname];
				if(t < 0) continue;		// out of action
				if(endsWith(pname, ".jpg") && t>ntime) {
					idx = i;
					iplot = j;
					time = ntime = t;
					param =  pname;
				}
			}
		}
	}
	
	var url = serverAddr + servletRoot+"/"+escape(plots[iplot].params[idx])+"?dt=b&t="+(time/1000.)+"&r="+refdir;
//	console.debug('stepCollection, url: '+url+', idx: '+idx+', iplot: '+iplot);
	plots[iplot].display.setImage(url,param,0);
	
//	refreshCollection(true, getTime(), getDuration(), refdir);	
}

//----------------------------------------------------------------------------------------
//refreshCollection:  refresh data, single step or continuous

function refreshCollection(onestep, time, fetchdur, reftime) {
//	onestep=false for refilling plot and continuing with RT data 
	refreshInProgress=true;
	if(debug) {
		console.log('refreshCollection: time: '+time+', reftime: '+reftime+', fetchdur: '+fetchdur+", onestep: "+onestep);
		console.trace();
	}

//	if(reftime=="absolute") time = getTime() - getDuration();		// adjust RE time to LE time
	if(stepDir != -2) setPlay(PAUSE,0);								// pause RT
	refreshCollection2(100, onestep, time, fetchdur, reftime);		// fetch & restart after pause complete
}

function refreshCollection2(maxwait, onestep, time, fetchdur, reftime) {
	refreshInProgress=true;
//	if(inProgress && maxwait>=0) { 		// wait til paused
	if(inProgress) { 		// wait til paused
		setTimeout(function(){refreshCollection2(--maxwait, onestep, time, fetchdur, reftime);}, 100); 
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
//		lastreqTime = time + fetchdur;		// time=left-edge, lastreqTime=right-edge time
		lastreqTime = time;					// time=left-edge, lastreqTime=right-edge time

		if(debug) console.debug('get time: '+time+', oldestTime: '+oldestTime+', now: '+now+', lastreqTime: '+(lastreqTime)+', fetchdur: '+fetchdur+", reftime: "+reftime);
		if(lastreqTime > now) 		{ time = 0; reftime="newest"; lastreqTime=0; }
		else if(time < oldestTime) 	{ time = 0; reftime="oldest"; lastreqTime=0; }
		
		if(debug) console.debug('>>> time: '+time+', oldestTime: '+oldestTime+', now: '+now+', lastreqTime: '+(lastreqTime)+', fetchdur: '+fetchdur+", reftime: "+reftime);
	}

	for(var j=0; j<plots.length; j++) {				// get data once each plot
		plots[j].dropdata();
		for(var i=0; i<plots[j].params.length; i++) {
//			console.debug('refresh fetch time: '+time+", dur: "+fetchdur);
			var isImage = endsWith(plots[j].params[i], ".jpg")
			if(isImage) fetchData(plots[j].params[i], j, fetchdur, time, reftime);			// fetch new data (async)
			else		fetchData(plots[j].params[i], j, fetchdur, time-fetchdur, reftime);	
		}	
	}	

	refreshCollection3(100,onestep,time,fetchdur,reftime);		// queue restart
}

function refreshCollection3(maxwait, onestep, time, fetchdur, reftime) {
	if(inProgress) { 	// wait til done
		setTimeout(function(){refreshCollection3(--maxwait, onestep, time, fetchdur, reftime);}, 100); 
		return; 
	}	
	if(debug) console.log('refreshCollection3: reftime: '+reftime+", onestep: "+onestep);

	if(!resetMode) {
		if(onestep) {
			for(var j=0; j<plots.length; j++) {
				if(debug) console.debug('refresh render: '+lastreqTime);
				plots[j].render(lastreqTime);	// see the data?
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

//	document.body.style.cursor = 'default';
	refreshInProgress=false;
	resetMode=false;
}

//----------------------------------------------------------------------------------------
//AjaxGet:  Ajax request helper func

function AjaxGet(myfunc, url, args) {
	if(debug) {			// foo debug 
		console.log('AjaxGet: '+url);
		console.trace();
	}

	var param = args[0];
	var pidx = args[1];
	var duration = args[2];
	var time = args[3];

	var xmlhttp=new XMLHttpRequest();

	xmlhttp.onreadystatechange=function() {
    	fetchActive(false);

		if (xmlhttp.readyState==4) {
			if(xmlhttp.status==200) {
				if(debug) console.log("xmlhttp got: "+url);
				var holdest = xmlhttp.getResponseHeader("oldest");		
				if(holdest != null) oldestTime = 1000 * Number(holdest);	// just set it, worry about multi-chan consistency later...

				var hnewest = xmlhttp.getResponseHeader("newest");		
				if(hnewest != null) {
					newestTime = 1000 * Number(hnewest);
//					if(top.rtflag == RT) 
						paramTime[param] = newestTime;		// for oldest-of-newest RT check
				}
				if(paramTime[param]==null) paramTime[param]= time+duration;
				
				var htime = 1000 * Number(xmlhttp.getResponseHeader("time"));
//				console.debug('url: '+url+', header time: '+htime+', argtime: '+time+', oldestTime: '+oldestTime+', newestTime: '+newestTime);
				myfunc(xmlhttp.responseText, url, args, htime);	
			}
			else {		// ERROR Handling
				if(debug) console.warn('Error on data fetch! '+url+', status: '+xmlhttp.status+", rtflag: "+top.rtflag);

				inProgress=0;		// no deadlock
//				document.body.style.cursor = 'default';		// done with this fetch
//				console.log('Error: '+url);
//				if(top.rtflag!=RT)	goPause();			// stop if playback mode?
				if(intervalID) { 	//  no warn on shutdown
					//console.log('stopping??? on xmlhttp.status: '+xmlhttp.status+", time: "+time+", newestTime: "+newestTime+", t>n: "+((time+duration)>=newestTime));
					if(top.rtflag==RT) return;
					else if(((time+duration) >= newestTime) || (xmlhttp.status != 410 && xmlhttp.status != 404)) {				// keep going (over gaps)
						if(debug) console.log('stopping on xmlhttp.status: '+xmlhttp.status+", time: "+time+", newestTime: "+newestTime);
						goPause();	
					}
					setTime(time);				// move slider thru gaps
				}
			}
		}
	};
	xmlhttp.open("GET",url,true);
	xmlhttp.onerror = function() { goPause(); alert('xmlhttp error'); };
	fetchActive(true);
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
//		opt.replace("//","/");				// collapse any double slash to single ('//' -> '/)
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

//	if(top.rtflag==PAUSE && oldestTime > 0) {
//	console.log('durationSelect, oldestTime: '+oldestTime);
	if(top.rtflag==PAUSE && !isImage && oldestTime>0) {
		rePlay();
//		refreshCollection(true,getTime(),getDuration(),"absolute");	// auto-refill plots to full duration??
	}
	
	top.plotDuration = secondsPerPlot;		// global for eavesdroppers
	setConfig('v',secondsPerPlot);
}

function getDuration() {
	var duration = document.getElementById("myDuration");
	return 	1000.*duration.options[duration.selectedIndex].value;		// msec
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
//updateScaling:  handle update-scaling select

function scalingSelect(cb) {
	scalingMode = cb.options[cb.selectedIndex].value;
	if(scalingMode == "Tight") 			setConfig('sc','t');
	else if(scalingMode == "Manual") 	setConfig('sc','m');
	else if(scalingMode == "Auto") 		setConfig('sc','a');
	else								setConfig('sc','s');
	rebuildPage();
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
		refreshCollection(false,0,getDuration(),"newest");		// update plots
	}
}

//----------------------------------------------------------------------------------------	
//setPlay:  0=pause, 1=RT, 2=playback

function setPlay(mode, time) {
	top.rtflag = mode;				
	setSingleStep();
	if(debug) console.debug('setPlay: mode: '+mode+', time: '+time+', singleStep: '+singleStep);
	if(mode==PAUSE) {				// stop RT
		stopRT();
		inProgress=0;			// make sure not spinning
//		document.body.style.cursor = 'default';		
		document.getElementById('play').innerHTML = playStr;
		setDivSize();			// resize divs on pause
	}
	else {
		if(debug) console.debug('starting plots, singlestep: '+singleStep);
		if(!singleStep)	// no restart animation if singlestep mode
			for(var i=0; i<plots.length; i++) plots[i].start();
		rtCollection(time);
		document.getElementById('play').innerHTML = '||';
	}

	updatePauseDisplay(mode);
}

function getPlayMode() {
	return top.rtflag;
}

function updatePauseDisplay(mode) {
	fetchActive(false);

	if(stepDir == -2) 		document.getElementById('<').checked=true;
	else if(mode==PAUSE){
		document.getElementById('play').innerHTML = playStr;
		document.getElementById('play').checked = true;
		if(document.getElementById('RT')) document.getElementById('RT').checked=false;
	}
	else if(mode==RT && rtmode==0) 	{
		if(document.getElementById('RT')) document.getElementById('RT').checked=true;
		document.getElementById('play').checked = false;
	}
	else if(mode==PLAY) {
		document.getElementById('play').innerHTML="||";
		document.getElementById('play').checked = true;
		if(document.getElementById('RT')) document.getElementById('RT').checked=false;
	}
}

//----------------------------------------------------------------------------------------	
//rePlay:  re-start at current time & mode (pause/play/RT)

function rePlay() {
	var mode = PAUSE;
	if(rtmode==0 && document.getElementById('RT') && document.getElementById('RT').checked) mode = RT;
	else if(document.getElementById('play').innerHTML=="||") mode = PLAY;

	if(mode==PAUSE && !isImage && oldestTime > 0) {
		refreshCollection(true,getTime(),getDuration(),"absolute");	// auto-refill plots to full duration??
	}
	else if(mode==PLAY) {
		playFwd();
	}
	else if(mode==RT) {
		refreshCollection(false,0,getDuration(),"newest");	// this auto-fills now			
	}
}

//----------------------------------------------------------------------------------------	
//stopRT:  clear RT timer

function stopRT() {
	if(debug) console.log("stopRT.");
	if(intervalID != 0) clearInterval(intervalID);
//	if(intervalID2 != 0) clearInterval(intervalID2);
	if(intervalID2 != 0) clearTimeout(intervalID2);
	intervalID = intervalID2 = 0;
	if(intervalID3 != 0) clearTimeout(intervalID3);		// turn off playRvs timer (only) here
	intervalID3=0;
	for(var i=0; i<plots.length; i++) plots[i].stop(); 
	document.getElementById('play').innerHTML = playStr;
	singleStep = true;		// mjm 7/30/15
}

//----------------------------------------------------------------------------------------	
//setSingleStep:  set flag if small incremental view update (more efficient)

function setSingleStep() {
	if(stepDir< 0) 	singleStep = true;
	else			singleStep = false; // default
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
	return 	document.getElementById('play').innerHTML==playStr;
}

//----------------------------------------------------------------------------------------	
//isRT:  true/false if in pause state

function isRT() {
	return !document.getElementById('play').innerHTML==playStr;
}

//----------------------------------------------------------------------------------------	
//smoothCheck:  set/unset smooth option

function smoothCheck(cb) {
	if(cb) doSmooth = cb.checked;
	for(var i=0; i<plots.length; i++) { 
		plots[i].setSmooth(doSmooth); plots[i].render(lastreqTime); 
	}
	setConfig('sm',doSmooth);
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
// show status of data-fetch

function fetchActive(status) {
//	console.debug('fetchActive: '+status);
	if(status) 	{
		document.getElementById("timestamp").style.color = "red";
		document.body.style.cursor = 'wait';
	} else {
		document.body.style.cursor = 'default';
		document.getElementById("timestamp").style.color = "white";
	}
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
	stopRT();		// ??

	setTimeout(function(){ rebuildPage2(20); }, 1000);					// finish rebuild with wait for buildCharts()
}

function rebuildPage2(maxWait) {
	if((inProgress || refreshInProgress) && maxWait>0) { 						// wait til prior update done
//		console.debug('inProgress: '+inProgress+', refreshInProgress: '+refreshInProgress);
		setTimeout(function(){rebuildPage2(--maxWait);}, 100); 
		return; 
	}
	
	refreshCollection(true,getTime(),getDuration(),"absolute");					// auto-refill plots to full duration
	if(!isPause()) 	goRT();


}

//----------------------------------------------------------------------------------------
//setTime:  update screen display of current time position

function setTimeNoSlider(time) {
	if(time == 0 || isNaN(time)) return;		// uninitialized
//	updateTimeLimits(time);		// not always accurate limits
	
	var month = new Array();
	month[0] = "Jan";	month[1] = "Feb";	month[2] = "Mar";	month[3] = "Apr";	month[4] = "May";	month[5] = "Jun";
	month[6] = "Jul";	month[7] = "Aug";	month[8] = "Sep";	month[9] = "Oct";	month[10] = "Nov";	month[11] = "Dec";
	
	d = new Date(time);		// msec
//	var dstring = d.toUTCString();
//	dstring = dstring.split(", ")[1];		// string leading "Day, "

//	var dstring = ("0"+(d.getDate()+1)).slice(-2) + " " + month[d.getMonth()] + " " + 
	var dstring = ("0"+d.getDate()).slice(-2) + " " + month[d.getMonth()] + " " + 
    d.getFullYear() + " " + ("0" + d.getHours()).slice(-2) + ":" + ("0" + d.getMinutes()).slice(-2) + ":" + ("0" + d.getSeconds()).slice(-2);
	
	var cb = document.getElementById('myDuration');
	var durString = cb.options[cb.selectedIndex].text;

	document.getElementById("timestamp").innerHTML = dstring + ' (' + durString + ')';
	top.plotTime = time / 1000.;		// global, units=sec
	
//	if(debug) console.debug('setTimeNoSlider: '+time);
}

function setTime(time) {
	if(debug) console.debug("setTime: "+time);
		
	if(time == 0 || isNaN(time)) return;		// uninitialized
	setTimeNoSlider(time);
	
	// set time slider
//	if(rtflag!=PAUSE) 				// no fight over timeslider
	setTimeSlider(time);		
}

function setTimeSlider(time) {
	if(debug) console.debug('setTimeSlider, time: '+time+', oldest: '+oldestTime+', newest: '+newestTime);
	
	var el = document.getElementById('TimeSelect');
	if(newestTime == 0 || oldestTime == 0) {   			// failsafe	
		if(debug) console.log("WARNING:  setTimeSlider without limits, newestTime: "+newestTime+", oldestTime: "+oldestTime);
//		getLimits(true,true);			// (can be infinite loop?, as getLimits calls setTimeSlider!)
		el.value = 0;
		return;
	}
	var mDur = 0.;
	if(!isImage) mDur = getDuration();		// duration msec	// try without duration adjust 7/2/15
	if(mDur > (newestTime-oldestTime)) mDur = newestTime-oldestTime;
	var percent = 100. * (time - oldestTime - mDur) / (newestTime - oldestTime - mDur);
	if(debug) console.debug('setTimeSlider, time: '+time+", percent: "+percent+', oldestTime: '+oldestTime+', newestTime: '+newestTime+', mDur: '+mDur);
	el.value = percent;
}

function getTime() {
//	console.debug('getTime: '+top.plotTime);
	return top.plotTime * 1000.;		//  msec
}

var lastSet=0;
function timeSelect(el) {
//	console.debug("timeSelect, inProgress: "+inProgress);
	if(inProgress) return;
	var now = new Date().getTime();
	if((now - lastSet) < 100) return;			// ease up if busy
	lastSet = now;
	goTime(el.value);
//	console.debug("timeSelect: "+value);
}

function updateTimeLimits(time) {
//	if(debug) 
		console.log("updateTimeLimits: "+time);
	if(time <= 0) return;
	if(time > newestTime) newestTime = time;
	if(time < oldestTime) oldestTime = time;
}

var resetMode=false;
function resetLimits(pplot) {
//	resetMode=true;
//	goBOF();
//	goEOF();
//	oldestTime = new Date().getTime();  newestTime=0;		// force reset
//	fetchData(plots[pplot].params[0], pplot, 0., 0., "newest");	
//	fetchData(plots[pplot].params[0], pplot, 0., 0., "oldest");	
	for(var i=0; i<plots[pplot].params.length; i++) {
		AjaxGetParamTimeNewest(plots[pplot].params[i]);
		AjaxGetParamTimeOldest(plots[pplot].params[i]);

	}

//	document.getElementById('TimeSelect').value = 100;		// peg slider
}

// get data at limits (new, old)
// should have a time-only fetch version (f=t)
function getLimits(forceFlagOld, forceFlagNew) {
	if(nplot<=0 || !plots[0] || !plots[0].params || plots[0].params.length<=0) return;		// notta
//	var otime = getTime();
	
	var iplot=0;		// find first plot with channel
	for(iplot=0; iplot<nplot; iplot++) if(plots[iplot] && plots[iplot].params.length > 0) break;
	if(debug) console.debug('!!!!getLimits, newestTime: '+newestTime+", oldestTime: "+oldestTime+", limitParam: "+plots[iplot].params[0]+", forceOld: "+forceFlagOld+', forceNew+', forceFlagNew);

	if(newestTime == 0 || newestTime < oldestTime || forceFlagNew) {
//		fetchData(plots[iplot].params[0], 0, 0, 0, "newest");
		updateNewest();
	}
	
//	var status = getLimits2(plots[iplot].params[0], forceFlagNew);
	if(oldestTime == 0 || oldestTime > newestTime || forceFlagOld) {
//		fetchData(plots[iplot].params[0], 0, 0, 0, "oldest");
		updateOldest();
	}

	
//	if(debug) 
//		console.debug("getLimits out, oldestTime: "+oldestTime+", newestTime: "+newestTime);
//	if(!forceFlagOld && !forceFlagNew) setTime(otime);
	
	return(status);
}

// get newest all plot params
function updateNewest() {
//	console.debug("updateNewest!");
	newestTime = 0;		// force update
	for(var j=0; j<plots.length; j++) {
		for(var i=0; i<plots[j].params.length; i++) {
			AjaxGetParamTimeNewest(plots[j].params[i]);
		}
	}
}

//get oldest all plot params
function updateOldest() {
//	console.debug("updateOldest!");
	oldestTime = new Date().getTime();		// force update
	for(var j=0; j<plots.length; j++) {
		for(var i=0; i<plots[j].params.length; i++) {
			AjaxGetParamTimeOldest(plots[j].params[i]);
		}
	}
}

function AjaxGetParamTimeNewest(param) {	
	var xmlhttp=new XMLHttpRequest();
	var munge = "?dt=s&f=t&r=newest&d=0";
	var url = serverAddr + servletRoot+"/"+escape(param)+munge;
	
	xmlhttp.onreadystatechange=function() {
		if (xmlhttp.readyState==4) {
	    	fetchActive(false);

			if(xmlhttp.status==200) {
				var ptime = 1000* Number(xmlhttp.responseText);		// msec
				paramTime[param] = ptime;
				if(ptime > newestTime) newestTime = ptime;
				if(debug) console.debug("AjaxGetParamTime, param: "+param+", time: "+paramTime[param]);
			}
			else {  				
	    		paramTime[param] = 0;		// out of action
				console.log('AjaxGetParamTime Error: '+url);
			}
		}
	};
//	console.debug("AjaxGetParamTime: "+url);
	xmlhttp.open("GET",url,true);				// arg3=false for synchronous request
	fetchActive(true);
	xmlhttp.send();
}

function AjaxGetParamTimeOldest(param) {	
	var xmlhttp=new XMLHttpRequest();
	var munge = "?dt=s&f=t&r=oldest&d=0";
	var url = serverAddr + servletRoot+"/"+escape(param)+munge;
	
	xmlhttp.onreadystatechange=function() {
		if (xmlhttp.readyState==4) {
	    	fetchActive(false);

			if(xmlhttp.status==200) {
				var ptime = 1000* Number(xmlhttp.responseText);		// msec
				paramTime[param] = ptime;
				if(ptime < oldestTime) oldestTime = ptime;
				if(debug) console.debug("AjaxGetParamTimeOldest, param: "+param+", ptime: "+ptime+', oldestTime: '+oldestTime);
			}
			else {  				
	    		paramTime[param] = 0;		// out of action
				console.log('AjaxGetParamTime Error: '+url);
			}
		}
	};
//	console.debug("AjaxGetParamTime: "+url);
	xmlhttp.open("GET",url,true);				// arg3=false for synchronous request
	fetchActive(true);
	xmlhttp.send();
}

function getLimits2(param, forceFlagOld) {
	if(newestTime == 0) { 		// wait til paused
		if(debug) console.debug('waiting for newestTime...');
		setTimeout(function(){getLimits2(param, forceFlagOld);}, 100); 
		return; 
	}
	if(debug) console.debug('getLimits2 got newestTime: '+newestTime);
	
	if(oldestTime == 0 || oldestTime > newestTime || forceFlagOld) {
		fetchData(param, 0, 0, 0, "oldest");
	}
	
	return getLimits3();
}

function getLimits3() {
	if(oldestTime == 0) { 		// wait til paused
		if(debug) console.debug('waiting for oldestTime...');
		setTimeout(function(){getLimits3();}, 100); 
		return; 
	}
//	if(debug) console.debug('getLimits3 got oldestTime: '+oldestTime);
	return true;
}


//----------------------------------------------------------------------------------------
//buildCharts:  build canvas and smoothie charts

function buildCharts() {
	refreshInProgress = true;
	if(debug) console.log('buildCharts: '+plots.length);
//	newestTime = oldestTime = 0;			// force time limit reset
	
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
		if(window.innerWidth > window.innerHeight) {		// wider than tall
			switch(plots.length) {	
			case 2:		case 4:		case 6:		case 8:		case 10:	case 14:	ncol = 2;	break;
			case 9:		case 12:	case 15:	case 18:					ncol = 3;	break;
			case 16:	case 20:	ncol = 4;	break;
			default:											ncol = 1;	break;
			}
		}
		else {
			switch(plots.length) {				// taller than wide
			case 4:		case 6:		case 8:		case 10:	case 14:	ncol = 2;	break;
			case 9:		case 12:	case 15:	case 18:			ncol = 3;	break;
			case 16:	case 20:							ncol = 4;	break;
			default:									ncol = 1;	break;
			}
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

			// parent div to hold chanbox, chanlist, clearbox
			var pdiv = document.createElement('div');
			pdiv.id = "phead";
			pcell0.appendChild(pdiv);

			// + addchan button
			addChanBox(iplot, pdiv);					

			// child div to hold chanlist
			var cdiv = document.createElement('div');
			cdiv.style.float="left";
			cdiv.style.width="1px";		// nominal, will overflow
			pdiv.appendChild(cdiv);

			//  create label for each param
			for(var j=0; j<plots[iplot].params.length; j++) {
				nparam++;						
				// create label element above plot
				var node = document.createElement('label');
				node.style.whiteSpace="nowrap";
				var param = plots[iplot].params[j];
				if(plots[iplot].params.length > 1) param = param.split("/").pop(); 	// truncate to just param name if multiple
				node.innerHTML = param;
				node.id = 'label'+j;
				node.style.color = plots[iplot].color(j);
				node.style.padding = '0 4px';
				cdiv.appendChild(node);	
				setConfig('p'+iplot+''+j,plots[iplot].params[j]);
			}

			// x clearPlot button
			if(plots[iplot].params.length > 0) {		// only if any curves to clear
				addClearBox('clear'+iplot, clearPlotSelect, 'x', pdiv);
			}

			// create a canvas for each plot box
			prow = plotTable.insertRow(1);
			var pcell1 = prow.insertCell(0); 
//			var pdiv1 = document.createElement('div');
//			pcell1.appendChild(pdiv1);					// ??
			
			var canvas = new Array();			// MJM 10/12/16
//			maxLayer = 1;		// foo debug
			for(i=0; i<maxLayer; i++) {
				canvas.push(document.createElement('canvas'));
//				var canvas = document.createElement('canvas');
				canvas[i].innerHTML = emsg; 
				canvas[i].id = 'plot'+iplot; 
				canvas[i].setAttribute("class", "canvas");

				canvas[i].width = Wg/ncol-15; 			// width used in setting chart duration

				Hg = (graphs.clientHeight / nrow) - pcell0.offsetHeight - 20;
				canvas[i].height = Hg;		// ensure same for all

				canvas[i].align="center";
				if(i>0) {
					canvas[i].style.position="absolute";
					canvas[i].style.top=pdiv.height;
					canvas[i].style.left=0;
				}
//				if(i==(maxLayer-1)) addListeners(canvas[i]);		// add mouse click event listeners
				canvas[i].style.zIndex = -1;				// no mouse click on layers
//				pdiv1.appendChild(canvas[i]);
				pcell1.appendChild(canvas[i]);
			}
			addListeners(pcell1);							// add listener to cell (vs canvas layer)
			// associate smoothie chart with canvas
			plots[iplot].addCanvas(canvas);

//			vidscan(canvas, 'NASATV/image.jpg');		// foo debug
		}
	}


	buildChanLists();		// re-initialize (overkill?)
	updateSelect();			// update-interval selection menu
	
	top.rtflag=PAUSE;		// auto-pause (less squirmy?)
	durationSelect(document.getElementById("myDuration"));	    // plot duration selection menu		(NEED?)

	getLimits(1,1);			// re-update limits?
	
//	inProgress=0;			// failsafe 
//	setPause(false,0);		// auto pause (less perplexing) 
	refreshInProgress = false;

	setDivSize();
}	

//----------------------------------------------------------------------------------------
//drag-plot utilities

//figure out if mouse or touch events supported
isTouchSupported = 		'ontouchstart' in window 			// works on most browsers 
					|| 	'onmsgesturechange' in window;		// IE10

isPointerEnabled = window.navigator.msPointerEnabled || window.MSPointerEvent || window.PointerEvent;
if(isPointerEnabled) isTouchSupported = false;				// IE10 pointer/gesture events not yet supported

//isTouchSupported = false;			// for debugging 
var startEvent = isTouchSupported ? (isPointerEnabled ? (window.PointerEvent ? 'pointerdown' : 'MSPointerDown') : 'touchstart') : 'mousedown';
var moveEvent = isTouchSupported ?  (isPointerEnabled ? (window.PointerEvent ? 'pointermove' : 'MSPointerMove') : 'touchmove')  : 'mousemove';
var endEvent = isTouchSupported ?   (isPointerEnabled ? (window.PointerEvent ? 'pointerup'   : 'MSPointerUp')   : 'touchend')   : 'mouseup';
var outEvent = isTouchSupported ?   (isPointerEnabled ? (window.PointerEvent ? 'pointerdown' : 'MSPointerOut')  : 'touchcancel'): 'mouseout';

//var clickEvent = isTouchSupported ? 'click touchend touchcancel mouseup' : 'click';		// ?

function addListeners(c) {
//	console.debug('isTouchSupported: '+isTouchSupported+", isPointerEnabled: "+isPointerEnabled+", PointerEvent: "+window.PointerEvent+", MSPointer: "+window.MSPointerEvent);

	c.addEventListener(startEvent,mouseDown, false); 	
	c.addEventListener(endEvent,  mouseUp,   false);	
	c.addEventListener(outEvent,  mouseOut,   false);	 
//	c.addEventListener(moveEvent, mouseMove, false);	
//	c.addEventListener(clickEvent, mouseClick, false);			// ?
//	c.addEventListener('dblclick', mouseDblClick, false);

//	if(isTouchSupported) {
//		c.addEventListener('gesturestart', pinchStart);
//		c.addEventListener('gestureend',   pinchEnd);
//	}
	c.addEventListener("mousewheel", mouseWheel, false);
}

var rect1x=0;
var rect1y=0;
var rect2y=0;
var rect;
var startMoveTime=0;
var thiswin=0;
var thisplot=0;
var mouseIsStep=false;
var oldStepTime=0;
var mouseClickX=0;

function mouseDown(e) {
//	console.log('mouseDown');
	thisplot = mouseClickPlot(e);
	thiswin = this;		// for mouseout

	if(!plots[thisplot] || plots[thisplot].type == 'text') return;		// notta for text (yet)
	
	reScale = true;
	e.preventDefault();		// stop scrolling
	mouseIsMove=false;		// not yet
//	console.log('mousedown, e: '+e.target.id);
	setPlay(PAUSE,0);
	
	if(isTouchSupported) { 	
		if(isPointerEnabled) {
			rect1x = e.offsetX;
			rect1y = e.offsetY;
		}
		else {
			rect1x = e.touches[0].clientX; 	
			rect1y = e.touches[0].clientY; 
		}
		if(e.touches.length>1) rect2y = e.touches[1].clientY; 
	} 
	else {	
		rect1x = e.clientX;				
		rect1y = e.offsetY;				
	}

	startMoveTime = getTime();
	if(plots[thisplot].type == 'stripchart')
		this.addEventListener(moveEvent, mouseMove);

	// mouse-step logic:
//	mouseIsStep = endsWith(plots[thisplot].params[0], ".jpg");
	mouseIsStep = (plots[thisplot].type == 'video');
	if(!mouseIsStep) return;		// step only for non-video

	var rect = e.target.getBoundingClientRect();
	mouseClickX = rect1x / (rect.right - rect.left);
	oldStepTime=0;
	if(mouseClickX >= 0.5) 	setTimeout(function(){mouseStep("next");}, 500);
	else					setTimeout(function(){mouseStep("prev");}, 500);
}

function mouseStep(dir) {
	if(!mouseIsStep) return;
	if(!refreshInProgress) {
		var stepTime = getTime();
		if(debug) console.debug('mouseIsStep: '+mouseIsStep+', stepTime: '+stepTime+' oldStepTime: '+oldStepTime);
		stepCollection(thisplot,stepTime,dir);
		oldStepTime = stepTime;
		setTimeSlider(getTime());
	}
	setTimeout(function(){mouseStep(dir);}, 100);
}

function mouseOut(e) {
//	console.log('mouseOut');
//	e.preventDefault();		// for IE

//	clearTimeout();
	if(thiswin) {
		thiswin.removeEventListener(moveEvent, mouseMove);
		thiswin=0;		// avoid thrash
	}
	mouseIsMove=mouseIsStep=false; 
//	mouseUp(e);
}

function mouseUp(e) {
//	console.log('mouseUp');
//	e.preventDefault();		// for IE

//	clearTimeout();
	if(mouseIsStep && getTime() == startMoveTime) {
		if(mouseClickX >= 0.5) 	stepCollection(thisplot,startMoveTime,"next");
		else					stepCollection(thisplot,startMoveTime,"prev");
		setTime(getTime());
	}
	if(thiswin) {
		thiswin.removeEventListener(moveEvent, mouseMove);
		thiswin=0;		// avoid thrash
	}
//	if(mouseIsMove) mouseMove(e);			// ?? final-position slider ??
	mouseIsMove=mouseIsStep=false;
}

function mouseClickPlot(e) {
	var elem;
	if (e.srcElement) elem = e.srcElement;
	else 			  elem = e.target;
	return parseInt(elem.id.replace('plot',''));
}

var lastMove=0;
var mouseIsMove=false;
function mouseMove(e) {
//	console.log('mouseMove');
	if(mouseIsStep) return;			// no shimmy
	e.preventDefault();				// stop scrolling
	var now = Date().now;
	if((now - lastMove) < 100) return;	// limit update rate

	if(!refreshInProgress && !inProgress) {
		lastMove = now;
		mouseIsMove=true;

		var rect = e.target.getBoundingClientRect();
		var rectw = rect.right - rect.left;			// box width
		var eclientX;
		if(isTouchSupported) {
			if(isPointerEnabled) eclientX = e.offsetX;
			else				 eclientX = e.touches[0].clientX;
		}
		else				 eclientX = e.clientX;
		var relstep = (rect1x - eclientX)/rectw;
		
		stepDir= 0;		// no side effects
		var mDur = getDuration();		// duration msec
		var inc = relstep * mDur;			// msec
//		if(debug) console.log('rect1x: '+rect1x+', eclientX: '+eclientX+', relstep: '+relstep+', inc: '+inc);
		
//		if(Math.abs(relstep) < 0.01) return;				// too small to bother
		if(e.touches && e.touches.length == 2) 	pinchScale(e);
		else									mouseScale(e);

		mouseIsStep = false;		// switch gears
		var newTime = Math.round(startMoveTime + inc);
		if(getTime() != newTime || scalingMode == "Manual") {
			refreshCollection(true,newTime,mDur,"absolute");
			setTime(newTime);			// was cmt out...
		}
	}
}

function mouseScale(e) {
	
	var rect = e.target.getBoundingClientRect();
	var recth = rect.bottom - rect.top;			// box height
	var eclientY;
	if(isTouchSupported) {
		if(isPointerEnabled) eclientY = e.offsetY;
		else				 eclientY = e.touches[0].clientY;
	} else				 eclientY = e.offsetY;
//	else				 eclientY = e.clientY;

	var relStart = rect1y/recth - 0.5;
	var relStepY = (eclientY - rect1y) / recth;
//	if(debug) console.debug('rect1y: '+rect1y+', recth: '+recth+', eclientY: '+eclientY+', mouseScale: '+relStepY+', relStepY: '+relStepY);
	
	rect1y = eclientY;								// reset baseline for setScale logic

	if(e.shiftKey) {	 // zoom
		plots[mouseClickPlot(e)].display.setScale(null, 1./(1.-relStepY));
	}
	else {				// offset
		plots[mouseClickPlot(e)].display.setScale(relStepY, null);
	}

}

function pinchScale(e) {
//	var rect = e.target.getBoundingClientRect();
//	var recth = rect.bottom - rect.top;			// box height
	var drecty = Math.abs(rect1y - rect2y);
	var erecty = Math.abs(e.touches[0].clientY-e.touches[1].clientY);
	var scale = drecty / erecty;
	
	rect1y = e.touches[0].clientY;			// reset baseline for setScale logic
	rect2y = e.touches[1].clientY;

//	console.debug('pinchScale: '+scale+', erecty: '+erecty+', drecty: '+drecty);

	plots[mouseClickPlot(e)].display.setScale(null, scale);
}

function mouseWheel(e) {
	if(inProgress || refreshInProgress || scalingMode!="Manual") return;			// pacing
	var delta = Math.max(-1, Math.min(1, (e.wheelDelta || -e.detail)));
//	console.debug('mouseWheel delta: '+delta);
	plots[mouseClickPlot(e)].display.setScale(null, 1.-(delta/4.));
	refreshCollection(true,getTime(),getDuration(),"absolute");
}

//----------------------------------------------------------------------------------------
//buildChanLists:  build channel select option list for all plots

function buildChanLists() {
//	console.debug('buildChanLists!, channels.length: '+channels.length);
	channels.sort();			// alpha sort
	for(var j=0; j<plots.length; j++) {
		var add = document.getElementById('add'+j);
//		if(add.optgroup) add.optgroup.length = 0;	// reset
		add.options.length = 0;		// reset
		var ogl=add.getElementsByTagName('optgroup');
		for (var i=ogl.length-1;i>=0;i--) add.removeChild(ogl[i]);

		var elo = document.createElement("option");
		elo.value = elo.textContent = '+';
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
//				elo.textContent = chan.split(".")[0];	// strip suffix from display?
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
//	input.style.width = '2.5em';		// was '80px'
	var div = document.createElement('div');
	div.style.width='2em';
	div.style.float='left';
	div.style.overflow='hidden';
	el.appendChild(div);
	div.appendChild(input);
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
	input.textAlign="top";

	var div = document.createElement('div');
//	div.style.width='2vmax';
	div.style.float='right';
	div.style.textAlign="right";
	el.appendChild(div);
	div.appendChild(input);
	input.addEventListener('click', clearPlotSelect);	
}

//----------------------------------------------------------------------------------------
//addChanSelect:  add parameter to selected plot

function addChanSelect() {
	if(!this.options) return;		// some browsers
//	var nline = totalLines();
	noRebuild=true;					// no rebuild charts during selection

	var chan = this.options[this.selectedIndex].value;
	if(chan == '+') return;	// firewall: not a real selection

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
	goPause();
//	getLimits(1,0);		// ??
	reScale = true;
	stepDir= -1;
	if(debug) console.log("goBOF");
//	goTime(0);		// more robust limit checks?
	refreshCollection(true,getDuration(),getDuration(),"oldest");	// time is right-edge!

//	setTimeout(function(){ setTime(oldestTime); }, 200);
//	console.log("goBOF, oldestTime: "+oldestTime);
}

function playRvs() {
	reScale = true;
	singleStep = true;
	stopRT();
	getLimits(0,0);		// make sure limits are known...
	stepDir= -2;		// this affects playTime() to play reverse

	intervalID3 = 
		setInterval(
				function() {
					if(inProgress) {
						if(debug) console.debug("playRvs inProgress!");
						return;			// throttle
					}
					var mDur = getDuration();		// duration msec
					var inc = -0.1 * mDur;
					var newtime = getTime()+inc;
					if(debug) console.debug("playRvs getTime: "+newtime+", oldestTime: "+oldestTime);
					if(newtime <= (oldestTime+mDur)) {
						clearTimeout(intervalID3); intervalID3=0;
						goBOF();
					} else {
						refreshCollection(true,newtime, mDur, "absolute");
						setTime(newtime);		// move back 0.1x duration from left-edge time
					}
				}, 
				tDelay/10);				// 10x rate

}

function goStepRvs() {
	goPause();
	stepDir= -1;
	var mDur = getDuration();		// duration msec
	var inc = -1 * mDur;
	refreshCollection(true,getTime()+inc, mDur, "absolute");
	setTime(getTime() + inc);		// move back 1x duration from left-edge time
}

function goPause() {
	reScale = true;
	stepDir= 0;
	setPlay(PAUSE,0);			// was ,-1
	updatePauseDisplay(top.rtflag);
}

function goStepFwd() {	
	goPause();
	stepDir= 1;
	var mDur = getDuration();		// duration msec	
	var inc = 1 * mDur;				// msec
	refreshCollection(true,getTime()+inc,mDur,"absolute");	// move fwd 1x from left-edge time
	setTime(getTime() + inc);
}

function playFwd() {
	goPause();
	reScale = true;
	getLimits(0,0);		// make sure limits are known...
//	getLimits(1,1);		// make sure limits are known...
	stepDir= 2;			// this affects playTime() to use RT-playDelay to advance playback clock
	setPlay(PLAY,getTime());	
}

function togglePlay(el){
	if(el.innerHTML=="||") { 
		el.innerHTML=playStr;  
		goPause(); 
	}
	else { 
		if(document.getElementById('play').innerHTML == 'RT') 
				goRT();
		else 	playFwd(); 
		
		el.innerHTML='||'; 	
	}
	return false;
}

function goEOF() {
	goPause();
//	getLimits(0,1);		// ??
	reScale = true;
	stepDir= 1;
	if(debug) console.log("goEOF");
	refreshCollection(true,0,getDuration(),"newest");
	document.getElementById('play').innerHTML = 'RT';

//	document.getElementById('RTlab').innerHTML = 'RT';
//	timeOut = setTimeout( function(){ if(isPause()) document.getElementById('RTlab').innerHTML = '>'; },10000); 
}

function goRT() {
	reScale = true;
	stepDir=2;
	if(debug) console.log("goRT!");
	refreshCollection(false,0,getDuration(), "newest");
}

function goRT2() {
	refreshCollection(false,0,getDuration(), "newest");
}

var maxwaitTime=0;
function goTime(percentTime) {
	stepDir=0;		// turn off playRvs
	if(percentTime==0) {
//		getLimits(1,0);		// force new limits
		refreshCollection(true,0,getDuration(),"oldest");
	}
	else if (percentTime == 100) {
//		getLimits(0,1);		// force new limits
		refreshCollection(true,0,getDuration(),"newest");
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

// go to percentTime, where time is right-edge (newest) of duration time interval
function goTime2(percentTime) {
	maxwaitTime=0;
	if(newestTime == 0) {		// hopefully doesn't happen, obscure problems if lumber on
//		alert('Warning, unknown limits, unable to set time position');			
		return;
	}
	
	var mDur = 0.;
	if(!isImage) mDur = getDuration();		// duration msec
	if(mDur > (newestTime - oldestTime)) mDur = newestTime - oldestTime;
	
	var gotime = oldestTime + mDur + percentTime * (newestTime - oldestTime - mDur) / 100.;		
//	if(gotime < oldestTime) goTime = oldestTime;
//	if(gotime > (newestTime - mDur)) gotime = newestTime - mDur;
	
// try again, MJM 8/17/16, gotime is right-edge plot
	if(gotime < (oldestTime+mDur)) goTime = oldestTime + mDur;
	if(gotime > newestTime) gotime = newestTime;
	
	if(debug) console.debug("goTime: "+gotime+", percent: "+percentTime+", oldestTime: "+oldestTime+", newestTime: "+newestTime+", mDur: "+mDur);
	refreshCollection(true,gotime,getDuration(),"absolute");	// go to derived absolute time
//	setTime(gotime+mDur);
	if(percentTime==0 || percentTime==100) 	setTime(gotime);
	else									setTimeNoSlider(gotime);		// no tug of war
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
	this.yoffset = 0;						// default autoscale
	this.yrange = 1;
	this.autoScale=true;					
	this.ymin = 0.;
	this.ymax = 0.;
	
	// over-ride defaults if provided 
	for (var n in arguments[0]) { this[n] = arguments[0][n]; }
//	console.log('plot doFill: '+this.doFill+', doSmooth: '+this.doSmooth);
	
	var interpolate;		// note: smooth is per chart (fill is per-line)
	if(doSmooth) interpolate = 'bezier';
	else		 interpolate = 'linear';
	
	// create smoothie chart
	if(debug) console.log('new chart');
	
	this.chart = new SmoothieChart({
		yRangeFunction:myYRangeFunction.bind(this),
		interpolation:interpolate,			// linear, bezier
		grid:{ 
			fillStyle:'#ffffff', 
			strokeStyle:'#cccccc', 
			sharpLines:false, 
			verticalSections:this.vertGrids 
		},
		labels:{ 
			fillStyle:'#000000', 
			fontSize:'12', 
			precision:1 
		},
//		timestampFormatter:SmoothieChart.timeFormatter,
		timestampFormatter:myTimeFormatter,
		timerangeFormatter:myRangeFormatter
	});

	this.chart.options.scaleSmoothing = 0.25;		// default 0.125
	this.chart.stop();		// ?? init
// now handled by refreshCollection RBNB data fetch
//	this.chart.options['maxDataSetLength'] = this.maxChartPts;		// trial
//	this.chart.resetBounds = false;
	
	//----------------------------------------------------
	// set short range value with letter suffix
	function myRangeFormatter(val) {
	    var aval = Math.abs(val);
	      
		var prec=5;		// digits of precision
		if(aval == 0 || isNaN(aval)) prec=0;
		else if(aval > 20) 	prec=0;
		else if(aval > 1)  	prec=1;
		else 				prec = Math.ceil(-Math.log(aval)/Math.LN10);
		if(scalingMode != "Standard") prec = prec+1;		// need more digits if tight scaling
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
//		var y= pad2(date.getUTCFullYear() - 2000);
//		var M= pad2(date.getUTCMonth()+1);
//		var d= pad2(date.getUTCDate());
//		var h= pad2(date.getUTCHours());
//		var m= pad2(date.getUTCMinutes());
//		var s= pad2(date.getUTCSeconds());
		var y= pad2(date.getFullYear() - 2000);
		var M= pad2(date.getMonth()+1);
		var d= pad2(date.getDate());
		var h= pad2(date.getHours());
		var m= pad2(date.getMinutes());
		var s= pad2(date.getSeconds());
		var ms= pad2(date.getMilliseconds());
//		console.debug('duration: '+duration);
		var now = new Date().getTime();		// msec since 1970
		var then = date.getTime();
		var longAgo = (now - then) > 86400000;	// 1 day (msec)
		if(longAgo && duration > 864000)	return M+'/'+d+'/'+y;		// 10 days (duration=sec)
//		else if(longAgo && duration >= 600) 	return M+'/'+d+'/'+y+' '+h+':'+m;
		else if(longAgo && duration >= 86400) 	return M+'/'+d+' '+h+':'+m;
//		else if(longAgo)			return M+'/'+d+'/'+y+' '+h+':'+m+':'+s;
		else if(duration >= 600)		return h+':'+m;		// trust sec==00 if min>=10
		else if(duration >= 10)			return h+':'+m+':'+s;
		else if(duration >= 1) {
			var fsec = parseFloat(s+'.'+ms).toFixed(1).toString();		// 1 decimal digit
			return h+':'+m+':'+fsec;
		}
		else {
			var fsec = parseFloat(s+'.'+ms).toString();	// trim trailing zeros
			return ':'+fsec;
		}
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
//			console.debug('addValue, param: '+param+', line.length: '+line.data.length);
			var nosort=true;
			if(nosort) {		// try faster append without sort
				line.data.push([time, value]);		// try faster push 
				line.maxValue = isNaN(line.maxValue) ? value : Math.max(line.maxValue, value);
				line.minValue = isNaN(line.minValue) ? value : Math.min(line.minValue, value);
			} else {
				line.append(time, value);	// smoothie func
			}
			this.chart.now = time;		// for playback time render()
		}
	};
	
	// direct-assignment a data value to timeseries
	this.putValue = function(line, time, value, idx) {
//		console.debug('putValue, param: '+param+', line.length: '+line.data.length);

		line.data[idx] = [time, value];		// try faster push 
		line.maxValue = isNaN(line.maxValue) ? value : Math.max(line.maxValue, value);
		line.minValue = isNaN(line.minValue) ? value : Math.min(line.minValue, value);

		this.chart.now = time;		// for playback time render()
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
		this.width = element[0].width;		// stripcharts use only first layer
		this.canvas = element[0];
		this.chart.streamTo(element[0],1000);  // 1 sec delay
	};
	
	this.setDelay = function(delay) {
		this.chart.delay = delay;
	};
	
	this.stop = function() {			// no go
		this.chart.stop();
	};
	
	this.start = function() {			// re go
//		this.dropdata();				// eliminate old-data glitch?
		this.chart.start();
	};
	
	this.render = function(etime) {
		if(typeof this.canvas == 'undefined') return;		// notta to do
		this.chart.stop();									// no scrolling if render!?
		var chartnow = (etime>0)?etime:this.chart.now;
//		console.debug('cnow: '+this.chart.now+', etime: '+etime);
		this.chart.options.scaleSmoothing = 1.0;	// one-step jump to new scale
		for(var key in this.lines) {
//			console.debug("RESET BOUNDS, key: "+key+", linelen: "+this.lines[key].data.length)
			if(this.lines[key].data.length > 0) this.lines[key].resetBounds();		// only scale active lines
		}
		this.chart.render(this.canvas, chartnow);
		this.chart.options.scaleSmoothing = 0.25;   		// was 0.125
	};
	
	this.dropdata = function() {
		this.chart.stop();
		for(var j=0; j<this.chart.seriesSet.length; j++) {
			var ts = this.chart.seriesSet[j].timeSeries;
//			ts.data.splice(0, ts.data.length);		// delete all data
			ts.data = [];				// better delete?
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
	
	// set scale in terms of normalized offset and range
	this.setScale = function(yoffset, yrange) {
		if(scalingMode != "Manual") return;			// notta
		
		if(this.autoScale) {						// initialize, then switch to manual scaling
			this.yrange = (this.ymax - this.ymin);
			this.yoffset = this.ymin + this.yrange / 2;
			this.autoScale = false;
		}
		if(yoffset != null) this.yoffset += yoffset * this.yrange;
		if(yrange != null)  this.yrange   = yrange  * this.yrange;
//		console.debug('setCale, yoffset: '+yoffset+', this.yoffset: '+this.yoffset+', yrange: '+yrange+', this.yrange: '+this.yrange);
	}
	
	// myYRangeFunction:  custom y-range function
	function myYRangeFunction(range) {
		
		if(scalingMode != "Manual") this.autoScale = true;

		if(!this.autoScale) {									// Manual Scaling
			this.ymax = this.yoffset + this.yrange/2;
			this.ymin = this.yoffset - this.yrange/2;
			return { min: this.ymin, max: this.ymax};
		}

		else if(scalingMode == "Tight") {						// Tight scaling

			var wildPointReject = 5;							// wild point reject > this number stdDev	
			if(wildPointReject > 0) {						
				var getAverage = function( data ){
					var i = data.length, 
						sum = 0;
					while( i-- ) sum += data[ i ][1];							// bleh, values are in data[*][1]
					return (sum / data.length );
				},
				getStandardDeviation = function( data ){
					var avg = getAverage( data ), 
						i = data.length,
						v = 0;
					while( i-- )v += Math.pow( (data[ i ][1] - avg), 2 );		// bleh, values are in data[*][1]
					v /= data.length;
					return Math.sqrt(v);
				};	    		    

				// now compute min/max throwing out wildpoints
				var chartMaxValue = Number.NaN, chartMinValue = Number.NaN;
				for (var d = 0; d < this.chart.seriesSet.length; d++) {
					var timeSeries = this.chart.seriesSet[d].timeSeries;
					var mean = getAverage(timeSeries.data);
					var stdDev = getStandardDeviation(timeSeries.data);
					var wpmax = mean + wildPointReject * stdDev;
					var wpmin = mean - wildPointReject * stdDev;
//					console.debug("series: "+d+", mean: "+mean+", stdDev: "+stdDev+", oldMin: "+range.min+", oldMax: "+range.max);
					
					if (timeSeries.data.length) {
						// Walk through all data points, finding the min/max value
						timeSeries.maxValue = timeSeries.data[0][1];
						timeSeries.minValue = timeSeries.data[0][1];
						for (var i = 1; i < timeSeries.data.length; i++) {
							var value = timeSeries.data[i][1];
							if (value > timeSeries.maxValue && value < wpmax) timeSeries.maxValue = value;
							if (value < timeSeries.minValue && value > wpmin) timeSeries.minValue = value;
						}
						chartMaxValue = !isNaN(chartMaxValue) ? Math.max(chartMaxValue, timeSeries.maxValue) : timeSeries.maxValue;
						chartMinValue = !isNaN(chartMinValue) ? Math.min(chartMinValue, timeSeries.minValue) : timeSeries.minValue;
					} 

					if(Math.abs(range.min - timeSeries.minValue) < stdDev) timeSeries.minValue = range.min;		// only adjust if > stdDev
					if(Math.abs(range.max - timeSeries.maxValue) < stdDev) timeSeries.maxValue = range.max;	
					
					chartMaxValue = !isNaN(chartMaxValue) ? Math.max(chartMaxValue, timeSeries.maxValue) : timeSeries.maxValue;
					chartMinValue = !isNaN(chartMinValue) ? Math.min(chartMinValue, timeSeries.minValue) : timeSeries.minValue;
//					console.debug("series: "+d+", mean: "+mean+", stdDev: "+stdDev+", newMin: "+range.min+", newMax: "+range.max);
				}
				range.min = chartMinValue;		
				range.max = chartMaxValue;
			}
			this.ymin = range.min;
			this.ymax = range.max;
			return({min: range.min, max: range.max});
		}

		else {													// Auto and Standard scaling

			var vmin = roundHumane(range.min,0);
			var vmax = roundHumane(range.max,1);
			if((vmin*vmax>0) && (vmin/vmax <= 0.25)) vmin = 0.;
			if(isNaN(vmin) || isNaN(vmax)) return({min: range.min, max: range.max});		// watch for bad nums
				
			// adjust by any manual tweeks:
//			var vrange = this.yrange * (vmax - vmin);						// scaled range
//			var voffset = (vmax+vmin)/2. + this.yoffset *(vmax-vmin);		// new midpoint
//			vmax = voffset + vrange / 2;
//			vmin = voffset - vrange / 2;
//			console.debug('myYRange, ymin: '+vmin+', ymax: '+vmax+', this.yrange: '+this.yrange);

			if((scalingMode == "Auto") && (reScale==false)) {	// Auto scaling
				if(vmax > this.ymax) this.ymax = vmax;			// increasing only
				if(vmin < this.ymin) this.ymin = vmin;				
				if(debug) console.debug("autoscale, vmax: "+vmax+", ymax: "+this.ymax+", vmin: "+vmin+", ymin: "+this.ymin);
			}
			else {												// Standard scaling
				this.ymax = vmax;
				this.ymin = vmin;			
				if(plots.length==1 && scalingMode=="Auto") reScale = false;	// one-shot rescale flag (logic doesn't work per-plot)
				if(debug) console.debug("stdscale, vmax: "+vmax+", ymax: "+this.ymax+", vmin: "+vmin+", ymin: "+this.ymin);
			}

			this.yrange = this.ymax - this.ymin;
			this.yoffset = this.ymin + this.yrange / 2;

			return {min: this.ymin, max: this.ymax};
//			return {min: vmin, max: vmax};
		}
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
	var colors = new Array('#2020ee','#ee1010','#00dd00','#880088','#000000','#808080');	// ~RGB
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
//		else if	(endsWith(param,".mp3") || endsWith(param,".wav")) 	paramtype = 'audio';
		else if (endsWith(param,"txt"))								paramtype = 'text';
		else														paramtype = 'stripchart';
		if(this.type == null) this.type = paramtype; 			// only set type on first param

//		if(debug)console.log('addParam: '+param+", type: "+this.type);

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
//				if(this.display == null) this.display = new vidscan(param);	
//				if(paramtype == 'video') this.params.length=0;			// only one vid per plot?
				this.display = new vidscan(param);	
				/*
				else if(paramtype == 'video') {		// presume a stripchart added to video is audio
					alert("only one video per plot");	
					return;
				}
				*/
				break;	
			case 'audio':		
				if(this.display == null) this.display = new audioscan();	// was audioscan(param)
				else if(paramtype == 'audio') {		// presume a stripchart added to video is audio
					alert("only one audio per plot");	
					return;
				}
				break;	
			case 'text':
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
	
//  ----------------------------------------------------------------------------------------    
	// setText: display text in plot box
	this.setText = function(text) {
		var cvs = this.canvas[0];		// first layer
		var ctx = cvs.getContext('2d');
		ctx.font = "16px Arial";
		ctx.clearRect(0,0,cvs.width,cvs.height); 		// clear old image
		
		var lineHeight = 20;
		var lineMargin = 20;
		var y = lineHeight;
		var lines = text.split('\n');
		var firstLine = lines.length - Math.ceil((cvs.height-20) / lineHeight);	// drop old lines
		if(firstLine < 0) firstLine = 0;
		for(var n=firstLine; n < lines.length; n++) {
			ctx.fillText(lines[n], lineMargin, y);
			y += lineHeight;
		}
	}
}

//----------------------------------------------------------------------------------------	
// AudioScan:  audio replay functions
//----------------------------------------------------------------------------------------	

//---------------------------------------------------------------------------------	
//var audioContext=null;
var audioAlert=true;
function audioscan() {
	this.rate = 22050;			// hard code audio rate for now.  22050 is slowest Web Audio supports
//	this.rate = 8000;			// need to derive rate...
	
	if(audioContext == null) {
//		var contextClass = (window.AudioContext || window.webkitAudioContext || window.mozAudioContext || window.oAudioContext || window.msAudioContext);
//		if (contextClass) audioContext = new contextClass();
		audioContext = new (window.AudioContext || window.webkitAudioContext)();
	}	
	if(audioContext == null) {
		if(audioAlert) {
//			audioAlert=false;		// one time alert
			alert('no audio context available with this browser');
		}
		goPause();
		return;
	}
	
	var audioSource = audioContext.createBufferSource();
	audioSource.connect(audioContext.destination);
	
	this.playPcmChunk = function(audio, srate) {
//		var audio=new Float32Array(data);
		if(srate <= 0) srate = this.rate;

//		TO DO:  use audio in audio.wav (vs pcm) format, then skip the createBuffer() call which is not implemented on IOS
		if(audio.length <= 0) {
			console.warn("playPcmChunk zero length audio!");
			inProgress=0;
			return;
		}
	
		// Warning:  Safari doesn't work with srate<22050
		var audioBuffer = audioContext.createBuffer(1, audio.length, srate);	
		audioBuffer.getChannelData(0).set(audio);
		audioSource.buffer = audioBuffer;
		audioSource.start ? audioSource.start(0) : audioSource.noteOn(0);
	}
    
    this.playMp3Chunk = function(data) {
    	audioContext.decodeAudioData(data, this.playPcmChunk, 0);
    }
    
    this.playWavChunk = function(data) {
//    	audioContext.decodeAudioData(data, this.playPcmChunk);
    	this.playPcmChunk(data.slice(44), 0);			// slice of .wav header
    }
    
	this.playAudio = function(audio) {		
//		audio = bytesToAudio(audio);
		if(audio.length == 0) return;		// notta
		var audio=new Float32Array(data);
		var audioBuffer = audioContext.createBuffer(1, audio.length , this.rate);
		audioBuffer.getChannelData(0).set(audio);
//		var audioBuffer = audioContext.createBuffer(1, audio.length , this.rate);
//		audioBuffer.getChannelData(0).set(audio);
		audioSource.buffer = audioBuffer;
		audioSource.start(0);
/*		
		audioContext.decodeAudioData(audio,function(buffer){
			audioSource.buffer = buffer;
			audioSource.start(0);
		},alert('decodeAudio Error'));
*/
	}
	
	// https://ccrma.stanford.edu/courses/422/projects/WaveFormat/
	function addWaveHeader(audio, sampleRate) {
	    var bytesPerSample = 2;
	    var numChannels = 1;

	    var numFrames = audio.length / bytesPerSample;

	    var blockAlign = numChannels * bytesPerSample;
	    var byteRate = sampleRate * blockAlign;
	    var dataSize = numFrames * blockAlign;
	 
	    var buffer = new ArrayBuffer(44);
	    var dv = new DataView(buffer);
	 
	    var p = 0;
	 
	    function writeString(s) {
	        for (var i = 0; i < s.length; i++) {
	            dv.setUint8(p + i, s.charCodeAt(i));
	        }
	        p += s.length;
	    }
	 
	    function writeUint32(d) {
	        dv.setUint32(p, d, true);
	        p += 4;
	    }
	 
	    function writeUint16(d) {
	        dv.setUint16(p, d, true);
	        p += 2;
	    }
	 
	    writeString('RIFF');              // ChunkID
	    writeUint32(dataSize + 36);       // ChunkSize
	    writeString('WAVE');              // Format
	    writeString('fmt ');              // Subchunk1ID
	    writeUint32(16);                  // Subchunk1Size
	    writeUint16(1);                   // AudioFormat
	    writeUint16(numChannels);         // NumChannels
	    writeUint32(sampleRate);          // SampleRate
	    writeUint32(byteRate);            // ByteRate
	    writeUint16(blockAlign);          // BlockAlign
	    writeUint16(bytesPerSample * 8);  // BitsPerSample
	    writeString('data');              // Subchunk2ID
	    writeUint32(dataSize);            // Subchunk2Size
	 
	    var wav = new ArrayBuffer(buffer.length + audio.length);
	    wav.set(buffer);
	    wav.set(audio, buffer.length);
	    return wav;
	    
//	    return buffer.concat(c);
	}

	//----------------------------------------------------------------------------------------
	// bytesToAudio:  convert byte array of shorts to audio array (scaled floats)

	function bytesToAudio(barray) {
		var n = barray.length / 2;
		var sarray = new Float32Array();
		for(i=0, j=0; i<n; i++,j+=2) {
			sarray[i] = (barray[j] + barray[j+1]*256.) / 32768.;
		}

		return sarray;
	}
	
}

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

//var videoInProgress=0;		// try global?  // should be per plot
function vidscan(param) {
//	console.log('new vidscan: '+param);
	this.videoInProgress = 0;
	this.addLayer={};
// globals
	Tnew=0;
	Told=0;
	this.canvas=null;
	
//  ----------------------------------------------------------------------------------------    
//  addCanvas:  set canvas object

    this.addCanvas = function(element) {
    	this.canvas = element;		// element here is array of canvas (layers)
//    	canvas = element;		// foo
//    	console.log('vidscan addCanvas: '+this.canvas.id);
    }

//  ----------------------------------------------------------------------------------------    
//  imgerror:  handle image load error

    function imgerror() {
    	this.videoInProgress=0;
    	console.debug('vidscan imgerror, playmode: '+getPlayMode());
//    	if(getPlayMode()==RT) adjustPlayDelay(1000);		// try slowing down
//    	else				 
    		goPause();					//  stop!
    }

//  ----------------------------------------------------------------------------------------    
    var lastload=0;
    this.setImage = function(imgurl,param,ilayer) {
    	if(!this.canvas) return;
    	
    	if(debug) console.log("vidscan setImage, inprogress: "+this.videoInProgress+', imgurl: '+imgurl+', ilayer: '+ilayer);
		var now = new Date().getTime();
//   	if((imgurl.indexOf("newest") != -1) || (imgurl.indexOf("oldest") != -1)) { // no wait check on limits requests
//   	}
		if((now-lastload)>10000) {		// checks to avoid deadlock
			if(debug) console.debug('reset video_inprogress');
    		this.videoInProgress = 0;
    	}
		else if(this.videoInProgress>2 /* && imgurl.indexOf("oldest")!=-1 && imgurl.indexOf("newest")!=-1 */) {		// don't overwhelm (was >2)
    		console.warn('video busy, skipping: '+imgurl);
//    		this.videoInProgress = 0;		// single wait?
    		return;						
    	}
		
		lastload = now;
		this.videoInProgress++;
    	img=new Image();							// make new Image every time to avoid onload bug?
    	
    	if(ilayer >= maxLayer) ilayer = maxLayer-1;		// image layers, extra layers auto-alpha
    	img.canvas = this.canvas[ilayer];
    	if(ilayer == 0) alpha = 1;
    	else			alpha = 0.5;
//    	if(img.canvas == null) console.debug("OOPS, setImage without canvas!!!");
//    	this.img.onload = imgload.bind(this);

    	img.onload = function() {		// draw on image load (new function with new image)
//    		if(debug) console.warn("imgload: complete: "+this.complete+", inprogress: "+this.videoInProgress+", this.width: "+this.width);
    		if(this.canvas == null) return;					// can happen with getLimits() before buildCharts()
    		ratiox = this.width / this.canvas.width;
    		ratioy = this.height / this.canvas.height;
    		if(ratiox > ratioy) {
    			w = this.canvas.width;
    			h = this.height / ratiox;
    			x = 0;
    			y = (this.canvas.height - h) / 2;
    		}
    		else {
    			h = this.canvas.height;
    			w = this.width / ratioy;
    			x = (this.canvas.width - w) / 2;
    			y = 0;
    		}

    		var ctx = this.canvas.getContext('2d');
			ctx.clearRect(0,0,this.canvas.width,this.canvas.height); 		// clear old image layer
    		ctx.globalAlpha = alpha;
//    		console.debug('draw alpha: '+alpha+', canvas: '+this.canvas);
    		ctx.drawImage(this,x,y,w,h);
    	}
    	img.onerror = imgerror.bind(this);
    	
		var twostep=false;
		if(twostep) {
			img.src = imgurl;								// this is what initiates network fetch
			setImageTime(imgurl,param);							// fetch timestamp separate step
		}
		else {
			this.AjaxGetImage(imgurl,img,param);				// get image and timestamp one-step (from header)
		}
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
   
//----------------------------------------------------------------------------------------
// setImageTime:  get image time via separate Ajax request
    
    function setImageTime(imgurl,param) {
    	if(debug) console.debug("image.setTime: "+imgurl);
    	AjaxGetV(imageSetTime, imgurl+"&dt=s&f=t", param);
    	function imageSetTime(txt) {
    		stime = Math.floor(1000*parseFloat(txt));
    		if(imgurl.indexOf("r=newest") != -1) { 
    			Tnew = stime;  
//        		if(Tnew > newestTime || newestTime == 0) 
        			newestTime = Tnew;
    		}
    		else if(imgurl.indexOf("r=oldest") != -1) { 
    			Told = stime; 
//        		if(Told < oldestTime || oldestTime == 0) 
        			oldestTime = Told;
    		}
//    		setTimeNoSlider(stime);
    		setTime(stime);
    		paramTime[param] = stime;
    		lastmediaTime = stime;
    	}
    }

//----------------------------------------------------------------------------------------
// AjaxGetV:  Ajax request helper func

    function AjaxGetV(myfunc, url, param, args) {	
    	var xmlhttp=new XMLHttpRequest();

    	xmlhttp.onreadystatechange=function() {
        	fetchActive(false);

    		if (xmlhttp.readyState==4) {
    			if(xmlhttp.status==200) {
    				myfunc(xmlhttp.responseText, url, args);
    			}
    			else {
    				if	   (url.indexOf("r=next") != -1) paramTime[param] = 99999999999999;
    	    		else if(url.indexOf("r=prev") != -1) paramTime[param] = 0;   				
//    	    		paramTime[param] = -1;		// out of action
//  				console.log('Error: '+url);
// following alert can be deadly lock-up loop on ipad:  add status field to display?
//    				alert('Error on data fetch! '+url+', status: '+xmlhttp.status);
    			}
    		}
    	};
//    	console.debug('AjaxGetV: '+url);
    	xmlhttp.open("GET",url,true);
    	fetchActive(true);
    	xmlhttp.send();
    }


//  ----------------------------------------------------------------------------------------
// AjaxGetImage:  long way around to get image header with timestamp
// this may not be reliable and portable:  have seen partial images chrome/Win8, no images android/std-browser...
// alternate: much simpler/reliable img.src=foo, deal with timestamps separately (also more compatible with webturbine...)
    var Tlast=0;
    nreq=0;
    this.AjaxGetImage = function(url,img,param) { 
		
    	if(debug) console.log('AjaxGetImage, url: '+url);
//    	if(this.nreq > 2) return;		// drop frames if getting behind...
    	var instance = this;			// for reference inside onreadystatechange function
    	var xmlhttp=new XMLHttpRequest();
    	
    	xmlhttp.onreadystatechange=function() {
        	fetchActive(false);

    		if (xmlhttp.readyState==4) {
//    			console.log("Ajax readstatechange: "+xmlhttp.readyState);
    			
				if(debug) console.log("AjaxGetImage, got: "+url+", inprogress: "+instance.videoInProgress+", status: "+xmlhttp.status);
				instance.videoInProgress--;			// decremented in imgload()
				if(instance.videoInProgress < 0) instance.videoInProgress = 0;
				
    			if(xmlhttp.status==200) {
    				if(debug && isPause()) console.log('AjaxGetImage while paused! url: '+url);

    				var wurl = window.URL || window.webkitURL;
    				var tstamp = this.getResponseHeader("time");								// float sec (with millisecond resolution)
    				var tstamp2 = this.getResponseHeader("Last-Modified");
//    				if(!tstamp) tstamp = Date.parse(tstamp2)/1000;								// full-sec
    				if(debug) console.log('image header tstamp: '+tstamp+", Last-Modified: "+tstamp2);
    				
    		    	img.src = wurl.createObjectURL(new Blob([this.response], {type: "image/jpeg"}));
    				if(debug) console.debug("img.src = new blob!  url: "+url+", length: "+xmlhttp.response.size);

    				var holdest = xmlhttp.getResponseHeader("oldest");		
    				if(holdest != null) oldestTime = 1000 * Number(holdest);	// just set it, worry about multi-chan consistency later...

    				var hnewest = xmlhttp.getResponseHeader("newest");		
    				if(hnewest != null) {
    					newestTime = 1000 * Number(hnewest);
    					paramTime[param] = newestTime;
    				}

    		    	if(debug) console.debug('AjaxGetImage, tstamp: '+tstamp+", holdest: "+holdest+", hnewest: "+hnewest);
    			
    		    	if(tstamp != null) {
    		    		var T = Math.floor(1000*parseFloat(tstamp));
    		    		if( /* (T > Tnew) || */ hnewest==null && (url.indexOf("r=newest") != -1)) {
    		    			Tnew = T;
    		    			if(debug) console.debug("AjaxGetImage, update newest time: "+newestTime+" -> "+T+", url: "+url);
//        		    		if(T > newestTime || newestTime == 0) 
        		    			newestTime = T;
    		    		}
    		    		
//    		    		if((Told==0) || (T < Told) || (url.indexOf("r=oldest") != -1)) Told = oldestTime = T;
    		    		if(/* (T < Told) || */ holdest==null && (url.indexOf("r=oldest") != -1)) {
    		    			Told = T;
//        		    		if(T < oldestTime || oldestTime == 0) 
        		    			oldestTime = T;
    		    		}
    		    		
        		    	Tlast = T;
        		    	if(debug) console.debug('AjaxGetImage, header Time: '+T);
//        		    	if(debug) console.debug("param: "+param+", plots[0].params[0]: "+plots[0].params[0]);
        		    	if(param==plots[0].params[0]) setTime(T);		// ??
        		    	lastmediaTime = T;
//        	    		setTimeNoSlider(T);
        	    		if(hnewest == null || top.rtflag!=RT) paramTime[param] = T;
    		    	}
    		    	else {	// update time limits if not provided in HTTP header
//    		    		url = url.split('?',1);
    		    		url = url.replace("dt=b","dt=s");
    		    		url = url.replace("f=b","f=t");
    		    		setImageTime(url,param);
    		    	}
    			}
    			else {
    				if(debug) console.log('Warning, xmlhttp.status: '+xmlhttp.status);
    				if	   (url.indexOf("r=next") != -1) paramTime[param] = 99999999999999;
    	    		else if(url.indexOf("r=prev") != -1) paramTime[param] = 0; 
    				
					else if((getTime() >= newestTime && top.rtflag!=RT) || (xmlhttp.status != 410 && xmlhttp.status != 404)) {	// keep going (over gaps)
						if(debug) console.log('stopping on xmlhttp.status: '+xmlhttp.status+", time: "+getTime()+", newestTime: "+newestTime);
						goPause();
					}
//    				if(top.rtflag==RT) adjustPlayDelay(100);    			// need?	
    				// following alert can be deadly lock-up loop on ipad:  add status field to display?
//    				alert('Error on image fetch! '+url+', status: '+xmlhttp.status);
    			}
    		}
    	}
    	xmlhttp.open("GET",url,true);
    	xmlhttp.responseType = 'blob';
    	fetchActive(true);
    	xmlhttp.send();
//    	nreq++;
    }
}



