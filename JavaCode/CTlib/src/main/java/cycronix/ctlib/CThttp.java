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
package cycronix.ctlib;

import java.io.File;

//---------------------------------------------------------------------------------	
// CThttp:  push files to HTTP server
// Matt Miller, Cycronix
// 02/13/2018


import java.io.IOException;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;


/**
 * CloudTurbine utility class that extends CTwriter class to write via HTTP PUT versus local filesystem
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2018/02/13
 * 
*/

public class CThttp extends CTwriter {
	
	public String CTwebhost = "http://localhost:8000";
	
	//------------------------------------------------------------------------------------------------
	// constructor

	public CThttp(String dstFolder) throws IOException {
		super(dstFolder);
	}
	
	public CThttp(String dstFolder, String ihost) throws IOException {
		super(dstFolder);
		CTwebhost = ihost;
	}
	
	//------------------------------------------------------------------------------------------------

	static CloseableHttpClient httpclient = HttpClients.createDefault();
	private HttpResponse httpput(String SourceChan, byte[] body) 
			throws IOException {

		String url = CTwebhost + "/" + SourceChan;
		final HttpPut put=new HttpPut(url);
		if (body != null) {
			put.setEntity(new ByteArrayEntity(body));
			//			System.err.println("CThttp PUT: "+url);
		}
		return httpclient.execute(put);
	}

	//------------------------------------------------------------------------------------------------
	// over-ride CTwriter method to replace file-writes with HTTP PUT
	protected void writeToStream(String pathname, byte[] bdata) throws IOException {
		try { 
			if(!File.separator.equals("/")) {		// replace Windows back-slash with slash for hopefully universal HTTP syntax
				pathname = pathname.replace(File.separator.charAt(0), '/');
			}
			
//			System.err.println("CThttp, pathname: "+pathname);
			httpput(pathname, bdata);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
