/* Created by Javier Montaner  (twitter: @tumaku_) during M-week (February 2014) of MakeSpaceMadrid
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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class HM10Activity extends Activity {
	
	/* SensorTag BLE procedure:
	 * The sensors in SensorTag require a special mechanism to use them.
	 * In order to save battery power, every sensor needs to be enabled (i.e. activated) prior 
	 * to reading it or subscribing to notifications on value changes.
	 * This is done by writing a byte into a characteristic present in every service (*_CONF)
	 * Once the sensor is enabled, standard BLE mechanisms apply:
	 * - you can read its value
	 * - you can (un)subscribe to notifications  writing the *-DATA characteristic descriptor 
	 * 
	 * A special case is the Key service that controls the two buttons on sensor tag. This service 
	 * does not require to be enabled. To interact with this service, only the notification mechanism 
	 * applies (read value is not supported - to be confirmed)
	 */
			

	private final static int WSTATE_CONNECT=0;
	private final static int WSTATE_SEARCH_SERVICES=1;
	private final static int WSTATE_NOTIFY_KEY=2;
	private final static int WSTATE_READ_KEY=3;
	private final static int WSTATE_DUMMY=4;
	private final static int WSTATE_WRITE_KEY=5;
	

	private TextView mTextReceived;
	private TextView mTextLongReceived;
	private EditText mTextSent;
	private TextView mTextInfo;
	private TextView mTextNotification;
	private Button mButtonRead;
	private Button mButtonSend;
	private Button mButtonReset;

	private Context mContext;
	private HM10BroadcastReceiver mBroadcastReceiver;
	private String mDeviceAddress;
	private int mState=0;
	
	private TumakuBLE  mTumakuBLE=null;
	
    
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hm10);           
        mContext=this;
        mBroadcastReceiver= new HM10BroadcastReceiver();
        mDeviceAddress=getIntent().getStringExtra(TumakuBLE.EXTRA_ADDRESS);
        if (mDeviceAddress==null) {
            if (Constant.DEBUG) Log.i("JMG","No device address received to start SensorTag Activity");
        	finish();
        }
		mTumakuBLE=((TumakuBLEApplication)getApplication()).getTumakuBLEInstance(this);
        mTumakuBLE.setDeviceAddress(mDeviceAddress);
        mTextReceived = (TextView) findViewById(R.id.receivedText);
        mTextLongReceived= (TextView) findViewById(R.id.longReceivedText);
        mTextSent = (EditText) findViewById(R.id.sentText);
        mTextInfo = (TextView) findViewById(R.id.textInfo);
        mTextNotification = (TextView) findViewById(R.id.textNotification);
        mButtonRead= (Button) findViewById(R.id.buttonRead);
        mButtonSend= (Button) findViewById(R.id.buttonSend);
        mButtonReset= (Button) findViewById(R.id.buttonReset);

        
        mButtonRead.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        	    if ((mState==WSTATE_DUMMY)) {
	        	    mState=WSTATE_READ_KEY;
	        	    nextState();  
        	    }
            }
        });

        mButtonSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        	    if ((mState==WSTATE_DUMMY)) {
	        	    mState=WSTATE_WRITE_KEY;
	        	    nextState();  
        	    } else 
 	    		   Toast.makeText(mContext, "Cannot send data in current statet. Do a reset first.", Toast.LENGTH_SHORT).show();    
            }
        });
        
        mButtonReset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        	        mState=WSTATE_CONNECT;
           		    mTumakuBLE.resetTumakuBLE();
            		mTumakuBLE.setDeviceAddress(mDeviceAddress);
        		 	updateInfoText("Reset connection to device");
        		 	mTextLongReceived.setText("");
	        	    nextState();  
	        	    
            }
        });
	
	}
	
	@Override
	public void onResume(){
		super.onResume();
		IntentFilter filter = new IntentFilter(TumakuBLE.WRITE_SUCCESS);
		filter.addAction(TumakuBLE.READ_SUCCESS);		
		filter.addAction(TumakuBLE.DEVICE_CONNECTED);		
		filter.addAction(TumakuBLE.DEVICE_DISCONNECTED);		
		filter.addAction(TumakuBLE.SERVICES_DISCOVERED);		
		filter.addAction(TumakuBLE.NOTIFICATION);		
		filter.addAction(TumakuBLE.WRITE_DESCRIPTOR_SUCCESS);		
		this.registerReceiver(mBroadcastReceiver, filter);
		if (mTumakuBLE.isConnected()){
			mState=WSTATE_NOTIFY_KEY;
			nextState();
		 	updateInfoText("Resume connection to device");
		} else {
			mState=WSTATE_CONNECT;
			nextState();
		 	updateInfoText("Start connection to device");
		}
		
	}
	
	@Override
	public void onStop(){
		super.onStop();
        this.unregisterReceiver(this.mBroadcastReceiver);      
	}
	
			
	
		protected void nextState(){
			switch(mState) {			   
			   case (WSTATE_CONNECT):
			       if (Constant.DEBUG) Log.i("JMG","State Connected");
			       mTumakuBLE.connect();
			       break;
			   case(WSTATE_SEARCH_SERVICES):
			       if (Constant.DEBUG) Log.i("JMG","State Search Services");
				   mTumakuBLE.discoverServices();
			       break;			   
			   case(WSTATE_READ_KEY):
			       if (Constant.DEBUG) Log.i("JMG","State Read Key");
			   	   mTumakuBLE.read(TumakuBLE.SENSORTAG_KEY_SERVICE,TumakuBLE.SENSORTAG_KEY_DATA);
				   break;
			   case(WSTATE_NOTIFY_KEY):
			       if (Constant.DEBUG) Log.i("JMG","State Notify Key");
		   	       mTumakuBLE.enableNotifications(TumakuBLE.SENSORTAG_KEY_SERVICE,TumakuBLE.SENSORTAG_KEY_DATA,true);
				   break;

			   case(WSTATE_WRITE_KEY):
				   String tmpString=mTextSent.getText().toString();
			   	   mTextSent.setText("");
			       if (Constant.DEBUG) Log.i("JMG","State Write State " + tmpString);
			       byte tmpArray []= new byte[tmpString.length()];
			       for (int i=0; i<tmpString.length();i++) tmpArray[i]=(byte)tmpString.charAt(i);
		   	       mTumakuBLE.write(TumakuBLE.SENSORTAG_KEY_SERVICE,TumakuBLE.SENSORTAG_KEY_DATA, tmpArray);
				   break;


			   default:
				   
			}			
			
		}
		


		protected void updateInfoText(String text) {
			mTextInfo.setText(text);
		}

		protected void updateNotificationText(String text) {
			mTextNotification.setText(text);
		}

		protected void displayText(String text) {
			mTextReceived.setText(text);
		}
  
		protected void updateLongText(String text) {
			String tmp=mTextLongReceived.getText().toString();
			tmp+="/"+text;
			int tmpLength=tmp.length();
			if (tmpLength>=400) {
				tmp=tmp.substring(tmpLength-400);
			}
			mTextLongReceived.setText(tmp);
		}
    
    private class HM10BroadcastReceiver extends BroadcastReceiver {
		//YeelightCallBack.WRITE_SUCCESS);
		//YeelightCallBack.READ_SUCCESS);		
		//YeelightCallBack.DEVICE_CONNECTED);		

	    public String bytesToString(byte[] bytes){
	    	  StringBuilder stringBuilder = new StringBuilder(
	                    bytes.length);
	            for (byte byteChar : bytes)
	                stringBuilder.append(String.format("%02X ", byteChar));
	            return stringBuilder.toString();
	    }
	    
       @Override
       public void onReceive(Context context, Intent intent) {
           if (intent.getAction().equals(TumakuBLE.DEVICE_CONNECTED)) {
		       if (Constant.DEBUG) Log.i("JMG","DEVICE_CONNECTED message received");
		       
        	   updateInfoText("Received connection event");
        	   mState=WSTATE_SEARCH_SERVICES;
        	   nextState();
        	   return;
           }
           if (intent.getAction().equals(TumakuBLE.DEVICE_DISCONNECTED)) {
		       if (Constant.DEBUG) Log.i("JMG","DEVICE_DISCONNECTED message received");
		       //This is an unexpected device disconnect situation generated by Android BLE stack
		       //Usually happens on the service discovery step :-(
		       //Try to reconnect
	    	   String fullReset=intent.getStringExtra(TumakuBLE.EXTRA_FULL_RESET);
	    	   if (fullReset!=null){
			       if (Constant.DEBUG) Log.i("JMG","DEVICE_DISCONNECTED message received with full reset flag");
	    		   Toast.makeText(mContext, "Unrecoverable BT error received. Launching full reset", Toast.LENGTH_SHORT).show();    
	        	   mState=WSTATE_CONNECT;
	       		   mTumakuBLE.resetTumakuBLE();
	       		   mTumakuBLE.setDeviceAddress(mDeviceAddress);
	       		   mTumakuBLE.setup();
	        	   nextState();
	        	   return;	    		   
	    	   } else {		       		       
			       if (mState!=WSTATE_CONNECT){
		    		   Toast.makeText(mContext, "Device disconnected unexpectedly. Reconnecting.", Toast.LENGTH_SHORT).show();    
		        	   mState=WSTATE_CONNECT;
		       		   mTumakuBLE.resetTumakuBLE();
		       		   mTumakuBLE.setDeviceAddress(mDeviceAddress);
		        	   nextState();
		        	   return;
			       }
	    	   }
           }
           if (intent.getAction().equals(TumakuBLE.SERVICES_DISCOVERED)) {
		       if (Constant.DEBUG) Log.i("JMG","SERVICES_DISCOVERED message received");
		       
        	   updateInfoText("Received services discovered event");
        	   mState=WSTATE_NOTIFY_KEY;
        	   nextState();
        	   return;
           }

           if (intent.getAction().equals(TumakuBLE.READ_SUCCESS)) {
		       if (Constant.DEBUG) Log.i("JMG","READ_SUCCESS message received");
		       String readValue= intent.getStringExtra(TumakuBLE.EXTRA_VALUE);
		       byte [] readByteArrayValue= intent.getByteArrayExtra(TumakuBLE.EXTRA_VALUE_BYTE_ARRAY);
		
		       if (readValue==null) updateInfoText("Received Read Success Event but no value in Intent"  );
		       else {
		    	   updateInfoText("Received Read Success Event: " + readValue);
		       }
		       if (readValue==null) readValue="null";

        	   if (mState==WSTATE_READ_KEY) {
        		   if (readByteArrayValue!=null) displayText(readValue);
        		   mState=WSTATE_DUMMY;
        		   nextState();
        		   return;
        	   }
        	   return;
           }

           if (intent.getAction().equals(TumakuBLE.WRITE_SUCCESS)) {
		       if (Constant.DEBUG) Log.i("JMG","WRITE_SUCCESS message received");
        	   updateInfoText("Received Write Success Event");
        	   if (mState==WSTATE_WRITE_KEY) {
        		   mState=WSTATE_DUMMY;
        		   nextState();
        		   return;
        	   }    
               return;
           }                
           
           if (intent.getAction().equals(TumakuBLE.NOTIFICATION)) {
		       String notificationValue= intent.getStringExtra(TumakuBLE.EXTRA_VALUE);
		       String characteristicUUID= intent.getStringExtra(TumakuBLE.EXTRA_CHARACTERISTIC);
			   byte [] notificationValueByteArray =  intent.getByteArrayExtra(TumakuBLE.EXTRA_VALUE_BYTE_ARRAY);
		       if (notificationValue==null) notificationValue="NULL";
		       if (characteristicUUID==null) characteristicUUID="MISSING";
 		       if (Constant.DEBUG) {
 		    	   Log.i("JMG","NOTIFICATION message received");
 		    	   Log.i("JMG", "Characteristic: "+ characteristicUUID);
 		    	   Log.i("JMG","Value: " + notificationValue);
 		       }
		       updateNotificationText("Received Notification Event: Value: " + notificationValue +
		    	     " -  Characteristic UUID: " + characteristicUUID);
		       if (!notificationValue.equalsIgnoreCase("null")) {
		    	   if (characteristicUUID.equalsIgnoreCase(TumakuBLE.SENSORTAG_KEY_DATA)) {
		 		       if (Constant.DEBUG) Log.i("JMG","NOTIFICATION of Key Service");
		 		       if (notificationValueByteArray==null) {
		 		    	  if (Constant.DEBUG) Log.i("JMG","No notificationValueByteArray received. Discard notification");
		 		    	  return;
		 		       }
		 		       String tmpString="";
		 		       for (int i=0; i<notificationValueByteArray.length; i++) tmpString+=(char)notificationValueByteArray[i];
		 		       displayText(tmpString);
		 		       updateLongText(tmpString);
		    	   }
		       }
         	   return;
           }  
 
           if (intent.getAction().equals(TumakuBLE.WRITE_DESCRIPTOR_SUCCESS)) {
		       if (Constant.DEBUG) Log.i("JMG","WRITE_DESCRIPTOR_SUCCESS message received");
        	   updateInfoText("Received Write Descriptor Success Event");
        	   if (mState==WSTATE_NOTIFY_KEY) {
        		   mState=WSTATE_READ_KEY;
        		   nextState();
        	   }
        	   return;
           }     
 
   
       }
       
    }
}
