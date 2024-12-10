package Exceptions;

import java.io.IOException;

public class LostPacketException extends IOException {
    public LostPacketException(String message) {
        super(message);
    }
}
