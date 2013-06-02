package com.fritzco.robotcontrols;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class BTtoWIFIChatService extends Service {
	protected static final String TAG = "MainServiceWifiBTChat";
	protected static final boolean D = true;
	// Member object for the chat services
	private BluetoothAdapter mBluetoothAdapter = null;
	protected BluetoothChatService mBTChatService = null;
	//protected WifiChatService mWifiChatService = null;
	protected RobotHTTPD mHTTPDService = null;
	// Name of the BT connected device
	protected String mBTConnectedDeviceName = "";
	protected String mWifiConnectedDeviceName = "";
	String BTStat;
	String WiFiStat;

	int TCP_SERVER_PORT;
	String ROBOTNAME;

	static final int MSG_REGISTER_CLIENT = 1;
	static final int MSG_UNREGISTER_CLIENT = 2;
	static final int MSG_SET_INT_VALUE = 3;
	static final int MSG_SET_STRING_VALUE = 4;
	static final int MSG_SEND_BT_STATUS = 5;
	static final int MSG_SEND_BT_DEVICE_NAME = 6;
	static final int MSG_SEND_WIFI_STATUS = 7;
	static final int MSG_SEND_WIFI_DEVICE_NAME = 8;

	private static boolean isRunning = false;
	final Messenger mMessenger = new Messenger(new IncomingHandler()); // Target we publish for clients to send messages to IncomingHandler.
	Messenger mClientMessenger; // the return messenger in the UI thread
	Thread thread;

	class IncomingHandler extends Handler { // Handler of incoming messages from main activity.
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_REGISTER_CLIENT:
				TCP_SERVER_PORT = msg.getData().getInt("PORT");
				ROBOTNAME = msg.getData().getString("ROBOT");
				sendToMainActivity("register client: " + ROBOTNAME + " on port " + TCP_SERVER_PORT);
				startup();
				mClientMessenger = msg.replyTo;
				break;
			case MSG_UNREGISTER_CLIENT:
				//INTERRUPTME = true;
				sendToMainActivity("UNregister client");
				shutdown();
				mClientMessenger = null;
				break;
			case MSG_SET_INT_VALUE:
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		Log.d("service", "onBind");
		return mMessenger.getBinder();
	}

	// this will be called to connect

	final Handler mBTHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case BluetoothChatService.MESSAGE_STATE_CHANGE:
				if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case BluetoothChatService.STATE_CONNECTED:
					//setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
					BTStat = mBTConnectedDeviceName;
					//mConversationArrayAdapter.clear();
					break;
				case BluetoothChatService.STATE_CONNECTING:
					//setStatus(R.string.title_connecting);
					BTStat = " Connecting... ";
					break;
				case BluetoothChatService.STATE_LISTEN:
					BTStat = " Listening ";
					break;
				case BluetoothChatService.STATE_NONE:
					//setStatus(R.string.title_not_connected);
					BTStat = " Not Connected ";
					break;
				case BluetoothChatService.STATE_FAILED_TO_CONNECT:
					//setStatus(R.string.title_not_connected);
					BTStat = " Failed to Connect to " + ROBOTNAME;
					break;
				case BluetoothChatService.STATE_CONNECTION_LOST:
					//setStatus(R.string.title_not_connected);
					BTStat = " Connection LOST ";
					break;
				}
				sendToMainActivity(BTStat, MSG_SEND_BT_STATUS);
				mHTTPDService.setBTstat(BTStat);
				break;
			case BluetoothChatService.MESSAGE_WRITE:
				byte[] writeBuf = (byte[]) msg.obj;
				// construct a string from the buffer
				String writeMessage = new String(writeBuf);
				sendToMainActivity("BTWrite: " + writeMessage);
				mHTTPDService.setBTWrite("BT write: " + writeMessage);
				//mConversationArrayAdapter.add("Me:  " + writeMessage);
				break;
			case BluetoothChatService.MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				// construct a string from the valid bytes in the buffer
				String readMessage = new String(readBuf, 0, msg.arg1);
				sendToMainActivity("BTRead: " + readMessage);
				mHTTPDService.setBTRead("BT read: " + readMessage);
				//mConversationArrayAdapter.add(mBTConnectedDeviceName+":  " + readMessage);
				// Send to Wifi Write.
				//writeToWifi(readBuf, msg.arg1);
				break;
			case BluetoothChatService.MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mBTConnectedDeviceName = msg.getData().getString(BluetoothChatService.DEVICE_NAME);
				sendToMainActivity(mBTConnectedDeviceName, MSG_SEND_BT_DEVICE_NAME);
				mHTTPDService.setBTDev(mBTConnectedDeviceName);
				break;
			case BluetoothChatService.MESSAGE_TOAST:
				Log.d(TAG, "received a TOAST MESSAGE. " + msg.getData().getString(BluetoothChatService.TOAST));
				break;
			}
		}
	};

	// The Handler that gets information back from the RobotHTTPD
	final Handler mHTTPDHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			/*
			case WifiChatService.MESSAGE_STATE_CHANGE:
				if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case WifiChatService.STATE_CONNECTED:
					//setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
					WiFiStat = mWifiConnectedDeviceName;
					//mConversationArrayAdapter.clear();
					break;
				case WifiChatService.STATE_CONNECTING:
					//setStatus(R.string.title_connecting);
					WiFiStat = " Connecting... ";
					break;
				case WifiChatService.STATE_LISTEN:
					WiFiStat = " Listening ";
					break;
				case WifiChatService.STATE_NONE:
					//setStatus(R.string.title_not_connected);
					WiFiStat = " Not Connected ";
					break;
				}
				sendToMainActivity(WiFiStat, MSG_SEND_WIFI_STATUS);
				break;
				*/
			/*
			case WifiChatService.MESSAGE_WRITE:
				byte[] writeBuf = (byte[]) msg.obj;
				// construct a string from the buffer
				String writeMessage = new String(writeBuf,0,msg.arg1);
				sendToMainActivity("WifiWrite: " + writeMessage);
				//mConversationArrayAdapter.add("Me:  " + writeMessage);
				break;
				*/
			case RobotHTTPD.MESSAGE_READ:
				byte[]  readBuf = (byte[] ) msg.obj;
				// construct a string from the valid bytes in the buffer
				String readMessage = new String(readBuf, 0, msg.arg1);
				sendToMainActivity("HTTP In: " + readMessage);
				//mConversationArrayAdapter.add(mBTConnectedDeviceName+":  " + readMessage);
				// Send to BT Write
				writeToBT(readBuf);
				break;
			case RobotHTTPD.RESET_BLUETOOTH:
				setupChat(mBTHandler, mHTTPDHandler);
				break;
				/*
			case WifiChatService.MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mWifiConnectedDeviceName = msg.getData().getString(WifiChatService.DEVICE_NAME);
				sendToMainActivity(mWifiConnectedDeviceName, MSG_SEND_WIFI_DEVICE_NAME);
				break;
			case WifiChatService.MESSAGE_TOAST:
				Log.d(TAG, "Wifi MESSAGE_TOAST received.  Debug OUT");
				break;
				*/
			}
		}
	};
	// The Handler that gets information back from the BluetoothChatService
	// handler to get back here to add to log screen
	private void initChat(Handler mBTHandler, Handler mHTTPDHandler) {
		sendToMainActivity("...Started\n");

		// Initialize the BluetoothChatService to perform bluetooth connections
		mBTChatService = new BluetoothChatService(this, mBTHandler);

		// Initialize the buffer for outgoing messages - not sure what this is for yet...
		//mOutStringBuffer = new StringBuffer("");
		// initialize WifiChatService to perform Wifi connections
		//mWifiChatService = new WifiChatService(this, mHTTPDHandler, TCP_SERVER_PORT);
		File wwwpath=new File(Environment.getExternalStorageDirectory(),"wwwRoot");
		try {
			mHTTPDService = new RobotHTTPD(TCP_SERVER_PORT, mHTTPDHandler, wwwpath);
		} catch (IOException e) {
			Log.e("BTtoWIFIChatService", "IO exception on RobotHTTPD");
			e.printStackTrace();
		}
	}

	private void setupChat(Handler mBTHandler, Handler mWiFiHandler) {
		// Performing this check in onResume() covers the case in which BT was
		// not enabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
		if (mBTChatService != null) {
			// Only if the state is STATE_NONE, do we know that we haven't started already
			int theState = mBTChatService.getState();
			if (theState == BluetoothChatService.STATE_NONE ||
					theState == BluetoothChatService.STATE_CONNECTION_LOST) {
				// Start the Bluetooth chat services
				// we will do this if we want to accept a session, which we don't: mBTChatService.start();
				// GET device Address from list of paired devices.
				// If not paired to device, complain        
				mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
				boolean robotFound = false;
				// Get a set of currently paired devices
				Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
				String devname = "";
				String devaddr = "";

				// If there are paired devices, add each one to the ArrayAdapter
				if (pairedDevices.size() > 0) {
					for (BluetoothDevice device : pairedDevices) {
						devname = device.getName();
						devaddr = device.getAddress();
						if (D) Log.d("BTactivity ",  devname + " at " + devaddr);
						sendToMainActivity("Paired: " + devname + " at " + devaddr);
						if (devname.replace("\r","").replace("\n","").equals(ROBOTNAME)) {
							sendToMainActivity("Found what we are looking for: " + ROBOTNAME);
							robotFound = true;
							break;
						}
					}
				} else {
					if (D) Log.d("BTactivity" , ": NO PAIRED DEVICES");
				}
				if (robotFound) {
					// Get the BLuetoothDevice object
					BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(devaddr);
					// Attempt to connect to the device    
					mBTChatService.connect(device);
				} else {
					sendToMainActivity("Robot not found...Turn on Robot and pair to this Android.  Relaunch to retry.");
				}

			}
		}
		else {
			// big problems
			Log.e(TAG, "BT chat service is null");
		}
		/* All we have to do for HTTPD is new up the object
		if (mWifiChatService != null) {
			if (mWifiChatService.getState() == WifiChatService.STATE_NONE) {
				mWifiChatService.start();
			}
		}
		*/
	}

	private void startup() {
		initChat(mBTHandler, mHTTPDHandler);
		setupChat(mBTHandler, mHTTPDHandler);
	}



	public static boolean isRunning()
	{
		return isRunning;
	}
	@Override
	synchronized public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("MyService", "Received start id " + startId + ": " + intent);
		return START_STICKY; // run until explicitly stopped.
	}

	@Override 
	synchronized public void onDestroy() {
		Log.d("service", "onDestroy called, interrupting");
		//INTERRUPTME = true;
		shutdown();
		isRunning = false;
		super.onDestroy();
	};

	protected void shutdown() {
		if (mBTChatService != null) mBTChatService.stop();
		mBTChatService = null;
		/*
		if (mWifiChatService != null) mWifiChatService.stop();
		mWifiChatService = null;
		*/
		mHTTPDService.stop();
	}

	public void sendToMainActivity(String s, int messageType) {
		Bundle b = new Bundle();
		b.putString("str1", s);
		Message msg = Message.obtain(null, messageType);
		msg.setData(b);
		try {mClientMessenger.send(msg);}
		catch (RemoteException ex) {
			Log.d("TEST", "remote exception " + ex);
		} catch (NullPointerException ex) {
			Log.d("TEST", "got null messenger.  ignoring");
		}
		Log.d(TAG, s);
	}

	public void sendToMainActivity(String s) {
		Bundle b = new Bundle();
		b.putString("str1", s);
		Message msg = Message.obtain(null, MSG_SET_STRING_VALUE);
		msg.setData(b);
		try {mClientMessenger.send(msg);}
		catch (RemoteException ex) {
			Log.d("TEST", "remote exception " + ex);
		} catch (NullPointerException ex) {
			Log.d("TEST", "got null messenger.  ignoring");
		}
		Log.d(TAG, s);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		isRunning = true;
	}


/*
	private void writeToWifi(byte[] b, int size) {
		if (mWifiChatService.getState() != WifiChatService.STATE_CONNECTED)
			sendToMainActivity("Cant write to WIFI.  Not Connected...\n");
		else {
			Log.d("FUG", "writing to WIFI " + size + " bytes: ");
			mWifiChatService.write(b, size);
		}
	}
*/
	
	private void writeToBT(byte[] b) {
		if (mBTChatService.getState() != BluetoothChatService.STATE_CONNECTED)
			sendToMainActivity("Cant write to Bluetooth.  Not Connected...\n");
		else {
			Log.d("FUG", "writing to BT " + b.length + " bytes");
			mBTChatService.write(b);
		}
	}


}
