package de.androidcrypto.blechatclientblessed;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.welie.blessed.BluetoothBytesParser;
import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothCentralManagerCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;
import com.welie.blessed.ConnectionPriority;
import com.welie.blessed.GattStatus;
import com.welie.blessed.HciStatus;

import com.welie.blessed.ScanFailure;
import com.welie.blessed.WriteType;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import timber.log.Timber;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT8;
import static com.welie.blessed.BluetoothBytesParser.bytes2String;

import static java.lang.Math.abs;

class BluetoothHandler {

    // Intent constants
    // new in part 2
    public static final String BLUETOOTHHANDLER_PERIPHERAL_MAC_ADDRESS = "androidcrypto.bluetoothhandler.peripheralmacaddress";
    public static final String BLUETOOTHHANDLER_PERIPHERAL_MAC_ADDRESS_EXTRA = "androidcrypto.bluetoothhandler.peripheralmacaddress.extra";

    // new in chat, randomly generated UUID (e.g. https://www.uuidgenerator.net/version4)
    private static final UUID BLUETOOTH_CHAT_SERVICE_UUID = UUID.fromString("00008dc1-c6a2-484f-9dae-93a63ad832a5");
    private static final UUID BLUETOOTH_CHAT_CHARACTERISTIC_UUID = UUID.fromString("00008dc2-c6a2-484f-9dae-93a63ad832a5");
    // receive chat messages
    public static final String BLUETOOTH_CHAT = "androidcrypto.bluetooth.chat";
    public static final String BLUETOOTH_CHAT_EXTRA = "androidcrypto.bluetooth.chat.extra";


    public static final String BLUETOOTHHANDLER_CURRENT_TIME = "androidcrypto.bluetoothhandler.currenttime";
    public static final String BLUETOOTHHANDLER_CURRENT_TIME_EXTRA = "androidcrypto.bluetoothhandler.currenttime.extra";
    // new in part 3
    public static final String BLUETOOTHHANDLER_BATTERY_LEVEL = "androidcrypto.bluetoothhandler.batterylevel";
    public static final String BLUETOOTHHANDLER_BATTERY_LEVEL_EXTRA = "androidcrypto.bluetoothhandler.batterylevel.extra";

    public static final String MEASUREMENT_EXTRA_PERIPHERAL = "androidcrypto.measurement.peripheral";

    // UUIDs for the Device Information service (DIS)
    private static final UUID DEVICE_INFORMATION_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    private static final UUID MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Battery Service (BAS)
    private static final UUID BATTERY_LEVEL_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");

    // Local variables
    public BluetoothCentralManager central;
    private static BluetoothHandler instance = null;
    private final Context context;
    private final Handler handler = new Handler();
    private int currentTimeCounter = 0;

    // new in chat
    public void sendData(String peripheralMacAddress, String dataToSendString) {
        Timber.i("send data: %s", dataToSendString);

        byte[] value = dataToSendString.getBytes(StandardCharsets.UTF_8);
        // todo check for data length and MTU
        // If it has the write property we write the current time
        try {
            // write the data to the device
            BluetoothPeripheral connectedPeripheral = central.getPeripheral(peripheralMacAddress);
            System.out.println("*** currentMTU: " + connectedPeripheral.getCurrentMtu());
            connectedPeripheral.writeCharacteristic(BLUETOOTH_CHAT_SERVICE_UUID, BLUETOOTH_CHAT_CHARACTERISTIC_UUID, value, WriteType.WITHOUT_RESPONSE);
            System.out.println("*** data send to peripheral: " + dataToSendString);
        } catch (IllegalArgumentException e) {
            // do nothing
            System.out.println("writeCharacteristic not allowed");
        }
    }

    // new in part 4
    public void connectToAddress(String peripheralMacAddress) {
        Timber.i("BH connectToAddress: %s", peripheralMacAddress);
        central.stopScan();
        BluetoothPeripheral bluetoothPeripheral = central.getPeripheral(peripheralMacAddress);
        central.connectPeripheral(bluetoothPeripheral, peripheralCallback);
    }

    // new in part 2
    /*
    public void connectToHeartRateServiceDevice() {
        startScanHrs();
    }

     */

    // new in chat
    public void connectToChatServiceDevice() {
        startScanChat();
    }

    // new in part 2
    public void disconnectFromChatServiceDevice(String peripheralMacAddress) {
        BluetoothPeripheral connectedPeripheral = central.getPeripheral(peripheralMacAddress);
        central.cancelConnection(connectedPeripheral);
        try {
            central.close();
        } catch (IllegalArgumentException e) {
            // do nothing
        }
    }

    // new in part 2
    public void enableAllSubscriptions(String peripheralMacAddress, boolean enable) {
        BluetoothPeripheral connectedPeripheral = central.getPeripheral(peripheralMacAddress);
        connectedPeripheral.setNotify(BATTERY_LEVEL_SERVICE_UUID, BATTERY_LEVEL_CHARACTERISTIC_UUID, enable);
    }

    // Callback for peripherals
    private final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {

        @Override
        public void onServicesDiscovered(@NotNull BluetoothPeripheral peripheral) {
            // Request a higher MTU, iOS always asks for 185
            peripheral.requestMtu(185);
            // Request a new connection priority
            peripheral.requestConnectionPriority(ConnectionPriority.HIGH);
            // ### commented this line out as not all (most older) Smartphones don't run Bluetooth 5 with these capabilities
            // peripheral.setPreferredPhy(PhyType.LE_1M, PhyType.LE_2M, PhyOptions.S2);

            // Read manufacturer and model number from the Device Information Service
            peripheral.readCharacteristic(DEVICE_INFORMATION_SERVICE_UUID, MANUFACTURER_NAME_CHARACTERISTIC_UUID);
            peripheral.readCharacteristic(DEVICE_INFORMATION_SERVICE_UUID, MODEL_NUMBER_CHARACTERISTIC_UUID);
            peripheral.readPhy();
        }

        @Override
        public void onNotificationStateUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status == GattStatus.SUCCESS) {
                final boolean isNotifying = peripheral.isNotifying(characteristic);
                Timber.i("SUCCESS: Notify set to '%s' for %s", isNotifying, characteristic.getUuid());

            } else {
                Timber.e("ERROR: Changing notification state failed for %s (%s)", characteristic.getUuid(), status);
            }
        }

        @Override
        public void onCharacteristicWrite(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status == GattStatus.SUCCESS) {
                Timber.i("SUCCESS: Writing <%s> to <%s>", bytes2String(value), characteristic.getUuid());
            } else {
                Timber.i("ERROR: Failed writing <%s> to <%s> (%s)", bytes2String(value), characteristic.getUuid(), status);
            }
        }

        @Override
        public void onCharacteristicUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status != GattStatus.SUCCESS) return;

            UUID characteristicUUID = characteristic.getUuid();
            BluetoothBytesParser parser = new BluetoothBytesParser(value);

            if (characteristicUUID.equals(BATTERY_LEVEL_CHARACTERISTIC_UUID)) {
                String valueString = parser.getIntValue(FORMAT_UINT8).toString();
                Timber.i("Received battery level %s%%", valueString);
                // new in part 3
                Intent intent = new Intent(BLUETOOTHHANDLER_BATTERY_LEVEL);
                intent.putExtra(BLUETOOTHHANDLER_BATTERY_LEVEL_EXTRA, valueString);
                sendMeasurement(intent, peripheral);

            } else if (characteristicUUID.equals(MANUFACTURER_NAME_CHARACTERISTIC_UUID)) {
                String manufacturer = parser.getStringValue(0);
                Timber.i("Received manufacturer: %s", manufacturer);
            } else if (characteristicUUID.equals(MODEL_NUMBER_CHARACTERISTIC_UUID)) {
                String modelNumber = parser.getStringValue(0);
                Timber.i("Received modelnumber: %s", modelNumber);
            }
        }

        @Override
        public void onMtuChanged(@NotNull BluetoothPeripheral peripheral, int mtu, @NotNull GattStatus status) {
            Timber.i("new MTU set: %d", mtu);
        }

        private void sendMeasurement(@NotNull Intent intent, @NotNull BluetoothPeripheral peripheral ) {
            intent.putExtra(MEASUREMENT_EXTRA_PERIPHERAL, peripheral.getAddress());
            context.sendBroadcast(intent);
        }

    };

    // Callback for central
    private final BluetoothCentralManagerCallback bluetoothCentralManagerCallback = new BluetoothCentralManagerCallback() {

        @Override
        public void onConnectedPeripheral(@NotNull BluetoothPeripheral peripheral) {
            Timber.i("connected to '%s'", peripheral.getName());
            Intent intent = new Intent(BLUETOOTHHANDLER_PERIPHERAL_MAC_ADDRESS);
            String returnString = peripheral.getAddress() + " (" +
            peripheral.getName() + ")";
            intent.putExtra(BLUETOOTHHANDLER_PERIPHERAL_MAC_ADDRESS_EXTRA, returnString);
            context.sendBroadcast(intent);
        }

        @Override
        public void onConnectionFailed(@NotNull BluetoothPeripheral peripheral, final @NotNull HciStatus status) {
            Timber.e("connection '%s' failed with status %s", peripheral.getName(), status);
            Intent intent = new Intent(BLUETOOTHHANDLER_PERIPHERAL_MAC_ADDRESS);
            String returnString = "";
            intent.putExtra(BLUETOOTHHANDLER_PERIPHERAL_MAC_ADDRESS_EXTRA, returnString);
            context.sendBroadcast(intent);
        }

        @Override
        public void onDisconnectedPeripheral(@NotNull final BluetoothPeripheral peripheral, final @NotNull HciStatus status) {
            Timber.i("disconnected '%s' with status %s", peripheral.getName(), status);
            Intent intent = new Intent(BLUETOOTHHANDLER_PERIPHERAL_MAC_ADDRESS);
            String returnString = "";
            intent.putExtra(BLUETOOTHHANDLER_PERIPHERAL_MAC_ADDRESS_EXTRA, returnString);
            context.sendBroadcast(intent);

            // do not reconnect to this device when it becomes available again
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //central.autoConnectPeripheral(peripheral, peripheralCallback);
                    central.connectPeripheral(peripheral, peripheralCallback);
                }
            }, 5000);
        }

        @Override
        public void onDiscoveredPeripheral(@NotNull BluetoothPeripheral peripheral, @NotNull ScanResult scanResult) {
            Timber.i("Found peripheral '%s'", peripheral.getName());
            central.stopScan();

            central.connectPeripheral(peripheral, peripheralCallback);

        }

        @Override
        public void onBluetoothAdapterStateChanged(int state) {
            Timber.i("bluetooth adapter changed state to %d", state);
            if (state == BluetoothAdapter.STATE_ON) {
                // Bluetooth is on now, start scanning again
                // Scan for peripherals with a certain service UUIDs
                central.startPairingPopupHack();
                // changed in part 2
                //startScan();
                startScanChat();
            }
        }

        @Override
        public void onScanFailed(@NotNull ScanFailure scanFailure) {
            Timber.i("scanning failed with error %s", scanFailure);
        }
    };

    public static synchronized BluetoothHandler getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothHandler(context.getApplicationContext());
        }
        return instance;
    }

    private BluetoothHandler(Context context) {
        this.context = context;

        // Plant a tree
        Timber.plant(new Timber.DebugTree());

        // Create BluetoothCentral
        central = new BluetoothCentralManager(context, bluetoothCentralManagerCallback, new Handler());

        // Scan for peripherals with a certain service UUIDs
        central.startPairingPopupHack();

        // changed in part 2
        // the scanning is commented out here, it will be done by calling connectToHeartRateServiceDevice
        // startScan();

    }

    /*
    private void startScan() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // ### add HRS = HeartRate service to scan UUIDs
                central.scanForPeripheralsWithServices(new UUID[]{HEART_RATE_SERVICE_UUID, BLOOD_PRESSURE_SERVICE_UUID, HEALTH_THERMOMETER_SERVICE_UUID, PULSE_OXIMETER_SERVICE_UUID, WEIGHT_SCALE_SERVICE_UUID, GLUCOSE_SERVICE_UUID});
                //central.scanForPeripheralsWithServices(new UUID[]{BLP_SERVICE_UUID, HTS_SERVICE_UUID, PLX_SERVICE_UUID, WSS_SERVICE_UUID, GLUCOSE_SERVICE_UUID});
            }
        },1000);

    }
    */


    // new in chat
    // this will connect to HeartRateService devices only
    private void startScanChat() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                central.scanForPeripheralsWithServices(new UUID[]{BLUETOOTH_CHAT_SERVICE_UUID});
            }
        },1000);
    }

    private boolean isOmronBPM(final String name) {
        return name.contains("BLESmart_") || name.contains("BLEsmart_");
    }
}
