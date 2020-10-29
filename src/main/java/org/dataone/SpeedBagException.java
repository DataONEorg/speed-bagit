package org.dataone;


public class SpeedBagException extends Exception {

    /**
     * Create a SpeedyBagException exception.
     *
     * @param message: A detailed message about the exception
     */
    public SpeedBagException(String message) {
        super(message);
    }

    /**
     * Create a SpeedyBagException with a an Exception
     *
     * @param message: A detailed message about the exception
     * @param cause:   An additional exception to attached
     */
    public SpeedBagException(String message, Exception cause) {
        super(message, cause);
    }
}
