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

import java.util.List;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class SensorTagActivity extends Activity {
	
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
	
	private final static int STATE_CONNECT=0;
	private final static int STATE_SEARCH_SERVICES=1;
	private final static int STATE_READ_KEY=2;// Not used/supported -Skip this state
	private final static int STATE_NOTIFY_KEY=3;
	private final static int STATE_WRITE_ENABLE_HUMIDITY=4;
	private final static int STATE_READ_HUMIDITY=5;
	private final static int STATE_NOTIFY_HUMIDITY=6;
	private final static int STATE_WRITE_ENABLE_IR_TEMPERATURE=7;
	private final static int STATE_READ_IR_TEMPERATURE=8;
	private final static int STATE_NOTIFY_IR_TEMPERATURE=9;
	private final static int STATE_DUMMY=10;
	private final static int STATE_READ=11;
	private final static int STATE_DISCONNECT=12;
			
	private RadioButton mRadioButton1;
	private RadioButton mRadioButton2;
	private TextView mTextTemperature;
	private TextView mTextHumidity;
	private TextView mTextInfo;
	private TextView mTextNotification;
	private Button mButtonRead;
	private Button mButtonReset;

	private Context mContext;
	private SensorTagBLEBroadcastReceiver mBroadcastReceiver;
	private String mDeviceAddress;
	private int mState=0;
	
	private TumakuBLE  mTumakuBLE=null;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sensortag);           
        mContext=this;
        mBroadcastReceiver= new SensorTagBLEBroadcastReceiver();
        mDeviceAddress=getIntent().getStringExtra(TumakuBLE.EXTRA_ADDRESS);
        if (mDeviceAddress==null) {
            if (Constant.DEBUG) Log.i("JMG","No device address received to start SensorTag Activity");
        	finish();
        }
		mTumakuBLE=((TumakuBLEApplication)getApplication()).getTumakuBLEInstance(this);
        mTumakuBLE.setDeviceAddress(mDeviceAddress);
        mRadioButton1 = (RadioButton) findViewById(R.id.radioButton1);
        mRadioButton1.setClickable(false);
        mRadioButton2 = (RadioButton) findViewById(R.id.radioButton2);
        mRadioButton2.setClickable(false);
        mTextTemperature = (TextView) findViewById(R.id.textTemperature);
        mTextHumidity = (TextView) findViewById(R.id.textHumidity);
        mTextInfo=(TextView) findViewById(R.id.textInfo);
        mTextNotification=(TextView) findViewById(R.id.textNotification);
        mButtonRead= (Button) findViewById(R.id.buttonRead);
        mButtonReset= (Button) findViewById(R.id.buttonReset);
        restoreValues();
        
        mButtonRead.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        	    if ((mState==STATE_DUMMY)||(mState==STATE_READ)) {
	        	    mState=STATE_READ;
	        	    nextState();  
        	    }
            }
        });

        mButtonReset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        	        mState=STATE_CONNECT;
           		    mTumakuBLE.resetTumakuBLE();
            		mTumakuBLE.setDeviceAddress(mDeviceAddress);
        		 	updateInfoText("Reset connection to device");
	        	    nextState();  
            }
        });
	
	}
	
	@Override
	public void onResume(){
		super.onResume();
		IntentFilter filter = new IntentFilter(TumakuBLE.WRITE_SUCCESS);
		filter.addAction(TumakuBLE.READ_SUCCESS);		
		filter.addAction(TumakuBLE.WRITE_DESCRIPTOR_SUCCESS);	
		filter.addAction(TumakuBLE.NOTIFICATION);		
		filter.addAction(TumakuBLE.DEVICE_CONNECTED);		
		filter.addAction(TumakuBLE.DEVICE_DISCONNECTED);		
		filter.addAction(TumakuBLE.SERVICES_DISCOVERED);		
		this.registerReceiver(mBroadcastReceiver, filter);
		if (mTumakuBLE.isConnected()){
			mState=STATE_NOTIFY_KEY;
			nextState();
		 	updateInfoText("Resume connection to device");
		} else {
			mState=STATE_CONNECT;
			nextState();
		 	updateInfoText("Start connection to device");
		}
		
	}
	
	@Override
	public void onStop(){
		super.onStop();
        this.unregisterReceiver(this.mBroadcastReceiver);
        try {
	   	       mTumakuBLE.enableNotifications(TumakuBLE.SENSORTAG_HUMIDITY_SERVICE,TumakuBLE.SENSORTAG_HUMIDITY_DATA,false);
	   	       mTumakuBLE.enableNotifications(TumakuBLE.SENSORTAG_KEY_SERVICE,TumakuBLE.SENSORTAG_KEY_DATA,false);
	   	       mTumakuBLE.enableNotifications(TumakuBLE.SENSORTAG_IR_TEMPERATURE_SERVICE,TumakuBLE.SENSORTAG_IR_TEMPERATURE_DATA,false);
		   	   mTumakuBLE.write(TumakuBLE.SENSORTAG_HUMIDITY_SERVICE,TumakuBLE.SENSORTAG_HUMIDITY_CONF, new byte[]{0});
		   	   mTumakuBLE.write(TumakuBLE.SENSORTAG_IR_TEMPERATURE_SERVICE,TumakuBLE.SENSORTAG_IR_TEMPERATURE_CONF, new byte[]{0});

        }catch (Exception exc) {
		       if (Constant.DEBUG) {
		    	   Log.i("JMG","Exception caught during SensorTag activity onStop()");       	
		    	   Log.i("JMG","Exception: " + exc.getMessage());       	
		       }
        }
        
	}
	
	
		
		protected void nextState(){
			switch(mState) {			   
			   case (STATE_CONNECT):
			       if (Constant.DEBUG) Log.i("JMG","State Connected");
			       mTumakuBLE.connect();
			       break;
			   case(STATE_SEARCH_SERVICES):
			       if (Constant.DEBUG) Log.i("JMG","State Search Services");
				   mTumakuBLE.discoverServices();
			       break;			   
			   case(STATE_READ):
			       if (Constant.DEBUG) Log.i("JMG","State Read ");
			   	   mTumakuBLE.read(TumakuBLE.SENSORTAG_IR_TEMPERATURE_SERVICE,TumakuBLE.SENSORTAG_IR_TEMPERATURE_DATA);
				   break;
			   case(STATE_READ_KEY):
			       if (Constant.DEBUG) Log.i("JMG","State Read Key");
			   	   mTumakuBLE.read(TumakuBLE.SENSORTAG_KEY_SERVICE,TumakuBLE.SENSORTAG_KEY_DATA);
				   break;
			   case(STATE_NOTIFY_KEY):
			       if (Constant.DEBUG) Log.i("JMG","State Notify Key");
		   	       mTumakuBLE.enableNotifications(TumakuBLE.SENSORTAG_KEY_SERVICE,TumakuBLE.SENSORTAG_KEY_DATA,true);
				   break;
			   case(STATE_WRITE_ENABLE_HUMIDITY):
			       if (Constant.DEBUG) Log.i("JMG","State Enable Humidity");
			   	   mTumakuBLE.write(TumakuBLE.SENSORTAG_HUMIDITY_SERVICE,TumakuBLE.SENSORTAG_HUMIDITY_CONF, new byte[]{1});
				   break;
			   case(STATE_READ_HUMIDITY):
			       if (Constant.DEBUG) Log.i("JMG","State Read Humidity");
			   	   mTumakuBLE.read(TumakuBLE.SENSORTAG_HUMIDITY_SERVICE,TumakuBLE.SENSORTAG_HUMIDITY_DATA);
				   break;
			   case(STATE_NOTIFY_HUMIDITY):
			       if (Constant.DEBUG) Log.i("JMG","State Notify Humidity");
		   	       mTumakuBLE.enableNotifications(TumakuBLE.SENSORTAG_HUMIDITY_SERVICE,TumakuBLE.SENSORTAG_HUMIDITY_DATA,true);
   				   break;				   
			   case(STATE_WRITE_ENABLE_IR_TEMPERATURE):
			       if (Constant.DEBUG) Log.i("JMG","State Enable IR Temperature");
			   	   mTumakuBLE.write(TumakuBLE.SENSORTAG_IR_TEMPERATURE_SERVICE,TumakuBLE.SENSORTAG_IR_TEMPERATURE_CONF, new byte[]{1});
				   break;
			   case(STATE_READ_IR_TEMPERATURE):
			       if (Constant.DEBUG) Log.i("JMG","State Read IR Temperature");
			   	   mTumakuBLE.read(TumakuBLE.SENSORTAG_IR_TEMPERATURE_SERVICE,TumakuBLE.SENSORTAG_IR_TEMPERATURE_DATA);
				   break;
			   case(STATE_NOTIFY_IR_TEMPERATURE):
			       if (Constant.DEBUG) Log.i("JMG","State Notify IR TEmperature");
		   	       mTumakuBLE.enableNotifications(TumakuBLE.SENSORTAG_IR_TEMPERATURE_SERVICE,TumakuBLE.SENSORTAG_IR_TEMPERATURE_DATA,true);
   				   break;				   
			   case(STATE_DISCONNECT):
			       if (Constant.DEBUG) Log.i("JMG","State Disconect");
			   	  // mTumakuBLE.disconnect();
				   
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

		protected void updateKeyValues(byte [] value) {
			try{
				switch (value[0]) {
				case (byte)0X00:
					mRadioButton1.setChecked(false);
				    mRadioButton2.setChecked(false);
				    break;
				case (byte)0X01:
					mRadioButton1.setChecked(true);
				    mRadioButton2.setChecked(false);
				    break;
				case (byte)0X02:
					mRadioButton1.setChecked(false);
				    mRadioButton2.setChecked(true);
				    break;
				case (byte)0X03:
					mRadioButton1.setChecked(true);
				    mRadioButton2.setChecked(true);
				    break;
				default:   
				      if (Constant.DEBUG) {
				    	  Log.i("JMG","Unsupported Key value requested: " + value[0]);
				      }
						
				}
				
				
			} catch (Exception exc) {
			      if (Constant.DEBUG) {
			    	  Log.i("JMG","Exception while updating Key values");
			    	  Log.i("JMG",exc.getMessage());
			      }
			
			}

		}

		protected void updateHumidityValues(byte [] value) {
			try{
				double temperatureValue=TumakuBLE.calcHumRel(TumakuBLE.shortUnsignedAtOffset(value, 0));
				double humidityValue=TumakuBLE.calcHumRel(TumakuBLE.shortUnsignedAtOffset(value, 2));		
				//mTextTemperature.setText(String.format("%.1f", temperatureValue));
				mTextHumidity.setText(String.format("%.1f", humidityValue));
				
			} catch (Exception exc) {
			      if (Constant.DEBUG) {
			    	  Log.i("JMG","Exception while updating Humidity values");
			    	  Log.i("JMG",exc.getMessage());
			      }
			
			}

		}	
		
		protected void updateIRTemperatureValues(byte [] value) {
			try{
				double ambientTemperatureValue=TumakuBLE.extractAmbientTemperature(TumakuBLE.shortUnsignedAtOffset(value, 2));
				double targetTemperatureValue=TumakuBLE.extractTargetTemperature(TumakuBLE.shortUnsignedAtOffset(value, 0), ambientTemperatureValue);		
				mTextTemperature.setText(String.format("%.1f",ambientTemperatureValue));
				
			} catch (Exception exc) {
			      if (Constant.DEBUG) {
			    	  Log.i("JMG","Exception while updating Humidity values");
			    	  Log.i("JMG",exc.getMessage());
			      }
			
			}

		}	
    private void restoreValues() {
    	mRadioButton1.setChecked(false);
    	mRadioButton2.setChecked(false);
    }
	   
        
    
    private class SensorTagBLEBroadcastReceiver extends BroadcastReceiver {
		//YeelightCallBack.WRITE_SUCCESS);
		//YeelightCallBack.READ_SUCCESS);		
		//YeelightCallBack.DEVICE_CONNECTED);		

       @Override
       public void onReceive(Context context, Intent intent) {
           if (intent.getAction().equals(TumakuBLE.DEVICE_CONNECTED)) {
		       if (Constant.DEBUG) Log.i("JMG","DEVICE_CONNECTED message received");
		       
        	   updateInfoText("Received connection event");
        	   mState=STATE_SEARCH_SERVICES;
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
	        	   mState=STATE_CONNECT;
	       		   mTumakuBLE.resetTumakuBLE();
	       		   mTumakuBLE.setDeviceAddress(mDeviceAddress);
	       		   mTumakuBLE.setup();
	        	   nextState();
	        	   return;	    		   
	    	   } else {		       		       
			       if (mState!=STATE_CONNECT){
		    		   Toast.makeText(mContext, "Device disconnected unexpectedly. Reconnecting.", Toast.LENGTH_SHORT).show();    
		        	   mState=STATE_CONNECT;
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
        	   mState=STATE_NOTIFY_KEY;
        	   nextState();
        	   return;
           }

           if (intent.getAction().equals(TumakuBLE.READ_SUCCESS)) {
		       if (Constant.DEBUG) Log.i("JMG","READ_SUCCESS message received");
		       String readValue= intent.getStringExtra(TumakuBLE.EXTRA_VALUE);
		       if (readValue==null) updateInfoText("Received Read Success Event but no value in Intent"  );
		       else {
		    	   updateInfoText("Received Read Success Event: " + readValue);
	    		   //Toast.makeText(mContext, "Received Read Success Event: " + readValue, Toast.LENGTH_SHORT).show();    
		       }
        	   if (mState==STATE_READ_KEY) {
        		   mState=STATE_NOTIFY_KEY;
        		   nextState();
        	   }
        	   if (mState==STATE_READ_HUMIDITY) {
        		   mState=STATE_NOTIFY_HUMIDITY;
        		   nextState();
        	   }
        	   if (mState==STATE_READ_IR_TEMPERATURE) {
        		   mState=STATE_NOTIFY_IR_TEMPERATURE;
        		   nextState();
        	   }
        	   return;
           }

           if (intent.getAction().equals(TumakuBLE.WRITE_SUCCESS)) {
		       if (Constant.DEBUG) Log.i("JMG","WRITE_SUCCESS message received");
        	   updateInfoText("Received Write Success Event");
        	   if (mState==STATE_WRITE_ENABLE_HUMIDITY) {
        		   mState=STATE_READ_HUMIDITY;
        		   nextState();
        	   }        	   
        	   if (mState==STATE_WRITE_ENABLE_IR_TEMPERATURE) {
        		   mState=STATE_READ_IR_TEMPERATURE;
        		   nextState();
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
		 		       updateKeyValues(notificationValueByteArray);
		    	   }
		    	   if (characteristicUUID.equalsIgnoreCase(TumakuBLE.SENSORTAG_HUMIDITY_DATA)) {
		 		       if (Constant.DEBUG) Log.i("JMG","NOTIFICATION of Humidity Service");
		 		       if (notificationValueByteArray==null) {
		 		    	  if (Constant.DEBUG) Log.i("JMG","No notificationValueByteArray received. Discard notification");
		 		    	  return;
		 		       }
		 		       updateHumidityValues(notificationValueByteArray);
		    	   }
		    	   if (characteristicUUID.equalsIgnoreCase(TumakuBLE.SENSORTAG_IR_TEMPERATURE_DATA)) {
		 		       if (Constant.DEBUG) Log.i("JMG","NOTIFICATION of IR Temperature Service");
		 		       if (notificationValueByteArray==null) {
		 		    	  if (Constant.DEBUG) Log.i("JMG","No notificationValueByteArray received. Discard notification");
		 		    	  return;
		 		       }
		 		       updateIRTemperatureValues(notificationValueByteArray);
		    	   }
		       }
         	   return;
           }  

           
           if (intent.getAction().equals(TumakuBLE.WRITE_DESCRIPTOR_SUCCESS)) {
		       if (Constant.DEBUG) Log.i("JMG","WRITE_DESCRIPTOR_SUCCESS message received");
        	   updateInfoText("Received Write Descriptor Success Event");
        	   if (mState==STATE_NOTIFY_KEY) {
        		   mState=STATE_WRITE_ENABLE_HUMIDITY;
        		   nextState();
        	   }
        	   if (mState==STATE_NOTIFY_HUMIDITY) {
        		   mState=STATE_WRITE_ENABLE_IR_TEMPERATURE;
        		   nextState();
        	   }
        	   if (mState==STATE_NOTIFY_IR_TEMPERATURE) {
        		   mState=STATE_DUMMY;
    		       if (Constant.DEBUG) Log.i("JMG","Sensor initialisation completed");
        		   nextState();
        	   }
        	   return;
           }     
           
   
       }
       
    }
}
