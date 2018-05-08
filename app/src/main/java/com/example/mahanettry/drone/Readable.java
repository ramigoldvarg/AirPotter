package com.example.mahanettry.drone;

import java.io.IOError;
import java.io.IOException;

/**
 * Created by power on 5/8/2018.
 */

public interface Readable {
    boolean readLine(String line) throws Exception;

    void close() throws IOException;
}
