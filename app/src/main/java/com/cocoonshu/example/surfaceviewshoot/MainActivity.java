package com.cocoonshu.example.surfaceviewshoot;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

public class MainActivity extends AppCompatActivity {

    private FloatingActionButton mFabScreenShoot    = null;
    private ViewGroup            mSurfaceViewParent = null;
    private ViewGroup            mLayoutPannel      = null;
    private SurfaceView          mSfvCameraPreview  = null;
    private CameraHelper         mCameraHelper      = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViews();
        setupListeners();
    }

    private void findViews() {
        mCameraHelper      = new CameraHelper(getApplicationContext());
        mSurfaceViewParent = (ViewGroup) findViewById(R.id.content_main);
        mLayoutPannel      = (ViewGroup) findViewById(R.id.content_preview);
        mSfvCameraPreview  = (SurfaceView) findViewById(R.id.SurfaceView_Camera);
        mFabScreenShoot    = (FloatingActionButton) findViewById(R.id.fab);
    }

    private void setupListeners() {
        mFabScreenShoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCameraHelper.capturePictureSync();
            }
        });

        mCameraHelper.setOnRequestCameraPermissionListener(new CameraHelper.RequestPermissionCallback() {
            @Override
            public boolean onRequestCameraPermission(String[] permissions, int requestID) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    boolean isAllOK = true;
                    for (String permission : permissions) {
                        isAllOK &= checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
                    }
                    if (!isAllOK) {
                        requestPermissions(permissions, requestID);
                    }
                    return isAllOK;
                } else {
                    return true;
                }
            }
        });

        mCameraHelper.setOnCapturedListener(new CameraHelper.OnCapturedListener() {
            @Override
            public void onCaptured(Bitmap bitmap) {
                mLayoutPannel.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
                mLayoutPannel.setDrawingCacheEnabled(true);

                Bitmap             mask         = mLayoutPannel.getDrawingCache();
                Bitmap             output       = Bitmap.createBitmap(mask.getWidth(), mask.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas             canvas       = new Canvas(output);
                Paint              paint        = new Paint(Paint.ANTI_ALIAS_FLAG);
                PorterDuffXfermode xfermode     = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
                float              bitmapWidth  = bitmap.getWidth();
                float              bitmapHeight = bitmap.getHeight();
                float              maskWidth    = mask.getWidth();
                float              maskHeight   = mask.getHeight();

                canvas.save();
                canvas.scale(maskWidth / bitmapHeight, maskHeight / bitmapWidth);
                canvas.translate(bitmapHeight, 0);
                canvas.rotate(90);
                canvas.drawBitmap(bitmap, 0, 0, paint);
                canvas.restore();

                paint.setXfermode(xfermode);
                canvas.drawBitmap(mask, 0, 0, paint);

//                paint.setXfermode(null);
//                canvas.drawBitmap(mask, 0, 0, paint);

                new ImageWriter().writeToDirectory(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
                ).onFinished(new ImageWriter.OnFinishedListener() {
                    @Override
                    public void onFinished(String fileName) {
                        Snackbar.make(mFabScreenShoot, "Image saved to " + fileName, Snackbar.LENGTH_LONG).show();
                    }
                }).execute(output);
            }
        });

        mCameraHelper.setOnErrorListener(new CameraHelper.OnErrorListener() {
            @Override
            public void onErrorOccurred(int error, String errorMessage) {
                Snackbar.make(mFabScreenShoot, errorMessage, Snackbar.LENGTH_LONG).show();
            }
        });

        mSfvCameraPreview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mCameraHelper.setupCamera(mSurfaceViewParent.getHeight(), mSurfaceViewParent.getWidth());
                Size previewSize = mCameraHelper.getSuggestPreviewSize();
                holder.setFixedSize(previewSize.getHeight(), previewSize.getWidth());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                mCameraHelper.startPreview(holder);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mCameraHelper.stopPreview();
            }
        });

        mSfvCameraPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!mCameraHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
