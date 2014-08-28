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

import android.os.Bundle;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;


public class DeviceActivity extends ListActivity 
	implements OnItemClickListener{

	private static final int RECONNECT_ITEM =1;

	private BLEDeviceBroadcastReceiver mBroadcastReceiver;
	private List <TumakuBLE.ServiceType> mServices=null;
	private ListView mListView;
	private TextView mHeaderText;
	private MySimpleArrayAdapter mAdapter=null;
	private Context mContext=null;
	private String mAddress;
	private String mDeviceName;
	private TumakuBLE mTumakuBLE;
	private int mState=STATE_DUMMY;
	
	private final static int STATE_DUMMY=0;
	private final static int STATE_CONNECT=1;
	private final static int STATE_CONNECTING=2;
	private final static int STATE_CONNECTED=3; 
	private final static int STATE_RETRIEVING_SERVICES=4;
	private final static int STATE_SERVICES_RETRIEVED=5; 	
	private final static int STATE_DISCONNECTING=6; 	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i("JMG","DeviceActivity triggered OnCreate()");
		mContext=this;
        setContentView(R.layout.device_list);           
		mBroadcastReceiver= new BLEDeviceBroadcastReceiver();
		mHeaderText= (TextView) findViewById(R.id.header);
		mServices=new ArrayList<TumakuBLE.ServiceType>();
		mTumakuBLE=((TumakuBLEApplication)getApplication()).getTumakuBLEInstance(this);

		mAddress=getIntent().getStringExtra(TumakuBLE.EXTRA_ADDRESS);
		if (mAddress==null) {
			// return to main activity if no device address received
	    	if (Constant.DEBUG) Log.i("JMG", "No device address data defined in intent");
    	    Toast.makeText(this, "No  device address defined in intent", Toast.LENGTH_SHORT).show();    	    	
			finish();
		}		
		
		mDeviceName=getIntent().getStringExtra(TumakuBLE.EXTRA_NAME);
		if (mDeviceName==null) {
			// return to main activity if no device address received
	    	if (Constant.DEBUG) Log.i("JMG", "No device name data defined in intent");
    	    Toast.makeText(this, "No  device name defined in intent", Toast.LENGTH_SHORT).show();    	    	
			finish();
		}		

		
    	if (Constant.DEBUG) {
    		Log.i("JMG", "Device address: "+ mAddress);
    		Log.i("JMG", "Device name: "+ mDeviceName);
    	}

		mHeaderText.setText("Device Name: "+ mDeviceName + " - Addresss: " + mAddress);
		mState=STATE_DUMMY;
		mTumakuBLE.setDeviceAddress(mAddress);
		
        mListView = getListView();
        mAdapter=new MySimpleArrayAdapter(this, mServices);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);    		
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		String menuTitle= getResources().getString(R.string.reconnect);		
    	menu.add(0,RECONNECT_ITEM,0,menuTitle);
    	return true;
    }

	@Override
    public boolean onOptionsItemSelected (MenuItem item){
    	switch (item.getItemId()){
    		case RECONNECT_ITEM:
    	        if (mTumakuBLE.isConnected()){
    	    	    if (Constant.DEBUG) Log.i("JMG", "Disconnecting GATT Reconnect DeviceActivity");
    	        	TumakuBLE.disconnect();
    	        }
	    	    if (Constant.DEBUG) Log.i("JMG", "ReStart Connection Reconnect DeviceActivity");
    	        restartConnection(false);
		
    			break;
    	}
    	return true;
    }    
 	
	public void restartConnection(boolean fullResetFlag){
		mState=STATE_CONNECT;
        TumakuBLE.resetTumakuBLE();
		mTumakuBLE.setDeviceAddress(mAddress);
		mAdapter.clear();
	    if (Constant.DEBUG) Log.i("JMG", "Reset mTumakuBLE restartConnection() DeviceActivity");
	    if (fullResetFlag) {
	    	
	    }
	    
		IntentFilter filter = new IntentFilter(TumakuBLE.DEVICE_CONNECTED);	
		filter.addAction(TumakuBLE.DEVICE_DISCONNECTED);	
		filter.addAction(TumakuBLE.SERVICES_DISCOVERED);	
		this.registerReceiver(mBroadcastReceiver, filter);
		mTumakuBLE.connect();	
		mState=STATE_CONNECTING;
		updateTitle();
	}
	
	public void onResume(){
		super.onResume();
	    if (Constant.DEBUG) Log.i("JMG", "ReStart Connection onResume DeviceActivity");		
		restartConnection(false);
	}
	
	@Override
	public void onPause(){
		// The application Disconnects onPause 
		// but the TumakuBLE service tree is not cleared until next connect() call
		super.onPause();
		mState=STATE_DISCONNECTING;
        this.unregisterReceiver(this.mBroadcastReceiver);
        if (mTumakuBLE.isConnected()){
    	    if (Constant.DEBUG) Log.i("JMG", "Disconnecting GATT onPause DeviceActivity");
        	TumakuBLE.disconnect();
        }
	}
	
    @Override
    public void onItemClick(AdapterView<?> adapter, View view,
                int position, long id) {     	
	    if (Constant.DEBUG) Log.i("JMG", "Selected service " + mServices.get(position).getService().getUuid().toString());
    	Intent intentActivity= new Intent(mContext, ServiceActivity.class);
		intentActivity.putExtra(TumakuBLE.EXTRA_SERVICE, mServices.get(position).getService().getUuid().toString());
    	mContext.startActivity(intentActivity);

    }
	
    //called from BroadacastReceiver (maybe outside UI thread, so this mechanism must be used
    protected void updateServiceList(){
    	this.runOnUiThread(new Runnable() {
	        @Override
	        public void run() {
	        	mAdapter.clear();
			    mAdapter.addAll(mTumakuBLE.getServices());				       
			    if (Constant.DEBUG) Log.i("JMG", "Added new services ");
	        }
		}
       );
    }
    
    //called from BroadacastReceiver (maybe outside UI thread, so this mechanism must be used
    protected void updateTitle(){
    	this.runOnUiThread(new Runnable() {
	        @Override
	        public void run() {
	        	String stateString="";
	        	switch (mState){
	        	case(STATE_DUMMY):
	        		stateString="Dummy";
	        		break;
	        	case(STATE_CONNECT):
	        		stateString="Connect";
	        		break;	        	
	        	case(STATE_CONNECTING):
	        		stateString="Connecting";
	        		break;
	        	case(STATE_CONNECTED):
	        		stateString="Connected";
	        		break;
	        	case(STATE_RETRIEVING_SERVICES):
	        		stateString="Retrieving Services";
	        		break;
	        	case(STATE_SERVICES_RETRIEVED):
	        		stateString="Services discovered";
	        		break;
	        	case(STATE_DISCONNECTING):
	        		stateString="Disconnecting";
	        		break;
	        	}
	        	mHeaderText.setText("Addr: " + mAddress + "  - Name: "+ mDeviceName+ "  - State: " + stateString);	        
	        }
		}
       );
    }
    
    public class MySimpleArrayAdapter extends ArrayAdapter<TumakuBLE.ServiceType> {
  	  private final Context context;
  	  public MySimpleArrayAdapter(Context context, List <TumakuBLE.ServiceType> serviceList) {
  	    super(context, R.layout.device_list_item, serviceList);
  	    this.context = context;
  	  }

  	  @Override
  	  public View getView(int position, View convertView, ViewGroup parent) {
  	    LayoutInflater inflater = (LayoutInflater) context
  	        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
  	    View rowView = inflater.inflate(R.layout.device_list_item, parent, false);
  	    TextView serviceText = (TextView) rowView.findViewById(R.id.deviceName);
  	    TextView characteristicsText = (TextView) rowView.findViewById(R.id.deviceAddress);
  	    String serviceUUID=mServices.get(position).getService().getUuid().toString();
  	    serviceText.setText(serviceUUID);
  	    if (serviceUUID.equalsIgnoreCase(TumakuBLE.YEELIGHT_SERVICE)) rowView.setBackgroundColor(Color.GREEN);
  	    if (serviceUUID.equalsIgnoreCase(TumakuBLE.SENSORTAG_HUMIDITY_SERVICE)) rowView.setBackgroundColor(Color.RED);
  	    if (serviceUUID.equalsIgnoreCase(TumakuBLE.SENSORTAG_KEY_SERVICE)) rowView.setBackgroundColor(Color.YELLOW);
  	    if (serviceUUID.equalsIgnoreCase(TumakuBLE.SENSORTAG_IR_TEMPERATURE_SERVICE)) rowView.setBackgroundColor(Color.RED);
  	    if (serviceUUID.equalsIgnoreCase(TumakuBLE.TETHERCELL_SERVICE)) rowView.setBackgroundColor(Color.BLUE);
  	    if (serviceUUID.equalsIgnoreCase(TumakuBLE.BLEDUINO_UART_SERVICE)) rowView.setBackgroundColor(Color.BLUE);

  	    serviceText.setTypeface(null, Typeface.BOLD);
  	  	String characteristicsString ="";
  	  	for (BluetoothGattCharacteristic characteristicInList : mServices.get(position).getCharacteristics()){
  	  		characteristicsString+=characteristicInList.getUuid().toString()+"\n";
  	  	}
  	    characteristicsText.setText(characteristicsString);
  	    return rowView;
  	  }
  	} 

 
    private class BLEDeviceBroadcastReceiver extends BroadcastReceiver {
	
       @Override
       public void onReceive(Context context, Intent intent) {
           if (intent.getAction().equals(TumakuBLE.DEVICE_CONNECTED)) {
		       if (Constant.DEBUG) Log.i("JMG","DEVICE_CONNECTED message received DeviceActivity");
		       //once connected to device, discover services
		       mState=STATE_CONNECTED;
		       mTumakuBLE.discoverServices();
		       mState= STATE_RETRIEVING_SERVICES;
			   updateTitle();
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
				   restartConnection(true);
	        	   return;	    		   
	    	   } else {		       		       
			       if (mState!=STATE_CONNECTING){
					   Toast.makeText(mContext, "Device disconnected unexpectedly. Reconnecting.", Toast.LENGTH_SHORT).show();    	    		    
					   restartConnection(false);
		        	   return;
			       }
		       }
           }
           if (intent.getAction().equals(TumakuBLE.SERVICES_DISCOVERED)) {
		       if (Constant.DEBUG) Log.i("JMG","SERVICES_DISCOVERED message received  DeviceActivity");
		       mState= STATE_SERVICES_RETRIEVED;
		       updateServiceList();
			   updateTitle();
		       return;
           }
        }
       
    }
    
    
}
