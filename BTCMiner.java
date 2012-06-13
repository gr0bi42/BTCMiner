/*!
   BTCMiner -- BTCMiner for ZTEX USB-FPGA Modules
   Copyright (C) 2011 ZTEX GmbH
   http://www.ztex.de

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License version 3 as
   published by the Free Software Foundation.

   This program is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
   General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, see http://www.gnu.org/licenses/.
!*/

/* TODO: 
 * HUP signal
*/ 
 

import java.io.*;
import java.util.*;
import java.net.*;
import java.security.*;
import java.text.*;
import java.util.zip.*;

import ch.ntb.usb.*;

import ztex.*;

// *****************************************************************************
// ******* ParameterException **************************************************
// *****************************************************************************
// Exception the prints a help message
class ParameterException extends Exception {
    public final static String helpMsg = new String (
		"Parameters:\n"+
		"    -host <string>    Host URL (default: http://127.0.0.1:8332)\n" +
		"    -u <string>       RPC User name\n" + 
		"    -p <string>       RPC Password\n" + 
		"    -b <url> <user name> <password> \n" + 
		"                      URL, user name and password of a backup server. Can be specified multiple times. \n"+
		"    -lp <url> <user name> <password> \n" + 
		"                      URL, user name and password of a long polling server (determined automatically by default) \n"+
		"    -l <log file>     Log file (default: BTCMiner.log) \n" +
		"    -lb <log file>    Log of submitted blocks file \n" +
		"    -m s|t|p|c        Set single mode, test mode, programming mode or cluster mode\n"+
		"                      Single mode: runs BTCMiner on a single board (default mode)\n" +
		"                      Test mode: tests a board using some test data\n" +
		"                      Programming mode: programs device with the given firmware\n" +
		"                      Cluster mode: runs BTCMiner on all programmed boards\n" +
		"    -ep0              Always use slow EP0 for Bitstream transfer\n" +
		"    -oh               Overheat threshold: if the hash rate drops by that factor (but at least two frequency steps)\n"+
		"                      the overheat shutdown is triggered (default: 0.04, recommended: 0 to 0.08) \n"+
		"    -ps <string>      Select devices with the given serial number,\n" +
		"                      in cluster mode: select devices which serial number starts with the given string\n" +
		"    -v                Be verbose\n" +
		"    -h                This help\n" +
		"Parameters in single mode, test mode and programming mode\n"+
		"    -d <number>       Device Number, see -i\n" +
		"    -f <ihx file>     Firmware file (required in programming mode)\n" + 
		"    -i                Print bus info\n" +
		"Parameters in cluster mode\n"+
		"    -n <number>       Maximum amount of devices per thread (default: 10)\n"+
		"Parameters in programming mode\n"+
		"    -pt <string>      Program devices of the given type\n" + 
		"                      If neither -ps nor -ps is given, only unconfigured devices are programmed\n" +
		"    -s                Set serial number\n" +
		"    -rf               Erase firmware in EEPROM (overwrites -f, requires -pt or -ps)\n"
	);
		
    
    public ParameterException (String msg) {
	super( msg + "\n" + helpMsg );
    }
}

/* *****************************************************************************
   ******* ParserException *****************************************************
   ***************************************************************************** */   
class ParserException extends Exception {
    public ParserException(String msg ) {
	super( msg );
    }
}    

/* *****************************************************************************
   ******* FirmwareException ***************************************************
   ***************************************************************************** */   
class FirmwareException extends Exception {
    public FirmwareException(String msg ) {
	super( msg );
    }
}    


// *****************************************************************************
// ******* MsgObj *************************************************************
// *****************************************************************************
interface MsgObj {
    public void msg(String s);
}
    

// *****************************************************************************
// ******* NewBlockMonitor *****************************************************
// *****************************************************************************
class NewBlockMonitor extends Thread implements MsgObj {
    public int newCount = -1;
    
    public boolean running;
    
    private static final int minLongPollInterval = 250; // in ms

    private byte[] prevBlock = new byte[32];
    private byte[] dataBuf = new byte[128];
    
    private Vector<LogString> logBuf = new Vector<LogString>();
    
// ******* constructor *********************************************************
    public NewBlockMonitor( ) {
	start();
    }

// ******* checkNew ***********************************************************Ü
    synchronized public boolean checkNew ( byte[] data ) throws NumberFormatException {
	if ( data.length < 36 )
	    throw new NumberFormatException("Invalid length of data");

	boolean n = false;

	for ( int i=0; i<32; i++ ) {
	    n = n | ( data[i+4] != prevBlock[i] );
	    prevBlock[i] = data[i+4];
	}
	if ( n ) {
	    newCount += 1;
	    if ( newCount > 0 )
		msg("New block detected by block monitor");
	}
	    
	return n;
    }

// ******* run *****************************************************************
    public void run () {
	running = true;
	
	boolean enableLP = true;
	boolean warnings = true;
	long enableLPTime = 0;

	while ( running ) {
	    long t = new Date().getTime();
	    
	    if ( BTCMiner.longPollURL!=null && enableLP && t>enableLPTime) {
		try {
//		    msg("info: LP");
		    BTCMiner.hexStrToData(BTCMiner.jsonParse(BTCMiner.bitcoinRequest(this, BTCMiner.longPollURL, BTCMiner.longPollUser, BTCMiner.longPollPassw, "getwork", ""), "data"), dataBuf);
		    for ( int i=0; i<32; i++ ) {
			prevBlock[i] = dataBuf[i+4];
		    }
		    newCount += 1;
		    msg("New block detected by long polling");
		}
		catch ( MalformedURLException e ) {
		    msg("Warning: " + e.getLocalizedMessage() + ": disabling long polling");
		    enableLP = false;
		}
		catch ( IOException e ) {
		    if ( new Date().getTime() < t+500 ) {
			msg("Warning: " + e.getLocalizedMessage() + ": disabling long polling fo 60s");
			enableLPTime = new Date().getTime() + 60000;
		    }
		}
		catch ( Exception e ) {
		    if ( warnings )
			msg("Warning: " + e.getLocalizedMessage());
		    warnings = false;
		}
	    }
	    
	    if ( BTCMiner.longPollURL==null )
		enableLPTime = new Date().getTime() + 2000;
	    
	    t += minLongPollInterval - new Date().getTime();
	    if ( t > 5 ) {
		try {
		    Thread.sleep( t );
		}
		catch ( InterruptedException e) {
		}	 
	    }
	}
	
//	System.out.println("Stopping block monitor"); 
    }

// ******* msg *****************************************************************
    public void msg(String s) {
	synchronized ( logBuf ) {
	    logBuf.add( new LogString( s ) );
	}
    }

// ******* print ***************************************************************
    public void print () {
	synchronized ( logBuf ) {
	    for ( int j=0; j<logBuf.size(); j++ ) {
	        LogString ls = logBuf.elementAt(j);
	        System.out.println( ls.msg );
		if ( BTCMiner.logFile != null ) {
		    BTCMiner.logFile.println( BTCMiner.dateFormat.format(ls.time) + ": " + ls.msg );
		}
	    }
	    logBuf.clear();
	}
    }

}

// *****************************************************************************
// ******* BTCMinerThread ******************************************************
// *****************************************************************************
class BTCMinerThread extends Thread {
    private Vector<BTCMiner> miners = new Vector<BTCMiner>();
    private String busName;
    private PollLoop pollLoop = null;
    
// ******* constructor *********************************************************
    public BTCMinerThread( String bn ) {
	busName = bn;
    }

// ******* add *****************************************************************
    public void add ( BTCMiner m ) {
	synchronized ( miners ) {
	    miners.add ( m );
	    m.name = busName + ": " + m.name;
	}

	if ( pollLoop==null ) {
	    BTCMiner.printMsg("Starting mining thread for bus " + busName);
	    start();
	}
    }

// ******* size ****************************************************************
    public int size () {
	return miners.size();
    }

// ******* elementAt ***********************************************************
    public BTCMiner elementAt ( int i ) {
	return miners.elementAt(i);
    }

// ******* find ****************************************************************
    public BTCMiner find ( int dn ) {
	for (int i=0; i<miners.size(); i++ ) {
	    if ( (miners.elementAt(i).ztex().dev().dev().getDevnum() == dn) )
		return miners.elementAt(i);
	}
	return null;
    }

// ******* busName *************************************************************
    public String busName () {
	return busName;
    }

// ******* running *************************************************************
    public boolean running () {
	return pollLoop != null;
    }

// ******* run *****************************************************************
    public void run () {
	pollLoop = new PollLoop(miners);
	pollLoop.run();
	pollLoop = null;
    }

// ******* printInfo ************************************************************
    public void printInfo ( ) {
	if ( pollLoop != null )
	    pollLoop.printInfo( busName );
    }
}


// *****************************************************************************
// ******* BTCMinerCluster *****************************************************
// *****************************************************************************
class BTCMinerCluster {
    public static int maxDevicesPerThread = 10;

    private Vector<BTCMinerThread> threads = new Vector<BTCMinerThread>();
    private Vector<BTCMiner> allMiners = new Vector<BTCMiner>();

// ******* constructor **************************************************************
    public BTCMinerCluster( boolean verbose ) {
	final long infoInterval = 300000;
    
	scan( verbose );
	
	long nextInfoTime = new Date().getTime() + 60000;
	
	boolean quit = false;
	while ( threads.size()>0 && !quit) {

	    try {
		Thread.sleep( 300 );
	    }
	    catch ( InterruptedException e) {
	    }
	    
	    BTCMiner.newBlockMonitor.print();
	    for (int i=0; i<allMiners.size(); i++) 
		allMiners.elementAt(i).print();
		
	    if ( new Date().getTime() > nextInfoTime ) {
		double d = 0.0;
		double e = 0.0;
		for ( int i=0; i<allMiners.size(); i++ ) {
		    BTCMiner m = allMiners.elementAt(i);
		    m.printInfo( true );
		    d+=m.submittedHashRate();
		    e+=m.totalHashRate();
		}
		for ( int i=0; i<threads.size(); i++ )
		    threads.elementAt(i).printInfo();
		
		BTCMiner.printMsg("Total hash rate: " + String.format("%.1f",  e ) + " MH/s");
		BTCMiner.printMsg("Total submitted hash rate: " + String.format("%.1f",  d ) + " MH/s");
		BTCMiner.printMsg(" -------- ");
		nextInfoTime = new Date().getTime() + infoInterval;
	    }
		
	    for (int i=threads.size()-1; i>=0; i--) {
		BTCMinerThread t = threads.elementAt(i);
		if ( !t.running() ) {
		    BTCMiner.printMsg( "Stopped thread for bus " + t.busName() );
    		    threads.removeElementAt(i);
    		}
	    }
	    
	    try {
		StringBuffer sb = new StringBuffer();
		while ( System.in.available() > 0 ) {
		    int j = System.in.read();
		    if (j>32) 
			sb.append((char) j);
		}
		String cmd = sb.toString();
		if (cmd.length()<1) {}
		else if (cmd.equalsIgnoreCase("q") || cmd.equalsIgnoreCase("quit") ) {
		    for (int i=allMiners.size()-1; i>=0; i--) {
			allMiners.elementAt(i).suspend();
			try {
			    Thread.sleep( 10 );
			}
			catch ( InterruptedException e) {
			}	 
		    }
		    quit=true;
		}
		else if (cmd.equalsIgnoreCase("r") || cmd.equalsIgnoreCase("rescan") ) {
		    scan( verbose );
		}
		else if (cmd.equalsIgnoreCase("s") || cmd.equalsIgnoreCase("suspend") ) {
		    long t = new Date().getTime();
		    int j=0;
		    for (int i=allMiners.size()-1; i>=0; i--) {
			if ( allMiners.elementAt(i).suspend() ) j++;
			allMiners.elementAt(i).startTimeAdjust = t;
			try {
			    Thread.sleep( 10 );
			}
			catch ( InterruptedException e) {
			}	 
		    }
		    BTCMiner.printMsg("Suspended " + j + " of " + allMiners.size() + " miners. Enter `r' to resume.");
		}
		else if (cmd.equalsIgnoreCase("c") || cmd.equalsIgnoreCase("counter_reset") ) {
		    for (int i=allMiners.size()-1; i>=0; i--) {
			allMiners.elementAt(i).resetCounters();
		    BTCMiner.printMsg("Reset all performance end error counters.");
		    }
		}
		else if (cmd.equalsIgnoreCase("h") || cmd.equalsIgnoreCase("help") ) {
		    System.out.println("q(uit)		 Exit BTCMiner");
		    System.out.println("h(elp)	         Print theis help");
		    System.out.println("r(escan)         Rescan bus");
		    System.out.println("c(ounter_reset)  Reset performance and error counters");
		    System.out.println("s(uspend)        Suspend cluster");
		}
		else System.out.println("Invalid command: `"+cmd+"'");
		    
	    }
	    catch ( Exception e ) {
	    }

	}
	
//	BTCMiner.newBlockMonitor.running = false;
    }
    
// ******* add *****************************************************************
    private void add ( BTCMiner m ) {
	int i=0, j=0;
	String bn = m.ztex().dev().dev().getBus().getDirname() + "-" + j;
	while ( i<threads.size() ) {
	    BTCMinerThread t = threads.elementAt(i);
	    if ( bn.equalsIgnoreCase(threads.elementAt(i).busName()) ) {
		if ( t.size() < maxDevicesPerThread )
		    break;
		j++;
		i=0;
		bn = m.ztex().dev().dev().getBus().getDirname() + "-" + j;
	    }
	    else {
		i++;
	    }
	}

	if ( i >= threads.size() )
	    threads.add( new BTCMinerThread(bn) );
	threads.elementAt(i).add(m);
    }

// ******* find ****************************************************************
    private BTCMiner find ( ZtexDevice1 dev ) {
	int dn = dev.dev().getDevnum();
	String bn = dev.dev().getBus().getDirname();
	for ( int i=threads.size()-1; i>=0; i-- )  {
	    BTCMiner m = threads.elementAt(i).find(dn);
	    if (  m != null && bn.equals(m.ztex().dev().dev().getBus().getDirname()) )
		return m;
	}
	return null;
    }

// ******* insertIntoAllMiners *************************************************
    private void insertIntoAllMiners ( BTCMiner m ) {
	int j = 0;
	while ( j<allMiners.size() && m.name.compareTo(allMiners.elementAt(j).name)>=0 )
	    j++;
	allMiners.insertElementAt(m, j);
    }

// ******* scan ****************************************************************
    private void scan ( boolean verbose ) {
	long t = new Date().getTime();

	allMiners.clear();
	for ( int i = threads.size()-1; i>=0; i-- )  {
	    BTCMinerThread mt = threads.elementAt(i);
	    for (int j=mt.size()-1; j>=0; j-- ) {
		BTCMiner m = mt.elementAt(j);
		insertIntoAllMiners(m);
		if ( m.suspended ) {
		    m.suspended = false;
		    m.isRunning = false;
		    try {
			Thread.sleep( 20 );
		    }
		    catch ( InterruptedException e) {
		    }	 
		    BTCMiner.printMsg(m.name + ": resuming");
		}
		else {
		    m.startTimeAdjust = t;
		    BTCMiner.printMsg(m.name + ": already running");
		}
	    }
	}

	BTCMiner.printMsg("\n(Re)Scanning bus ... ");

	PollLoop.scanMode = true;

	ZtexScanBus1 bus = new ZtexScanBus1( ZtexDevice1.ztexVendorId, ZtexDevice1.ztexProductId, false, false, 1,  null, 10, 0, 1, 0 );
	int k = 0;
	int l = 0;
	for (int i=0; i<bus.numberOfDevices(); i++ ) {
	    try {
		ZtexDevice1 dev = bus.device(i);
		if ( dev.productId(0)!=10 || dev.productId(2)>1 )
		    break;
		
		if ( BTCMiner.filterSN == null || dev.snString().substring(0,BTCMiner.filterSN.length()).equals(BTCMiner.filterSN) ) {
		    k += 1;
		    BTCMiner m = find( dev );
		    if ( m == null ) {
			l += 1;
			m = new BTCMiner ( dev, null, verbose );
			m.clusterMode = true;
			add( m );
			BTCMiner.printMsg(m.name + ": added");
			insertIntoAllMiners(m);

			for ( int j=1; j<m.numberOfFpgas(); j++ ) {
			    BTCMiner n = new BTCMiner( m.ztex(), m.fpgaNum(j), verbose );
			    n.clusterMode = true;
			    add( n );
			    BTCMiner.printMsg(n.name + ": added");
			    insertIntoAllMiners(n);
			}
		    }
    		}
    	    }
	    catch ( Exception e ) {
		BTCMiner.printMsg( "Error: "+e.getLocalizedMessage() );
	    }
	}

	if ( k == 0 ) {
	    System.err.println("No devices found. At least one device has to be connected.");
	    System.exit(0);
	} 
	BTCMiner.printMsg("" + l + " new devices found.");

	t = new Date().getTime();
	for (int i=0; i<allMiners.size(); i++ ) 
	    allMiners.elementAt(i).startTime+= t-allMiners.elementAt(i).startTimeAdjust;
	
	PollLoop.scanMode = false;
	
	BTCMiner.printMsg("\nSummary: ");
	for (int i=0; i<threads.size(); i++ )
	    BTCMiner.printMsg("  Bus " + threads.elementAt(i).busName() + "\t: " + threads.elementAt(i).size() + " miners");
	BTCMiner.printMsg("  Total  \t: " + allMiners.size() + " miners\n");
	BTCMiner.printMsg("\nDisconnect all devices or enter `q' for exit. Enter `h' for help.\n");
	
	BTCMiner.connectionEffort = 1.0 + Math.exp( (1.0 - Math.sqrt(Math.min(allMiners.size(),maxDevicesPerThread)*allMiners.size())) / 13.0 );
//	System.out.println( BTCMiner.connectionEffort );

    }
}


// *****************************************************************************
// ******* LogString ***********************************************************
// *****************************************************************************
class LogString {
    public Date time;
    public String msg;
    
    public LogString(String s) {
	time = new Date();
	msg = s;
    }
}


// *****************************************************************************
// ******* PollLoop ************************************************************
// *****************************************************************************
class PollLoop {
    public static boolean scanMode = false;
    
    private double usbTime = 0.0;
    private double networkTime = 0.0;
    private double timeW = 1e-6;
    private Vector<BTCMiner> v;
    public static final long minQueryInterval = 250;

// ******* constructor *********************************************************
    public PollLoop ( Vector<BTCMiner> pv ) {
	v = pv;
    }
	
// ******* run *****************************************************************
    public void run ( ) {
	int maxIoErrorCount = (int) Math.round( (BTCMiner.rpcCount > 1 ? 2 : 4)*BTCMiner.connectionEffort );
	int ioDisableTime = BTCMiner.rpcCount > 1 ? 60 : 30;
	
	while ( v.size()>0 ) {
	    long t0 = new Date().getTime();
	    long tu = 0;

	    if ( ! scanMode ) {
		synchronized ( v ) {
		    for ( int i=v.size()-1; i>=0; i-- ) {
			BTCMiner m = v.elementAt(i);
			
			m.usbTime = 0;
			
			try { 
			    if ( ! m.suspended ) {
				if ( m.checkUpdate() && m.getWork() ) { // getwork calls getNonces
			    	    m.dmsg("Got new work");
			    	    m.sendData();
				}
				else {
			    	    m.getNonces();
				}
				m.updateFreq();
				m.printInfo(false);
			    }
			}
			catch ( IOException e ) {
			    m.ioErrorCount[m.rpcNum]++;
			    if ( m.ioErrorCount[m.rpcNum] >= maxIoErrorCount ) {
    			        m.msg("Error: "+e.getLocalizedMessage() +": Disabling URL " + m.rpcurl[m.rpcNum] + " for " + ioDisableTime + "s");
    			        m.disableTime[m.rpcNum] = new Date().getTime() + ioDisableTime*1000;
    			        m.ioErrorCount[m.rpcNum] = 0;
			    }
    			}
			catch ( ParserException e ) {
    			    m.msg("Error: "+e.getLocalizedMessage() +": Disabling URL " + m.rpcurl[m.rpcNum] + " for 60s");
    			    m.disableTime[m.rpcNum] = new Date().getTime() + 60000;
    			}
			catch ( Exception e ) {
    			    m.msg("Error: "+e.getLocalizedMessage()+": Disabling device");
    			    m.fatalError = "Error: "+e.getLocalizedMessage()+": Device disabled since " + BTCMiner.dateFormat.format( new Date() );
    			    v.removeElementAt(i);
			}

    			tu += m.usbTime;
    			
    			if ( ! m.clusterMode ) {
    			    BTCMiner.newBlockMonitor.print();
    			}
    		    }
		}

		t0 = new Date().getTime() - t0;
		usbTime = usbTime * 0.9998 + tu;
		networkTime = networkTime * 0.9998 + t0 - tu;
		timeW = timeW * 0.9998 + 1;
	    }
	    else {
		t0 = 0;
	    }
	    
	    t0 = minQueryInterval - t0;
	    if ( t0 > 5 ) {
		try {
		    Thread.sleep( t0 );
		}
		catch ( InterruptedException e) {
		}	 
	    }
	}
    }

// ******* printInfo ***********************************************************
    public void printInfo( String name ) {
	int oc = 0;
	double gt=0.0, gtw=0.0, st=0.0, stw=0.0;
	for ( int i=v.size()-1; i>=0; i-- ) {
	    BTCMiner m = v.elementAt(i);
	    oc += m.overflowCount;
	    m.overflowCount = 0;
	    
	    st += m.submitTime;
	    stw += m.submitTimeW;
	    
	    gt += m.getTime;
	    gtw += m.getTimeW;
	}
	    
	BTCMiner.printMsg(name + ": poll loop time: " + Math.round((usbTime+networkTime)/timeW) + "ms (USB: " + Math.round(usbTime/timeW) + "ms network: " + Math.round(networkTime/timeW) + "ms)   getwork time: " 
		+  Math.round(gt/gtw) + "ms  submit time: " +  Math.round(st/stw) + "ms" );
	if ( oc > 0 )
	    BTCMiner.printMsg( name + ": Warning: " + oc + " overflows occured. This is usually caused by a slow network connection." );
    }
}

// *****************************************************************************
// *****************************************************************************
// ******* BTCMiner ************************************************************
// *****************************************************************************
// *****************************************************************************
class BTCMiner implements MsgObj  {

// *****************************************************************************
// ******* static methods ******************************************************
// *****************************************************************************
    static final int maxRpcCount = 32;
    static String[] rpcurl = new String[maxRpcCount];
    static String[] rpcuser = new String[maxRpcCount];
    static String[] rpcpassw = new String[maxRpcCount];
    static int rpcCount = 1;

    static String longPollURL = null;
    static String longPollUser = "";
    static String longPollPassw = "";
    
    static int bcid = -1;

    static String firmwareFile = null;
    static boolean printBus = false;

    public final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    static PrintStream logFile = null;
    static PrintStream blkLogFile = null;
    
    static double connectionEffort = 2.0;
    
    static NewBlockMonitor newBlockMonitor = null;
    
    static boolean forceEP0Config = false;
    
    static double overheatThreshold = 0.04;
    
    static String filterSN = null;

    public static final String[] dummyFirmwareNames = {
	"USB-FPGA Module 1.15d (default)" ,
	"USB-FPGA Module 1.15x (default)" ,
	"USB-FPGA Module 1.15y (default)"
    };

    public static final int[] defaultFirmwarePID1 = {
	13 ,
	13 ,
	15
    };

    public static final String[] firmwareFiles = {
	"ztex_ufm1_15d4.ihx" ,
	"ztex_ufm1_15d4.ihx" ,
	"ztex_ufm1_15y1.ihx" 
    };
    
    
// ******* printMsg *************************************************************
    public static void printMsg ( String msg ) {
	System.out.println( msg );
	if ( logFile != null )
	    logFile.println( dateFormat.format( new Date() ) + ": " + msg );
    }

// ******* encodeBase64 *********************************************************
    public static String encodeBase64(String s) {
        return encodeBase64(s.getBytes());
    }

    public static String encodeBase64(byte[] src) {
	return encodeBase64(src, 0, src.length);
    }

    public static String encodeBase64(byte[] src, int start, int length) {
        final String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
	byte[] encodeData = new byte[64];
        byte[] dst = new byte[(length+2)/3 * 4 + length/72];
        int x = 0;
        int dstIndex = 0;
        int state = 0;
        int old = 0;
        int len = 0;
	int max = length + start;

	for (int i = 0; i<64; i++) {
	    byte c = (byte) charSet.charAt(i);
	    encodeData[i] = c;
	}
	
        for (int srcIndex = start; srcIndex<max; srcIndex++) {
	    x = src[srcIndex];
	    switch (++state) {
	    case 1:
	        dst[dstIndex++] = encodeData[(x>>2) & 0x3f];
		break;
	    case 2:
	        dst[dstIndex++] = encodeData[((old<<4)&0x30) 
	            | ((x>>4)&0xf)];
		break;
	    case 3:
	        dst[dstIndex++] = encodeData[((old<<2)&0x3C) 
	            | ((x>>6)&0x3)];
		dst[dstIndex++] = encodeData[x&0x3F];
		state = 0;
		break;
	    }
	    old = x;
	    if (++len >= 72) {
	    	dst[dstIndex++] = (byte) '\n';
	    	len = 0;
	    }
	}

	switch (state) {
	case 1: dst[dstIndex++] = encodeData[(old<<4) & 0x30];
	   dst[dstIndex++] = (byte) '=';
	   dst[dstIndex++] = (byte) '=';
	   break;
	case 2: dst[dstIndex++] = encodeData[(old<<2) & 0x3c];
	   dst[dstIndex++] = (byte) '=';
	   break;
	}
	return new String(dst);
    }

// ******* hexStrToData ********************************************************
    public static byte[] hexStrToData( String str ) throws NumberFormatException {
	if ( str.length() % 2 != 0 ) 
	    throw new NumberFormatException("Invalid length of string");
	byte[] buf = new byte[str.length() >> 1];
	for ( int i=0; i<buf.length; i++) {
	    buf[i] = (byte) Integer.parseInt( str.substring(i*2,i*2+2), 16);
	}
	return buf;
    }

    public static void hexStrToData( String str, byte[] buf ) throws NumberFormatException {
	if ( str.length()<buf.length*2 ) 
	    throw new NumberFormatException("Invalid length of string");
	for ( int i=0; i<buf.length; i++) {
	    buf[i] = (byte) Integer.parseInt( str.substring(i*2,i*2+2), 16);
	}
    }

// ******* dataToHexStr ********************************************************
    public static String dataToHexStr (byte[] data)  {
	final char hexchars[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
	char[] buf = new char[data.length*2];
	for ( int i=0; i<data.length; i++) {
	    buf[i*2+0] = hexchars[(data[i] & 255) >> 4];
	    buf[i*2+1] = hexchars[(data[i] & 15)];
	}
	return new String(buf);
    }

// ******* dataToInt **********************************************************
    public static int dataToInt (byte[] buf, int offs)  {
	if ( offs + 4 > buf.length )
	    throw new NumberFormatException("Invalid length of data");
	return (buf[offs+0] & 255) | ((buf[offs+1] & 255)<<8) | ((buf[offs+2] & 255)<<16) | ((buf[offs+3] & 255)<<24);
    }

// ******* intToData **********************************************************
    public static byte[] intToData (int n)  {
	byte[] buf = new byte[4];
	buf[0] = (byte) (n & 255);
	buf[1] = (byte) ((n >> 8) & 255);
	buf[2] = (byte) ((n >> 16) & 255);
	buf[3] = (byte) ((n >> 24) & 255);
	return buf;
    }

// ******* intToHexStr ********************************************************
    public static String intToHexStr (int n)  {
	return dataToHexStr( reverse( intToData ( n ) ) );
    }

// ******* reverse ************************************************************
    public static byte[] reverse (byte[] data)  {
	byte[] buf = new byte[data.length];
	for ( int i=0; i<data.length; i++) 
	    buf[data.length-i-1] = data[i];
	return buf;
    }

// ******* jsonParse ***********************************************************
// does not work if parameter name is a part of a parameter value
    public static String jsonParse (String response, String parameter) throws ParserException {
	int lp = parameter.length();
	int i = 0;
	while ( i+lp<response.length() && !parameter.equalsIgnoreCase(response.substring(i,i+lp)) )
	    i++;
	i+=lp;
	if ( i>=response.length() )
	    throw new ParserException( "jsonParse: Parameter `"+parameter+"' not found" );
	while ( i<response.length() && response.charAt(i) != ':' )
	    i++;
	i+=1;
	while ( i<response.length() && (byte)response.charAt(i) <= 32 )
	    i++;
	if ( i>=response.length() )
	    throw new ParserException( "jsonParse: Value expected after `"+parameter+"'" );
	int j=i;
	if ( i<response.length() && response.charAt(i)=='"' ) {
	    i+=1;
	    j=i;
	    while ( j<response.length() && response.charAt(j) != '"' )
		j++;
	    if ( j>=response.length() )
		throw new ParserException( "jsonParse: No closing `\"' found for value of paramter `"+parameter+"'" );
	}
	else { 
	    while ( j<response.length() && response.charAt(j) != ',' && response.charAt(j) != /*{*/'}'  ) 
		j++;
	}
	return response.substring(i,j);
    } 


// ******* checkSnString *******************************************************
// make sure that snString is 10 chars long
    public static String checkSnString ( String snString ) {
    	if ( snString.length()>10 ) {
    	    snString = snString.substring(0,10);
	    System.err.println( "Serial number too long (max. 10 characters), truncated to `" + snString + "'" );
	}
	while ( snString.length()<10 )
	    snString = '0' + snString;
	return snString;
    }


// ******* getType *************************************************************
    private static String getType ( ZtexDevice1 pDev ) {
	byte[] buf = new byte[64];
	try {
	    Ztex1v1 ztex = new Ztex1v1 ( pDev );
    	    ztex.vendorRequest2( 0x82, "Read descriptor", 0, 0, buf, 64 );
    	    if ( buf[0] < 1 || buf[0] > 5 ) 
    		throw new FirmwareException("Invalid BTCMiner descriptor version");

	    int i0 = buf[0] > 4 ? 11 : ( buf[0] > 2 ? 10 : 8 );
    	    int i = i0;
    	    while ( i<64 && buf[i]!=0 )
    		i++;
    	    if ( i < i0+1 )
    		throw new FirmwareException("Invalid bitstream file name");

    	    return new String(buf, i0, i-i0);
    	}
    	catch ( Exception e ) {
	    System.out.println("Warning: "+e.getLocalizedMessage() );
	}
	return null;
    }


// *****************************************************************************
// ******* non-static methods **************************************************
// *****************************************************************************
    private Ztex1v1 ztex = null;
    private int fpgaNum = 0;
    
    public int numNonces, offsNonces, freqM, freqMDefault, freqMaxM, extraSolutions;
    public double freqM1;
    public double hashesPerClock;
    private String bitFileName = null;
    public String name;
    public String fatalError = null;
    private boolean suspendSupported = false;

    public int ioErrorCount[] = new int[maxRpcCount];
    public long disableTime[] = new long[maxRpcCount];
        
    public int rpcNum = 0;
    private int prevRpcNum = 0;
    
    public boolean verbose = false;
    public boolean clusterMode = false;
    
    public Vector<LogString> logBuf = new Vector<LogString>();

    private byte[] blockBuf = new byte[80];
    private byte[] dataBuf = new byte[128];
    private byte[] dataBuf2 = new byte[128];
    private byte[] midstateBuf = new byte[32];
    private byte[] sendBuf = new byte[44];
    private byte[] hashBuf = new byte[32];
    
    private int newCount = 0;

    public boolean isRunning = false;
    public boolean suspended = false;
    
    MessageDigest digest = null;
    
    public int[] lastGoldenNonces = { 0, 0, 0, 0, 0, 0, 0, 0 };
    public int[] goldenNonce, nonce, hash7;
    public int submittedCount = 0,  totalSubmittedCount = 0;
    public long startTime, startTimeAdjust;
    
    public int overflowCount = 0;
    public long usbTime = 0;
    public double getTime = 0.0; 
    public double getTimeW = 1e-6; 
    public double submitTime = 0.0; 
    public double submitTimeW = 1e-6; 
    
    public long maxPollInterval = 20000;
    public long infoInterval = 15000;
    
    public long lastGetWorkTime = 0;
    public long ignoreErrorTime = 0;
    public long lastInfoTime = 0;
        
    public double[] errorCount = new double[256];
    public double[] errorWeight = new double[256];
    public double[] errorRate = new double[256];
    public double[] maxErrorRate = new double[256];
    public final double maxMaxErrorRate = 0.05;
    public final double errorHysteresis = 0.1; // in frequency steps
    
    private double maxHashRate = 0;
    
    private int numberOfFpgas = 0;
    private int[] fpgaMap;

// ******* BTCMiner ************************************************************
// constructor
    public BTCMiner ( ZtexDevice1 pDev, String firmwareFile, boolean v ) throws UsbException, FirmwareException, NoSuchAlgorithmException {
	
	digest = MessageDigest.getInstance("SHA-256");
	verbose = v;

	ztex  = new Ztex1v1 ( pDev );
	ztex.enableExtraFpgaConfigurationChecks = true;

	String snString=null;
	if ( ( pDev.productId(2)==0) && (firmwareFile==null) ) {
	    for ( int j=0; j<defaultFirmwarePID1.length; j++ )
		if ( defaultFirmwarePID1[j]==pDev.productId(1) && pDev.productString().equals(dummyFirmwareNames[j]) ) 
		    firmwareFile = firmwareFiles[j];
	    if ( firmwareFile != null ) {
	        msg("Using firmware `" + firmwareFile + "'" + " for `" + pDev.productString() +"'" );
	        snString = pDev.snString();
	    }
	}

        if ( firmwareFile != null ) {
    	    try {
    		ZtexIhxFile1 ihxFile = new ZtexIhxFile1( firmwareFile );
		if ( snString != null ) 
		    ihxFile.setSnString( snString );
		ztex.uploadFirmware( ihxFile, false );
    	    }
    	    catch ( Exception e ) {
    		throw new FirmwareException ( e.getLocalizedMessage() );
    	    }
    	}
    	    
        if ( ! ztex.valid() || ztex.dev().productId(0)!=10 || ztex.dev().productId(2)!=1 )
    	    throw new FirmwareException("Wrong or no firmware");
    	    
	try {
	    byte buf[] = new byte[6];
	    ztex.macRead(buf);
	    msg("MAC address: " + dataToHexStr(buf)); 
	}
	catch (Exception e) {
	    msg("No mac address support"); 
	}
    	
	getDescriptor();    	    
	
	goldenNonce = new int[numNonces*(1+extraSolutions)];
	nonce = new int[numNonces];
	hash7 = new int[numNonces];
	
	name = bitFileName+"-"+ztex.dev().snString();
    	msg( "New device: "+ descriptorInfo() );
    	
//    	long d = Math.round( 2500.0 / (freqM1 * (freqMaxM+1) * numNonces) * 1000.0 );
//    	if ( d < maxPollInterval ) maxPollInterval=d;

	numberOfFpgas = 0;
	try {
	    fpgaMap = new int[ztex.numberOfFpgas()];
    	    for (int i=0; i<ztex.numberOfFpgas(); i++ ) {
    		try {
		    ztex.selectFpga(i);
		    msg("FPGA "+ (i+1) + ": configuration time: " + ( forceEP0Config ? ztex.configureFpgaLS( "fpga/"+bitFileName+".bit" , true, 2 ) : ztex.configureFpga( "fpga/"+bitFileName+".bit" , true, 2 ) ) + " ms");
    		    try {
    			Thread.sleep( 100 );
    		    }
		    catch ( InterruptedException e) {
    		    } 
		    fpgaMap[numberOfFpgas] = i;
		    numberOfFpgas += 1;
    		}
		catch ( Exception e ) {
		    msg( "Error configuring FPGA " + i + ": " + e.getLocalizedMessage() );
		}
	    }
	}
        catch ( InvalidFirmwareException e ) {
    	    throw new FirmwareException( e.getLocalizedMessage() );
    	}
	    
	if ( numberOfFpgas < 1 )
	    throw new FirmwareException("No FPGA's found");

	fpgaNum = fpgaMap[0];
	name += "-" + (fpgaNum+1);
    	msg( "New FPGA" );
	freqM = -1;
	updateFreq();
	
	lastInfoTime = new Date().getTime();
	
	for (int i=0; i<255; i++) {
	    errorCount[i] = 0;
	    errorWeight[i] = 0;
	    errorRate[i] = 0;
	    maxErrorRate[i] = 0;
	}
	maxHashRate = freqMDefault + 1.0;
	
	startTime = new Date().getTime();
	startTimeAdjust = startTime;
	
	for (int i=0; i<rpcCount; i++) {
	    disableTime[i] = 0;
	    ioErrorCount[i] = 0;
	}
	
	if ( newBlockMonitor == null ) {
	    newBlockMonitor = new NewBlockMonitor();
	}
	
    }

    public BTCMiner ( Ztex1v1 pZtex, int pFpgaNum, boolean v ) throws UsbException, FirmwareException, NoSuchAlgorithmException {
	digest = MessageDigest.getInstance("SHA-256");
	verbose = v;

	ztex  = pZtex;
	fpgaNum = pFpgaNum;

        if ( ! ztex.valid() || ztex.dev().productId(0)!=10 || ztex.dev().productId(2)!=1 || ( ztex.dev().productId(3)<1 && ztex.dev().productId(3)>2 ) )
    	    throw new FirmwareException("Wrong or no firmware");
    	    
	getDescriptor();    	    

	goldenNonce = new int[numNonces*(1+extraSolutions)];
	nonce = new int[numNonces];
	hash7 = new int[numNonces];
	
	name = bitFileName+"-"+ztex.dev().snString()+"-"+(fpgaNum+1);
    	
    	try {
    	    msg( "New FPGA" );
	    freqM = -1;
	    updateFreq();
	    
	    lastInfoTime = new Date().getTime();
	}
	catch ( Exception e ) {
	    throw new FirmwareException ( e.getLocalizedMessage() );
	}
	
	
	for (int i=0; i<255; i++) {
	    errorCount[i] = 0;
	    errorWeight[i] = 0;
	    errorRate[i] = 0;
	    maxErrorRate[i] = 0;
	}
	maxHashRate = freqMDefault + 1.0;
	
	startTime = new Date().getTime();
	startTimeAdjust = startTime;
	
	for (int i=0; i<rpcCount; i++) {
	    disableTime[i] = 0;
	    ioErrorCount[i] = 0;
	}
	
    }

// ******* ztex ****************************************************************
    public Ztex1v1 ztex() {
	return ztex;
    }

// ******* numberofFpgas *******************************************************
    public int numberOfFpgas() {
	return numberOfFpgas;
    }

// ******* selectFpga **********************************************************
    public void selectFpga() throws UsbException, InvalidFirmwareException, IndexOutOfBoundsException {
	ztex.selectFpga(fpgaNum);
    }

// ******* fpgaNum *************************************************************
    public int fpgaNum() {
	return fpgaNum;
    }

    public int fpgaNum(int n) throws IndexOutOfBoundsException { // only valid for root miner
	if ( n<0 || n>=numberOfFpgas )
    	    throw new IndexOutOfBoundsException( "fpgaNum: Invalid FPGA number" );
	return fpgaMap[n];
    }

// ******* msg *****************************************************************
    public void msg(String s) {
	if ( clusterMode ) {
	    synchronized ( logBuf ) {
		logBuf.add( new LogString( s ) );
	    }
	}
	else {
	    printMsg( ( name!=null ? name + ": " : "" ) + s );
	}
    }

// ******* dmsg *****************************************************************
    void dmsg(String s) {
	if ( verbose )
	    msg(s);
    }

// ******* print ***************************************************************
    public void print () {
	synchronized ( logBuf ) {
	    for ( int j=0; j<logBuf.size(); j++ ) {
	        LogString ls = logBuf.elementAt(j);
	        System.out.println( name + ": " + ls.msg );
		if ( logFile != null ) {
		    logFile.println( dateFormat.format(ls.time) + ": " + name + ": " + ls.msg );
		}
	    }
	    logBuf.clear();
	}
    }

// ******* httpGet *************************************************************
    public static String httpGet(MsgObj msgObj, String url, String user, String passw, String request) throws MalformedURLException, IOException {
	HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout((int) Math.round(2000.0*BTCMiner.connectionEffort));
        con.setReadTimeout(url == longPollURL ? 1000000 : (int) Math.round(2000.0*BTCMiner.connectionEffort));
        con.setRequestProperty("Authorization", "Basic " + encodeBase64(user + ":" + passw));
        con.setRequestProperty("Accept-Encoding", "gzip,deflate");
        con.setRequestProperty("Content-Type", "application/json");
	con.setRequestProperty("Cache-Control", "no-cache");
        con.setRequestProperty("User-Agent", "ztexBTCMiner");
        con.setRequestProperty("Content-Length", "" + request.length());
        con.setUseCaches(false);
        con.setDoInput(true);
        con.setDoOutput(true);
        
        // Send request
        OutputStreamWriter wr = new OutputStreamWriter ( con.getOutputStream ());
        wr.write(request);
        wr.flush();
        wr.close();

        // read response header
        String str = con.getHeaderField("X-Reject-Reason");
        if( str != null && ! str.equals("") ) {
            msgObj.msg("Warning: Rejected block: " + str);
        } 

        // read response header
    	str = con.getHeaderField("X-Long-Polling");
        if ( str != null && ! str.equals("") && longPollURL==null ) {
    	    synchronized ( BTCMiner.newBlockMonitor ) {
    		if ( longPollURL==null ) {
    		    longPollURL = (str.length()>7 && str.substring(0,4).equalsIgnoreCase("http") ) ? str : url+str;
    		    msgObj.msg("Using LongPolling URL " + longPollURL);
    		    longPollUser = user;
    		    longPollPassw = passw;
    		}
    	    }
        }

        // read response	
        InputStream is;
        if ( con.getContentEncoding() == null )
    	    is = con.getInputStream();
    	else if ( con.getContentEncoding().equalsIgnoreCase("gzip") )
    	    is = new GZIPInputStream(con.getInputStream());
    	else if (con.getContentEncoding().equalsIgnoreCase("deflate") )
            is = new InflaterInputStream(con.getInputStream());
        else
    	    throw new IOException( "httpGet: Unknown encoding: " + con.getContentEncoding() );

        byte[] buf = new byte[1024];
        StringBuffer response = new StringBuffer(); 
        int len;
        while ( (len = is.read(buf)) > 0 ) {
            response.append(new String(buf,0,len));
        }
        is.close();
        con.disconnect();
        
        return response.toString();
    }

/*    String httpGet(String request) throws MalformedURLException, IOException {
	return httpGet(rpcurl[rpcNum], rpcuser[rpcNum], rpcpassw[rpcNum], request )
    } */

// ******* bitcoinRequest ******************************************************
    public static String bitcoinRequest( MsgObj msgObj, String url, String user, String passw, String request, String params) throws MalformedURLException, IOException {
	bcid += 1;
	return httpGet( msgObj, url, user, passw, "{\"jsonrpc\":\"1.0\",\"id\":" + bcid + ",\"method\":\""+ request + "\",\"params\":["+ (params.equals("") ? "" : ("\""+params+"\"")) + "]}" );
    }

    public String bitcoinRequest( String request, String params) throws MalformedURLException, IOException {
	String s = bitcoinRequest( this, rpcurl[rpcNum], rpcuser[rpcNum], rpcpassw[rpcNum], request, params );
        ioErrorCount[rpcNum] = 0;
        return s;
    }


// ******* getWork *************************************************************
    public boolean getWork() throws UsbException, MalformedURLException, IOException, ParserException {

	long t = new Date().getTime();
    
	int i = 0;
	while ( i<rpcCount && (disableTime[i]>t) ) 
	    i++;
	if ( i >= rpcCount )
	    return false;

	rpcNum = i;	
	String response = bitcoinRequest("getwork","" );
	t = new Date().getTime() - t;
	getTime = getTime * 0.99 + t;
	getTimeW = getTimeW * 0.99 + 1;

	hexStrToData(jsonParse(response,"data"), dataBuf2);
	newBlockMonitor.checkNew( dataBuf2 );
	newCount = newBlockMonitor.newCount;
	
//	if ( newCount == newBlockMonitor.newCount ) {
//	    try {
	        while ( getNonces() ) {}
//	    }
//	    catch ( IOException e )
//	        ioErrorCount[rpcNum]++;
//	    }
//	}
	
	hexStrToData(jsonParse(response,"data"), dataBuf);
	hexStrToData(jsonParse(response,"midstate"), midstateBuf);
	initWork();
	lastGetWorkTime = new Date().getTime();
	prevRpcNum = i;
	return true;
    }

// ******* submitWork **********************************************************
    public void submitWork( int n ) throws MalformedURLException, IOException {
	long t = new Date().getTime();

	dataBuf[76  ] = (byte) (n & 255);
	dataBuf[76+1] = (byte) ((n >> 8) & 255);
	dataBuf[76+2] = (byte) ((n >> 16) & 255);
	dataBuf[76+3] = (byte) ((n >> 24) & 255);

	dmsg( "Submitting new nonce " + intToHexStr(n) );
	if ( blkLogFile != null )
	    blkLogFile.println( dateFormat.format( new Date() ) + ": " + name + ": submitted " + dataToHexStr(dataBuf) + " to " + rpcurl[rpcNum]);
	String response = bitcoinRequest( "getwork", dataToHexStr(dataBuf) );
	String err = null;
	try {
	    err = jsonParse(response,"error");
	}
	catch ( ParserException e ) {
	}
	if ( err!=null && !err.equals("null") && !err.equals("") ) 
	    msg( "Error attempting to submit new nonce: " + err );

	for (int i=lastGoldenNonces.length-1; i>0; i-- )
	    lastGoldenNonces[i]=lastGoldenNonces[i-1];
	lastGoldenNonces[0] = n;

	t = new Date().getTime() - t;
	submitTime = submitTime * 0.99 + t;
	submitTimeW = submitTimeW * 0.99 + 1;
    }

// ******* initWork **********************************************************
    public void initWork (byte[] data, byte[] midstate) {
	if ( data.length != 128 )
	    throw new NumberFormatException("Invalid length of data");
	if ( midstate.length != 32 )
	    throw new NumberFormatException("Invalid length of midstate");
	for (int i=0; i<128; i++)
	    dataBuf[i] = data[i];
	for (int i=0; i<32; i++)
	    midstateBuf[i] = midstate[i];
	initWork();
    }

    public void initWork () {
	// data is Middleendian !!!
	for (int i=0; i<80; i+=4 ) {
	    blockBuf[i  ] = dataBuf[i+3];
	    blockBuf[i+1] = dataBuf[i+2];
	    blockBuf[i+2] = dataBuf[i+1];
	    blockBuf[i+3] = dataBuf[i  ];
	}
    }

// ******* getHash ***********************************************************
    public byte[] getHash(byte[] data) {
        digest.update(blockBuf);
        byte[] first = digest.digest();
        byte[] second = digest.digest(first);
        return second;
    }

// ******* getHash ***********************************************************
    public byte[] getHash(int n) {
	
	blockBuf[76  ] = (byte) ((n >> 24) & 255);
	blockBuf[76+1] = (byte) ((n >> 16) & 255);
	blockBuf[76+2] = (byte) ((n >> 8) & 255);
	blockBuf[76+3] = (byte) (n & 255);

/*        digest.update(blockBuf);
        byte[] first = digest.digest();
        byte[] second = digest.digest(first);
        return second; */
        try {
    	    digest.update(blockBuf);
    	    digest.digest(hashBuf,0,32);
    	    digest.update(hashBuf);
    	    digest.digest(hashBuf,0,32);
    	}
    	catch ( DigestException e ) {
    	    msg( "Error calculating hash value: " + e.getLocalizedMessage() ); // should never occur
    	}
        return hashBuf;
    }

// ******* sendData ***********************************************************
    public void sendData () throws UsbException {
	for ( int i=0; i<12; i++ ) 
	    sendBuf[i] = dataBuf[i+64];
	for ( int i=0; i<32; i++ ) 
	    sendBuf[i+12] = midstateBuf[i];
	    
	long t = new Date().getTime();
	try {
	    selectFpga();
	}
	catch ( InvalidFirmwareException e )  {
	    // shouldn't occur
	}
        ztex.vendorCommand2( 0x80, "Send hash data", 0, 0, sendBuf, 44 );
        usbTime += new Date().getTime() - t;
        
        ignoreErrorTime = new Date().getTime() + 500; // ignore errors for next 1s
	for ( int i=0; i<numNonces; i++ ) 
	    nonce[i] = 0;
        isRunning = true;
    }

// ******* setFreq *************************************************************
    public void setFreq (int m) throws UsbException {
	if ( m > freqMaxM ) m = freqMaxM;

	long t = new Date().getTime();
	try {
	    selectFpga();
	}
	catch ( InvalidFirmwareException e )  {
	    // shouldn't occur
	}
        ztex.vendorCommand( 0x83, "Send hash data", m, 0 );
        usbTime += new Date().getTime() - t;

        ignoreErrorTime = new Date().getTime() + 2000; // ignore errors for next 2s
    }

// ******* suspend *************************************************************
    public boolean suspend ( )  {
        suspended = true;
	if ( suspendSupported ) {
	    try {
		selectFpga();
    		ztex.vendorCommand( 0x84, "Suspend" );
	    }
	    catch ( Exception e )  {
		msg( "Suspend command failed: " + e.getLocalizedMessage() );
		return false;
	    }
	}
	else {
	    msg( "Suspend command not supported. Update Firmware." );
	    return false;
	}
	return true;
    }

// ******* updateFreq **********************************************************
    public void updateFreq() throws UsbException {

	for ( int i=0; i<freqMaxM; i++ )  {
	    if ( maxErrorRate[i+1]*i < maxErrorRate[i]*(i+20) )
		maxErrorRate[i+1] = maxErrorRate[i]*(1.0+20.0/i);
	}

	int maxM = 0;
	while ( maxM<freqMDefault && maxErrorRate[maxM+1]<maxMaxErrorRate )
	    maxM++;
	while ( maxM<freqMaxM && errorWeight[maxM]>150 && maxErrorRate[maxM+1]<maxMaxErrorRate )
	    maxM++;
	    
	int bestM=0;
	double bestR=0;
	for ( int i=0; i<=maxM; i++ )  {
	    double r = (i + 1 + ( i == freqM ? errorHysteresis : 0))*(1-maxErrorRate[i]);
	    if ( r > bestR ) {
		bestM = i;
		bestR = r;
	    }
	}
	
	if ( bestM != freqM ) {
	    freqM = bestM;
	    msg ( "Set frequency to " + String.format("%.2f",(freqM+1)*(freqM1)) +"MHz" );
	    setFreq( freqM );
	}

	maxM = freqMDefault;
	while ( maxM<freqMaxM && errorWeight[maxM+1]>100 )
	    maxM++;
	if ( ( bestM+1 < (1.0-overheatThreshold )*maxHashRate ) && bestM < maxM-1 )  {
	    try {
		selectFpga();
		ztex.resetFpga();
	    }
	    catch ( Exception e ) {
	    }
	    throw new UsbException("Hash rate drop of " + String.format("%.1f",(1.0-1.0*(bestM+1)/maxHashRate)*100) + "% detect. This may be caused by overheating. FPGA is shut down to prevent damage.  " + maxHashRate);
	}
    }

// ******* getNonces ***********************************************************
    public boolean getNonces() throws UsbException, MalformedURLException, IOException {
	if ( !isRunning || disableTime[prevRpcNum] > new Date().getTime() ) return false;
	
	rpcNum = prevRpcNum;
	
	getNoncesInt();
	
        if ( ignoreErrorTime < new Date().getTime() ) {
	    errorCount[freqM] *= 0.995;
    	    errorWeight[freqM] = errorWeight[freqM]*0.995 + 1.0;
            for ( int i=0; i<numNonces; i++ ) {
        	if ( ! checkNonce( nonce[i], hash7[i] ) )
    		    errorCount[freqM] +=1.0/numNonces;
    	    }
    	    
	    errorRate[freqM] = errorCount[freqM] / errorWeight[freqM] * Math.min(1.0, errorWeight[freqM]*0.01) ;
    	    if ( errorRate[freqM] > maxErrorRate[freqM] )
    	        maxErrorRate[freqM] = errorRate[freqM];
    	    if ( errorWeight[freqM] > 100 )
    		maxHashRate = Math.max(maxHashRate, (freqM+1.0)*(1-errorRate[freqM]));
    	}
    	
	boolean submitted = false;
        for ( int i=0; i<numNonces*(1+extraSolutions); i++ ) {
    	    int n = goldenNonce[i];
    	    if ( n != -offsNonces ) {
    		getHash(n);
    		if ( hashBuf[31]==0 && hashBuf[30]==0 && hashBuf[29]==0 && hashBuf[28]==0 ) {
    		    int j=0;
    		    while ( j<lastGoldenNonces.length && lastGoldenNonces[j]!=n )
    			j++;
        	    if  (j>=lastGoldenNonces.length) {
        	        submitWork( n );
        		submittedCount+=1;
        		totalSubmittedCount+=1;
        	        submitted = true;
        	    }
    		}
    	    }
        }
        return submitted;
    } 

// ******* getNoncesInt ********************************************************
    public void getNoncesInt() throws UsbException {
	int bs = 12+extraSolutions*4;
	byte[] buf = new byte[numNonces*bs];
	boolean overflow = false;

	long t = new Date().getTime();
	try {
	    selectFpga();
	}
	catch ( InvalidFirmwareException e )  {
	    // shouldn't occur
	}
        ztex.vendorRequest2( 0x81, "Read hash data", 0, 0, buf, numNonces*bs );
        usbTime += new Date().getTime() - t;
        
//	System.out.print(dataToHexStr(buf)+"            ");
        for ( int i=0; i<numNonces; i++ ) {
	    goldenNonce[i*(1+extraSolutions)] = dataToInt(buf,i*bs+0) - offsNonces;
	    int j = dataToInt(buf,i*bs+4) - offsNonces;
	    overflow |= ((j >> 4) & 0xfffffff) < ((nonce[i]>>4) & 0xfffffff);
	    nonce[i] = j;
	    hash7[i] = dataToInt(buf,i*bs+8);
	    for ( j=0; j<extraSolutions; j++ )
		goldenNonce[i*(1+extraSolutions)+1+j] = dataToInt(buf,i*bs+12+j*4) - offsNonces;
	}
	if ( overflow && ! PollLoop.scanMode )
	    overflowCount += 1;
    }

// ******* checkNonce *******************************************************
    public boolean checkNonce( int n, int h ) throws UsbException {
	int offs[] = { 0, 1, -1, 2, -2 };
//	int offs[] = { 0 };
	for (int i=0; i<offs.length; i++ ) {
	    getHash(n + offs[i]);
	    if ( ( (hashBuf[31] & 255) | ((hashBuf[30] & 255)<<8) | ((hashBuf[29] & 255)<<16) | ((hashBuf[28] & 255)<<24) ) == h + 0x5be0cd19 )
		return true;
    	}
        return false;
    }

// ******* totalHashRate *******************************************************
    public double totalHashRate () {
	return fatalError == null ? (freqM+1)*freqM1*(1-errorRate[freqM])*hashesPerClock : 0;
    }
    
// ******* submittedHashRate ***************************************************
    public double submittedHashRate () {
	return fatalError == null ? 4.294967296e6 * totalSubmittedCount / (new Date().getTime()-startTime) : 0;
    }
    
// ******* printInfo ***********************************************************
    public void printInfo( boolean force ) {
	long t = new Date().getTime();
	if ( !force && (clusterMode || lastInfoTime+infoInterval > t || !isRunning) )
	    return;
	    
	if ( fatalError != null ) {
	    printMsg(name + ": " + fatalError);
	    return;
	}

	if ( suspended ) {
	    printMsg(name + ": Suspended");
	    return;
	}
	
	StringBuffer sb = new StringBuffer( "f=" + String.format("%.2f",(freqM+1)*freqM1)+"MHz" );

	if ( errorWeight[freqM]>20 )
	    sb.append(",  errorRate="+ String.format("%.2f",errorRate[freqM]*100)+"%");

	if ( errorWeight[freqM]>100 )
	    sb.append(",  maxErrorRate="+ String.format("%.2f",maxErrorRate[freqM]*100)+"%");

/*	if ( freqM<255 && (errorWeight[freqM+1]>100.1 || maxErrorRate[freqM+1]>0.001 ) )
	    sb.append(",  nextMaxErrorRate="+ String.format("%.2f",maxErrorRate[freqM+1]*100)+"%"); */

	double hr = (freqM+1)*freqM1*(1-errorRate[freqM])*hashesPerClock;

	if ( errorWeight[freqM]>20 )
	    sb.append(",  hashRate=" + String.format("%.1f", hr )+"MH/s" );
	    
	sb.append(",  submitted " +submittedCount+" new nonces,  luckFactor=" + String.format("%.2f", submittedHashRate()/hr+0.0049 ));
	submittedCount = 0;
	
	printMsg(name + ": " + sb.toString());
	    
	lastInfoTime = t;
    }

// ******* getDescriptor *******************************************************
    private void getDescriptor () throws UsbException, FirmwareException {
	byte[] buf = new byte[64];

        ztex.vendorRequest2( 0x82, "Read descriptor", 0, 0, buf, 64 );
        if ( buf[0] != 5 ) {
    	    if ( ( buf[0] != 2 ) && ( buf[0] != 4 ) ) {
    		throw new FirmwareException("Invalid BTCMiner descriptor version. Firmware must be updated.");
    	    }
            msg("Warning: Firmware out of date");
    	}
        numNonces = (buf[1] & 255) + 1;
        offsNonces = ((buf[2] & 255) | ((buf[3] & 255) << 8)) - 10000;
        freqM1 = ( (buf[4] & 255) | ((buf[5] & 255) << 8) ) * 0.01;
        freqM = (buf[6] & 255);
        freqMaxM = (buf[7] & 255);
        if ( freqM > freqMaxM )
    	    freqM = freqMaxM;
        freqMDefault = freqM;
        
        suspendSupported = buf[0] == 5;
        
        hashesPerClock = buf[0] > 2 ? ( ( (buf[8] & 255) | ((buf[9] & 255) << 8) ) +1 )/128.0 : 1.0;
        extraSolutions = buf[0] > 4 ? buf[10] : 0;
        
        int i0 = buf[0] > 4 ? 11 : ( buf[0] == 4 ? 10 : 8 );
        int i = i0;
        while ( i<64 && buf[i]!=0 )
    	    i++;
    	if ( i < i0+1)
    	    throw new FirmwareException("Invalid bitstream file name");
    	bitFileName = new String(buf, i0, i-i0);

        if ( buf[0] < 4 ) {
    	    if ( bitFileName.substring(0,13).equals("ztex_ufm1_15b") ) 
    		hashesPerClock = 0.5;
    	    msg( "Warning: HASHES_PER_CLOCK not defined, assuming " + hashesPerClock );
    	}
    }
    
// ******* checkUpdate **********************************************************
    public boolean checkUpdate() {
	long t = new Date().getTime();
	if ( !isRunning ) return true;
	if ( ignoreErrorTime > t ) return false;
	if ( newCount < newBlockMonitor.newCount) return true;
	if ( disableTime[prevRpcNum] > t ) return true;
	if ( lastGetWorkTime + maxPollInterval < t ) return true;
	for ( int i=0; i<numNonces ; i++ )
	    if ( nonce[i]<0 ) return true;
	return false;
    }

// ******* descriptorInfo ******************************************************
    public String descriptorInfo () {
	return "bitfile=" + bitFileName + "   f_default=" + String.format("%.2f",freqM1 * (freqMDefault+1)) + "MHz  f_max=" + String.format("%.2f",freqM1 * (freqMaxM+1))+ "MHz  HpC="+hashesPerClock+"H";
    }

// ******* resetCounters ******************************************************Ü
    public void resetCounters () {
	while ( freqMDefault<freqM && errorWeight[freqMDefault+1]>100 )
	    freqMDefault++;

	for ( int i=0; i<255; i++ ) {
	    errorCount[i] *= 0.05;
	    errorWeight[i] *= 0.05;
	    errorRate[i]=0;
	    maxErrorRate[i]=0;
	}
	startTime = new Date().getTime();
	totalSubmittedCount = 0;
    }
    
// *****************************************************************************
// ******* main ****************************************************************
// *****************************************************************************
    public static void main (String args[]) {
    
	int devNum = -1;
	boolean workarounds = false;
	
        String firmwareFile = null, snString = null;
        boolean printBus = false;
        boolean verbose = false;
        boolean eraseFirmware = false;

        String filterType = null;
        String logFileName = "BTCMiner.log";
        
        char mode = 's';
        
        rpcCount = 1; 
        rpcurl[0] = "http://127.0.0.1:8332";
        rpcuser[0] = null;
        rpcpassw[0] = null;

	try {
// init USB stuff
	    LibusbJava.usb_init();

	    
// scan the command line arguments
    	    for (int i=0; i<args.length; i++ ) {
	        if ( args[i].equals("-d") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			devNum = Integer.parseInt( args[i] );
		    } 
		    catch (Exception e) {
		        throw new ParameterException("Device number expected after -d");
		    }
		}
		else if ( args[i].equals("-l") ) {
		    i++;
		    if (i>=args.length) {
			throw new ParameterException("Error: File name expected after `-l'");
		    }
		    try {
			logFileName = args[i];
		    } 
		    catch (Exception e) {
			throw new ParameterException("Error: File name expected after `-l': "+e.getLocalizedMessage() );
		    }
		}
		else if ( args[i].equals("-bl") ) {
		    i++;
		    if (i>=args.length) {
			throw new ParameterException("Error: File name expected after `-dl'");
		    }
		    try {
			blkLogFile = new PrintStream ( new FileOutputStream ( args[i], true ), true );
		    } 
		    catch (Exception e) {
			throw new ParameterException("Error: File name expected after `-bl': "+e.getLocalizedMessage() );
		    }
		}
	        else if ( args[i].equals("-host") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			rpcurl[0] = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("URL expected after -host");
		    }
		}
	        else if ( args[i].equals("-u") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			rpcuser[0] = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("User expected after -u");
		    }
		}
	        else if ( args[i].equals("-p") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			rpcpassw[0] = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("Password expected after -p");
		    }
		}
	        else if ( args[i].equals("-b") ) {
	    	    i+=3;
		    try {
			if (i>=args.length) throw new Exception();
			if ( rpcCount >= maxRpcCount )
			    throw new IndexOutOfBoundsException("Maximum aoumount of backup servers reached");
    			rpcurl[rpcCount] = args[i-2];
    			rpcuser[rpcCount] = args[i-1];
    			rpcpassw[rpcCount] = args[i];
			rpcCount+=1;
		    } 
		    catch (Exception e) {
		        throw new ParameterException("<URL> <user name> <password> expected after -b");
		    }
		}
	        else if ( args[i].equals("-lp") ) {
	    	    i+=3;
		    try {
			if (i>=args.length) throw new Exception();
    			longPollURL = args[i-2];
    			longPollUser = args[i-1];
    			longPollPassw = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("<URL> <user name> <password> expected after -lp");
		    }
		}
	        else if ( args[i].equals("-f") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			firmwareFile = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("ihx file name expected afe -f");
		    }
		}
	        else if ( args[i].equals("-pt") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			filterType = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("<string> after -pt");
		    }
		}
	        else if ( args[i].equals("-ps") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			filterSN = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("<string> after -ps");
		    }
		}
	        else if ( args[i].equals("-m") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
			if ( args[i].length() < 1 ) throw new Exception();
			mode = Character.toLowerCase( args[i].charAt(0) );
			if ( mode != 's' && mode != 't'  && mode != 'p' && mode != 'c' ) throw new Exception();
		    } 
		    catch (Exception e) {
		        throw new ParameterException("s|t|p|c expected afe -m");
		    }
		}
		else if ( args[i].equals("-s") ) {
		    i++;
    	    	    if ( i >= args.length ) {
			throw new ParameterException("Error: String expected after -s");
		    }
    		    snString = checkSnString(args[i]);
		}
		else if ( args[i].equals("-i") ) {
		    printBus = true;
		} 
		else if ( args[i].equals("-v") ) {
		    verbose = true;
		} 
		else if ( args[i].equals("-rf") ) {
		    eraseFirmware = true;
		} 
		else if ( args[i].equals("-ep0") ) {
		    forceEP0Config = true;
		} 
		else if ( args[i].equals("-h") ) {
		        System.err.println(ParameterException.helpMsg);
	    	        System.exit(0);
		}
	        else if ( args[i].equals("-n") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			 BTCMinerCluster.maxDevicesPerThread = Integer.parseInt( args[i] );
		    } 
		    catch (Exception e) {
		        throw new ParameterException("Number expected after -n");
		    }
		}
	        else if ( args[i].equals("-oh") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			overheatThreshold = Double.parseDouble( args[i] );
		    } 
		    catch (Exception e) {
		        throw new ParameterException("Number expected after -oh");
		    }
		}
		else throw new ParameterException("Invalid Parameter: "+args[i]);
	    }
	    
	    logFile = new PrintStream ( new FileOutputStream ( logFileName, true ), true );

    	    if ( overheatThreshold > 0.1001 ) System.err.println("Warning: overheat threshold set to " + overheatThreshold +": overheat shutdown may be triggered too late, recommended values: 0..0.1");
	    
	    if ( BTCMinerCluster.maxDevicesPerThread < 1 )
		BTCMinerCluster.maxDevicesPerThread = 127;
		
	    if ( mode != 'c' && filterSN != null)
		filterSN = checkSnString(filterSN);
	    
	    if ( mode != 't' && mode != 'p' ) {
		if ( rpcuser[0] == null ) {
		    System.out.print("Enter RPC user name: ");
		    rpcuser[0] = new BufferedReader(new InputStreamReader( System.in) ).readLine();
		}

		if ( rpcpassw[0] == null ) {
		    System.out.print("Enter RPC password: ");
		    rpcpassw[0] = new BufferedReader(new InputStreamReader(System.in) ).readLine();
		}
	    }
		
/*	    Authenticator.setDefault(new Authenticator() {
    		protected PasswordAuthentication getPasswordAuthentication() {
        	    return new PasswordAuthentication (BTCMiner.rpcuser, BTCMiner.rpcpassw.toCharArray());
    		}
	    }); */
	    

	    if ( mode == 's' || mode == 't' ) {
		if ( devNum < 0 )
		    devNum = 0;
	
		ZtexScanBus1 bus = new ZtexScanBus1( ZtexDevice1.ztexVendorId, ZtexDevice1.ztexProductId, filterSN==null, false, 1,  filterSN, 10, 0, 1, 0 );
		if ( bus.numberOfDevices() <= 0) {
		    System.err.println("No devices found");
		    System.exit(0);
		} 
		if ( printBus ) {
	    	    bus.printBus(System.out);
	    	    System.exit(0);
		}
		
	        BTCMiner miner = new BTCMiner ( bus.device(devNum), firmwareFile, verbose );
		if ( mode == 't' ) { // single mode
		    miner.initWork( 
			hexStrToData( "0000000122f3e795bb7a55b2b4a580e0dbba9f2a5aedbfc566632984000008de00000000e951667fbba0cfae7719ab2fb4ab8d291a20d387782f4610297f5899cc58b7d64e4056801a08e1e500000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000" ),
			hexStrToData( "28b81bd40a0e1b75d18362cb9a2faa61669d42913f26194f776c349e97559190" )
		    );

		    miner.sendData ( );
		    for (int i=0; i<200; i++ ) {
			try {
			    Thread.sleep( 250 );
			}
			catch ( InterruptedException e) {
			}	 
			miner.getNoncesInt();

    			miner.getHash(miner.nonce[0]);
    			for ( int j=0; j<miner.numNonces; j++ ) {
	    		    System.out.println( i +"-" + j + ":  " + intToHexStr(miner.nonce[j]) + "    " + miner.checkNonce(miner.nonce[j],miner.hash7[j])  + "   " +  miner.overflowCount + "    " + intToHexStr(miner.goldenNonce[j*(1+miner.extraSolutions)]) + "      "  + dataToHexStr( miner.getHash( miner.goldenNonce[j]) ) );
	    		}
		    } 
		}
		else { // single mode
		    Vector<BTCMiner> v = new Vector<BTCMiner>();
		    v.add ( miner );
		    for ( int i=1; i<miner.numberOfFpgas(); i++ )
			v.add(new BTCMiner(miner.ztex(), miner.fpgaNum(i), verbose) );
		    System.out.println("");
		    if ( miner.ztex().numberOfFpgas()>1 ) 
			System.out.println("A multi-FPGA board is detected. Use the cluster mode for additional statistics.");
		    System.out.println("Disconnect device or press Ctrl-C for exit\n");
		    new PollLoop(v).run(); 
		}
	    }
	    else if ( mode == 'p' ) {
		if ( eraseFirmware && filterType == null && filterSN == null ) 
		    throw new ParameterException("-rf requires -pt or -ps");
		    
/*		ZtexScanBus1 bus = ( filterType == null && filterSN == null ) 
			? new ZtexScanBus1( ZtexDevice1.cypressVendorId, ZtexDevice1.cypressProductId, true, false, 1)
			: new ZtexScanBus1( ZtexDevice1.ztexVendorId, ZtexDevice1.ztexProductId, false, false, 1, null, 10, 0, 1, 0 ); */
		ZtexScanBus1 bus = new ZtexScanBus1( ZtexDevice1.ztexVendorId, ZtexDevice1.ztexProductId, filterType==null && filterSN==null, false, 1, null, 10, 0, 0, 0 );

		if ( bus.numberOfDevices() <= 0) {
		    System.err.println("No devices found");
		    System.exit(0);
		} 
		if ( printBus ) {
	    	    bus.printBus(System.out);
	    	    System.exit(0);
		}
		if ( firmwareFile == null && !eraseFirmware )
		    throw new Exception("Parameter -f or -rf required in programming mode");

		int imin=0, imax=bus.numberOfDevices()-1;
		if ( devNum >= 0 ) {
		    imin=devNum;
		    imax=devNum;
		}
		
	        ZtexIhxFile1 ihxFile = eraseFirmware ? null : new ZtexIhxFile1( firmwareFile );
		    
		int j = 0;
		for (int i=imin; i<=imax; i++ ) {
		    ZtexDevice1 dev = bus.device(i);
		    if ( ( filterSN == null || filterSN.equals(dev.snString()) ) &&
			 ( filterType == null || ( (dev.productId(2) == 1) && filterType.equals(getType(dev))) ) &&
			 ( filterType != null || filterSN != null || dev.productId(2) == 0) ) {
			Ztex1v1 ztex = new Ztex1v1 ( dev );
			if ( snString != null && ihxFile != null ) 
			    ihxFile.setSnString( snString );
			else if ( ztex.valid() && ihxFile != null )
			    ihxFile.setSnString( dev.snString() );
			if ( eraseFirmware ) {
			    ztex.eepromDisable();
			    System.out.println("EEPROM erased: " + ztex.toString());
			}
			else {
			    System.out.println("\nold: "+ztex.toString());
	    		    System.out.println("Firmware upload time: " + ztex.uploadFirmware( ihxFile, false ) + " ms");
			    System.out.println("EEPROM programming time: " + ztex.eepromUpload( ihxFile, false ) + " ms");
			    System.out.println("new: " + ztex.toString());
			}
		    j+=1;
		    }
		}
		System.out.println("\ntotal amount of (re-)programmed devices: " + j);
	    }
	    else if ( mode == 'c' ) {
		new BTCMinerCluster( verbose );
	    }
	    
	    
	}
	catch (Exception e) {
	    System.out.println("Error: "+e.getLocalizedMessage() );
	} 

	if ( BTCMiner.newBlockMonitor != null ) {
	    BTCMiner.newBlockMonitor.running = false;
	    BTCMiner.newBlockMonitor.interrupt();
	}
	
	System.exit(0);
	
   } 

}
