package ubicomp.steptracker;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.util.ArrayList;

public class DebugFragment extends Fragment {

    private StepTrackerUtil mStepTrackerUtil;

    private final Handler mHandler = new Handler();
    private Runnable mTimer;

    private double mDebugSeriesX;

    private GraphView mDebugGraph;
    private LineGraphSeries<DataPoint> mDebugSeries;
    private LineGraphSeries<DataPoint> mFilterSeries;
    private PointsGraphSeries<DataPoint> mPointSeries;

    private TextView mStepText;
    private TextView mNativeStepText;

    final int mMaxDataPoints = 60;
    final int mMinY = -8;
    final int mMaxY = 8;
    final int mTimerDelay = 100;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.debug_fragment, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        mStepText = getActivity().findViewById(R.id.debug_steps_count_text);
        mNativeStepText = getActivity().findViewById(R.id.debug_native_steps_count_text);

        // chart
        mDebugGraph = getActivity().findViewById(R.id.debug_graph);

        // magnitude series
        mDebugSeries = new LineGraphSeries<>();

        // filtered value series
        mFilterSeries = new LineGraphSeries<>();
        mFilterSeries.setColor(Color.GREEN);

        // step indicator series
        mPointSeries = new PointsGraphSeries<>();
        mPointSeries.setShape(PointsGraphSeries.Shape.TRIANGLE);
        mPointSeries.setColor(Color.RED);
        mPointSeries.setSize(20);

        // add series to chart
        mDebugGraph.addSeries(mDebugSeries);
        mDebugGraph.addSeries(mFilterSeries);
        mDebugGraph.addSeries(mPointSeries);

        // chart config
        mDebugGraph.getViewport().setMinX(0);
        mDebugGraph.getViewport().setMaxX(mMaxDataPoints);
        mDebugGraph.getViewport().setMinY(mMinY);
        mDebugGraph.getViewport().setMaxY(mMaxY);
        mDebugGraph.getViewport().setYAxisBoundsManual(true);
        mDebugGraph.getViewport().setXAxisBoundsManual(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        mStepTrackerUtil = new StepTrackerUtil(getActivity());
        mDebugSeries.resetData(new DataPoint[]{});

        mTimer = new Runnable() {
            @Override
            public void run() {
                mStepText.setText(String.valueOf(mStepTrackerUtil.getStepCount()));
                mNativeStepText.setText(String.valueOf(mStepTrackerUtil.getNativeStepCount()));

                ArrayList<DrawPoint> drawPoints = mStepTrackerUtil.getDrawPoints();

                for (DrawPoint dp: drawPoints) {
                    mDebugSeries.appendData(new DataPoint(mDebugSeriesX, dp.magnitude), true, mMaxDataPoints);
                    mFilterSeries.appendData(new DataPoint(mDebugSeriesX, dp.filteredMagnitude), true, mMaxDataPoints);

                    if (dp.stepPoint) {
                        mPointSeries.appendData(new DataPoint(mDebugSeriesX, dp.filteredMagnitude), true, mMaxDataPoints);
                    }

                    mDebugSeriesX++;
                }
                mStepTrackerUtil.clearDrawPoints();
                mHandler.postDelayed(this, mTimerDelay);
            }
        };
        mHandler.postDelayed(mTimer, mTimerDelay);
    }
}
