package ubicomp.steptracker;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;

public class DebugFragment extends Fragment implements SensorEventListener {
    private int mStepCount;

    private final Filter filter = new Filter();

    private final Handler mHandler = new Handler();
    private Runnable mTimer;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private double mSeriesX;
    private double mLastestSensorX;
    private double mLasestSensorY;
    private double mLatestSensorZ;

    private GraphView mDebugGraphX;
    private LineGraphSeries<DataPoint> mDebugSeriesX;
    private DataPoint[] mDebugDataPointsX;

    private GraphView mDebugGraphY;
    private LineGraphSeries<DataPoint> mDebugSeriesY;
    private DataPoint[] mDebugDataPointsY;

    private GraphView mDebugGraphZ;
    private LineGraphSeries<DataPoint> mDebugSeriesZ;
    private DataPoint[] mDebugDataPointsZ;

    private TextView mDebugStepText;

    final int mMaxDataPoints = 60;
    final int mMinY = -8;
    final int mMaxY = 8;
    final int mTimerDelay = 50;

    ArrayList<Double> x = new ArrayList<>();
    ArrayList<Double> y = new ArrayList<>();
    ArrayList<Double> z = new ArrayList<>();

    ArrayList<Double> xg = new ArrayList<>();
    ArrayList<Double> yg = new ArrayList<>();
    ArrayList<Double> zg = new ArrayList<>();

    ArrayList<Double> xu = new ArrayList<>();
    ArrayList<Double> yu = new ArrayList<>();
    ArrayList<Double> zu = new ArrayList<>();

    Double mDotProduct;
    ArrayList<Double> mDotProductPoints = new ArrayList<>();

    Double mDotProductLowPassFilter;
    ArrayList<Double> mDotProductLowPassFilterPoints = new ArrayList<>();

    Double mDotProductHighPassFilter;
    ArrayList<Double> mDotProductHighPassFilterPoints = new ArrayList<>();

    private Double mThreshold = 0.09;
    private boolean mZeroCrossing = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.debug_fragment, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        mStepCount = 0;

        // step text
        mDebugStepText = getActivity().findViewById(R.id.debug_step_text);

        // x chart
        mDebugGraphX = getActivity().findViewById(R.id.debug_graph_x);
        mDebugSeriesX = new LineGraphSeries<>();
        mDebugGraphX.addSeries(mDebugSeriesX);

        mDebugGraphX.getViewport().setMinX(0);
        mDebugGraphX.getViewport().setMaxX(mMaxDataPoints);
        mDebugGraphX.getViewport().setMinY(mMinY);
        mDebugGraphX.getViewport().setMaxY(mMaxY);
        mDebugGraphX.getViewport().setYAxisBoundsManual(true);
        mDebugGraphX.getViewport().setXAxisBoundsManual(true);

//        mDebugSeriesX.setTitle("Linear Accelerometer: X");
//        mDebugGraphX.getLegendRenderer().setVisible(true);
//        mDebugGraphX.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);

        // y chart
//        mDebugGraphY = getActivity().findViewById(R.id.debug_graph_y);
//        mDebugSeriesY = new LineGraphSeries<>();
//        mDebugGraphY.addSeries(mDebugSeriesY);
//        mDebugGraphY.getViewport().setMinX(0);
//        mDebugGraphY.getViewport().setMaxX(mMaxDataPoints);
//        mDebugGraphY.getViewport().setMinY(mMinY);
//        mDebugGraphY.getViewport().setMaxY(mMaxY);
//        mDebugGraphY.getViewport().setYAxisBoundsManual(true);
//        mDebugGraphY.getViewport().setXAxisBoundsManual(true);
//
//        mDebugSeriesY.setTitle("Linear Accelerometer: Y");
//        mDebugGraphY.getLegendRenderer().setVisible(true);
//        mDebugGraphY.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);


        // z chart
//        mDebugGraphZ = getActivity().findViewById(R.id.debug_graph_z);
//        mDebugSeriesZ = new LineGraphSeries<>();
//        mDebugGraphZ.addSeries(mDebugSeriesZ);
//        mDebugGraphZ.getViewport().setMinX(0);
//        mDebugGraphZ.getViewport().setMaxX(mMaxDataPoints);
//        mDebugGraphZ.getViewport().setMinY(mMinY);
//        mDebugGraphZ.getViewport().setMaxY(mMaxY);
//        mDebugGraphZ.getViewport().setYAxisBoundsManual(true);
//        mDebugGraphZ.getViewport().setXAxisBoundsManual(true);
//
//        mDebugSeriesZ.setTitle("Linear Accelerometer: Z");
//        mDebugGraphZ.getLegendRenderer().setVisible(true);
//        mDebugGraphZ.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);


        mSeriesX = 0;
        mLastestSensorX = 0;
        mLasestSensorY = 0;
        mLatestSensorZ = 0;

        mDotProduct = 0.0;
        mDotProductHighPassFilter = 0.0;

        xg.add(0.0); xg.add(0.0);
        yg.add(0.0); yg.add(0.0);
        zg.add(0.0); zg.add(0.0);

        mDotProductLowPassFilterPoints.add(0.0);
        mDotProductLowPassFilterPoints.add(0.0);

        mDotProductHighPassFilterPoints.add(0.0);
        mDotProductHighPassFilterPoints.add(0.0);
    }

    @Override
    public void onResume() {
        super.onResume();
        mDebugSeriesX.resetData(new DataPoint[]{});
//        mDebugSeriesY.resetData(new DataPoint[]{});
//        mDebugSeriesZ.resetData(new DataPoint[]{});

        mTimer = new Runnable() {
            @Override
            public void run() {
            mSeriesX += 1;
            mDebugSeriesX.appendData(new DataPoint(mSeriesX, mDotProductHighPassFilter), true, mMaxDataPoints);
//            mDebugSeriesX.appendData(new DataPoint(mSeriesX, xg.get(xg.size()-1)), true, mMaxDataPoints);
//            mDebugSeriesY.appendData(new DataPoint(mSeriesX, yg.get(yg.size()-1)), true, mMaxDataPoints);
//            mDebugSeriesZ.appendData(new DataPoint(mSeriesX, zg.get(zg.size()-1)), true, mMaxDataPoints);

            mHandler.postDelayed(this, mTimerDelay);
            }
        };
        mHandler.postDelayed(mTimer, mTimerDelay);
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        double newX = (double) event.values[0];
        double newY = (double) event.values[1];
        double newZ = (double) event.values[2];

        x.add(newX);
        y.add(newY);
        z.add(newZ);

        if (x.size() == 3) {

            // calc gravity acceleration
            double newXg = filter.filterLowPass0Hz(x, xg);
            double newYg = filter.filterLowPass0Hz(y, yg);
            double newZg = filter.filterLowPass0Hz(z, zg);
            xg.add(newXg);
            yg.add(newYg);
            zg.add(newZg);

            // calc user acceleration
            double newXu = newX - newXg;
            double newYu = newY - newYg;
            double newZu = newZ - newZg;
            xu.add(newXu);
            yu.add(newYu);
            zu.add(newZu);

            // calc dot product
            mDotProduct = filter.dotProduct(newXu, newXg, newYu, newYg, newZu, newZg);
            mDotProductPoints.add(mDotProduct);

            // filter dot product low pass
            mDotProductLowPassFilter = filter.filterLowPass5Hz(mDotProductPoints, mDotProductLowPassFilterPoints);
            mDotProductLowPassFilterPoints.add(mDotProductLowPassFilter);

            // filter dot product high pass
            mDotProductHighPassFilter = filter.filterHighPass1Hz(mDotProductLowPassFilterPoints, mDotProductHighPassFilterPoints);
            mDotProductHighPassFilterPoints.add(mDotProductHighPassFilter);

            if (mDotProductHighPassFilter > mThreshold && mZeroCrossing) {
                mStepCount++;
                mZeroCrossing = false;
                mDebugStepText.setText(Integer.toString(mStepCount));
            }

            if (mDotProductHighPassFilter < 0) {
                mZeroCrossing = true;
            }

            x.remove(0);
            y.remove(0);
            z.remove(0);
            xg.remove(0);
            yg.remove(0);
            zg.remove(0);

            mDotProductPoints.remove(0);
            mDotProductLowPassFilterPoints.remove(0);
            mDotProductHighPassFilterPoints.remove(0);
        }
        else {
            xu.add(newX);
            yu.add(newY);
            zu.add(newZ);

            mDotProduct = filter.dotProduct(newX, 0.0, newY, 0.0, newZ, 0.0);
            mDotProductPoints.add(mDotProduct);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
