
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

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
	// read-performance:
	static int nchan = 120;						// ~1/sqrt(nchan)
	static int ncount = 100;
	static boolean debug = false;
	static int dt = 1000;						// msec
	static int samprate = 40000;				// 40KHz
	
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
			ctw.setBlockMode(true, true);			// block data per flush
			
			int blocksize = (dt/1000) * samprate;
			int[] data = new int[blocksize];
			for(int i=0; i<data.length; i++) data[i] = i;		// put something in (sawtooth)
 			ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4).order(ByteOrder.LITTLE_ENDIAN);        
	        IntBuffer intBuffer = byteBuffer.asIntBuffer();
	        intBuffer.put(data);
	        
			long time = System.currentTimeMillis();
			long startTime = time;
			ctw.preflush(time);		// pre-flush to establish initial audio blockTime?

			// loop and write some output
			for(int i=1; i<=ncount; i++) {
//				ctw.setTime(time+=dt);	
				ctw.setTime(System.currentTimeMillis());
				for(int j=0; j<nchan; j++) ctw.putData("c"+j+".i32", byteBuffer.array());
				ctw.flush(true);
				System.out.println("put block: "+i);
			}
			long stopTime = System.currentTimeMillis();
			long PPS = (long)(1000.*(nchan*blocksize*ncount)/(stopTime-startTime));
			System.out.println("CTperf, Total Time (ms): "+(stopTime-startTime)+", Pts/Sec: "+PPS+", PPS/Chan: "+(PPS/nchan));
		} catch(Exception e) {
			System.err.println("CTperf exception: "+e);
			e.printStackTrace();
		} 
	}
}
