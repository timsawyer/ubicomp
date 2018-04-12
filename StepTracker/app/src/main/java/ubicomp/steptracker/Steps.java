package ubicomp.steptracker;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

public class Steps extends AppCompatActivity implements SensorEventListener {
    final FragmentManager fragmentManager = getSupportFragmentManager();

    final Fragment stepsFragment = new StepsFragment();
    final Fragment nativeStepsFragment = new NativeStepsFragment();
    final Fragment debugFragment = new DebugFragment();

    private SensorManager mSensorManager;
    private Sensor mCountSensor;

    boolean mAppRunning = false;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_steps:
                    FragmentTransaction stepsFragmentTransaction = fragmentManager.beginTransaction();
                    stepsFragmentTransaction.replace(R.id.fragment_container, stepsFragment).commit();

                    return true;
                case R.id.navigation_debug:
                    FragmentTransaction debugFragmentTransaction = fragmentManager.beginTransaction();
                    debugFragmentTransaction.replace(R.id.fragment_container, debugFragment).commit();
                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_steps);

        // show steps fragment
//        FragmentTransaction stepsFragmentTransaction = fragmentManager.beginTransaction();
//        stepsFragmentTransaction.replace(R.id.fragment_container, stepsFragment).commit();

        FragmentTransaction stepsFragmentTransaction = fragmentManager.beginTransaction();
        stepsFragmentTransaction.replace(R.id.fragment_container, debugFragment).commit();

        // register listener for bottom nav
        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAppRunning = true;

        mCountSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (mCountSensor != null) {
            mSensorManager.registerListener(this, mCountSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mAppRunning = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (mAppRunning) {
//            mNativeStepText.setText(String.valueOf(event.values[0]));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
