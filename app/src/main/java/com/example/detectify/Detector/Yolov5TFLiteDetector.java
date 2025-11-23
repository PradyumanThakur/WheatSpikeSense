package com.example.detectify.Detector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import com.example.detectify.Utility.Recognition;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.DequantizeOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.common.ops.QuantizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.metadata.MetadataExtractor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class Yolov5TFLiteDetector {
    private static final String TAG = "Yolov5TFLiteDetector";

    private final Size INPUT_SIZE = new Size(640, 640);
    private final int[] OUTPUT_SIZE = new int[]{1, 25200, 7};
    private Boolean IS_INT8 = false;
    public float DETECT_THRESHOLD = 0.50f;
    public float IOU_THRESHOLD = 0.50f;
    private final float IOU_CLASS_DUPLICATED_THRESHOLD = 0.70f;
    private final String MODEL_YOLOV5S = "yolov5s-fp16.tflite";
    private final String LABEL_FILE = "label.txt";
    private String MODEL_FILE;

    private Interpreter tflite;
    private Interpreter.Options options = new Interpreter.Options();
    private ArrayList<String> associatedAxisLabels;

    // reusable buffers
    private TensorImage inputImage;
    private TensorBuffer outputBuffer;
    private ImageProcessor imageProcessor;
    private MetadataExtractor.QuantizationParams input5SINT8QuantParams = new MetadataExtractor.QuantizationParams(0.003921568859368563f, 0);
    private MetadataExtractor.QuantizationParams output5SINT8QuantParams = new MetadataExtractor.QuantizationParams(0.00828352477401495f, 5);

    public String getModelFile() {
        return this.MODEL_FILE;
    }

    public void setModelFile(String modelFile) {
        switch (modelFile) {
            case "yolov5s-fp16":
                IS_INT8 = false;
                MODEL_FILE = MODEL_YOLOV5S;
                break;
            default:
                Log.i(TAG, "Only yolov5s-fp16 is supported!");
        }
    }

    public String getLabelFile() {
        return LABEL_FILE;
    }

    public Size getInputSize() {
        return INPUT_SIZE;
    }

    public int[] getOutputSize() {
        return OUTPUT_SIZE;
    }

    public void setIOUThreshold(float threshold) {
        this.IOU_THRESHOLD = threshold;
    }

    public void setDetectThreshold(float threshold) {
        this.DETECT_THRESHOLD = threshold;
    }

    public void initialModel(Context activity) {
        Log.i(TAG, "Loading model: " + MODEL_FILE);
        try {
            ByteBuffer tfliteModel = FileUtil.loadMappedFile(activity, MODEL_FILE);
            tflite = new Interpreter(tfliteModel, options);

            associatedAxisLabels = (ArrayList<String>) FileUtil.loadLabels(activity, LABEL_FILE);

            // reusable input and output buffers
            inputImage = IS_INT8 ? new TensorImage(DataType.UINT8) : new TensorImage(DataType.FLOAT32);
            outputBuffer = IS_INT8 ? TensorBuffer.createFixedSize(OUTPUT_SIZE, DataType.UINT8)
                    : TensorBuffer.createFixedSize(OUTPUT_SIZE, DataType.FLOAT32);

            ImageProcessor.Builder builder = new ImageProcessor.Builder()
                    .add(new ResizeOp(INPUT_SIZE.getHeight(), INPUT_SIZE.getWidth(), ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(0, 255));
            if (IS_INT8) {
                builder.add(new QuantizeOp(input5SINT8QuantParams.getZeroPoint(), input5SINT8QuantParams.getScale()))
                        .add(new CastOp(DataType.UINT8));
            }
            imageProcessor = builder.build();

            Log.i(TAG, "Model and labels loaded successfully.");
        } catch (IOException e) {
            Log.e(TAG, "Error reading model or label: ", e);
            Toast.makeText(activity, "load model error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public ArrayList<Recognition> detect(Bitmap bitmap) {
        inputImage.load(bitmap);
        inputImage = imageProcessor.process(inputImage);

        if (tflite != null) {
            tflite.run(inputImage.getBuffer(), outputBuffer.getBuffer());
        }

        if (IS_INT8) {
            TensorProcessor tensorProcessor = new TensorProcessor.Builder()
                    .add(new DequantizeOp(output5SINT8QuantParams.getZeroPoint(), output5SINT8QuantParams.getScale()))
                    .build();
            outputBuffer = tensorProcessor.process(outputBuffer);
        }

        float[] recognitionArray = outputBuffer.getFloatArray();
        ArrayList<Recognition> allRecognitions = new ArrayList<>(OUTPUT_SIZE[1]);

        for (int i = 0; i < OUTPUT_SIZE[1]; i++) {
            int gridStride = i * OUTPUT_SIZE[2];
            float x = recognitionArray[0 + gridStride] * INPUT_SIZE.getWidth();
            float y = recognitionArray[1 + gridStride] * INPUT_SIZE.getHeight();
            float w = recognitionArray[2 + gridStride] * INPUT_SIZE.getWidth();
            float h = recognitionArray[3 + gridStride] * INPUT_SIZE.getHeight();
            int xmin = (int) Math.max(0, x - w / 2.);
            int ymin = (int) Math.max(0, y - h / 2.);
            int xmax = (int) Math.min(INPUT_SIZE.getWidth(), x + w / 2.);
            int ymax = (int) Math.min(INPUT_SIZE.getHeight(), y + h / 2.);
            float confidence = recognitionArray[4 + gridStride];
            float[] classScores = Arrays.copyOfRange(recognitionArray, 5 + gridStride, this.OUTPUT_SIZE[2] + gridStride);

            int labelId = 0;
            float maxLabelScores = 0.f;
            for (int j = 0; j < classScores.length; j++) {
                if (classScores[j] > maxLabelScores) {
                    maxLabelScores = classScores[j];
                    labelId = j;
                }
            }

            Recognition r = new Recognition(labelId, "", maxLabelScores, confidence, new RectF(xmin, ymin, xmax, ymax));
            allRecognitions.add(r);
        }

        ArrayList<Recognition> nmsRecognitions = nms(allRecognitions);
        ArrayList<Recognition> nmsFilterBoxDuplicationRecognitions = nmsAllClass(nmsRecognitions);

        for (Recognition recognition : nmsFilterBoxDuplicationRecognitions) {
            recognition.setLabelName(associatedAxisLabels.get(recognition.getLabelId()));
        }

        return nmsFilterBoxDuplicationRecognitions;
    }

    protected ArrayList<Recognition> nms(ArrayList<Recognition> allRecognitions) {
        ArrayList<Recognition> nmsRecognitions = new ArrayList<>();

        for (int i = 0; i < OUTPUT_SIZE[2] - 5; i++) {
            allRecognitions.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
            boolean[] removed = new boolean[allRecognitions.size()];

            for (int j = 0; j < allRecognitions.size(); j++) {
                if (removed[j]) continue;
                Recognition max = allRecognitions.get(j);
                if (max.getLabelId() != i || max.getConfidence() <= DETECT_THRESHOLD) continue;
                nmsRecognitions.add(max);
                for (int k = j + 1; k < allRecognitions.size(); k++) {
                    if (!removed[k] && allRecognitions.get(k).getLabelId() == i &&
                            boxIou(max.getLocation(), allRecognitions.get(k).getLocation()) > IOU_THRESHOLD) {
                        removed[k] = true;
                    }
                }
            }
        }
        return nmsRecognitions;
    }

    protected ArrayList<Recognition> nmsAllClass(ArrayList<Recognition> allRecognitions) {
        allRecognitions.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        ArrayList<Recognition> nmsRecognitions = new ArrayList<>();
        boolean[] removed = new boolean[allRecognitions.size()];

        for (int i = 0; i < allRecognitions.size(); i++) {
            if (removed[i] || allRecognitions.get(i).getConfidence() <= DETECT_THRESHOLD) continue;
            Recognition max = allRecognitions.get(i);
            nmsRecognitions.add(max);
            for (int j = i + 1; j < allRecognitions.size(); j++) {
                if (!removed[j] &&
                        boxIou(max.getLocation(), allRecognitions.get(j).getLocation()) > IOU_CLASS_DUPLICATED_THRESHOLD) {
                    removed[j] = true;
                }
            }
        }
        return nmsRecognitions;
    }

    protected float boxIou(RectF a, RectF b) {
        float intersection = boxIntersection(a, b);
        float union = boxUnion(a, b);
        if (union <= 0) return 0;
        return intersection / union;
    }

    protected float boxIntersection(RectF a, RectF b) {
        float maxLeft = Math.max(a.left, b.left);
        float maxTop = Math.max(a.top, b.top);
        float minRight = Math.min(a.right, b.right);
        float minBottom = Math.min(a.bottom, b.bottom);
        float w = minRight - maxLeft;
        float h = minBottom - maxTop;
        return (w < 0 || h < 0) ? 0 : w * h;
    }

    protected float boxUnion(RectF a, RectF b) {
        float i = boxIntersection(a, b);
        float u = (a.right - a.left) * (a.bottom - a.top)
                + (b.right - b.left) * (b.bottom - b.top) - i;
        return u;
    }

    public void addNNApiDelegate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NnApiDelegate nnApiDelegate = new NnApiDelegate();
            options.addDelegate(nnApiDelegate);
            Log.i(TAG, "using nnapi delegate.");
        }
    }

    public void addGPUDelegate() {
        CompatibilityList compatibilityList = new CompatibilityList();
        if (compatibilityList.isDelegateSupportedOnThisDevice()) {
            GpuDelegate.Options delegateOptions = compatibilityList.getBestOptionsForThisDevice();
            GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
            options.addDelegate(gpuDelegate);
            Log.i(TAG, "using gpu delegate.");
        } else {
            useMaxCPUThreads();
            Log.i(TAG, "GPU not supported, using CPU threads.");
        }
    }

    public void useMaxCPUThreads() {
        int coreCount = Runtime.getRuntime().availableProcessors();
        addThread(coreCount);
        Log.i(TAG, "Using maximum CPU threads: " + coreCount);
    }

    public void addThread(int threadCount) {
        options.setNumThreads(Math.max(4, threadCount));
    }
}