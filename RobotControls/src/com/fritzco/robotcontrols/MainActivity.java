package com.fritzco.robotcontrols;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.fritzco.robotcontrols.R;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivityWifiBTChat";
    private static final boolean D = true;

	TextView logTextView;
	TextView statusTextView;

	String ipAddr;
	String BTStat = " Disconnected ";
	String WiFiStat = " Disconnected ";
	
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    
    private SharedPreferences mySharedPreferences;
    private static final String MY_PREFS = "my prefs";
    private static final String TCP_PORT = "tcp port";
    private static final String NAME_OF_ROBOT = "name of robot";
    private static final String Default_robot_name = "RN42-Roboto001-762E";
    private static final int Default_port = 2000;
    // These are defaults at startup.  
    // TODO: add these to PREFERENCES the right way.
    String ROBOTNAME = Default_robot_name;
    int TCP_SERVER_PORT = Default_port;
    
    Messenger mService = null;
    boolean mIsBound;
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            //TextView tv = (TextView)findViewById(R.id.textView1);
            addToLog("Attached.");
            try {
                Message msg = Message.obtain(null, BTtoWIFIChatService.MSG_REGISTER_CLIENT);
                Bundle b = new Bundle();
                b.putInt("PORT", TCP_SERVER_PORT);
                b.putString("ROBOT", ROBOTNAME);
                msg.setData(b);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
            	Log.d(TAG, "remoteexception in onserviceconnected" + e);
                // In this case the service has crashed before we could even do anything with it
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
            mService = null;
            addToLog("Disconnected.\n");
        }
    };
    
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case BTtoWIFIChatService.MSG_SET_STRING_VALUE:
                String str1 = msg.getData().getString("str1");
                addToLog(str1);
                break;
            case BTtoWIFIChatService.MSG_SEND_BT_DEVICE_NAME:
            	BTStat = msg.getData().getString("str1");
            	break;
            case BTtoWIFIChatService.MSG_SEND_BT_STATUS:
            	BTStat = msg.getData().getString("str1");
            	break;
            case BTtoWIFIChatService.MSG_SEND_WIFI_DEVICE_NAME:
            	WiFiStat = msg.getData().getString("str1");
            	break;
            case BTtoWIFIChatService.MSG_SEND_WIFI_STATUS:
            	WiFiStat = msg.getData().getString("str1");
            	break;
            default:
                addToLog("unimplemented message from service: " + msg.getData().getString("str1"));
                break;
            }
            updateStatusBar();
        }
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");
        setContentView(R.layout.activity_main);
        
        // Get local Bluetooth adapter.  this is a basic check at start of app
        // we don't start/bind to service if this isn't on
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // get text view for logging
        logTextView = (TextView) findViewById(R.id.logTextView);
        logTextView.setMovementMethod(new ScrollingMovementMethod());
        onCreateDoRestoreInstanceState(savedInstanceState);
        
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // If we make it here we have bluetooth, we assume everything has wifi...
		Button button = (Button)findViewById(R.id.buttonStart);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Start
				doBindService();
			}
		});
		Button button2 = (Button)findViewById(R.id.buttonStop);
		button2.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Start
				doUnbindService();
			}
		});
		showIPAddr();
    }
    void doBindService() {
    	addToLog("binding");
    	mIsBound = bindService(new Intent(this, BTtoWIFIChatService.class), mConnection, Context.BIND_AUTO_CREATE);
        addToLog("Bound: " + mIsBound);
    }
    
    void doUnbindService() {
    	addToLog("unbinding");
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, BTtoWIFIChatService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.  this calls onDestroy in service.
            unbindService(mConnection);
            mIsBound = false;
            addToLog("Unbound.");
            BTStat = " Disconnected ";
            WiFiStat = " Disconnected ";
        }
    }
    
    protected void onCreateDoRestoreInstanceState(Bundle savedInstanceState) {
    	if (savedInstanceState != null) {
			TCP_SERVER_PORT = savedInstanceState.getInt(TCP_PORT, Default_port);
	    	ROBOTNAME = savedInstanceState.getString(NAME_OF_ROBOT, Default_robot_name);
	    	logTextView.setText(savedInstanceState.getString("tv", "FOOBAR"));
    	}
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	outState.putString("tv", logTextView.getText().toString());
    	outState.putString(NAME_OF_ROBOT , ROBOTNAME);
    	outState.putInt(TCP_PORT, TCP_SERVER_PORT);
    };
    
    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, just put up a toast that says go turn it on
        if (!mBluetoothAdapter.isEnabled()) {
        	Toast.makeText(this, "Bluetooth is not turned on.  Go turn on Bluetooth", 
        			Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");
        //CheckIfServiceIsRunning();
        doBindService();// bind always 
    }

    private void CheckIfServiceIsRunning() {
        //If the service is running when the activity starts, we want to automatically bind to it.
        if (BTtoWIFIChatService.isRunning()) {
        	addToLog("service is running.  binding");
            doBindService();
        }
    }
    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if(D) Log.e(TAG, "--- ON DESTROY --- unbinding");
        doUnbindService();
    }
    /*
    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }
    */
    
    private void addToLog(String s) {
    	//logTextView.getEditableText().insert(0, s);
    	logTextView.append(s);
    	Log.d(TAG, s);
    }
    private void updateStatusBar() {
        statusTextView = (TextView) findViewById(R.id.statusTextView);
        statusTextView.setText("IP: " + ipAddr + "\nWifi: " + WiFiStat + "\nBlueTooth: " + BTStat +
        		"\nPort " + TCP_SERVER_PORT + " Robot: " + ROBOTNAME);
    }
    
    private void showIPAddr() {
    	// what is my IP address?
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        ipAddr = intToIp(ipAddress);
        addToLog("My IP address is: " + ipAddr + "\n");
        updateStatusBar();
    }
    
    private String intToIp(int i) {
        return (i & 0xFF ) + "." +
                    ((i >> 8 ) & 0xFF) + "." +
                    ((i >> 16 ) & 0xFF) + "." +
                    ( (i >> 24 ) & 0xFF) ;
     }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate( R.menu.activity_main, menu);
        return true;
    }
    

    
    
}
