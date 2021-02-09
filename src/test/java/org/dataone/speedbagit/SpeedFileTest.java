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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SpeedFileTest {

    /**
     * Test that getPath works
     */
    @Test
    public void testGetPath()
    {
        String testCsvFile = "1234, 56789";
        String targetPath = "data/my_csv_file.csv";
        InputStream fileStream = new ByteArrayInputStream(testCsvFile.getBytes(StandardCharsets.UTF_8));
        try {
            SpeedStream speedStream = new SpeedStream(fileStream, MessageDigest.getInstance("MD5"));
            SpeedFile testFile = new SpeedFile(speedStream, targetPath, false);
            String path = testFile.getPath();
            assertEquals(path, targetPath);
        } catch (NoSuchAlgorithmException e) {
            fail();
        }
    }

    /**
     * Test that getStream is returning the stream that was passed into the
     * constructor
     */
    @Test
    public void testGetStream()
    {
        String testCsvFile = "1234, 56789";
        String targetPath = "data/my_csv_file.csv";
        InputStream fileStream = new ByteArrayInputStream(testCsvFile.getBytes(StandardCharsets.UTF_8));
        try {
            SpeedStream speedStream = new SpeedStream(fileStream, MessageDigest.getInstance("MD5"));
            SpeedFile testFile = new SpeedFile(speedStream, targetPath, false);
            SpeedStream stream = testFile.getStream();
            assertEquals(stream, speedStream);
        } catch (NoSuchAlgorithmException e) {
            fail();
        }
    }

    /**
     * Test isTagFile is returning the correct property
     */
    @Test
    public void testIsTagFile()
    {
        String testCsvFile = "1234, 56789";
        String targetPath = "data/my_csv_file.csv";
        boolean tagFile = false;
        InputStream fileStream = new ByteArrayInputStream(testCsvFile.getBytes(StandardCharsets.UTF_8));
        try {
            SpeedStream speedStream = new SpeedStream(fileStream, MessageDigest.getInstance("MD5"));
            SpeedFile testFile = new SpeedFile(speedStream, targetPath, tagFile);
            boolean isTag = testFile.isTagFile();
            assertEquals(isTag, tagFile);
        } catch (NoSuchAlgorithmException e) {
            fail();
        }
    }
}
