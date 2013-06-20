/*
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

import com.apportable.bluetooth.IConnection;
import com.apportable.bluetooth.IConnectionCallback;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * API for the Bluetooth Click, Link, Compete library. This library simplifies
 * the process of establishing Bluetooth connections and sending data in a way
 * that is geared towards multi-player games.
 */

public class Connection {
    public static final String TAG = "com.apportable.bluetooth.Connection";

    public static final int SUCCESS = 0;

    public static final int FAILURE = 1;

    public static final int MAX_SUPPORTED = 7;

    public interface OnConnectionServiceReadyListener {
        public void OnConnectionServiceReady();
    }

    public interface OnIncomingConnectionListener {
        public void OnIncomingConnection(String device);
    }

    public interface OnMaxConnectionsReachedListener {
        public void OnMaxConnectionsReached();
    }

    public interface OnMessageReceivedListener {
        public void OnMessageReceived(String device, String message);
    }

    public interface OnConnectionLostListener {
        public void OnConnectionLost(String device);
    }
    
    public interface OnSocketIOExceptionListener {
        public void OnSocketIOException();
    }

    private OnConnectionServiceReadyListener mOnConnectionServiceReadyListener;

    private OnIncomingConnectionListener mOnIncomingConnectionListener;

    private OnMaxConnectionsReachedListener mOnMaxConnectionsReachedListener;

    private OnMessageReceivedListener mOnMessageReceivedListener;

    private OnConnectionLostListener mOnConnectionLostListener;
    
    private OnSocketIOExceptionListener mOnSocketIOExceptionListener;

    private ServiceConnection mServiceConnection;

    private Context mContext;

    private String mPackageName = "";

    private boolean mStarted = false;

    private final Object mStartLock = new Object();

    private IConnection mIconnection;
    
	private boolean mShutdown;

    private IConnectionCallback mIccb = new IConnectionCallback.Stub() {
        public void incomingConnection(String device) throws RemoteException {
            if (mOnIncomingConnectionListener != null) {
                mOnIncomingConnectionListener.OnIncomingConnection(device);
            }
        }

        public void connectionLost(String device) throws RemoteException {
            if (mOnConnectionLostListener != null) {
                mOnConnectionLostListener.OnConnectionLost(device);
            }
        }

        public void maxConnectionsReached() throws RemoteException {
            if (mOnMaxConnectionsReachedListener != null) {
                mOnMaxConnectionsReachedListener.OnMaxConnectionsReached();
            }
        }

        public void messageReceived(String device, String message) throws RemoteException {
            if (mOnMessageReceivedListener != null) {
                mOnMessageReceivedListener.OnMessageReceived(device, message);
            }
        }

		public void socketIOException() throws RemoteException {
            if (mOnSocketIOExceptionListener != null) {
                mOnSocketIOExceptionListener.OnSocketIOException();
            }
		}
    };

    public Connection(Context ctx, OnConnectionServiceReadyListener ocsrListener, boolean isClient) {
        mOnConnectionServiceReadyListener = ocsrListener;
        mContext = ctx;
        mPackageName = ctx.getPackageName();
        mShutdown = false;
        mServiceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                synchronized (mStartLock) {
                	if (mShutdown) return;  // unbind doesn't happen fast enough?
                    mIconnection = IConnection.Stub.asInterface(service);
                    mStarted = true;
                    if (mOnConnectionServiceReadyListener != null) {
                        mOnConnectionServiceReadyListener.OnConnectionServiceReady();
                    }
                }
            }

            public void onServiceDisconnected(ComponentName name) {
                synchronized (mStartLock) {
                    try {
                        mStarted = false;
                        mIconnection.unregisterCallback(mPackageName);
                        mIconnection.shutdown(mPackageName);
                    } catch (RemoteException e) {
                        Log.e(TAG, "RemoteException in onServiceDisconnected", e);
                    }
                    mIconnection = null;
                }
            }
        };
        Intent intent;
        if (isClient) {
        	intent = new Intent(ctx, ConnectionClientService.class);
        } else {
        	intent = new Intent(ctx, ConnectionServerService.class);
        }
        mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public int startServer(final int maxConnections, OnIncomingConnectionListener oicListener,
            OnMaxConnectionsReachedListener omcrListener, OnMessageReceivedListener omrListener,
            OnConnectionLostListener oclListener, OnSocketIOExceptionListener msioListener) {
        if (!mStarted) {
            return Connection.FAILURE;
        }
        if (maxConnections > MAX_SUPPORTED) {
            Log.e(TAG, "The maximum number of allowed connections is " + MAX_SUPPORTED);
            return Connection.FAILURE;
        }
        mOnIncomingConnectionListener = oicListener;
        mOnMaxConnectionsReachedListener = omcrListener;
        mOnMessageReceivedListener = omrListener;
        mOnConnectionLostListener = oclListener;
        mOnSocketIOExceptionListener = msioListener;
        try {
            int result = mIconnection.startServer(mPackageName, maxConnections);
            mIconnection.registerCallback(mPackageName, mIccb);
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in startServer", e);
        }
        return Connection.FAILURE;
    }
    
    public void stopServer() {
    	try {
			mIconnection.stopServer();
		} catch (RemoteException e) {
            Log.e(TAG, "RemoteException in startServer", e);
		}
    }

    public int connect(String device, OnMessageReceivedListener omrListener,
            OnConnectionLostListener oclListener) {
        if (!mStarted) {
            return Connection.FAILURE;
        }
        mOnMessageReceivedListener = omrListener;
        mOnConnectionLostListener = oclListener;
        try {
            int result = mIconnection.connect(mPackageName, device);
            mIconnection.registerCallback(mPackageName, mIccb);
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in connect", e);
        }
        return Connection.FAILURE;
    }

    public int sendMessage(String device, String message) {
        if (!mStarted) {
            return Connection.FAILURE;
        }
        try {
            return mIconnection.sendMessage(mPackageName, device, message);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in sendMessage", e);
        }
        return Connection.FAILURE;
    }

    public int broadcastMessage(String message) {
        if (!mStarted) {
            return Connection.FAILURE;
        }
        try {
            return mIconnection.broadcastMessage(mPackageName, message);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in broadcastMessage", e);
        }
        return Connection.FAILURE;
    }

    public String getConnections() {
        if (!mStarted) {
            return "";
        }
        try {
            return mIconnection.getConnections(mPackageName);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in getConnections", e);
        }
        return "";
    }

    public int getVersion() {
        if (!mStarted) {
            return Connection.FAILURE;
        }
        try {
            return mIconnection.getVersion();
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in getVersion", e);
        }
        return Connection.FAILURE;
    }

    public String getAddress() {
        if (!mStarted) {
            return "";
        }
        try {
            return mIconnection.getAddress();
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in getAddress", e);
        }
        return "";
    }
    
    public String getName() {
        if (!mStarted) {
            return "";
        }
        try {
            return mIconnection.getName();
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in getVersion", e);
        }
        return "";
    }
    
    public void shutdown() {
    	Log.d(TAG, "in Connection.java shutdown " + mPackageName);
    	mShutdown = true;
        if (!mStarted) {
        	Log.d(TAG, "in Connection.java shutdown extra call " + mPackageName);
            return;
        }
        try {
            mStarted = false;
            if (mIconnection != null) {
            	mIconnection.unregisterCallback(mPackageName);
                mIconnection.shutdown(mPackageName);
            }
            mContext.unbindService(mServiceConnection);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in shutdown", e);
        }
    }
}
