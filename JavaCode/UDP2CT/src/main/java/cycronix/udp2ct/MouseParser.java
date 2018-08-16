/*
Copyright 2018 Erigo Technologies

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
 * MouseParser
 *
 * Parse the incoming UDP data packet containing mouse position and write it out in CT/Unity format.
 *
 * Packet format:
 * --------------
 * 5 byte header, "MOUSE"
 * 8 byte long containing millisecond epoch timestamp
 * 2 x 4 byte floats containing x,y position
 *
 * John Wilson, Erigo Technologies
 * 8/2018
 */

package cycronix.udp2ct;

import cycronix.ctlib.CTwriter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class MouseParser implements UnityPlayer {

	private String CSV = "";
	private CTwriter ctw = null;
    private double time_msec = 0;
	private boolean bSavePacketDataToCT = false;
    private boolean bPrintDebug = false;

    // Store data for later processing by the calling class
    private float xpos = -999.0f;
	private float ypos = -999.0f;

	//---------------------------------------------------------------------------------------------
	// constructor
	public MouseParser(CTwriter ctwI, double time_msecI, byte[] packetBytes, boolean bSavePacketDataToCTI, boolean bPrintDebugI) throws Exception {

		ctw = ctwI;
		bSavePacketDataToCT = bSavePacketDataToCTI;
		time_msec = time_msecI;
		bPrintDebug = bPrintDebugI;

		// System.err.println("packet:\n" + UDP2CT.bytesToHex(packetBytes));

		ByteBuffer packetBB = ByteBuffer.wrap(packetBytes);
		packetBB = packetBB.order(ByteOrder.BIG_ENDIAN);

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
			System.err.println("Mouse position packet: bytes: " + packetBytes.length + ", t: " + time_msecI);
			//
			// Initialize CSV string
			//
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS z");
			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
			String dateStr = sdf.format(new Date((long) (time_msec)));
			CSV = new String("MOUSE," + dateStr);
			//
			// Read data
			//
			long time_from_packet = packetBB.getLong();
			// Add to the CSV string
			CSV += "," + time_from_packet;
			// x,y position from the data packet is normalized
			// Expand location so that and x or y position 0 maps to -1*GAME_FIELD_EXTENT
			// and and x or y position 1 maps to +1*GAME_FIELD_EXTENT
			float GAME_FIELD_EXTENT = 9.75f;
			float xpos_raw = packetBB.getFloat();
			// Add to the CSV string
			CSV += "," + String.format("%.4f", xpos_raw);
			xpos = xpos_raw * 2.0f*GAME_FIELD_EXTENT - GAME_FIELD_EXTENT;
			if (xpos < -1f * GAME_FIELD_EXTENT) {
				// On dual-monitor systems, pos will end up going -1 to +1
				// cut off -1 to 0 so that it stays at -1*GAME_FIELD_EXTENT
				xpos = -1f * GAME_FIELD_EXTENT;
			}
			float ypos_raw = packetBB.getFloat();
			// Add to the CSV string
			CSV += "," + String.format("%.4f", ypos_raw);
			ypos = ypos_raw * 2.0f*GAME_FIELD_EXTENT - GAME_FIELD_EXTENT;
			//
			// Save extracted data to CT if requested
			//
			if (bSavePacketDataToCT && (ctw != null)) {
				ctw.putData("time.i64", time_from_packet);
				ctw.putData("x.f32", xpos_raw);
				ctw.putData("y.f32", ypos_raw);
				ctw.putData("data.txt", CSV);
			}
		} catch (Exception e) {
			System.err.println("Error parsing Mouse position packet: " + e);
		}

    }
    
	//------------------------------------------------------------------------------------------------------
	//
	// Create a string for the Mouse position to participate in CTrollaball.
	//
	// This method implements the required method defined in interface UnityPlayer.
	//
	public String createUnityString(UDP2CT udp2ctI) {
		double time_sec = time_msec / 1000.0;
		return udp2ctI.createUnityString(time_sec,xpos,0.0f,ypos,0.0f,0.0f,0.0f);
	}

}
