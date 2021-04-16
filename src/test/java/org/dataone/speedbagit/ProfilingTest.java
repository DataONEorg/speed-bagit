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

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.zip.ZipOutputStream;

/**
 * A suite of tests that should be run under a profiler and ignored by CI systems and ordinary builds.
 * These should be run before each release to ensure that emory management is
 * sane (ie entire files aren't loaded into memory at once).
 */

public class ProfilingTest {
	/**
	 * Test that SpeedBagIt can handle creating a 100GB archive of
	 * 100, 1GB files.
	 */
	@Test
	@Disabled
	public void testLargeFiles() throws IOException, NoSuchAlgorithmException {
		// Create 100, 1GB files
		GenerateFiles("largeFiles/", 100, 1000000000L);
		CreateBag("largeFiles/", "./bagged_data.zip");
	}

	/**
	 * Test that SpeedBagIt can handle creating a 100GB archive of
	 * 5000, 1kb files.
	 */
	@Test
	@Disabled
	public void testSmallFiles() throws IOException, NoSuchAlgorithmException {
		GenerateFiles("smallFiles/", 5000, 1000);
		CreateBag("smallFiles/", "./bagged_data.zip");
	}

	/**
	 * A utility method for generating fake data files.
	 *
	 * @param targetDirectory: The directory where the files will go
	 * @param fileCount: The number of files to create
	 * @param targetSize: The size, in bytes of each file
	 */
	public static void GenerateFiles(String targetDirectory, int fileCount, long targetSize) throws FileNotFoundException {

		File directory = new File(targetDirectory);
		if (!directory.exists()) {
			directory.mkdir();
		}

		// Determine a chunck size (5% of the targetSize)
		long chunkSize = (long) (targetSize * .05);
		Random rd = new Random();
		byte[] randomData = new byte[(int) chunkSize];
		rd.nextBytes(randomData);
		int filesWritten = 0;
		System.out.println(randomData.length);
		while (filesWritten < fileCount) {
			// Create a new file
			File dataFile = new File(targetDirectory + "/" + "test_file" + filesWritten);
			// Write data to file
			long bytesWritten = 0;
			try (FileOutputStream stream = new FileOutputStream(dataFile)) {
				while (bytesWritten < targetSize) {
					System.out.println(bytesWritten);
					System.out.println(targetSize);
					stream.write(randomData);
					bytesWritten += randomData.length;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			filesWritten += 1;
		}

	}

	/**
	 * Utility method for generating a bag archive using SpeedBagIt
	 *
	 * @param PayloadPath The path to the data directory that will be bagged
	 * @param bagPath The path to the bagit archive that will be created
	 */
	public static void CreateBag(String PayloadPath, String bagPath) throws IOException, NoSuchAlgorithmException {
		SpeedBagIt bag = new SpeedBagIt(1.0, "MD5");
		File dataDirectory = new File(PayloadPath);
		File[] directoryListing = dataDirectory.listFiles();
		if (directoryListing != null) {
			for (File dataFile : directoryListing) {
				// Let SpeedBagIt know where the file should be written to in the bag, specifying the
				// path *with* the filename.
				String filePath = "data/"+dataFile.getName();
				System.out.println(bagPath);
				bag.addFile(new FileInputStream(dataFile), filePath, MessageDigest.getInstance("MD5"), false);
			}
		}

		// Specify where the BagIt fill will go on disk
		Path bagFilePath = Files.createFile(Paths.get(bagPath));
		// Create a stream for SpeedBagIt to write to
		FileOutputStream fos = new FileOutputStream(bagFilePath.toString());
		InputStream bagStream = bag.stream();
        IOUtils.copy(bagStream, fos);
	}
}
