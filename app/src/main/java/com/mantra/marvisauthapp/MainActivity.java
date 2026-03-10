package com.mantra.marvisauthapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.mantra.marvisauth.DeviceInfo;
import com.mantra.marvisauth.IrisAnatomy;
import com.mantra.marvisauth.MarvisAuth_Callback;
import com.mantra.marvisauth.enums.DeviceDetection;
import com.mantra.marvisauth.enums.DeviceModel;

import java.io.File;

public class MainActivity extends AppCompatActivity implements MarvisAuth_Callback {

    private CardView cardCapture, cardMatch;
    private TextView txtConnectionStatus, txtDeviceDetails, txtTotalUsers, txtBottomMessage;
    private ImageView imgConnStatus, imgHeaderStatus;
    private Button btnInitDevice, btnUninitDevice, btnShowUsers, btnDeleteOptions;

    private BiometricManager bioManager;
    private IrisDatabaseHelper dbHelper;
    private DeviceModel pendingModel;

    private int selectedDeleteOption = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new IrisDatabaseHelper(this);
        bioManager = BiometricManager.getInstance(this);

        initViews();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUserCount();
        bioManager.setListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        bioManager.removeListener();
    }

    private void initViews() {
        cardCapture = findViewById(R.id.cardCapture);
        cardMatch = findViewById(R.id.cardMatch);
        txtConnectionStatus = findViewById(R.id.txtConnectionStatus);
        txtDeviceDetails = findViewById(R.id.txtDeviceDetails);
        txtTotalUsers = findViewById(R.id.txtTotalUsers);
        txtBottomMessage = findViewById(R.id.txtBottomMessage);

        imgConnStatus = findViewById(R.id.imgConnStatus);
        imgConnStatus.setBackgroundResource(0);

        imgHeaderStatus = findViewById(R.id.imgHeaderStatus);

        btnInitDevice = findViewById(R.id.btnInitDevice);
        btnUninitDevice = findViewById(R.id.btnUninitDevice);
        btnShowUsers = findViewById(R.id.btnShowUsers);
        btnDeleteOptions = findViewById(R.id.btnDeleteOptions);
    }

    private void setupListeners() {
        btnInitDevice.setOnClickListener(v -> {
            if (bioManager.isReady()) {
                Toast.makeText(this, "Already Initialized", Toast.LENGTH_SHORT).show();
                return;
            }

            if (pendingModel != null || bioManager.getConnectedModel() != null) {
                txtBottomMessage.setText("Initializing...");
                btnInitDevice.setEnabled(false);

                new Thread(() -> {
                    int ret = bioManager.initDevice();

                    runOnUiThread(() -> {
                        if (bioManager.isReady() && ret == 0) {
                            fillDeviceDetails(bioManager.getLastDeviceInfo());
                            txtBottomMessage.setText("Initialization Success.");
                            btnInitDevice.setEnabled(false);
                            btnInitDevice.setAlpha(0.6f);
                            btnUninitDevice.setEnabled(true);
                            btnUninitDevice.setAlpha(1.0f);
                            cardCapture.setAlpha(1.0f);
                            cardMatch.setAlpha(1.0f);
                        } else {
                            txtBottomMessage.setText("Initialization Failed: " + bioManager.getErrorMessage(ret));
                            btnInitDevice.setEnabled(true);
                            btnInitDevice.setAlpha(1.0f);
                        }
                    });
                }).start();
            } else {
                Toast.makeText(this, "Connect device first", Toast.LENGTH_SHORT).show();
            }
        });

        btnUninitDevice.setOnClickListener(v -> {
            new Thread(() -> {
                bioManager.uninitDevice();
                runOnUiThread(() -> {
                    txtDeviceDetails.setText("Make: -\nModel: -\nSerial: -\nW/H: -");
                    txtBottomMessage.setText("Device Uninitialized. Press Init.");

                    btnInitDevice.setEnabled(true);
                    btnInitDevice.setAlpha(1.0f);
                    btnUninitDevice.setEnabled(false);
                    btnUninitDevice.setAlpha(0.6f);
                    cardCapture.setAlpha(0.6f);
                    cardMatch.setAlpha(0.6f);

                    Toast.makeText(this, "Device Uninitialized", Toast.LENGTH_SHORT).show();
                });
            }).start();
        });

        cardCapture.setOnClickListener(v -> {
            if (!bioManager.isConnected()) {
                Toast.makeText(this, "Please connect device first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!bioManager.isReady()) {
                Toast.makeText(this, "Please init device first", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(MainActivity.this, EnrollmentActivity.class));
        });

        cardMatch.setOnClickListener(v -> {
            if (!bioManager.isConnected()) {
                Toast.makeText(this, "Please connect device first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!bioManager.isReady()) {
                Toast.makeText(this, "Please init device first", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(MainActivity.this, MatchActivity.class));
        });

        btnShowUsers.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, UserListActivity.class));
        });

        btnDeleteOptions.setOnClickListener(v -> showDeleteOptionsDialog());
    }

    @Override
    public void OnDeviceDetection(String deviceName, DeviceDetection detection) {
        runOnUiThread(() -> {
            if (detection == DeviceDetection.CONNECTED) {
                try {
                    pendingModel = DeviceModel.valueOf(deviceName);
                } catch (Exception e) {
                    pendingModel = DeviceModel.MIS100V2;
                }

                setUIConnected(true, deviceName != null ? deviceName : "MIS100V2");

                if (bioManager.isReady()) {
                    txtBottomMessage.setText("Device Ready.");
                    btnInitDevice.setEnabled(false);
                    btnInitDevice.setAlpha(0.6f);
                    btnUninitDevice.setEnabled(true);
                    btnUninitDevice.setAlpha(1.0f);
                    fillDeviceDetails(bioManager.getLastDeviceInfo());
                } else {
                    txtBottomMessage.setText("Device found! Press INIT.");
                    btnInitDevice.setEnabled(true);
                    btnInitDevice.setAlpha(1.0f);
                    btnUninitDevice.setEnabled(false);
                    btnUninitDevice.setAlpha(0.6f);
                    txtDeviceDetails.setText("Make: -\nModel: -\nSerial: -\nW/H: -");
                }
            } else {
                pendingModel = null;
                setUIConnected(false, "");
                txtBottomMessage.setText("Device Disconnected.");

                txtDeviceDetails.setText("Make: -\nModel: -\nSerial: -\nW/H: -");
                btnInitDevice.setEnabled(false);
                btnInitDevice.setAlpha(0.6f);
                btnUninitDevice.setEnabled(false);
                btnUninitDevice.setAlpha(0.6f);
            }
        });
    }

    private void setUIConnected(boolean connected, String deviceName) {
        if (connected) {
            imgConnStatus.setImageDrawable(null);
            imgConnStatus.setBackgroundResource(R.drawable.ic_launcher_background);
            imgConnStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#22C55E")));

            txtConnectionStatus.setText("Connected (" + deviceName + ")");
            txtConnectionStatus.setTextColor(Color.parseColor("#22C55E"));

            imgHeaderStatus.setImageResource(R.drawable.green_eye_connect);
            imgHeaderStatus.clearColorFilter();
        } else {
            imgConnStatus.setImageDrawable(null);
            imgConnStatus.setBackgroundResource(R.drawable.ic_launcher_background);
            imgConnStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));

            txtConnectionStatus.setText("Disconnected");
            txtConnectionStatus.setTextColor(Color.parseColor("#EF4444"));

            imgHeaderStatus.setImageResource(R.drawable.red_eye_disconnect);
            imgHeaderStatus.clearColorFilter();
        }

        cardCapture.setAlpha(connected && bioManager.isReady() ? 1.0f : 0.6f);
        cardMatch.setAlpha(connected && bioManager.isReady() ? 1.0f : 0.6f);
    }

    private void fillDeviceDetails(DeviceInfo info) {
        if (info == null) return;
        String details = String.format("Make: %s\nModel: %s\nSerial: %s\nW/H: %d x %d",
                info.Make, info.Model, info.SerialNo, info.Width, info.Height);
        txtDeviceDetails.setText(details);

        cardCapture.setAlpha(1.0f);
        cardMatch.setAlpha(1.0f);
    }

    private void refreshUserCount() {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            long count = DatabaseUtils.queryNumEntries(db, IrisDatabaseHelper.TABLE_USERS);
            txtTotalUsers.setText("Registered Users: " + count);
        } catch (Exception e) {
            txtTotalUsers.setText("Registered Users: 0");
        }
    }

    private void showDeleteOptionsDialog() {
        String[] options = {"Delete local files", "Delete database", "Delete both"};
        selectedDeleteOption = 0; // Default selection for delete dialogue

        new AlertDialog.Builder(this)
                .setTitle("Select Data to Clear")
                .setSingleChoiceItems(options, 0, (dialog, which) -> {
                    selectedDeleteOption = which;
                })
                .setPositiveButton("DELETE", (dialog, which) -> {
                    if (selectedDeleteOption == 0) {
                        deleteAllLocalFiles();
                        Toast.makeText(this, "Local Files Cleared", Toast.LENGTH_SHORT).show();
                    } else if (selectedDeleteOption == 1) {
                        clearDatabase();
                        Toast.makeText(this, "Database Cleared", Toast.LENGTH_SHORT).show();
                    } else if (selectedDeleteOption == 2) {
                        deleteAllLocalFiles();
                        clearDatabase();
                        Toast.makeText(this, "All Data Cleared", Toast.LENGTH_SHORT).show();
                    }
                    refreshUserCount();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void clearDatabase() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("DELETE FROM " + IrisDatabaseHelper.TABLE_USERS);
    }

    private void deleteAllLocalFiles() {
        File baseDirectory = new File(getExternalFilesDir(null), "MarvisUsers");
        if (baseDirectory.exists() && baseDirectory.isDirectory()) {
            deleteRecursive(baseDirectory);
        }
    }

    // Recursively delete files and subdirectories
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    @Override
    public void OnPreview(int errorCode, int quality, byte[] image, IrisAnatomy anatomy) {
    }

    @Override
    public void OnComplete(int errorCode, int quality, byte[] image, IrisAnatomy anatomy) {
    }
}