
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import cycronix.ctlib.*;

/**
 * CloudTurbine demo source, performance test
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2016/02/16
 * 
 */

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

public class CTchallenge {
	static String sourceFolder = "CTchallenge";
	static long nchan = 16;					// ~1/sqrt(nchan)
	static long ncount = 360;				// number of blocks to output
	static boolean debug = false;
	static long blockDur = 10000;			// msec
	static long blocksPerSeg = 6;			// msec
	static long samprate = 500000;			// was 40KHz
	
	public static void main(String[] args) {
		if(args.length > 0) sourceFolder = args[0];
		doSource();
	}

	static void doSource() {
		try {
			String dataFolder = "CTdata" + File.separator + sourceFolder;
			
			// setup CTwriter
			CTwriter ctw = new CTwriter(dataFolder);
			//			ctw.setDebug(debug);
			ctw.setBlockMode(true, false);			// pack, no-zip 
			ctw.autoSegment(blocksPerSeg);			// segment layer
			
			int blocksize = (int)((blockDur/1000) * samprate);
			float[] data = new float[blocksize];
			for(int i=0; i<data.length; i++) data[i] = i%(int)(1*samprate);		// put something in (10Hz sawtooth)
 			ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4).order(ByteOrder.LITTLE_ENDIAN);        
	        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
	        floatBuffer.put(data);
	        
			long time = System.currentTimeMillis();
			long startTime = time;
//			CTinfo.setDebug(true);
			
			// loop and write some output
			ctw.setTime(time);		// establish initial blockTime
			for(int i=1; i<=ncount; i++) {
				ctw.setTime(time+=blockDur);			// block-interval times
				for(int j=0; j<nchan; j++) ctw.putData("c"+j+".f32", byteBuffer.array());
				ctw.flush(true);				// gapless back-to-back block times
				System.out.println("Put block: "+i);
			}
			long stopTime = System.currentTimeMillis();
			long PPS = (long)(1000.*(nchan*blocksize*ncount)/(stopTime-startTime));
			System.out.println("Total Time (ms): "+(stopTime-startTime)+", Pts/Sec: "+PPS+", PPS/Chan: "+(PPS/nchan));
		} catch(Exception e) {
			System.err.println("Exception: "+e);
			e.printStackTrace();
		} 
	}
}
