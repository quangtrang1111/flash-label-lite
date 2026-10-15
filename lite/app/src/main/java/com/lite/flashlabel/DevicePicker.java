package com.lite.flashlabel;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Printer list (bonded + discovered), replacing DeviceHomeActivity.
 * Filtering follows DeviceManager.isAvailableDevice().
 */
@SuppressWarnings({"MissingPermission", "deprecation"})
final class DevicePicker {
    interface Callback {
        void onConnected(boolean ok);
    }

    private DevicePicker() {
    }

    static void show(Activity a, Prefs prefs, Callback cb) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(a, Strings.NO_BLUETOOTH, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adapter.isEnabled()) {
            a.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }
        List<String> labels = new ArrayList<>();
        List<String> macs = new ArrayList<>();
        ArrayAdapter<String> list = new ArrayAdapter<>(a, android.R.layout.simple_list_item_1, labels);
        for (BluetoothDevice d : adapter.getBondedDevices()) {
            if (Printer.isSupportedName(d.getName())) {
                labels.add(d.getName() + "\n" + d.getAddress());
                macs.add(d.getAddress());
            }
        }
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d == null || !Printer.isSupportedName(d.getName()) || macs.contains(d.getAddress())) return;
                macs.add(d.getAddress());
                list.add(d.getName() + "\n" + d.getAddress());
            }
        };
        a.registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        adapter.startDiscovery();

        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(Strings.CHOOSE_PRINTER)
                .setAdapter(list, (d, which) -> connect(a, prefs, macs.get(which), cb))
                .setNegativeButton(Strings.CANCEL, null)
                .create();
        dialog.setOnDismissListener(d -> {
            adapter.cancelDiscovery();
            try {
                a.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
            }
        });
        dialog.show();
    }

    static void connect(Activity a, Prefs prefs, String mac, Callback cb) {
        ProgressDialog pd = ProgressDialog.show(a, null, Strings.CONNECTING, true, false);
        new Thread(() -> {
            boolean ok = Printer.get().connect(mac, 20000);
            a.runOnUiThread(() -> {
                if (!a.isFinishing()) pd.dismiss();
                if (ok) {
                    prefs.lastPrinter(mac);
                } else {
                    Toast.makeText(a, Strings.CONNECT_FAILED, Toast.LENGTH_SHORT).show();
                }
                cb.onConnected(ok);
            });
        }, "connect").start();
    }
}
