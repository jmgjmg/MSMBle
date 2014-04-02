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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class TethercellActivity extends Activity {
	
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
	private final static int WSTATE_WRITE_AUTH_PIN=2;
	private final static int WSTATE_READ_VOLTAGE=3;
	private final static int WSTATE_READ_STATE=4;
	private final static int WSTATE_READ_PERIOD=5;
	private final static int WSTATE_READ_UTC=6;
	private final static int WSTATE_READ_TIMER_INDEX=7;
	private final static int WSTATE_READ_TIMER=8;
	private final static int WSTATE_DUMMY=9;
	private final static int WSTATE_TOGGLE_STATE=10;
	private final static int WSTATE_WRITE_STATE=11;
	private final static int WSTATE_WRITE_PERIOD=12;
	private final static int WSTATE_WRITE_UTC=13;
	private final static int WSTATE_WRITE_TIMER_INDEX=14;
	private final static int WSTATE_WRITE_TIMER=15;
	
	private final static byte[] TETHERCELL_PIN ={0x00,0x00,0x00,0x00};

	private TextView mTextVoltage;
	private TextView mTextState;
	private CheckBox mCheckBoxState;
	private TextView mTextPeriod;
	private TextView mTextUtc;
	private TextView mTextTimerIndex;
	private TextView mTextTimer;
	private TextView mTextInfo;
	private TextView mTextNotification;
	private Button mButtonRead;
	private Button mButtonReset;
	private Spinner mSpinnerStart;
	private Spinner mSpinnerDuration;
	private Spinner mSpinnerRepeat;
	private Button mButtonSetTimer;

	private Context mContext;
	private TethercellBLEBroadcastReceiver mBroadcastReceiver;
	private String mDeviceAddress;
	private int mState=0;
	
	private TumakuBLE  mTumakuBLE=null;
	
	
    byte [] mTimerValue= new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,(byte)0x00,0x00,0x00,0x00,0x00};
    // byte [] timerValue= new byte[]{0x20,0x00,0x00,0x00,0x40,0x00,0x00,0x00,(byte)0x80,0x00,0x00,0x00,0x01};
    // BYTES 0-3 uint32 Start time (less significant byte first) 0x20 0x00 0x00 0x00   -> switch on at 32 seconds UTC time
    // BYTES 4-7 uint32 On Time (less significant byte first) 0x40 0x00 0x00 0x00   -> keep on for 64 seconds
    // BYTES 8-11 uint32 Repeat Timer (less significant byte first) 0x80 0x00 0x00 0x00   -> repeat after 128 seconds
    // BYTE 12 uint8 On Flag 0x00  -> this timer is currently forcing the switch to be on 

    byte mTimerIndex= (byte)0x02;
    boolean mSwitchState=false;
    
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tethercell);           
        mContext=this;
        mBroadcastReceiver= new TethercellBLEBroadcastReceiver();
        mDeviceAddress=getIntent().getStringExtra(TumakuBLE.EXTRA_ADDRESS);
        if (mDeviceAddress==null) {
            if (Constant.DEBUG) Log.i("JMG","No device address received to start SensorTag Activity");
        	finish();
        }
		mTumakuBLE=((TumakuBLEApplication)getApplication()).getTumakuBLEInstance(this);
        mTumakuBLE.setDeviceAddress(mDeviceAddress);
        mTextVoltage = (TextView) findViewById(R.id.textVoltage);
        mTextState = (TextView) findViewById(R.id.textState);
        mCheckBoxState = (CheckBox) findViewById(R.id.checkBoxState);
        mCheckBoxState.setChecked(false);
        mTextPeriod = (TextView) findViewById(R.id.textPeriod);
        mTextUtc = (TextView) findViewById(R.id.textUtc);
        mTextTimerIndex = (TextView) findViewById(R.id.textTimerIndex);
        mTextTimer = (TextView) findViewById(R.id.textTimer);
        mTextInfo=(TextView) findViewById(R.id.textInfo);
        mTextNotification=(TextView) findViewById(R.id.textNotification);
        mButtonRead= (Button) findViewById(R.id.buttonRead);
        mButtonReset= (Button) findViewById(R.id.buttonReset);
        mSpinnerStart= (Spinner) findViewById(R.id.spinnerStart);
        initialiseSpinner(mSpinnerStart);
        mSpinnerDuration= (Spinner) findViewById(R.id.spinnerDuration);
        initialiseSpinner(mSpinnerDuration);
        mSpinnerRepeat= (Spinner) findViewById(R.id.spinnerRepeat);
        initialiseSpinner(mSpinnerRepeat);
        mButtonSetTimer= (Button) findViewById(R.id.buttonSetTimer);

        mCheckBoxState.setOnClickListener(new OnClickListener() {     		 
        		      @Override
        		      public void onClick(View v) {
          				if (mState==WSTATE_DUMMY) {
        	        	    mState=WSTATE_TOGGLE_STATE;
        	        	    nextState();  
            			} else mCheckBoxState.setChecked(false);
        		      }
        		    });

        mButtonSetTimer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        	    if ((mState==WSTATE_DUMMY)) {
        	    	setTimerArray();
    	    		mSpinnerStart.setSelection(0);
    	    		mSpinnerDuration.setSelection(0);
    	    		mSpinnerRepeat.setSelection(0);
	        	    mState=WSTATE_WRITE_STATE;
	        	    nextState();  
        	    }
            }
        });
        
        
        mButtonRead.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        	    if ((mState==WSTATE_DUMMY)) {
	        	    mState=WSTATE_READ_VOLTAGE;
	        	    nextState();  
        	    }
            }
        });

        mButtonReset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        	        mState=WSTATE_CONNECT;
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
		filter.addAction(TumakuBLE.DEVICE_CONNECTED);		
		filter.addAction(TumakuBLE.DEVICE_DISCONNECTED);		
		filter.addAction(TumakuBLE.SERVICES_DISCOVERED);		
		this.registerReceiver(mBroadcastReceiver, filter);
		if (mTumakuBLE.isConnected()){
			mState=WSTATE_WRITE_AUTH_PIN;
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
	
	private void initialiseSpinner(Spinner spinner) {
    	List<Integer> list = new ArrayList<Integer>();   	
    	list.add(0);
    	list.add(1);
    	list.add(2);
    	list.add(3);
    	list.add(4);
    	list.add(5);
    	ArrayAdapter<Integer> dataAdapter = new ArrayAdapter<Integer>(this,
    		android.R.layout.simple_spinner_item, list);
    	dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	spinner.setAdapter(dataAdapter);		
	}
			
	private void setTimerArray(){
		mSwitchState=false;
		int startSeconds= (Integer)mSpinnerStart.getSelectedItem() *60;
		int durationSeconds=(Integer)mSpinnerDuration.getSelectedItem() *60;
		int repeatSeconds=(Integer)mSpinnerRepeat.getSelectedItem() *60;

    	if (startSeconds==0) { //battery should start on
    		startSeconds=1;
    		mSwitchState=true;
    	}
    	
		if (repeatSeconds!=0){ //if repetition is defined, the period is computed as the repeat value plus the duration value
			repeatSeconds+=durationSeconds;
		}


    	if (durationSeconds==0) { //in any case, a duration of 0 resets the timer and switches off
            Toast.makeText(mContext, "Timer cleared", Toast.LENGTH_SHORT).show(); 
            startSeconds=0;
            repeatSeconds=0;
            mSwitchState=false;
    	}
		
		mTimerValue[0]=(byte) (startSeconds%256);
		mTimerValue[1]=(byte) (startSeconds/256);
		mTimerValue[4]=(byte) (durationSeconds%256);
		mTimerValue[5]=(byte) (durationSeconds/256);
		mTimerValue[8]=(byte) (repeatSeconds%256);
		mTimerValue[9]=(byte) (repeatSeconds/256);
		mTimerValue[12]=(byte)0;		// always we indicate that we start with the switch off 
										// not sure for the case when (mSwitchState==true)
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
			   case(WSTATE_WRITE_AUTH_PIN):
			       if (Constant.DEBUG) Log.i("JMG","State Write Auth Pin");
		   	       mTumakuBLE.write(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_AUTH_PIN, TETHERCELL_PIN);
				   break;
			   case(WSTATE_READ_VOLTAGE):
			       if (Constant.DEBUG) Log.i("JMG","State Read Voltage");
		   	   	   mTumakuBLE.read(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_VOLTAGE);
				   break;
			   case(WSTATE_READ_STATE):
			       if (Constant.DEBUG) Log.i("JMG","State Read State");
			   	   mTumakuBLE.read(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_STATE);
				   break;
			   case(WSTATE_READ_PERIOD):
			       if (Constant.DEBUG) Log.i("JMG","State Read Period");
			   	   mTumakuBLE.read(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_PERIOD);
				   break;
			   case(WSTATE_READ_UTC):
			       if (Constant.DEBUG) Log.i("JMG","State Read UTC");
			   	   mTumakuBLE.read(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_UTC);
				   break;
			   case(WSTATE_READ_TIMER_INDEX):
			       if (Constant.DEBUG) Log.i("JMG","State Read State");
			   	   mTumakuBLE.read(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_TIMER_ARRAY_INDEX);
				   break;
			   case(WSTATE_READ_TIMER):
			       if (Constant.DEBUG) Log.i("JMG","State Read State");
			   	   mTumakuBLE.read(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_TIMER_ARRAY);
				   break;
			   case(WSTATE_TOGGLE_STATE):
				   byte newState=0x00;
				   if (mCheckBoxState.isChecked()) {
					   newState=0x01;
				   }
			       if (Constant.DEBUG) Log.i("JMG","State Toggle State" + Byte.toString(newState));
		   	       mTumakuBLE.write(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_STATE, new byte[]{newState});
				   break;

			   case(WSTATE_WRITE_STATE):
				   byte newByteState=0x00;
				   if (mSwitchState) {
					   newByteState=0x01;
				   }
			       if (Constant.DEBUG) Log.i("JMG","State Write State" + Byte.toString(newByteState));
		   	       mTumakuBLE.write(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_STATE, new byte[]{newByteState});
				   break;

			   case(WSTATE_WRITE_PERIOD):
			       if (Constant.DEBUG) Log.i("JMG","State Write Period");
		   	       mTumakuBLE.write(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_PERIOD, new byte[]{0x20,0x03});  //Set advertising interval to 0,5 seconds
		   	       //mTumakuBLE.write(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_PERIOD, new byte[]{0x40,0x06}); //Set advertising interval to 1 second
				   break;

			   case(WSTATE_WRITE_UTC):
			       if (Constant.DEBUG) Log.i("JMG","State Write UTC");
		   	       mTumakuBLE.write(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_UTC, new byte[]{0x05,0x00,0x00,0x00}); //5 seconds
				   break;

			   case(WSTATE_WRITE_TIMER_INDEX):
			       if (Constant.DEBUG) Log.i("JMG","State Write Timer Index");
		   	       mTumakuBLE.write(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_TIMER_ARRAY_INDEX, new byte[]{mTimerIndex}); // timer index 2 (third one since index starts at 0)
				   break;

			   case(WSTATE_WRITE_TIMER):
			       if (Constant.DEBUG) Log.i("JMG","State Write Timer");
		   	       mTumakuBLE.write(TumakuBLE.TETHERCELL_SERVICE,TumakuBLE.TETHERCELL_TIMER_ARRAY, mTimerValue); // timer index 2 (third one since index starts at 0)
				   break;

			   default:
				   
			}			
			
		}
		

		public static short getShort(byte b1, byte b2) {
	          return (short) ((b1 << 8) | (b2 & 0xFF));
		}
		
		protected void displayVoltage(byte[] value) {
			if (value.length<2) return;
			mTextVoltage.setText(Integer.toString(getShort(value[1], value[0])));
		}

		protected void displayState(byte[] value) {
			mTextState.setText(Integer.toString(getShort((byte)0x00, value[0])));
		}

		protected void displayPeriod(byte[] value) {
			if (value.length<2) return;
			short period=getShort(value[1], value[0]);
			long miliseconds= (625L * period)/1000L;
			mTextPeriod.setText(Long.toString(miliseconds));
		}

		protected void updateInfoText(String text) {
			mTextInfo.setText(text);
		}

		protected void updateNotificationText(String text) {
			mTextNotification.setText(text);
		}

        
    
    private class TethercellBLEBroadcastReceiver extends BroadcastReceiver {
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
        	   mState=WSTATE_WRITE_AUTH_PIN;
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

        	   if (mState==WSTATE_READ_VOLTAGE) {
        		   if (readByteArrayValue!=null) displayVoltage(readByteArrayValue);
        		   mState=WSTATE_READ_STATE;
        		   nextState();
        		   return;
        	   }
        	   if (mState==WSTATE_READ_STATE) {
        		   if (readByteArrayValue!=null) {
        			   displayState(readByteArrayValue);
            		   if (readByteArrayValue[0]==0x01) mCheckBoxState.setChecked(true); 
            		   else mCheckBoxState.setChecked(false); 
        		   } else {
        			   mCheckBoxState.setChecked(false);   
        			   mTextState.setText("");
        		   }       			   
        		   mState=WSTATE_READ_PERIOD;
        		   nextState();
        		   return;
        	   }
        	   if (mState==WSTATE_READ_PERIOD) {
        		   if (readByteArrayValue!=null) displayPeriod(readByteArrayValue);
        		   mState=WSTATE_READ_UTC;
        		   nextState();
        		   return;
        	   }
        	   if (mState==WSTATE_READ_UTC) {
        		   mTextUtc.setText(readValue);
        		   mState=WSTATE_READ_TIMER_INDEX;
        		   nextState();
        		   return;
        	   }
        	   if (mState==WSTATE_READ_TIMER_INDEX) {
        		   mTextTimerIndex.setText(readValue);
        		   mState=WSTATE_READ_TIMER;
        		   nextState();
        		   return;
        	   }
        	   if (mState==WSTATE_READ_TIMER) {
        		   mTextTimer.setText(readValue);
        		   mState=WSTATE_DUMMY;
        		   nextState();
        		   return;
        	   }
        	   return;
           }

           if (intent.getAction().equals(TumakuBLE.WRITE_SUCCESS)) {
		       if (Constant.DEBUG) Log.i("JMG","WRITE_SUCCESS message received");
        	   updateInfoText("Received Write Success Event");
        	   if (mState==WSTATE_WRITE_AUTH_PIN) {
        		   mState=WSTATE_READ_VOLTAGE;
        		   nextState();
        		   return;
        	   }    
	           if (mState==WSTATE_TOGGLE_STATE) {
        		   mState=WSTATE_READ_VOLTAGE;
        		   nextState();
        		   return;
	           }        	   
	           if (mState==WSTATE_WRITE_STATE) {
        		   mState=WSTATE_WRITE_PERIOD;
        		   nextState();
        		   return;
	           }        	   
	           if (mState==WSTATE_WRITE_PERIOD) {
        		   mState=WSTATE_WRITE_UTC;
        		   nextState();
        		   return;
	           }        	   
	           if (mState==WSTATE_WRITE_UTC) {
        		   mState=WSTATE_WRITE_TIMER_INDEX;
        		   nextState();
        		   return;
	           }        	   
	           if (mState==WSTATE_WRITE_TIMER_INDEX) {
        		   mState=WSTATE_WRITE_TIMER;
        		   nextState();
        		   return;
	           }        	   
	           if (mState==WSTATE_WRITE_TIMER) {
        		   mState=WSTATE_READ_VOLTAGE;
        		   nextState();
        		   return;
	           }        	   
               return;
           }                
   
       }
       
    }
}
