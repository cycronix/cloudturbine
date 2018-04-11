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

package cycronix.ctlib;

import java.io.File;

//---------------------------------------------------------------------------------	
// CThttp:  push files to HTTP server
// Matt Miller, Cycronix
// 02/13/2018


import java.io.IOException;

//import javax.xml.bind.DatatypeConverter;
import org.apache.commons.net.util.Base64;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
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
	private String userpass = null;
	static CloseableHttpClient httpclient = HttpClients.createDefault();
	
	//------------------------------------------------------------------------------------------------
	// constructor
	/**
	 * Constructor
	 * @param source source name
	 * @throws IOException on error
	 */
	public CThttp(String source) throws IOException {
		super(source);
		enableSelfSigned();
	}
	
	/**
	 * Constructor
	 * @param source source name
	 * @param ihost host name
	 * @throws IOException on error
	 */
	public CThttp(String source, String ihost) throws IOException {
		super(source);
		if(ihost!=null) CTwebhost = ihost;
		enableSelfSigned();
	}
	
	// following to enable self-signed SSL certificates (disable or better integrate with user/pass?)
	private void enableSelfSigned() {
		try {
		httpclient = HttpClients.custom()
	            .setSSLSocketFactory(new SSLConnectionSocketFactory(SSLContexts.custom()
	                    .loadTrustMaterial(null, new TrustSelfSignedStrategy())
	                    .build()
	                )
	            ).build();
		} catch(Exception e) {
			System.err.println("Exception on TrustSelfSigned");
		}
	}
	
	//------------------------------------------------------------------------------------------------
	/**
	 * login by providing user name, password
	 * @param user user name
	 * @param pw password
	 * @throws Exception on error
	 */
	public void login(String user, String pw) throws Exception {
		userpass = new String(Base64.encodeBase64((user + ":" + pw).getBytes()));
//		userpass = DatatypeConverter.printBase64Binary((user + ":" + pw).getBytes("UTF-8"));
	}
	
	//------------------------------------------------------------------------------------------------

	private HttpResponse httpput(String SourceChan, byte[] body) 
			throws IOException {

		String url = CTwebhost + "/" + SourceChan;
		final HttpPut put=new HttpPut(url);
		if(userpass != null) put.setHeader("Authorization", "Basic " + userpass);
		
		if (body != null) {
			CTinfo.debugPrint("PUT: "+url+", userpass: "+userpass);
			put.setEntity(new ByteArrayEntity(body));
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
			
			httpput(pathname, bdata);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
