package com.tcs.innovations.omniabot;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Face;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;

public class FaceDetectionActivity extends AppCompatActivity {

    private TextView title, faceData;
    private Button detectButton, takePhotoBtn;
    private ImageView imageView, thumbnail;
    private LinearLayout buttonContainer;
    // The image selected to detect.
    private Bitmap mBitmap;

    private static final int REQUEST_TAKE_PHOTO = 0;

    // The URI of photo taken with camera
    private Uri mImageUri;
    // Progress dialog popped up when communicating with server.
    ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_detection);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle("Please wait");

        title = (TextView) findViewById(R.id.title);
        faceData = (TextView) findViewById(R.id.face_data);
        detectButton = (Button) findViewById(R.id.detect_btn);
        takePhotoBtn = (Button) findViewById(R.id.take_photo_btn);
        buttonContainer = (LinearLayout) findViewById(R.id.btn_container);
        imageView = (ImageView) findViewById(R.id.image_view);
        thumbnail = (ImageView) findViewById(R.id.thumbnail);

        detectButton.setEnabled(false);

        takePhotoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if(intent.resolveActivity(getPackageManager()) != null) {
                    // Save the photo taken to a temporary file.
                    File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    try {
                        File file = File.createTempFile("IMG_", ".jpg", storageDir);
                        mImageUri = Uri.fromFile(file);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
                        startActivityForResult(intent, REQUEST_TAKE_PHOTO);
                    } catch (IOException e) {
                        setInfo(e.getMessage());
                        Log.e("FaceDetection", e.toString());
                    }
                }
            }
        });

        detectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Put the image into an input stream for detection.
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

                // Start a background task to detect faces in the image.
                new DetectionTask().execute(inputStream);

                // Prevent button click during detecting.
                setAllButtonsEnabledStatus(false);
            }
        });
    }

    // Set the information panel on screen.
    private void setInfo(String info) {
        Toast.makeText(getApplicationContext(), info, Toast.LENGTH_SHORT).show();
    }

    // Deal with the result of selection of the photos and faces.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode)
        {
            case REQUEST_TAKE_PHOTO:
                if (resultCode == RESULT_OK) {
                    Uri imageUri;
                    if (data == null || data.getData() == null) {
                        imageUri = mImageUri;
                    } else {
                        imageUri = data.getData();
                    }

                    mImageUri = imageUri;

                    mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                            mImageUri, getContentResolver());
                    if (mBitmap != null) {
                        // Show the image on screen.
                        imageView.setImageBitmap(mBitmap);

                        // Add detection log.
                        Log.i("FaceDetection","Image: " + mImageUri + " resized to " + mBitmap.getWidth()
                                + "x" + mBitmap.getHeight());

                        detectButton.setEnabled(true);
                    }

                }
                break;
            default:
                break;
        }
    }

    // Background task of face detection.
    private class DetectionTask extends AsyncTask<InputStream, String, Face[]> {
        private boolean mSucceed = true;

        @Override
        protected Face[] doInBackground(InputStream... params) {
            // Get an instance of face service client to detect faces in image.
            FaceServiceClient faceServiceClient = new FaceServiceRestClient(getString(R.string.subscription_key));
            try {
                publishProgress("Detecting...");

                // Start detection.
                return faceServiceClient.detect(
                        params[0],  /* Input stream of image to detect */
                        true,       /* Whether to return face ID */
                        true,       /* Whether to return face landmarks */
                        /* Which face attributes to analyze, currently we support:
                           age,gender,headPose,smile,facialHair */
                        new FaceServiceClient.FaceAttributeType[] {
                                FaceServiceClient.FaceAttributeType.Age,
                                FaceServiceClient.FaceAttributeType.Gender,
                                FaceServiceClient.FaceAttributeType.Glasses,
                                FaceServiceClient.FaceAttributeType.Smile,
                                FaceServiceClient.FaceAttributeType.HeadPose
                        });
            } catch (Exception e) {
                mSucceed = false;
                e.printStackTrace();
                publishProgress(e.getMessage());
//                addLog(e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog.show();
//            addLog("Request: Detecting in image " + mImageUri);
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            mProgressDialog.setMessage(progress[0]);
            setInfo(progress[0]);
        }

        @Override
        protected void onPostExecute(Face[] result) {
            if (mSucceed) {
                Log.i("FaceDetection","Response: Success. Detected " + (result == null ? 0 : result.length)
                        + " face(s) in " + mImageUri);
            }

            // Show the result on screen when detection is done.
            setUiAfterDetection(result, mSucceed);
        }
    }

    // Show the result on screen when detection is done.
    private void setUiAfterDetection(Face[] result, boolean succeed) {
        // Detection is done, hide the progress dialog.
        mProgressDialog.dismiss();

        // Enable all the buttons.
        setAllButtonsEnabledStatus(true);

        // Disable button "detect" as the image has already been detected.
        setDetectButtonEnabledStatus(false);

        if (succeed) {
            // The information about the detection result.
            String detectionResult;
            if (result != null && result.length != 0) {
                detectionResult = result.length + " face"
                        + (result.length != 1 ? "s" : "") + " detected";

                // Show the detected faces on original image.
                imageView.setImageBitmap(ImageHelper.drawFaceRectanglesOnBitmap(
                        mBitmap, result, true));

//                // Set the adapter of the ListView which contains the details of the detected faces.
//                FaceListAdapter faceListAdapter = new FaceListAdapter(result);
//
//                // Show the detailed list of detected faces.
//                ListView listView = (ListView) findViewById(R.id.list_detected_faces);
//                listView.setAdapter(faceListAdapter);

                Face face = result[0];

                // Show the face details.
                DecimalFormat formatter = new DecimalFormat("#0.0");
                String face_description = "Age: " + formatter.format(face.faceAttributes.age) + "\n"
                        + "Gender: " + face.faceAttributes.gender + "\n"
//                        + "Head pose(in degree): roll(" + formatter.format(face.faceAttributes.headPose.roll) + "), "
//                        + "yaw(" + formatter.format(face.faceAttributes.headPose.yaw) + ")\n"
                        + "Glasses: " + face.faceAttributes.glasses + "\n"
                        + "Smile: " + formatter.format(face.faceAttributes.smile);

                try {
                    thumbnail.setImageBitmap(ImageHelper.generateFaceThumbnail(
                            mBitmap, face.faceRectangle));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                faceData.setText(face_description);

            } else {
                detectionResult = "0 face detected";
            }
            setInfo(detectionResult);
        }

        mImageUri = null;
        mBitmap = null;
    }

    // Set whether the buttons are enabled.
    private void setDetectButtonEnabledStatus(boolean isEnabled) {
        detectButton.setEnabled(isEnabled);
    }

    // Set whether the buttons are enabled.
    private void setAllButtonsEnabledStatus(boolean isEnabled) {
        detectButton.setEnabled(isEnabled);

        takePhotoBtn.setEnabled(isEnabled);

    }
}
