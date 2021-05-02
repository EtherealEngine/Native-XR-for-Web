/*
 * Copyright (C) 2014 Yuya Tanaka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xrengine.xr.transcoder.format;

public class MediaFormatStrategyPresets {
    public static final int AUDIO_BITRATE_AS_IS = -1;
    public static final int AUDIO_CHANNELS_AS_IS = -1;

    /**
     * @deprecated Use {@link #createExportPreset960x540Strategy()}.
     */
    @Deprecated
    public static final MediaFormatStrategy EXPORT_PRESET_960x540 = new ExportPreset960x540Strategy();

    /**
     * Preset based on Nexus 4 camera recording with 720p quality.
     * This preset is ensured to work on any Android &gt;=4.3 devices by Android CTS (if codec is available).
     * Default bitrate is 8Mbps. {@link #createAndroid720pStrategy(int)} to specify bitrate.
     */
    public static MediaFormatStrategy createAndroid720pStrategy() {
        return new Android720pFormatStrategy();
    }

    /**
     * Preset based on Nexus 4 camera recording with 720p quality.
     * This preset is ensured to work on any Android &gt;=4.3 devices by Android CTS (if codec is available).
     * Audio track will be copied as-is.
     *
     * @param bitrate Preferred bitrate for video encoding.
     */
    public static MediaFormatStrategy createAndroid720pStrategy(int bitrate) {
        return new Android720pFormatStrategy(bitrate);
    }

    /**
     * Preset based on Nexus 4 camera recording with 720p quality.
     * This preset is ensured to work on any Android &gt;=4.3 devices by Android CTS (if codec is available).
     * <br>
     * Note: audio transcoding is experimental feature.
     *
     * @param bitrate       Preferred bitrate for video encoding.
     * @param audioBitrate  Preferred bitrate for audio encoding.
     * @param audioChannels Output audio channels.
     */
    public static MediaFormatStrategy createAndroid720pStrategy(int bitrate, int audioBitrate, int audioChannels) {
        return new Android720pFormatStrategy(bitrate, audioBitrate, audioChannels);
    }

    /**
     * Preset similar to iOS SDK's AVAssetExportPreset960x540.
     * Note that encoding resolutions of this preset are not supported in all devices e.g. Nexus 4.
     * On unsupported device encoded video stream will be broken without any exception.
     */
    public static MediaFormatStrategy createExportPreset960x540Strategy() {
        return new ExportPreset960x540Strategy();
    }

    private MediaFormatStrategyPresets() {
    }
}
