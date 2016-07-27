package cycronix.ctlib;

/**
 * Utility class for CloudTurbine debugging and file info.
 * <p>
 * @author Matt Miller (MJM), Cycronix
 * @version 2016/05/02
 * 
*/

public class CTinfo {

	private static boolean debug = false;
	
	private CTinfo() {};		// constructor
	
	//--------------------------------------------------------------------------------------------------------
	// debug methods
	
	/**
	 * Set debug mode for all CTlib methods.
	 * @param dflag boolean true/false debug mode
	 */	
	public static void setDebug(boolean setdebug) {
		debug = setdebug;
		if(debug) debugPrint("debug set true!");
	}
	
	private static String callerClassName() {
		String cname = new Exception().getStackTrace()[2].getClassName();		// 2 levels up
		cname = cname.substring(cname.lastIndexOf(".")+1);	// last part
		return cname;
	}
	
	/**
	 * Print given debug message if setDebug mode true. Automatically prepends calling class name to debug print.
	 * @param idebug over-ride (force) debug print true/false
	 * @param msg debug message
	 * @see #debugPrint(String)
	 */
	public static void debugPrint(boolean idebug, String msg) {
		if(debug || idebug) System.err.println(callerClassName()+": "+msg);
	}
	
	/**
	 * Print given debug message if setDebug mode true. Automatically prepends calling class name to debug print.
	 * @param msg debug message 
	 * @see #debugPrint(boolean, String)
	 */
	public static void debugPrint(String msg) {
		if(debug) System.err.println(callerClassName()+": "+msg);
	}
	
	/**
	 * Print given warning message. Automatically prepends calling class name to debug print.
	 * @param msg warning message 
	 */
	public static void warnPrint(String msg) {
		System.err.println(callerClassName()+": "+msg);
	}
	
	//--------------------------------------------------------------------------------------------------------
	// fileType:  return file type code based on file extension
	
	/**
	 * Return file type character code based on file suffix
	 * @param fname file name
	 * @return filetype character code
	 * @see #fileType(String,char)
	 */
	public static char fileType(String fname) {
		return fileType(fname, 's');
	}
	
	/**
	 * Return file type character code based on file suffix
	 * @param fName file name
	 * @param typeDefault default type if no built-in rule found
	 * @return filetype code
	 * @see #fileType(String)
	 */
	public static char fileType(String fName, char typeDefault) {
		
		char fType = typeDefault;		// default
		if		(fName.endsWith(".bin")) fType = 'B';
		else if	(fName.endsWith(".jpg")) fType = 'B';
		else if	(fName.endsWith(".wav")) fType = 'j';		// was 'B'
		else if	(fName.endsWith(".mp3")) fType = 'B';
		else if	(fName.endsWith(".pcm")) fType = 'j';		// FFMPEG s16sle audio
		else if	(fName.endsWith(".txt")) fType = 's';	
		else if	(fName.endsWith(".f32")) fType = 'f';
		else if	(fName.endsWith(".f64")) fType = 'F';
		else if	(fName.endsWith(".i16")) fType = 'j';		// 's' is string for compat with WebTurbine
		else if	(fName.endsWith(".i32")) fType = 'i';
		else if	(fName.endsWith(".i64")) fType = 'I';
		else if (fName.endsWith(".Num")) fType = 'N';
		else if (fName.endsWith(".num")) fType = 'n';
		return(fType);
	}
	
	/**
	 * Data word size corresponding to given file type
	 * @param ftype filetype character code
	 * @return word size (bytes per word)
	 * @see #fileType(String)
	 */
	public static int wordSize(char ftype) {
		switch(ftype) {
		case 'f':	return 4;
		case 'F':	return 8;
		case 'j':	return 2;
		case 'i':	return 4;
		case 'I':	return 8;
		default:	return 1;	
		}
	}
	
}
