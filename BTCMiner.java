/*!
   BTCMiner -- BTCMiner for ZTEX USB-FPGA Modules
   Copyright (C) 2011-2012 ZTEX GmbH
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

import java.io.*;
import java.util.*;
import java.net.*;
import java.security.*;
import java.text.*;
import java.util.zip.*;

import ch.ntb.usb.*;

import ztex.*;

// *****************************************************************************
// ******* BTCMinerHTTPD *******************************************************
// *****************************************************************************
class BTCMinerHTTPD extends NanoHTTPD {
	private File myRootDir;
	private int refreshTmo = 30;
	private boolean remoteSwitching = true;

	// ******* Constructor
	// *************************************************************************
	public BTCMinerHTTPD(int port, File wwwroot) throws IOException {
		super(port, wwwroot);
		this.myRootDir = wwwroot;
	}

	// ******* Response
	// *************************************************************************
	public Response serve(String uri, String method, Properties header, Properties parms, Properties files) {
/*
                System.out.println( method + " '" + uri + "' " );
                Enumeration e = header.propertyNames();
                while ( e.hasMoreElements())
                {
                        String value = (String)e.nextElement();
                        System.out.println( "  HDR: '" + value + "' = '" + header.getProperty( value ) + "'" );
                }
                e = parms.propertyNames();
                while ( e.hasMoreElements())
                {
                        String value = (String)e.nextElement();
                        System.out.println( "  PRM: '" + value + "' = '" + parms.getProperty( value ) + "'" );
                }
                e = files.propertyNames();
                while ( e.hasMoreElements())
                {
                        String value = (String)e.nextElement();
                        System.out.println( "  UPLOADED: '" + value + "' = '" + files.getProperty( value ) + "'" );
                }
'accept-encoding' = 'gzip,deflate,sdch'
*/
		Response rsp;
		if (uri.equalsIgnoreCase("/json")) {
			rsp = new NanoHTTPD.Response(HTTP_OK, MIME_JSON, serveJson(uri, header));
			return rsp;
		} else if (uri.equalsIgnoreCase("/s_pool")) {
			rsp = new NanoHTTPD.Response(HTTP_OK, MIME_HTML, serveSelectPool(parms));
			return rsp;
		} else if (uri.equalsIgnoreCase("/s_refresh")) {
			rsp = new NanoHTTPD.Response(HTTP_OK, MIME_HTML, serveSelectRefresh(parms));
			return rsp;
		} else if (uri.equalsIgnoreCase("/s_rpc")) {
			rsp = new NanoHTTPD.Response(HTTP_OK, MIME_HTML, serveSelectRPC(parms));
			return rsp;
		} else if (uri.equalsIgnoreCase("/s_value")) {
			rsp = new NanoHTTPD.Response(HTTP_OK, MIME_HTML, serveSetValue(parms));
			return rsp;
		} else {
			rsp = serveFile(uri, header, myRootDir, true);
		}
		rsp.addHeader("Cache-Control", "no-cache");
		rsp.addHeader("Pragma", "no-cache");
		return rsp;
	}

	// ******* serveJson
	// *************************************************************************
	private String serveJson(String uri, Properties header) {
		StringBuffer sb = new StringBuffer("{ \"id\":\"" + BTCMiner.clusterId + "\"");
		Date now = new Date();
		sb.append(", \"date\":\"" + now.toString() + "\"");
		long uptime = (now.getTime() - BTCMiner.upTime) / 1000;
		sb.append(", \"uptime\":\"" + uptime + "\"");
		if (header.getProperty("host") != null) {
			sb.append(", \"host\":\"" + header.getProperty("host") + "\"");
		}
		if (BTCMiner.cluster == null) {
			sb.append(", \"state\":\"initializing\"");
		} else {
			sb.append(", \"state\":\"running\"");
			sb.append(", \"refresh\":\"" + refreshTmo + "\"");
			if (BTCMiner.longPollURL != null) {
				sb.append(", \"longpoll\":\"" + BTCMiner.longPollURL + "\"");
			} else {
				sb.append(", \"longpoll\":\"\"");
			}

			int server = BTCMiner.rpcCount;
			int backup1 = BTCMiner.rpcFirstBackup;
			int backup = 0;
			if (backup1 > 0) {
				backup = server - backup1;
			}
			sb.append(", \"server_count\":\"" + (server - backup) + "\"");
			sb.append(", \"server\":[");
			if (server > 0) {
				for (int i = 0; i < (server - backup); i++) {
					RPC rpc = BTCMiner.rpc[i];
					if (i > 0) {
						sb.append(", ");
					}
					String state;
					if (rpc.disabled()) {
						state = "disabled";
					} else {
						state = "enabled";
					}
					String mining;
					if (rpc.mine) {
						mining = "enabled";
					} else {
						mining = "disabled";
					}
					sb.append("{\"index\":\"" + i + "\", \"state\":\"" + state + "\", \"mining\":\"" + mining +"\", \"name\":\"" + rpc.name +"\", \"url\":\"" + rpc.url + "\", \"host\":\"" + rpc.host + "\", \"user\":\"" + rpc.usr + "\", \"pass\":\"" + rpc.pwd + "\", \"ioerr_monitor_time\":\"" + rpc.ioDisableMonitorTime + "\", \"getwork\":\"" + rpc.sharesGetwork +  "\", \"accept\":\"" + rpc.sharesAccepted + "\", \"reject\":\"" + rpc.sharesRejected + "\", \"disable\":\"" + rpc.poolDisabled + "\", \"difficulty\":\"" + String.format("%.1f", rpc.difficulty) + "\"}");
				}
			}
			sb.append("]");

			sb.append(", \"backup_server_count\":\"" + backup + "\"");
			sb.append(", \"backup_server\":[");
			if (backup > 0) {
				for (int i = backup1; i < server; i++) {
					RPC rpc = BTCMiner.rpc[i];
					if (i > backup1) {
						sb.append(", ");
					}
					String state;
					if (rpc.disabled()) {
						state = "disabled";
					} else {
						state = "enabled";
					}
					sb.append("{\"index\":\"" + i + "\", \"state\":\"" + state + "\", \"mining\":\"enabled\", \"name\":\"" + rpc.name +"\", \"url\":\"" + rpc.url + "\", \"host\":\"" + rpc.host + "\", \"user\":\"" + rpc.usr + "\", \"pass\":\"" + rpc.pwd + "\", \"ioerr_monitor_time\":\"" + rpc.ioDisableMonitorTime + "\", \"getwork\":\"" + rpc.sharesGetwork +  "\", \"accept\":\"" + rpc.sharesAccepted + "\", \"reject\":\"" + rpc.sharesRejected + "\", \"disable\":\"" + rpc.poolDisabled + "\", \"difficulty\":\"" + String.format("%.1f", rpc.difficulty) + "\"}");
				}
			}
			sb.append("]");

			double hashrate_total = 0.0;
			double hashrate_submitted = 0.0;
			sb.append(", \"miner\":[");
			for (int i = 0;; i++) {
				BTCMiner m = BTCMiner.cluster.getMiner(i);
				if (m == null) {
					break;
				}
				if (i > 0) {
					sb.append(", ");
				}
				String state;
				if (!m.isRunning) {
					state = "not running";
				} else if (m.fatalError != null) {
					state = m.fatalError;
				} else if (m.suspended) {
					state = "suspended";
				} else {
					state = "running";
				}
				double fr = (m.freqM + 1) * m.freqM1;
				double er = m.errorRate[m.freqM];
				double hr = fr * (1 - er) * m.hashesPerClock;
				hashrate_total += hr;
				double hs = m.submittedHashRate();
				hashrate_submitted += hs;
				double lk = hs / hr + 0.0049;
				sb.append("{\"index\":\"" + i + "\", \"name\":\"" + m.name + "\", \"state\":\"" + state + "\", \"frequency\":\"" + String.format("%.2f", fr) + "\", \"hashrate\":\"" + String.format("%.2f", hr) + "\", \"errorrate\":\"" + String.format("%.2f", er * 100.0) + "\", \"maxerrorrate\":\"" + String.format("%.2f", m.maxErrorRate[m.freqM] * 100.0) + "\", \"shares\":\"" + m.submittedCount + "\", \"luck\":\"" + String.format("%.2f", lk) + "\"}");
			}
			sb.append("]");
			sb.append(", \"total_hashrate\":\"" + String.format("%.1f", hashrate_total) + "\"");
			sb.append(", \"submitted_hashrate\":\"" + String.format("%.1f", hashrate_submitted) + "\"");
			if (remoteSwitching) {
				sb.append(", \"remote_switch\":\"1\"");
			} else {
				sb.append(", \"remote_switch\":\"0\"");
			}
			if (BTCMiner.getTargetCheck()) {
				sb.append(", \"target_check\":\"1\"");
			} else {
				sb.append(", \"target_check\":\"0\"");
			}
		}

		sb.append("}");
		return sb.toString();
	}

	// ******* serveSelectPool
	// *************************************************************************
	private String serveSelectPool(Properties parms) {
		if (parms.getProperty("pool") != null) {
			boolean force = false;
			if (parms.getProperty("force") != null) {
				force = true;
			}

			if (remoteSwitching || force) {
				try {
					int newpool = Integer.parseInt(parms.getProperty("pool"));
					int server = BTCMiner.rpcCount;
					int backup1 = BTCMiner.rpcFirstBackup;
					int backup = 0;
					if (backup1 > 0) {
						backup = server - backup1;
					}
					if (newpool >= 0 && newpool < (server - backup)) {
						for (int i = 0; i < (server - backup); i++) {
							if (i == newpool) {
								BTCMiner.rpc[i].mine = true;
							}
						}
						for (int i = 0; i < (server - backup); i++) {
							if (i != newpool) {
								BTCMiner.rpc[i].mine = false;
							}
						}
						if (BTCMiner.newBlockMonitor != null) {
							synchronized (BTCMiner.newBlockMonitor) {
								BTCMiner.disableLPTime = new Date().getTime() + 20000;
								BTCMiner.longPollURL = null;
								BTCMiner.longPollUser = "";
								BTCMiner.longPollPassw = "";
							}
						}
					}
				} catch (NumberFormatException e) {
				}
			}
		}
		return "<html><body></body></html>";
	}

	// ******* serveSelectRefresh
	// *************************************************************************
	private String serveSelectRefresh(Properties parms) {
		if (parms.getProperty("refresh") != null) {
			try {
				int refresh = Integer.parseInt(parms.getProperty("refresh"));
				if (refresh >= 0 && refresh <= 60) {
					refreshTmo = refresh;
				}
			} catch (NumberFormatException e) {
			}
		}
		return "<html><body></body></html>";
	}

	// ******* serveSetValue
	// *************************************************************************
	private String serveSetValue(Properties parms) {
		if (parms.getProperty("remoteswitch") != null) {
			try {
				int value = Integer.parseInt(parms.getProperty("remoteswitch"));
				if (value == 0) {
					remoteSwitching = false;
				} else {
					remoteSwitching = true;
				}
			} catch (NumberFormatException e) {
			}
		} else if (parms.getProperty("targetcheck") != null) {
			try {
				int value = Integer.parseInt(parms.getProperty("targetcheck"));
				if (value == 0) {
					BTCMiner.setTargetCheck(false);
				} else {
					BTCMiner.setTargetCheck(true);
				}
			} catch (NumberFormatException e) {
			}
		} else if (parms.getProperty("resetdisable") != null) {
			try {
				int value = Integer.parseInt(parms.getProperty("resetdisable"));
				int server = BTCMiner.rpcCount;
				if (value == 4242) {	/* all */
					for (int i = 0; i < server; i++) {
						RPC rpc = BTCMiner.rpc[i];
						rpc.enable();
					}
				} else if (value >= 0 && value < server) {
					RPC rpc = BTCMiner.rpc[value];
					rpc.enable();
				}
			} catch (NumberFormatException e) {
			}
		} else if (parms.getProperty("resetstat") != null) {
			try {
				int value = Integer.parseInt(parms.getProperty("resetstat"));
				int server = BTCMiner.rpcCount;
				if (value == 4242) {	/* all */
					for (int i = 0; i < server; i++) {
						RPC rpc = BTCMiner.rpc[i];
						rpc.resetStats();
					}
				} else if (value >= 0 && value < server) {
					RPC rpc = BTCMiner.rpc[value];
					rpc.resetStats();
				}
			} catch (NumberFormatException e) {
			}
		}
		return "<html><body></body></html>";
	}

	// ******* serveSelectRPC
	// *************************************************************************
	private String serveSelectRPC(Properties parms) {
		if (parms.getProperty("pool") != null && parms.getProperty("rpcname") != null && parms.getProperty("rpcurl") != null && parms.getProperty("rpchost") != null && parms.getProperty("rpcusr") != null && parms.getProperty("rpcpwd") != null && parms.getProperty("iomon") != null) {
			try {
				int pool = Integer.parseInt(parms.getProperty("pool"));
				int iomon = Integer.parseInt(parms.getProperty("iomon"));
				int server = BTCMiner.rpcCount;
				if (pool >= 0 && pool < server) {
					RPC rpc = BTCMiner.rpc[pool];
					rpc.name = parms.getProperty("rpcname");
					rpc.url = parms.getProperty("rpcurl");
					rpc.host = parms.getProperty("rpchost");
					rpc.usr = parms.getProperty("rpcusr");
					rpc.pwd = parms.getProperty("rpcpwd");
					rpc.ioDisableMonitorTime = iomon;
					if (BTCMiner.newBlockMonitor != null) {
						synchronized (BTCMiner.newBlockMonitor) {
							BTCMiner.disableLPTime = new Date().getTime() + 20000;
							BTCMiner.longPollURL = null;
							BTCMiner.longPollUser = "";
							BTCMiner.longPollPassw = "";
						}
					}
				}
			} catch (NumberFormatException e) {
			}
		}
		return "<html><body></body></html>";
	}
}

// *****************************************************************************
// ******* ParameterException **************************************************
// *****************************************************************************
// Exception the prints a help message
class ParameterException extends Exception {
	public final static String helpMsg = new String(
		"Parameters:\n" +
		"    -o <name> <web> <url> <user name> <password> <disable timeout>\n" +
		"                      Name, website, URL, user name and password of a server. Can be specified multiple times\n" +
		"    -b <name> <web> <url> <user name> <password> <disable timeout>\n" +
		"                      Name, website, URL, user name and password of a backup server. Can be specified multiple times\n" +
		"    -lp <url> <user name> <password> \n" +
		"                      URL, user name and password of a long polling server (determined automatically by default)\n" +
		"    -l <log file>     Log file (default: BTCMiner.log)\n" +
		"    -l2 <log file>    Secondary log file, logs everything but statistics\n" +
		"    -bl <log file>    Log of submitted blocks file\n" +
		"    -nolog            do not log to file\n" +
		"    -c <file name>    Secondary command input file, can be a named pipe\n" +
		"    -m s|t|p|c|h      Set single mode, test mode, programming mode, cluster mode or http-cluster mode\n" +
		"                      Single mode: runs BTCMiner on a single board (default mode)\n" +
		"                      Test mode: tests a board using some test data\n" +
		"                      Programming mode: programs device with the given firmware\n" +
		"                      (HTTP-)Cluster mode: runs BTCMiner on all programmed boards\n" +
		"    -ep0              Always use slow EP0 for Bitstream transfer\n" +
		"    -oh <number>      Overheat threshold: if the hash rate drops by that factor (but at least two frequency steps)\n" +
		"                      the overheat shutdown is triggered (default: 0.04, recommended: 0 to 0.08)\n" +
		"    -t <number>       Temperature limit (in \u00b0C, default 70\u00b0C)\n" +
		"    -ps <string>      Select devices with the given serial number,\n" +
		"                      in cluster mode: select devices which serial number starts with the given string\n" +
		"    -e <number>       Maximum error rate\n" +
		"    -tc               Enable target check (disabled by default)\n" +
		"    -ac <seconds>     automatically reset performance and error counters every given seconds\n" +
		"    -iomon <seconds>  Time used to detect successive IO-errors of dead/lagging pools (default: 20)\n" +
		"    -v                Be verbose\n" +
		"    -h                This help\n" +
		"Parameters in single mode, test mode and programming mode\n" +
		"    -d <number>       Device Number, see -i\n" +
		"    -f <ihx file>     Firmware file (required in programming mode)\n" +
		"    -i                Print bus info\n" +
		"Parameters in cluster mode\n" +
		"    -n <number>       Maximum amount of devices per thread (default: 10)\n" +
		"Parameters in httpd cluster mode\n" +
		"    -id <string>      Mining cluster ID shown on status page (default default)\n" +
		"    -p <number>       httpd port number (default: 8080)\n" +
		"Parameters in programming mode\n" +
		"    -pt <string>      Program devices of the given type\n" +
		"                      If neither -ps nor -ps is given, only unconfigured devices are programmed\n" +
		"    -s                Set serial number\n" +
		"    -rf               Erase firmware in EEPROM (overwrites -f, requires -pt or -ps)\n");

	public ParameterException(String msg) {
		super(msg + "\n" + helpMsg);
	}
}

// *****************************************************************************
// ******* ParserException *****************************************************
// *****************************************************************************
class ParserException extends Exception {
	public ParserException(String msg) {
		super(msg);
	}
}

// *****************************************************************************
// ******* FirmwareException ***************************************************
// *****************************************************************************
class FirmwareException extends Exception {
	public FirmwareException(String msg) {
		super(msg);
	}
}

// *****************************************************************************
// ******* MsgObj **************************************************************
// *****************************************************************************
interface MsgObj {
	public void msg(String s);

	public void rejectReason(String s);
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

	public static boolean submitOld;

	// ******* Constructor
	// *************************************************************************
	public NewBlockMonitor() {
		start();
	}

	// ******* checkNew
	// *************************************************************************
	public synchronized boolean checkNew(byte[] data) throws NumberFormatException {
		if (data.length < 36)
			throw new NumberFormatException("Invalid length of data");

		boolean n = false;

		for (int i = 0; i < 32; i++) {
			n = n | (data[i + 4] != prevBlock[i]);
			prevBlock[i] = data[i + 4];
		}
		if (n) {
			newCount += 1;
			submitOld = true;
			if (newCount > 0) {
				msg("New block detected by block monitor");
			}
		}

		return n;
	}

	// ******* run
	// *************************************************************************
	public void run() {
		running = true;

		boolean enableLP = true;
		boolean warnings = true;
		long enableLPTime = 0;

		submitOld = true;

		while (running) {
			long t = new Date().getTime();

			if (BTCMiner.longPollURL != null && enableLP && t > enableLPTime) {
				try {
					//		    msg("info: LP");
					String req = BTCMiner.bitcoinRequest(this, BTCMiner.longPollURL, BTCMiner.longPollUser, BTCMiner.longPollPassw, "getwork", "");
					BTCMiner.hexStrToData(BTCMiner.jsonParse(req, "data"), dataBuf);
					submitOld = true;
					String so = null;
					try {
						so = BTCMiner.jsonParse(req, "submitold");
						if (so.equalsIgnoreCase("false")) {
							submitOld = false;
						}
					} catch (Exception e) {
					}

					for (int i = 0; i < 32; i++) {
						prevBlock[i] = dataBuf[i + 4];
					}
					newCount += 1;
					msg("New block detected by long polling" + (so == null ? "" : " (submitold = " + so + ")"));
				} catch (MalformedURLException e) {
					msg("Warning: " + e.getLocalizedMessage() + ": disabling long polling");
					enableLP = false;
				} catch (IOException e) {
					if (new Date().getTime() < t + 500) {
						msg("Warning: " + e.getLocalizedMessage() + ": disabling long polling fo 60s");
						enableLPTime = new Date().getTime() + 60000;
					}
				} catch (Exception e) {
					if (warnings) {
						msg("Warning: " + e.getLocalizedMessage());
					}
					warnings = false;
				}
			}

			if (BTCMiner.longPollURL == null) {
				enableLPTime = new Date().getTime() + 2000;
			}
			t += minLongPollInterval - new Date().getTime();
			if (t > 5) {
				try {
					Thread.sleep(t);
				} catch (InterruptedException e) {
				}
			}
		}
		// System.out.println("Stopping block monitor");
	}

	// ******* msg
	// *************************************************************************
	public void msg(String s) {
		synchronized (logBuf) {
			logBuf.add(new LogString(s));
		}
	}

	// ******* rejectReason
	// *************************************************************************
	public void rejectReason(String s) {
	}

	// ******* print
	// *************************************************************************
	public void print() {
		synchronized (logBuf) {
			for (int j = 0; j < logBuf.size(); j++) {
				LogString ls = logBuf.elementAt(j);
				System.out.println(ls.msg);
				if (BTCMiner.logFile != null) {
					BTCMiner.logFile.println(BTCMiner.dateFormat.format(ls.time) + ": " + ls.msg);
				}
				if (BTCMiner.logFile2 != null && !ls.msg.substring(0, 18).equals("New block detected")) {
					BTCMiner.logFile2.println(BTCMiner.dateFormat.format(ls.time) + ": " + ls.msg);
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

	// ******* Constructor
	// *************************************************************************
	public BTCMinerThread(String bn) {
		busName = bn;
	}

	// ******* add
	// *************************************************************************
	public void add(BTCMiner m) {
		synchronized (miners) {
			miners.add(m);
/* xxx
			m.name = busName + ": " + m.name;
*/
		}

		if (pollLoop == null) {
			BTCMiner.printMsg2("Starting mining thread for bus " + busName);
			start();
		}
	}

	// ******* size
	// *************************************************************************
	public int size() {
		return miners.size();
	}

	// ******* elementAt
	// *************************************************************************
	public BTCMiner elementAt(int i) {
		return miners.elementAt(i);
	}

	// ******* find
	// *************************************************************************
	public BTCMiner find(int dn) {
		for (int i = 0; i < miners.size(); i++) {
			if ((miners.elementAt(i).ztex().dev().dev().getDevnum() == dn)) {
				return miners.elementAt(i);
			}
		}
		return null;
	}

	// ******* busName
	// *************************************************************************
	public String busName() {
		return busName;
	}

	// ******* running
	// *************************************************************************
	public boolean running() {
		return pollLoop != null;
	}

	// ******* run
	// *************************************************************************
	public void run() {
		pollLoop = new PollLoop(miners);
		pollLoop.run();
		pollLoop = null;
	}

	// ******* printInfo
	// *************************************************************************
	public void printInfo() {
		if (pollLoop != null) {
			pollLoop.printInfo(busName);
		}
	}

	// ******* disconnect
	// *************************************************************************
	public int disconnect(String ss, Vector<BTCMiner> allMiners) {
		int i = 0;
		synchronized (miners) {
			for (int j = miners.size() - 1; j >= 0; j--) {
				BTCMiner m = miners.elementAt(j);
				if (ss.equals(m.ztex().dev().snString())) {
					BTCMiner.printMsg("Disconnecting " + m.name);
					if (allMiners != null) {
						allMiners.removeElement(m);
					}
					m.suspend();
					miners.removeElementAt(j);
					i += 1;
				}
			}
		}
		return i;
	}
}

// *****************************************************************************
// ******* BTCMinerCluster *****************************************************
// *****************************************************************************
class BTCMinerCluster {
	public static int maxDevicesPerThread = 10;
	public static boolean verbose = false;
	public static long autoResetInterval = 0;

	private Vector<BTCMinerThread> threads = new Vector<BTCMinerThread>();
	private Vector<BTCMiner> allMiners = new Vector<BTCMiner>();

	// ******* totalHashrate
	// *************************************************************************
	public double totalHashrate() {
		double h = 0.0;
		for (int i = 0; i < allMiners.size(); i++) {
			BTCMiner m = allMiners.elementAt(i);
			h += m.totalHashRate();
		}
		return h;
	}

	// ******* submittedHashrate
	// *************************************************************************
	public double submittedHashrate() {
		double h = 0.0;
		for (int i = 0; i < allMiners.size(); i++) {
			BTCMiner m = allMiners.elementAt(i);
			h += m.submittedHashRate();
		}
		return h;
	}

	// ******* getMiner
	// *************************************************************************
	public BTCMiner getMiner(int index) {
		if (index < 0 || index >= allMiners.size()) {
			return null;
		}
		BTCMiner m = allMiners.elementAt(index);
		return m;
	}

	// ******* Constructor
	// *************************************************************************
	public BTCMinerCluster() {
		scan(verbose);
	}

	// ******* run
	// *************************************************************************
	public void run() {
		final long infoInterval = 300000;

		long nextInfoTime = new Date().getTime() + 60000;
		long nextResetTime = new Date().getTime() + (3 * 60 * 1000); // first auto reset after 3 minutes

		boolean quit = false;
		while (threads.size() > 0 && !quit) {
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
			}

			BTCMiner.newBlockMonitor.print();
			for (int i = 0; i < allMiners.size(); i++) {
				allMiners.elementAt(i).print();
			}

			if (new Date().getTime() > nextInfoTime) {
				double d = 0.0;
				double e = 0.0;
				for (int i = 0; i < allMiners.size(); i++) {
					BTCMiner m = allMiners.elementAt(i);
					m.printInfo(true);
					d += m.submittedHashRate();
					e += m.totalHashRate();
				}
/* xxx
*/
				for ( int i=0; i<threads.size(); i++ ) {
					threads.elementAt(i).printInfo();
				}
				BTCMiner.printMsg2("Total hash rate: " + String.format("%.1f", e) + " MH/s");
				BTCMiner.printMsg2("Total submitted hash rate: " + String.format("%.1f", d) + " MH/s");
				BTCMiner.printMsg2(" -------- ");
				nextInfoTime = new Date().getTime() + infoInterval;
			}

			if (autoResetInterval > 0 && new Date().getTime() > nextResetTime) {
				for (int i = allMiners.size() - 1; i >= 0; i--) {
					allMiners.elementAt(i).resetCounters();
				}
				BTCMiner.printMsg2("Auto reset all performance end error counters.");
				nextResetTime = new Date().getTime() + autoResetInterval;
			}

			for (int i = threads.size() - 1; i >= 0; i--) {
				BTCMinerThread t = threads.elementAt(i);
				if (!t.running()) {
					BTCMiner.printMsg2("Stopped thread for bus " + t.busName());
					threads.removeElementAt(i);
				}
			}

			try {
				StringBuffer sb = new StringBuffer();
				while (System.in.available() > 0) {
					int j = System.in.read();
					if (j > 32) {
						sb.append((char) j);
					}
				}

				if (sb.length() == 0 && BTCMiner.in2 != null) {
					while (BTCMiner.in2.available() > 0) {
						int j = BTCMiner.in2.read();
						if (j > 32) {
							sb.append((char) j);
						}
					}
				}

				String cmd = sb.toString();

				if (cmd.length() < 1) {
				} else if (cmd.equalsIgnoreCase("q") || cmd.equalsIgnoreCase("quit")) {
					for (int i = allMiners.size() - 1; i >= 0; i--) {
						allMiners.elementAt(i).suspend();
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
						}
					}
					quit = true;
				} else if (cmd.equalsIgnoreCase("r") || cmd.equalsIgnoreCase("rescan")) {
					scan(verbose);
				} else if (cmd.equalsIgnoreCase("s") || cmd.equalsIgnoreCase("suspend")) {
					long t = new Date().getTime();
					int j = 0;
					for (int i = allMiners.size() - 1; i >= 0; i--) {
						if (allMiners.elementAt(i).suspend()) {
							j++;
						}
						allMiners.elementAt(i).startTimeAdjust = t;
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
						}
					}
					BTCMiner.printMsg2("Suspended " + j + " of " + allMiners.size() + " miners. Enter `r' to resume.");
				} else if (cmd.equalsIgnoreCase("c") || cmd.equalsIgnoreCase("counter_reset")) {
					for (int i = allMiners.size() - 1; i >= 0; i--) {
						allMiners.elementAt(i).resetCounters();
					}
					BTCMiner.printMsg2("Reset all performance end error counters.");
				} else if (cmd.equalsIgnoreCase("i") || cmd.equalsIgnoreCase("info")) {
					nextInfoTime = 0;
				} else if ( cmd.charAt(0) == 'd' || cmd.charAt(0) == 'D' ) {
					int i = (cmd.length() >= 10 && cmd.substring(0, 10).equalsIgnoreCase("disconnect")) ? 10 : 1;
					while (i < cmd.length() && cmd.charAt(i) <= ' ') {
						i++;
					}
					int j = cmd.length() - 1;
					while (j >= i && cmd.charAt(j) <= ' ') {
						j--;
					}
					if (i <= j) {
						String ss = BTCMiner.checkSnString(cmd.substring(i, j + 1));
						j = 0;
						for (i = threads.size() - 1; i >= 0; i--)  {
							j += threads.elementAt(i).disconnect(ss, allMiners);
						}
						System.out.println("Disconnected " + j + " miners");
					} else {
						System.out.println("No serial number specified");
					}
				} else if (cmd.equalsIgnoreCase("h") || cmd.equalsIgnoreCase("help")) {
					System.out.println("q(uit)                         Exit BTCMiner");
					System.out.println("r(escan)                       Rescan bus");
					System.out.println("c(ounter_reset)                Reset performance and error counters");
					System.out.println("s(uspend)                      Suspend cluster");
					System.out.println("d(isconnect) <serial nunmber>  Disconnect device");
					System.out.println("i(nfo)                         Print cluster informations");
					System.out.println("h(elp)                         Print this help");
				} else {
					System.out.println("Invalid command: `" + cmd + "', enter `h' for help");
				}
			} catch (Exception e) {
			}
		}
		// BTCMiner.newBlockMonitor.running = false;
	}

	// ******* add
	// *************************************************************************
	private void add(BTCMiner m) {
		int i = 0, j = 0;
		String bn = m.ztex().dev().dev().getBus().getDirname() + "-" + j;
		while (i < threads.size()) {
			BTCMinerThread t = threads.elementAt(i);
			if (bn.equalsIgnoreCase(threads.elementAt(i).busName())) {
				if (t.size() < maxDevicesPerThread) {
					break;
				}
				j++;
				i = 0;
				bn = m.ztex().dev().dev().getBus().getDirname() + "-" + j;
			} else {
				i++;
			}
		}

		if (i >= threads.size()) {
			threads.add(new BTCMinerThread(bn));
		}
		threads.elementAt(i).add(m);
	}

	// ******* find
	// *************************************************************************
	private BTCMiner find(ZtexDevice1 dev) {
		int dn = dev.dev().getDevnum();
		String bn = dev.dev().getBus().getDirname();
		for (int i = threads.size() - 1; i >= 0; i--) {
			BTCMiner m = threads.elementAt(i).find(dn);
			if (m != null && bn.equals(m.ztex().dev().dev().getBus().getDirname())) {
				return m;
			}
		}
		return null;
	}

	// ******* insertIntoAllMiners
	// *************************************************************************
	private void insertIntoAllMiners(BTCMiner m) {
		int j = 0;
		while (j < allMiners.size() && m.name.compareTo(allMiners.elementAt(j).name) >= 0) {
			j++;
		}
		allMiners.insertElementAt(m, j);
	}

	// ******* scan
	// *************************************************************************
	private void scan(boolean verbose) {
		long t = new Date().getTime();

		allMiners.clear();
		for (int i = threads.size() - 1; i >= 0; i--) {
			BTCMinerThread mt = threads.elementAt(i);
			for (int j = mt.size() - 1; j >= 0; j--) {
				BTCMiner m = mt.elementAt(j);
				insertIntoAllMiners(m);
				if (m.suspended) {
					m.suspended = false;
					m.isRunning = false;
					try {
						Thread.sleep(20);
					} catch (InterruptedException e) {
					}
					BTCMiner.printMsg2(m.name + ": resuming");
				} else {
					m.startTimeAdjust = t;
					BTCMiner.printMsg2(m.name + ": already running");
				}
			}
		}

		BTCMiner.printMsg2("\n(Re)Scanning bus ... ");

		PollLoop.scanMode = true;

		ZtexScanBus1 bus = new ZtexScanBus1(ZtexDevice1.ztexVendorId, ZtexDevice1.ztexProductId, false, false, 1, null, 10, 0, 1, 0);
		int k = 0;
		int l = 0;
		for (int i = 0; i < bus.numberOfDevices(); i++) {
			try {
				ZtexDevice1 dev = bus.device(i);
				if (dev.productId(0) != 10 || dev.productId(2) > 1) {
					break;
				}
				if (BTCMiner.filterSN == null || dev.snString().substring(0, BTCMiner.filterSN.length()).equals(BTCMiner.filterSN)) {
					k += 1;
					BTCMiner m = find(dev);
					if (m == null) {
						l += 1;
						m = new BTCMiner(dev, null, verbose);
						m.clusterMode = true;
						add(m);
						BTCMiner.printMsg(m.name + ": added");
						insertIntoAllMiners(m);

						for (int j = 1; j < m.numberOfFpgas(); j++) {
							BTCMiner n = new BTCMiner(m.ztex(), m.fpgaNum(j), verbose);
							n.clusterMode = true;
							add(n);
							BTCMiner.printMsg(n.name + ": added");
							insertIntoAllMiners(n);
						}
					}
				}
			} catch (Exception e) {
				BTCMiner.printMsg("Error: " + e.getLocalizedMessage());
			}
		}

		if (k == 0) {
			System.err.println("No devices found. At least one device has to be connected.");
			System.exit(0);
		}
		BTCMiner.printMsg2("" + l + " new devices found.");

		t = new Date().getTime();
		for (int i = 0; i < allMiners.size(); i++) {
			allMiners.elementAt(i).startTime += t - allMiners.elementAt(i).startTimeAdjust;
		}
		PollLoop.scanMode = false;

		BTCMiner.printMsg2("\nSummary: ");
		for (int i = 0; i < threads.size(); i++) {
			BTCMiner.printMsg2("  Bus " + threads.elementAt(i).busName() + "\t: " + threads.elementAt(i).size() + " miners");
		}
		BTCMiner.printMsg2("  Total  \t: " + allMiners.size() + " miners\n");
		BTCMiner.printMsg2("\nDisconnect all devices or enter `q' for exit. Enter `h' for help.\n");

		BTCMiner.connectionEffort = 1.0 + Math.exp((1.0 - Math.sqrt(Math.min(allMiners.size(), maxDevicesPerThread) * allMiners.size())) / 13.0);
	}
}

// *****************************************************************************
// ******* LogString ***********************************************************
// *****************************************************************************
class LogString {
	public Date time;
	public String msg;

	// ******* Constructor
	// *************************************************************************
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

	// ******* Constructor
	// *************************************************************************
	public PollLoop(Vector<BTCMiner> pv) {
		v = pv;
	}

	// ******* run
	// *************************************************************************
	public void run() {
		while (v.size() > 0) {
			long t0 = new Date().getTime();
			long tu = 0;

			if (!scanMode) {
				synchronized (v) {
					for (int i = v.size() - 1; i >= 0; i--) {
						BTCMiner m = v.elementAt(i);
						m.usbTime = 0;
						try {
							if (!m.suspended) {
								if (m.checkUpdate() && m.getWork()) { // getwork calls getNonces
									//m.dmsg("Got new work");
									m.sendData();
								} else {
									m.getNonces();
								}
								m.updateFreq();
								m.printInfo(false);
							}
						} catch (IOException e) {
							RPC rpc = BTCMiner.rpc[m.rpcNum];
							m.msg("Error: " + e.getLocalizedMessage());
							rpc.disable();
						} catch (ParserException e) {
							RPC rpc = BTCMiner.rpc[m.rpcNum];
							m.msg("Error: " + e.getLocalizedMessage());
							rpc.disable();
						} catch (NumberFormatException e) {
							RPC rpc = BTCMiner.rpc[m.rpcNum];
							m.msg("Error: " + e.getLocalizedMessage());
							rpc.disable();
						} catch (IndexOutOfBoundsException e) {
							RPC rpc = BTCMiner.rpc[m.rpcNum];
							m.msg("Error: " + e.getLocalizedMessage());
							rpc.disable();
						} catch (Exception e) {
							m.msg("Error: " + e.getLocalizedMessage() + ": Disabling device");
							m.fatalError = "Error: " + e.getLocalizedMessage() + ": Device disabled since " + BTCMiner.dateFormat.format(new Date());
							v.removeElementAt(i);
						}

						tu += m.usbTime;

						if (!m.clusterMode) {
							BTCMiner.newBlockMonitor.print();
						}
					}
				}

				t0 = new Date().getTime() - t0;
				usbTime = usbTime * 0.9998 + tu;
				networkTime = networkTime * 0.9998 + t0 - tu;
				timeW = timeW * 0.9998 + 1;
			} else {
				t0 = 0;
			}

			t0 = minQueryInterval - t0;
			if (t0 > 5) {
				try {
					Thread.sleep(t0);
				} catch (InterruptedException e) {
				}
			}
		}
	}

	// ******* printInfo
	// *************************************************************************
	public void printInfo(String name) {
		int oc = 0;
		double gt = 0.0, gtw = 0.0, st = 0.0, stw = 0.0;
		for (int i = v.size() - 1; i >= 0; i--) {
			BTCMiner m = v.elementAt(i);
			oc += m.overflowCount;
			m.overflowCount = 0;

			st += m.submitTime;
			stw += m.submitTimeW;

			gt += m.getTime;
			gtw += m.getTimeW;
		}

		BTCMiner.printMsg2(name + ": poll loop time: " + Math.round((usbTime + networkTime) / timeW) + "ms (USB: " + Math.round(usbTime / timeW) + "ms network: " + Math.round(networkTime / timeW) + "ms)   getwork time: " + Math.round(gt / gtw) + "ms  submit time: " + Math.round(st / stw) + "ms");
		if (oc > 0) {
			BTCMiner.printMsg(name + ": Warning: " + oc + " overflows occured. This is usually caused by a slow network connection.");
		}
	}
}

// *****************************************************************************
// ******* RPC *****************************************************************
// *****************************************************************************
class RPC {
	public String		name;
	public String		url;
	public String		host;
	public String		usr;
	public String		pwd;
	public boolean		mine = false;
	public int		sharesGetwork;
	public int		sharesAccepted;
	public int		sharesRejected;
	public int		poolDisabled;
	public double		difficulty;

	public int		ioDisableMonitorTime;
	static final int	ioDisableTime = 10;
	int[]			ioDisableTimeEffort = { 1, 1, 2, 2, 2, 3, 4, 5, 6, 9, 12, 12, 18, 18, 30, 30, 60, 60, 90, 120, 180, 360 };
	int			ioDisableCount;
	long			disableTime;

	public RPC(String name, String url, String host, String usr, String pwd, boolean mine) {
		this.name = name;
		this.url = url;
		this.host = host;
		this.usr = usr;
		this.pwd = pwd;
		this.mine = mine;

		sharesGetwork = 0;
		sharesAccepted = 0;
		sharesRejected = 0;
		poolDisabled = 0;
		difficulty = 1.0;
		disableTime = 0;
		ioDisableMonitorTime = 20;
		ioDisableCount = 0;
	}

	public synchronized void disable() {
		long t = new Date().getTime();

		if (t > disableTime) {
			if (t < (disableTime + ioDisableMonitorTime * 1000)) {
				ioDisableCount += 1;
			} else if (t > (disableTime + 10 * ioDisableMonitorTime * 1000)) {
				ioDisableCount = 0;
			}

			int i = ioDisableCount;
			if (i > (ioDisableTimeEffort.length - 1)) {
				i = ioDisableTimeEffort.length - 1;
			}

			BTCMiner.printMsg("Disabling URL " + host + " for " + (ioDisableTimeEffort[i] * ioDisableTime) + "s");
			disableTime = t + ioDisableTimeEffort[i] * ioDisableTime * 1000;
			poolDisabled += 1;
		}
	}

	public synchronized void enable() {
		disableTime = 0;
		ioDisableCount = 0;
	}

	public synchronized boolean disabled() {
		return disableTime > new Date().getTime();
	}

	public void resetStats() {
		sharesGetwork = 0;
		sharesAccepted = 0;
		sharesRejected = 0;
		poolDisabled = 0;
	}
}

// *****************************************************************************
// ******* BTCMiner ************************************************************
// *****************************************************************************
class BTCMiner implements MsgObj {

	// ******* static methods
	// *************************************************************************
	static BTCMinerCluster cluster = null;
	static String clusterId = "default";
	static final int maxRpcCount = 32;
	static RPC[] rpc = new RPC[maxRpcCount];
	static int rpcCount = 0;
	static int rpcFirstBackup = 0;

	static int idGlob = 0;

	static String longPollURL = null;
	static String longPollUser = "";
	static String longPollPassw = "";
	static long disableLPTime = 0;

	static int bcid = -1;

	static String firmwareFile = null;
	static boolean printBus = false;

	public final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	static PrintStream logFile = null;
	static PrintStream logFile2 = null;
	static PrintStream blkLogFile = null;

	static InputStream in2 = null;
	static String in2FileName = null;

	static double connectionEffort = 2.0;

	static NewBlockMonitor newBlockMonitor = null;

	static boolean forceEP0Config = false;

	static double overheatThreshold = 0.04;

	static double maxMaxErrorRate = 0.05;

	static double tempLimit = 65;  // in degree celsius

	static boolean targetCheck = false;

	static String filterSN = null;

	public static long upTime;

	public static final String[] dummyFirmwareNames = { "USB-FPGA Module 1.15d (default)", "USB-FPGA Module 1.15x (default)", "USB-FPGA Module 1.15y (default)" };

	public static final int[] defaultFirmwarePID1 = { 13, 13, 15 };

	public static final String[] firmwareFiles = { "ztex_ufm1_15d4.ihx", "ztex_ufm1_15d4.ihx", "ztex_ufm1_15y1.ihx" };

	public static final byte[] sha256_init_state = hexStrToData("67e6096a85ae67bb72f36e3c3af54fa57f520e518c68059babd9831f19cde05b");
	public static final byte[] sha256_pad1 = hexStrToData("000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000");

	public static final double DIFFEXACTONE = 26959946667150639794667015087019630673637144422540572481103610249216.0;

	public static synchronized int getId() {
		int	id;

		id = idGlob;
		idGlob++;
		return id;
	}

	// ******* printMsg
	// *************************************************************************
	public static void printMsg(String msg) {
		System.out.println(msg);
		if (logFile != null) {
			logFile.println(dateFormat.format(new Date()) + ": " + msg);
		}
		if (logFile2 != null) {
			logFile2.println(dateFormat.format(new Date()) + ": " + msg);
		}
	}

	// ******* printMsg2
	// *************************************************************************
	public static void printMsg2(String msg) {
		System.out.println(msg);
		if (logFile != null) {
			logFile.println(dateFormat.format(new Date()) + ": " + msg);
		}
	}

	// ******* encodeBase64
	// *************************************************************************
	public static String encodeBase64(String s) {
		return encodeBase64(s.getBytes());
	}

	public static String encodeBase64(byte[] src) {
		return encodeBase64(src, 0, src.length);
	}

	public static String encodeBase64(byte[] src, int start, int length) {
		final String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
		byte[] encodeData = new byte[64];
		byte[] dst = new byte[(length + 2) / 3 * 4 + length / 72];
		int x = 0;
		int dstIndex = 0;
		int state = 0;
		int old = 0;
		int len = 0;
		int max = length + start;

		for (int i = 0; i < 64; i++) {
			byte c = (byte) charSet.charAt(i);
			encodeData[i] = c;
		}

		for (int srcIndex = start; srcIndex < max; srcIndex++) {
			x = src[srcIndex];
			switch (++state) {
			case 1:
				dst[dstIndex++] = encodeData[(x >> 2) & 0x3f];
				break;
			case 2:
				dst[dstIndex++] = encodeData[((old << 4) & 0x30) | ((x >> 4) & 0xf)];
				break;
			case 3:
				dst[dstIndex++] = encodeData[((old << 2) & 0x3C) | ((x >> 6) & 0x3)];
				dst[dstIndex++] = encodeData[x & 0x3F];
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
		case 1:
			dst[dstIndex++] = encodeData[(old << 4) & 0x30];
			dst[dstIndex++] = (byte) '=';
			dst[dstIndex++] = (byte) '=';
			break;
		case 2:
			dst[dstIndex++] = encodeData[(old << 2) & 0x3c];
			dst[dstIndex++] = (byte) '=';
			break;
		}
		return new String(dst);
	}

	// ******* hexStrToData
	// *************************************************************************
	public static byte[] hexStrToData(String str) throws NumberFormatException {
		if (str.length() % 2 != 0) {
			throw new NumberFormatException("Invalid length of string");
		}
		byte[] buf = new byte[str.length() >> 1];
		for (int i = 0; i < buf.length; i++) {
			buf[i] = (byte) Integer.parseInt(str.substring(i * 2, i * 2 + 2), 16);
		}
		return buf;
	}

	public static void hexStrToData(String str, byte[] buf) throws NumberFormatException {
		if (str.length() < buf.length * 2) {
			throw new NumberFormatException("Invalid length of string");
		}
		for (int i = 0; i < buf.length; i++) {
			buf[i] = (byte) Integer.parseInt(str.substring(i * 2, i * 2 + 2), 16);
		}
	}

	// ******* hexStrToData2
	// *************************************************************************
	public static void hexStrToData2(String str, byte[] buf) throws NumberFormatException {
		if (str.length() < buf.length * 2) {
			throw new NumberFormatException("Invalid length of string");
		}
		for (int i = 0; i < buf.length; i++) {
			buf[i] = (byte) (Integer.parseInt(str.substring(i * 2, i * 2 + 1), 16) + Integer.parseInt(str.substring(i * 2 + 1, i * 2 + 2), 16) * 16);
		}
	}

	// ******* dataToHexStr
	// *************************************************************************
	public static String dataToHexStr(byte[] data) {
		final char hexchars[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
		char[] buf = new char[data.length * 2];
		for (int i = 0; i < data.length; i++) {
			buf[i * 2 + 0] = hexchars[(data[i] & 255) >> 4];
			buf[i * 2 + 1] = hexchars[(data[i] & 15)];
		}
		return new String(buf);
	}

	// ******* dataToInt
	// *************************************************************************
	public static int dataToInt(byte[] buf, int offs) {
		if (offs + 4 > buf.length) {
			throw new NumberFormatException("Invalid length of data");
		}
		return (buf[offs + 0] & 255) | ((buf[offs + 1] & 255) << 8) | ((buf[offs + 2] & 255) << 16) | ((buf[offs + 3] & 255) << 24);
	}

	// ******* intToData
	// *************************************************************************
	public static byte[] intToData(int n) {
		byte[] buf = new byte[4];
		buf[0] = (byte) (n & 255);
		buf[1] = (byte) ((n >> 8) & 255);
		buf[2] = (byte) ((n >> 16) & 255);
		buf[3] = (byte) ((n >> 24) & 255);
		return buf;
	}

	public static void intToData(int n, byte[] buf, int offs) {
		buf[offs + 0] = (byte) (n & 255);
		buf[offs + 1] = (byte) ((n >> 8) & 255);
		buf[offs + 2] = (byte) ((n >> 16) & 255);
		buf[offs + 3] = (byte) ((n >> 24) & 255);
	}

	// ******* intToHexStr
	// *************************************************************************
	public static String intToHexStr(int n) {
		return dataToHexStr(reverse(intToData(n)));
	}

	// ******* reverse
	// *************************************************************************
	public static byte[] reverse(byte[] data) {
		byte[] buf = new byte[data.length];
		for (int i = 0; i < data.length; i++) {
			buf[data.length - i - 1] = data[i];
		}
		return buf;
	}

	// ******* jsonParse
	// *************************************************************************
	// does not work if parameter name is a part of a parameter value
	public static String jsonParse(String response, String parameter) throws ParserException {
		int lp = parameter.length();
		int i = 0;
		while (i + lp < response.length() && !parameter.equalsIgnoreCase(response.substring(i, i + lp))) {
			i++;
		}
		i += lp;
		if (i >= response.length()) {
			throw new ParserException("jsonParse: Parameter `" + parameter + "' not found");
		}
		while (i < response.length() && response.charAt(i) != ':') {
			i++;
		}
		i += 1;
		while (i < response.length() && (byte) response.charAt(i) <= 32) {
			i++;
		}
		if (i >= response.length()) {
			throw new ParserException("jsonParse: Value expected after `" + parameter + "'");
		}
		int j = i;
		if (i < response.length() && response.charAt(i) == '"') {
			i += 1;
			j = i;
			while (j < response.length() && response.charAt(j) != '"') {
				j++;
			}
			if (j >= response.length()) {
				throw new ParserException("jsonParse: No closing `\"' found for value of paramter `" + parameter + "'");
			}
		} else {
			while (j < response.length() && response.charAt(j) != ',' && response.charAt(j) != /* { */'}' && response.charAt(j) != '\n') {
				j++;
			}
		}
		return response.substring(i, j);
	}

	// ******* checkSnString
	// *************************************************************************
	// make sure that snString is 10 chars long
	public static String checkSnString(String snString) {
		if (snString.length() > 10) {
			snString = snString.substring(0, 10);
			System.err.println("Serial number too long (max. 10 characters), truncated to `" + snString + "'");
		}
		while (snString.length() < 10) {
			snString = '0' + snString;
		}
		return snString;
	}

	// ******* getType
	// *************************************************************************
	private static String getType(ZtexDevice1 pDev) {
		byte[] buf = new byte[64];
		try {
			Ztex1v1 ztex = new Ztex1v1(pDev);
			ztex.vendorRequest2(0x82, "Read descriptor", 0, 0, buf, 64);
			if (buf[0] < 1 || buf[0] > 5) {
				throw new FirmwareException("Invalid BTCMiner descriptor version");
			}
			int i0 = buf[0] > 4 ? 11 : (buf[0] > 2 ? 10 : 8);
			int i = i0;
			while (i < 64 && buf[i] != 0) {
				i++;
			}
			if (i < i0 + 1) {
				throw new FirmwareException("Invalid bitstream file name");
			}
			return new String(buf, i0, i - i0);
		} catch (Exception e) {
			System.out.println("Warning: " + e.getLocalizedMessage());
		}
		return null;
	}

	// ******* sha256_transform
	// *************************************************************************
	public static void sha256_transform(byte[] state, int state_offs, byte[] data, int data_offs, byte[] out, int out_offs) throws NumberFormatException {
		if (state.length < state_offs + 32) {
			throw new NumberFormatException("Invalid length of state");
		}
		if (data.length < data_offs + 64) {
			throw new NumberFormatException("Invalid length of data");
		}
		if (out.length < out_offs + 32) {
			throw new NumberFormatException("Invalid length of out");
		}

		final int[] k = { 0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2 };

		int A = dataToInt(state, state_offs + 0);
		int B = dataToInt(state, state_offs + 4);
		int C = dataToInt(state, state_offs + 8);
		int D = dataToInt(state, state_offs + 12);
		int E = dataToInt(state, state_offs + 16);
		int F = dataToInt(state, state_offs + 20);
		int G = dataToInt(state, state_offs + 24);
		int H = dataToInt(state, state_offs + 28);
		int T, T2;
		int[] wBuf = new int[64];

		for (int i = 0; i < 16; i++) {
			wBuf[i] = dataToInt(data, data_offs + 4 * i);
		}
		for (int i = 16; i < 64; i++) {
			T = wBuf[i - 2];
			T2 = wBuf[i - 15];
			wBuf[i] = (((T >>> 17) | (T << 15)) ^ ((T >>> 19) | (T << 13)) ^ (T >>> 10)) + wBuf[i - 7] + (((T2 >>> 7) | (T2 << 25)) ^ ((T2 >>> 18) | (T2 << 14)) ^ (T2 >>> 3)) + wBuf[i - 16];
		}

		for (int i = 0; i < 64; i++) {
			T = H + (((E >>> 6) | (E << 26)) ^ ((E >>> 11) | (E << 21)) ^ ((E >>> 25) | (E << 7))) + ((E & F) ^ (~E & G)) + k[i] + wBuf[i];
			T2 = (((A >>> 2) | (A << 30)) ^ ((A >>> 13) | (A << 19)) ^ ((A >>> 22) | (A << 10))) + ((A & B) ^ (A & C) ^ (B & C));
			H = G;
			G = F;
			F = E;
			E = D + T;
			D = C;
			C = B;
			B = A;
			A = T + T2;
		}

		intToData(A + dataToInt(state, state_offs + 0), out, out_offs + 0);
		intToData(B + dataToInt(state, state_offs + 4), out, out_offs + 4);
		intToData(C + dataToInt(state, state_offs + 8), out, out_offs + 8);
		intToData(D + dataToInt(state, state_offs + 12), out, out_offs + 12);
		intToData(E + dataToInt(state, state_offs + 16), out, out_offs + 16);
		intToData(F + dataToInt(state, state_offs + 20), out, out_offs + 20);
		intToData(G + dataToInt(state, state_offs + 24), out, out_offs + 24);
		intToData(H + dataToInt(state, state_offs + 28), out, out_offs + 28);
	}

	// ******* printBus
	// *************************************************************************
	public static void printBus (ZtexScanBus1 bus) {
		for (int i=0; i<bus.numberOfDevices(); i++) {
			ZtexDevice1 dev = bus.device(i);
			System.out.println(i + ": " + dev.toString());
			try {
				byte buf[] = new byte[6];
				new Ztex1v1(dev).macRead(buf);
				System.out.println("   MAC address: " + dataToHexStr(buf)); 
			} catch (Exception e) {
			}
		}
	}

	// ******* getTargetCheck
	// *************************************************************************
	public static boolean getTargetCheck() {
		return targetCheck;
	}

	// ******* setTargetCheck
	// *************************************************************************
	public static void setTargetCheck(boolean value) {
		targetCheck = value;
	}




	// ******* non-static methods
	// *************************************************************************
	private Ztex1v1 ztex = null;
	private int fpgaNum = 0;

	public int numNonces, offsNonces, freqM, freqMDefault, freqMaxM, extraSolutions;
	public double freqM1;
	public double hashesPerClock;
	private String bitFileName = null;
	public String name;
	public String fatalError = null;
	private boolean suspendSupported = false;

	public int id;

	private String lastRejectReason = "";

	public int rpcNum = 0;
	private int prevRpcNum = 0;

	public boolean verbose = false;
	public boolean clusterMode = false;

	public Vector<LogString> logBuf = new Vector<LogString>();

	private byte[] dataBuf = new byte[128];
	private byte[] dataBuf2 = new byte[128];
	private byte[] midstateBuf = new byte[32];
	private byte[] sendBuf = new byte[44];
	private byte[] hashBuf = hexStrToData("00000000000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000010000");
	private final String targetDiff1 = "0000000000000000000000000000000000000000000000000000ffff00000000";
	private String targetString;
	private byte[] targetBuf;
	private double difficulty = 1.0;

	private int newCount = 0;

	public boolean isRunning = false;
	public boolean suspended = false;

	MessageDigest digest = null;

	public int[] lastGoldenNonces = { 0, 0, 0, 0, 0, 0, 0, 0 };
	public int[] goldenNonce, nonce, hash7;
	public int submittedCount = 0;
	public double totalSubmittedCount = 0.0;
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
	public final double errorHysteresis = 0.1; // in frequency steps

	private double maxHashRate = 0;

	private int numberOfFpgas = 0;
	private int[] fpgaMap;

	// ******* Constructor
	// *************************************************************************
	public BTCMiner(Ztex1v1 pZtex, String firmwareFile, boolean v) throws UsbException, FirmwareException, NoSuchAlgorithmException {

		digest = MessageDigest.getInstance("SHA-256");
		verbose = v;

		ztex = pZtex;
		ztex.tempSensorUpdateInterval = 1000;
		ztex.enableExtraFpgaConfigurationChecks = true;

		String snString = null;
		if ((ztex.dev().productId(2) == 0) && (firmwareFile == null)) {
			for (int j = 0; j < defaultFirmwarePID1.length; j++) {
				if (defaultFirmwarePID1[j] == ztex.dev().productId(1) && ztex.dev().productString().equals(dummyFirmwareNames[j])) {
					firmwareFile = firmwareFiles[j];
				}
			}
			if (firmwareFile != null) {
				msg("Using firmware `" + firmwareFile + "'" + " for `" + ztex.dev().productString() +"'");
				snString = ztex.dev().snString();
			}
		}

		if (firmwareFile != null) {
			try {
				ZtexIhxFile1 ihxFile = new ZtexIhxFile1(firmwareFile);
				if (snString != null) {
					ihxFile.setSnString(snString);
				}
				ztex.uploadFirmware(ihxFile, false);
			} catch (Exception e) {
				throw new FirmwareException(e.getLocalizedMessage());
			}
		}

		if (!ztex.valid() || ztex.dev().productId(0) != 10 || ztex.dev().productId(2) != 1) {
			throw new FirmwareException("Wrong or no firmware");
		}

		getDescriptor();

		goldenNonce = new int[numNonces * (1 + extraSolutions)];
		nonce = new int[numNonces];
		hash7 = new int[numNonces];
		targetString = targetDiff1;
		targetBuf = hexStrToData(targetDiff1);

		name = bitFileName + "-" + ztex.dev().snString();
		//xxx name = ztex.dev().snString();
		msg("New device: " + descriptorInfo());
		try {
			byte buf[] = new byte[6];
			ztex.macRead(buf);
			msg("MAC address: " + dataToHexStr(buf));
		} catch (Exception e) {
			msg("No mac address support");
		}

		// long d = Math.round( 2500.0 / (freqM1 * (freqMaxM+1) * numNonces) * 1000.0 );
		// if ( d < maxPollInterval ) maxPollInterval=d;

		numberOfFpgas = 0;
		try {
			fpgaMap = new int[ztex.numberOfFpgas()];
			for (int i = 0; i < ztex.numberOfFpgas(); i++) {
				try {
					ztex.selectFpga(i);
					msg("FPGA " + (i + 1) + ": configuration time: " + (forceEP0Config ? ztex.configureFpgaLS("fpga/" + bitFileName + ".bit", true, 2) : ztex.configureFpga("fpga/" + bitFileName + ".bit", true, 2)) + " ms");
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
					}
					fpgaMap[numberOfFpgas] = i;
					numberOfFpgas += 1;
				} catch (Exception e) {
					msg("Error configuring FPGA " + i + ": " + e.getLocalizedMessage());
				}
			}
		} catch (InvalidFirmwareException e) {
			throw new FirmwareException(e.getLocalizedMessage());
		}

		if (numberOfFpgas < 1) {
			throw new FirmwareException("No FPGA's found");
		}

		fpgaNum = fpgaMap[0];
		name += "-" + (fpgaNum + 1);
		id = getId();
		msg("New FPGA");
		freqM = -1;
		updateFreq();

		lastInfoTime = new Date().getTime();

		for (int i = 0; i < 255; i++) {
			errorCount[i] = 0;
			errorWeight[i] = 0;
			errorRate[i] = 0;
			maxErrorRate[i] = 0;
		}
		maxHashRate = freqMDefault + 1.0;

		startTime = new Date().getTime();
		startTimeAdjust = startTime;

		if (newBlockMonitor == null) {
			newBlockMonitor = new NewBlockMonitor();
		}
	}

	public BTCMiner(ZtexDevice1 pDev, String firmwareFile, boolean v) throws UsbException, FirmwareException, NoSuchAlgorithmException {
		this (new Ztex1v1 (pDev), firmwareFile, v);
	}

	public BTCMiner(Ztex1v1 pZtex, int pFpgaNum, boolean v) throws UsbException, FirmwareException, NoSuchAlgorithmException {
		digest = MessageDigest.getInstance("SHA-256");
		verbose = v;

		ztex = pZtex;
		fpgaNum = pFpgaNum;

		if (!ztex.valid() || ztex.dev().productId(0) != 10 || ztex.dev().productId(2) != 1 || (ztex.dev().productId(3) < 1 && ztex.dev().productId(3) > 2)) {
			throw new FirmwareException("Wrong or no firmware");
		}

		getDescriptor();

		goldenNonce = new int[numNonces * (1 + extraSolutions)];
		nonce = new int[numNonces];
		hash7 = new int[numNonces];
		targetString = targetDiff1;
		targetBuf = hexStrToData(targetDiff1);

		name = bitFileName + "-" + ztex.dev().snString() + "-" + (fpgaNum + 1);
		//xxx name = ztex.dev().snString() + "-" + (fpgaNum + 1);
		id = getId();
		try {
			msg("New FPGA");
			freqM = -1;
			updateFreq();

			lastInfoTime = new Date().getTime();
		} catch (Exception e) {
			throw new FirmwareException(e.getLocalizedMessage());
		}

		for (int i = 0; i < 255; i++) {
			errorCount[i] = 0;
			errorWeight[i] = 0;
			errorRate[i] = 0;
			maxErrorRate[i] = 0;
		}
		maxHashRate = freqMDefault + 1.0;

		startTime = new Date().getTime();
		startTimeAdjust = startTime;
	}

	// ******* ztex
	// *************************************************************************
	public Ztex1v1 ztex() {
		return ztex;
	}

	// ******* numberofFpgas
	// *************************************************************************
	public int numberOfFpgas() {
		return numberOfFpgas;
	}

	// ******* selectFpga
	// *************************************************************************
	public void selectFpga() throws UsbException, InvalidFirmwareException, IndexOutOfBoundsException {
		ztex.selectFpga(fpgaNum);
	}

	// ******* fpgaNum
	// *************************************************************************
	public int fpgaNum() {
		return fpgaNum;
	}

	public int fpgaNum(int n) throws IndexOutOfBoundsException { // only valid for root miner
		if (n < 0 || n >= numberOfFpgas) {
			throw new IndexOutOfBoundsException("fpgaNum: Invalid FPGA number");
		}
		return fpgaMap[n];
	}

	// ******* msg
	// *************************************************************************
	public void msg(String s) {
		if (clusterMode) {
			synchronized (logBuf) {
				logBuf.add(new LogString(s));
			}
		} else {
			printMsg((name != null ? name + ": " : "") + s);
		}
	}

	// ******* dmsg
	// *************************************************************************
	void dmsg(String s) {
		if (verbose) {
			msg(s);
		}
	}

	// ******* print
	// *************************************************************************
	public void print() {
		synchronized (logBuf) {
			for (int j = 0; j < logBuf.size(); j++) {
				LogString ls = logBuf.elementAt(j);
				System.out.println(name + ": " + ls.msg);
				if (logFile != null) {
					logFile.println(dateFormat.format(ls.time) + ": " + name + ": " + ls.msg);
				}
				if (logFile2 != null) {
					logFile2.println(dateFormat.format(ls.time) + ": " + name + ": " + ls.msg);
				}
			}
			logBuf.clear();
		}
	}

	// ******* rejectReason
	// *************************************************************************
	public void rejectReason(String s) {
		lastRejectReason = s;
	}

	// ******* httpGet
	// *************************************************************************
	public static String httpGet(MsgObj msgObj, String url, String user, String passw, String request) throws MalformedURLException, IOException {
		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
		con.setRequestMethod("POST");
		con.setConnectTimeout((int) Math.round(2000.0 * BTCMiner.connectionEffort));
		con.setReadTimeout(url == longPollURL ? 1000000 : (int) Math.round(2000.0 * BTCMiner.connectionEffort));
		con.setRequestProperty("Authorization", "Basic " + encodeBase64(user + ":" + passw));
		con.setRequestProperty("Accept-Encoding", "gzip,deflate");
		con.setRequestProperty("Content-Type", "application/json");
		con.setRequestProperty("Cache-Control", "no-cache");
		con.setRequestProperty("User-Agent", "ztexBTCMiner");
		con.setRequestProperty("X-Mining-Extensions", "longpoll midstate submitold");
		con.setRequestProperty("Content-Length", "" + request.length());
		con.setUseCaches(false);
		con.setDoInput(true);
		con.setDoOutput(true);

		//xxxmsgObj.msg("Request: " + request);
		// Send request
		OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
		wr.write(request);
		wr.flush();
		wr.close();

		// read response header
		String str = con.getHeaderField("X-Reject-Reason");
		if (str != null && !str.equals("")) {
			msgObj.rejectReason(str);
		}

		// read response header
		str = con.getHeaderField("X-Long-Polling");
		if (str != null && !str.equals("") && longPollURL == null) {
			synchronized (BTCMiner.newBlockMonitor) {
				long t = new Date().getTime();

				if (longPollURL == null && t > disableLPTime) {
					longPollURL = (str.length() > 7 && str.substring(0, 4).equalsIgnoreCase("http")) ? str : url + str;
					msgObj.msg("Using LongPolling URL " + longPollURL);
					longPollUser = user;
					longPollPassw = passw;
				}
			}
		}

		// read response
		InputStream is;
		if (con.getContentEncoding() == null) {
			is = con.getInputStream();
		} else if (con.getContentEncoding().equalsIgnoreCase("gzip")) {
			is = new GZIPInputStream(con.getInputStream());
		} else if (con.getContentEncoding().equalsIgnoreCase("deflate")) {
			is = new InflaterInputStream(con.getInputStream());
		} else {
			throw new IOException("httpGet: Unknown encoding: " + con.getContentEncoding());
		}

		byte[] buf = new byte[1024];
		StringBuffer response = new StringBuffer();
		int len;
		while ((len = is.read(buf)) > 0) {
			response.append(new String(buf, 0, len));
		}
		is.close();
		con.disconnect();

		//xxxmsgObj.msg("Response: " + response.toString());
		return response.toString();
	}

	// ******* bitcoinRequest
	// *************************************************************************
	public static String bitcoinRequest(MsgObj msgObj, String url, String user, String passw, String request, String params) throws MalformedURLException, IOException {
		bcid += 1;
		return httpGet(msgObj, url, user, passw, "{\"jsonrpc\":\"1.0\",\"id\":" + bcid + ",\"method\":\"" + request + "\",\"params\":[" + (params.equals("") ? "" : ("\"" + params + "\"")) + "]}");
	}

	public String bitcoinRequest(String request, String params) throws MalformedURLException, IOException {
		bcid += 1;
		RPC rpc = BTCMiner.rpc[rpcNum];
		String s = httpGet(this, rpc.host, rpc.usr, rpc.pwd, "{\"jsonrpc\":\"1.0\",\"id\":" + bcid + ",\"method\":\"" + request + "\",\"params\":[" + (params.equals("") ? "" : ("\"" + params + "\"")) + "]}");
		//rpc.enable(false);
		return s;
	}

	// ******* getWork
	// *************************************************************************
	public boolean getWork() throws UsbException, MalformedURLException, IOException, ParserException {
		long t = new Date().getTime();

		int i = 0;
		while (i < rpcCount && (rpc[i].mine == false || rpc[i].disabled())) {
			i++;
		}
		if (i >= rpcCount) {
			msg("Warning: unable to obtain new work! Specifying a backup pool may help.");
			return false;
		}
		rpcNum = i;

		String response = bitcoinRequest("getwork", "");
		dmsg("getWork returned: " + response);
		RPC rpc = BTCMiner.rpc[rpcNum];
		rpc.sharesGetwork += 1;

		t = new Date().getTime() - t;
		getTime = getTime * 0.99 + t;
		getTimeW = getTimeW * 0.99 + 1;

		String target;
		try {
			hexStrToData(jsonParse(response, "data"), dataBuf2);
			target = jsonParse(response, "target");
			newBlockMonitor.checkNew(dataBuf2);
		} catch (NumberFormatException e) {
			throw new ParserException(e.getLocalizedMessage());
		}

		if (newCount >= newBlockMonitor.newCount || newBlockMonitor.submitOld) {
			while (getNonces()) {}
		}
		newCount = newBlockMonitor.newCount;

		if (targetCheck) {
			if (target.length() != 64) {
				throw new ParserException("Invalid target from " + rpc.name);
			}
			if (!target.equals(targetString)) {
				dmsg("getWork new target: " + target);
				hexStrToData(target, targetBuf);
				targetString = target;
				double d = 0.0;
				for (int j=31; j>=0; j--) {
					d *= 256;
					d += targetBuf[j] & 255;
				}
				if (d > 0.0) {
					difficulty =  DIFFEXACTONE / d;
				} else {
					difficulty =  1.0;
				}
				dmsg("getWork new difficulty: " + difficulty);
			}
			if (rpc.difficulty != difficulty) {
				rpc.difficulty = difficulty;
			}
		}

		System.arraycopy(dataBuf2, 0, dataBuf, 0, dataBuf2.length);
/*
		boolean b = false;
		int k = dataBuf.length - sha256_pad1.length;
		for (int j = 0; j < sha256_pad1.length; j++) {
			if (sha256_pad1[j] != dataBuf[k + j]) {
				b = true;
				dataBuf[k + j] = sha256_pad1[j];
			}
		}
		if (b) {
			msg("Warning: Invalid data from " + rpc.name);
		}
*/
		sha256_transform(sha256_init_state, 0, dataBuf, 0, midstateBuf, 0);

		lastGetWorkTime = new Date().getTime();
		prevRpcNum = i;
		return true;
	}

	// ******* submitWork
	// *************************************************************************
	public void submitWork(int n) throws MalformedURLException, IOException {
		long t = new Date().getTime();

		RPC rpc = BTCMiner.rpc[rpcNum];

		intToData(n, dataBuf, 76);

		dmsg("Submitting new nonce " + intToHexStr(n));
		if (blkLogFile != null) {
			blkLogFile.println(dateFormat.format(new Date()) + ": " + name + ": submitted " + dataToHexStr(dataBuf) + " to " + rpc.name);
		}
		String response = bitcoinRequest("getwork", dataToHexStr(dataBuf));
		dmsg("submitWork returned: " + response);
		String err = null;
		try {
			err = jsonParse(response, "error");
		} catch (ParserException e) {
		}
		if (err != null && !err.equals("null") && !err.equals("")) {
			msg("Error attempting to submit new nonce: " + err);
		}

		String result = null;
		try {
			result = jsonParse(response, "result");
		} catch (ParserException e) {
		}
		if (result != null && result.equals("true")) {
			rpc.sharesAccepted += 1;
		} else {
			msg("Warning: Rejected block: " + lastRejectReason);
			rpc.sharesRejected += 1;
		}

		for (int i = lastGoldenNonces.length - 1; i > 0; i--) {
			lastGoldenNonces[i] = lastGoldenNonces[i - 1];
		}
		lastGoldenNonces[0] = n;

		t = new Date().getTime() - t;
		submitTime = submitTime * 0.99 + t;
		submitTimeW = submitTimeW * 0.99 + 1;
	}

	// ******* initWork
	// *************************************************************************
	public void initWork(byte[] data, byte[] midstate) {
		if (data.length != 128) {
			throw new NumberFormatException("Invalid length of data");
		}
		if (midstate.length != 32) {
			throw new NumberFormatException("Invalid length of midstate");
		}
		for (int i = 0; i < 128; i++) {
			dataBuf[i] = data[i];
		}
		for (int i = 0; i < 32; i++) {
			midstateBuf[i] = midstate[i];
		}
	}

	// ******* getHash
	// *************************************************************************
	public int getHash(int n) throws NumberFormatException {
		intToData(n, dataBuf, 76);
		sha256_transform(midstateBuf, 0, dataBuf, 64, hashBuf, 0);
		sha256_transform(sha256_init_state, 0, hashBuf, 0, hashBuf, 0);
		return dataToInt(hashBuf, 28);
	}

	// ******* compareWithTarget - returns true if smaller than or equal to target
	// *************************************************************************
	public boolean compareWithTarget(int n, boolean dolog) throws NumberFormatException {
		/* already done in gethash
		intToData(n, dataBuf, 76);
		sha256_transform(midstateBuf, 0, dataBuf, 64, hashBuf, 0);
		sha256_transform(sha256_init_state, 0, hashBuf, 0, hashBuf, 0);
		*/
		for (int i = 0; i < 32; i++) {
			int j = i + 3 - 2 * (i % 4);
			if ((hashBuf[31 - j] & 255) < (targetBuf[31 - i] & 255)) {
				if (dolog) {
					dmsg("n=" + intToHexStr(n) + " d=" + dataToHexStr(dataBuf));
					dmsg("n=" + intToHexStr(n) + " t=" + dataToHexStr(targetBuf));
					dmsg("n=" + intToHexStr(n) + " h=" + dataToHexStr(hashBuf).substring(0, 64) + " -> VALID");
				}
				return true;
			}
			if ((hashBuf[31 - j] & 255) > (targetBuf[31 - i] & 255)) {
				if (dolog) {
					dmsg("n=" + intToHexStr(n) + " t=" + dataToHexStr(targetBuf));
					dmsg("n=" + intToHexStr(n) + " h=" + dataToHexStr(hashBuf).substring(0, 64) + " -> MISSED");
				}
				return false;
			}
		}
		if (dolog) {
			dmsg("n=" + intToHexStr(n) + " t=" + dataToHexStr(targetBuf));
			dmsg("n=" + intToHexStr(n) + " h=" + dataToHexStr(hashBuf).substring(0, 64) + " -> OK(default)");
		}
		return true;
	}

	// ******* getMidstate
	// *************************************************************************
	public byte[] getMidstate() {
		sha256_transform(sha256_init_state, 0, dataBuf, 0, hashBuf, 0);
		return hashBuf;
	}

	// ******* sendData
	// *************************************************************************
	public void sendData() throws UsbException {
		for (int i = 0; i < 12; i++) {
			sendBuf[i] = dataBuf[i + 64];
		}
		for (int i = 0; i < 32; i++) {
			sendBuf[i + 12] = midstateBuf[i];
		}

		long t = new Date().getTime();
		synchronized (ztex) {
			try {
				selectFpga();
			} catch (InvalidFirmwareException e) {
				// shouldn't occur
			}
			ztex.vendorCommand2(0x80, "Send hash data", 0, 0, sendBuf, 44);
		}
		usbTime += new Date().getTime() - t;

		ignoreErrorTime = new Date().getTime() + 500; // ignore errors for next 1s
		for (int i = 0; i < numNonces; i++) {
			nonce[i] = 0;
		}
		isRunning = true;
	}

	// ******* setFreq
	// *************************************************************************
	public void setFreq(int m) throws UsbException {
		if (m > freqMaxM) {
			m = freqMaxM;
		}

		long t = new Date().getTime();
		synchronized (ztex) {
			try {
				selectFpga();
			} catch (InvalidFirmwareException e) {
				// shouldn't occur
			}
			ztex.vendorCommand(0x83, "Send hash data", m, 0);
		}
		usbTime += new Date().getTime() - t;
		ignoreErrorTime = new Date().getTime() + 2000; // ignore errors for next 2s
	}

	// ******* suspend
	// *************************************************************************
	public boolean suspend() {
		suspended = true;
		if (suspendSupported) {
			try {
				synchronized (ztex) {
					selectFpga();
					ztex.vendorCommand(0x84, "Suspend");
				}
			} catch (Exception e) {
				msg("Suspend command failed: " + e.getLocalizedMessage());
				return false;
			}
		} else {
			msg("Suspend command not supported. Update Firmware.");
			return false;
		}
		return true;
	}

	// ******* updateFreq
	// *************************************************************************
	public void updateFreq() throws UsbException {

		for (int i = 0; i < freqMaxM; i++) {
			if (maxErrorRate[i + 1] * i < maxErrorRate[i] * (i + 20)) {
				maxErrorRate[i + 1] = maxErrorRate[i] * (1.0 + 20.0 / i);
			}
		}

		int maxM = 0;
		while (maxM < freqMDefault && maxErrorRate[maxM + 1] < maxMaxErrorRate) {
			maxM++;
		}
		while (maxM < freqMaxM && errorWeight[maxM] > 150 && maxErrorRate[maxM + 1] < maxMaxErrorRate) {
			maxM++;
		}

		int bestM = 0;
		double bestR = 0;
		for (int i = 0; i <= maxM; i++) {
			double r = (i + 1 + (i == freqM ? errorHysteresis : 0)) * (1 - maxErrorRate[i]);
			if (r > bestR) {
				bestM = i;
				bestR = r;
			}
		}

		if (bestM != freqM) {
			msg("Set frequency " + (freqM < 0 ? "" : "from " + String.format("%.2f", (freqM + 1) * (freqM1)) + "MHz ") + "to " + String.format("%.2f", (bestM + 1) * (freqM1)) + "MHz");
			freqM = bestM;
			setFreq(freqM);
		}

		maxM = freqMDefault;
		while (maxM < freqMaxM && errorWeight[maxM + 1] > 100) {
			maxM++;
		}
		if ((bestM + 1 < (1.0 - overheatThreshold) * maxHashRate) && bestM < maxM - 1) {
			try {
				synchronized (ztex) {
					selectFpga();
					ztex.resetFpga();
				}
			} catch (Exception e) {
			}
			throw new UsbException("Hash rate drop of " + String.format("%.1f", (1.0 - 1.0 * (bestM + 1) / maxHashRate) * 100) + "% detect. This may be caused by overheating. FPGA is shut down to prevent damage.");
		}

		double temp;
		try { 
			temp = ztex.tempSensorRead(fpgaNum);
		} catch (Exception e) {
			temp = tempLimit - 1e12;
		}
		if (temp > tempLimit) {
			try {
				synchronized (ztex) {
					selectFpga();
					ztex.resetFpga();
				}
			} catch (Exception e) {}
			throw new UsbException("Overheating detected: T=" + String.format("%.1f",temp) + "\u00b0C. FPGA is shut down to prevent damage.");
		}
	}

	// ******* getNonces
	// *************************************************************************
	public boolean getNonces() throws UsbException, MalformedURLException, IOException {
		if (!isRunning || rpc[prevRpcNum].disabled()) {
			return false;
		}
		rpcNum = prevRpcNum;

		getNoncesInt();

		if (ignoreErrorTime < new Date().getTime()) {
			errorCount[freqM] *= 0.995;
			errorWeight[freqM] = errorWeight[freqM] * 0.995 + 1.0;
			for (int i = 0; i < numNonces; i++) {
				if (!checkNonce(nonce[i], hash7[i])) {
					errorCount[freqM] += 1.0 / numNonces;
				}
			}

			errorRate[freqM] = errorCount[freqM] / errorWeight[freqM] * Math.min(1.0, errorWeight[freqM] * 0.01);
			if (errorRate[freqM] > maxErrorRate[freqM]) {
				maxErrorRate[freqM] = errorRate[freqM];
			}
			if (errorWeight[freqM] > 120) {
				maxHashRate = Math.max(maxHashRate, (freqM + 1.0) * (1 - errorRate[freqM]));
			}
		}

		boolean submitted = false;
		for (int i = 0; i < numNonces * (1 + extraSolutions); i++) {
			int n = goldenNonce[i];
			if (n != -offsNonces) {
				if (getHash(n) == 0) {
					int j = 0;
					while (j < lastGoldenNonces.length && lastGoldenNonces[j] != n) {
						j++;
					}
					if (j >= lastGoldenNonces.length) {
						if (!targetCheck || compareWithTarget(n, false)) {
							submitWork(n);
							submittedCount += 1;
							totalSubmittedCount += difficulty;
							submitted = true;
						}
					}
				}
			}
		}
		return submitted;
	}

	// ******* getNoncesInt
	// *************************************************************************
	public void getNoncesInt() throws UsbException {
		int bs = 12 + extraSolutions * 4;
		byte[] buf = new byte[numNonces * bs];
		boolean overflow = false;

		long t = new Date().getTime();
		synchronized (ztex) {
			try {
				selectFpga();
			} catch (InvalidFirmwareException e) {
				// shouldn't occur
			}
			ztex.vendorRequest2(0x81, "Read hash data", 0, 0, buf, numNonces * bs);
		}
		usbTime += new Date().getTime() - t;

		//dmsg"getNoncesInt: " + dataToHexStr(buf));
		for (int i = 0; i < numNonces; i++) {
			goldenNonce[i * (1 + extraSolutions)] = dataToInt(buf, i * bs + 0) - offsNonces;
			int j = dataToInt(buf, i * bs + 4) - offsNonces;
			overflow |= ((j >> 4) & 0xfffffff) < ((nonce[i] >> 4) & 0xfffffff);
			nonce[i] = j;
			hash7[i] = dataToInt(buf, i * bs + 8);
			for (j = 0; j < extraSolutions; j++) {
				goldenNonce[i * (1 + extraSolutions) + 1 + j] = dataToInt(buf, i * bs + 12 + j * 4) - offsNonces;
			}
		}
		if (overflow && !PollLoop.scanMode) {
			overflowCount += 1;
		}
	}

	// ******* checkNonce
	// *************************************************************************
	public boolean checkNonce(int n, int h) throws UsbException {
		int offs[] = { 0, 1, -1, 2, -2 };
		// int offs[] = { 0 };
		for (int i = 0; i < offs.length; i++) {
			if (getHash(n + offs[i]) == h + 0x5be0cd19) {
				return true;
			}
		}
		return false;
	}

	// ******* totalHashRate
	// *************************************************************************
	public double totalHashRate() {
		return fatalError == null ? (freqM + 1) * freqM1 * (1 - errorRate[freqM]) * hashesPerClock : 0;
	}

	// ******* submittedHashRate
	// *************************************************************************
	public double submittedHashRate() {
		return fatalError == null ? 4.294967296e6 * totalSubmittedCount / (new Date().getTime() - startTime) : 0;
	}

	// ******* printInfo
	// *************************************************************************
	public void printInfo(boolean force) {
		long t = new Date().getTime();
		if (!force && (clusterMode || lastInfoTime + infoInterval > t || !isRunning)) {
			return;
		}
		if (fatalError != null) {
			printMsg2(name + ": " + fatalError);
			return;
		}

		if (suspended) {
			printMsg2(name + ": Suspended");
			return;
		}

		StringBuffer sb = new StringBuffer("f=" + String.format("%.2f", (freqM + 1) * freqM1) + "MHz");

		if (errorWeight[freqM] > 20) {
			sb.append(",  errorRate=" + String.format("%.2f", errorRate[freqM] * 100) + "%");
		}

		if (errorWeight[freqM] > 100) {
			sb.append(",  maxErrorRate=" + String.format("%.2f", maxErrorRate[freqM] * 100) + "%");
		}

		double hr = (freqM + 1) * freqM1 * (1 - errorRate[freqM]) * hashesPerClock;

		if (errorWeight[freqM] > 20) {
			sb.append(",  hashRate=" + String.format("%.1f", hr) + "MH/s");
		}

		try {
			sb.append(", T=" + String.format("%.1f", ztex.tempSensorRead(fpgaNum)) + "\u00b0C");
		} catch (Exception e) {}

		sb.append(",  submitted " + submittedCount + " new nonces,  luckFactor=" + String.format("%.2f", submittedHashRate() / hr + 0.0049));
		submittedCount = 0;

		printMsg2(name + ": " + sb.toString());

		lastInfoTime = t;
	}

	// ******* getDescriptor
	// *************************************************************************
	private void getDescriptor() throws UsbException, FirmwareException {
		byte[] buf = new byte[64];

		ztex.vendorRequest2(0x82, "Read descriptor", 0, 0, buf, 64);
		if (buf[0] != 5) {
			if ((buf[0] != 2) && (buf[0] != 4)) {
				throw new FirmwareException("Invalid BTCMiner descriptor version. Firmware must be updated.");
			}
			msg("Warning: Firmware out of date");
		}
		numNonces = (buf[1] & 255) + 1;
		offsNonces = ((buf[2] & 255) | ((buf[3] & 255) << 8)) - 10000;
		freqM1 = ((buf[4] & 255) | ((buf[5] & 255) << 8)) * 0.01;
		freqM = (buf[6] & 255);
		freqMaxM = (buf[7] & 255);
		if (freqM > freqMaxM) {
			freqM = freqMaxM;
		}
		freqMDefault = freqM;

		suspendSupported = buf[0] == 5;

		hashesPerClock = buf[0] > 2 ? (((buf[8] & 255) | ((buf[9] & 255) << 8)) + 1) / 128.0 : 1.0;
		extraSolutions = buf[0] > 4 ? buf[10] : 0;

		int i0 = buf[0] > 4 ? 11 : (buf[0] == 4 ? 10 : 8);
		int i = i0;
		while (i < 64 && buf[i] != 0) {
			i++;
		}
		if (i < i0 + 1) {
			throw new FirmwareException("Invalid bitstream file name");
		}
		bitFileName = new String(buf, i0, i - i0);

		if (buf[0] < 4) {
			if (bitFileName.substring(0, 13).equals("ztex_ufm1_15b")) {
				hashesPerClock = 0.5;
			}
			msg("Warning: HASHES_PER_CLOCK not defined, assuming " + hashesPerClock);
		}
	}

	// ******* checkUpdate
	// *************************************************************************
	public boolean checkUpdate() {
		long t = new Date().getTime();

		if (!isRunning) {
			return true;
		}
		if (ignoreErrorTime > t) {
			return false;
		}
		if (newCount < newBlockMonitor.newCount) {
			return true;
		}
		if (rpc[prevRpcNum].disabled()) {
			return true;
		}
		if (lastGetWorkTime + maxPollInterval < t) {
			return true;
		}
		for (int i = 0; i < numNonces; i++) {
			if (((nonce[i] >> 1) & 0x7fffffff) > (0x38000000 + Math.round(Math.random() * 0x10000000))) {
				return true;
			}
		}
		return false;
	}

	// ******* descriptorInfo
	// *************************************************************************
	public String descriptorInfo() {
		return "bitfile=" + bitFileName + "   f_default=" + String.format("%.2f", freqM1 * (freqMDefault + 1)) + "MHz  f_max=" + String.format("%.2f", freqM1 * (freqMaxM + 1)) + "MHz  HpC=" + hashesPerClock + "H";
	}

	// ******* resetCounters
	// *************************************************************************
	public void resetCounters() {
		while (freqMDefault < freqM && errorWeight[freqMDefault + 1] > 100) {
			freqMDefault++;
		}
		for (int i = 0; i < 255; i++) {
			errorCount[i] *= 0.05;
			errorWeight[i] *= 0.05;
			errorRate[i] = 0;
			maxErrorRate[i] = 0;
		}
		startTime = new Date().getTime();
		totalSubmittedCount = 0.0;
	}

	// ******* main
	// *************************************************************************
	public static void main(String args[]) {

		int devNum = -1;
		boolean workarounds = false;

		String firmwareFile = null, snString = null;
		boolean printBus = false;
		boolean verbose = false;
		boolean eraseFirmware = false;
		boolean noLog = false;

		String filterType = null;
		String logFileName = "BTCMiner.log";

		char mode = 's';

		File wwwroot = new File(".").getAbsoluteFile();
		int httpdPortno = 8080;

		int ioDisableMonitorTime = 0;

		upTime = new Date().getTime();

		try {
			// init USB stuff
			LibusbJava.usb_init();

			// scan the command line arguments
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("-d")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						devNum = Integer.parseInt(args[i]);
					} catch (Exception e) {
						throw new ParameterException("Device number expected after -d");
					}
				} else if (args[i].equals("-l")) {
					i++;
					if (i >= args.length) {
						throw new ParameterException("Error: File name expected after `-l'");
					}
					try {
						logFileName = args[i];
					} catch (Exception e) {
						throw new ParameterException("Error: File name expected after `-l': " + e.getLocalizedMessage());
					}
				} else if (args[i].equals("-l2")) {
					i++;
					if (i >= args.length) {
						throw new ParameterException("Error: File name expected after `-l2'");
					}
					try {
						logFile2 = new PrintStream(new FileOutputStream(args[i], true), true);
					} catch (Exception e) {
						throw new ParameterException("Error: File name expected after `-l2': " + e.getLocalizedMessage());
					}
				} else if (args[i].equals("-bl")) {
					i++;
					if (i >= args.length) {
						throw new ParameterException("Error: File name expected after `-dl'");
					}
					try {
						blkLogFile = new PrintStream(new FileOutputStream(args[i], true), true);
					} catch (Exception e) {
						throw new ParameterException("Error: File name expected after `-bl': " + e.getLocalizedMessage());
					}
				} else if (args[i].equals("-c")) {
					i++;
					if (i >= args.length) {
						throw new ParameterException("Error: File name expected after `-c'");
					}
					try {
						in2FileName = args[i];
						new Thread() {
							public void run() {
								try {
									in2 = new FileInputStream(in2FileName);
								} catch (Exception e) {
									System.err.println("Error: File name expected after `-c': " + e.getLocalizedMessage());
								}
							}
						}.start();
					} catch (Exception e) {
						throw new ParameterException("Error: File name expected after `-c': " + e.getLocalizedMessage());
					}
				} else if (args[i].equals("-o")) {
					if (rpcCount >= maxRpcCount) {
						throw new IndexOutOfBoundsException("Maximum amount of servers reached");
					}
					if (rpcFirstBackup > 0) {
						throw new ParameterException("Don't mix options -o and -b");
					}
					i += 5;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						rpc[rpcCount] = new RPC(/*name*/args[i - 4], /*url*/args[i - 3], /*host*/args[i - 2], /*usr*/args[i - 1], /*pwd*/args[i], rpcCount == 0);
						rpcCount += 1;
					} catch (Exception e) {
						throw new ParameterException("<name> <website> <URL> <user name> <password> expected after -o");
					}
				} else if (args[i].equals("-b")) {
					if (rpcCount >= maxRpcCount) {
						throw new IndexOutOfBoundsException("Maximum amount of servers reached");
					}
					if (rpcCount == 0) {
						throw new ParameterException("Please specify at least one -o before -b");
					}
					i += 5;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						rpc[rpcCount] = new RPC(/*name*/args[i - 4], /*url*/args[i - 3], /*host*/args[i - 2], /*usr*/args[i - 1], /*pwd*/args[i], true);
						if (rpcFirstBackup == 0) {
							rpcFirstBackup = rpcCount;
						}
						rpcCount += 1;
					} catch (Exception e) {
						throw new ParameterException("<name> <website> <URL> <user name> <password> expected after -b");
					}
				} else if (args[i].equals("-lp")) {
					i += 3;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						longPollURL = args[i - 2];
						longPollUser = args[i - 1];
						longPollPassw = args[i];
					} catch (Exception e) {
						throw new ParameterException("<URL> <user name> <password> expected after -lp");
					}
				} else if (args[i].equals("-f")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						firmwareFile = args[i];
					} catch (Exception e) {
						throw new ParameterException("ihx file name expected after -f");
					}
				} else if (args[i].equals("-pt")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						filterType = args[i];
					} catch (Exception e) {
						throw new ParameterException("<string> after -pt");
					}
				} else if (args[i].equals("-ps")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						filterSN = args[i];
					} catch (Exception e) {
						throw new ParameterException("<string> after -ps");
					}
				} else if (args[i].equals("-m")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						if (args[i].length() < 1) {
							throw new Exception();
						}
						mode = Character.toLowerCase(args[i].charAt(0));
						if (mode != 's' && mode != 't' && mode != 'p' && mode != 'c' && mode != 'h') {
							throw new Exception();
						}
					} catch (Exception e) {
						throw new ParameterException("s|t|p|c|h expected after -m");
					}
				} else if (args[i].equals("-s")) {
					i++;
					if (i >= args.length) {
						throw new ParameterException("Error: String expected after -s");
					}
					snString = checkSnString(args[i]);
				} else if (args[i].equals("-i")) {
					printBus = true;
				} else if (args[i].equals("-v")) {
					verbose = true;
					BTCMinerCluster.verbose = true;
				} else if (args[i].equals("-rf")) {
					eraseFirmware = true;
				} else if (args[i].equals("-ep0")) {
					forceEP0Config = true;
				} else if (args[i].equals("-tc")) {
					targetCheck = true;
				} else if (args[i].equals("-h")) {
					System.out.println(ParameterException.helpMsg);
					System.exit(0);
				} else if (args[i].equals("-n")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						BTCMinerCluster.maxDevicesPerThread = Integer.parseInt(args[i]);
					} catch (Exception e) {
						throw new ParameterException("Number expected after -n");
					}
				} else if (args[i].equals("-oh")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						overheatThreshold = Double.parseDouble(args[i]);
					} catch (Exception e) {
						throw new ParameterException("Number expected after -oh");
					}
				} else if ( args[i].equals("-t") ) {
					i++;
					try {
						if (i>=args.length) {
							throw new Exception();
						}
						tempLimit = Double.parseDouble( args[i] );
					} catch (Exception e) {
						throw new ParameterException("Number expected after -t");
					}
				} else if (args[i].equals("-e")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						double d = Double.parseDouble(args[i]);
						if (d < 0.0001) {
							d = 0.0001;
						}
						if (d < maxMaxErrorRate) {
							maxMaxErrorRate = d;
						}
					} catch (Exception e) {
						throw new ParameterException("Number expected after -e");
					}
				} else if (args[i].equals("-ac")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						long autoResetInterval = Long.parseLong(args[i]);
						if (autoResetInterval < 0) {
							throw new Exception();
						}
						BTCMinerCluster.autoResetInterval = autoResetInterval * 1000; // in milliseconds
					} catch (Exception e) {
						throw new ParameterException("Wrong or missing parameter after -ac");
					}
				} else if (args[i].equals("-nolog")) {
					noLog = true;
				} else if (args[i].equals("-id")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						clusterId = args[i];
					} catch (Exception e) {
						throw new ParameterException("Some value expected after -id");
					}
				} else if (args[i].equals("-p")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						httpdPortno = Integer.parseInt(args[i]);
						if (httpdPortno <= 0 || httpdPortno >= 65536) {
							throw new Exception();
						}
					} catch (Exception e) {
						throw new ParameterException("Wrong or missing parameter after -p");
					}
				} else if (args[i].equals("-iomon")) {
					i++;
					try {
						if (i >= args.length) {
							throw new Exception();
						}
						ioDisableMonitorTime = Integer.parseInt(args[i]);
						if (ioDisableMonitorTime <= 0) {
							throw new Exception();
						}
					} catch (Exception e) {
						throw new ParameterException("Wrong or missing parameter after -iomon");
					}
				} else {
					throw new ParameterException("Invalid Parameter: " + args[i]);
				}
			}

			if (noLog != true) {
				logFile = new PrintStream(new FileOutputStream(logFileName, true), true);
			}

			if (overheatThreshold > 0.1001) {
				System.err.println("Warning: overheat threshold set to " + overheatThreshold + ": overheat shutdown may be triggered too late, recommended values: 0..0.1");
			}

			if (rpcFirstBackup == 0) {
				System.err.println("Warning: no backup mining pools specified!");
			}

			if (ioDisableMonitorTime > 0) {
				for (int i = 0; i < rpcCount; i++) {
					rpc[i].ioDisableMonitorTime = ioDisableMonitorTime;
				}
			}

			if (BTCMinerCluster.maxDevicesPerThread < 1) {
				BTCMinerCluster.maxDevicesPerThread = 127;
			}

			if (mode != 'c' && mode != 'h' && filterSN != null) {
				filterSN = checkSnString(filterSN);
			}

			if (mode != 't' && mode != 'p') {
				if (rpcCount == 0) {
					throw new ParameterException("Missing rpcpool - Please specify at least one mining pool");
				}
			}

			if (mode == 's' || mode == 't') {
				if (devNum < 0) {
					devNum = 0;
				}

				ZtexScanBus1 bus = new ZtexScanBus1(ZtexDevice1.ztexVendorId, ZtexDevice1.ztexProductId, filterSN == null, false, 1, filterSN, 10, 0, 1, 0);
				if (bus.numberOfDevices() <= 0) {
					System.err.println("No devices found");
					System.exit(0);
				}
				if (printBus) {
					printBus(bus);
					System.exit(0);
				}

				BTCMiner miner = new BTCMiner(bus.device(devNum), firmwareFile, verbose);
				if (mode == 't') { // single mode
					miner.initWork(hexStrToData("0000000122f3e795bb7a55b2b4a580e0dbba9f2a5aedbfc566632984000008de00000000e951667fbba0cfae7719ab2fb4ab8d291a20d387782f4610297f5899cc58b7d64e4056801a08e1e500000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000"), hexStrToData("28b81bd40a0e1b75d18362cb9a2faa61669d42913f26194f776c349e97559190"));

					miner.sendData();
					for (int i = 0; i < 200; i++) {
						try {
							Thread.sleep(250);
						} catch (InterruptedException e) {
						}
						miner.getNoncesInt();

						for (int j = 0; j < miner.numNonces; j++) {
							System.out.println(i + "-" + j + ":  " + intToHexStr(miner.nonce[j]) + "    " + miner.checkNonce(miner.nonce[j], miner.hash7[j]) + "   " + miner.overflowCount + "    " + intToHexStr(miner.goldenNonce[j * (1 + miner.extraSolutions)]) + "      " + intToHexStr(miner.getHash(miner.goldenNonce[j])));
						}
					}
				} else { // single mode
					Vector<BTCMiner> v = new Vector<BTCMiner>();
					v.add(miner);
					for (int i = 1; i < miner.numberOfFpgas(); i++) {
						v.add(new BTCMiner(miner.ztex(), miner.fpgaNum(i), verbose));
					}
					System.out.println("");
					if (miner.ztex().numberOfFpgas() > 1) {
						System.out.println("A multi-FPGA board is detected. Use the cluster mode for additional statistics.");
					}
					System.out.println("Disconnect device or press Ctrl-C for exit\n");
					new PollLoop(v).run();
				}
			} else if (mode == 'p') {
				if (eraseFirmware && filterType == null && filterSN == null) {
					throw new ParameterException("-rf requires -pt or -ps");
				}

				ZtexScanBus1 bus = new ZtexScanBus1(ZtexDevice1.ztexVendorId, ZtexDevice1.ztexProductId, filterType == null && filterSN == null, false, 1, null, 10, 0, 0, 0);

				if (bus.numberOfDevices() <= 0) {
					System.err.println("No devices found");
					System.exit(0);
				}
				if (printBus) {
					printBus(bus);
					System.exit(0);
				}
				if (firmwareFile == null && !eraseFirmware) {
					throw new Exception("Parameter -f or -rf required in programming mode");
				}

				int imin = 0, imax = bus.numberOfDevices() - 1;
				if (devNum >= 0) {
					imin = devNum;
					imax = devNum;
				}

				ZtexIhxFile1 ihxFile = eraseFirmware ? null : new ZtexIhxFile1(firmwareFile);

				int j = 0;
				for (int i = imin; i <= imax; i++) {
					ZtexDevice1 dev = bus.device(i);
					if ((filterSN == null || filterSN.equals(dev.snString())) && (filterType == null || ((dev.productId(2) == 1) && filterType.equals(getType(dev)))) && (filterType != null || filterSN != null || dev.productId(2) == 0)) {
						Ztex1v1 ztex = new Ztex1v1(dev);
						if (snString != null && ihxFile != null) {
							ihxFile.setSnString(snString);
						} else if (ztex.valid() && ihxFile != null) {
							ihxFile.setSnString(dev.snString());
						}
						if (eraseFirmware) {
							ztex.eepromDisable();
							System.out.println("EEPROM erased: " + ztex.toString());
						} else {
							System.out.println("\nold: " + ztex.toString());
							System.out.println("Firmware upload time: " + ztex.uploadFirmware(ihxFile, false) + " ms");
							System.out.println("EEPROM programming time: " + ztex.eepromUpload(ihxFile, false) + " ms");
							System.out.println("new: " + ztex.toString());
						}
						j += 1;
					}
				}
				System.out.println("\ntotal amount of (re-)programmed devices: " + j);
			} else if (mode == 'c') {
				cluster = new BTCMinerCluster();
				cluster.run();
			} else if (mode == 'h') {
				try {
					new BTCMinerHTTPD(httpdPortno, wwwroot);
				} catch (IOException ioe) {
					System.err.println("Couldn't start builtin HTTP server on port " + String.format("%d", httpdPortno) + "!\n" + ioe);
					System.exit(-1);
				}
				System.out.println("Builtin HTTP server is running. Listening on port " + String.format("%d", httpdPortno) + ".");
				cluster = new BTCMinerCluster();
				cluster.run();
				// try { System.in.read(); } catch(Throwable t) {}
			}

		} catch (Exception e) {
			System.out.println("Error: " + e.getLocalizedMessage());
		}

		if (BTCMiner.newBlockMonitor != null) {
			BTCMiner.newBlockMonitor.running = false;
			BTCMiner.newBlockMonitor.interrupt();
		}
		System.exit(0);
	}
}
