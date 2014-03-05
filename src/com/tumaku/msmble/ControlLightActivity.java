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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class ControlLightActivity extends Activity implements OnSeekBarChangeListener {
	
	private final static int STATE2_CONNECT=0;
	private final static int STATE2_SEARCH_SERVICES=1;
	private final static int STATE2_READ=2;
	private final static int STATE2_WRITE=3; 
	private final static int STATE2_DISCONNECT=4;
			
	private SeekBar mSeekBarRed;
	private SeekBar mSeekBarGreen;
	private SeekBar mSeekBarBlue;
	private SeekBar mSeekBarIntensity;
	private TextView mTextRed;
	private TextView mTextGreen;
	private TextView mTextBlue;
	private TextView mTextIntensity;
	private TextView mTextInfo;
	private Button mButtonRead;
	private Button mButtonReset;

	private TextView mTextColourSample;
	private Context mContext;
	private YeelightBLEBroadcastReceiver mBroadcastReceiver;
	private String mDeviceAddress;
	private int mState2=0;
	
	private TumakuBLE  mTumakuBLE=null;
	private String  mControlString="128,128,128,100,,,";

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.light_control);           
        mContext=this;
        mBroadcastReceiver= new YeelightBLEBroadcastReceiver();
        mDeviceAddress=getIntent().getStringExtra(TumakuBLE.EXTRA_ADDRESS);
        if (mDeviceAddress==null) {
            if (Constant.DEBUG) Log.i("JMG","No device address received to start ControlLight Activity");
        	finish();
        }
        //mTumakuBLE= TumakuBLE.getInstance(mContext);
		mTumakuBLE=((TumakuBLEApplication)getApplication()).getTumakuBLEInstance(this);
        mTumakuBLE.setDeviceAddress(mDeviceAddress);
        mSeekBarRed = (SeekBar) findViewById(R.id.seekBarRed);
        mSeekBarRed.setOnSeekBarChangeListener(this);
        mSeekBarRed.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.OVERLAY));
        mSeekBarGreen = (SeekBar) findViewById(R.id.seekBarGreen);
        mSeekBarGreen.setOnSeekBarChangeListener(this);
        mSeekBarGreen.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(Color.GREEN, PorterDuff.Mode.OVERLAY));
        mSeekBarBlue = (SeekBar) findViewById(R.id.seekBarBlue);
        mSeekBarBlue.setOnSeekBarChangeListener(this);
        mSeekBarBlue.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(Color.BLUE, PorterDuff.Mode.OVERLAY));
        mSeekBarIntensity = (SeekBar) findViewById(R.id.seekBarIntensity);
        mSeekBarIntensity.setOnSeekBarChangeListener(this);
        mSeekBarIntensity.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY));
        mTextRed = (TextView) findViewById(R.id.textRed);
        mTextGreen = (TextView) findViewById(R.id.textGreen);
        mTextBlue = (TextView) findViewById(R.id.textBlue);
        mTextIntensity = (TextView) findViewById(R.id.textIntensity);
        mTextColourSample = (TextView) findViewById(R.id.colourSample);
        mTextRed.setTypeface(null, Typeface.BOLD);
        mTextGreen.setTypeface(null, Typeface.BOLD);
        mTextBlue.setTypeface(null, Typeface.BOLD);
        mTextIntensity.setTypeface(null, Typeface.BOLD);
        mTextInfo=(TextView) findViewById(R.id.textInfo);
        mButtonRead= (Button) findViewById(R.id.buttonRead);
        mButtonReset= (Button) findViewById(R.id.buttonReset);
        restoreValues();
        
        mButtonRead.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        	    if (mState2>=STATE2_READ) {
	        	    mState2=STATE2_READ;
	        	    nextState2();  
        	    }
            }
        });

        mButtonReset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        	        mState2=STATE2_CONNECT;
           		    mTumakuBLE.resetTumakuBLE();
            		mTumakuBLE.setDeviceAddress(mDeviceAddress);
        		 	updateInfoText("Reset connection to device");
	        	    nextState2();  
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
			mState2=STATE2_WRITE;
			nextState2();
		 	updateInfoText("Resume connection to device");
		} else {
			mState2=STATE2_CONNECT;
			nextState2();
		 	updateInfoText("Start connection to device");
		}
		
	}
	
	@Override
	public void onStop(){
		super.onStop();
		storeValues();
        this.unregisterReceiver(this.mBroadcastReceiver);
	}
	
	@Override
	public void onDestroy(){
		super.onDestroy();
        //mTumakuBLE.disconnect();
	}
	
    @Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
	   updateDisplay();
	   if (mState2>=STATE2_READ) {
		   mState2=STATE2_WRITE;
		   nextState2();
	   }
	}
	
	@Override
	public void onStartTrackingTouch(SeekBar arg0) {
		// TODO Auto-generated method stub
	}
	
	@Override
	public void onStopTrackingTouch(SeekBar arg0) {
		storeValues();
		updateDisplay();
 	   // mState2=STATE2_READ;
 	   // nextState2();
	}
	
		
		protected void nextState2(){
			switch(mState2) {			   
			   case (STATE2_CONNECT):
			       if (Constant.DEBUG) Log.i("JMG","State Connected");
			       mTumakuBLE.connect();
			       break;
			   case(STATE2_SEARCH_SERVICES):
			       if (Constant.DEBUG) Log.i("JMG","State Search Services");
				   mTumakuBLE.discoverServices();
			       break;			   
			   case(STATE2_READ):
			       if (Constant.DEBUG) Log.i("JMG","State Read");
			   	   mTumakuBLE.read(TumakuBLE.YEELIGHT_SERVICE,TumakuBLE.CHARACTERISTIC_CONTROL);
				   break;
			   case(STATE2_WRITE):
			       if (Constant.DEBUG) Log.i("JMG","State Write");
			   	   mTumakuBLE.write(TumakuBLE.YEELIGHT_SERVICE,TumakuBLE.CHARACTERISTIC_CONTROL,mControlString);
				   break;
			   case(STATE2_DISCONNECT):
			       if (Constant.DEBUG) Log.i("JMG","State Disconect");
			   	  // mTumakuBLE.disconnect();
				   
				   break;
			   default:
				   
			}			
			
		}
		
	
	protected void updateInfoText(String text) {
		mTextInfo.setText(text);
	}
	
	
    private void restoreValues() {
	    SharedPreferences settings = getSharedPreferences(getString(R.string.prefsFile), 0);        
	    int redValue= settings.getInt(getString(R.string.red),0);
	    int greenValue= settings.getInt(getString(R.string.green),0);
	    int blueValue= settings.getInt(getString(R.string.blue),0);
	    int intensityValue= settings.getInt(getString(R.string.intensity),0);
	    
	    mSeekBarRed.setProgress(redValue);
	    mSeekBarGreen.setProgress(greenValue);
	    mSeekBarBlue.setProgress(blueValue);
	    mSeekBarIntensity.setProgress(intensityValue);
	    updateDisplay();
    }
	
    private void storeValues(){
	    SharedPreferences settings = getSharedPreferences(getString(R.string.prefsFile), 0);
	    SharedPreferences.Editor editor = settings.edit();
	    editor.putInt(getString(R.string.red),mSeekBarRed.getProgress());
	    editor.putInt(getString(R.string.green),mSeekBarGreen.getProgress());
	    editor.putInt(getString(R.string.blue),mSeekBarBlue.getProgress());
	    editor.putInt(getString(R.string.intensity),mSeekBarIntensity.getProgress());
	    editor.commit();
    }
    
    
    private  void updateDisplay(){
	   	 mTextRed.setText(getString(R.string.red)+": "+ mSeekBarRed.getProgress());
		 mTextGreen.setText(getString(R.string.green)+": "+ mSeekBarGreen.getProgress());
		 mTextBlue.setText(getString(R.string.blue)+": "+ mSeekBarBlue.getProgress());
		 mTextIntensity.setText(getString(R.string.intensity)+": "+ mSeekBarIntensity.getProgress());
    	 mTextColourSample.setBackgroundColor(
    	    Color.rgb(mSeekBarRed.getProgress(),mSeekBarGreen.getProgress(),mSeekBarBlue.getProgress()));
    	 mControlString=threeDigits(mSeekBarRed.getProgress())+","+threeDigits(mSeekBarGreen.getProgress())+","+
    			 threeDigits(mSeekBarBlue.getProgress())+","+threeDigits(mSeekBarIntensity.getProgress())+","+",,";
    	 mControlString=generateControLString();
    }
    
    private String generateControLString() {
    	String result=""+mSeekBarRed.getProgress()+","+mSeekBarGreen.getProgress()+","+mSeekBarBlue.getProgress()+","
    			+mSeekBarIntensity.getProgress()+",";
        while (result.length()<18){result+=",";}
    	return result;
    }
   
    private String threeDigits(int value) {
    	if (value <0) value=-value;
    	if (value>255) value%=256;
    	if (value<10) return ("00"+value);
    	if (value<100) return ("0"+value);
    	return (""+ value);
    }
    
    
    private class YeelightBLEBroadcastReceiver extends BroadcastReceiver {
		//YeelightCallBack.WRITE_SUCCESS);
		//YeelightCallBack.READ_SUCCESS);		
		//YeelightCallBack.DEVICE_CONNECTED);		

       @Override
       public void onReceive(Context context, Intent intent) {
           if (intent.getAction().equals(TumakuBLE.DEVICE_CONNECTED)) {
		       if (Constant.DEBUG) Log.i("JMG","DEVICE_CONNECTED message received");
		       
        	   updateInfoText("Received connection event");
        	   mState2=STATE2_SEARCH_SERVICES;
        	   nextState2();
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
	    		   Toast.makeText(mContext, "Urecoverable BT error received. Launching full reset", Toast.LENGTH_SHORT).show();    
	        	   mState2=STATE2_CONNECT;
	       		   mTumakuBLE.resetTumakuBLE();
	       		   mTumakuBLE.setDeviceAddress(mDeviceAddress);
	       		   mTumakuBLE.setup();
	        	   nextState2();
	        	   return;	    		   
	    	   } else {		       		       
			       if (mState2!=STATE2_CONNECT){
		    		   Toast.makeText(mContext, "Device disconnected unexpectedly. Reconnecting.", Toast.LENGTH_SHORT).show();    
		        	   mState2=STATE2_CONNECT;
		       		   mTumakuBLE.resetTumakuBLE();
		       		   mTumakuBLE.setDeviceAddress(mDeviceAddress);
		        	   nextState2();
		        	   return;
			       }
	    	   }
           }
           if (intent.getAction().equals(TumakuBLE.SERVICES_DISCOVERED)) {
		       if (Constant.DEBUG) Log.i("JMG","SERVICES_DISCOVERED message received");
		       
        	   updateInfoText("Received services discovered event");
        	   mState2=STATE2_WRITE;
        	   nextState2();
        	   return;
           }

           if (intent.getAction().equals(TumakuBLE.READ_SUCCESS)) {
		       if (Constant.DEBUG) Log.i("JMG","READ_SUCCESS message received");
		       String readValue= intent.getStringExtra(TumakuBLE.EXTRA_VALUE);
		       if (readValue==null) updateInfoText("Received Read Success Event but no value in Intent"  );
		       else updateInfoText("Received Read Success Event: " + readValue);
        	   //mState2=STATE2_DISCONNECT;
        	   //nextState2();
        	   return;
           }

           if (intent.getAction().equals(TumakuBLE.WRITE_SUCCESS)) {
		       if (Constant.DEBUG) Log.i("JMG","WRITE_SUCCESS message received");
        	   updateInfoText("Received Write Success Event");
        	   //mState2=STATE2_READ;        	   
        	   //nextState2();
        	   return;
           }       
       }
       
    }
}
