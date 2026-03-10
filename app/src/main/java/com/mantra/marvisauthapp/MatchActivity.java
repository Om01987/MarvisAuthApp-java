package com.mantra.marvisauthapp;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.mantra.marvisauth.IrisAnatomy;
import com.mantra.marvisauth.MarvisAuth_Callback;
import com.mantra.marvisauth.enums.DeviceDetection;
import com.mantra.marvisauth.enums.ImageFormat;

public class MatchActivity extends AppCompatActivity implements MarvisAuth_Callback {

    private BiometricManager bioManager;
    private IrisDatabaseHelper dbHelper;

    private ImageView imgMatchPreview;
    private TextView txtMatchStatus;
    private Button btnActionMatching;

    private boolean isMatchingActive = false;
    private Paint paint;
    private Thread matchingThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match);

        bioManager = BiometricManager.getInstance(this);
        dbHelper = new IrisDatabaseHelper(this);

        imgMatchPreview = findViewById(R.id.imgMatchPreview); // Ensure this ID matches your XML
        txtMatchStatus = findViewById(R.id.txtMatchStatus);

        // Changed from btnStopMatching to a toggle button
        btnActionMatching = findViewById(R.id.btnStopMatching);

        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setAntiAlias(true);

        setupUIForIdle();

        // Toggle Logic for Start/Stop
        btnActionMatching.setOnClickListener(v -> {
            if (isMatchingActive) {
                stopMatchingLoop();
            } else {
                startContinuousMatching();
            }
        });

        // Handle Back Press explicitly to stop scanner
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                stopMatchingLoop();
                finish(); // Return to Dashboard
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bioManager.setListener(this);
        // We do NOT start automatically anymore. User must click "Start Matching"
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMatchingLoop();
        bioManager.removeListener();
    }

    private void setupUIForScanning() {
        runOnUiThread(() -> {
            btnActionMatching.setText("Stop Matching");
            btnActionMatching.setBackgroundColor(Color.parseColor("#EF4444")); // Red
            imgMatchPreview.setImageResource(R.drawable.logo); // Reset to default eye logo
        });
    }

    private void setupUIForIdle() {
        runOnUiThread(() -> {
            btnActionMatching.setText("Start Matching");
            btnActionMatching.setBackgroundColor(Color.parseColor("#0ea5e9")); // Blue/Primary
            updateStatus("Scanner Idle. Press Start.", Color.parseColor("#334155"));
            imgMatchPreview.setImageResource(R.drawable.logo);
        });
    }

    private void startContinuousMatching() {
        if (isMatchingActive) return;
        isMatchingActive = true;
        setupUIForScanning();

        matchingThread = new Thread(() -> {
            while (isMatchingActive) {
                updateStatus("Ready: Place eye on the scanner...", Color.parseColor("#334155"));

                int[] quality = new int[1];
                IrisAnatomy anatomy = new IrisAnatomy();

                // Blocks and waits for a capture (Timeout 10 seconds)
                int ret = bioManager.getSDK().AutoCapture(10000, quality, anatomy);

                if (!isMatchingActive) break; // User pressed stop/back while it was blocking

                if (ret == 0) {
                    updateStatus("Processing Match...", Color.parseColor("#EAB308")); // Yellow

                    try {
                        int expectedSize = bioManager.getLastDeviceInfo().Width * bioManager.getLastDeviceInfo().Height + 1078;

                        // The SDK PDF states 1:1 matching is done via BMP.
                        byte[] liveBmpBytes = new byte[expectedSize];
                        int[] bmpLen = new int[1];
                        bioManager.getSDK().GetImage(liveBmpBytes, bmpLen, 0, ImageFormat.BMP);

                        byte[] finalLiveTemplate = new byte[bmpLen[0]];
                        System.arraycopy(liveBmpBytes, 0, finalLiveTemplate, 0, bmpLen[0]);

                        // Traverse DB for Match
                        String matchedName = traverseAndMatch(finalLiveTemplate);

                        if (matchedName != null) {
                            updateStatus(" MATCH FOUND: " + matchedName, Color.parseColor("#22C55E")); // Green
                        } else {
                            updateStatus(" NO MATCH FOUND", Color.parseColor("#EF4444")); // Red
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        updateStatus("Error extracting live image.", Color.parseColor("#EF4444"));
                    }

                    // Pause to let user read the result before scanner turns back on
                    try { Thread.sleep(2500); } catch (InterruptedException ignored) {}

                } else {
                    // Timeout or Capture Fail. Let the while loop gracefully restart AutoCapture
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            }
            // Once the loop breaks (isMatchingActive becomes false), reset UI
            setupUIForIdle();
        });
        matchingThread.start();
    }

    private void stopMatchingLoop() {
        isMatchingActive = false;
        if (bioManager.getSDK() != null) {
            bioManager.getSDK().StopCapture(); // Forcefully unblock AutoCapture
        }
    }

    private String traverseAndMatch(byte[] liveTemplate) {
        Cursor cursor = dbHelper.getAllUsers();
        String matchedUserName = null;

        // Define minimum threshold score for a successful match
        int threshold = 70;

        if (cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") String name = cursor.getString(cursor.getColumnIndex(IrisDatabaseHelper.COL_NAME));
                // We use the BMP image for matching as per the PDF documentation overview
                @SuppressLint("Range") byte[] leftImage = cursor.getBlob(cursor.getColumnIndex(IrisDatabaseHelper.COL_LEFT_IMAGE));
                @SuppressLint("Range") byte[] rightImage = cursor.getBlob(cursor.getColumnIndex(IrisDatabaseHelper.COL_RIGHT_IMAGE));

                // Check Left Eye
                if (leftImage != null && leftImage.length > 0) {
                    int score = perform1to1Match(liveTemplate, leftImage);
                    if (score >= threshold) {
                        matchedUserName = name;
                        break;
                    }
                }

                // Check Right Eye
                if (rightImage != null && rightImage.length > 0) {
                    int score = perform1to1Match(liveTemplate, rightImage);
                    if (score >= threshold) {
                        matchedUserName = name;
                        break;
                    }
                }

            } while (cursor.moveToNext());
        }
        cursor.close();
        return matchedUserName;
    }

    private int perform1to1Match(byte[] liveBmpImage, byte[] storedBmpImage) {
        try {
            // TODO: Use Matching 1:1 function from MarvisAuth SDk.

            // return bioManager.getSDK().MatchIris(liveBmpImage, storedBmpImage);


            return 0; // Return 0 score until i find and uncomment the real function
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public void OnPreview(int errorCode, int quality, byte[] image, IrisAnatomy anatomy) {
        if (errorCode == 0 && image != null && isMatchingActive) {
            if (quality < 40) paint.setColor(Color.RED);
            else if (quality < 60) paint.setColor(Color.YELLOW);
            else paint.setColor(Color.GREEN);

            Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);

            if (anatomy != null && anatomy.irisR > 0) {
                canvas.drawCircle(anatomy.irisX, anatomy.irisY, anatomy.irisR, paint);
            }

            runOnUiThread(() -> {
                imgMatchPreview.setImageBitmap(mutableBitmap);
                updateStatus("Scanning... Quality: " + quality, Color.BLUE);
            });
        }
    }

    @Override
    public void OnComplete(int errorCode, int quality, byte[] image, IrisAnatomy anatomy) {
        // Handled synchronously by AutoCapture
    }

    @Override
    public void OnDeviceDetection(String deviceName, DeviceDetection detection) {
        if (detection == DeviceDetection.DISCONNECTED) {
            runOnUiThread(() -> Toast.makeText(this, "Device Disconnected", Toast.LENGTH_SHORT).show());
            stopMatchingLoop();
            finish(); // Directly falls back to Dashboard
        }
    }

    private void updateStatus(String msg, int color) {
        runOnUiThread(() -> {
            txtMatchStatus.setText(msg);
            txtMatchStatus.setTextColor(color);
        });
    }
}