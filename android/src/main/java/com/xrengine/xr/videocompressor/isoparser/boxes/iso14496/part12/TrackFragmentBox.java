/*
 * Copyright 2009 castLabs GmbH, Berlin
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xrengine.xr.videocompressor.isoparser.boxes.iso14496.part12;

import com.xrengine.xr.videocompressor.isoparser.Box;
import com.xrengine.xr.videocompressor.isoparser.support.AbstractContainerBox;
import com.xrengine.xr.videocompressor.isoparser.support.DoNotParseDetail;

/**
 * <h1>4cc = "{@value #TYPE}"</h1>
 * aligned(8) class TrackFragmentBox extends Box('traf'){
 * }
 */
public class TrackFragmentBox extends AbstractContainerBox {
    public static final String TYPE = "traf";

    public TrackFragmentBox() {
        super(TYPE);
    }

    @DoNotParseDetail
    public TrackFragmentHeaderBox getTrackFragmentHeaderBox() {
        for (Box box : getBoxes()) {
            if (box instanceof TrackFragmentHeaderBox) {
                return (TrackFragmentHeaderBox) box;
            }
        }
        return null;
    }

}
