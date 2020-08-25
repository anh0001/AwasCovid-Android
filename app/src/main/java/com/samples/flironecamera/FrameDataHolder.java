/*******************************************************************
 * @title FLIR THERMAL SDK
 * @file FrameDataHolder.java
 * @Author FLIR Systems AB
 *
 * @brief Container class that holds references to Bitmap images
 *
 * Copyright 2019:    FLIR Systems
 ********************************************************************/

package com.samples.flironecamera;

import android.graphics.Bitmap;

class FrameDataHolder {

    public final Bitmap msxBitmap;
    public final Bitmap dcBitmap;
    public final Bitmap scaledTemperatureBitmap;
    public final int minTemp, maxTemp; // Min max temperature in Celcius

    FrameDataHolder(Bitmap msxBitmap, Bitmap dcBitmap, Bitmap temperatureBitmap, int minTemp, int maxTemp){
        this.msxBitmap = msxBitmap;
        this.dcBitmap = dcBitmap;
        this.scaledTemperatureBitmap = temperatureBitmap;
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
    }
}
