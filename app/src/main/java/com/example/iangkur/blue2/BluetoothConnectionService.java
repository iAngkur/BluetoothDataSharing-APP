package com.example.iangkur.blue2;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

public class BluetoothConnectionService {

    private static final String TAG = "BluetoothConnectivity";
    private static final String appName = "MyApp";

    private static final UUID My_UUID = UUID.fromString("9ba901e3-e1dd-4114-844e-3ee57ad39a02");

    private final BluetoothAdapter mbluetoothAdapter;
    Context mcontext;

    private AcceptThread mInsecureAcceptThread;

    private ConnectThread mconnectThread;
    private BluetoothDevice mDevice;
    private UUID deviceUUID;
    ProgressDialog mprogressDialog;

    private ConnectedThread mconnectedThread;


    public BluetoothConnectionService(Context context) {
        mcontext = context;
        mbluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        start();
    }

    private class AcceptThread  extends Thread{
        private final BluetoothServerSocket mserverSocket;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;

            try {
                tmp = mbluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, My_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mserverSocket = tmp;

        }

        public void run(){

            BluetoothSocket socket = null;

            try {
                socket = mserverSocket.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (socket != null){
                connected(socket, mDevice);
            }

        }

        public void cancel(){
            try {
                mserverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private class ConnectThread extends Thread{
        private BluetoothSocket msocket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            mDevice = device;
            deviceUUID = uuid;
        }

        public void run(){
            BluetoothSocket tmp = null;


            try {
                tmp = mDevice.createRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            msocket = tmp;

            mbluetoothAdapter.cancelDiscovery();

            try {
                msocket.connect();
            } catch (IOException e) {
                try {
                    msocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }

            connected(msocket, mDevice);
        }

        public void cancel(){
            try {
                msocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized  void start(){
        if (mconnectThread != null){
            mconnectThread.cancel();
            mconnectThread = null;
        }

        if (mInsecureAcceptThread == null){
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }
    }

    public void startClient(BluetoothDevice device, UUID uuid){
        mprogressDialog = ProgressDialog.show(mcontext, "Connecting Bluetooth", "Please Wait....", true);
        mconnectThread = new ConnectThread(device, uuid);
        mconnectThread.start();
    }

    private class ConnectedThread  extends Thread{
        private final BluetoothSocket msocket;
        private final InputStream minputStream;
        private final OutputStream moutputStream;

        public ConnectedThread(BluetoothSocket socket) {
            msocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;


            try {
                mprogressDialog.dismiss();
            }catch (NullPointerException e){
                e.printStackTrace();
            }


            try {
                tmpIn = msocket.getInputStream();
                tmpOut = msocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            minputStream = tmpIn;
            moutputStream = tmpOut;

        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true){
                try {
                    bytes = minputStream.read(buffer);
                    String incomingMessage = new String(buffer, 0, bytes);

                    Intent incomingMessageIntent = new Intent("incomingMessage");
                    incomingMessageIntent.putExtra("theMessage", incomingMessage);
                    LocalBroadcastManager.getInstance(mcontext).sendBroadcast(incomingMessageIntent);

                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        public void write(byte[] bytes){
            String text = new String (bytes, Charset.defaultCharset());
            try {
                moutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cancel(){
            try {
                msocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void connected(BluetoothSocket msocket, BluetoothDevice mDevice) {
        mconnectedThread = new ConnectedThread(msocket);
        mconnectedThread.start();
    }

    public void write(byte[] out){
       ConnectedThread r;
       mconnectedThread.write(out);
    }
}
