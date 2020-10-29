package org.dataone;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * A stream that computes the checksum and size of an object as it streams.
 *
 */
public class SpeedStream extends FilterInputStream {

    private MessageDigest cksum;
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
        this.cksum = sum;
        this.size = 0;
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
            this.cksum.update((byte) b);
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
            this.cksum.update(buf, off, len);
            // DEVNOTE: This is incorrect; this adds the *maximum* number of bytes read
            this.size += len;
        }
        return len;
    }

    /**
     * Returns the number of bytes that were streamed
     * @return The number of bytes streamed
     */
    public int getSize() {
        return size;
    }

    /**
     *
     * @return The checksum of the streamed bytes
     */
    public byte[] getChecksum() {
        return this.cksum.digest();
    }
}
