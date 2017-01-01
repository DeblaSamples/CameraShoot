package com.cocoonshu.example.surfaceviewshoot;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;

/**
 * Mask image writer
 * @Auther Cocoonshu
 * @Date   2017-01-01 10:59:11
 */
public class ImageWriter extends AsyncTask<Bitmap, Integer, String> {

    private String             mDir                = null;
    private OnFinishedListener mOnFinishedListener = null;

    public interface OnFinishedListener {
        void onFinished(String fileName);
    }

    public ImageWriter() {

    }

    public ImageWriter writeToDirectory(String dir) {
        mDir = dir;
        return this;
    }

    public ImageWriter onFinished(OnFinishedListener listener) {
        mOnFinishedListener = listener;
        return this;
    }

    @Override
    protected String doInBackground(Bitmap... params) {
        Bitmap bitmap = null;
        if (params != null && params.length > 0) {
            bitmap = params[0];
        }

        try {
            File parent = new File(mDir);
            if (!parent.exists()) {
                parent.mkdirs();
            }

            Calendar calendar = Calendar.getInstance();
            String   fileName = String.format("/MIX_%04d_%02d_%02d_%02d_%02d_%02d.png",
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH),
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    calendar.get(Calendar.SECOND));

            File             file = new File(mDir + fileName);
            FileOutputStream fout = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fout);
            fout.close();

            return mDir + fileName;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onPostExecute(String fileName) {
        super.onPostExecute(fileName);
        if (mOnFinishedListener != null) {
            mOnFinishedListener.onFinished(fileName);
        }
    }
}
