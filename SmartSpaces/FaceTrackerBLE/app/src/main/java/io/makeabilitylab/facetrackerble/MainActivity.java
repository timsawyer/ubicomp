package io.makeabilitylab.facetrackerble;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import io.makeabilitylab.facetrackerble.ble.BLEDevice;
import io.makeabilitylab.facetrackerble.ble.BLEListener;
import io.makeabilitylab.facetrackerble.ble.BLEUtil;
import io.makeabilitylab.facetrackerble.camera.CameraSourcePreview;
import io.makeabilitylab.facetrackerble.camera.GraphicOverlay;

/**
 * Demonstrates how to use the Google Android Vision API--specifically the FaceDetector--along
 * with the RedBear Duo. The app detects faces in real-time and transmits left eye and right eye
 * state information (open probability) along with basic emotion inference (sad/happiness probability).
 *
 * It is based on:
 *   - The Google Code Lab tutorial: https://codelabs.developers.google.com/codelabs/face-detection/index.html#1
 *   - The Google Mobile Vision Face Tracker tutorial: https://developers.google.com/vision/android/face-tracker-tutorial
 *   - The FaceTracker demo: https://github.com/googlesamples/android-vision/tree/master/visionSamples/FaceTracker
 *   - The Googly Eyes demo: https://github.com/googlesamples/android-vision/tree/master/visionSamples/googly-eyes
 *   - The CSE590 BLE demo: https://github.com/jonfroehlich/CSE590Sp2018/tree/master/A03-BLEAdvanced
 *
 * Jon TODO:
 *  1. (Low priority) We shouldn't disconnect from BLE just because our orientation changed (e.g., from Portrait to Landscape). How to deal?
 */
public class MainActivity extends AppCompatActivity implements BLEListener{

    private ImageView screenshotView;
    private boolean screenshotAllowed = true;
    private Uri mImageFile;
    private float mCurrentFaceHappiness;

    private static final String TAG = "FaceTrackerBLE";
    private static final int RC_HANDLE_GMS = 9001;
    private static final int CAMERA_PREVIEW_WIDTH = 640;
    private static final int CAMERA_PREVIEW_HEIGHT = 480;

    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private MainActivity mContext;

    private boolean mIsFrontFacing = true;
    private boolean mFaceFound = false;

    // Bluetooth stuff
    private BLEDevice mBLEDevice;

    // TODO: Define your device name and the length of the name. For your assignment, do not use the
    // default name or you will not be able to discriminate your board from everyone else's board.
    // Note the device name and the length should be consistent with the ones defined in the Duo sketch
    private final String TARGET_BLE_DEVICE_NAME = "TimboLE";


    // moving average filter for smoothing the ultrasonic measurements
    private int mDistanceWindowSize = 5;
    ArrayList<Double> mDistancePoints = new ArrayList<Double>();
    private Double mDistanceMovingAverage;

    // moving average filter for smoothing the face tracking measurements
    private int mFaceTrackingWindowSize = 5;
    ArrayList<Double> mFaceTrackingPoints = new ArrayList<Double>();
    private Double mFaceTrackingMovingAverage;


    private double calculateAverage(ArrayList<Double> values) {
        Double sum = 0.0;
        if(!values.isEmpty()) {
            for (Double value : values) {
                sum += value;
            }
            return sum / values.size();
        }
        return sum;
    }


    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        mContext = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        screenshotView = (ImageView) findViewById(R.id.screenshotView);
        screenshotView.setVisibility(View.GONE);

        mPreview = (CameraSourcePreview) findViewById(R.id.cameraSourcePreview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        final Button button = (Button) findViewById(R.id.buttonFlip);
        button.setOnClickListener(mFlipButtonListener);


        final Button pictureButton = (Button) findViewById(R.id.buttonPicture);
        pictureButton.setOnClickListener(mPictureButtonListener);

        if (savedInstanceState != null) {
            mIsFrontFacing = savedInstanceState.getBoolean("IsFrontFacing");
        }

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }

        // Make sure that Bluetooth is supported.
        if (!BLEUtil.isSupported(this)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        // Make sure that we have required permissions.
        if (!BLEUtil.hasPermission(this)) {
            BLEUtil.requestPermission(this);
        }

        // Make sure that Bluetooth is enabled.
        if (!BLEUtil.isBluetoothEnabled(this)) {
            BLEUtil.requestEnableBluetooth(this);
        }

        mBLEDevice = new BLEDevice(this, TARGET_BLE_DEVICE_NAME);
        mBLEDevice.addListener(this);
        attemptBleConnection();
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        // Snackbars are like Toasts
        // See: https://stackoverflow.com/q/34432339
        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == BLEUtil.REQUEST_ENABLE_BLUETOOTH
                && !BLEUtil.isBluetoothEnabled(this)) {
            finish();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        int cameraFacing = mIsFrontFacing ? CameraSource.CAMERA_FACING_FRONT : CameraSource.CAMERA_FACING_BACK;

        Context context = getApplicationContext();

        // Setup the Google Vision FaceDetector:
        //   - https://developers.google.com/android/reference/com/google/android/gms/vision/face/FaceDetector
        // We use the detector in a pipeline structure in conjunction with a source (Camera)
        // and a processor (in this case, MultiProcessor.Builder<>(new FaceTrackerFactory()))
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        // This processor distributes the items of detection results among individual trackers
        // so that you can detect multiple faces.
        // See: https://developers.google.com/android/reference/com/google/android/gms/vision/MultiProcessor
        // MultiProcessor faceProcessor = new MultiProcessor.Builder<>(new FaceTrackerFactory()).build();

        // This processor only finds the largest face in the frame.
        LargestFaceFocusingProcessor faceProcessor = new LargestFaceFocusingProcessor(detector, new FaceTracker(mGraphicOverlay));

        // set the detector's processor
        detector.setProcessor(faceProcessor);

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        // The face detector can run with a fairly low resolution image (e.g., 320x240)
        // Running in lower images is significantly faster than higher resolution
        // We've currently set this to 640x480
        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT)
                .setFacing(cameraFacing)
                .setRequestedFps(30.0f)
                .build();
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();

        if (!BLEUtil.isBluetoothEnabled(this)) {
            BLEUtil.requestEnableBluetooth(this);
        }
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBLEDevice != null) {
            mBLEDevice.disconnect();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // Check user response to requesting bluetooth permissions
        if (requestCode == BLEUtil.REQUEST_BLUETOOTH_PERMISSIONS) {
            if(BLEUtil.hasPermission(this)){
                attemptBleConnection();
            }else{
                finish();
                return;
            }
        }

        // Check user response to requesting camera permissions
        if (requestCode == RC_HANDLE_CAMERA_PERM) {
            if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission granted - initialize the camera source");
                // we have permission, so create the camerasource
                createCameraSource();
                return;
            }

            Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                    " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    finish();
                }
            };

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Face Tracker BLE Demo")
                    .setMessage(R.string.no_camera_permission)
                    .setPositiveButton(R.string.ok, listener)
                    .show();
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    //==============================================================================================
    // UI
    //==============================================================================================

    /**
     * Saves the camera facing mode, so that it can be restored after the device is rotated.
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean("IsFrontFacing", mIsFrontFacing);
    }

    /**
     * Toggles between front-facing and rear-facing modes.
     */
    private View.OnClickListener mFlipButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            mIsFrontFacing = !mIsFrontFacing;

            if (mCameraSource != null) {
                mCameraSource.release();
                mCameraSource = null;
            }

            createCameraSource();
            startCameraSource();
        }
    };

    private View.OnClickListener mPictureButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            takeAndSavePicture();
        }
    };


    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class FaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new FaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class FaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        FaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        public int map(float x, double in_min, double in_max, double out_min, double out_max)
        {
            double val = (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
            return (int) val;
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceFound = true;
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);


            // CSE590 Student TODO:
            // Once a face is detected, you get lots of information about the face for free, including
            // a smiling probability score, eye shut probability, and the location of the face in the
            // camera image (note that we are using a small camera size to speedup processing:
            //   The dimensions are: CAMERA_PREVIEW_WIDTH x CAMERA_PREVIEW_HEIGHT
            //
            // You need to translate the location of the face into data that will be useful for your servo.
            // For example, this code would translate the X location of the face into a range from 0-255:
            //    (centerOfFace.x / CAMERA_PREVIEW_WIDTH) * 255
            // Since the servo motor can move in only one dimension, you only need to track one dimension of movement
            //
            // To properly calculate the location of the face, you may need to handle front facing vs. rear facing camera
            // and portrait vs. landscape phone modes properly.
            //
            // You can also turn on Landmark detection to get more information about the face like cheek, ear, mouth, etc.
            //   See: https://developers.google.com/android/reference/com/google/android/gms/vision/face/Landmark
            boolean isPortrait = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
            String debugFaceInfo = String.format("Portrait: %b Front-Facing Camera: %b FaceId: %d Loc (x,y): (%.1f, %.1f) Size (w, h): (%.1f, %.1f) Left Eye: %.1f Right Eye: %.1f  Smile: %.1f",
                    isPortrait,
                    mIsFrontFacing,
                    face.getId(),
                    face.getPosition().x, face.getPosition().y,
                    face.getHeight(), face.getWidth(),
                    face.getIsLeftEyeOpenProbability(), face.getIsRightEyeOpenProbability(),
                    face.getIsSmilingProbability());

//            Log.i(TAG, debugFaceInfo);

            // Come up with your own communication protocol to Arduino. Make sure that you change the
            // RECEIVE_MAX_LEN in your Arduino code to match the # of bytes you are sending.
            // For example, one protocol might be:
            // 0 : Control byte
            // 1 : left eye open probability (0-255 where 0 is eye closed and 1 is eye open)
            // 2 : right eye open probability (0-255 where 0 is eye closed and 1 is eye open)
            // 3 : happiness probability (0-255 where 0 sad, 128 is neutral, and 255 is happy)
            // 4 : x-location of face (0-255 where 0 is left side of camera and 255 is right side of camera)
            byte[] buf = new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00}; // 5-byte initialization

            // CSE590 Student TODO:
            // Write code that puts in your data into the buffer
            double leftEyeProb = face.getIsLeftEyeOpenProbability();
            double rightEyeProb = face.getIsRightEyeOpenProbability();

            if (face.getIsLeftEyeOpenProbability() > 0.9) {
                buf[1] = 0x01;
            }

            if (face.getIsRightEyeOpenProbability() > 0.9) {
                buf[2] = 0x01;
            }

            if (face.getIsSmilingProbability() > 0.5) {
                buf[3] = 0x01;
            }


            // calc center of face and, map to number between 0 and 255
            float centerOfFaceX = face.getPosition().x + (face.getWidth() / 2);
            int faceLocation = map(centerOfFaceX, 0, 500, 0, 255);

            mFaceTrackingPoints.add((double) faceLocation);
            mFaceTrackingMovingAverage = calculateAverage(mFaceTrackingPoints);

            if (mFaceTrackingPoints.size() > mFaceTrackingWindowSize) {
                mFaceTrackingPoints.remove(0);
            }

            buf[4] = (byte) Math.round(mFaceTrackingMovingAverage);

            // Send the data!
            mBLEDevice.sendData(buf);


            // save current face happiness for picture taking
            mCurrentFaceHappiness = face.getIsSmilingProbability();
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
            mFaceFound = false;
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }

    //==============================================================================================
    // Bluetooth stuff
    //==============================================================================================

    private void attemptBleConnection(){
        if(BLEUtil.hasPermission(this) &&
                BLEUtil.isBluetoothEnabled(this) &&
                mBLEDevice.getState() == BLEDevice.State.DISCONNECTED){
            String msg = "Attempting to connect to '" + TARGET_BLE_DEVICE_NAME + "'";
            Toast toast = Toast.makeText(
                    MainActivity.this,
                    msg,
                    Toast.LENGTH_LONG);
            toast.show();
            TextView textViewBleStatus = (TextView)findViewById(R.id.textViewBleStatus);
            textViewBleStatus.setText(msg);
            mBLEDevice.connect();
        }
    }

    @Override
    public void onBleConnected() {
        Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();

        TextView textViewBleStatus = (TextView)findViewById(R.id.textViewBleStatus);
        textViewBleStatus.setText("Connected to '" + TARGET_BLE_DEVICE_NAME + "'");
    }

    @Override
    public void onBleConnectFailed() {
        Toast toast = Toast
                .makeText(
                        MainActivity.this,
                        "Couldn't find the BLE device with name '" + TARGET_BLE_DEVICE_NAME + "'!",
                        Toast.LENGTH_SHORT);
        toast.setGravity(0, 0, Gravity.CENTER);
        toast.show();

        TextView textViewBleStatus = (TextView)findViewById(R.id.textViewBleStatus);
        textViewBleStatus.setText("BLE connection to '" + TARGET_BLE_DEVICE_NAME + "' failed");

        // Jon TODO: We should really pause here before trying to reconnect...
        // Have some sort of backoff
        attemptBleConnection();
    }

    @Override
    public void onBleDisconnected() {
        Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();

        TextView textViewBleStatus = (TextView)findViewById(R.id.textViewBleStatus);
        textViewBleStatus.setText("Disconnected from '" + TARGET_BLE_DEVICE_NAME + "'");

        // Jon TODO: We should really pause here before trying to reconnect...
        // Have some sort of backoff
        attemptBleConnection();
    }

    @Override
    public void onBleDataReceived(byte[] data) {
        // CSE590 Student TODO
        // Write code here that receives the ultrasonic measurements from Arduino
        // and outputs them in your app. (You could also consider receiving the angle
        // of the servo motor but this would be more for debugging and is not necessary)
        for (int i = 0; i < data.length; i += 3) {
            if (data[i] == 0x0A) {
                int rangeFinderValue;
                rangeFinderValue = ((data[i + 1] << 8) & 0x0000ff00)
                        | (data[i + 2] & 0x000000ff);

                double rangeInCentimeters = rangeFinderValue / 58;
                mDistancePoints.add(rangeInCentimeters);
                mDistanceMovingAverage = calculateAverage(mDistancePoints);

                if (mDistancePoints.size() > mDistanceWindowSize) {
                    mDistancePoints.remove(0);
                }

                TextView textViewBleStatus = (TextView)findViewById(R.id.rangeFromFace);

                if (mDistanceMovingAverage < 260 && mFaceFound) {
                    textViewBleStatus.setText("Face distance: " + Math.round(mDistanceMovingAverage) + "cm");
                }
                else {
                    textViewBleStatus.setText("Face distance: Unknown");
                }


                // if a face is close enough and is smiling, take a picture and store.
                if (screenshotAllowed && rangeInCentimeters < 40 && mCurrentFaceHappiness > 0.9) {
                    screenshotAllowed = false;

                    takeAndSavePicture();

                    // wait 10 seconds before allowing another picture
                    new CountDownTimer(10000, 1000) {
                        public void onTick(long millisUntilFinished) {}
                        public void onFinish() {
                            screenshotAllowed = true;
                        }
                    }.start();
                }

            }
        }
    }

    @Override
    public void onBleRssiChanged(int rssi) {
        // Not needed for this app
    }

    private void takeAndSavePicture() {
        mPreview.takePicture(null, new CameraSource.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data) {
                try {
                    File outputFile = getOutputMediaFile();
                    FileOutputStream outStream = new FileOutputStream(outputFile);
                    outStream.write(data);
                    outStream.close();

                    mImageFile = FileProvider.getUriForFile(mContext, "facetracker.authority", outputFile);
                    screenshotView.setImageURI(mImageFile);
                    screenshotView.setVisibility(View.VISIBLE);

                    // after 2.5 seconds hide the picture that we just took
                    new CountDownTimer(2500, 1000) {
                        public void onTick(long millisUntilFinished) {}
                        public void onFinish() {
                            screenshotView.setVisibility(View.GONE);
                        }
                    }.start();
                }
                catch(IOException e) {
                    System.out.println(e.toString());
                }
            }
        });
    }

    private static File getOutputMediaFile(){
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "FaceTrackerPictures");

        if (!mediaStorageDir.exists()){
            if (!mediaStorageDir.mkdirs()){
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(mediaStorageDir.getPath() + File.separator +
                "IMG_"+ timeStamp + ".jpg");
    }
}
