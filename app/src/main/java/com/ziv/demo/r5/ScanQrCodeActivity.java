package com.ziv.demo.r5;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.kathline.barcode.BitmapUtils;
import com.kathline.barcode.CameraImageGraphic;
import com.kathline.barcode.CameraSourcePreview;
import com.kathline.barcode.FrameMetadata;
import com.kathline.barcode.GraphicOverlay;
import com.kathline.barcode.MLKit;
import com.kathline.barcode.PermissionUtil;
import com.kathline.barcode.ViewfinderView;
import com.kathline.barcode.barcodescanner.WxGraphic;
import com.ziv.demo.r5.utils.GlobalLiveData;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

public class ScanQrCodeActivity extends AppCompatActivity implements View.OnClickListener {
    public static final int PHOTO_REQUEST_CODE = 1111;

    private ImageView imgBack;
    private ImageView imgSwitchCamera;
    private TextView imgGallery;
    private TextView imgExit;
    private TextView imgLight;
    private RelativeLayout bottomMask;

    private CameraSourcePreview preview;
    private ViewfinderView viewfinderView;
    private GraphicOverlay graphicOverlay;
    private MLKit mlKit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_code);

        initViews();

        initQrCodeScanner();
    }

    private void initQrCodeScanner() {
        //构造出扫描管理器
        mlKit = new MLKit(this, preview, graphicOverlay);
        //是否扫描成功后播放提示音和震动
        mlKit.setPlayBeepAndVibrate(true, false);
        //仅识别二维码
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC)
                .build();
        mlKit.setBarcodeFormats(null);
        mlKit.setOnScanListener(new MLKit.OnScanListener() {
            @Override
            public void onSuccess(List<Barcode> barcodes, @NonNull GraphicOverlay graphicOverlay, InputImage image) {
                showScanResult(barcodes, graphicOverlay, image);
            }

            @Override
            public void onFail(int code, Exception e) {

            }
        });
    }

    private void showScanResult(List<Barcode> barcodes, GraphicOverlay graphicOverlay, InputImage image) {
        if (barcodes.isEmpty()) {
            return;
        }
        Bitmap bitmap = null;
        ByteBuffer byteBuffer = image.getByteBuffer();
        if (byteBuffer != null) {
            FrameMetadata.Builder builder = new FrameMetadata.Builder();
            builder.setWidth(image.getWidth())
                    .setHeight(image.getHeight())
                    .setRotation(image.getRotationDegrees());
            bitmap = BitmapUtils.getBitmap(byteBuffer, builder.build());
        } else {
            bitmap = image.getBitmapInternal();
        }
        if (bitmap != null) {
            graphicOverlay.add(new CameraImageGraphic(graphicOverlay, bitmap));
        }
        for (int i = 0; i < barcodes.size(); ++i) {
            Barcode barcode = barcodes.get(i);
            WxGraphic graphic = new WxGraphic(graphicOverlay, barcode);
            graphic.setColor(getResources().getColor(R.color.colorAccent));
            graphic.setOnClickListener(new WxGraphic.OnClickListener() {
                @Override
                public void onClick(Barcode barcode) {
                    String rawValue = barcode.getRawValue();
                    clickBarcode(rawValue);
                }
            });
            graphicOverlay.add(graphic);
        }
        if (barcodes.size() > 0) {
            imgBack.setVisibility(View.VISIBLE);
            imgSwitchCamera.setVisibility(View.INVISIBLE);
            bottomMask.setVisibility(View.GONE);
            mlKit.stopProcessor();
            if (barcodes.size() == 1) {
                clickBarcode(barcodes.get(0).getRawValue());
            }
        }
    }

    private void clickBarcode(String rawValue) {
        String trim = rawValue.trim();
        Toast.makeText(getApplicationContext(), "选择:" + trim, Toast.LENGTH_SHORT).show();
        int length = trim.length();
        Log.d("QrCode", trim + " -> " + length);
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char c = trim.charAt(i);
            Log.d("QrCode", "char -> " + c);
            stringBuilder.append(c);
            if (i < length - 1 && i % 2 != 0) {
                stringBuilder.append(":");
            }
        }
        String value = stringBuilder.toString().toUpperCase(Locale.ROOT);
        Log.d("QrCode", "value -> " + value);
        GlobalLiveData.getInstance().mQrCodeStr.postValue(value);
        finish();
    }

    private void initViews() {
        preview = findViewById(R.id.preview_view);
        viewfinderView = findViewById(R.id.viewfinderView);
        graphicOverlay = findViewById(R.id.graphic_overlay);

        imgBack = findViewById(R.id.img_back);
        imgSwitchCamera = findViewById(R.id.img_switch_camera);
        imgLight = findViewById(R.id.img_light);
        imgExit = findViewById(R.id.img_exit);
        imgGallery = findViewById(R.id.img_gallery);
        bottomMask = findViewById(R.id.bottom_mask);

        imgBack.setOnClickListener(this);
        imgSwitchCamera.setOnClickListener(this);
        imgGallery.setOnClickListener(this);
        imgExit.setOnClickListener(this);
        imgLight.setOnClickListener(this);
    }

    private void requirePermission() {
        PermissionUtil.getInstance().with(this).requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, new PermissionUtil.PermissionListener() {
            @Override
            public void onGranted() {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, PHOTO_REQUEST_CODE);
            }

            @Override
            public void onDenied(List<String> deniedPermission) {
                PermissionUtil.getInstance().showDialogTips(getBaseContext(), deniedPermission, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
//                        finish();
                    }
                });
            }

            @Override
            public void onShouldShowRationale(List<String> deniedPermission) {
                requirePermission();
            }
        });
    }

    public void showPictures() {
        requirePermission();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.img_gallery:
                showPictures();
                break;
            case R.id.img_light:
                mlKit.switchLight();
                break;
            case R.id.img_exit:
                finish();
                break;
            case R.id.img_switch_camera:
                mlKit.switchCamera();
                break;
            case R.id.img_back:
                mlKit.startProcessor();
                imgBack.setVisibility(View.GONE);
                imgSwitchCamera.setVisibility(View.VISIBLE);
                bottomMask.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }
}