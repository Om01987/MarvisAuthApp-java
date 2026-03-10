package com.mantra.marvisauthapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.mantra.marvisauth.DeviceInfo;
import com.mantra.marvisauth.IrisAnatomy;
import com.mantra.marvisauth.MarvisAuth;
import com.mantra.marvisauth.MarvisAuth_Callback;
import com.mantra.marvisauth.enums.DeviceDetection;
import com.mantra.marvisauth.enums.DeviceModel;
import com.mantra.marvisauth.enums.ImageFormat;
import com.mantra.marvisauth.enums.LogLevel;

import java.util.ArrayList;
import java.util.List;

public class BiometricManager implements MarvisAuth_Callback {

    private static BiometricManager instance;
    private MarvisAuth marvisAuth;
    private MarvisAuth_Callback activeListener;

    private boolean isDeviceConnected = false;
    private boolean isDeviceInitialized = false;
    private DeviceModel currentModel = DeviceModel.MIS100V2;
    private DeviceInfo lastDeviceInfo;

    public static synchronized BiometricManager getInstance(Context context) {
        if (instance == null) {
            instance = new BiometricManager(context.getApplicationContext());
        }
        return instance;
    }

    private BiometricManager(Context context) {
        try {
            marvisAuth = new MarvisAuth(context, this);
            String logPath = context.getExternalFilesDir(null).getAbsolutePath() + "/marvis_log.txt";
            marvisAuth.SetLogProperties(logPath, LogLevel.ERROR);

            // Catch devices plugged in before the app launched
            if (marvisAuth.IsDeviceConnected(DeviceModel.MIS100V2)) {
                isDeviceConnected = true;
                currentModel = DeviceModel.MIS100V2;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setListener(MarvisAuth_Callback listener) {
        this.activeListener = listener;

        // Instantly notify the dashboard of the live hardware state
        new Handler(Looper.getMainLooper()).post(() -> {
            if (activeListener != null) {
                if (isDeviceConnected) {
                    activeListener.OnDeviceDetection(currentModel.name(), DeviceDetection.CONNECTED);
                } else {
                    activeListener.OnDeviceDetection("Device", DeviceDetection.DISCONNECTED);
                }
            }
        });
    }

    public void removeListener() {
        this.activeListener = null;
    }

    public int initDevice() {
        if (marvisAuth == null) return -1;
        if (isDeviceInitialized) return 0;

        DeviceInfo info = new DeviceInfo();
        int ret = marvisAuth.Init(currentModel, info);
        if (ret == 0) {
            isDeviceInitialized = true;
            lastDeviceInfo = info;
        } else {
            isDeviceInitialized = false;
            lastDeviceInfo = null;
        }
        return ret;
    }

    public void uninitDevice() {
        if (marvisAuth != null && isDeviceInitialized) {
            try {
                marvisAuth.Uninit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        isDeviceInitialized = false;
        lastDeviceInfo = null;
    }

    public int startCapture(int timeOut, int minQuality) {
        if (marvisAuth != null && isDeviceInitialized) {
            return marvisAuth.StartCapture(timeOut, minQuality);
        }
        return -1;
    }

    public int autoCapture(int timeOut, int[] quality, IrisAnatomy anatomy) {
        if (marvisAuth != null && isDeviceInitialized) {
            return marvisAuth.AutoCapture(timeOut, quality, anatomy);
        }
        return -1;
    }

    public int stopCapture() {
        if (marvisAuth != null) {
            return marvisAuth.StopCapture();
        }
        return -1;
    }

    public int getImage(byte[] outImage, int[] outLen, int compressionRatio, ImageFormat format) {
        if (marvisAuth != null && isDeviceInitialized) {
            return marvisAuth.GetImage(outImage, outLen, compressionRatio, format);
        }
        return -1;
    }

    public String getErrorMessage(int errorCode) {
        if (marvisAuth != null) {
            return marvisAuth.GetErrorMessage(errorCode);
        }
        return "SDK not initialized";
    }

    public String getSDKVersion() {
        if (marvisAuth != null) {
            return marvisAuth.GetSDKVersion();
        }
        return "N/A";
    }

    public String[] getConnectedDevices() {
        if (marvisAuth != null) {
            List<String> deviceList = new ArrayList<>();
            int ret = marvisAuth.GetConnectedDevices(deviceList);
            if (ret == 0) {
                return deviceList.toArray(new String[0]);
            }
        }
        return new String[0];
    }

    public void dispose() {
        if (marvisAuth != null) {
            try {
                marvisAuth.Dispose();
            } catch (Exception e) {
                e.printStackTrace();
            }
            marvisAuth = null;
            isDeviceInitialized = false;
            isDeviceConnected = false;
            instance = null;
        }
    }

    public MarvisAuth getSDK() { return marvisAuth; }

    // Rely strictly on the listener flag for stability during fast hot-swaps
    public boolean isConnected() { return isDeviceConnected; }
    public boolean isReady() { return isConnected() && isDeviceInitialized; }
    public DeviceInfo getLastDeviceInfo() { return lastDeviceInfo; }
    public DeviceModel getConnectedModel() { return currentModel; }

    @Override
    public void OnDeviceDetection(String deviceName, DeviceDetection detection) {
        if (detection == DeviceDetection.CONNECTED) {
            isDeviceConnected = true;
            try {
                currentModel = DeviceModel.valueOf(deviceName);
            } catch (Exception e) {
                currentModel = DeviceModel.MIS100V2; // Safe Fallback
            }
        } else {
            isDeviceConnected = false;
            isDeviceInitialized = false;
            currentModel = DeviceModel.MIS100V2;
            lastDeviceInfo = null;

            // CRITICAL: SDK must Uninit on physical disconnect to reset its internal listener
            if (marvisAuth != null) {
                try {
                    marvisAuth.Uninit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Push updates strictly to the Main Thread so the UI never crashes
        new Handler(Looper.getMainLooper()).post(() -> {
            if (activeListener != null) {
                activeListener.OnDeviceDetection(deviceName != null ? deviceName : "MIS100V2", detection);
            }
        });
    }

    @Override
    public void OnPreview(int errorCode, int quality, byte[] image, IrisAnatomy anatomy) {
        if (activeListener != null) {
            activeListener.OnPreview(errorCode, quality, image, anatomy);
        }
    }

    @Override
    public void OnComplete(int errorCode, int quality, byte[] image, IrisAnatomy anatomy) {
        if (activeListener != null) {
            activeListener.OnComplete(errorCode, quality, image, anatomy);
        }
    }
}