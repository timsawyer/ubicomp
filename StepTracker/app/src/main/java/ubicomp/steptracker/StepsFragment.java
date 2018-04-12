package ubicomp.steptracker;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;
import java.util.ArrayList;
import uk.me.berndporr.iirj.*;


public class StepsFragment extends Fragment implements SensorEventListener {
    private final Handler mHandler = new Handler();
    private Runnable mTimer;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mNativeStepSensor;

    private Double mMagnitude;
    ArrayList<Double> mMagnitudePoints = new ArrayList<Double>();

    private Double mFilteredMagnitude;
    ArrayList<Double> mFilteredPoints = new ArrayList<Double>();

    private GraphView mGraph;
    private int mSeriesX;
    private LineGraphSeries<DataPoint> mSeries;
    private LineGraphSeries<DataPoint> mFilterSeries;
    private PointsGraphSeries<DataPoint> mPointSeries;

    ArrayList<Double> mDrawnMagnitudePoints = new ArrayList<>();
    ArrayList<Double> mDrawnFilteredPoints = new ArrayList<>();
    ArrayList<Double> mDrawnStepPoints = new ArrayList<>();

    ArrayList<DrawPoint> mDrawPoints = new ArrayList<>();
    private TextView mStepCountText;
    private TextView mNativeStepCountText;

    final int mMaxDataPoints = 150;
    final double mMinY = -1.0;
    final double mMaxY = 8.0;
    final int mTimerDelay = 150;

    private final double mThreshold = 0.1;
    private int mStepCount;
    private boolean mZeroCrossing = false;
    private double mMovingAverage;
    private int mMovingAverageWindowSize = 40;

    private Butterworth mButterworth = new Butterworth();

    private SensorEventListener mNativeStepListener;
    private Float mNativeStepInitialValue;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        // setup native step counter
        mNativeStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        mNativeStepListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                onNativeStepSensorChanged(event);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.steps_fragment, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        // chart
        mGraph = getActivity().findViewById(R.id.steps_graph);
        mSeries = new LineGraphSeries<>();
        mFilterSeries = new LineGraphSeries<>();
        mFilterSeries.setColor(Color.GREEN);
        mGraph.addSeries(mSeries);
        mGraph.addSeries(mFilterSeries);

        // point series to show when step logged
        mPointSeries = new PointsGraphSeries<>();
        mGraph.addSeries(mPointSeries);
        mPointSeries.setShape(PointsGraphSeries.Shape.TRIANGLE);
        mPointSeries.setColor(Color.RED);
        mPointSeries.setSize(20);

        mGraph.getViewport().setMinX(0);
        mGraph.getViewport().setMaxX(mMaxDataPoints);
        mGraph.getViewport().setMinY(mMinY);
        mGraph.getViewport().setMaxY(mMaxY);
        mGraph.getViewport().setYAxisBoundsManual(true);
        mGraph.getViewport().setXAxisBoundsManual(true);

        mStepCountText = getActivity().findViewById(R.id.steps_count_text);
        mNativeStepCountText = getActivity().findViewById(R.id.native_steps_count_text);

        mSeriesX = 0;
        mStepCount = 0;
        mFilteredMagnitude = 0.0;
        mMagnitude = 0.0;
        mMovingAverage = 0.0;
    }

    @Override
    public void onResume() {
        super.onResume();
        mSeries.resetData(new DataPoint[]{});
        mFilterSeries.resetData(new DataPoint[]{});
        mSeriesX = 0;
        mFilteredMagnitude = 0.0;
        mMagnitude = 0.0;
        mDrawnStepPoints.clear();
        mDrawnFilteredPoints.clear();
        mDrawnMagnitudePoints.clear();
        mButterworth.lowPass(20, 50, 5);

        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mNativeStepListener, mNativeStepSensor, SensorManager.SENSOR_DELAY_UI);

        mTimer = new Runnable() {
            @Override
            public void run() {
                // draw points
                for (DrawPoint dp: mDrawPoints) {
                    mSeries.appendData(new DataPoint(mSeriesX, dp.magnitude), true, mMaxDataPoints);
                    mFilterSeries.appendData(new DataPoint(mSeriesX, dp.filteredMagnitude), true, mMaxDataPoints);

                    if (dp.stepPoint) {
                        mPointSeries.appendData(new DataPoint(mSeriesX, dp.filteredMagnitude), true, mMaxDataPoints);
                    }

                    mSeriesX++;
                }
                mDrawPoints.clear();

                mStepCountText.setText(Integer.toString(mStepCount));
                mHandler.postDelayed(this, mTimerDelay);
            }
        };
        mHandler.postDelayed(mTimer, mTimerDelay);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        mSensorManager.unregisterListener(mNativeStepListener);
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

                // if peak above threshold
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
            int stepCount = Math.round(event.values[0] - mNativeStepInitialValue);
            mNativeStepCountText.setText(String.valueOf(stepCount));
        }

    }

    private double calculateAverage(ArrayList <Double> values) {
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
