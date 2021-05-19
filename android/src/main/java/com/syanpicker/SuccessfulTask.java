package com.syanpicker;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.GuardedAsyncTask;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.luck.picture.lib.PictureSelector;
import com.luck.picture.lib.entity.LocalMedia;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SuccessfulTask extends GuardedAsyncTask<Void, Void> {
    private ReactContext reactContext;

    private Callback mPickerCallback; // 保存回调

    private final Promise mPickerPromise; // 保存Promise

    private final ReadableMap cameraOptions; // 保存图片选择/相机选项

    private List<LocalMedia> selectList = new ArrayList<>();

    private final Intent intent;

    protected SuccessfulTask(
            ReactContext reactContext,
            Callback mPickerCallback,
            Promise mPickerPromise,
            ReadableMap cameraOptions,
            List<LocalMedia> selectList,
            Intent intent
    ) {
        super(reactContext);
        this.reactContext = reactContext;
        this.mPickerCallback = mPickerCallback;
        this.mPickerPromise = mPickerPromise;
        this.cameraOptions = cameraOptions;
        this.selectList = selectList;
        this.intent = intent;
    }

    @Override
    protected void doInBackgroundGuarded(Void... voids) {
        this.onGetResult();
    }

    private void onGetResult() {
        List<LocalMedia> tmpSelectList = PictureSelector.obtainMultipleResult(this.intent);
        if (cameraOptions != null) {
            boolean isRecordSelected = cameraOptions.getBoolean("isRecordSelected");
            if (!tmpSelectList.isEmpty() && isRecordSelected) {
                selectList = tmpSelectList;
            }

            WritableArray imageList = new WritableNativeArray();
            boolean enableBase64 = cameraOptions.getBoolean("enableBase64");

            for (LocalMedia media : tmpSelectList) {
                imageList.pushMap(getImageResult(media, enableBase64));
            }
            invokeSuccessWithResult(imageList);
        }
    }

    private WritableMap getImageResult(LocalMedia media, Boolean enableBase64) {
        WritableMap imageMap = new WritableNativeMap();
        String path = media.getPath();

        if (media.isCompressed() || media.isCut()) {
            path = media.getCompressPath();
        }

        if (media.isCut()) {
            path = media.getCutPath();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        imageMap.putDouble("width", options.outWidth);
        imageMap.putDouble("height", options.outHeight);
        imageMap.putString("type", "image");
        imageMap.putString("uri", "file://" + path);
        imageMap.putString("original_uri", "file://" + media.getPath());
        imageMap.putInt("size", (int) new File(path).length());

        if (enableBase64) {
            String encodeString = getBase64StringFromFile(path);
            imageMap.putString("base64", encodeString);
        }

        return imageMap;
    }

    /**
     * 获取图片base64编码字符串
     *
     * @param absoluteFilePath 文件路径
     * @return base64字符串
     */
    private String getBase64StringFromFile(String absoluteFilePath) {
        InputStream inputStream;
        try {
            inputStream = new FileInputStream(new File(absoluteFilePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        byte[] bytes;
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        bytes = output.toByteArray();
        if(absoluteFilePath.toLowerCase().endsWith("png")){
            return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        }
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private void invokeSuccessWithResult(WritableArray imageList) {
        if (this.mPickerCallback != null) {
            this.mPickerCallback.invoke(null, imageList);
            this.mPickerCallback = null;
        } else if (this.mPickerPromise != null) {
            this.mPickerPromise.resolve(imageList);
        }
    }
}
