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
import org.tensorflow.lite.gpu.GpuDelegateFactory;
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
import java.util.List;
import java.util.PriorityQueue;

public class Yolov5TFLiteDetector {
    private static final String TAG = "Yolov5TFLiteDetector";

    private final Size INPUT_SIZE = new Size(640, 640);
    private final int[] OUTPUT_SIZE = new int[]{1, 25200, 7};
    private Boolean IS_INT8 = false;
    public float DETECT_THRESHOLD = 0.50f;
    public float IOU_THRESHOLD = 0.50f;
    private final float IOU_CLASS_DUPLICATED_THRESHOLD = 0.70f;
    private final String MODEL_YOLOV5S = "yolov5s-fp16.tflite";
    //private final String MODEL_YOLOV5M = "yolov5m-fp16.tflite";
    private final String LABEL_FILE = "label.txt";
    MetadataExtractor.QuantizationParams input5SINT8QuantParams = new MetadataExtractor.QuantizationParams(0.003921568859368563f, 0);
    MetadataExtractor.QuantizationParams output5SINT8QuantParams = new MetadataExtractor.QuantizationParams(0.00828352477401495f, 5);
    private String MODEL_FILE;

    private Interpreter tflite;
    private List<String> associatedAxisLabels;
    Interpreter.Options options = new Interpreter.Options();

    public String getModelFile() {
        return this.MODEL_FILE;
    }

    public void setModelFile(String modelFile) {
        switch (modelFile) {
            case "yolov5s-fp16":
                IS_INT8 = false;
                MODEL_FILE = MODEL_YOLOV5S;
                break;
            /*
            case "yolov5m-fp16":
                IS_INT8 = false;
                MODEL_FILE = MODEL_YOLOV5M;
                break;
            */
            default:
                Log.i("tfliteSupport", "Only yolov5n/s/m can be loaded!");
        }
    }

    public String getLabelFile() {
        return this.LABEL_FILE;
    }

    public Size getInputSize() {
        return this.INPUT_SIZE;
    }

    public int[] getOutputSize() {
        return this.OUTPUT_SIZE;
    }

    public void setIOUThreshold(float threshold) {
        this.IOU_THRESHOLD = threshold;
    }

    public void setDetectThreshold(float threshold) {
        this.DETECT_THRESHOLD = threshold;
    }

    /**
     * To initialize the model, you can load the corresponding agent in advance through addNNApiDelegate(),
     * addGPUDelegate()
     *
     * @param activity
     */

    public void initialModel(Context activity) {
        Log.i("tfliteSupport", "Loading model: " + MODEL_FILE);
        // Initialise the model
        try {
            ByteBuffer tfliteModel = FileUtil.loadMappedFile(activity, MODEL_FILE);
            tflite = new Interpreter(tfliteModel, options);
            Log.i("tfliteSupport", "Success reading model: " + MODEL_FILE);

            associatedAxisLabels = FileUtil.loadLabels(activity, LABEL_FILE);
            Log.i("tfliteSupport", "Success reading label: " + LABEL_FILE);

        } catch (IOException e) {
            Log.e("tfliteSupport", "Error reading model or label: ", e);
            Toast.makeText(activity, "load model error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Detection steps
     *
     * @param bitmap
     * @return
     */
    public ArrayList<Recognition> detect(Bitmap bitmap) {
        // The input of yolov5s-tflite is: [1, 640, 640,3]. Each frame of the camera image needs to be resized and then normalized.
        TensorImage yolov5TfliteInput;
        ImageProcessor imageProcessor;
        if (IS_INT8) {
            imageProcessor =
                    new ImageProcessor.Builder()
                            .add(new ResizeOp(INPUT_SIZE.getHeight(), INPUT_SIZE.getWidth(), ResizeOp.ResizeMethod.BILINEAR))
                            .add(new NormalizeOp(0, 255))
                            .add(new QuantizeOp(input5SINT8QuantParams.getZeroPoint(), input5SINT8QuantParams.getScale()))
                            .add(new CastOp(DataType.UINT8))
                            .build();
            yolov5TfliteInput = new TensorImage(DataType.UINT8);
        } else {
            imageProcessor =
                    new ImageProcessor.Builder()
                            .add(new ResizeOp(INPUT_SIZE.getHeight(), INPUT_SIZE.getWidth(), ResizeOp.ResizeMethod.BILINEAR))
                            .add(new NormalizeOp(0, 255))
                            .build();
            yolov5TfliteInput = new TensorImage(DataType.FLOAT32);
        }

        yolov5TfliteInput.load(bitmap);
        yolov5TfliteInput = imageProcessor.process(yolov5TfliteInput);

        // The output of yolov5s-tflite is: [1, 25200, 7]. You can find the relevant tflite model from the GitHub release of v5. The output is [0,1], processed to 640.
        TensorBuffer probabilityBuffer;
        if (IS_INT8) {
            probabilityBuffer = TensorBuffer.createFixedSize(OUTPUT_SIZE, DataType.UINT8);
        } else {
            probabilityBuffer = TensorBuffer.createFixedSize(OUTPUT_SIZE, DataType.FLOAT32);
        }

        // Inference calculation
        if (null != tflite) {
            // Here tflite will add a batch=1 latitude by default.
            tflite.run(yolov5TfliteInput.getBuffer(), probabilityBuffer.getBuffer());
        }

        // The inverse quantization output here needs to be executed after the model tflite.run.
        if (IS_INT8) {
            TensorProcessor tensorProcessor = new TensorProcessor.Builder()
                    .add(new DequantizeOp(output5SINT8QuantParams.getZeroPoint(), output5SINT8QuantParams.getScale()))
                    .build();
            probabilityBuffer = tensorProcessor.process(probabilityBuffer);
        }

        // The output data is flattened out
        float[] recognitionArray = probabilityBuffer.getFloatArray();
        // Here the flatten array is re-parsed (xywh, obj, classes).
        ArrayList<Recognition> allRecognitions = new ArrayList<>();
        for (int i = 0; i < OUTPUT_SIZE[1]; i++) {
            int gridStride = i * OUTPUT_SIZE[2];
            // Since the author of yolov5 divided the output by the image size when exporting tflite, it needs to be multiplied back here.
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
//            if(i % 1000 == 0){
//                Log.i("tfliteSupport","x,y,w,h,conf:"+x+","+y+","+w+","+h+","+confidence);
//            }
            int labelId = 0;
            float maxLabelScores = 0.f;
            for (int j = 0; j < classScores.length; j++) {
                if (classScores[j] > maxLabelScores) {
                    maxLabelScores = classScores[j];
                    labelId = j;
                }
            }

            Recognition r = new Recognition(
                    labelId,
                    "",
                    maxLabelScores,
                    confidence,
                    new RectF(xmin, ymin, xmax, ymax));
            allRecognitions.add(
                    r);
        }
        // Log.i("tfliteSupport", "recognize data size: "+allRecognitions.size());

        // Non-maximum suppression output
        ArrayList<Recognition> nmsRecognitions = nms(allRecognitions);
        //The second non-maximum suppression is to filter out those cases where the same target is recognized and more than 2 target borders are of different categories.
        ArrayList<Recognition> nmsFilterBoxDuplicationRecognitions = nmsAllClass(nmsRecognitions);

        // Update label information
        for (Recognition recognition : nmsFilterBoxDuplicationRecognitions) {
            int labelId = recognition.getLabelId();
            String labelName = associatedAxisLabels.get(labelId);
            recognition.setLabelName(labelName);
        }

        return nmsFilterBoxDuplicationRecognitions;
    }

    /**
     * non-maximum suppression
     *
     * @param allRecognitions
     * @return
     */
    protected ArrayList<Recognition> nms(ArrayList<Recognition> allRecognitions) {
        ArrayList<Recognition> nmsRecognitions = new ArrayList<Recognition>();

        // Traverse each category and do nms under each category
        for (int i = 0; i < OUTPUT_SIZE[2] - 5; i++) {
            // Here, make a queue for each category, and put the ones with higher labelScore at the front.
            PriorityQueue<Recognition> pq =
                    new PriorityQueue<Recognition>(
                            25200,
                            new Comparator<Recognition>() {
                                @Override
                                public int compare(final Recognition l, final Recognition r) {
                                    // Intentionally reversed to put high confidence at the head of the queue.
                                    return Float.compare(r.getConfidence(), l.getConfidence());
                                }
                            });

            // The same categories are filtered out, and obj must be greater than the set threshold.
            for (int j = 0; j < allRecognitions.size(); ++j) {
//                if (allRecognitions.get(j).getLabelId() == i) {
                if (allRecognitions.get(j).getLabelId() == i && allRecognitions.get(j).getConfidence() > DETECT_THRESHOLD) {
                    pq.add(allRecognitions.get(j));
//                    Log.i("tfliteSupport", allRecognitions.get(j).toString());
                }
            }

            // nms loop traversal
            while (pq.size() > 0) {
                // Take out the one with the highest probability first
                Recognition[] a = new Recognition[pq.size()];
                Recognition[] detections = pq.toArray(a);
                Recognition max = detections[0];
                nmsRecognitions.add(max);
                pq.clear();

                for (int k = 1; k < detections.length; k++) {
                    Recognition detection = detections[k];
                    if (boxIou(max.getLocation(), detection.getLocation()) < IOU_THRESHOLD) {
                        pq.add(detection);
                    }
                }
            }
        }
        return nmsRecognitions;
    }

    /**
     * Probability is used to perform non-maximum suppression on all data without distinguishing categories. The largest one is taken out first.
     *
     * @param allRecognitions
     * @return
     */

    protected ArrayList<Recognition> nmsAllClass(ArrayList<Recognition> allRecognitions) {
        ArrayList<Recognition> nmsRecognitions = new ArrayList<Recognition>();

        PriorityQueue<Recognition> pq =
                new PriorityQueue<Recognition>(
                        100,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(final Recognition l, final Recognition r) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(r.getConfidence(), l.getConfidence());
                            }
                        });

        // The same categories are filtered out, and obj must be greater than the set threshold.
        for (int j = 0; j < allRecognitions.size(); ++j) {
            if (allRecognitions.get(j).getConfidence() > DETECT_THRESHOLD) {
                pq.add(allRecognitions.get(j));
            }
        }

        while (pq.size() > 0) {
            // Take out the one with the highest probability first
            Recognition[] a = new Recognition[pq.size()];
            Recognition[] detections = pq.toArray(a);
            Recognition max = detections[0];
            nmsRecognitions.add(max);
            pq.clear();

            for (int k = 1; k < detections.length; k++) {
                Recognition detection = detections[k];
                if (boxIou(max.getLocation(), detection.getLocation()) < IOU_CLASS_DUPLICATED_THRESHOLD) {
                    pq.add(detection);
                }
            }
        }
        return nmsRecognitions;
    }

    protected float boxIou(RectF a, RectF b) {
        float intersection = boxIntersection(a, b);
        float union = boxUnion(a, b);
        if (union <= 0) return 1;
        return intersection / union;
    }

    protected float boxIntersection(RectF a, RectF b) {
        float maxLeft = a.left > b.left ? a.left : b.left;
        float maxTop = a.top > b.top ? a.top : b.top;
        float minRight = a.right < b.right ? a.right : b.right;
        float minBottom = a.bottom < b.bottom ? a.bottom : b.bottom;
        float w = minRight -  maxLeft;
        float h = minBottom - maxTop;

        if (w < 0 || h < 0) return 0;
        float area = w * h;
        return area;
    }

    protected float boxUnion(RectF a, RectF b) {
        float i = boxIntersection(a, b);
        float u = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i;
        return u;
    }

    /**
     * Add NNapi proxy
     */
    public void addNNApiDelegate() {
        NnApiDelegate nnApiDelegate = null;
        // Initialize interpreter with NNAPI delegate for Android Pie or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            NnApiDelegate.Options nnApiOptions = new NnApiDelegate.Options();
//            nnApiOptions.setAllowFp16(true);
//            nnApiOptions.setUseNnapiCpu(true);
            //ANEURALNETWORKS_PREFER_LOW_POWER：Prefer to perform in a manner that minimizes battery consumption. This setting is suitable for compilations that are performed frequently.
            //ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER：Prefers to return a single answer as quickly as possible, even if it uses more battery. This is the default value.
            //ANEURALNETWORKS_PREFER_SUSTAINED_SPEED：Tends to maximize throughput of consecutive frames, for example when processing consecutive frames from a camera.
//            nnApiOptions.setExecutionPreference(NnApiDelegate.Options.EXECUTION_PREFERENCE_SUSTAINED_SPEED);
//            nnApiDelegate = new NnApiDelegate(nnApiOptions);
            nnApiDelegate = new NnApiDelegate();
            options.addDelegate(nnApiDelegate);
            Log.i("tfliteSupport", "using nnapi delegate.");
        }
    }

    /**
     * Add GPU agent
     */
    public void addGPUDelegate() {
        CompatibilityList compatibilityList = new CompatibilityList();
        if(compatibilityList.isDelegateSupportedOnThisDevice()){
            // if the device has a supported GPU, add the GPU delegate
            GpuDelegateFactory.Options delegateOptions = compatibilityList.getBestOptionsForThisDevice();
            GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
            options.addDelegate(gpuDelegate);
            Log.i("tfliteSupport", "using gpu delegate.");

        } else {
            // if the GPU is not supported, run on 4 threads    
            addThread(4);
            Log.i("tfliteSupport", "GPU is not supported on this device.");
        }
    }

    /**
     * Add number of threads
     * @param thread
     */
    public void addThread(int thread) {
        options.setNumThreads(thread);
    }
}