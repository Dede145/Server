package Exceptions;

import java.io.IOException;

public class FileNotReadableException extends IOException{
    public FileNotReadableException(String message) {
        super(message);
    }
}
