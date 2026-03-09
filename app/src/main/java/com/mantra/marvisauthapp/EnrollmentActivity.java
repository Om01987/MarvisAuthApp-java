package com.mantra.marvisauthapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mantra.marvisauth.IrisAnatomy;
import com.mantra.marvisauth.MarvisAuth_Callback;
import com.mantra.marvisauth.enums.DeviceDetection;
import com.mantra.marvisauth.enums.ImageFormat;

public class EnrollmentActivity extends AppCompatActivity implements MarvisAuth_Callback {

    private BiometricManager bioManager;
    private IrisDatabaseHelper dbHelper;

    private EditText edtUserName;
    private ImageView imgLeftEye, imgRightEye;
    private TextView txtEnrollStatus;
    private Button btnStopCapture, btnSaveUser;

    private boolean isCapturing = false;
    private int currentEye = -1; // 0 for Left, 1 for Right
    private Paint paint;

    // Temporary storage for the session
    private byte[] leftImgBmp, leftImgIso, rightImgBmp, rightImgIso;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enrollment);

        bioManager = BiometricManager.getInstance(this);
        dbHelper = new IrisDatabaseHelper(this);

        edtUserName = findViewById(R.id.edtUserName);
        imgLeftEye = findViewById(R.id.imgLeftEye);
        imgRightEye = findViewById(R.id.imgRightEye);
        txtEnrollStatus = findViewById(R.id.txtEnrollStatus);
        btnStopCapture = findViewById(R.id.btnStopCapture);
        btnSaveUser = findViewById(R.id.btnSaveUser);

        // Setup Paint for drawing Iris Anatomy circle
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setAntiAlias(true);

        imgLeftEye.setOnClickListener(v -> startCaptureForEye(0));
        imgRightEye.setOnClickListener(v -> startCaptureForEye(1));

        btnStopCapture.setOnClickListener(v -> {
            if (isCapturing) {
                bioManager.getSDK().StopCapture();
                isCapturing = false;
                updateStatus("Capture stopped manually", Color.RED);
                resetUIState();
            }
        });

        btnSaveUser.setOnClickListener(v -> saveUserToDb());
    }

    @Override
    protected void onResume() {
        super.onResume();
        bioManager.setListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isCapturing) {
            bioManager.getSDK().StopCapture();
            isCapturing = false;
        }
        bioManager.removeListener();
    }

    private void startCaptureForEye(int eyePosition) {
        if (!bioManager.isReady()) {
            Toast.makeText(this, "Device not ready!", Toast.LENGTH_SHORT).show();
            return;
        }

        currentEye = eyePosition;
        isCapturing = true;

        imgLeftEye.setEnabled(false);
        imgRightEye.setEnabled(false);
        btnStopCapture.setEnabled(true);
        btnSaveUser.setEnabled(false);

        // Reset the active ImageView to a neutral color during preview
        if (currentEye == 0) imgLeftEye.setBackgroundColor(Color.parseColor("#E2E8F0"));
        if (currentEye == 1) imgRightEye.setBackgroundColor(Color.parseColor("#E2E8F0"));

        updateStatus((eyePosition == 0 ? "LEFT" : "RIGHT") + " EYE: Place eye on scanner...", Color.BLUE);

        int ret = bioManager.getSDK().StartCapture(10000, 60);
        if (ret != 0) {
            isCapturing = false;
            updateStatus("Capture Failed to Start: " + ret, Color.RED);
            resetUIState();
        }
    }

    @Override
    public void OnPreview(int errorCode, int quality, byte[] image, IrisAnatomy anatomy) {
        if (errorCode == 0 && image != null) {
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
                // Target the specific image view that was clicked
                if (currentEye == 0) {
                    imgLeftEye.setImageBitmap(mutableBitmap);
                } else if (currentEye == 1) {
                    imgRightEye.setImageBitmap(mutableBitmap);
                }
                updateStatus("Scanning... Quality: " + quality, Color.BLUE);
            });
        }
    }

    @Override
    public void OnComplete(int errorCode, int quality, byte[] image, IrisAnatomy anatomy) {
        isCapturing = false;
        if (errorCode == 0) {
            updateStatus("Capture Success! Quality: " + quality, Color.GREEN);
            extractImagesFromSDK();
        } else {
            updateStatus("Capture Failed or Timeout: " + bioManager.getSDK().GetErrorMessage(errorCode), Color.RED);
            resetUIState();
        }
    }

    private void extractImagesFromSDK() {
        new Thread(() -> {
            try {
                int expectedSize = bioManager.getLastDeviceInfo().Width * bioManager.getLastDeviceInfo().Height + 1078;

                byte[] bmpBytes = new byte[expectedSize];
                int[] bmpLen = new int[1];
                bioManager.getSDK().GetImage(bmpBytes, bmpLen, 0, ImageFormat.BMP);

                byte[] isoBytes = new byte[expectedSize];
                int[] isoLen = new int[1];
                bioManager.getSDK().GetImage(isoBytes, isoLen, 10, ImageFormat.IIR_K1);

                byte[] finalBmp = new byte[bmpLen[0]];
                System.arraycopy(bmpBytes, 0, finalBmp, 0, bmpLen[0]);

                byte[] finalIso = new byte[isoLen[0]];
                System.arraycopy(isoBytes, 0, finalIso, 0, isoLen[0]);

                if (currentEye == 0) {
                    leftImgBmp = finalBmp;
                    leftImgIso = finalIso;
                } else {
                    rightImgBmp = finalBmp;
                    rightImgIso = finalIso;
                }

                runOnUiThread(() -> {
                    Bitmap bmp = BitmapFactory.decodeByteArray(finalBmp, 0, finalBmp.length);

                    if (currentEye == 0) {
                        imgLeftEye.setImageBitmap(bmp);
                        imgLeftEye.setBackgroundColor(Color.parseColor("#22C55E")); // Green border
                    } else if (currentEye == 1) {
                        imgRightEye.setImageBitmap(bmp);
                        imgRightEye.setBackgroundColor(Color.parseColor("#22C55E")); // Green border
                    }

                    resetUIState();
                    btnSaveUser.setEnabled(leftImgIso != null || rightImgIso != null);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> updateStatus("Error extracting image data", Color.RED));
                resetUIState();
            }
        }).start();
    }

    private void saveUserToDb() {
        String name = edtUserName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a user name", Toast.LENGTH_SHORT).show();
            return;
        }

        long id = dbHelper.insertUser(name, leftImgBmp, leftImgIso, rightImgBmp, rightImgIso);
        if (id != -1) {
            Toast.makeText(this, "User Enrolled Successfully! ID: " + id, Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, "Database Error", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetUIState() {
        runOnUiThread(() -> {
            imgLeftEye.setEnabled(true);
            imgRightEye.setEnabled(true);
            btnStopCapture.setEnabled(false);
            btnSaveUser.setEnabled(leftImgIso != null || rightImgIso != null);
        });
    }

    private void updateStatus(String msg, int color) {
        runOnUiThread(() -> {
            txtEnrollStatus.setText(msg);
            txtEnrollStatus.setTextColor(color);
        });
    }

    @Override
    public void OnDeviceDetection(String deviceName, DeviceDetection detection) {
        if (detection == DeviceDetection.DISCONNECTED) {
            isCapturing = false;
            finish();
        }
    }
}