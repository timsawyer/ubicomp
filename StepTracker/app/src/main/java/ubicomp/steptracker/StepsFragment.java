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

import static android.os.Environment.getDataDirectory;

public class StepsFragment extends Fragment implements SensorEventListener {
    private final Handler mHandler = new Handler();
    private Runnable mTimer;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;


    private Double mMagnitude;
    private Double mMovingAverage;
    private Double mAverage;
    private Double mStandardDeviation;

    private int mMAWindowSize = 50;
    private int mLag = 5;
    private double mThreshold = 5.0;
    private double mInfluence = 1.0;

    private GraphView mGraph;
    private int mSeriesX;
    private LineGraphSeries<DataPoint> mSeries;
    private LineGraphSeries<DataPoint> mMASeries;
    private PointsGraphSeries<DataPoint> mPointSeries;

    ArrayList<Double> mMagnitudePoints = new ArrayList<Double>();
    ArrayList<Double> mFilteredPoints = new ArrayList<Double>();
    ArrayList<Double> mStdDevPoints = new ArrayList<Double>();
    ArrayList<Double> mMAPoints = new ArrayList<Double>();

    final int mMaxDataPoints = 60;
    final double mMinY = 0.0;
    final double mMaxY = 6.0;
    final int mTimerDelay = 100;

    private int mStepCount;
    private TextView mStepCountText;


    private TextView mFilenameInput;
    private boolean mPipeToFile = false;
    private String mFilename;
    private FileOutputStream mFileOutputStream;
    private File mFile;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

//        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);


        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.steps_fragment, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

//        mFilenameInput = getActivity().findViewById(R.id.filenameText);

        // x chart
        mGraph = getActivity().findViewById(R.id.steps_graph);
        mSeries = new LineGraphSeries<>();
        mMASeries = new LineGraphSeries<>();
        mMASeries.setColor(Color.GREEN);
        mGraph.addSeries(mSeries);
        mGraph.addSeries(mMASeries);

        // point series to show when step logged
        mPointSeries = new PointsGraphSeries<>();
        mGraph.addSeries(mPointSeries);
        mPointSeries.setShape(PointsGraphSeries.Shape.TRIANGLE);
        mPointSeries.setColor(Color.RED);
        mPointSeries.setSize(20);

        mGraph.getViewport().setMinX(0);
        mGraph.getViewport().setMaxX(mMaxDataPoints);
//        mGraph.getViewport().setMinY(mMinY);
//        mGraph.getViewport().setMaxY(mMaxY);
//        mGraph.getViewport().setYAxisBoundsManual(true);
        mGraph.getViewport().setXAxisBoundsManual(true);

        mStepCountText = getActivity().findViewById(R.id.steps_count_text);

        mSeriesX = 0;
        mStepCount = 0;

//        ArrayList <Double> values = new ArrayList <Double>(Arrays.asList(1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0));
//        double stdDev = calculateStandardDeviation(values);
//        double i = stdDev;

        // button listeners
//        final Button start_button = getActivity().findViewById(R.id.start_button);
//        start_button.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                startClicked();
//            }
//        });
//
//        final Button stop_button = getActivity().findViewById(R.id.stop_button);
//        stop_button.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                stopClicked();
//            }
//        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mSeries.resetData(new DataPoint[]{});

        mTimer = new Runnable() {
            @Override
            public void run() {
                mSeriesX += 1;
                mSeries.appendData(new DataPoint(mSeriesX, mMagnitude), true, mMaxDataPoints);
                mMASeries.appendData(new DataPoint(mSeriesX, mMovingAverage), true, mMaxDataPoints);
                mHandler.postDelayed(this, mTimerDelay);

                mStepCountText.setText(Integer.toString(mStepCount));
            }
        };
        mHandler.postDelayed(mTimer, mTimerDelay);
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        // magnitude^2 = x^2 + y^2 + z^2
        mMagnitude = Math.sqrt(Math.pow(event.values[0], 2.0) + Math.pow(event.values[1], 2.0) + Math.pow(event.values[2], 2.0));
        mMagnitudePoints.add(mMagnitude);

        // filter signal with moving average filter
        if (mMagnitudePoints.size() >= mMAWindowSize) {
            mMovingAverage = calculateAverage(mMagnitudePoints);
            mMAPoints.add(mMovingAverage);
            mMagnitudePoints.remove(0);

            // peak detection algorithm from: https://stackoverflow.com/questions/22583391/peak-signal-detection-in-realtime-timeseries-data/22640362#22640362
            if (mFilteredPoints.size() == mLag) {
                // if peak
                if (Math.abs(mMovingAverage - mAverage) > (mThreshold * mStandardDeviation)) {
                    // if positive peak
                    if (mMovingAverage > mAverage) {
                        mStepCount++;
                        mPointSeries.appendData(new DataPoint(mSeriesX, mMovingAverage), true, mMaxDataPoints);
                    }

                    // set filteredY(i) to influence*y(i) + (1-influence)*filteredY(i-1);
                    Double filteredMag = (mInfluence * mMovingAverage) + (1 - mInfluence) * mFilteredPoints.get(mFilteredPoints.size()-1);
                    mFilteredPoints.add(filteredMag);
                }
                else {
                    mFilteredPoints.add(mMovingAverage);
                }

                mFilteredPoints.remove(0);
                mMAPoints.remove(0);

                mAverage = calculateAverage(mFilteredPoints);
                mStandardDeviation = calculateStandardDeviation(mFilteredPoints);
            }
            else {
                mFilteredPoints.add(mMovingAverage);
                mAverage = calculateAverage(mFilteredPoints);
                mStandardDeviation = calculateStandardDeviation(mFilteredPoints);
            }
        }




//
//        if (mMAPoints.size() > mMAWindowSize) {
//            mMAPoints.remove(0);
//            mStdDevPoints.remove(0);

//            if absolute(y(i) - avgFilter(i-1)) > threshold*stdFilter(i-1) then
//            if y(i) > avgFilter(i-1) then
//            set signals(i) to +1;
//            int lastPoint = mMAPoints.size() - 1;
//            if (Math.abs(mMagnitude - mMAPoints.get(lastPoint)) > (mThreshold * mStdDevPoints.get(lastPoint)) &&
//                mMagnitude > mMAPoints.get(lastPoint)) {
//                mStepCount++;
//                mPointSeries.appendData(new DataPoint(mSeriesX + 1, mMagnitude), true, mMaxDataPoints);
//            }
//        }
//
//        if (mPipeToFile) {
//            String output = event.values[0] + "," + event.values[1] + "," + event.values[2] + "," + mMagnitude + "\n";
//
//            try {
//                mFileOutputStream.write(output.getBytes());
//            }
//            catch(IOException e) {}
//        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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

    private double calculateStandardDeviation(ArrayList <Double> values) {
        Double average = calculateAverage(values);
        Double stdDev = 0.0;

        if(!values.isEmpty()) {
            for (int i = 0; i < values.size(); i++)
            {
                stdDev += Math.pow((values.get(i) - average),2);
            }
            return Math.sqrt(stdDev / values.size());

        }
        return stdDev;
    }

//    public void startClicked() {
//        mFilename = mFilenameInput.getText().toString();
//        mFile = new File(getActivity().getFilesDir(), mFilename);
//
//        try {
//            mFileOutputStream = new FileOutputStream(mFile);
//        }
//        catch (FileNotFoundException e) {}
//
//        mPipeToFile = true;
//    }
//
//    public void stopClicked() {
//        try {
//            mFileOutputStream.close();
//        }
//        catch (IOException e) {}
//
//        mPipeToFile = false;
//    }

}
