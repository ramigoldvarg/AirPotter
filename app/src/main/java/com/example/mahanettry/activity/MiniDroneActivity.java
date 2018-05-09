package com.example.mahanettry.activity;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.test.mock.MockContentProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.erz.joysticklibrary.JoyStick;
import com.example.mahanettry.drone.DroneDecorator;
import com.example.mahanettry.drone.Movement;
import com.example.mahanettry.drone.MovementRecorder;
import com.example.mahanettry.drone.SpellReader;
import com.example.mahanettry.drone.Spells;
import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.example.mahanettry.R;
import com.example.mahanettry.drone.MiniDrone;

import java.util.ArrayList;
import java.util.Date;

import at.grabner.circleprogress.CircleProgressView;

public class MiniDroneActivity extends AppCompatActivity implements JoyStick.JoyStickListener {
    private static final String TAG = "MiniDroneActivity";
    private MiniDrone mMiniDrone;
    private DroneDecorator spellDrone;

    private Spells spells;
    MovementRecorder mvRecorder;
    Movement currMovement;

    private final int REQ_CODE_SPEECH_INPUT = 100;
    private TextView txtSpeechInput;
    private ImageView btnSpeak;

    private ProgressDialog mConnectionProgressDialog;
    private ProgressDialog mDownloadProgressDialog;

    private CircleProgressView mBatteryView;
    private Button mTakeOffLandBt;
    private JoyStick rollJoystick;
    private JoyStick yawJoystick;


    private int mNbMaxDownload;
    private int mCurrentDownloadIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_minidrone);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        initIHM();


        this.spells = new Spells(new SpellReader(R.raw.spells, this));
        mvRecorder = new MovementRecorder();

        Intent intent = getIntent();
        ARDiscoveryDeviceService service = intent.getParcelableExtra(DeviceListActivity.EXTRA_DEVICE_SERVICE);
        mMiniDrone = new MiniDrone(this, service);
        spellDrone = new DroneDecorator(mMiniDrone);

        mMiniDrone.addListener(mMiniDroneListener);

        txtSpeechInput = (TextView) findViewById(R.id.txtSpeechInput);
        btnSpeak = (ImageView) findViewById(R.id.btnSpeak);
        btnSpeak.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                promptSpeechInput();
            }
        });
    }

    /**
     * Showing google speech input dialog
     * */
    private void promptSpeechInput() {
        spellDrone.stopCurrentMove();

        if (currMovement != null) {
            currMovement.setEndTime((new Date()).getTime());
            txtSpeechInput.setText("");
            mvRecorder.addMovement(currMovement);
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Receiving speech input
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    txtSpeechInput.setText("");
                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                    if (result.get(0).toLowerCase().equals(spells.getTakeOffSpell())) {
                        spellDrone.takeoff();
                    } else if(result.get(0).toLowerCase().equals(spells.getLandSpell())) {
                        mvRecorder.reset();
                        spellDrone.land();
                    } else if (result.get(0).toLowerCase().equals(spells.getRetraceSpell())) {
                        spellDrone.preformTrack(mvRecorder.retraceTrack());
                        mvRecorder.reset();
                    } else if (result.get(0).toLowerCase().equals(spells.getShoot())) {
                        mMiniDrone.shoot();
                    } else if (result.get(0).toLowerCase().equals(spells.getDance())) {
                        mMiniDrone.flip();
                    } else {
                        try {
                            currMovement = new Movement(new Date().getTime(), spells.getSpell(result.get(0).toLowerCase()));

                            spellDrone.startNewMovement(spells.getSpell(result.get(0).toLowerCase()));
                        } catch (Exception ex) {
                            Toast.makeText(getApplicationContext(),
                                    result.get(0),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
                break;
            }

        }
    }


    @Override
    protected void onStart() {
        super.onStart();

        // show a loading view while the minidrone is connecting
        if ((mMiniDrone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mMiniDrone.getConnectionState())))
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Connecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            // if the connection to the MiniDrone fails, finish the activity
            if (!mMiniDrone.connect()) {
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mMiniDrone != null)
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Disconnecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            if (!mMiniDrone.disconnect()) {
                finish();
            }
        } else {
            finish();
        }
    }

    @Override
    public void onDestroy()
    {
        mMiniDrone.dispose();
        super.onDestroy();
    }

    private void initIHM() {

        findViewById(R.id.emergencyBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mMiniDrone.emergency();
            }
        });

        mTakeOffLandBt = (Button) findViewById(R.id.takeOffOrLandBt);
        mTakeOffLandBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (mMiniDrone.getFlyingState()) {
                    case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                        mMiniDrone.takeOff();
                        break;
                    case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                    case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                        mMiniDrone.land();
                        mvRecorder.reset();
                        break;
                    default:
                }
            }
        });

        rollJoystick = (JoyStick) findViewById(R.id.rollJoystick);
        rollJoystick.setListener(this);

        yawJoystick = (JoyStick) findViewById(R.id.yawJoystick);
        yawJoystick.setListener(this);

        findViewById(R.id.takePictureBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mMiniDrone.takePicture();
            }
        });

        mBatteryView = (CircleProgressView) findViewById(R.id.batteryView);
        mBatteryView.setTextColor(Color.parseColor("#00cfc6"));
    }

    //region Drone Joystick Movement
    @Override
    public void onMove(JoyStick joyStick, double angle, double power, int direction) {
        int posX = -(int) (Math.cos(angle) * power);
        int posY = -(int) (Math.sin(-angle) * power);

        switch (joyStick.getId()) {
            case R.id.rollJoystick:
                rollYAxis(posY);
                rollXAxis(posX);

                break;
            case R.id.yawJoystick:
                yawXAxis(posX);
                yawYAxis(posY);

                break;
        }
    }

    public void rollYAxis(int value) {
        mMiniDrone.setPitch((byte) value);
        mMiniDrone.setFlag((byte) 1);
    }

    public void rollXAxis(int value) {
        mMiniDrone.setRoll((byte) value);
        mMiniDrone.setFlag((byte) 1);
    }

    public void yawYAxis(int value) {
        mMiniDrone.setGaz((byte) value);
    }

    public void yawXAxis(int value) {
        mMiniDrone.setYaw((byte) value);
    }
    //endregion

    @Override
    public void onTap() {}

    @Override
    public void onDoubleTap() {}

    private final MiniDrone.Listener mMiniDroneListener = new MiniDrone.Listener() {
        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state)
            {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    mConnectionProgressDialog.dismiss();
                    break;

                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    // if the deviceController is stopped, go back to the previous activity
                    mConnectionProgressDialog.dismiss();
                    finish();
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onBatteryChargeChanged(int batteryPercentage) {
            if (batteryPercentage > 60) {
                mBatteryView.setBarColor(Color.parseColor("#26EE99"));
            } else if (batteryPercentage > 30) {
                mBatteryView.setBarColor(Color.parseColor("#fef65b"));
            } else {
                mBatteryView.setBarColor(Color.parseColor("#e60000"));
            }

            mBatteryView.setValue(batteryPercentage);
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    mTakeOffLandBt.setBackgroundResource(R.drawable.ic_flight_takeoff);
                    mTakeOffLandBt.setEnabled(true);

                    break;
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    mTakeOffLandBt.setBackgroundResource(R.drawable.ic_flight_land);
                    mTakeOffLandBt.setEnabled(true);

                    break;
                default:
                    mTakeOffLandBt.setEnabled(false);

            }
        }

        @Override
        public void onPictureTaken(ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Log.i(TAG, "Picture has been taken");
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {
            mDownloadProgressDialog.dismiss();

            mNbMaxDownload = nbMedias;
            mCurrentDownloadIndex = 1;

            if (nbMedias > 0) {
                mDownloadProgressDialog = new ProgressDialog(MiniDroneActivity.this, R.style.AppCompatAlertDialogStyle);
                mDownloadProgressDialog.setIndeterminate(false);
                mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mDownloadProgressDialog.setMessage("Downloading medias");
                mDownloadProgressDialog.setMax(mNbMaxDownload * 100);
                mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);
                mDownloadProgressDialog.setProgress(0);
                mDownloadProgressDialog.setCancelable(false);
                mDownloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mMiniDrone.cancelGetLastFlightMedias();
                    }
                });
                mDownloadProgressDialog.show();
            }
        }

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {
            mDownloadProgressDialog.setProgress(((mCurrentDownloadIndex - 1) * 100) + progress);
        }

        @Override
        public void onDownloadComplete(String mediaName) {
            mCurrentDownloadIndex++;
            mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);

            if (mCurrentDownloadIndex > mNbMaxDownload) {
                mDownloadProgressDialog.dismiss();
                mDownloadProgressDialog = null;
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_info:
                LayoutInflater inflater = getLayoutInflater();
                View dialoglayout = inflater.inflate(R.layout.instructions, null);
                dialoglayout.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setView(dialoglayout);
                builder.setTitle("Instructions");
                builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });
                builder.show();

                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
