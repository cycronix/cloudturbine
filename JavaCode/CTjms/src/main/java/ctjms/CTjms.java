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

package ctjms;

import org.apache.activemq.ActiveMQConnectionFactory;

import java.io.File;
import java.util.ArrayList;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import cycronix.ctlib.*;
 
/**
 * CTjms:  read CT files, 
 * send time/data as JMS message stream, 
 * read stream and (re)write CT files 
 * add some timing metrics channels
 * 
 * Copyright Cycronix
 * Matt Miller, 9/21/2017
 * 
 * Ref:  http://activemq.apache.org/hello-world.html
 */

public class CTjms {
	private static String CTsource = "CTmousetrack";
	private static String CTroot = ".";
	private static boolean shutdown = false;
	
    public static void main(String[] args) throws Exception {
        thread(new Producer(), false);
        thread(new Consumer(), false).join();		// block until consumer is done
    }
 
    public static Thread thread(Runnable runnable, boolean daemon) {
        Thread brokerThread = new Thread(runnable);
        brokerThread.setDaemon(daemon);
        brokerThread.start();
        return brokerThread;
    }
 
    //----------------------------------------------------------------------------------------
    // Producer:  read CT data, send to JMS
    
    public static class Producer implements Runnable {
        public void run() {
            try {
                // Create a ConnectionFactory
                ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");
 
                // Create a Connection
                Connection connection = connectionFactory.createConnection();
                connection.start();

                // Create a Session
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
 
                // Create the destination (Topic or Queue)
                Destination destination = session.createQueue("TEST.FOO");

                // Create a MessageProducer from the Session to the Topic or Queue
                MessageProducer producer = session.createProducer(destination);
                producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

                CTreader ctr = new CTreader(CTroot);
                ArrayList<String> chans = ctr.listChans(CTsource);
                double lastTime = 0.;

                // loop for a while reading live CT data and send to JMS
                for(int j=0; j<1000; j++) {
                	if(shutdown) break;
                	// tread water if nothing new...
                	double newestTime = ctr.newTime(CTsource);
                	if(newestTime <= lastTime) {
                		System.err.println("nothing new...");
                		Thread.sleep(100);
                		continue;
                	}
                	if(lastTime == 0) lastTime = newestTime;

                	// single request for all CT chans (time aligned response)
                	CTmap ctmap = new CTmap();
                	for(String chan:chans) ctmap.add(chan);
                	ctmap = ctr.getDataMap(ctmap, CTsource, lastTime, 10000., "absolute");	// big request past lastTime to get past end of newest data
                	
                	// first line:  csv time values for first chan
                	String csvstring = "";
                	double t[] = ctmap.firstEntry().getValue().getTime();
                	for(int i=0; i<t.length; i++) csvstring = csvstring + t[i] + ",";
                	csvstring += "\n";		// group-time on leading line
                	lastTime = t[t.length-1];
                	
                	// following lines, CSV values per channel
                	for(String chan:chans) {
                		CTdata data = ctmap.get(chan);
                		String[] dd = data.getDataAsString('s');
                		csvstring = csvstring + chan + "=";
                		for(int i=0; i<dd.length; i++) csvstring = csvstring + dd[i] + ",";		// concatenate CSV data per channel
                		csvstring += "\n";
                	}
                	
                	// add a current-time and latency channel for diagnostics
                	double now = (double)(System.currentTimeMillis())/1000.;
                	csvstring = csvstring + "T1="+now+"\n";
                	csvstring = csvstring + "L10=";
            		for(int i=0; i<t.length; i++) csvstring = csvstring + (now-t[i]) + ",";		// concatenate CSV data per channel
            		csvstring += "\n";
                	
                	// send multi-line response as message (this could be JSON format)
            		TextMessage message = session.createTextMessage(csvstring);
            		message.setJMSMessageID("CloudTurbine");
//            		System.err.println("send message: "+csvstring);
            		producer.send(message);

            		Thread.sleep(100);
                }
                // Clean up
                System.err.println("Producer shutting down!");
                producer.close();
                session.close();
                connection.close();
                shutdown = true;
            }
            catch (Exception e) {
            	System.out.println("Caught: " + e);
                e.printStackTrace();
                return;
            }
        }
    }
 
    //----------------------------------------------------------------------------------------
    // Consumer:  read JMS data, send to CT
    
    public static class Consumer implements Runnable, ExceptionListener {
        public void run() {
            try {
 
                // Create a ConnectionFactory
                ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");
 
                // Create a Connection
                Connection connection = connectionFactory.createConnection();
                connection.start();
 
                connection.setExceptionListener(this);
 
                // Create a Session
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
 
                // Create the destination (Topic or Queue)
                Destination destination = session.createQueue("TEST.FOO");

                // Create a MessageConsumer from the Session to the Topic or Queue
                MessageConsumer consumer = session.createConsumer(destination);

                // create CTwriter
                CTwriter ctw = new CTwriter(CTroot+File.separator+"CTjms");
                ctw.setBlockMode(true,true);
                
                // Wait for a messages
                Message message = null;
                do {
                	message = consumer.receive(10000);

                	if (message instanceof TextMessage) {
                		TextMessage textMessage = (TextMessage) message;
                		String text = textMessage.getText();
                		System.out.println("Received: " + text);
                		
                		// parse lines in message, first line: time, following lines: channel(s)
                		String chanparse[] = text.split("\n");
                		boolean firstLine=true;
            			String times[] = null;
            			double T1 = 0.;
                		for(String ctext:chanparse) {
                			if(firstLine) {
                				times = ctext.split(",");
                				firstLine = false;
                			}
                			else {		// channel line format: chan=1,2,3,4,...
                				String sparse[] = ctext.split("=");
                				String chan = sparse[0];
                				String values[] = sparse[1].split(",");
                				int nval = Math.min(values.length, times.length);		// ensure time,data same length
                				for(int i=0; i<nval; i++) {
                					ctw.setTime(Double.parseDouble(times[i]));
                					ctw.putData(chan,values[i]);						// write to CT
                				}
                				if(chan.equals("T1")) T1 = Double.parseDouble(values[0]);
                			}
                		}
                		
                		// save some diagnostic timing channels
                    	double T2 = (double)(System.currentTimeMillis())/1000.;
                    	ctw.setTime(T1);
                    	ctw.putData("T2", T2);
    					ctw.putData("L21",T2-T1);
        				for(int i=0; i<times.length; i++) {
        					ctw.setTime(Double.parseDouble(times[i]));
        					ctw.putData("L20",T2-Double.parseDouble(times[i]));
        				}
                		
                		ctw.flush();
                	} else {
                		System.out.println("Received non-text message: " + message);
                	}
                } while(message != null);

                System.err.println("Consumer Done!");
                consumer.close();
                session.close();
                connection.close();
                shutdown = true;
            } catch (Exception e) {
                System.out.println("Caught: " + e);
                e.printStackTrace();
            }
        }
 
        public synchronized void onException(JMSException ex) {
            System.out.println("JMS Exception occured.  Shutting down client.");
        }
    }
}