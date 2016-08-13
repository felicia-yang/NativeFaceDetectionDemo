package ph.edu.dlsu.nativefacedetectiondemo;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import butterknife.Bind;
import butterknife.ButterKnife;
import ph.edu.dlsu.mhealth.android.OpenCvCameraView;
import ph.edu.dlsu.mhealth.android.PreviewTemplate;
import ph.edu.dlsu.mhealth.vision.DetectionBasedTracker;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

        private static final String TAG = "FaceDetectionDemo";

        private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);

        public static final int VIEW_MODE_RGBA = 0;
        public static final int VIEW_MODE_FACE_DETECT = 1;

        public static int viewMode = VIEW_MODE_RGBA; // default

        /// Para sa Image Processing
        private Mat mRgba;
        private Mat mGray;
        private Mat mMask;
    //private Mat mIntermediate;

        // Para sa guidance template to the user
        private PreviewTemplate previewTemplate;
        private int zoneCounter;
        private int currentZone;
        private org.opencv.core.Rect zoomWindow;

        // Camera
        private static final String STATE_CAMERA_INDEX = "cameraIndex";
        private boolean mIsCameraFrontFacing;
        private int mNumCameras;
        private int mCameraIndex = 0;  // default to front camera

        // Take picture view
        private Bitmap photo;
        private Boolean isTakePicture = false;

        // Bindings
        @Bind(R.id.fd_activity_surface_view)
        OpenCvCameraView mOpenCvCameraView;

        // Para sa object detector
        private DetectionBasedTracker faceDetector;
        private final float RELATIVE_OBJECT_SIZE = 0.2f;
        private int mAbsoluteObjectSize = 0;


//        // OpenCV Static Loading
//        static
//        {
//        if(!OpenCVLoader.initDebug()){
//            Log.d(TAG, "Loading OpenCV failed!");
//        }
//        else
//        {
//            Log.d(TAG, "Loading OpenCV successfully!");
//            System.loadLibrary("mhealth_vision");
//
//            mOpenCvCameraView.enableView();
//            mOpenCvCameraView.enableFpsMeter();
//
//            initCascadeClassifier();
//
//        }
//        }


    // Loads OpenCV and Native libraries
    // Initiates the Object detector
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // BaseLoader loads libopencv_java3.so located at libs directory

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("mhealth_vision");

                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.enableFpsMeter();

                    initCascadeClassifier();

                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

        public MainActivity() {
            // Log.i(TAG, "Instantiated new " + this.getClass());
        }

        /**
         * Called when the activity is first created.
         */
        @SuppressLint("NewApi")
        @Override
        public void onCreate(Bundle savedInstanceState) {

            super.onCreate(savedInstanceState);

            final Window window = getWindow();
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            if (savedInstanceState != null) {
                mCameraIndex = savedInstanceState.getInt(STATE_CAMERA_INDEX, 0);
            }

            setContentView(R.layout.activity_main);
            ButterKnife.bind(this);

            mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
            mOpenCvCameraView.setCameraIndex(mCameraIndex);
            mNumCameras = mOpenCvCameraView.getNumberOfCameras();
            mIsCameraFrontFacing = mOpenCvCameraView.isCameraFrontFacing();
            mOpenCvCameraView.setCvCameraViewListener(this);
            // mOpenCvCameraView.setMaxFrameSize(WIDTH, HEIGHT);

        }


    private void initCascadeClassifier() {
        try {
            // load cascade file from application resources
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "lbpcascade_fingerpad.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            faceDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);

            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

            cascadeDir.delete();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }
    }


    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the current camera index.
        savedInstanceState.putInt(STATE_CAMERA_INDEX, mCameraIndex);
        super.onSaveInstanceState(savedInstanceState);
    }


    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        //if (!OpenCVLoader.initDebug()) {
        //    Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
        //    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
        // } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        // }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
        if (faceDetector != null) {
            faceDetector.release();
        }
    }




    public void onCameraViewStarted(int width, int height) {

        /// Frame processing initialization
        mGray = new Mat(height, width, CvType.CV_8UC1);
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        //mIntermediate = new Mat();



        // Initialize the Object Template
        previewTemplate = new PreviewTemplate();
        previewTemplate.initCenters(height, width);
        previewTemplate.initRois(height);
        previewTemplate.initMask();

        mMask = previewTemplate.getWindowMask();  // circular mask

        //currentZone = Constants.TWELVEoCLOCK_ZONE;
        currentZone = PreviewTemplate.NP_CENTRAL_ZONE;

        // Initialize zoom window
        zoomWindow = previewTemplate.getDisplayWindow();

        // Initialize detector
        mAbsoluteObjectSize = Math.round(height * RELATIVE_OBJECT_SIZE);
        faceDetector.setMinFaceSize(mAbsoluteObjectSize);

        // Initialize picture boolean
        isTakePicture = false;

        // Take picture using a button instead of screen listener: OK
//
//        if (isShowTrackToast && viewMode == CameraBasedCheckActivity.VIEW_MODE_TRACK)
//            showToast("Choose your 3-finger pad ROI...");
//        else
//            showToast("Click the screen to take a picture.");
    }


    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
        mMask.release();

    }


    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mIsCameraFrontFacing) {
            // Mirror (horizontally flip) the preview.
            Core.flip(mRgba, mRgba, 1);
            Core.flip(mGray, mGray, 1);
        }


        switch (MainActivity.viewMode) {

            case MainActivity.VIEW_MODE_RGBA:
                break;

            /* Test: OK */
            case MainActivity.VIEW_MODE_FACE_DETECT:

                // Show template for initial zone
                previewTemplate.displayZone(mRgba, currentZone);

                // Acquire the region of interest
                Rect roi = previewTemplate.getRoi(currentZone);

                MatOfRect objects = new MatOfRect();

                // Detect the finger pads in the zoomed image
                if (faceDetector != null)
                     faceDetector.detect(mGray.submat(roi), objects);

               // mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2,
               //         new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());

                Rect[] objectsArray = objects.toArray();
                for (Rect aObjectsArray : objectsArray) {
                    // Correction of rect location by offset
                    Point topLeft = new Point(roi.x + aObjectsArray.tl().x, roi.y + aObjectsArray.tl().y);
                    Point bottomRight = new Point(roi.x + aObjectsArray.br().x, roi.y + aObjectsArray.br().y);
                    Imgproc.rectangle(mRgba, topLeft, bottomRight, new Scalar(0,250,0), 3);
                }
                break;

        } // END SWITCH

        if (isTakePicture) takePhoto(mRgba);

        return mRgba;
    }


    private void takePhoto(Mat rgba) {

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

        String currentDateandTime = sdf.format(new Date());

        String fileName = currentDateandTime + PhotoActivity.PHOTO_FILE_EXTENSION;

        photo = null;

        try {
            photo = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rgba, photo);
        } catch (CvException e) {
            //Log.d(TAG, e.getMessage());
        }


        FileOutputStream out = null;
        final String appName = getString(R.string.app_name);
        final String albumPath = Environment.getExternalStorageDirectory() + File.separator + appName;
        final String photoPath = albumPath + File.separator + fileName;

        File sd = new File(albumPath);

        boolean success = true;

        if (!sd.exists()) {
            success = sd.mkdir();
        }

        if (success) {
            File dest = new File(sd, fileName);

            try {
                out = new FileOutputStream(dest);
                photo.compress(Bitmap.CompressFormat.PNG, 1, out); // bmp is your Bitmap instance
                // PNG is a lossless format, the compression factor (100) is ignored (changed to 0)

            } catch (Exception e) {
                // e.printStackTrace();
                //Log.d(TAG, e.getMessage());
            } finally {
                try {
                    if (out != null) {
                        out.close();
                        //Log.d(TAG, "OK!!");
                    }
                } catch (IOException e) {
                    //Log.d(TAG, e.getMessage() + "Error");
                    // e.printStackTrace();
                }
            }

            // Recycle bitmap
            if (photo != null)
                photo.recycle();

            photo = null;


            // Save photo information
            final ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DATA, photoPath);
            values.put(MediaStore.Images.Media.MIME_TYPE, PhotoActivity.PHOTO_MIME_TYPE);
            values.put(MediaStore.Images.Media.TITLE, appName);
            values.put(MediaStore.Images.Media.DESCRIPTION, appName);
            values.put(MediaStore.Images.Media.DATE_TAKEN, currentDateandTime);


            // Try to insert the photo into the MediaStore.
            Uri uri = null;

            try {
                uri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            } catch (final Exception e) {
                //Log.e(TAG, "Failed to insert photo into MediaStore");
                e.printStackTrace();
                // Since the insertion failed, delete the photo.
                File fphoto = new File(photoPath);
                if (!fphoto.delete()) {
                    //Log.e(TAG, "Failed to delete non-inserted photo");
                }
            }


            // Open the photo in LabActivity.
            final Intent intent = new Intent(this, PhotoActivity.class);
            intent.putExtra(PhotoActivity.EXTRA_PHOTO_URI, uri);
            intent.putExtra(PhotoActivity.EXTRA_PHOTO_DATA_PATH, photoPath);
            startActivity(intent);
        }

        /// reset take photo boolean
        isTakePicture = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Log.i(TAG, "called onCreateOptionsMenu");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_camera, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);

        long action = item.getItemId();

        if (action == R.id.action_rgba) {
            viewMode = VIEW_MODE_RGBA;
        } else if (action == R.id.action_facedetect) {
            viewMode = VIEW_MODE_FACE_DETECT;
        }
        return super.onOptionsItemSelected(item);
    }



    /*************** Button handling *********************/

    public void onClickReverseCamera(View v) {
        mCameraIndex++;
        if (mCameraIndex == mNumCameras) {
            mCameraIndex = 0;
        }
        recreate();
    }

    public void onClickCamera(View v) {
        isTakePicture = true;
    }
}