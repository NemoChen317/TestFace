package com.home.testface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int IMAGE_REQUEST_CODE = 55;
    private Paint paint , textPaint;
    private ImageView img;
    private Button chooseBtn;
    private ProgressBar progressBar;
    private static final String TAG = MainActivity.class.getSimpleName();
    private volatile FirebaseVisionFaceDetector detector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);
        paint = new Paint();
        textPaint = new Paint();
        img = findViewById(R.id.img);
        progressBar = findViewById(R.id.progressBar);
        chooseBtn = findViewById(R.id.button);

        chooseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                img.setVisibility(View.INVISIBLE);
                Intent intent = new Intent(
                        Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, IMAGE_REQUEST_CODE);
            }
        });
//        initMlkiFace(image);
    }

    private void initMlkiFace(final Bitmap bitmap) {
        if (bitmap != null) {
            FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
            if (detector == null) {
                FirebaseVisionFaceDetectorOptions options =
                        new FirebaseVisionFaceDetectorOptions.Builder()
                                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
//                                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                                .build();
                detector = FirebaseVision.getInstance().getVisionFaceDetector(options);
            }
            detector.detectInImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
                @Override
                public void onSuccess(List<FirebaseVisionFace> faces) {
                    Log.d(TAG, "onSuccess: 識別成功");
                    Log.d(TAG, "onSuccess: face size" + faces.size());
                    ArrayList<FacePoint> facePoints = new ArrayList<>();
                    facePoints.clear();

                    if(faces.size() >= 1){
                        List<Rect> rects = new ArrayList<>();
                        for(int i = 0 ; i < faces.size() ; i++){
                            rects.add(faces.get(i).getBoundingBox());
                        }
                        drawMlkiLandmarks(bitmap, rects);
                    }

                    try {
                        if (detector != null) {
                            detector.close();
                            detector = null;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d(TAG, "onFailure: 識別失敗" + e);
                    try {
                        if (detector != null) {
                            detector.close();
                            detector = null;
                        }
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            });
        }
    }

    public Bitmap drawMlkiLandmarks(Bitmap bitmap, List<Rect> rects) {

        Log.d(TAG, "drawMlkiLandmarks: rects count = " + rects.size());
        Bitmap bitmap2 = bitmap.copy(bitmap.getConfig(), true);
        Canvas canvas = new Canvas(bitmap2);

        paint.setColor(Color.RED);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);

        canvas.drawBitmap(bitmap2 , 0 , 0 , null);
        Bitmap mosaicBitmap = makeMosaicBitmap(bitmap2);

        for(int i = 0 ; i < rects.size() ; i++) {
//            canvas.drawRect(rects.get(i), paint);

            canvas.drawBitmap(mosaicBitmap , rects.get(i) , new RectF(rects.get(i)) , paint);
        }

        progressBar.setVisibility(View.GONE);
        img.setVisibility(View.VISIBLE);
        img.setImageBitmap(bitmap2);

        return bitmap2;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //在相簿裡面選擇好相片之後調回到現在的這個activity中
        switch (requestCode) {
            case IMAGE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {//resultcode是setResult裡面設定的code值
                    try {
                        Uri selectedImage = data.getData(); //獲取系統返回的照片的Uri
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        Cursor cursor = getContentResolver().query(selectedImage,
                                filePathColumn, null, null, null);//從系統表中查詢指定Uri對應的照片
                        cursor.moveToFirst();
                        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                        String path = cursor.getString(columnIndex); //獲取照片路徑
                        cursor.close();
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2;

                        Bitmap bitmap;
                        try {
                            bitmap = BitmapFactory.decodeFile(path , options);
                        }catch (OutOfMemoryError e){
                            options.inSampleSize = 4;
                            bitmap = BitmapFactory.decodeFile(path , options);
                        }


                        progressBar.setVisibility(View.VISIBLE);
                        initMlkiFace(bitmap);
//                        img.setVisibility(View.VISIBLE);
//                        img.setImageBitmap(bitmap);
                    } catch (Exception e) {
                        // TODO Auto-generatedcatch block
                        e.printStackTrace();
                    }
                }
                break;
        }
    }

    private Bitmap makeMosaicBitmap(Bitmap bitmap) {

        int w = Math.round(bitmap.getWidth() / 16f);
        int h = Math.round(bitmap.getHeight() / 16f);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (bitmap != null) {
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, false);
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
        }

        return bitmap;
    }
}
