package ubicomp.steptracker;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.RippleDrawable;

public class StepsFragment extends Fragment {
    private final Handler mHandler = new Handler();
    private Runnable mTimer;
    private TextView mStepCountText;
    private StepTrackerUtil mStepTrackerUtil;
    private final int mTimerDelay = 100;
    private int mStepCount;

    private LinearLayout mLinearLayout;
    private RippleDrawable mRippleDrawable;
    private Button mRippleButton;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private boolean mRippleInEffect;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.steps_fragment, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        DisplayMetrics displayMetrics = getActivity().getResources().getDisplayMetrics();
        mDisplayWidth  = displayMetrics.widthPixels;
        mDisplayHeight = displayMetrics.heightPixels;

        int color = Color.parseColor("#ff252525");
        int pressed = Color.parseColor("#88ffffff");
        int mask = Color.parseColor("#ff252525");

        ColorDrawable defaultColor = new ColorDrawable(color);
        ColorDrawable maskColor = new ColorDrawable(mask);

        mRippleDrawable = new RippleDrawable(getColorStateList(color, pressed), defaultColor, null);
//        mRippleDrawable.setRadius(800);

        mRippleButton = (Button) getActivity().findViewById(R.id.ripple_button);
        mRippleButton.setBackground(mRippleDrawable);

        mStepCountText = getActivity().findViewById(R.id.steps_count_text);

        mLinearLayout = getActivity().findViewById(R.id.steps_count_layout);
        mLinearLayout.setZ(300);

        mRippleInEffect = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        mStepCount = 0;
        mStepTrackerUtil = new StepTrackerUtil(getActivity());

        mTimer = new Runnable() {
            @Override
            public void run() {
                int stepCount = mStepTrackerUtil.getStepCount();

                if (stepCount > mStepCount) {
                    mStepCount = stepCount;
                    mStepCountText.setText(String.valueOf(mStepCount));
                    showRippleAnimation();
                }

                mHandler.postDelayed(this, mTimerDelay);
            }
        };
        mHandler.postDelayed(mTimer, mTimerDelay);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void showRippleAnimation() {
        if (!mRippleInEffect) {
            mRippleDrawable.setHotspot(mDisplayWidth/2, mDisplayHeight/2);
            mRippleButton.setPressed(true);
            mRippleInEffect = true;

            Runnable timer = new Runnable() {
                @Override
                public void run() {
                    mRippleButton.setPressed(false);
                    mRippleInEffect = false;
                }
            };
            mHandler.postDelayed(timer, 400);
        }
    }

    public ColorStateList getColorStateList(int normalColor, int pressedColor)
    {
        return new ColorStateList(
            new int[][]
                {
                    new int[]{android.R.attr.state_pressed},
                    new int[]{android.R.attr.state_focused},
                    new int[]{android.R.attr.state_activated},
                    new int[]{}
                },
            new int[]
                {
                    pressedColor,
                    pressedColor,
                    pressedColor,
                    normalColor
                }
            );
    }
}
