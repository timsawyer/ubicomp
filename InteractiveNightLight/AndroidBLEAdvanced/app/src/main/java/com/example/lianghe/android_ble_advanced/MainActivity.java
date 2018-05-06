/* BLE demo: Demonstrate how to bi-directionally communicate with the Duo board through BLE. In this
 * example code, it shows how to send digital and analog data from the Android app to the Duo board
 * and how to receive data from the board.
 *
 * The app is built based on the example code provided by the RedBear Team:
 * https://github.com/RedBearLab/Android
 */

package com.example.lianghe.android_ble_advanced;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.example.lianghe.android_ble_advanced.BLE.RBLGattAttributes;
import com.example.lianghe.android_ble_advanced.BLE.RBLService;

import com.skydoves.colorpickerpreference.ColorEnvelope;
import com.skydoves.colorpickerpreference.ColorListener;
import com.skydoves.colorpickerpreference.ColorPickerDialog;
import com.skydoves.colorpickerpreference.ColorPickerView;
import com.skydoves.colorpickerpreference.FlagMode;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private AlertDialog alertDialog;

    // Define the device name and the length of the name
    // Note the device name and the length should be consistent with the ones defined in the Duo sketch
    private String mTargetDeviceName = "TIMBOle";
    private int mNameLen = 0x08;

    private final static String TAG = MainActivity.class.getSimpleName();

    // Declare all variables associated with the UI components
    private Button mConnectBtn = null;
    private TextView mDeviceName = null;
    private TextView mRssiValue = null;
    private TextView mUUID = null;
    private TextView mAnalogInValue = null;
    private ToggleButton mDigitalOutBtn, mDigitalInBtn, mAnalogInBtn;
    private SeekBar mServoSeekBar, mPWMSeekBar;
    private String mBluetoothDeviceName = "";
    private String mBluetoothDeviceUUID = "";

    // Declare all Bluetooth stuff
    private BluetoothGattCharacteristic mCharacteristicTx = null;
    private RBLService mBluetoothLeService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice = null;
    private String mDeviceAddress;

    private boolean flag = true;
    private boolean mConnState = false;
    private boolean mScanFlag = false;

    private byte[] mData = new byte[3];
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 2000;   // millis

    final private static char[] hexArray = { '0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    // Process service connection. Created by the RedBear Team
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((RBLService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private void setButtonDisable() {
        flag = false;
        mConnState = false;

        mDigitalOutBtn.setEnabled(flag);
        mAnalogInBtn.setEnabled(flag);
        mServoSeekBar.setEnabled(flag);
        mPWMSeekBar.setEnabled(flag);
        mConnectBtn.setText("Connect");
        mRssiValue.setText("");
        mDeviceName.setText("");
        mUUID.setText("");
    }

    private void setButtonEnable() {
        flag = true;
        mConnState = true;

        mDigitalOutBtn.setEnabled(flag);
        mAnalogInBtn.setEnabled(flag);
        mServoSeekBar.setEnabled(flag);
        mPWMSeekBar.setEnabled(flag);
        mConnectBtn.setText("Disconnect");
    }

    // Process the Gatt and get data if there is data coming from Duo board. Created by the RedBear Team
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (RBLService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Toast.makeText(getApplicationContext(), "Disconnected",
                        Toast.LENGTH_SHORT).show();
                setButtonDisable();
            } else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED
                    .equals(action)) {
                Toast.makeText(getApplicationContext(), "Connected",
                        Toast.LENGTH_SHORT).show();

                getGattService(mBluetoothLeService.getSupportedGattService());
            } else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
                mData = intent.getByteArrayExtra(RBLService.EXTRA_DATA);

                readAnalogInValue(mData);
            } else if (RBLService.ACTION_GATT_RSSI.equals(action)) {
                displayData(intent.getStringExtra(RBLService.EXTRA_DATA));
            }
        }
    };

    // Display the received RSSI on the interface
    private void displayData(String data) {
        if (data != null) {
            mRssiValue.setText(data);
            mDeviceName.setText(mBluetoothDeviceName);
            mUUID.setText(mBluetoothDeviceUUID);
        }
    }

    // Display the received Analog/Digital read on the interface
    private void readAnalogInValue(byte[] data) {
        for (int i = 0; i < data.length; i += 3) {
            if (data[i] == 0x0A) {
                if (data[i + 1] == 0x01)
                    mDigitalInBtn.setChecked(false);
                else
                    mDigitalInBtn.setChecked(true);
            } else if (data[i] == 0x0B) {
                int Value;

                Value = ((data[i + 1] << 8) & 0x0000ff00)
                        | (data[i + 2] & 0x000000ff);

                mAnalogInValue.setText(Value + "");
            }
        }
    }

    // Get Gatt service information for setting up the communication
    private void getGattService(BluetoothGattService gattService) {
        if (gattService == null)
            return;

        setButtonEnable();
        startReadRssi();

        mCharacteristicTx = gattService
                .getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);

        BluetoothGattCharacteristic characteristicRx = gattService
                .getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
        mBluetoothLeService.setCharacteristicNotification(characteristicRx,
                true);
        mBluetoothLeService.readCharacteristic(characteristicRx);
    }

    // Start a thread to read RSSI from the board
    private void startReadRssi() {
        new Thread() {
            public void run() {

                while (flag) {
                    mBluetoothLeService.readRssi();
                    try {
                        sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
        }.start();
    }

    // Scan all available BLE-enabled devices
    private void scanLeDevice() {
        new Thread() {

            @Override
            public void run() {
                mBluetoothAdapter.startLeScan(mLeScanCallback);

                try {
                    Thread.sleep(SCAN_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }.start();
    }

    // Callback function to search for the target Duo board which has matched UUID
    // If the Duo board cannot be found, debug if the received UUID matches the predefined UUID on the board
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             final byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    byte[] serviceUuidBytes = new byte[16];
                    String serviceUuid = "";
                    for (int i = (21+mNameLen), j = 0; i >= (6+mNameLen); i--, j++) {
                        serviceUuidBytes[j] = scanRecord[i];
                    }
                    /*
                     * This is where you can test if the received UUID matches the defined UUID in the Arduino
                     * Sketch and uploaded to the Duo board: 0x713d0000503e4c75ba943148f18d941e.
                     */
                    serviceUuid = bytesToHex(serviceUuidBytes);
                    if (stringToUuidString(serviceUuid).equals(
                            RBLGattAttributes.BLE_SHIELD_SERVICE
                                    .toUpperCase(Locale.ENGLISH)) && device.getName().equals(mTargetDeviceName)) {
                        mDevice = device;
                        mBluetoothDeviceName = mDevice.getName();
                        mBluetoothDeviceUUID = serviceUuid;
                    }
                }
            });
        }
    };

    // Convert an array of bytes into Hex format string
    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    // Convert a string to a UUID format
    private String stringToUuidString(String uuid) {
        StringBuffer newString = new StringBuffer();
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(0, 8));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(8, 12));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(12, 16));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(16, 20));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(20, 32));

        return newString.toString();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Associate all UI components with variables
        mConnectBtn = (Button) findViewById(R.id.connectBtn);
        mDeviceName = (TextView) findViewById(R.id.deviceName);
        mRssiValue = (TextView) findViewById(R.id.rssiValue);
        mAnalogInValue = (TextView) findViewById(R.id.ananlogIn);
        mDigitalOutBtn = (ToggleButton) findViewById(R.id.DOutBtn);
        mDigitalInBtn = (ToggleButton) findViewById(R.id.DInBtn);
        mAnalogInBtn = (ToggleButton) findViewById(R.id.AInBtn);
        mServoSeekBar = (SeekBar) findViewById(R.id.ServoSeekBar);
        mPWMSeekBar = (SeekBar) findViewById(R.id.PWMSeekBar);
        mUUID = (TextView) findViewById(R.id.uuidValue);

        // Connection button click event
        mConnectBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mScanFlag == false) {
                    // Scan all available devices through BLE
                    scanLeDevice();

                    Timer mTimer = new Timer();
                    mTimer.schedule(new TimerTask() {

                        @Override
                        public void run() {
                            if (mDevice != null) {
                                mDeviceAddress = mDevice.getAddress();
                                mBluetoothLeService.connect(mDeviceAddress);
                                mScanFlag = true;
                            } else {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        Toast toast = Toast
                                                .makeText(
                                                        MainActivity.this,
                                                        "Couldn't search Ble Shiled device!",
                                                        Toast.LENGTH_SHORT);
                                        toast.setGravity(0, 0, Gravity.CENTER);
                                        toast.show();
                                    }
                                });
                            }
                        }
                    }, SCAN_PERIOD);
                }

                System.out.println(mConnState);
                if (mConnState == false) {
                    mBluetoothLeService.connect(mDeviceAddress);
                } else {
                    mBluetoothLeService.disconnect();
                    mBluetoothLeService.close();
                    setButtonDisable();
                }
            }
        });

        // Send data to Duo board
        // It has three bytes: maker, data value, reserved
        mDigitalOutBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {


            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                byte buf[] = new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00 };

                if (isChecked == true)
                    buf[1] = 0x01;
                else
                    buf[1] = 0x00;

                mCharacteristicTx.setValue(buf);
                mBluetoothLeService.writeCharacteristic(mCharacteristicTx);
            }
        });

        mAnalogInBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                byte[] buf = new byte[] { (byte) 0xA0, (byte) 0x00, (byte) 0x00 };

                if (isChecked == true)
                    buf[1] = 0x01;
                else
                    buf[1] = 0x00;

                mCharacteristicTx.setValue(buf);
                mBluetoothLeService.writeCharacteristic(mCharacteristicTx);
            }
        });

        // Configure the servo Seekbar
        mServoSeekBar.setEnabled(false);
        mServoSeekBar.setMax(180);  // Servo can rotate from 0 to 180 degree
        mServoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                byte[] buf = new byte[] { (byte) 0x03, (byte) 0x00, (byte) 0x00 };

                buf[1] = (byte) mServoSeekBar.getProgress();

                mCharacteristicTx.setValue(buf);
                mBluetoothLeService.writeCharacteristic(mCharacteristicTx);
            }
        });

        // Configure the PWM Seekbar
        mPWMSeekBar.setEnabled(false);
        mPWMSeekBar.setMax(255);
        mPWMSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                byte[] buf = new byte[] { (byte) 0x02, (byte) 0x00, (byte) 0x00 };

                buf[1] = (byte) mPWMSeekBar.getProgress();

                mCharacteristicTx.setValue(buf);
                mBluetoothLeService.writeCharacteristic(mCharacteristicTx);
            }
        });

        // Bluetooth setup. Created by the RedBear team.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
        }

        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        Intent gattServiceIntent = new Intent(MainActivity.this,
                RBLService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);



        // colorpicker dialog setup
        ColorPickerDialog.Builder builder = new ColorPickerDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("ColorPicker Dialog");
        builder.setPreferenceName("MyColorPickerDialog");
        builder.setPositiveButton("OK", new ColorListener() {
            @Override
            public void onColorSelected(ColorEnvelope colorEnvelope) {
                int[] rgb = colorEnvelope.getColorRGB();

                // send red, green, blue in 3 separate messages
                byte[] bufRed = new byte[] { (byte) 0x07, (byte) 0x00, (byte) 0x00 };
                bufRed[1] = (byte) rgb[0];
                mCharacteristicTx.setValue(bufRed);
                mBluetoothLeService.writeCharacteristic(mCharacteristicTx);

                byte[] bufGreen = new byte[] { (byte) 0x08, (byte) 0x00, (byte) 0x00 };
                bufGreen[1] = (byte) rgb[1];
                mCharacteristicTx.setValue(bufGreen);
                mBluetoothLeService.writeCharacteristic(mCharacteristicTx);

                byte[] bufBlue = new byte[] { (byte) 0x09, (byte) 0x00, (byte) 0x00 };
                bufBlue[1] = (byte) rgb[2];
                mCharacteristicTx.setValue(bufBlue);
                mBluetoothLeService.writeCharacteristic(mCharacteristicTx);

                // update ui to show new color
                String rgbText = "rgb(" + rgb[0] + "," + rgb[1] + "," + rgb[2] + ")";
                TextView textView = findViewById(R.id.colorRGB);
                textView.setText(rgbText);

                LinearLayout linearLayout = findViewById(R.id.colorRect);
                linearLayout.setBackgroundColor(colorEnvelope.getColor());


            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });

        alertDialog = builder.create();

        ColorPickerView colorPickerView = builder.getColorPickerView();
        colorPickerView.setFlagMode(FlagMode.ALWAYS);
    }

    public void showDialog(View view) {
        alertDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check if BLE is enabled on the device. Created by the RedBear team.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

    }

    @Override
    protected void onStop() {
        super.onStop();

        flag = false;

        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mServiceConnection != null)
            unbindService(mServiceConnection);
    }

    // Create a list of intent filters for Gatt updates. Created by the RedBear team.
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(RBLService.ACTION_GATT_RSSI);

        return intentFilter;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT
                && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}