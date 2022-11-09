package de.androidcrypto.blechatclientblessed;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothPeripheral;

import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    // new in part 4
    Button listDevices;

    /**
     * Return Intent extra
     */
    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    String macAddressFromScan = ""; // will get filled by Intent from DeviceScanActivity

    // new in part 2
    Button connectToChatServiceDevices, disconnectFromChatServiceDevice;
    com.google.android.material.textfield.TextInputEditText connectedDevice, currentTime;
    com.google.android.material.textfield.TextInputEditText batteryLevel;
    Button enableSubscriptions, disableSubscriptions;

    com.google.android.material.textfield.TextInputLayout dataToSendLayout;
    com.google.android.material.textfield.TextInputEditText dataToSend;

    // new in part 2
    BluetoothHandler bluetoothHandler;
    String peripheralMacAddress; // filled by BroadcastReceiver getPeripheralMacAddressStateReceiver

    private TextView measurementValue;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int ACCESS_LOCATION_REQUEST = 2;
    private final DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // new in part 2
        connectToChatServiceDevices = findViewById(R.id.btnMainConnectToChatServiceDevices);
        disconnectFromChatServiceDevice = findViewById(R.id.btnMainDisconnectFromChatServiceDevice);
        connectedDevice = findViewById(R.id.etMainConnectedDevice);
        enableSubscriptions = findViewById(R.id.btnMainEnableAllSubscriptions);
        disableSubscriptions = findViewById(R.id.btnMainDisableAllSubscriptions);
        dataToSendLayout = findViewById(R.id.etMainDataToSendsLayout);
        dataToSend = findViewById(R.id.etMainDataToSend);
        batteryLevel = findViewById(R.id.etMainBatteryLevel);

        // new in part 4
        listDevices = findViewById(R.id.btnMainListDevices);

        // new in chat
        registerReceiver(chatMessageDataReceiver, new IntentFilter(BluetoothHandler.BLUETOOTH_CHAT));

        // new in part 2
        registerReceiver(getPeripheralMacAddressStateReceiver, new IntentFilter(BluetoothHandler.BLUETOOTHHANDLER_PERIPHERAL_MAC_ADDRESS));
        registerReceiver(currentTimeDataReceiver, new IntentFilter(BluetoothHandler.BLUETOOTHHANDLER_CURRENT_TIME));

        // new in part 3
        registerReceiver(batteryLevelDataReceiver, new IntentFilter(BluetoothHandler.BLUETOOTHHANDLER_BATTERY_LEVEL));

        registerReceiver(locationServiceStateReceiver, new IntentFilter((LocationManager.MODE_CHANGED_ACTION)));

        // this is for debug purposes - it leaves the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        /*
        // new in part 2
        connectToHrsDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothHandler != null) {
                    Log.i("Main", "connectToHrsDevices");
                    bluetoothHandler.connectToHeartRateServiceDevice();
                }
            }
        });

         */

        // new in part 2
        disconnectFromChatServiceDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothHandler != null) {
                    if (peripheralMacAddress.length() > 16) {
                        Log.i("Main", "disconnectFromChatServiceDevice");
                        System.out.println("periphalMac: " + peripheralMacAddress);
                        bluetoothHandler.enableAllSubscriptions(peripheralMacAddress, false);
                        bluetoothHandler.disconnectFromChatServiceDevice(peripheralMacAddress);
                    }
                }
            }
        });

        // new in part 2
        enableSubscriptions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothHandler != null) {
                    if (peripheralMacAddress.length() > 16) {
                        Log.i("Main", "enable all subscriptions");
                        bluetoothHandler.enableAllSubscriptions(peripheralMacAddress, true);
                        disconnectFromChatServiceDevice.setEnabled(false);
                        disableSubscriptions.setEnabled(true);
                        enableSubscriptions.setEnabled(false);
                    }
                }
            }
        });

        // new in part 2
        disableSubscriptions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothHandler != null) {
                    if (peripheralMacAddress.length() > 16) {
                        Log.i("Main", "disable all subscriptions");
                        bluetoothHandler.enableAllSubscriptions(peripheralMacAddress, false);
                        disconnectFromChatServiceDevice.setEnabled(true);
                        enableSubscriptions.setEnabled(true);
                        disableSubscriptions.setEnabled(false);
                    }
                }
            }
        });

        // new in part 4
        listDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, DeviceLeListActivity.class);
                startActivity(intent);
            }
        });

        // new in part 4
        // receive the address from DeviceListOwnActivity, if we receive data run the connection part
        Intent incommingIntent = getIntent();
        Bundle extras = incommingIntent.getExtras();
        if (extras != null) {
            initBluetoothHandler();
            macAddressFromScan = extras.getString(EXTRA_DEVICE_ADDRESS); // retrieve the data using keyName
            System.out.println("Main received data: " + macAddressFromScan);
            try {
                if (!macAddressFromScan.equals("")) {
                    Log.i("Main", "selected MAC: " + macAddressFromScan);
                    connectedDevice.setText(macAddressFromScan);
                    if (bluetoothHandler != null) {
                        if (macAddressFromScan.length() > 16) {
                            Log.i("Main", "connect to macAddress: " + macAddressFromScan);
                            bluetoothHandler.connectToAddress(macAddressFromScan);
                        } else {
                            Log.i("Main", "macAddressFromScan !> 16");
                        }
                    } else {
                        Log.i("Main", "bluetoothHandler == null");
                    }
                }
            } catch (NullPointerException e) {
                // do nothing, there are just no data
                Log.i("Main", "null pointer exception: " + e.toString());
            }
        }

        // new in chat
        // todo enable/disable input on connection state
        dataToSendLayout.setEndIconOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothHandler != null) {
                    if (peripheralMacAddress != null) {


                        if (peripheralMacAddress.length() > 16) {
                            String dataToSendString = dataToSend.getText().toString();
                            Log.i("Main", "send data");
                            bluetoothHandler.sendData(peripheralMacAddress, dataToSendString);
                            System.out.println("*** sendData: " + dataToSendString);
                            // clear edittext
                            dataToSend.setText("");
                            // todo implement a recyclerview
                        }
                    }
                }

            }
        });

        // new in chat
        connectToChatServiceDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothHandler != null) {
                    Log.i("Main", "connectToChatDevices");
                    bluetoothHandler.connectToChatServiceDevice();
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();

        if (getBluetoothManager().getAdapter() != null) {
            if (!isBluetoothEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                checkPermissions();
            }
        } else {
            Timber.e("This device has no Bluetooth hardware");
        }
    }

    private boolean isBluetoothEnabled() {
        BluetoothAdapter bluetoothAdapter = getBluetoothManager().getAdapter();
        if (bluetoothAdapter == null) return false;

        return bluetoothAdapter.isEnabled();
    }

    private void initBluetoothHandler() {
        // BluetoothHandler.getInstance(getApplicationContext());
        // new in part 2
        bluetoothHandler = BluetoothHandler.getInstance(getApplicationContext());
    }

    @NotNull
    private BluetoothManager getBluetoothManager() {
        return Objects.requireNonNull((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE), "cannot get BluetoothManager");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // new in chat
        unregisterReceiver(chatMessageDataReceiver);

        // new in part 2
        unregisterReceiver(getPeripheralMacAddressStateReceiver);
        unregisterReceiver(currentTimeDataReceiver);

        // new in part 3
        unregisterReceiver(batteryLevelDataReceiver);

        unregisterReceiver(locationServiceStateReceiver);
    }

    /**
     * section for BroadcastReceiver
     */

    private final BroadcastReceiver chatMessageDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String dataString = intent.getStringExtra(BluetoothHandler.BLUETOOTH_CHAT_EXTRA);
            if (dataString == null) return;
            // todo implement receyclerview
            System.out.println("*** received message: " + dataString);
            connectedDevice.setText(dataString);
            //batteryLevel.setText(resultString);
        }
    };

    // new in part 3
    private final BroadcastReceiver batteryLevelDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String dataString = intent.getStringExtra(BluetoothHandler.BLUETOOTHHANDLER_BATTERY_LEVEL_EXTRA);
            if (dataString == null) return;
            String resultString = "The remaining battery level is " +
                    dataString + " %";
            batteryLevel.setText(resultString);
        }
    };

    // new in part 2
    private final BroadcastReceiver getPeripheralMacAddressStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String dataString = intent.getStringExtra(BluetoothHandler.BLUETOOTHHANDLER_PERIPHERAL_MAC_ADDRESS_EXTRA);
            if (dataString == null) return;

            // save the peripheralsMacAddress
            if (dataString.length() > 16) {
                peripheralMacAddress = dataString.substring(0, 17);
                connectToChatServiceDevices.setEnabled(false);
                disconnectFromChatServiceDevice.setEnabled(true);
                connectedDevice.setText(dataString);
                enableSubscriptions.setEnabled(true);
                disableSubscriptions.setEnabled(false);
            } else {
                peripheralMacAddress = "disconnected";
                connectedDevice.setText(peripheralMacAddress);
                connectToChatServiceDevices.setEnabled(true);
                disconnectFromChatServiceDevice.setEnabled(false);
                enableSubscriptions.setEnabled(false);
                disableSubscriptions.setEnabled(false);
            }
        }
    };

    // new in part 2
    private final BroadcastReceiver currentTimeDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String dateString = intent.getStringExtra(BluetoothHandler.BLUETOOTHHANDLER_CURRENT_TIME_EXTRA);
            if (dateString == null) return;
            currentTime.setText(dateString);
        }
    };

    private final BroadcastReceiver locationServiceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(LocationManager.MODE_CHANGED_ACTION)) {
                boolean isEnabled = areLocationServicesEnabled();
                Timber.i("Location service state changed to: %s", isEnabled ? "on" : "off");
                checkPermissions();
            }
        }
    };

    /**
     * section for permissions
     */

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] missingPermissions = getMissingPermissions(getRequiredPermissions());
            if (missingPermissions.length > 0) {
                requestPermissions(missingPermissions, ACCESS_LOCATION_REQUEST);
            } else {
                permissionsGranted();
            }
        }
    }

    private String[] getMissingPermissions(String[] requiredPermissions) {
        List<String> missingPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String requiredPermission : requiredPermissions) {
                if (getApplicationContext().checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(requiredPermission);
                }
            }
        }
        return missingPermissions.toArray(new String[0]);
    }

    private String[] getRequiredPermissions() {
        int targetSdkVersion = getApplicationInfo().targetSdkVersion;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
            return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        } else return new String[]{Manifest.permission.ACCESS_COARSE_LOCATION};
    }

    private void permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work for SDK < 31
        int targetSdkVersion = getApplicationInfo().targetSdkVersion;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && targetSdkVersion < Build.VERSION_CODES.S) {
            if (checkLocationServices()) {
                initBluetoothHandler();
            }
        } else {
            initBluetoothHandler();
        }
    }

    private boolean areLocationServicesEnabled() {
        LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Timber.e("could not get location manager");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        } else {
            boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            return isGpsEnabled || isNetworkEnabled;
        }
    }

    private boolean checkLocationServices() {
        if (!areLocationServicesEnabled()) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Location services are not enabled")
                    .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                    .setPositiveButton("Enable", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // if this button is clicked, just close
                            // the dialog box and do nothing
                            dialog.cancel();
                        }
                    })
                    .create()
                    .show();
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check if all permission were granted
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            permissionsGranted();
        } else {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Permission is required for scanning Bluetooth peripherals")
                    .setMessage("Please grant permissions")
                    .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                            checkPermissions();
                        }
                    })
                    .create()
                    .show();
        }
    }
}
