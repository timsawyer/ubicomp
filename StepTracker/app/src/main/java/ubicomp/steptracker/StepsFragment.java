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
import android.widget.Button;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import uk.me.berndporr.iirj.*;

import static android.os.Environment.getDataDirectory;

public class StepsFragment extends Fragment implements SensorEventListener {
    private final Handler mHandler = new Handler();
    private Runnable mTimer;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private Double mMagnitude;
    ArrayList<Double> mMagnitudePoints = new ArrayList<Double>();

    private Double mFilteredMagnitude;
    ArrayList<Double> mFilteredPoints = new ArrayList<Double>();

    private GraphView mGraph;
    private int mSeriesX;
    private LineGraphSeries<DataPoint> mSeries;
    private LineGraphSeries<DataPoint> mFilterSeries;
    private PointsGraphSeries<DataPoint> mPointSeries;

    ArrayList<Double> mStepPoints = new ArrayList<Double>();
    private TextView mStepCountText;

    final int mMaxDataPoints = 100;
    final double mMinY = 8.0;
    final double mMaxY = 12.0;
    final int mTimerDelay = 200;

    private final int mBufferSize = 100;
    private final double mC = 0.7;
    private final double mThreshold = 10.5;
    private int mStepCount;
    private int mPeakCount;
    private double mPeakAccumulate;
    private double mPeakMean;

    private Butterworth mButterworth = new Butterworth();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

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

        mSeriesX = 0;
        mStepCount = 0;
        mPeakCount = 0;
        mPeakAccumulate = 0.0;
        mPeakMean = 0.0;
    }

    @Override
    public void onResume() {
        super.onResume();
        mSeries.resetData(new DataPoint[]{});
        mFilterSeries.resetData(new DataPoint[]{});
        mSeriesX = 0;
        mButterworth.lowPass(20, 50, 5);

        mTimer = new Runnable() {
            @Override
            public void run() {
                mSeriesX += 1;

                mSeries.appendData(new DataPoint(mSeriesX, mMagnitude), true, mMaxDataPoints);
                mFilterSeries.appendData(new DataPoint(mSeriesX, mFilteredMagnitude), true, mMaxDataPoints);
                mStepCountText.setText(Integer.toString(mStepCount));

                for (Double stepPoint: mStepPoints) {
                    mPointSeries.appendData(new DataPoint(mSeriesX, stepPoint), true, mMaxDataPoints);
                }

                mStepPoints.clear();
                mHandler.postDelayed(this, mTimerDelay);
            }
        };
        mHandler.postDelayed(mTimer, mTimerDelay);
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        // magnitude^2 = x^2 + y^2 + z^2
        mMagnitude = Math.sqrt(Math.pow(event.values[0], 2.0) + Math.pow(event.values[1], 2.0) + Math.pow(event.values[2], 2.0));
//        mMagnitudePoints.add(mMagnitude);

        mFilteredMagnitude = mButterworth.filter(mMagnitude);
        mFilteredPoints.add(mFilteredMagnitude);

        // filter signal with moving average filter
        if (mFilteredPoints.size() == mBufferSize) {
            mPeakCount = 0;
            mPeakAccumulate = 0;

            for (int i = 1; i < mFilteredPoints.size()-1; i++) {
                double forwardSlope = mFilteredPoints.get(i+1) - mFilteredPoints.get(i);
                double backwardSlope = mFilteredPoints.get(i) - mFilteredPoints.get(i-1);

                if (forwardSlope < 0 && backwardSlope > 0) {
                    mPeakCount++;
                    mPeakAccumulate += mFilteredPoints.get(i);
                }
            }

            mPeakMean = mPeakAccumulate / mPeakCount;

            for (int i = 1; i < mFilteredPoints.size()-1; i++) {
                double forwardSlope = mFilteredPoints.get(i+1) - mFilteredPoints.get(i);
                double backwardSlope = mFilteredPoints.get(i) - mFilteredPoints.get(i-1);

                if (forwardSlope < 0 &&
                    backwardSlope > 0 &&
                    mFilteredPoints.get(i) > mC * mPeakMean &&
                    mFilteredPoints.get(i) > mThreshold
                ) {
                    mStepCount++;

                    // TODO: get step points to align with correct x value of peaks
                    mStepPoints.add(mFilteredPoints.get(i));
                }
            }

            mFilteredPoints.clear();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
