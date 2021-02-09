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

/**
 * The SpeedFile class represents a file that will be placed in a BagIt archive. It holds
 * a stream to the data that's written to the file in the archive and the location of the file.
 */
public class SpeedFile {
    private String bagPath;
    private SpeedStream stream;
    private boolean isTagFile;

    /**
     * Constructs a new SpeedFile instance.
     *
     * @param stream A SpeedStream representing the bytes of the file
     * @param bagPath The path, relative to the bag root that the file is written to
     * @param isTagFile A boolean that represents whether the file is a BagIt Tag file
     */
    public SpeedFile(SpeedStream stream, String bagPath, boolean isTagFile) {
        this.stream = stream;
        this.bagPath = bagPath;
        this.isTagFile = isTagFile;
    }

    /**
     * Gets the path, relative to the bag root that the file
     * is written to
     * @return The path of the file
     */
    public String getPath() {
        return this.bagPath;
    }

    /**
     * Gets the underlying stream to the file
     *
     * @return The stream of the file's bytes
     */

    public SpeedStream getStream() {
        return this.stream;
    }

    /**
     * Gets the flag representing the Tag file status
     *
     * @return Whether or not the file is a BagIt Tag file
     */
    public boolean isTagFile() {
        return this.isTagFile;
    }
}
