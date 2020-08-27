package net.jp.garlands.simpleAndroidMedia;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.storage.OnObbStateChangeListener;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.ImageView;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.Profile;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareDialog;
import com.nifcloud.mbaas.core.LoginCallback;
import com.nifcloud.mbaas.core.NCMB;
import com.nifcloud.mbaas.core.NCMBException;
import com.nifcloud.mbaas.core.NCMBFacebookParameters;
import com.nifcloud.mbaas.core.NCMBUser;

import org.cocos2dx.lib.Cocos2dxActivity;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AppActivity extends Cocos2dxActivity {
    private static final String TAG = AppActivity.class.getSimpleName();
    private static final boolean D = true;

    private static final int REQUEST_GET_RECOGNIZER = 12345;
    private static Activity me = null;
    private LocationManager _lcmLocationManager;
    private String _strBestProvider = "";
    private double _dblCurrentLatitude = 0;
    private double _dblCurrentLongitude = 0;

    public static native void onNativeRetStartFacebookLogIn(int status);
    public static native void onSaveImageToCameraroll(boolean state);
    public static native void onReceiveSaveFaceImage();
    public static native void onNativeRetFacebookShare(boolean state);
    public static native void onNativeRetSppechError(String message);
    public static native void onNativeRetServiceResult(String message);

    //obb
    private static String mObbFilePath = null;
    private StorageManager mSM = null;
    private static boolean obb_state = false;

    private boolean logined = false;

    private static File cameraFile;
    private static Uri cameraUri;
    private static String filePath;

    final Handler handler = new Handler();
    private static int retry_count = 0;

//    private static Uri m_uri;

    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int REQUEST_EXTERNAL_STORAGE = 1;


    private static final int REQUEST_CAPTURE_IMAGE = 100;
    private static final int REQUEST_GET_IMAGE = 1;
    ImageView imageView1;

    private static String mCurrentPhotoPath;
    private static String mImageFileName;


    CallbackManager callbackManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("INFO", "AppActivity onCreate ");
        me = this;

        Context context = getContext();

        Log.i("VOICE", " pre InitializeRecording ");
//deprecated        mObbFilePath = new File(Environment.getExternalStorageDirectory() + "/Android/obb/" + context.getPackageName(), "main." + getVersionCode() + "." + context.getPackageName() + ".obb").getPath();
        mObbFilePath = new File(Environment.getExternalStorageDirectory() + "/Android/obb/" + context.getPackageName(), "main.1" + "." + context.getPackageName() + ".obb").getPath();
        Log.i("OBB", " obb path " + mObbFilePath);

        File obbfile = new File(mObbFilePath);
        if( obbfile.exists() ){
            Log.i("OBB", " obb file exist ");
            StorageManager storageMgr = (StorageManager)getSystemService(STORAGE_SERVICE);
            storageMgr.mountObb(mObbFilePath, null, new OnObbStateChangeListener(){
                public void onObbStateChange(String path, int state) {
                    Log.i("OBB", "enter onObbStateChange");
                    Log.i("OBB", " path=" + path + "; state=" + state);


                    if (state == OnObbStateChangeListener.ERROR_ALREADY_MOUNTED) {
                        Log.i("OBB", " Already Mounted Error");
                        StorageManager stmgr = (StorageManager)getSystemService(STORAGE_SERVICE);
                        mObbFilePath = stmgr.getMountedObbPath(path);
                        Log.i("OBB", " mount path " + mObbFilePath);
                        obb_state = true;
//                        InitializeRecording();
                    }
                    if (state == OnObbStateChangeListener.MOUNTED) {
                        Log.i("OBB", " MOUNTED");

                        StorageManager stmgr = (StorageManager)getSystemService(STORAGE_SERVICE);
                        mObbFilePath = stmgr.getMountedObbPath(path);
                        Log.i("OBB", " mount path " + mObbFilePath);
                        obb_state = true;
//                        InitializeRecording();
                    } else {
                        Log.i("OBB", " Other");
                    }
                }
            });
        }else{
            Log.i("OBB", " obb file not found ");
        }


    public static boolean checkReachability() {
        Log.i ("INFO","checkReachability");
        ConnectivityManager connMgr = (ConnectivityManager)
                me.getSystemService(me.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if( networkInfo != null )
        {
            if( networkInfo.isConnected() ) {
                Log.i("INFO", "networkInfo is Connected");
                return true;
            }
            else
                Log.i ("INFO","networkInfo is no connect");
        }
        else
            Log.i ("INFO","networkInfo is null");

        return false;
    }

    public static void StartFacebookLogIn()
    {
        Log.i("INFO","StartFacebookLogIn");
        LoginManager.getInstance().logInWithReadPermissions(me, Arrays.asList("public_profile"));
    }

    public static boolean CheckFacebookLogIn(){
        Log.i("INFO","CheckFacebookLogIn");
        boolean ret = true;
        if( AccessToken.getCurrentAccessToken() == null ){
            Log.i("INFO","  AccessToken is null");
            ret = false;
        }else{
            if( Profile.getCurrentProfile() == null ){
                Log.i("INFO","  Profile is null");
                ret = false;
            }
        }
        return ret;
    }
    //cocos2d-xから呼ぶメソッドの追加
    public static boolean StartCamera(){
        Log.i("CAMERA", "Enter StartCamera()");
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            int permission = ContextCompat.checkSelfPermission(me, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission so prompt the user
                ActivityCompat.requestPermissions(
                        me,
                        PERMISSIONS_STORAGE,
                        REQUEST_EXTERNAL_STORAGE
                );
                return false;
            }
//        }
        String status = Environment.getExternalStorageState();
        if ( !status.equals(Environment.MEDIA_MOUNTED)) {
            Log.i("ALBUM", " getExternalStorageState " + status);
            return false;
        }
//        } else {
//            File file = Environment.getExternalStorageDirectory();
//            if (file.canWrite()){
//                Log.i("ALBUM", " file canWrite ");
//            }else {
//                Log.i("ALBUM", " faile cannt Write ");
//                return;
//            }
//        }

        Context context = getContext();

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if( takePictureIntent == null ){
            Log.i("CAMERA", "null takePictureIntent()");
        }
        // Ensure that there's a camera activity to handle the intent

        if (takePictureIntent.resolveActivity(context.getPackageManager()) != null) {
            // Create the File where the photo should go
            Log.i("CAMERA", "enter if takePictureIntent()");
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.i("PHOTO"," photoFile error ");
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Log.i("CAMERA", " enter photoFile");

                Uri photoURI = FileProvider.getUriForFile(me,
                        "net.jp.garlands.simpleAndroidMedia.fileprovider",
                        photoFile);
//                Uri photoURI = FileProvider.getUriForFile(me,
//                        "fileprovider",
//                        photoFile);
                if( photoURI != null ) {
                    Log.i("CAMERA", " photoURI" + photoURI);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    Log.i("CAMERA", " call startActivityForResult");
                    me.startActivityForResult(takePictureIntent, 1);
                    Log.i("CAMERA", " out startActivityForResult");
                    return true;
                }else {
                    Log.i("CAMERA", " photoURI is null");
                }
            }
        }
        return false;
    }


    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    StartCamera();
                }
                return;
            }
        }
    }

    private static File createImageFile() throws IOException {
        // Create an image file name
        Log.i("CAMERA", "enter createImageFile");
        Context context = getContext();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "image_" + timeStamp + "_";
        mImageFileName = imageFileName + ".png";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".png",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        Log.i("CAMERA", " mCurrentPhotoPath : " + mCurrentPhotoPath);

        return image;
    }

    public static void facebookShare(String filepath)
    {
        Log.i("ALBUM", "Enter facebookShare() " + filepath);

        try {
            FileInputStream inputStream = new FileInputStream(filepath);

            Bitmap image = BitmapFactory.decodeStream(inputStream);
            SharePhoto photo =new SharePhoto.Builder().setBitmap(image).build();
            SharePhotoContent content = new SharePhotoContent.Builder()
                    .addPhoto(photo)
                    .build();
            ShareDialog share_dialog = new ShareDialog(me);
            share_dialog.show(content);
            net.jp.garlands.simpleAndroidMedia.AppActivity.onNativeRetFacebookShare(true);
        } catch (IOException e) {
            Log.d("ALBUM", " error: FileOutputStream");
            net.jp.garlands.simpleAndroidMedia.AppActivity.onNativeRetFacebookShare(true);
        }
    }

    public static void StartImageFolder(){
        Log.i("INFO", "Enter StartImageFolder()");

        // capture画像のファイルパス
        cameraFile = new File(filePath);
        cameraUri = Uri.fromFile(cameraFile);

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/png");
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
        me.startActivityForResult(intent, REQUEST_GET_IMAGE);
    }

    public static void PlayMovie(){

    }

    /*
    public static int getVersionCode(){
        Context context = getContext();
        PackageManager pm = context.getPackageManager();
        int versionCode = 1;

        try{
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            versionCode = packageInfo.versionCode;
        }catch(NameNotFoundException e){
            e.printStackTrace();
        }
        return versionCode;
    }*/

    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data) {
        Log.i("INFO", "Enter onActivityResult()");

        if( REQUEST_CAPTURE_IMAGE == requestCode && resultCode == Activity.RESULT_OK ){
            Log.i("INFO", " REQUEST_CAPTURE_IMAGE ");

            Bitmap capturedImage = (Bitmap) data.getExtras().get("data");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            capturedImage.compress(Bitmap.CompressFormat.PNG, 100, bos);
            byte[] src = bos.toByteArray();
            Log.i("INFO", " image data " + src.length );


            Context context = getContext();
            File dir = context.getFilesDir();
            filePath = dir + "/face.png";
            Log.i("INFO", "write " + filePath);

            try {
                DataOutputStream dataOutStream = new DataOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(filePath)));
                dataOutStream.write(src, 0, src.length);
                dataOutStream.close();
                Log.i("INFO", "success write" + filePath);

                net.jp.garlands.simpleAndroidMedia.AppActivity.onReceiveSaveFaceImage();
            } catch (IOException fe) {
                Log.i("INFO", "error write");
                fe.printStackTrace();
                net.jp.garlands.simpleAndroidMedia.AppActivity.onReceiveSaveFaceImage();
            }

            net.jp.garlands.simpleAndroidMedia.AppActivity.onReceiveSaveFaceImage();
        }else if( REQUEST_GET_IMAGE == requestCode && resultCode == Activity.RESULT_OK) {
            Log.i("CAMERA", " REQUEST_GET_IMAGE ");
            int targetW = 640;
            int targetH = 640;

            // Get the dimensions of the bitmap
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
            int photoW = bmOptions.outWidth;
            int photoH = bmOptions.outHeight;

            // Determine how much to scale down the image
            int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

            // Decode the image file into a Bitmap sized to fill the View
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inSampleSize = scaleFactor;
//            bmOptions.inPurgeable = true;

            Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
            byte[] src = bos.toByteArray();
            Log.i("INFO", " image data " + src.length );


            Context context = getContext();
            File dir = context.getFilesDir();
            filePath = dir + "/face.png";
            Log.i("INFO", "write " + filePath);

            try {
                DataOutputStream dataOutStream = new DataOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(filePath)));
                dataOutStream.write(src, 0, src.length);
                dataOutStream.close();
                Log.i("INFO", "success write" + filePath);

                net.jp.garlands.simpleAndroidMedia.AppActivity.onReceiveSaveFaceImage();

                File deletfile = new File(mCurrentPhotoPath);
                deletfile.delete();

            } catch (IOException fe) {
                Log.i("INFO", "error write");
                fe.printStackTrace();
                net.jp.garlands.simpleAndroidMedia.AppActivity.onReceiveSaveFaceImage();
            }

            net.jp.garlands.simpleAndroidMedia.AppActivity.onReceiveSaveFaceImage();
        }else if( REQUEST_GET_RECOGNIZER == requestCode && resultCode == Activity.RESULT_OK) {
            Log.i("CAMERA", " REQUEST_GET_RECOGNIZER ");
            ArrayList<String> kekka = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            if (kekka.size() > 0) {
                //一番最初にある認識結果を表示する
                final String message = kekka.get(0);
                net.jp.garlands.simpleAndroidMedia.AppActivity.onNativeRetServiceResult(message);
            } else {
                final String message = "error speech recognizer";
                net.jp.garlands.simpleAndroidMedia.AppActivity.onNativeRetSppechError(message);

            }
        }else{
            Log.i("INFO", " else ");
            if( logined == false )
                callbackManager.onActivityResult(requestCode, resultCode, data); //facebook
        }
    }

    public static void saveImageToCameraroll(String filname)
    {
        Log.i("ALBUM", "Enter saveImageToCameraroll " + filname);
        Context context = getContext();

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "photo_" + timeStamp + "_" + ".png";;
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        filePath = storageDir + imageFileName;

        Log.d("ALBUM", " imageFileName : " +  imageFileName);
        Log.d("ALBUM", " filePath : " +  filePath);


        try {
            // Read store image
            InputStream inputStream = new FileInputStream(filname);
            if( inputStream == null ) {
                Log.d("ALBUM", " inputStream == null");
                return;
            }

            // 外部ストレージのパスに画像を保存
            FileOutputStream output = new FileOutputStream(filePath);
            if( output == null ) {
                Log.d("ALBUM", " output == null");
                return;
            }

            // バッファーを使って画像を書き出す
            int DEFAULT_BUFFER_SIZE = 10240 * 4;
            byte buf[]=new byte[DEFAULT_BUFFER_SIZE];
            int len;
            while((len=inputStream.read(buf))!=-1){
                output.write(buf,0,len);
            }
            output.flush();
            output.close();
            inputStream.close();
            Log.d("ALBUM", " image saved");

            // 保存した画像をアンドロイドのデータベースへ登録
            registerDatabase(filePath);

        } catch (IOException e) {
            Log.d("ALBUM", " error: FileOutputStream");
        }
        net.jp.garlands.simpleAndroidMedia.AppActivity.onSaveImageToCameraroll(true);
    }

    // アンドロイドのデータベースへ登録する
    private static void registerDatabase(String file) {
        Log.i("ALBUM", "enter registerDatabase ");
        ContentValues contentValues = new ContentValues();
        ContentResolver contentResolver = AppActivity.me.getContentResolver();
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        contentValues.put("_data", file);
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues);
        Log.i("ALBUM", "leave registerDatabase ");
    }

    public static String nowDateTime(boolean datetype)
    {
        if( datetype == true ) {
            final DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            final Date date = new Date(System.currentTimeMillis());
            return df.format(date);
        }else {
            final DateFormat df = new SimpleDateFormat("yyyy/MM/dd");
            final Date date = new Date(System.currentTimeMillis());
            return df.format(date);
        }
    }

    public static int nowWeek()
    {
        Calendar cal = Calendar.getInstance();
	    int week = cal.get(Calendar.DAY_OF_WEEK);
	    Log.i("INFO", "nowWeek " + week);
	    return week;
    }

    public static String getObbPath()
    {
        Log.i("OBB", "enter getObbPath ");
        if( obb_state == true){
            Log.i("OBB", " return " + mObbFilePath);
            return mObbFilePath;
        }else{
            Log.i("OBB", " reutrn empty");
            String dummy = "";
            return dummy;
        }
    }

    @Override
    public void onDestroy() {
            Log.i("INFO", "AppActivity onDestroy()");
            super.onDestroy();
    }
    
    @Override
    public void onPause() {
        Log.i("INFO", "AppActivity onPause()");
//	    voiceRecorder.stopRecording();
        super.onPause();
    }
    
    @Override
    public void onResume() {
        Log.i("INFO", "AppActivity onResume()");
        super.onResume();
    }


    public static boolean StartRecording(){
    	// Start Japanese recognition.
        Log.i("VOICE","enter StartRecording");
        retry_count = 0;
        /*
        if (enableJa) {
		    NICTASR n = nictasrJa;
			if (n != null) {
				dataLength = 0; // for RTF
                Log.i("VOICE"," n.start()");
				n.start();
			}
		}

		// Start recording.
        Log.i("VOICE"," startRecording");
		started = true;
        voiceRecorder.startRecording();
        */
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,"net.jp.garlands.speechrecognizer");
        //認識する言語を指定（この場合は日本語）
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPANESE.toString());
        //認識する候補数の指定
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10);
        //音声認識時に表示する案内を設定
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "話してください");
        //音声認識を開始
        me.startActivityForResult(intent, REQUEST_GET_RECOGNIZER);
        return true;
    }

    
    public static boolean StopRecording(){
        Log.i("VOICE","enter StopRecording");
        /*
        if (started == false) {
            Log.i("VOICE"," started == false");
            return false;
	    }
        voiceRecorder.stopRecording();
	    started = false;
	
	    if (enableJa) {
    		NICTASR n = nictasrJa;
    		if (n != null) {
    			n.end();
                Log.i("VOICE"," n.end();");
            }
    	}
    	*/
        return true;
    }
}


