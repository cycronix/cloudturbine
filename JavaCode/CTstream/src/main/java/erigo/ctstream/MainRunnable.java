/*
Copyright 2007 Creare Inc.

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

package erigo.ctstream;
/**
  * A <code>Runnable</code> wrapper to start a class with a main() function
  *  in a separate thread.  Typically this is inovked using the synatax:
  *  <p><code>new Thread(new MainRunnable(SomeClass.class, args)).start();
  *  </code></p>
  */
  
// 02/15/2002  WHF  Created.

public class MainRunnable implements Runnable
{
	private final Object[] args=new Object[1];
	private final Class mainClass;
	private final static Class[] parameterTypes={ String[].class };
	
	/**
	  * Constructor, which takes the class which the static main method
	  *  should be called upon, and the arguments to that method.
	 *
	 *  @param mainClass The class whose main() method we will start
	 *  @param args      Command line arguments to give to the main method
	  */
	public MainRunnable(Class mainClass, String args[])
	{ this.mainClass=mainClass; this.args[0]=args; }
	
	/**
	  * Executes the main method for the supplied class.
	  */
	public void run()
	{
		try {
		mainClass.getMethod("main",parameterTypes).invoke(null,args);
		} catch (Exception e) 
		{ e.printStackTrace(); }
	}
}
