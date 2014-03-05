/*
 * Created by Javier Montaner  (twitter: @tumaku_) during M-week (February 2014) of MakeSpaceMadrid
 * http://www.makespacemadrid.org
 * @ 2014 Javier Montaner
 * 
 * Licensed under the MIT Open Source License 
 * http://opensource.org/licenses/MIT
 * 
 * Many thanks to Yeelight (special mention to Daping Liu) and Double Encore (Dave Smith)
 * for their support and shared knowlegde
 * 
 * Based on the API released by Yeelight:
 * http://www.yeelight.com/en_US/info/download
 * 
 * Based on the code created by Dave Smith (Double Encore):
 * https://github.com/devunwired/accessory-samples/tree/master/BluetoothGatt
 * http://www.doubleencore.com/2013/12/bluetooth-smart-for-android/
 * 
 * 
 * Scan Bluetooth Low Energy devices and their services and characteristics.
 * If the Yeelight Service is found, an activity can be launched to control colour and intensity of Yeelight Blue bulb
 * 
 * Tested on a Nexus 7 (2013)
 * 
 */



package com.tumaku.msmble;

import java.util.ArrayList;
import java.util.List;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TumakuBLE {
	
	public static final String YEELIGHT_SERVICE = "0000fff0-0000-1000-8000-00805f9b34fb";

	public static final String CHARACTERISTIC_CONTROL = "0000fff1-0000-1000-8000-00805f9b34fb";

	public static final String DEVICE_CONNECTED ="com.yeelight.blue.DEVICE_CONNECTED2";
	
	public static final String DEVICE_DISCONNECTED ="com.yeelight.blue.DEVICE_DISCONNECTED2";		
			
	public static final String SERVICES_DISCOVERED ="com.yeelight.blue.SERVICES_DISCOVERED";

	public static final String WRITE_SUCCESS = "com.yeelight.blue.WRITE_SUCCESS";
	
	public static final String READ_SUCCESS = "com.yeelight.blue.READ_SUCCESS";

	public static final String EXTRA_ADDRESS = "address";

	public static final String EXTRA_NAME = "name";

	public static final String EXTRA_CHARACTERISTIC  = "characteristic";

	public static final String EXTRA_VALUE = "address";

	public static final String EXTRA_SCANNING = "scanning";

	public static final String EXTRA_DEVICE  = "device";

	public static final String EXTRA_SERVICE  = "service";
		
	public static final String EXTRA_RESULT  = "result";
	
	public static final String EXTRA_FULL_RESET = "fullreset";
	
	
	private static BluetoothDevice mDevice;
	
	private static String mDeviceAddress;
	
	private static BluetoothGatt mGatt;
	
	private static Context mContext;
	
	private static List<ServiceType> mServices;
	
	private static MyCallBack mCallBack;
	
	private static BluetoothAdapter mAdapter=null;	
	
	private static TumakuBLE mTumakuBLE=null;

	private TumakuBLE(Context context){
		mContext= context;
		mDeviceAddress= null;
		mServices= new ArrayList <ServiceType> ();
		mCallBack = new MyCallBack();
	}
	
	public static TumakuBLE getInstance(Context context) {
		if(mTumakuBLE==null){
			mTumakuBLE = new TumakuBLE(context);
			setup();
		}
		return mTumakuBLE;
	} 
	
	public static void  resetTumakuBLE() {
		mDeviceAddress=null;
		disconnect();
		mGatt=null;
		if (mServices!=null) mServices.clear();
	}
		
		
	public static void setup() {
		BluetoothManager manager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
		mAdapter = manager.getAdapter();
	}

	public static void setContext(Context context) {
		mContext= context;
	}

	public void setDeviceAddress(String address) {
		mDeviceAddress=address;		
	}

	public String getDeviceAddress() {
		return mDeviceAddress;		
	}
	public List<ServiceType> getServices() {
		return mServices;		
	}
	
	public ServiceType getService(String serviceUUID) {
		for (ServiceType serviceInList : mServices)  {
			if (serviceInList.getService().getUuid().toString().equalsIgnoreCase(serviceUUID)) 
				return serviceInList;
		}
		return null;
	}
	
	public void startLeScan() {
		mAdapter.startLeScan((LeScanCallback)mContext);
	}
	
	public void stopLeScan() {
		mAdapter.stopLeScan((LeScanCallback)mContext);
	}
	
	public void connect() {
		mDevice   = mAdapter.getRemoteDevice(mDeviceAddress);
		mServices.clear();
		if(mGatt!=null){
			mGatt.connect();
		}else{
			mDevice.connectGatt(mContext, false, mCallBack);
		}
	}

	public void discoverServices() {
		if (Constant.DEBUG)
			Log.i("JMG", "Scanning services and caracteristics");				
		mGatt.discoverServices(); 
	}
	
	public static void disconnect(){
		if (mGatt!=null) {
			try{
				mGatt.disconnect();
				mGatt.close();
				if (Constant.DEBUG)
					Log.i("JMG", "Disconnecting GATT");				
			} catch(Exception ex){};
		}
		mGatt = null;
	}

	public boolean isConnected(){
		return (mGatt!=null);
	}

		
	
	public BluetoothGattCharacteristic findCharacteristic(String serviceUUID, String characteristicUUID){
		
		for (ServiceType serviceInList : mServices) {
			if (serviceInList.getService().getUuid().toString().equalsIgnoreCase(serviceUUID) ){
				for (BluetoothGattCharacteristic characteristicInList : serviceInList.getCharacteristics()) {
					if (characteristicInList.getUuid().toString().equalsIgnoreCase(characteristicUUID) ){
						return characteristicInList;
					}
				}											
			}
		}
		if(Constant.DEBUG)
			Log.i("JMG", "Characterisctic not found. Service: " + serviceUUID + " Characterisctic: " + characteristicUUID);			
		return null;
	}
	
	
	public void read(String serviceUUID, String characteristicUUID){
		BluetoothGattCharacteristic characteristic = findCharacteristic(serviceUUID, characteristicUUID);	
		if(characteristic!=null){
			mGatt.readCharacteristic(characteristic);
		} else {
			if(Constant.DEBUG) Log.i("JMG","Read Characteristic not found in device");
		}

	}
	

	public void write(String serviceUUID, String characteristicUUID, String data){
		BluetoothGattCharacteristic characteristic = findCharacteristic(serviceUUID, characteristicUUID);	
		if(characteristic!=null){
			characteristic.setValue(data.getBytes());
			mGatt.writeCharacteristic(characteristic);
			if(Constant.DEBUG) Log.i("JMG","Write Characteristic " + characteristicUUID + " with value " + data);
		} else {
			if(Constant.DEBUG) Log.i("JMG","Write Characteristic not found in device");
		}
		
	}
	
	
	
	public class ServiceType {
		private BluetoothGattService mService;
		private List<BluetoothGattCharacteristic> mCharacteristics;
		
		ServiceType(BluetoothGattService service) {
			mService = service;
			mCharacteristics= new ArrayList <BluetoothGattCharacteristic> ();
		}		
		
		public BluetoothGattService getService() {return mService;}
		public List<BluetoothGattCharacteristic> getCharacteristics () {return mCharacteristics;}
	}
	
	
    public static String bytesToString(byte[] bytes){
  	  StringBuilder stringBuilder = new StringBuilder(
                  bytes.length);
          for (byte byteChar : bytes)
              stringBuilder.append(String.format("%02X ", byteChar));
          return stringBuilder.toString();
    }


	
	
	public class MyCallBack extends BluetoothGattCallback{
		
		
		MyCallBack() { }


		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
				int newState) {
			super.onConnectionStateChange(gatt, status, newState);
			
			
			if(Constant.DEBUG){
				if (status!=0) {
					Log.i("JMG", "Error status received onConnectionStateChange: " + status + " - New state: " + newState);	
				} else {
					Log.i("JMG", "onConnectionStateChange received. status = " + status +
							" - State: " + newState);
				}
			}
			
			if ((status==133)||(status==257)) {
				if(Constant.DEBUG)
					Log.i("JMG", "Unrecoverable error 133 or 257. DEVICE_DISCONNECTED intent broadcast with full reset");	
				Intent intent = new Intent(DEVICE_DISCONNECTED);
				intent.putExtra(EXTRA_FULL_RESET, EXTRA_FULL_RESET);				
				mContext.sendBroadcast(intent);			
				return;
			}
							
			if (newState==BluetoothProfile.STATE_CONNECTED&&status==BluetoothGatt.GATT_SUCCESS){ //Connected
				    mGatt=gatt;
					if(Constant.DEBUG)
						Log.i("JMG", "New connected Device. DEVICE_CONNECTED intent broadcast");				
					Intent intent = new Intent(DEVICE_CONNECTED);
					intent.putExtra(EXTRA_ADDRESS, gatt.getDevice().getAddress());
					mContext.sendBroadcast(intent);			
					return;
			}
			
			if (newState==BluetoothProfile.STATE_DISCONNECTED&&status==BluetoothGatt.GATT_SUCCESS){ //Connected
				if(Constant.DEBUG)
					Log.i("JMG", "Disconnected Device. DEVICE_DISCONNECTED intent broadcast");	
				Intent intent = new Intent(DEVICE_DISCONNECTED);
				mContext.sendBroadcast(intent);			
				return;
			}
			if(Constant.DEBUG)
				Log.i("JMG", "Unknown values received onConnectionStateChange. Status: " + status + " - New state: " + newState);				
		}


		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			super.onReadRemoteRssi(gatt, rssi, status);
		}
		

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			super.onCharacteristicWrite(gatt, characteristic, status);
			if(Constant.DEBUG){
				if(status==0){
					Log.i("JMG", "Write success ,characteristic uuid=:"+characteristic.getUuid().toString());
				}else{
					Log.i("JMG", "Write fail ,characteristic uuid=:"+characteristic.getUuid().toString()+" status="+status);
				}
			}
			Intent intent = new Intent(WRITE_SUCCESS);
			intent.putExtra(EXTRA_CHARACTERISTIC, characteristic.getUuid().toString());
			mContext.sendBroadcast(intent);	
		}

		
		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			// TODO Auto-generated method stub
			super.onCharacteristicRead(gatt, characteristic, status);
			if(Constant.DEBUG) {
				if(status==0){
					Log.i("JMG", "Read from:"+characteristic.getUuid().toString()+" value: "+ bytesToString(characteristic.getValue()));
				} else {
					Log.i("JMG", "Read fail ,characteristic uuid=:"+characteristic.getUuid().toString()+" status="+status);
				}					
			}
			Intent intent = new Intent(READ_SUCCESS);
			intent.putExtra(EXTRA_CHARACTERISTIC, characteristic.getUuid().toString());
			intent.putExtra(EXTRA_VALUE, bytesToString(characteristic.getValue()));
			mContext.sendBroadcast(intent);	
		}
		

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicChanged(gatt, characteristic);
			String uuid = characteristic.getUuid().toString();
			if(Constant.DEBUG){
				Log.i("JMG", "NOTIFICATION onCharacteristicChanged for characteristic " + uuid + 
						" value: " + bytesToString(characteristic.getValue()));
			}
			//TODO send broadcast message
		}
		
		
		@Override
		public void  onServicesDiscovered(BluetoothGatt gatt, int status) {
			super.onServicesDiscovered(gatt, status);
			if(Constant.DEBUG)
				Log.i("JMG", "onServicesDiscovered status: " + status);
			
			for(BluetoothGattService serviceInList: gatt.getServices()){
				String serviceUUID=serviceInList.getUuid().toString();
				ServiceType serviceType=new ServiceType(serviceInList);
				List <BluetoothGattCharacteristic> characteristics= serviceType.getCharacteristics();
				if(Constant.DEBUG)
					Log.i("JMG", "New service: " + serviceUUID);
				for(BluetoothGattCharacteristic characteristicInList : serviceInList.getCharacteristics()){
					if(Constant.DEBUG)
						Log.i("JMG", "New characteristic: " + characteristicInList.getUuid().toString());
					characteristics.add(characteristicInList);
				}
				mServices.add(serviceType);
			}
			Intent intent = new Intent(SERVICES_DISCOVERED);
			intent.putExtra(EXTRA_ADDRESS, gatt.getDevice().getAddress());
			mContext.sendBroadcast(intent);		
		}
		

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
			// TODO Auto-generated method stub
			super.onDescriptorWrite(gatt, descriptor, status);
			if(Constant.DEBUG)
				Log.i("JMG", "onDescriptorWrite "+ descriptor.getUuid().toString() + " status: " + status);		
		}
	}

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
