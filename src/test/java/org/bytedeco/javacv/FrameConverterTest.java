/*
 * Copyright (C) 2015 Samuel Audet
 *
 * Licensed either under the Apache License, Version 2.0, or (at your option)
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation (subject to the "Classpath" exception),
 * either version 2, or any later version (collectively, the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     http://www.gnu.org/licenses/
 *     http://www.gnu.org/software/classpath/license.html
 *
 * or as provided in the LICENSE.txt file that accompanied this code.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bytedeco.javacv;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.junit.Test;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.junit.Assert.*;

/**
 * Test cases for FrameConverter classes. Also uses other classes from JavaCV.
 *
 * @todo Figure out something for {@link AndroidFrameConverter}.
 *
 * @author Samuel Audet
 */
public class FrameConverterTest {

    @Test public void testJava2DFrameConverter() {
        System.out.println("Java2DFrameConverter");

        int[] depths = {Frame.DEPTH_UBYTE, Frame.DEPTH_SHORT, Frame.DEPTH_FLOAT};
        int[] channels = {1, 3, 4};
        for (int i = 0; i < depths.length; i++) {
            for (int j = 0; j < channels.length; j++) {
                Frame frame = new Frame(640 + 1, 480, depths[i], channels[j]);
                Java2DFrameConverter converter = new Java2DFrameConverter();

                Indexer frameIdx = frame.createIndexer();
                for (int y = 0; y < frameIdx.rows(); y++) {
                    for (int x = 0; x < frameIdx.cols(); x++) {
                        for (int z = 0; z < frameIdx.channels(); z++) {
                            frameIdx.putDouble(new int[] {y, x, z}, y + x + z);
                        }
                    }
                }

                BufferedImage image = converter.convert(frame);
                converter.frame = null;
                Frame frame2 = converter.convert(image);

                Indexer frame2Idx = frame2.createIndexer();
                for (int y = 0; y < frameIdx.rows(); y++) {
                    for (int x = 0; x < frameIdx.cols(); x++) {
                        for (int z = 0; z < frameIdx.channels(); z++) {
                            double value = frameIdx.getDouble(y, x, z);
                            assertEquals(value, frame2Idx.getDouble(y, x, z), 0);
                        }
                    }
                }

                try {
                    frame2Idx.getDouble(frameIdx.rows() + 1, frameIdx.cols() + 1);
                    fail("IndexOutOfBoundsException should have been thrown.");
                } catch (IndexOutOfBoundsException e) { }

                frameIdx.release();
                frame2Idx.release();
            }
        }

        int[] types = {BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB,
                       BufferedImage.TYPE_INT_ARGB_PRE, BufferedImage.TYPE_INT_BGR};
        for (int i = 0; i < types.length; i++) {
            BufferedImage image = new BufferedImage(640 + 1, 480, types[i]);
            Java2DFrameConverter converter = new Java2DFrameConverter();

            WritableRaster raster = image.getRaster();
            int[] array = ((DataBufferInt)raster.getDataBuffer()).getData();
            for (int j = 0; j < array.length; j++) {
                array[j] = j;
            }

            Frame frame = converter.convert(image);
            converter.bufferedImage = null;
            BufferedImage image2 = converter.convert(frame);

            WritableRaster raster2 = image2.getRaster();
            byte[] array2 = ((DataBufferByte)raster2.getDataBuffer()).getData();
            for (int j = 0; j < array.length; j++) {
                int n = ((array2[4 * j    ] & 0xFF) << 24) | ((array2[4 * j + 1] & 0xFF) << 16)
                      | ((array2[4 * j + 2] & 0xFF) << 8)  |  (array2[4 * j + 3] & 0xFF);
                assertEquals(array[j], n);
            }
        }
    }

    @Test public void testOpenCVFrameConverter() {
        System.out.println("OpenCVFrameConverter");

        for (int depth = 8; depth <= 64; depth *= 2) {
            assertEquals(depth, OpenCVFrameConverter.getFrameDepth(OpenCVFrameConverter.getIplImageDepth(depth)));
            assertEquals(depth, OpenCVFrameConverter.getFrameDepth(OpenCVFrameConverter.getMatDepth(depth)));
            if (depth < 64) {
                assertEquals(-depth, OpenCVFrameConverter.getFrameDepth(OpenCVFrameConverter.getIplImageDepth(-depth)));
                assertEquals(-depth, OpenCVFrameConverter.getFrameDepth(OpenCVFrameConverter.getMatDepth(-depth)));
            }
        }

        Frame frame = new Frame(640 + 1, 480, Frame.DEPTH_UBYTE, 3);
        OpenCVFrameConverter.ToIplImage converter1 = new OpenCVFrameConverter.ToIplImage();
        OpenCVFrameConverter.ToMat converter2 = new OpenCVFrameConverter.ToMat();

        UByteIndexer frameIdx = frame.createIndexer();
        for (int i = 0; i < frameIdx.rows(); i++) {
            for (int j = 0; j < frameIdx.cols(); j++) {
                for (int k = 0; k < frameIdx.channels(); k++) {
                    frameIdx.put(i, j, k, i + j + k);
                }
            }
        }

        IplImage image = converter1.convert(frame);
        Mat mat = converter2.convert(frame);

        converter1.frame = null;
        converter2.frame = null;
        Frame frame1 = converter1.convert(image);
        Frame frame2 = converter2.convert(mat);

        UByteIndexer frame1Idx = frame1.createIndexer();
        UByteIndexer frame2Idx = frame2.createIndexer();
        for (int i = 0; i < frameIdx.rows(); i++) {
            for (int j = 0; j < frameIdx.cols(); j++) {
                for (int k = 0; k < frameIdx.channels(); k++) {
                    int b = frameIdx.get(i, j, k);
                    assertEquals(b, frame1Idx.get(i, j, k));
                    assertEquals(b, frame2Idx.get(i, j, k));
                }
            }
        }

        try {
            frame1Idx.get(frameIdx.rows() + 1, frameIdx.cols() + 1);
            fail("IndexOutOfBoundsException should have been thrown.");
        } catch (IndexOutOfBoundsException e) { }

        try {
            frame2Idx.get(frameIdx.rows() + 1, frameIdx.cols() + 1);
            fail("IndexOutOfBoundsException should have been thrown.");
        } catch (IndexOutOfBoundsException e) { }

        frameIdx.release();
        frame1Idx.release();
        frame2Idx.release();
    }

}
