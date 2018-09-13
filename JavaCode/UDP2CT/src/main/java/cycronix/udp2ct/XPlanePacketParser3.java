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

/*
 * XPlanePacketParser3
 *
 * Parse any of the binary packets sent via UDP from X-Plane.  This class supports any of the binary packet formats;
 * IDs and channels names are provided via configuration file.  Store the parsed data in CloudTurbine.
 *
 * This class is based on XPlanePacketParser2.java, which in turn was based on XPlanePacketParser.java; both of these
 * classes were developed by Cycronix.  Those versions of the class captured the binary packet via UDP, parsed the
 * packet and stored the resulting data in RBNB server.
 *
 * Matt Miller, Cycronix
 * John Wilson, Erigo Technologies
 * 8/2018
 */

package cycronix.udp2ct;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.TimeZone;

import cycronix.ctlib.CTwriter;

public class XPlanePacketParser3 implements UnityPlayer {

	// Initial/starting position values - maintain one set of these across all instances of XPlanePacketParser3
	private static float lat_deg_init = -999.0f;
	private static float lon_deg_init = -999.0f;
	private static float alt_ftmsl_init = -999.0f;

	private static final String XPLANE_PARAMS_FILENAME = "XPlaneParams.txt";	// config file of code,params
    private static Hashtable<Integer,String[]> htable = null;
	private UDP2CT udp2ct = null;
    private CTwriter ctw = null;
	private String CSV = "";
    private double time_msec = 0;
    private boolean bSavePacketDataToCT = false;
    private boolean bPrintDebug = false;

    // Store some channels for later processing by the calling class
    private float lat_deg = -999.0f;
	private float lon_deg = -999.0f;
	private float alt_ftmsl = -999.0f;
	private float pitch_deg = -999.0f;
	private float roll_deg = -999.0f;
	private float hding_true = -999.0f;

	//---------------------------------------------------------------------------------------------
	// constructor
	public XPlanePacketParser3(UDP2CT udp2ctI, CTwriter ctwI, double time_msecI, byte[] packetBytes, boolean bSavePacketDataToCTI, boolean bPrintDebugI) throws Exception {

		if (htable == null) {
			System.err.println("Read X-Plane channel configuration file, \"" + XPLANE_PARAMS_FILENAME + "\"");
			htable = new HashParams().htable;
		}

		udp2ct = udp2ctI;
		ctw = ctwI;
		bSavePacketDataToCT = bSavePacketDataToCTI;
		time_msec = time_msecI;
		bPrintDebug = bPrintDebugI;

		// System.err.println("packet:\n" + UDP2CT.bytesToHex(packetBytes));

		ByteBuffer packetBB = ByteBuffer.wrap(packetBytes);
		packetBB = packetBB.order(ByteOrder.LITTLE_ENDIAN);

		try {
			//
			// Save the entire binary packet
			//
			if (bSavePacketDataToCT && (ctw != null)) {
				ctw.putData("data.bin", packetBytes);
			}
			//
			// Read the 5 character header
			//
			byte[] header = new byte[5];
			for (int i = 0; i < header.length; i++) header[i] = packetBB.get();
			if (bPrintDebug) {
				System.err.println("XPlane packet: bytes: " + packetBytes.length + ", t: " + time_msecI);
			} else {
				System.err.print(" X");
			}
			//
			// Initialize CSV string
			//
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS z");
			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
			String dateStr = sdf.format(new Date((long) (time_msec)));
			CSV = new String("XPLANE," + dateStr);
			//
			// Read each 36-byte structure
			//
			while (packetBB.hasRemaining()) {
				int id = packetBB.getInt();
				if (bPrintDebug) System.err.println("New packet: " + id);
				processPacket(packetBB, htable.get(id));
			}
			//
			// Save the CSV string
			//
			if (bSavePacketDataToCT && (ctw != null)) {
				ctw.putData("data.txt", CSV);
			}
		} catch (Exception e) {
			System.err.println("Error parsing XPlane packet: " + e);
		}

    }
    
    //------------------------------------------------------------------------------------------------------
    // process XPlane UDP packet into CT channels
    private void processPacket(ByteBuffer packetBB, String[] params) {

    	// Read 8 single precision floats
		float[] data = new float[8];
		for (int i=0; i<data.length; ++i) {
			data[i] = packetBB.getFloat();
		}

		try {
			for(int i=0; i<params.length; i++) {
				if(params[i]==null || (params[i].length() == 0)) continue;		 // skip blank names
				// May want to do something about checking for duplicate chan names.
				// Here's how it was previously done when storing data to RBNB:
				// if(srcMap.GetIndex(params[i]) >= 0) System.err.println("Warning, duplicate parameter: "+params[i]);
				if (bSavePacketDataToCT && (ctw != null)) {
					ctw.putData(params[i], data[i]);
				}
				if (bPrintDebug) System.err.println("chan[" + i + "]: " + params[i] + ", data: " + data[i]);
				// Add this value to the CSV string
				CSV += ","+data[i];
				// Certain channels are saved for later use
				if (params[i].equals("packet_017_pitch_deg.f32")) {
					pitch_deg = data[i];
				} else if (params[i].equals("packet_017_roll_deg.f32")) {
					roll_deg = data[i];
				} else if (params[i].equals("packet_017_hding_true.f32")) {
					hding_true = data[i];
				} else if (params[i].equals("packet_020_lat_deg.f32")) {
					lat_deg = data[i];
				} else if (params[i].equals("packet_020_lon_deg.f32")) {
					lon_deg = data[i];
				} else if (params[i].equals("packet_020_alt_ftmsl.f32")) {
					alt_ftmsl = data[i];
				}
			}
    	} catch (Exception e) {
    		System.err.println("Error parsing XPlane packet: " + e);
    	}
    }

	//------------------------------------------------------------------------------------------------------
	//
	// Create a string for the X-Plane aircraft to participate in a CT/Unity game.
	//
	// This method implements the required method defined in interface UnityPlayer.
	//
	// This method includes a lat/lon to x,y flat earth conversion approximation
	// Sources which discuss this conversion:
	// 1. See "Local, flat earth approximation" at http://www.edwilliams.org/avform.htm
	// 2. See "Equirectangular approximation" at https://www.movable-type.co.uk/scripts/latlong.html
	// For this conversion, need to be careful to do calculations in *radians*, not degrees.
	//
	// Here are the calculations, where lat0deg and lon0deg is the original/reference lat/lon position and
	// lat1deg and lon1deg is a later position.
	//
	// delta_y = R*(lat1deg-lat0deg)*pi/180
	// delta_x = R*cos(lat0deg*pi/180)*(lon1deg-lon0deg)*pi/180
	// distance traveled = sqrt(delta_x^2 + delta_y^2)
	//
	public String createUnityString() {
		double time_sec = time_msec / 1000.0;
		// Make sure we have good data to make a string
		if ((lat_deg == -999.0f) || (lon_deg == -999.0f) || (alt_ftmsl == -999.0f) || (pitch_deg == -999.0f) || (roll_deg == -999.0f) || (hding_true == -999.0f)) {
			// a data channel isn't set properly; don't specify a Unity text block
			return "";
		}
		if ((lat_deg == 0.0f) && (lon_deg == 0.0f) && (alt_ftmsl == 0.0f) && (pitch_deg == 0.0f) && (roll_deg == 0.0f) && (hding_true == 0.0f)) {
			// all data is 0, this is probably a startup packet; don't specify a Unity text block
			return "";
		}
		if (lat_deg_init == -999.0f) {
			// Store the initial/starting position
			lat_deg_init = lat_deg;
			lon_deg_init = lon_deg;
			alt_ftmsl_init = alt_ftmsl;
		}

		double R_earth_radius_km = 6378.137; // km, equatorial radius of the earth in WGS84
		double delta_y_km = R_earth_radius_km*Math.toRadians(lat_deg-lat_deg_init);
		double delta_x_km = R_earth_radius_km*Math.cos(Math.toRadians((double)lat_deg_init))*Math.toRadians(lon_deg-lon_deg_init);
		double alt_km = (alt_ftmsl - alt_ftmsl_init) / 3280.84;  // convert altitude from feet to km (1 km = 3280.84 feet)
		if (alt_km < 0) {
			alt_km = 0.0;
		}

		//
		// Construct the string to be used in CT/Unity
		//
		// Scale the position to fit nicely within the CTrollaball field of play
		double scalingFactor = 25.0;
		String unityStr = udp2ct.createUnityString(time_sec,
				                                   (float)(scalingFactor*delta_x_km),
				                                   (float)(scalingFactor*alt_km + udp2ct.getAltOffset()),
				                                   (float)(scalingFactor*delta_y_km),
				                                   (float)(-1.0*pitch_deg),
				                                   (float)hding_true,
				                                   (float)(-1.0*roll_deg));

		return unityStr;
	}
    
    //------------------------------------------------------------------------------------------------------
	//
	// HashParams
	//
	// Read the X-Plane UDP configuration file.
	//
	// UDP packets written out by X-Plane contain one or more 36-byte sections where the first 4 bytes is an
	// integer id code describing the type of packet this is and then 32 bytes made up of 8 single-precision channel
	// values.
	//
	// Each line in the configuration file we read here is a CSV string made up of the id followed by a series of
	// channel names.  Some channel names may be blank, indicating that we aren't interested in storing data for
	// that channel from the UDP packet.  The id is stored as a key in a hash table where the value in the hash
	// table is a String array of the CloudTurbine channel names (where each CloudTurbine channel name is
	// constructed from an entry in the CSV string).
	//
	// For example, consider the following line from the configuration file:
	//
	//     17,pitch_deg,roll_deg,hding_true,hding_mag
	//
	// "17": the id; this is the key for storing in the hash table
	// "pitch_deg,roll_deg,hding_true,hding_mag": data channel list; from this channel list and the given id, we will
	//     create the following CloudTurbine channel names:
	//
	//     pitch_deg    =>   packet_017_pitch_deg.f32
	//     roll_deg     =>   packet_017_roll_deg.f32
	//     hding_true   =>   packet_017_hding_true.f32
	//     hding_mag    =>   packet_017_hding_mag.f32
	//
	// Note that only 4 channel names are provided; thus, the last 4 single-precision channels in the UDP packet
	// are ignored.  Empty channel names can also be specified in the list given in the configuration file. For
	// example, consider the following line:
	//
	//    68,LD_ratio,,cl_total,cd_total,,,,LD_etaP
	//
	// In this case, the second, fifth, sixth and seventh channels in the corresponding UDP packet will not be saved
	// to CloudTurbine.
    //
    private class HashParams {

		Hashtable<Integer,String[]> htable = new Hashtable<Integer,String[]>();

		// Constructor
    	HashParams() throws Exception {

    		// Open the X-Plane configuration file
			// OPTION 1: open file from file system
    		// fis = new FileInputStream(XPLANE_PARAMS_FILENAME);
			// OPTION 2: open file from within the JAR
			InputStream fis = getClass().getResourceAsStream("/" + XPLANE_PARAMS_FILENAME);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));

			String line = "";
    		while ((line = br.readLine()) != null) {
    			// Read line-by-line through the id/channel list file
				// What we do here is store the id code as the key in a hash table where the corresponding value is
				//     an array of CloudTurbine channel names; if there is a channel we want to ignore, the channel
				//     name entry will be a blank string
    			String[] parsed = line.split(",");
    			if(parsed.length < 2) continue;	// skip empty lines
    			int code = new Integer(parsed[0]);
    			String[] params = new String[parsed.length-1];
    			System.arraycopy(parsed, 1, params, 0, parsed.length-1);
    			String scode;								// prepend code as Source folder
    			if(code >= 100) 	scode = ""+code;
    			else if(code >= 10) scode = "0"+code;
    			else 				scode = "00"+code;
    			for(int i=0; i<params.length; i++) {
    				// Create the CloudTurbine channel name; note that we can't have multi-level channel names
					// (ie, can't have a channel name like packet_017/pitch_deg)
 					if ( (params[i] != null) && (params[i].length() > 0) ) {
 						params[i] = "packet_" + scode + "_" + params[i] + ".f32";
					}
    			}
				// System.err.println(""+code+","+Arr2List(params));
        		htable.put(code, params);
    		}

    		// Done with the file
    		br.close();

			/*
			// Here's how entries can be stored directly in the hash table rather than reading lines from the configuration file
    		htable.put(3, new String[]{"Vk_Ind","","","Vk_True"});
    		htable.put(4, new String[]{"Mach","","VVI_fpm","","Gnorm","Gaxial","Gside"});
    		htable.put(17,new String[]{"Pitch","Roll","Hdg_True","Hdg_Mag"});
    		htable.put(20,new String[]{"Lat","Lon","Alt"});
    		htable.put(37,new String[]{"RPM"});
    		htable.put(41,new String[]{"N1"});
    		htable.put(42,new String[]{"N2"});
			*/

    	} // end Constructor
    }
    //----------------------------------------------------------------------------------
    // build up comma-separated string from string-array
    static String Arr2List(String[] s) {
        StringBuilder sb = new StringBuilder();
        for (String n : s) { 
            if (sb.length() > 0) sb.append(',');
            sb.append("'").append(n).append("'");
        }
        return sb.toString();
    }
}
