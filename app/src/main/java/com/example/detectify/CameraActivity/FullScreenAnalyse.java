package com.example.detectify.CameraActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import com.example.detectify.Detector.Yolov5TFLiteDetector;
import com.example.detectify.CameraActivity.ImageProcess;
import com.example.detectify.Utility.Recognition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class FullScreenAnalyse implements ImageAnalysis.Analyzer {
    private static final String TAG = "FullScreenAnalyse";

    public static class Result{

        public Result(long costTime, Bitmap bitmap) {
            this.costTime = costTime;
            this.bitmap = bitmap;
        }
        long costTime;
        Bitmap bitmap;
    }

    ImageView boxLabelCanvas;
    PreviewView previewView;
    int rotation;
    private TextView inferenceTimeTextView;
    private TextView frameSizeTextView;
    ImageProcess imageProcess;
    private Yolov5TFLiteDetector yolov5TFLiteDetector;
    private TextView objectCountsTextView;

    private int nextPotId = 0;
    public HashMap<String, Set<Integer>> spikePerPotSet = new HashMap<>();
    private HashMap<String, Integer> spikePerPot = new HashMap<>();
    private boolean detectionBit = false;

    public FullScreenAnalyse(Context context,
                             PreviewView previewView,
                             ImageView boxLabelCanvas,
                             int rotation,
                             TextView inferenceTimeTextView,
                             TextView frameSizeTextView,
                             Yolov5TFLiteDetector yolov5TFLiteDetector,
                             TextView objectCountsTextView) {
        this.previewView = previewView;
        this.boxLabelCanvas = boxLabelCanvas;
        this.rotation = rotation;
        this.inferenceTimeTextView = inferenceTimeTextView;
        this.frameSizeTextView = frameSizeTextView;
        this.imageProcess = new ImageProcess();
        this.yolov5TFLiteDetector = yolov5TFLiteDetector;
        this.objectCountsTextView = objectCountsTextView;

        // Initialize the first pot
        spikePerPotSet.put("Pot" + nextPotId, new HashSet<>());
        spikePerPot.put("Pot" + nextPotId, 0);
    }

    public HashMap<String, Integer> getSpikePerPot() {
        return spikePerPot;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        int previewHeight = previewView.getHeight();
        int previewWidth = previewView.getWidth();

        // Here Observable puts the logic of image analysis into sub-threads for calculation,
        // and gets back the corresponding data when rendering the UI to avoid front-end UI lags.
        Observable.create( (ObservableEmitter<Result> emitter) -> {
            long start = System.currentTimeMillis();
            Log.i(TAG,""+previewWidth+'/'+previewHeight);

            byte[][] yuvBytes = new byte[3][];
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            int imageHeight = image.getHeight();
            int imagewWidth = image.getWidth();

            imageProcess.fillBytes(planes, yuvBytes);
            int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            int[] rgbBytes = new int[imageHeight * imagewWidth];
            imageProcess.YUV420ToARGB8888(
                    yuvBytes[0],
                    yuvBytes[1],
                    yuvBytes[2],
                    imagewWidth,
                    imageHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes);

            // Original image bitmap
            Bitmap imageBitmap = Bitmap.createBitmap(imagewWidth, imageHeight, Bitmap.Config.ARGB_8888);
            imageBitmap.setPixels(rgbBytes, 0, imagewWidth, 0, 0, imagewWidth, imageHeight);

            // The image adapts to the screen fill_start format bitmap
            double scale = Math.max(
                    previewHeight / (double) (rotation % 180 == 0 ? imagewWidth : imageHeight),
                    previewWidth / (double) (rotation % 180 == 0 ? imageHeight : imagewWidth)
            );
            Matrix fullScreenTransform = imageProcess.getTransformationMatrix(
                    imagewWidth, imageHeight,
                    (int) (scale * imageHeight), (int) (scale * imagewWidth),
                    rotation % 180 == 0 ? 90 : 0, false
            );

            // Full size bitmap adapted to preview
            Bitmap fullImageBitmap = Bitmap.createBitmap(imageBitmap, 0, 0, imagewWidth, imageHeight, fullScreenTransform, false);
            // Crop the bitmap to the same size as the preview on the screen
            Bitmap cropImageBitmap = Bitmap.createBitmap(
                    fullImageBitmap, 0, 0,
                    previewWidth, previewHeight
            );

            // Model input bitmap
            Matrix previewToModelTransform =
                    imageProcess.getTransformationMatrix(
                            cropImageBitmap.getWidth(), cropImageBitmap.getHeight(),
                            yolov5TFLiteDetector.getInputSize().getWidth(),
                            yolov5TFLiteDetector.getInputSize().getHeight(),
                            0, false);
            Bitmap modelInputBitmap = Bitmap.createBitmap(cropImageBitmap, 0, 0,
                    cropImageBitmap.getWidth(), cropImageBitmap.getHeight(),
                    previewToModelTransform, false);

            Matrix modelToPreviewTransform = new Matrix();
            previewToModelTransform.invert(modelToPreviewTransform);

            ArrayList<Recognition> recognitions = yolov5TFLiteDetector.detect(modelInputBitmap);

            Bitmap emptyCropSizeBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
            Canvas cropCanvas = new Canvas(emptyCropSizeBitmap);
            // border brush
            Paint boxPaint = new Paint();
            boxPaint.setStrokeWidth(5);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setColor(Color.RED);
            // Font brush
            Paint textPain = new Paint();
            textPain.setTextSize(50);
            textPain.setColor(Color.RED);
            textPain.setStyle(Paint.Style.FILL);

            // Object counts for current frame
            HashMap<String, Integer> objectCounts = new HashMap<>();
            for (Recognition res : recognitions) {
                RectF location = res.getLocation();
                String label = res.getLabelName();
                float confidence = res.getConfidence();
                modelToPreviewTransform.mapRect(location);
                cropCanvas.drawRect(location, boxPaint);
                cropCanvas.drawText(label + ":" + String.format("%.2f", confidence), location.left, location.top, textPain);

                Log.d(TAG, label);
                objectCounts.put(label, objectCounts.getOrDefault(label, 0) + 1);

            }

            // Process detections
            if (objectCounts.isEmpty()) {
                detectionBit = true;
                if (spikePerPotSet.get("Pot" + nextPotId).size() <= 1) {
                    spikePerPotSet.get("Pot" + nextPotId).add(-1);
                }
            } else {
                if (objectCounts.getOrDefault("Pot", 0) > 0) {
                    if (detectionBit) {
                        nextPotId++;
                        spikePerPotSet.put("Pot" + nextPotId, new HashSet<>());
                        spikePerPot.put("Pot" + nextPotId, 0);
                        detectionBit = false;
                    }
                    if (objectCounts.getOrDefault("Wheat Spike", 0) > 0) {
                        spikePerPotSet.get("Pot" + nextPotId).add(objectCounts.get("Wheat Spike"));
                    }
                }
            }

            if (spikePerPotSet.get("Pot" + nextPotId).size() >= 1) {
                spikePerPot.put("Pot" + nextPotId, spikePerPotSet.get("Pot" + nextPotId).stream().max(Integer::compareTo).orElse(0));
            } else {
                spikePerPot.put("Pot" + nextPotId, 0);
            }

            //Log.d("spike", objectCounts.toString());
            //Log.d("spike", spikePerPotSet.toString());
            Log.d("spike", spikePerPot.toString());

            long end = System.currentTimeMillis();
            long costTime = (end - start);
            image.close();
            emitter.onNext(new Result(costTime, emptyCropSizeBitmap));
        }).subscribeOn(Schedulers.io()) // The observer is defined here, which is the thread of the above code. If it is not defined, it is synchronized with the main thread, not asynchronous.
                // Here is back to the main thread, the observer receives the data sent by the emitter for processing
                .observeOn(AndroidSchedulers.mainThread())
                // Here is the callback data returned to the main thread to process the sub-thread.
                .subscribe((Result result) -> {
                    boxLabelCanvas.setImageBitmap(result.bitmap);
                    frameSizeTextView.setText(previewHeight + "x" + previewWidth);
                    inferenceTimeTextView.setText(Long.toString(result.costTime) + "ms");
                    // Update objectCountsTextView with the latest count
                    objectCountsTextView.setText(generateCountsText());
                });
    }

    private String generateCountsText() {
        // Always reset the StringBuilder to ensure it only contains the latest counts
        StringBuilder countsText = new StringBuilder();

        // Only display the current pot's count
        countsText.append("Pot ").append(nextPotId)
                .append(": ").append(spikePerPot.getOrDefault("Pot" + nextPotId, 0))
                .append(" wheat spikes");

        return countsText.toString();
    }
}
