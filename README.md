MSMBle
========

Android project created during the M-week (February 2014) at MakespaceMadrid to show how to manage Bluetooth Low Energy devices form an Android terminal.
More information (in Spanish) available at:

http://wiki.makespacemadrid.org/index.php?title=Bombilla_LED_de_colores_con_Bluetooth_Low_Energy

A tutorial in English coming soon...


The application scans for any BLE device and its services and characteristics.
If the Yeelight Service is found, an activity can be launched to control colour and intensity of Yeelight Blue bulb
If  SensorTag services are found, an activity can be launched to visualise the state of the buttons, ambient temperature (IR Temperature sensor) 
and humidity from the SensorTag.

This application has been tested on a Google Nexus 7 (2013 edition)

Free aplication available for download from Google Play: 

https://play.google.com/store/apps/details?id=com.tumaku.msmble


Notes
=====

+ Based on the API released by Yeelight:

  http://www.yeelight.com/en_US/info/download

+ Based on the code created by Dave Smith (Double Encore):

  https://github.com/devunwired/accessory-samples/tree/master/BluetoothGatt
  
  http://www.doubleencore.com/2013/12/bluetooth-smart-for-android/
  
+ Based on the documentation of Texas Instruments SensorTag:

  http://processors.wiki.ti.com/index.php/SensorTag_User_Guide