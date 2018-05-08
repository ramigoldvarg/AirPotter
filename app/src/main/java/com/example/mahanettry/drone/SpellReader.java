package com.example.mahanettry.drone;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Created by power on 5/8/2018.
 */

public class SpellReader implements Readable {
    BufferedReader inputFile;

    public SpellReader(int file, Context context) {
        try {
            InputStream inputStream = context.getResources().openRawResource(file);
            InputStreamReader inputreader = new InputStreamReader(inputStream);
            inputFile = new BufferedReader(inputreader);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public boolean readLine(String line) throws Exception {
        try {
            line = this.inputFile.readLine();
            if(line != null) {
                return true;
            }

            return false;
        } catch (Exception ex) {
            throw ex;
        }
    }

    public void close() throws IOException {
        try {
            this.inputFile.close();
        } catch (IOException ex) {
            throw ex;
        }
    }
}
