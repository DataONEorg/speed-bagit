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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import org.apache.commons.codec.binary.Hex;

/**
 * A class that manages a stream of bytes to a BagIt archive. While the data
 * is transferred/streamed, the size and checksum are computed.
 */
public class SpeedStream extends FilterInputStream {

    // The object that holds the checksum state & performs checksumming
    private MessageDigest digest;
    // The number of bytes streamed
    private int size;
    /**
     * Constructs a new SpeedStream object
     *
     * @param in The stream t
     * @param sum A MessageDigest that is updated as the stream is streamed
     *
     */
    public SpeedStream(InputStream in, MessageDigest sum) {
        super(in);
        this.digest = sum;
        this.size = 0;

        // Reset the MessageDigest's state
        this.digest.reset();
    }

    /**
     * Reads a byte. Will block if no input is available.
     *
     * @return the byte read, or -1 if the end of the stream is reached.
     * @throws IOException if an I/O error has occurred
     */
    public int read() throws IOException {
        int b = in.read();
        if (b != -1) {
            this.digest.update((byte) b);
            this.size += 1;
        }
        return b;
    }

    /**
     * Reads into an array of bytes. If <code>len</code> is not zero, the method
     * blocks until some input is available; otherwise, no
     * bytes are read and <code>0</code> is returned.
     *
     * @param buf the buffer into which the data is read
     * @param off the start offset in the destination array <code>b</code>
     * @param len the maximum number of bytes read
     * @return the actual number of bytes read, or -1 if the end
     * of the stream is reached.
     * @throws NullPointerException      If <code>buf</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException If <code>off</code> is negative,
     *                                   <code>len</code> is negative, or <code>len</code> is greater than
     *                                   <code>buf.length - off</code>
     * @throws IOException               if an I/O error has occurred
     */
    public int read(byte[] buf, int off, int len) throws IOException {
        len = in.read(buf, off, len);
        if (len != -1) {
            this.digest.update(buf, off, len);
            this.size += len;
        }
        return len;
    }

    /**
     * Returns the number of bytes that were streamed
     *
     * @return The number of bytes streamed
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the checksum of the stream.
     *
     * Converts checksum.digest (byte[]) to a String. Since this is a checksum,
     * it should take up minimal space in memory.
     *
     * @return The checksum of the streamed bytes
     */
    public String getChecksum() {
        return Hex.encodeHexString(this.digest.digest());
    }
}
