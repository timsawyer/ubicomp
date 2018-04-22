package ubicomp.steptracker;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.util.ArrayList;
import uk.me.berndporr.iirj.Butterworth;

public class StepTrackerUtil implements SensorEventListener {

    private Activity mActivity;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mNativeStepSensor;

    private Double mMagnitude;
    ArrayList<Double> mMagnitudePoints = new ArrayList<Double>();

    private Double mFilteredMagnitude;
    ArrayList<Double> mFilteredPoints = new ArrayList<Double>();

    ArrayList<DrawPoint> mDrawPoints = new ArrayList<DrawPoint>();

    private final double mThreshold = 0.1;
    private int mStepCount = 0;
    private int mNativeStepCount = 0;
    private boolean mZeroCrossing = false;
    private double mMovingAverage = 0;
    private int mMovingAverageWindowSize = 40;

    // Butteworth filter library from https://github.com/berndporr/iirj
    private Butterworth mButterworth = new Butterworth();

    private SensorEventListener mNativeStepListener;
    private Float mNativeStepInitialValue;

    public StepTrackerUtil(Activity activity) {
        mActivity = activity;
        mSensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);

        mNativeStepListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                onNativeStepSensorChanged(event);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mNativeStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mNativeStepListener, mNativeStepSensor, SensorManager.SENSOR_DELAY_UI);
    }

    public int getNativeStepCount() {
        return mNativeStepCount;
    }

    public int getStepCount() {
        return mStepCount;
    }

    public ArrayList<DrawPoint> getDrawPoints() {
        return mDrawPoints;
    }

    public void clearDrawPoints() {
        mDrawPoints.clear();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // magnitude^2 = x^2 + y^2 + z^2
        double magnitude = Math.sqrt(Math.pow(event.values[0], 2.0) + Math.pow(event.values[1], 2.0) + Math.pow(event.values[2], 2.0));
        mMagnitudePoints.add(magnitude);

        if (mMagnitudePoints.size() >= mMovingAverageWindowSize) {
            mMovingAverage = calculateAverage(mMagnitudePoints);
            double filteredMagnitude = mButterworth.filter(magnitude - mMovingAverage);
            mFilteredPoints.add(filteredMagnitude);

            if (mFilteredPoints.size() == 3) {
                mMagnitude = mMagnitudePoints.get(1);
                mFilteredMagnitude = mFilteredPoints.get(1);

                DrawPoint dp = new DrawPoint();
                dp.magnitude = mMagnitude;
                dp.filteredMagnitude = mFilteredMagnitude;
                dp.stepPoint = false;

                double forwardSlope = mFilteredPoints.get(2) - mFilteredMagnitude;
                double backwardSlope = mFilteredMagnitude - mFilteredPoints.get(0);

                // if peak and above threshold and has been a zero crossing since last peak
                if (forwardSlope < 0 &&
                        backwardSlope > 0 &&
                        mFilteredPoints.get(1) > mThreshold &&
                        mZeroCrossing ) {

                    mStepCount++;
                    mZeroCrossing = false;

                    dp.stepPoint = true;
                }

                if (mFilteredMagnitude < 0) {
                    mZeroCrossing = true;
                }

                mDrawPoints.add(dp);

                mMagnitudePoints.remove(0);
                mFilteredPoints.remove(0);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void onNativeStepSensorChanged(SensorEvent event) {
        if (mNativeStepInitialValue == null) {
            mNativeStepInitialValue = event.values[0];
        }
        else {
            mNativeStepCount = Math.round(event.values[0] - mNativeStepInitialValue);
        }
    }

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
}
