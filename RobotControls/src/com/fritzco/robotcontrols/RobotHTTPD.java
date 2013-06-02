package com.fritzco.robotcontrols;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

import android.os.Handler;
import android.os.Message;

public class RobotHTTPD extends NanoHTTPD 
{

	public static final int MESSAGE_READ = 2;
	public static final int RESET_BLUETOOTH = 1;
    private Handler mHandler = null;
    private String BTstat = "";
    private String BTWrite = "";
	private String BTRead = "";
	private String BTDev = "";
	private int tiltVal = 90;  // up is down, down is up
	private static final int tiltMin = 20;  // full up
	private static final int tiltMax = 140; // full down
	private static final int tiltCenter = 90;
	private int panVal = 90;   // up is left, down is right
	private static final int panMin = 0;
	private static final int panMax = 180;
	private static final int panCenter = 90;
	private static final int speedVal = 13;  // 0-15 range, but only goes from 10 or so.
	private static final int incAmt = 10;    // increment amount for pan and tilt
	
	public RobotHTTPD(int port, Handler handler, File wwwroot) throws IOException {
		super(port, wwwroot);
		mHandler = handler;
	}

	@Override
	public Response serve( String uri, String method, Properties header, Properties parms, Properties files )
	{
		myOut.println( "OVERRIDEn " + method + " '" + uri + "' " );

		Enumeration e = header.propertyNames();

		e = parms.propertyNames();
		if ( e.hasMoreElements())
		{
			String value = (String)e.nextElement();
			String v = parms.getProperty( value );
			myOut.println( "  PRM: '" + value + "' = '" +
					parms.getProperty( value ) + "'" );
			if (value.matches("look"))
			{
				myOut.println("look " + v);
				// tell robot to look
				setPanTiltVal(v); // Yes, you got me, its setting global vals eek
				sendMessage("PAN," + panVal);
				sendMessage("TILT," + tiltVal);
			} else if (value.matches("go"))
			{
				myOut.println("go " + v);
				// tell robot to go
				sendMessage(setGoString(v));
			} else if (value.matches("status"))
			{
				myOut.println("get Status");
				if (BTstat.matches(".*LOST.*")) {
					Message msg = mHandler.obtainMessage(RESET_BLUETOOTH);
					mHandler.sendMessage(msg);
				}
				// go get status (keep up to date status object around to return)
				//String status = "IP addr, connect status with bt, other stuff...";
				String myStatus = getStatus();
				sendMessage(myStatus);
				String respTxt = "<!DOCTYPE html> <html lang=\"en\" xmlns=\"http://www.w3.org/1999/xhtml\"> " +
									"<head><meta charset=\"utf-8\" /><title></title></head>" +
									"<body><script type=\"text/javascript\">parent.feedback('" +
									myStatus + 
									"');</script></body></html>";
				return new Response("HTTP_OK", "text/html", respTxt);
			}
			return new NanoHTTPD.Response();
		}
		else
		{
			return super.serve(uri, method, header, parms, files);
		}
	}
	
	private String setGoString(String s) {
        int meander = 0;  // dont meander for now, just dont...
        int dir = 0;
        //if (meanderState == true) meander = 16; // bit 4
        if (s.matches("left")) {
        	dir = 1; // left
        } else if (s.matches("straight")) {
        	dir = 0; // straight
        } else if (s.matches("right")) {
        	dir = 2; // right
        } else if (s.matches("leftK")) {
        	dir = 3; // left KTurn, restrict to press every 2 sec.
        } else if (s.matches("back")) {
        	dir = 5; // back
        } else if (s.matches("rightK")) {
        	dir = 4; // right KTurn, restrict to press every 2 sec.
        }
        int godir = dir << 5; // bits 1-3
        int result = speedVal + meander + godir;
        return "GO," + Integer.toString(result);
	}
	
	private void setPanTiltVal (String v) {
		if (v.matches("center")) {
			panVal = panCenter; 
			tiltVal = tiltCenter;
		} else if (v.matches("leftUp")) {
			panVal += incAmt; tiltVal -= incAmt;
		} else if (v.matches("up")) {
			tiltVal -= incAmt;
		} else if (v.matches("rightUp")) {
			panVal -= incAmt; tiltVal -= incAmt;
		} else if (v.matches("left")) {
			panVal += incAmt;
		} else if (v.matches("right")) {
			panVal -= incAmt;
		} else if (v.matches("leftDown")) {
			panVal += incAmt; tiltVal += incAmt;
		} else if (v.matches("down")) {
			tiltVal += incAmt;
		} else if (v.matches("rightDown")) {
			panVal -= incAmt; tiltVal += incAmt;
		}
		if (panVal > panMax) panVal = panMax;
		if (panVal < panMin) panVal = panMin;
		if (tiltVal > tiltMax) tiltVal = tiltMax;
		if (tiltVal < tiltMin) tiltVal = tiltMin;
	}
	private void sendMessage(String s) {
        int bytes;
        char[] buffer = new char[1024];
    	bytes = s.length();
    	s.getChars(0, bytes, buffer, 0);
    	buffer[bytes] = '\n';  
    	byte[] b = new byte[bytes+1];
    	 for (int i = 0; i < bytes+1; i++) {
    	  b[i] = (byte) buffer[i];
    	 }
    	mHandler.obtainMessage(MESSAGE_READ, bytes+1, -1, b)
            .sendToTarget();
	}
	private String getStatus() {
		return "BT " + BTstat + " tilt " + tiltVal + " pan " + panVal;
	}

	public void setBTstat(String s) {
		BTstat = s;
	}
	public void setBTWrite(String s) {
		BTWrite = s;
	}
	public void setBTRead(String s) {
		BTRead = s;
	}
	public void setBTDev(String s) {
		BTDev = s;
	}

}