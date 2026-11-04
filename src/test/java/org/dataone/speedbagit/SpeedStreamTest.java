/**
 * This work was created by participants in the DataONE project, and is
 * jointly copyrighted by participating institutions in DataONE. For
 * more information on DataONE, see our web site at http://dataone.org.
 *
 *   Copyright ${year}
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * $Id$
 */

package org.dataone.speedbagit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


public class SpeedStreamTest {

    /**
     * Test that the default arguments are correctly set.
     *
     */
    @Test
    public void testCtor() throws NoSuchAlgorithmException, IOException {
        String testData = "12345, 345rfdew, 45tgfdr";

        InputStream testDataStream = new ByteArrayInputStream(testData.getBytes());
        MessageDigest targetDigest = MessageDigest.getInstance("MD5");
        SpeedStream speedStream = new SpeedStream(testDataStream, targetDigest);

        // Make sure that the size always starts at 0
        assertEquals(0, speedStream.getSize());
    }

    /**
     * The SpeedStream read methods that testRead calls.
     */
    enum ReadMode { SINGLE_BYTE, BUFFERED }

    /**
     * Test helper that returns the mock bytes of a test input.
     *
     * @param inputName The name of the input: empty, short or large
     * @return The bytes of the input
     */
    private static byte[] testInput(String inputName) {
        switch (inputName) {
            case "empty":
                return new byte[0];
            case "short":
                return "12345, 345rfdew, 45tgfdr".getBytes(StandardCharsets.UTF_8);
            case "large":
                // 3000 bytes, so a read into a 1024-byte buffer runs three times
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < 300; i++) {
                    builder.append("0123456789");
                }
                return builder.toString().getBytes(StandardCharsets.UTF_8);
            default:
                throw new IllegalArgumentException("Unknown test input " + inputName);
        }
    }

    /**
     Test helper that returns each read mode with each checksum algorithm, input name and expected checksum.
     *
     * @return The read mode, checksum algorithm, input name and expected checksum of each case
     */
    static Stream<Arguments> readParameters() {
        String[][] checksums = {
                {"MD5", "empty", "d41d8cd98f00b204e9800998ecf8427e"},
                {"MD5", "short", "88ccd39d1180ee61af10f051537a43a5"},
                {"MD5", "large", "8681a49920ffd5159feb1ae8fc2afb17"},
                {"SHA-1", "empty", "da39a3ee5e6b4b0d3255bfef95601890afd80709"},
                {"SHA-1", "short", "2fe482afad4e73addf3cb3823ff9b83144763bf2"},
                {"SHA-1", "large", "aca1bae58a8cd0841b2502f8f02f8df8df2f8028"},
                {"SHA-256", "empty", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"},
                {"SHA-256", "short", "6403b6be0d099dad457b00626d5c2d4ad9760068e9211a419a0603dcab962154"},
                {"SHA-256", "large", "bb291bdd4020f5b533ec332c2ab868276007893ebfcdaec5643bf21ac1852653"},
        };
        List<Arguments> cases = new ArrayList<>();
        for (ReadMode readMode : ReadMode.values()) {
            for (String[] checksum : checksums) {
                cases.add(Arguments.of(readMode, checksum[0], checksum[1], checksum[2]));
            }
        }
        return cases.stream();
    }

    /**
     * Tests that reading a SpeedStream returns the input bytes and records their size and checksum.
     */
    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("readParameters")
    public void testRead(ReadMode readMode, String algorithm, String inputName, String expectedChecksum)
            throws NoSuchAlgorithmException, IOException {
        byte[] input = testInput(inputName);
        SpeedStream speedStream = new SpeedStream(new ByteArrayInputStream(input),
                MessageDigest.getInstance(algorithm));

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        if (readMode == ReadMode.SINGLE_BYTE) {
            int value;
            while ((value = speedStream.read()) != -1) {
                result.write(value);
            }
        } else {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = speedStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
        }

        assertArrayEquals(input, result.toByteArray());
        assertEquals(input.length, speedStream.getSize());
        assertEquals(expectedChecksum, speedStream.getChecksum());
    }
}
