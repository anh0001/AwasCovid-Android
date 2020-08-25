/*******************************************************************
 * @title FLIR THERMAL SDK
 * @file CameraHandler.java
 * @Author FLIR Systems AB
 *
 * @brief Helper class that encapsulates *most* interactions with a FLIR ONE camera
 *
 * Copyright 2019:    FLIR Systems
 ********************************************************************/
package com.samples.flironecamera;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;

import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
import com.flir.thermalsdk.image.Point;
import com.flir.thermalsdk.image.Rectangle;
import com.flir.thermalsdk.image.TemperatureUnit;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.image.fusion.FusionMode;
import com.flir.thermalsdk.image.palettes.Palette;
import com.flir.thermalsdk.image.palettes.PaletteManager;
import com.flir.thermalsdk.live.Camera;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.discovery.DiscoveryFactory;
import com.flir.thermalsdk.live.streaming.ThermalImageStreamListener;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Encapsulates the handling of a FLIR ONE camera or built in emulator, discovery, connecting and start receiving images.
 * All listeners are called from Thermal SDK on a non-ui thread
 * <p/>
 * Usage:
 * <pre>
 * Start discovery of FLIR FLIR ONE cameras or built in FLIR ONE cameras emulators
 * {@linkplain #startDiscovery(DiscoveryEventListener, DiscoveryStatus)}
 * Use a discovered Camera {@linkplain Identity} and connect to the Camera
 * (note that calling connect is blocking and it is mandatory to call this function from a background thread):
 * {@linkplain #connect(Identity, ConnectionStatusListener)}
 * Once connected to a camera
 * {@linkplain #startStream(StreamDataListener)}
 * </pre>
 * <p/>
 * You don't *have* to specify your application to listen or USB intents but it might be beneficial for you application,
 * we are enumerating the USB devices during the discovery process which eliminates the need to listen for USB intents.
 * See the Android documentation about USB Host mode for more information
 * <p/>
 * Please note, this is <b>NOT</b> production quality code, error handling has been kept to a minimum to keep the code as clear and concise as possible
 */
class CameraHandler {

    private static final String TAG = "CameraHandler";

    private StreamDataListener streamDataListener;

    public interface StreamDataListener {
        void images(FrameDataHolder dataHolder);

        void images(Bitmap msxBitmap, Bitmap dcBitmap, Bitmap temperatureBitmap, double minTemp, double maxTemp);
    }

    //Discovered FLIR cameras
    LinkedList<Identity> foundCameraIdentities = new LinkedList<>();

    //A FLIR Camera
    private Camera camera;


    public interface DiscoveryStatus {
        void started();

        void stopped();
    }

    public CameraHandler() {
    }

    /**
     * Start discovery of USB and Emulators
     */
    public void startDiscovery(DiscoveryEventListener cameraDiscoveryListener, DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().scan(cameraDiscoveryListener, CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.started();
    }

    /**
     * Stop discovery of USB and Emulators
     */
    public void stopDiscovery(DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().stop(CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.stopped();
    }

    public void connect(Identity identity, ConnectionStatusListener connectionStatusListener) throws IOException {
        camera = new Camera();
        camera.connect(identity, connectionStatusListener);
    }

    public void disconnect() {
        if (camera == null) {
            return;
        }
        if (camera.isGrabbing()) {
            camera.unsubscribeAllStreams();
        }
        camera.disconnect();
    }

    /**
     * Start a stream of {@link ThermalImage}s images from a FLIR ONE or emulator
     */
    public void startStream(StreamDataListener listener) {
        this.streamDataListener = listener;
        camera.subscribeStream(thermalImageStreamListener);
    }

    /**
     * Stop a stream of {@link ThermalImage}s images from a FLIR ONE or emulator
     */
    public void stopStream(ThermalImageStreamListener listener) {
        camera.unsubscribeStream(listener);
    }

    /**
     * Add a found camera to the list of known cameras
     */
    public void add(Identity identity) {
        foundCameraIdentities.add(identity);
    }

    @Nullable
    public Identity get(int i) {
        return foundCameraIdentities.get(i);
    }

    /**
     * Get a read only list of all found cameras
     */
    @Nullable
    public List<Identity> getCameraList() {
        return Collections.unmodifiableList(foundCameraIdentities);
    }

    /**
     * Clear all known network cameras
     */
    public void clear() {
        foundCameraIdentities.clear();
    }

    @Nullable
    public Identity getCppEmulator() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("C++ Emulator")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    @Nullable
    public Identity getFlirOneEmulator() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    @Nullable
    public Identity getFlirOne() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            boolean isFlirOneEmulator = foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE");
            boolean isCppEmulator = foundCameraIdentity.deviceId.contains("C++ Emulator");
            if (!isFlirOneEmulator && !isCppEmulator) {
                return foundCameraIdentity;
            }
        }

        return null;
    }

    private void withImage(ThermalImageStreamListener listener, Camera.Consumer<ThermalImage> functionToRun) {
        camera.withImage(listener, functionToRun);
    }


    /**
     * Called whenever there is a new Thermal Image available, should be used in conjunction with {@link Camera.Consumer}
     */
    private final ThermalImageStreamListener thermalImageStreamListener = new ThermalImageStreamListener() {
        @Override
        public void onImageReceived() {
            //Will be called on a non-ui thread
            Log.d(TAG, "onImageReceived(), we got another ThermalImage");
            withImage(this, handleIncomingImage);
        }
    };

    /**
     * Function to process a Thermal Image and update UI
     */
    private final Camera.Consumer<ThermalImage> handleIncomingImage = new Camera.Consumer<ThermalImage>() {
        @Override
        public void accept(ThermalImage thermalImage) {
            Log.d(TAG, "accept() called with: thermalImage = [" + thermalImage.getDescription() + "]");
            //Will be called on a non-ui thread,
            // extract information on the background thread and send the specific information to the UI thread

            int temp_img_width = 480;
            int temp_img_height = 640;

            // Set temperature unit and get the values
            thermalImage.setTemperatureUnit(TemperatureUnit.CELSIUS);
//            Log.d(TAG, "tempBefore: " + thermalImage.getValueAt(new Point(216, 395)));
            Rectangle rect = new Rectangle(0, 0, temp_img_width, temp_img_height); // TODO: Directly set to the width and height size, must do something better
            double[] pixelsTemp = thermalImage.getValues(rect);
//            Log.d(TAG, "temperature: " + pixelsTemp[216 + 395 * 480]);

//            final List<Palette> palettes = PaletteManager.getDefaultPalettes();
            /*
                0: Iron
                1: Gray
                2: Rainbow
                3: Contrast
                from Flir-one android default app
             */
//            thermalImage.setPalette(palettes.get(0));  // Somehow this causes temperature reading error, probably the pixels temperature are converted

            //Get a bitmap with only IR data
            Bitmap msxBitmap;
            {
                thermalImage.getFusion().setFusionMode(FusionMode.THERMAL_ONLY);
                //thermalImage.getFusion().setFusionMode(FusionMode.MSX);
                msxBitmap = BitmapAndroid.createBitmap(thermalImage.getImage()).getBitMap();
                assert (msxBitmap.getWidth() == temp_img_width);
                assert (msxBitmap.getHeight() == temp_img_height);
//                // for debugging: save to a file
//                try {
//                    Log.i(TAG, "filename: " + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + "/msxImage.jpg");
//                    thermalImage.saveAs(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + "/msxImage.jpg");
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }

            //Get all pixel's temperatures
            // --- First method: convert to List of Integer
//            List<Integer> listPixelsTemp = new ArrayList<Integer>(pixelsTemp.length);
//            for (int i = 0; i < pixelsTemp.length; i++) {
//                listPixelsTemp.add((int) (pixelsTemp[i] * 10));  // two digits decimal
//            }
//            Log.i(TAG, "tempAfter: " + pixelsTemp[240 + 320 * 480]);  //center pos temperature

            // --- Second method: scale the temperatre and create a bitmap
            double min_temperature = 30.0;
            double max_temperature = 45.0;
            Bitmap temperatureBitmap = Bitmap.createBitmap(temp_img_width, temp_img_height, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < temperatureBitmap.getWidth(); x++) {
                for (int y = 0; y < temperatureBitmap.getHeight(); y++) {
                    double temperature = pixelsTemp[x + y * temperatureBitmap.getWidth()];

                    if (temperature < min_temperature)  // min temp 30 celcius
                        temperature = min_temperature;
                    else if (temperature > max_temperature)  // max temp 45 celcius
                        temperature = max_temperature;

                    int scaledTemp = (int) Math.round((temperature - min_temperature) * (255.0 / (max_temperature - min_temperature)));
                    scaledTemp = (scaledTemp > 255) ? 255 : scaledTemp;
                    temperatureBitmap.setPixel(x, y, Color.argb(255, scaledTemp, scaledTemp, scaledTemp));

//                    if (scaledTemp > 0) {
//                        Log.d(TAG, "pixValueRed: " + Color.red(temperatureBitmap.getPixel(x, y)));
//                        Log.d(TAG, "pixValueGreen: " + Color.green(temperatureBitmap.getPixel(x, y)));
//                        Log.d(TAG, "pixValueBlue: " + Color.blue(temperatureBitmap.getPixel(x, y)));
//                        Log.d(TAG, "temp: " + temperature);
//                        Log.d(TAG, "scaledTemp: " + scaledTemp);
//                    }
                }
            }
//            Log.d(TAG, "temperature: " + Color.red(temperatureBitmap.getPixel(216, 395)));
//            Log.d(TAG, "temperature: " + (min_temperature + Color.red(temperatureBitmap.getPixel(216, 395)) * (max_temperature - min_temperature) / 255.0));

            //Get a bitmap with the visual image, it might have different dimensions then the bitmap from THERMAL_ONLY
            Bitmap dcBitmap = BitmapAndroid.createBitmap(thermalImage.getFusion().getPhoto()).getBitMap();

            Log.d(TAG, "adding images to cache");
            streamDataListener.images(msxBitmap, dcBitmap, temperatureBitmap, min_temperature, max_temperature);
        }
    };


}
