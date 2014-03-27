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
import android.os.Handler;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.content.Context;
import android.content.Intent;
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

public class MainActivity extends ListActivity 
	implements OnItemClickListener, LeScanCallback {
	
	private static final int SCAN_ITEM =1;
	//private ConnectionManager mConnectionManager;
	private TumakuBLE mTumakuBLE;
	private static List <BluetoothDevice> mDeviceList=null;
	private static ListView mListView;
	private static MySimpleArrayAdapter mAdapter=null;
	private static Context mContext=null;
	private Menu mMenu;
	private boolean isScanning =false;
    private Handler mHandler = new Handler() ;
    private static Activity mActivity;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mActivity=this;
		mContext=this;
		mDeviceList=new ArrayList<BluetoothDevice>();
		//mTumakuBLE = TumakuBLE.getInstance(this);
		((TumakuBLEApplication)getApplication()).resetTumakuBLE();
		mTumakuBLE=((TumakuBLEApplication)getApplication()).getTumakuBLEInstance(this);
        mListView = getListView();
        mListView.setVisibility(View.VISIBLE);
        mAdapter=new MySimpleArrayAdapter(mContext,mDeviceList);
        mListView.setAdapter(mAdapter);
        //listView.setAdapter(new LazyAdapter(this));
        mListView.setOnItemClickListener(this);    	

	}
	
	
    @Override
    public void onItemClick(AdapterView<?> adapter, View view,
                int position, long id) {     	
    	if (Constant.DEBUG) Log.i("JMG", "Selected device " + mDeviceList.get(position).getAddress());
/*    	Intent intentActivity= new Intent(mContext, DeviceActivity.class);
		intentActivity.putExtra(YeelightDevice.EXTRA_DEVICE, mAdapter.getItemId(position));
		intentActivity.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
     	mContext.startActivity(intentActivity);

 */
    	if (isScanning) { //stop scanning
 		    configureScan(false);
		    mTumakuBLE.stopLeScan();
		    if (Constant.DEBUG) Log.i("JMG", "Stop scanning");
    	} 
        String address= mDeviceList.get(position).getAddress();
        String name = mDeviceList.get(position).getName();
        if (name==null) name="unknown";
    	Intent intentActivity= new Intent(this, DeviceActivity.class);
		intentActivity.putExtra(TumakuBLE.EXTRA_ADDRESS, address);
		intentActivity.putExtra(TumakuBLE.EXTRA_NAME, name);
    	this.startActivity(intentActivity);

    	if (Constant.DEBUG) Log.i("JMG", "Trying to connect");
    	//mConnectionManager.connect(address,true);
	    Toast.makeText(this, "Wait for connection to selected device", Toast.LENGTH_LONG).show();    	    		    
    }

	@Override
	protected void onResume() {
		super.onResume();
		mAdapter.clear();
		TumakuBLE.setup();
	}
    
	@Override
	protected void onPause() {
		super.onStop();
		//Make sure that there is no pending Callback
        mHandler.removeCallbacks(mStopRunnable);
		if (isScanning) {
			mTumakuBLE.stopLeScan();
		}		
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		mMenu=menu;
		String menuTitle= getResources().getString(R.string.scan);		
    	menu.add(0,SCAN_ITEM,0,menuTitle);
    	return true;
    }

	@Override
    public boolean onOptionsItemSelected (MenuItem item){
    	switch (item.getItemId()){
    		case SCAN_ITEM:
    			scan();
    			break;
    	}
    	return true;
    }    

    
	public void configureScan(boolean flag) {
		isScanning=flag;
		String itemText=null;
		if (isScanning) itemText=getResources().getString(R.string.stopScan);
	    else itemText=getResources().getString(R.string.scan);
		mMenu.findItem(SCAN_ITEM).setTitle(itemText);
	    if (Constant.DEBUG) Log.i("JMG", "Updated Menu Item. New value: " + itemText);    	
	}
	
	//Handle automatic stop of LEScan 
    private Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
		   mTumakuBLE.stopLeScan();
		   configureScan(false);
	       if (Constant.DEBUG) Log.i("JMG", "Stop scanning");
        }
    };

    
    private void scan() {
    	if (isScanning) { //stop scanning
 		    configureScan(false);
    		mTumakuBLE.stopLeScan();
		    if (Constant.DEBUG) Log.i("JMG", "Stop scanning");
    		return;
    	} else {
    	    mAdapter.clear();
    	    mAdapter.notifyDataSetChanged();
 		    configureScan(true);
    	    if (Constant.DEBUG) Log.i("JMG", "Start scanning for BLE devices...");
    	    mTumakuBLE.startLeScan();
    	    //automatically stop LE scan after 5 seconds
	    	mHandler.postDelayed(mStopRunnable, 30000); 
    	}
    }


	@Override
	public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

		String name="unknown";
		if (device.getName()!=null) name=device.getName();
		final String finalName =name;
		final String  finalAddress = device.getAddress();		
	    if (Constant.DEBUG) Log.i("JMG", "Found new device "+ finalAddress + " --- Name: " + finalName);
	    final BluetoothDevice finalDevice= device;
	    // This callback from Bluetooth LEScan can arrive at any moment not necessarily on UI thread. 
	    // Use this mechanism to update list View 
	    mActivity.runOnUiThread(new Runnable() {
		        @Override
		        public void run() {
				    mAdapter.add(finalDevice);	
				    mAdapter.notifyDataSetChanged();
			        if (Constant.DEBUG) Log.i("JMG", "Added new device "+ finalAddress + " --- Name: " + finalName);
		        }
			}
	       );

	}


    
    public class MySimpleArrayAdapter extends ArrayAdapter<BluetoothDevice> {
  	  private final Context context;
  	  public MySimpleArrayAdapter(Context context, List<BluetoothDevice> deviceList) {
  	    super(context, R.layout.device_list_item, deviceList);
  	    this.context = context;
  	  }

  	  @Override
  	  public View getView(int position, View convertView, ViewGroup parent) {
  	    LayoutInflater inflater = (LayoutInflater) context
  	        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
  	    View rowView = inflater.inflate(R.layout.device_list_item, parent, false);
  	    TextView nameText = (TextView) rowView.findViewById(R.id.deviceName);
  	    TextView addressText = (TextView) rowView.findViewById(R.id.deviceAddress);
  	    String name=mDeviceList.get(position).getName();
  	    if (name==null) name="unknown";
  	    nameText.setText(name);
  	    nameText.setTypeface(null, Typeface.BOLD);
  	    addressText.setText(mDeviceList.get(position).getAddress());
  	    if (name.toLowerCase().contains(mContext.getResources().getString(R.string.yeelight))) {
  	    	rowView.setBackgroundColor(Color.GREEN);
  	    }
  	    if (name.toLowerCase().contains(mContext.getResources().getString(R.string.sensortag))) {
  	    	rowView.setBackgroundColor(Color.RED);
  	    }
  	    return rowView;
  	  }
  	} 
    
}
