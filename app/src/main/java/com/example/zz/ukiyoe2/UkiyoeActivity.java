package com.example.zz.ukiyoe2;
//相机和相册调用部分参考《Android 第一行代码》
// 图像预处理和后处理部分参考https://blog.csdn.net/qq_27634797/article/details/90513246

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class UkiyoeActivity extends AppCompatActivity {

    public static final int TAKE_PHOTO = 1;
    public static final int SELECT_PHOTO = 2;
    private ImageView photo;
    private Uri imageUri;
    private Interpreter tfLite;
    //显示在imageView中的图像大小
    public int view_w = 780;
    public int view_h = 780;
    //要输入tflite模型的图像大小
    public int tf_w = 512;
    public int tf_h = 512;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ukiyoe);

        photo = (ImageView) findViewById(R.id.photo);
        Button takePhoto = (Button) findViewById(R.id.take_photo);
        Button selectPhoto = (Button) findViewById(R.id.select_photo);

        takePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File outputImage = new File(getExternalCacheDir(), "output_image.jpg");
                try {
                    if (outputImage.exists()) {
                        outputImage.delete();
                    }
                    outputImage.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (Build.VERSION.SDK_INT >= 24) {
                    imageUri = FileProvider.getUriForFile(UkiyoeActivity.this,
                            "com.example.zz.ukiyoe2.fileprovider", outputImage);
                } else {
                    imageUri = Uri.fromFile(outputImage);
                }

                Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(intent, TAKE_PHOTO);
            }
        });

        selectPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission(UkiyoeActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(UkiyoeActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                } else {
                    openAlbum();
                }
            }
        });

        photo.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(UkiyoeActivity.this);
                builder.setItems(new String[]{getResources().getString(R.string.save_picture)}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        photo.setDrawingCacheEnabled(true);
                        Bitmap imageBitmap = photo.getDrawingCache();
                        if (imageBitmap != null) {
                            saveImageToGallery(UkiyoeActivity.this, imageBitmap);
                        }
                        photo.setDrawingCacheEnabled(false);
                        imageBitmap.recycle();
                        System.gc();
                    }
                });
                builder.show();
                return true;
            }
        });

        String MODEL_FILE = "photo2ukiyoe.tflite";
        try {
            tfLite = new Interpreter(loadModelFile(getAssets(), MODEL_FILE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveImageToGallery(Context context, Bitmap bmp) {
        // 创建文件夹
        File appDir = new File(Environment.getExternalStorageDirectory(), "ukiyoe");
        if (!appDir.exists()) {
            appDir.mkdir();
        }
        //图片文件名称
        String fileName = "ukiyoe_"+System.currentTimeMillis() + ".jpg";
        File file = new File(appDir, fileName);
        //Log.d("222", "fileName:" + fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            Log.e("111",e.getMessage());
            e.printStackTrace();
        }

        // 把文件插入到系统图库
        String path = file.getAbsolutePath();
        //Log.d("222", "path:" + path);
        try {
            MediaStore.Images.Media.insertImage(context.getContentResolver(), path, fileName, null);
            Toast.makeText(context,"successfully saved", Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            Log.e("333",e.getMessage());
            e.printStackTrace();
            Toast.makeText(context,"failed to save", Toast.LENGTH_SHORT).show();
        }
        // 最后通知图库更新
//        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//        Uri uri = Uri.fromFile(file);
//        intent.setData(uri);
//        context.sendBroadcast(intent);
    }

    MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename) throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private Bitmap resize_and_pad(Bitmap bitmap, int w, int h)
    {
        //resize 根据图像的长边缩放图像
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale;

        if (width >= height){
            scale = ((float) w) / width;
        }else {
            scale = ((float) h) / height;
        }

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        bitmap.recycle();

        //padding
        int size_w = resizedBitmap.getWidth();
        int size_h = resizedBitmap.getHeight();
        // background
        Bitmap newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(newBitmap);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);

        paint.setColor(Color.WHITE);
        canvas.drawBitmap(resizedBitmap, (w-size_w) / 2, (h-size_h) / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP));
        canvas.drawRect(0, 0, w, h, paint);

        resizedBitmap.recycle();
        return newBitmap;
    }

    private void openAlbum(){
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        startActivityForResult(intent, SELECT_PHOTO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        switch (requestCode){
            case 1:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    openAlbum();
                }else {
                    Toast.makeText(this, "You denied the permission", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        switch (requestCode){
            case TAKE_PHOTO:
                if(resultCode == RESULT_OK){
                    try{
                        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                        bitmap = translate(bitmap);
                        photo.setImageBitmap(bitmap);
                    }catch (FileNotFoundException e){
                        e.printStackTrace();
                    }
                }
                break;
            case SELECT_PHOTO:
                if(Build.VERSION.SDK_INT >= 19){
                    handleImageOnKitKat(data);
                }else {
                    handleImageBeforeKitKat(data);
                }
                break;
            default:
                break;
        }
    }

    @TargetApi(19)
    private void handleImageOnKitKat(Intent data){
        String imagePath = null;
        Uri uri = data.getData();
        if(DocumentsContract.isDocumentUri(this, uri)){
            String docId = DocumentsContract.getDocumentId(uri);
            if("com.android.providers.media.documents".equals(uri.getAuthority())){
                String id = docId.split(":")[1];
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imagePath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            }else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())){
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(docId));
                imagePath = getImagePath(contentUri, null);
            }
        }else if ("content".equalsIgnoreCase(uri.getScheme())){
            imagePath = getImagePath(uri, null);
        }else if ("file".equalsIgnoreCase(uri.getScheme())){
            imagePath = uri.getPath();
        }
        displayImage(imagePath);
    }

    private void handleImageBeforeKitKat(Intent data){
        Uri uri = data.getData();
        String imagePath = getImagePath(uri, null);
        displayImage(imagePath);
    }

    private String getImagePath(Uri uri, String selection){
        String path = null;
        Cursor cursor = getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null){
            if (cursor.moveToFirst()){
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }

    private void displayImage(String imagePath){
        if (imagePath != null){
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            bitmap = translate(bitmap);
            photo.setImageBitmap(bitmap);
        }else {
            Toast.makeText(this, "Failed to get the image", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap translate(Bitmap img){
        img = resize_and_pad(img, tf_w, tf_h);
        int im_w = img.getWidth();
        int im_h = img.getHeight();

        float[][][][] inFloat = new float[1][im_h][im_w][3];
        int[] pixels = new int[im_h * im_w];
        img.getPixels(pixels, 0, img.getWidth(), 0, 0, im_h, im_w);
        int pixel = 0;
        //在Bitmap.Config.ARGB_8888模式下，a/r/g/b各占一个字节，一个像素总共占四字节
        for (int i = 0; i < im_h; ++i) {
            for (int j = 0; j < im_w; ++j) {
                final int val = pixels[pixel++];
                float red = (float) (((val >> 16) & 0xFF) / 127.5 - 1);
                float green = (float) (((val >> 8) & 0xFF) / 127.5 - 1);
                float blue = (float) ((val & 0xFF) / 127.5 - 1);
                float[] arr = {red, green, blue};
                inFloat[0][i][j] = arr;
            }
        }
        img.recycle();

        float[][][][] output;
        output = new float[1][im_h][im_w][3];

        //输入和输出都是多维浮点数组
        tfLite.run(inFloat, output);
        Log.d("output","output:" + output.length);

        float[][][] temp = output[0];
        int n = 0;
        int[] colorArr = new int[im_h * im_w];
        for (int i = 0; i < im_h; i++) {
            for (int j = 0; j < im_w; j++) {
                float[] arr = temp[i][j];
                int alpha = 255;
                int red = (int) ((0.5 * arr[0] + 0.5) * 255);
                int green = (int) ((0.5 * arr[1] + 0.5) * 255);
                int blue = (int) ((0.5 * arr[2] + 0.5) * 255);
                int tempARGB = (alpha << 24) | (red << 16) | (green << 8) | blue;
                colorArr[n++] = tempARGB;
            }
        }
        Bitmap output_img = Bitmap.createBitmap(colorArr, im_h, im_w, Bitmap.Config.ARGB_8888);
        output_img = resize_and_pad(output_img, view_w, view_h);

        System.gc();
        return output_img;
    }
}
