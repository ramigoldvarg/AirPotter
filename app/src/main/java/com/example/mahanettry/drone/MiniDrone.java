package com.example.mahanettry.drone;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerArgumentDictionary;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARControllerException;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.arcontroller.ARFeatureCommon;
import com.parrot.arsdk.arcontroller.ARFeatureMiniDrone;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_FAMILY_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDevice;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryException;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.parrot.arsdk.arutils.ARUTILS_DESTINATION_ENUM;
import com.parrot.arsdk.arutils.ARUTILS_FTP_TYPE_ENUM;
import com.parrot.arsdk.arutils.ARUtilsException;
import com.parrot.arsdk.arutils.ARUtilsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiniDrone {
    private static final String TAG = "MiniDrone";

    public static int nHigh = 0;

    public interface Listener {
        /**
         * Called when the connection to the drone changes
         * Called in the main thread
         *
         * @param state the state of the drone
         */
        void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state);

        /**
         * Called when the battery charge changes
         * Called in the main thread
         *
         * @param batteryPercentage the battery remaining (in percent)
         */
        void onBatteryChargeChanged(int batteryPercentage);

        /**
         * Called when the piloting state changes
         * Called in the main thread
         *
         * @param state the piloting state of the drone
         */
        void onPilotingStateChanged(ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state);

        /**
         * Called when a picture is taken
         * Called on a separate thread
         *
         * @param error ERROR_OK if picture has been taken, otherwise describe the error
         */
        void onPictureTaken(ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error);

        /**
         * Called before medias will be downloaded
         * Called in the main thread
         *
         * @param nbMedias the number of medias that will be downloaded
         */
        void onMatchingMediasFound(int nbMedias);

        /**
         * Called each time the progress of a download changes
         * Called in the main thread
         *
         * @param mediaName the name of the media
         * @param progress  the progress of its download (from 0 to 100)
         */
        void onDownloadProgressed(String mediaName, int progress);

        /**
         * Called when a media download has ended
         * Called in the main thread
         *
         * @param mediaName the name of the media
         */
        void onDownloadComplete(String mediaName);
    }

    private final List<Listener> mListeners;

    private final Handler mHandler;

    private final Context mContext;

    private ARDeviceController mDeviceController;
    private SDCardModule mSDCardModule;
    private ARCONTROLLER_DEVICE_STATE_ENUM mState;
    private ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM mFlyingState;
    private String mCurrentRunId;
    private ARDISCOVERY_PRODUCT_ENUM mProductType;
    private ARDiscoveryDeviceService mDeviceService;

    private ARUtilsManager mFtpListManager;
    private ARUtilsManager mFtpQueueManager;

    public MiniDrone(Context context, @NonNull ARDiscoveryDeviceService deviceService) {

        mContext = context;
        mDeviceService = deviceService;
        mListeners = new ArrayList<>();

        // needed because some callbacks will be called on the main thread
        mHandler = new Handler(context.getMainLooper());

        mState = ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED;

        // if the product type of the deviceService match with the types supported
        mProductType = ARDiscoveryService.getProductFromProductID(deviceService.getProductID());
        ARDISCOVERY_PRODUCT_FAMILY_ENUM family = ARDiscoveryService.getProductFamily(mProductType);
        if (ARDISCOVERY_PRODUCT_FAMILY_ENUM.ARDISCOVERY_PRODUCT_FAMILY_MINIDRONE.equals(family)) {

            ARDiscoveryDevice discoveryDevice = createDiscoveryDevice(deviceService);
            if (discoveryDevice != null) {
                mDeviceController = createDeviceController(discoveryDevice);
                discoveryDevice.dispose();
            }

        } else {
            Log.e(TAG, "DeviceService type is not supported by MiniDrone");
        }
    }

    public void dispose() {
        if (mDeviceController != null)
            mDeviceController.dispose();
    }

    //region Listener functions
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }
    //endregion Listener

    /**
     * Connect to the drone
     *
     * @return true if operation was successful.
     * Returning true doesn't mean that device is connected.
     * You can be informed of the actual connection through {@link Listener#onDroneConnectionChanged}
     */
    public boolean connect() {
        boolean success = false;
        if ((mDeviceController != null) && (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED.equals(mState))) {
            ARCONTROLLER_ERROR_ENUM error = mDeviceController.start();
            if (error == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                success = true;
            }
        }
        return success;
    }

    /**
     * Disconnect from the drone
     *
     * @return true if operation was successful.
     * Returning true doesn't mean that device is disconnected.
     * You can be informed of the actual disconnection through {@link Listener#onDroneConnectionChanged}
     */
    public boolean disconnect() {
        boolean success = false;
        if ((mDeviceController != null) && (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mState))) {
            ARCONTROLLER_ERROR_ENUM error = mDeviceController.stop();
            if (error == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                success = true;
            }
        }
        return success;
    }

    /**
     * Get the current connection state
     *
     * @return the connection state of the drone
     */
    public ARCONTROLLER_DEVICE_STATE_ENUM getConnectionState() {
        return mState;
    }

    /**
     * Get the current flying state
     *
     * @return the flying state
     */
    public ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM getFlyingState() {
        return mFlyingState;
    }

    public void takeOff() {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureMiniDrone().sendPilotingTakeOff();
        }
    }

    public void land() {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureMiniDrone().sendPilotingLanding();
        }
    }

    public void emergency() {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureMiniDrone().sendPilotingEmergency();
        }
    }

    public void takePicture() {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            // RollingSpider (not evo) are still using old deprecated command
            if (ARDISCOVERY_PRODUCT_ENUM.ARDISCOVERY_PRODUCT_MINIDRONE.equals(mProductType)) {
                mDeviceController.getFeatureMiniDrone().sendMediaRecordPicture((byte) 0);
            } else {
                mDeviceController.getFeatureMiniDrone().sendMediaRecordPictureV2();
            }
        }
    }

    /**
     * Set the forward/backward angle of the drone
     * Note that {@link MiniDrone#setFlag(byte)} should be set to 1 in order to take in account the pitch value
     *
     * @param pitch value in percentage from -100 to 100
     */
    public void setPitch(byte pitch) {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureMiniDrone().setPilotingPCMDPitch(pitch);
        }
    }

    /**
     * Set the side angle of the drone
     * Note that {@link MiniDrone#setFlag(byte)} should be set to 1 in order to take in account the roll value
     *
     * @param roll value in percentage from -100 to 100
     */
    public void setRoll(byte roll) {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll(roll);
        }
    }

    public void setYaw(byte yaw) {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureMiniDrone().setPilotingPCMDYaw(yaw);
        }
    }

    public void setGaz(byte gaz) {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz(gaz);
        }
    }

    /**
     * Take in account or not the pitch and roll values
     *
     * @param flag 1 if the pitch and roll values should be used, 0 otherwise
     */
    public void setFlag(byte flag) {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureMiniDrone().setPilotingPCMDFlag(flag);
        }
    }

    /**
     * Download the last flight medias
     * Uses the run id to download all medias related to the last flight
     * If no run id is available, download all medias of the day
     */
    public void getLastFlightMedias() {
        try {
            if (mFtpListManager == null) {
                mFtpListManager = new ARUtilsManager();
                mFtpListManager.initFtp(mContext, mDeviceService, ARUTILS_DESTINATION_ENUM.ARUTILS_DESTINATION_DRONE, ARUTILS_FTP_TYPE_ENUM.ARUTILS_FTP_TYPE_GENERIC);
            }
            if (mFtpQueueManager == null) {
                mFtpQueueManager = new ARUtilsManager();
                mFtpQueueManager.initFtp(mContext, mDeviceService, ARUTILS_DESTINATION_ENUM.ARUTILS_DESTINATION_DRONE, ARUTILS_FTP_TYPE_ENUM.ARUTILS_FTP_TYPE_GENERIC);
            }
            if (mSDCardModule == null) {
                mSDCardModule = new SDCardModule(mFtpListManager, mFtpQueueManager);
                mSDCardModule.addListener(mSDCardModuleListener);
            }
        } catch (ARUtilsException e) {
            Log.e(TAG, "Exception", e);
        }

        String runId = mCurrentRunId;
        if ((runId != null) && !runId.isEmpty()) {
            mSDCardModule.getFlightMedias(runId);
        } else {
            Log.e(TAG, "RunID not available, fallback to the day's medias");
            mSDCardModule.getTodaysFlightMedias();
        }
    }

    public void cancelGetLastFlightMedias() {
        if (mSDCardModule != null) {
            mSDCardModule.cancelGetFlightMedias();
        }
    }

    private ARDiscoveryDevice createDiscoveryDevice(@NonNull ARDiscoveryDeviceService service) {
        ARDiscoveryDevice device = null;
        try {
            device = new ARDiscoveryDevice(mContext, service);
        } catch (ARDiscoveryException e) {
            Log.e(TAG, "Exception", e);
            Log.e(TAG, "Error: " + e.getError());
        }

        return device;
    }

    private ARDeviceController createDeviceController(@NonNull ARDiscoveryDevice discoveryDevice) {
        ARDeviceController deviceController = null;
        try {
            deviceController = new ARDeviceController(discoveryDevice);

            deviceController.addListener(mDeviceControllerListener);
        } catch (ARControllerException e) {
            Log.e(TAG, "Exception", e);
        }

        return deviceController;
    }

    //region notify listener block
    private void notifyConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onDroneConnectionChanged(state);
        }
    }

    private void notifyBatteryChanged(int battery) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onBatteryChargeChanged(battery);
        }
    }

    private void notifyPilotingStateChanged(ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onPilotingStateChanged(state);
        }
    }

    private void notifyPictureTaken(ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onPictureTaken(error);
        }
    }

    private void notifyMatchingMediasFound(int nbMedias) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onMatchingMediasFound(nbMedias);
        }
    }

    private void notifyDownloadProgressed(String mediaName, int progress) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onDownloadProgressed(mediaName, progress);
        }
    }

    private void notifyDownloadComplete(String mediaName) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onDownloadComplete(mediaName);
        }
    }
    //endregion notify listener block

    private final SDCardModule.Listener mSDCardModuleListener = new SDCardModule.Listener() {
        @Override
        public void onMatchingMediasFound(final int nbMedias) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    notifyMatchingMediasFound(nbMedias);
                }
            });
        }

        @Override
        public void onDownloadProgressed(final String mediaName, final int progress) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    notifyDownloadProgressed(mediaName, progress);
                }
            });
        }

        @Override
        public void onDownloadComplete(final String mediaName) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    notifyDownloadComplete(mediaName);
                }
            });
        }
    };

    private final ARDeviceControllerListener mDeviceControllerListener = new ARDeviceControllerListener() {
        @Override
        public void onStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARCONTROLLER_ERROR_ENUM error) {
            mState = newState;
            if (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED.equals(mState)) {
                if (mSDCardModule != null) {
                    mSDCardModule.cancelGetFlightMedias();
                }
                if (mFtpListManager != null) {
                    mFtpListManager.closeFtp(mContext, mDeviceService);
                    mFtpListManager = null;
                }
                if (mFtpQueueManager != null) {
                    mFtpQueueManager.closeFtp(mContext, mDeviceService);
                    mFtpQueueManager = null;
                }
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    notifyConnectionChanged(mState);
                }
            });
        }

        @Override
        public void onExtensionStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARDISCOVERY_PRODUCT_ENUM product, String name, ARCONTROLLER_ERROR_ENUM error) {
        }

        @Override
        public void onCommandReceived(ARDeviceController deviceController, ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary) {
            // if event received is the battery update
            if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final int battery = (Integer) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED_PERCENT);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyBatteryChanged(battery);
                        }
                    });
                }
            }
            // if event received is the flying state update
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state = ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.getFromValue((Integer) args.get(ARFeatureMiniDrone.ARCONTROLLER_DICTIONARY_KEY_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE));

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mFlyingState = state;
                            notifyPilotingStateChanged(state);
                        }
                    });
                }
            }
            // if event received is the picture notification
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error = ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM.getFromValue((Integer) args.get(ARFeatureMiniDrone.ARCONTROLLER_DICTIONARY_KEY_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR));
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyPictureTaken(error);
                        }
                    });
                }
            }
            // if event received is the run id
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_RUNSTATE_RUNIDCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final String runID = (String) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_RUNSTATE_RUNIDCHANGED_RUNID);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mCurrentRunId = runID;
                        }
                    });
                }
            }
        }
    };

    public void lightParty() {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            for (int i = 0; i < 6; i++) {
                mDeviceController.getFeatureCommon().sendHeadlightsIntensity((byte) 100, (byte) 0);
                mDeviceController.getFeatureCommon().sendHeadlightsIntensity((byte) 0, (byte) 100);
            }

            mDeviceController.getFeatureCommon().sendHeadlightsIntensity((byte) 0, (byte) 0);
        }
    }

    public void lightsOn() {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureCommon().sendHeadlightsIntensity((byte) 100, (byte) 100);
        }
    }

    public void lightsOff() {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureCommon().sendHeadlightsIntensity((byte) 0, (byte) 0);
        }
    }

    public void start() throws java.lang.InterruptedException {

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDFlag((byte) 1);

    }

    public String drawInput(String input) throws InterruptedException {
        String regexHebrewPattern = "([\\p{InHebrew} ]+)";
        Pattern patternHebrew = Pattern.compile(regexHebrewPattern, Pattern.UNICODE_CASE);
        Matcher matcherHebrew = patternHebrew.matcher(input);

        if  (!matcherHebrew.matches()) {
            return "הקלט אינו תקין, יש להזין רק אותיות בעברית";
        } else {
            for (char curChar: input.toCharArray()) {
                if (!drawInputLetter(curChar)) {
                    return "שגיאה בציור האותיות";
                }

                drawSpace();
            }
        }

        return null;
    }

    public boolean drawInputLetter(char curChar) throws InterruptedException {
        switch (curChar) {
            case 'א':
                return false;
            case 'ב':
                drawBet();
                break;
            case 'ג':
                drawGimel();
                break;
            case 'ד':
                drawDaled();
                break;
            case 'ה':
                drawHey();
                break;
            case 'ו':
                drawVav();
                break;
            case 'ז':
                drawZain();
                break;
            case 'ח':
                drawHet(true);
                break;
            case 'ט':
                drawTet();
                break;
            case 'י':
                drawYud();
                break;
            case 'כ':
                drawKaf();
                break;
            case 'ל':
                drawLamed();
                break;
            case 'מ':
                drawMem();
                break;
            case 'נ':
                drawNun();
                break;
            case 'ס':
                //draw
                return false;
                //break;
            case 'ע':
                drawAin();
                break;
            case 'פ':
                drawPey();
                break;
            case 'צ':
                drawTzadik();
                break;
            case 'ק':
                drawKuf();
                break;
            case 'ר':
                drawReish();
                break;
            case 'ש':
                drawShin();
                break;
            case 'ת':
                drawTaf();
                break;
            default:
                return false;
        }

        return false;
    }

    public void sendOUp() throws java.lang.InterruptedException {
        start();
        lightsOn();

        int x = 20, y = 50;

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) x);


        for (int i = 0; (i < 100) && (y != -100); i++) {
            mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) y);

            Thread.sleep(500);

            y -= 25;
        }

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -50);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);

        lightsOff();

    }

    public void sendODown() throws java.lang.InterruptedException {
        start();
        lightsOn();

        int x = -20, y = -50;

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) x);


        for (int i = 0; (i < 100) && (y != 100); i++) {
            mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) y);

            Thread.sleep(500);

            y += 25;
        }

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 50);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);

        lightsOff();


    }

    public void sendToval() throws java.lang.InterruptedException {
        nHigh = 0;
        start();
        drawTaf();
        drawSpace();
        drawVav();
        drawSpace();
        drawBet();
        drawSpace();
        drawLamed();
    }

    public void drawLamed() throws java.lang.InterruptedException {
        start();
        lightsOn();
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        nHigh += 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(300);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(200);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(350);
        nHigh += 350;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(100);

        lightsOff();

    }

    public void drawVav() throws java.lang.InterruptedException {
        start();
        lightsOn();

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        nHigh += 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);
        lightsOff();
    }

    public void drawHet(boolean bTurnOffLight) throws java.lang.InterruptedException {
        start();
        lightsOn();

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        nHigh += 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(300);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(200);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);
        Thread.sleep(500);
        nHigh -= 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(100);

        if (bTurnOffLight) {
            lightsOff();
        }
    }

    public void drawTaf() throws java.lang.InterruptedException {
        drawHet(false);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(300);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);

        lightsOff();
    }

    public void drawBet() throws java.lang.InterruptedException {

        start();

        lightsOn();


        // Left

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);

        Thread.sleep(800);


        lightsOff();



        // Right

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);

        Thread.sleep(1300);



        // Holding Left

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);

        Thread.sleep(100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);

        Thread.sleep(100);



        lightsOn();



        // Up

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);

        Thread.sleep(500);

        nHigh += 500;

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);

        Thread.sleep(100);



        // Left

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);

        Thread.sleep(800);



        lightsOff();



        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);

        Thread.sleep(100);


        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);

        Thread.sleep(100);


    }

    public void drawSpace() throws java.lang.InterruptedException {
        start();
        // Moves back to the starting height point
        if (nHigh > 0) {
            mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);
            Thread.sleep(nHigh);
            nHigh = 0;
            mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
            Thread.sleep(100);
        }


//      Take the space
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(400);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(300);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(300);
    }

    // Not finished
    public void drawTzadik() throws java.lang.InterruptedException {
        start();

        // Up
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        nHigh += 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(300);

        // Tzup left
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -50);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 50);
        Thread.sleep(400);

        lightsOn();

        // Tzup right
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 50);
        Thread.sleep(200);

        // Stop
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -50);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(200);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(200);

        // \
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);
        Thread.sleep(900);

        // Stop
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 50);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(200);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(300);

        // _
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(600);

        // Stop
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(200);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(300);

        lightsOff();

    }

    public void drawKaf() throws java.lang.InterruptedException {
        start();

        // Left
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(900);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);

        // Back
        lightsOn();
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(900);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(300);

        // Up
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        nHigh += 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(300);

        // Left
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(300);

        lightsOff();
    }

    public void drawHey() throws java.lang.InterruptedException {

        start();
        lightsOn();


        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)100);
        Thread.sleep(500);
        nHigh += 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)0);
        Thread.sleep(100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)-100);
        Thread.sleep(300);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)0);
        Thread.sleep(200);

        lightsOff();
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)-100);
        Thread.sleep(500);
        lightsOn();
        Thread.sleep(250);


        nHigh -= 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)0);
        Thread.sleep(100);

        lightsOff();
    }

    public void drawKuf() throws java.lang.InterruptedException {
        start();

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)-100);
        Thread.sleep(200);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)0);
        Thread.sleep(100);

        lightsOn();


        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)100);
        Thread.sleep(700);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)0);
        Thread.sleep(100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)-100);
        Thread.sleep(300);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)0);
        Thread.sleep(200);

        lightsOff();
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)-100);
        Thread.sleep(450);
        lightsOn();
        Thread.sleep(200);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)0);
        Thread.sleep(100);

        lightsOff();
    }

    public void drawYud() throws java.lang.InterruptedException {
        start();


        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)100);
        Thread.sleep(400);
        lightsOn();
        Thread.sleep(100);
        nHigh += 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)0);
        Thread.sleep(100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)-100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)0);
        Thread.sleep(100);
        lightsOff();
    }

    public void drawNun() throws java.lang.InterruptedException {
        start();
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(100);
        lightsOn();
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(800);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);
        lightsOff();
    }

    public void drawReish() throws java.lang.InterruptedException {
        start();
        lightsOn();


        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)100);
        Thread.sleep(500);
        nHigh += 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)0);
        Thread.sleep(100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)100);
        Thread.sleep(700);
        lightsOff();
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)-100);
        Thread.sleep(250);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)0);
        Thread.sleep(200);
    }

    public void drawShin() throws java.lang.InterruptedException {
        start();

        // Up
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(300);

        // Down and draw
        lightsOn();
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(300);

        // Left
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(300);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(200);
        lightsOff();

        // Up
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(300);

        // Down and draw
        lightsOn();
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(300);

        // Left
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(300);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(200);

        // Up
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(300);
        nHigh -= 500;
    }

    public void drawTet() throws java.lang.InterruptedException {
        start();

        // No lights

        // Up
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(200);

        // Left
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(150);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(300);

        lightsOn();

        // Right
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(200);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(300);

        // Down
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(300);

        // Left
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(300);

        // Up
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(300);
        lightsOff();

        // Down
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(300);
    }

    public void drawDaled() throws java.lang.InterruptedException {
        start();

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)-100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)0);
        Thread.sleep(100);

        lightsOn();

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)100);
        Thread.sleep(500);
        nHigh += 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)0);
        Thread.sleep(100);

        lightsOff();

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)-100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)0);
        Thread.sleep(100);

        lightsOn();

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)100);
        Thread.sleep(800);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)-100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)0);
        Thread.sleep(100);

        lightsOff();
    }

    public void drawMem() throws java.lang.InterruptedException {

        start();

        // Left

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);

        Thread.sleep(800);




        // Right

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);

        Thread.sleep(100);



        lightsOn();


        Thread.sleep(1000);



        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);

        Thread.sleep(100);


        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);

        Thread.sleep(100);




        // Up

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);

        Thread.sleep(500);

        nHigh += 500;

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);

        Thread.sleep(100);



        // אלכסון
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(300);

        nHigh -= 300;

// עצירה
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);

        lightsOff();


// חזרה
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(150);
        nHigh += 150;


// עצירה
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);

        lightsOn();

// צופציק
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(200);
        nHigh += 200;


// עצירה
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);

        lightsOff();


    }

    public void drawPey() throws java.lang.InterruptedException {

        start();

        // Left

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);

        Thread.sleep(800);




        // Right

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);

        Thread.sleep(100);



        lightsOn();


        Thread.sleep(1000);



        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);

        Thread.sleep(100);


        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);

        Thread.sleep(100);




        // Up

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);

        Thread.sleep(500);

        nHigh += 500;

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);

        Thread.sleep(100);



        // Left

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);

        Thread.sleep(800);


// Stop
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);

        Thread.sleep(100);


        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);

        Thread.sleep(100);



// Down
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);

        Thread.sleep(250);

        nHigh -= 250;

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);

        Thread.sleep(100);



        // Right
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);

        Thread.sleep(400);


        lightsOff();

// Stop and move right
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);

        Thread.sleep(600);


        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);

        Thread.sleep(100);



        lightsOff();


    }

    public void drawZain() throws java.lang.InterruptedException {
        start();

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)100);
        Thread.sleep(400);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)-100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte)0);
        Thread.sleep(100);

        lightsOn();

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)100);
        Thread.sleep(500);
        nHigh += 500;
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte)0);
        Thread.sleep(100);

        lightsOff();

// צופציק
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(200);
        nHigh -= 200;


// עצירה
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);



        lightsOn();

// צופציק
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(400);
        nHigh += 400;


// עצירה
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);

        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);


        lightsOff();



    }

    public void drawGimel() throws java.lang.InterruptedException {
        start();
        drawReish();
        lightsOff();

        // Returns right
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);

        lightsOn();

        // Left +  down
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);
        Thread.sleep(500);

        // hadifa
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(100);
    }

    public void drawAin() throws java.lang.InterruptedException {
        drawNun();

        // Right
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) -100);
        Thread.sleep(250);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(50);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);

        // Up
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(100);

        lightsOff();

        // Goes to the left point of the letter
        // Down
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) -100);
        Thread.sleep(500);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDGaz((byte) 0);
        Thread.sleep(100);

        // left
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(250);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 100);
        Thread.sleep(50);
        mDeviceController.getFeatureMiniDrone().setPilotingPCMDRoll((byte) 0);
        Thread.sleep(100);
    }



}


