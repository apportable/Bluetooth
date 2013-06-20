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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 * Service for simplifying the process of establishing Bluetooth connections and
 * sending data in a way that is geared towards multi-player games.
 */

public class ConnectionService extends Service {
    public static final String TAG = "com.apportable.bluetooth.ConnectionClientService";

    private ArrayList<UUID> mUuid;

    private ConnectionService mSelf;

    private String mApp; // Assume only one app can use this at a time; may

    // change this later

    private IConnectionCallback mCallback;

    private ArrayList<String> mBtDeviceAddresses;

    private HashMap<String, BluetoothSocket> mBtSockets;

    private HashMap<String, Thread> mBtStreamWatcherThreads;

    private BluetoothAdapter mBtAdapter;
    
    private volatile boolean isListening = false;

    public ConnectionService() {
    	Log.d(TAG, "creating ConnectionService");
        mSelf = this;
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        mApp = "";
        mBtSockets = new HashMap<String, BluetoothSocket>();
        mBtDeviceAddresses = new ArrayList<String>();
        mBtStreamWatcherThreads = new HashMap<String, Thread>();
        mUuid = new ArrayList<UUID>();
        // Allow up to 7 devices to connect to the server
        mUuid.add(UUID.fromString("a60f35f0-b93a-11de-8a39-08002009c666"));
        mUuid.add(UUID.fromString("503c7430-bc23-11de-8a39-0800200c9a66"));
        mUuid.add(UUID.fromString("503c7431-bc23-11de-8a39-0800200c9a66"));
        mUuid.add(UUID.fromString("503c7432-bc23-11de-8a39-0800200c9a66"));
        mUuid.add(UUID.fromString("503c7433-bc23-11de-8a39-0800200c9a66"));
        mUuid.add(UUID.fromString("503c7434-bc23-11de-8a39-0800200c9a66"));
        mUuid.add(UUID.fromString("503c7435-bc23-11de-8a39-0800200c9a66"));
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    private class BtStreamWatcher implements Runnable {
        private String address;

        public BtStreamWatcher(String deviceAddress) {
            address = deviceAddress;
        }

        public void run() {
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            BluetoothSocket bSock = mBtSockets.get(address);
            try {
                InputStream instream = bSock.getInputStream();
                int bytesRead = -1;
                String message = "";
                while (true) {
                    message = "";
                    bytesRead = instream.read(buffer);
                    if (bytesRead != -1) {
                        while ((bytesRead == bufferSize) && (buffer[bufferSize - 1] != 0)) {
                            message = message + new String(buffer, 0, bytesRead);
                            bytesRead = instream.read(buffer);
                        }
                        message = message + new String(buffer, 0, bytesRead - 1); // Remove
                        // the
                        // stop
                        // marker
                        mCallback.messageReceived(address, message);
                    }
                }
            } catch (IOException e) {
                Log.i(TAG,
                        "IOException in BtStreamWatcher - probably caused by normal disconnection",
                        e);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in BtStreamWatcher while reading data", e);
            }
            // Getting out of the while loop means the connection is dead.
            try {
                BluetoothSocket myBsock = mBtSockets.get(address);
                myBsock.close();
                mBtDeviceAddresses.remove(address);
                mBtSockets.remove(address);
                mBtStreamWatcherThreads.remove(address);
                mCallback.connectionLost(address);
            } catch (NullPointerException e) {
                Log.e(TAG, "NullPointerException in BtStreamWatcher while disconnecting", e);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in BtStreamWatcher while disconnecting", e);
            } catch (IOException e) {
                Log.e(TAG, "IOException in BtStreamWatcher while disconnecting", e);
            }
        }
    }

    private class ConnectionWaiter implements Runnable {
        private String srcApp;

        private int maxConnections;

        public ConnectionWaiter(String theApp, int connections) {
            srcApp = theApp;
            maxConnections = connections;
        }

        public void run() {
            try {
            	Log.d(TAG, "starting ConnectionWaiter");
                for (int i = 0; i < Connection.MAX_SUPPORTED && maxConnections > 0; i++) {
                	if (!isListening) return;
                    BluetoothServerSocket myServerSocket = mBtAdapter
                            .listenUsingRfcommWithServiceRecord(srcApp, mUuid.get(i));
                	if (!isListening) return;
                    BluetoothSocket myBSock = myServerSocket.accept();
                	if (!isListening) return;
                    myServerSocket.close(); // Close the socket now that the
                    // connection has been made.

                    String address = myBSock.getRemoteDevice().getAddress();
                    mBtSockets.put(address, myBSock);
                    mBtDeviceAddresses.add(address);
                    Thread mBtStreamWatcherThread = new Thread(new BtStreamWatcher(address));
                    mBtStreamWatcherThread.start();
                    mBtStreamWatcherThreads.put(address, mBtStreamWatcherThread);
                    maxConnections = maxConnections - 1;
                    Log.d(TAG, "connected to client " + address + " maxConnections left is " + maxConnections);
                    if (mCallback != null) {
                        mCallback.incomingConnection(address);
                    }
                }
                if (mCallback != null) {
                    mCallback.maxConnectionsReached();
                }
            } catch (IOException e) {
            	try {
					mCallback.socketIOException();   // Android 4.2 servers will fail here when out of available bt sockets
				} catch (RemoteException e1) {
					Log.e(TAG, "Nested RemoteException in ConnectionService:ConnectionWaiter", e1);
				}
                Log.i(TAG, "IOException in ConnectionService:ConnectionWaiter", e);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in ConnectionService:ConnectionWaiter", e);
            }
        }
    }

    private BluetoothSocket getConnectedSocket(BluetoothDevice myBtServer, UUID uuidToTry) {
        BluetoothSocket myBSock;
        try {
            myBSock = myBtServer.createRfcommSocketToServiceRecord(uuidToTry);
            myBSock.connect();
            return myBSock;
        } catch (IOException e) {
            Log.i(TAG, "IOException in getConnectedSocket", e);
            if (mCallback != null) {
	            try {
					mCallback.socketIOException(); // Android 4.2 clients will fail here when out of available bt sockets
				} catch (RemoteException e1) {
	                Log.e(TAG, "RemoteException in getConnectedSocket", e);
				}
            }
        }
        return null;
    }

    private final IConnection.Stub mBinder = new IConnection.Stub() {
    	Thread mThread;
        public int startServer(String srcApp, int maxConnections) throws RemoteException {
            if (mApp.length() > 0) {
                return Connection.FAILURE;
            }
            mApp = srcApp;
            isListening = true;
            Log.d(TAG, "Starting thread");
            mThread = (new Thread(new ConnectionWaiter(srcApp, maxConnections)));
            mThread.start();
            Intent i = new Intent();
            i.setClass(mSelf, StartDiscoverableModeActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return Connection.SUCCESS;
        }
        
        public void stopServer() {
        	Log.d(TAG, "in sstopServer");
        	isListening = false;
        }

        public int connect(String srcApp, String device) throws RemoteException {
            if (mApp.length() > 0) {
                return Connection.FAILURE;
            }
            mApp = srcApp;
            BluetoothDevice myBtServer = mBtAdapter.getRemoteDevice(device);
            BluetoothSocket myBSock = null;

            for (int i = 0; i < Connection.MAX_SUPPORTED && myBSock == null; i++) {
                for (int j = 0; j < 3 && myBSock == null; j++) {
                    myBSock = getConnectedSocket(myBtServer, mUuid.get(i));
                    if (myBSock == null) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "InterruptedException in connect", e);
                        }
                    }
                }
            }
            if (myBSock == null) {
                return Connection.FAILURE;
            }

            mBtSockets.put(device, myBSock);
            mBtDeviceAddresses.add(device);
            Thread mBtStreamWatcherThread = new Thread(new BtStreamWatcher(device));
            mBtStreamWatcherThread.start();
            mBtStreamWatcherThreads.put(device, mBtStreamWatcherThread);
            return Connection.SUCCESS;
        }

        public int broadcastMessage(String srcApp, String message) throws RemoteException {
            if (!mApp.equals(srcApp)) {
                return Connection.FAILURE;
            }
            for (int i = 0; i < mBtDeviceAddresses.size(); i++) {
                sendMessage(srcApp, mBtDeviceAddresses.get(i), message);
            }
            return Connection.SUCCESS;
        }

        public String getConnections(String srcApp) throws RemoteException {
            if (!mApp.equals(srcApp)) {
                return "";
            }
            String connections = "";
            for (int i = 0; i < mBtDeviceAddresses.size(); i++) {
                connections = connections + mBtDeviceAddresses.get(i) + ",";
            }
            return connections;
        }

        public int getVersion() throws RemoteException {
            try {
                PackageManager pm = mSelf.getPackageManager();
                PackageInfo pInfo = pm.getPackageInfo(mSelf.getPackageName(), 0);
                return pInfo.versionCode;
            } catch (NameNotFoundException e) {
                Log.e(TAG, "NameNotFoundException in getVersion", e);
            }
            return 0;
        }

        public int registerCallback(String srcApp, IConnectionCallback cb) throws RemoteException {
            if (!mApp.equals(srcApp)) {
                return Connection.FAILURE;
            }
            Log.d(TAG, "registered callback for " + srcApp);
            mCallback = cb;
            return Connection.SUCCESS;
        }

        public int sendMessage(String srcApp, String destination, String message) throws RemoteException {
        	Log.d(TAG, "in write " + srcApp + "--" + mApp + "--" + destination + "--" + message);
            if (!mApp.equals(srcApp)) {
                return Connection.FAILURE;
            }
            try {
                BluetoothSocket myBsock = mBtSockets.get(destination);
                if (myBsock != null) {
                    OutputStream outStream = myBsock.getOutputStream();
                    byte[] stringAsBytes = (message + " ").getBytes();
                    stringAsBytes[stringAsBytes.length - 1] = 0; // Add a stop
                    // marker
                    outStream.write(stringAsBytes);
                	Log.d(TAG, "after write");
//                    try {
//						Thread.sleep(50);   // Give write time to get through
//					} catch (InterruptedException e) {
//						Thread.currentThread().interrupt();
//					}
                    return Connection.SUCCESS;
                }
            } catch (IOException e) {
                Log.i(TAG, "IOException in sendMessage - Dest:" + destination + ", Msg:" + message,
                        e);
            }
            return Connection.FAILURE;
        }

        public void shutdown(String srcApp) throws RemoteException {
        	Log.d(TAG, "In ConnectionService.java shutdown");
        	Log.d(TAG, "mBtDeviceAddresses.size()  is " + mBtDeviceAddresses.size());
            try {
                for (int i = 0; i < mBtDeviceAddresses.size(); i++) {
                    BluetoothSocket myBsock = mBtSockets.get(mBtDeviceAddresses.get(i));
                    Log.d(TAG, "closing " + mBtDeviceAddresses.get(i));
                    myBsock.close();
                }
                mBtSockets = new HashMap<String, BluetoothSocket>();
                mBtStreamWatcherThreads = new HashMap<String, Thread>();
                mBtDeviceAddresses = new ArrayList<String>();
                mApp = "";
            } catch (IOException e) {
                Log.i(TAG, "IOException in shutdown", e);
            }
        }

        public int unregisterCallback(String srcApp) throws RemoteException {
            if (!mApp.equals(srcApp)) {
                return Connection.FAILURE;
            }
            Log.d(TAG, "unregisterCallback setting mCallback to NULL");
            mCallback = null;
            return Connection.SUCCESS;
        }

        public String getAddress() throws RemoteException {
            return mBtAdapter.getAddress();
        }
        
        public String getName() throws RemoteException {
            return mBtAdapter.getName();
        }
    };

}
