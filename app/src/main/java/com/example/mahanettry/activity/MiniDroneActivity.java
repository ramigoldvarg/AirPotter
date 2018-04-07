package com.example.mahanettry.activity;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.example.mahanettry.R;
import com.example.mahanettry.drone.MiniDrone;

public class MiniDroneActivity extends AppCompatActivity {
    private static final String TAG = "MiniDroneActivity";
    private MiniDrone mMiniDrone;

    private ProgressDialog mConnectionProgressDialog;
    private ProgressDialog mDownloadProgressDialog;

    private TextView mBatteryLabel;
    private Button mTakeOffLandBt;
    private Button mDownloadBt;

    private int mNbMaxDownload;
    private int mCurrentDownloadIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_minidrone);

        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);

        initIHM();

        Intent intent = getIntent();
        ARDiscoveryDeviceService service = intent.getParcelableExtra(DeviceListActivity.EXTRA_DEVICE_SERVICE);
        mMiniDrone = new MiniDrone(this, service);
        mMiniDrone.addListener(mMiniDroneListener);

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

        /*
        findViewById(R.id.lightsOnBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mMiniDrone.lightsOn();
            }
        });

        findViewById(R.id.lightsOffBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mMiniDrone.lightsOff();
            }
        });

        findViewById(R.id.lightPartyBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mMiniDrone.lightParty();
            }
        });

        findViewById(R.id.sendText).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                    try {
                        String input = ((TextView)findViewById(R.id.letterTextView)).getText().toString();
                        Toast.makeText(MiniDroneActivity.this, "draw " + input, Toast.LENGTH_LONG).show();
                        mMiniDrone.sendLetterBt(input);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
            }
        });
*/

        EditText editText = (EditText)findViewById(R.id.letterTextView);
        editText.setImeActionLabel("שגר!", EditorInfo.IME_ACTION_DONE);

        editText.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    String input = ((EditText)v.findViewById((R.id.letterTextView))).getText().toString();

                    try {
                        String error = mMiniDrone.drawInput(input);

                        if (error != null) {
                            Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getApplicationContext(), "ציור \' " + input + '\'', Toast.LENGTH_LONG).show();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return true;
                }

                return false;
            }
        });


        Switch lightsSwitch = ((Switch)findViewById(R.id.switch_the_lights));
        lightsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mMiniDrone.lightsOn();
                } else {
                    mMiniDrone.lightsOff();
                }
            }
        });

        findViewById(R.id.sendTaf).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawTaf();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendVav).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawVav();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendBet).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawBet();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendLamed).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawLamed();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendKaf).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawKaf();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendKuf).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawKuf();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendYud).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawYud();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendHey).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawHey();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });


        findViewById(R.id.sendShin).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawShin();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendTet).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawTet();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendTzadik).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawTzadik();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendNun).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawNun();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendReish).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawReish();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendDaled).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawDaled();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendMem).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawMem();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendPey).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawPey();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendZain).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawZain();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendGimel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawGimel();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendAin).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawAin();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });




        findViewById(R.id.sendSpace).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.drawSpace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.sendToval).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    mMiniDrone.sendToval();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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
                        break;
                    default:
                }
            }
        });

        findViewById(R.id.takePictureBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mMiniDrone.takePicture();
            }
        });

        mDownloadBt = (Button)findViewById(R.id.downloadBt);
        mDownloadBt.setEnabled(false);
        mDownloadBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mMiniDrone.getLastFlightMedias();

                mDownloadProgressDialog = new ProgressDialog(MiniDroneActivity.this, R.style.AppCompatAlertDialogStyle);
                mDownloadProgressDialog.setIndeterminate(true);
                mDownloadProgressDialog.setMessage("Fetching medias");
                mDownloadProgressDialog.setCancelable(false);
                mDownloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mMiniDrone.cancelGetLastFlightMedias();
                    }
                });
                mDownloadProgressDialog.show();
            }
        });

        findViewById(R.id.gazUpBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setGaz((byte) 50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setGaz((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.gazDownBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setGaz((byte) -50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setGaz((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.yawLeftBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setYaw((byte) -50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setYaw((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.yawRightBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setYaw((byte) 50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setYaw((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.forwardBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setPitch((byte) 50);
                        mMiniDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setPitch((byte) 0);
                        mMiniDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.backBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setPitch((byte) -50);
                        mMiniDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setPitch((byte) 0);
                        mMiniDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.rollLeftBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setRoll((byte) -50);
                        mMiniDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setRoll((byte) 0);
                        mMiniDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.rollRightBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mMiniDrone.setRoll((byte) 50);
                        mMiniDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mMiniDrone.setRoll((byte) 0);
                        mMiniDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        mBatteryLabel = (TextView) findViewById(R.id.batteryLabel);
    }

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
            mBatteryLabel.setText(String.format("%d%%", batteryPercentage));
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    mTakeOffLandBt.setText("Take off");
                    mTakeOffLandBt.setEnabled(true);
                    mDownloadBt.setEnabled(true);
                    break;
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    mTakeOffLandBt.setText("Land");
                    mTakeOffLandBt.setEnabled(true);
                    mDownloadBt.setEnabled(false);
                    break;
                default:
                    mTakeOffLandBt.setEnabled(false);
                    mDownloadBt.setEnabled(false);
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
}
