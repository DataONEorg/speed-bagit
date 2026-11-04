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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SpeedFileTest {

    /**
     * Test that getPath returns the path of a data file or a tag file.
     */
    @ParameterizedTest
    @CsvSource({"data/my_csv_file.csv, false", "metadata/my_csv_file.csv, true"})
    public void testGetPath(String targetPath, boolean tagFile) throws NoSuchAlgorithmException
    {
        String testCsvFile = "1234, 56789";
        InputStream fileStream = new ByteArrayInputStream(testCsvFile.getBytes(StandardCharsets.UTF_8));
        SpeedStream speedStream = new SpeedStream(fileStream, MessageDigest.getInstance("MD5"));
        SpeedFile testFile = new SpeedFile(speedStream, targetPath, tagFile);
        assertEquals(targetPath, testFile.getPath());
    }

    /**
     * Test that getStream is returning the stream that was passed into the
     * constructor
     */
    @Test
    public void testGetStream() throws NoSuchAlgorithmException
    {
        String testCsvFile = "1234, 56789";
        String targetPath = "data/my_csv_file.csv";
        InputStream fileStream = new ByteArrayInputStream(testCsvFile.getBytes(StandardCharsets.UTF_8));
        SpeedStream speedStream = new SpeedStream(fileStream, MessageDigest.getInstance("MD5"));
        SpeedFile testFile = new SpeedFile(speedStream, targetPath, false);
        assertSame(speedStream, testFile.getStream());
    }

    /**
     * Test isTagFile is returning the correct property
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testIsTagFile(boolean tagFile) throws NoSuchAlgorithmException
    {
        String testCsvFile = "1234, 56789";
        String targetPath = "data/my_csv_file.csv";
        InputStream fileStream = new ByteArrayInputStream(testCsvFile.getBytes(StandardCharsets.UTF_8));
        SpeedStream speedStream = new SpeedStream(fileStream, MessageDigest.getInstance("MD5"));
        SpeedFile testFile = new SpeedFile(speedStream, targetPath, tagFile);
        assertEquals(tagFile, testFile.isTagFile());
    }
}
