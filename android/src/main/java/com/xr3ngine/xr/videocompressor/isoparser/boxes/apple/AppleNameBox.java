package com.xr3ngine.xr.videocompressor.isoparser.boxes.apple;

/**
 * Created by sannies on 10/15/13.
 */
public class AppleNameBox extends Utf8AppleDataBox {
    public static final String TYPE = "©nam";

    public AppleNameBox() {
        super(TYPE);
    }
}
