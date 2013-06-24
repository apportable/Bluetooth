/* Adapted from DemoMultiscreen.java from https://code.google.com/p/apps-for-android/source/browse/
 *
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.apportable.bluetooth;

import com.apportable.bluetooth.Connection;
import com.apportable.bluetooth.Connection.OnConnectionLostListener;
import com.apportable.bluetooth.Connection.OnConnectionServiceReadyListener;
import com.apportable.bluetooth.Connection.OnIncomingConnectionListener;
import com.apportable.bluetooth.Connection.OnMaxConnectionsReachedListener;
import com.apportable.bluetooth.Connection.OnMessageReceivedListener;
import com.apportable.bluetooth.Connection.OnSocketIOExceptionListener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

public class BluetoothConnectionManager {
    public static final String TAG = "BluetoothConnectionManager";
    
    private BluetoothConnectionManager self;

    private int mType; // 0 = server, 1 = client
    
    private Context mContext;
    
    private String mName;

    private Connection mConnection;

    private BluetoothAdapter myBt;
    
    private boolean mConnectedToServer;
    
    private String mConnectedServer;
    
    private native void didPublish();
    
    private native void didStopPublishing();
    
    private native void connectionReceived(String clientDevice);
    
    private native void didReceive(String message, String device);
    
    private native void didConnectToServer(String hostAddress);
    
    private native void didDisconnect(String device);
    
    private native void resetBluetoothNeeded();
    
	private boolean mRegistered;

    private OnMessageReceivedListener dataReceivedListener = new OnMessageReceivedListener() {
        public void OnMessageReceived(String device, String message) {
        	Log.d(TAG, "Got message :" + message + "-from-" + device);
        	didReceive(message, device);
        }
    };

    private OnMaxConnectionsReachedListener maxConnectionsListener = new OnMaxConnectionsReachedListener() {
        public void OnMaxConnectionsReached() {
            Log.e(TAG, "Max connections reached!");
        }
    };

    private OnIncomingConnectionListener connectedListener = new OnIncomingConnectionListener() {
        public void OnIncomingConnection(String device) {
        	Log.d(TAG, "Got connection from device :" + device);
        	myBt.cancelDiscovery();  /* Stop trying to be a client */
            connectionReceived(device);
        }
    };

    private OnConnectionLostListener disconnectedListener = new OnConnectionLostListener() {
        public void OnConnectionLost(String device) {
        	Log.d(TAG, "Disconnected from device :" + device);
        	didDisconnect(device);
        }
    };
    
    private OnSocketIOExceptionListener socketIOExceptionListener = new OnSocketIOExceptionListener() {
        public void OnSocketIOException() {
        	Log.d(TAG, "Popup to restart bluetooth");
        	resetBluetoothNeeded();
        }
    };
    
    private void sendDeviceMessage(String device, String message) {
    	Log.d(TAG, "message length is " + message.length());
    	Log.d(TAG, "write -" + message + "-to " + device);
        mConnection.sendMessage(device, message);
    }
    
 // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                String name = device.getName();
//                Log.d(TAG, "found " + name + "---" + device.getAddress() + " mName is " + mName);
                if (name.endsWith(mName)) {
                	mConnectedToServer = true;
                    myBt.cancelDiscovery(); // Cancel BT discovery explicitly so that connections can go through
                    int connectionStatus;
                    synchronized(self) {
	                    connectionStatus = mConnection.connect(device.getAddress(), dataReceivedListener, disconnectedListener);
                    }
                    if (connectionStatus != Connection.SUCCESS) {
                        Log.d(TAG, "Unable to connect; please try again.");
                    	mConnectedToServer = false;
                    } else {
                    	// Successful client connection to server
                    	didConnectToServer(device.getAddress());
                    	mConnectedServer = device.getAddress();
                    }
                }
            }
        }
    };
    
    private void doPublish() {
        String name = myBt.getName();
        if (!name.endsWith(mName)) {
            myBt.setName(name + "-" + mName);
            name = myBt.getName();
        }
        synchronized(self) {
	        mConnection.startServer(4, connectedListener, maxConnectionsListener,
	                dataReceivedListener, disconnectedListener, socketIOExceptionListener);
        }
    	didPublish();
    }

    private OnConnectionServiceReadyListener serviceReadyListener = new OnConnectionServiceReadyListener() {
        public void OnConnectionServiceReady() {
            myBt = BluetoothAdapter.getDefaultAdapter();

            if (mType == 0) {
            	doPublish();
            } else {
            	// Register the BroadcastReceiver
            	IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            	mContext.registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
            	mRegistered = true;
                myBt.startDiscovery();
            }
        }
    };
    
    private String getAddress() {
    	return myBt.getAddress();
    }
    
    public void startPublishing() {
    	Log.d(TAG, "start publishing");
    	synchronized(this) {
    		mConnection = new Connection(mContext, serviceReadyListener, false);
    	}
    }
    
    public void stopPublishing() {
    	mConnection.stopServer();
    }
    
    private void startSearching() { 
	    synchronized(this) {
	    	mConnection = new Connection(mContext, serviceReadyListener, true);	
	    }
    }
    
    private boolean isConnectedToServer() {
    	Log.d(TAG, "mConnectedToServer is " + (mConnectedToServer ? "true" : "false"));
    	return mConnectedToServer;
    }
    
    public BluetoothConnectionManager(Context ctx, String name, boolean type) {
    	self = this;
    	mContext = ctx;
    	mName = name;
        mType = type ? 1 : 0;
    }
    
    private static boolean isAvailable() {
	    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	    if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
	    	return true;
	    } else {
	    	return false;
	    }
	}

    private void shutdown() {
    	Log.d(TAG, "shutdown!!!" + (mConnectedToServer ? " client" : " server"));
    	mConnectedToServer = false;
        if (mConnection != null) {
            mConnection.shutdown();
            mConnection = null;
        }
        if (mRegistered) {
        	mContext.unregisterReceiver(mReceiver);
        	mRegistered = false;
        }
    }
    
    private void disconnect() {
    	shutdown();
    }
}
