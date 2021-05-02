/*
 * Copyright 2012 Sebastian Annies, Hamburg
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
package com.xr3ngine.xr.videocompressor.isoparser.boxes.apple;

import com.xr3ngine.xr.videocompressor.isoparser.support.AbstractContainerBox;

/**
 * <h1>4cc = "{@value #TYPE}"</h1>
 */
public class GenericMediaHeaderAtom extends AbstractContainerBox {

    public static final String TYPE = "gmhd";

    public GenericMediaHeaderAtom() {
        super(TYPE);
    }

}
