package com.miraicall.app;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;



/*
    Fragment used to crop profile photo

*/

public class Cropfragment extends MyFragment  implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, View.OnTouchListener {

    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "profilepic";
    //view containing the bitmap and the cropsquare, responsible for croping the bitmap
    private CropProfileView cpv;
    // seek bar used to resize the crop square
    private SeekBar sb;
    //path of the bitmap
    private String path;
    private int maxcount = 0;

    /*
    *Create a newInstance of CropFragment
    * argument
    * param1 : path of the bitmap file
     */
    public static Cropfragment newInstance(String param1) {
        Cropfragment fragment = new Cropfragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        fragment.setArguments(args);
        return fragment;
    }

    public Cropfragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parent = (MainActivity)this.getActivity();

        if (getArguments() != null) {
            path = getArguments().getString(ARG_PARAM1);

        }

    }

    /*
        Method used to get the ORIENTATION of the bitmap
     */
    private int getOrientation(Context context, Uri photoUri)
    {
        Cursor cursor = context.getContentResolver().query(photoUri,
                new String[]{MediaStore.Images.ImageColumns.ORIENTATION}, null, null, null);
        if(cursor != null){
            if (cursor.getCount() != 1) {
                cursor.close();
                return -1;
            }

            cursor.moveToFirst();
            int orientation = cursor.getInt(0);
            cursor.close();
            cursor = null;
            return orientation;
        }
        else{
            ExifInterface ei = null;
            try {
                ei = new ExifInterface(path);
                int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED);
                switch(orientation) {

                    case ExifInterface.ORIENTATION_ROTATE_90:
                        return 90;

                    case ExifInterface.ORIENTATION_ROTATE_180:
                        return 180;


                    case ExifInterface.ORIENTATION_ROTATE_270:
                        return 270;
                    default:
                        return 0;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return 0;

    }

    /*
        Function used to rotate the bitmap
     */
    public Bitmap rotateBitmap(Context context, Uri photoUri, Bitmap bitmap)
    {
        int orientation = getOrientation(context, photoUri);
        if (orientation <= 0) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(orientation);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
        return bitmap;
    }


    @Override
    public void onActivityCreated (Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);
        sb = parent.findViewById(R.id.seekBar);

        if(savedInstanceState != null) {
            //instantiat bitmap path with the value saved before going background
            path = savedInstanceState.getString("path");
        }
        if(path != null){

            cpv = parent.findViewById(R.id.cropview);
            //get bitmap from path
            if (Build.VERSION.SDK_INT >= 29) {
                File f = new File(path);
                MediaScannerConnection.scanFile(parent,new String[]{f.getAbsolutePath()},null,(path,uri)->{
                    if(uri == null){
                        uri = Uri.parse("file://"+this.path);
                    }

                    try (ParcelFileDescriptor pfd = parent.getContentResolver().openFileDescriptor(uri, "r")) {
                        if (pfd != null) {
                            Log.i("cropview","bitmap rotate = "+getOrientation(parent,uri));
                            Bitmap bm = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
                            bm = rotateBitmap(parent,uri,bm);
                            Log.i("cropview","bm height = "+bm.getHeight()+" bm width = "+bm.getWidth());
                            Bitmap finalBm = bm;
                            parent.runOnUiThread(()->{
                                cpv.setImageBitmap(finalBm);
                            });

                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }


                });

            }
            else{
                Bitmap bm =  BitmapFactory.decodeFile(path);
                Uri uri = Uri.parse("file://"+this.path);
                bm = rotateBitmap(parent,uri,bm);
                Log.i("cropview","bm height = "+bm.getHeight()+" bm width = "+bm.getWidth());

                cpv.setImageBitmap(bm);
            }


            cpv.setOnTouchListener(this);
            cpv.invalidate();
            Button b = parent.findViewById(R.id.button);
            b.setTag("Save");
            b.setOnClickListener(this);
            SeekBar sb = parent.findViewById(R.id.seekBar);
            sb.setProgress(100);
            sb.setOnSeekBarChangeListener(this);
        }

        parent.setFragment(this);

    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle saveState) {
        super.onSaveInstanceState(saveState);
        // save bitmap path because app went background
        saveState.putString("path", path);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_cropfragment, container, false);

    }

    @Override
    public void onPause (){

        super.onPause();

    }
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        //tell cropview to resize  the crop rectangle
        cpv.resizerec(progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {


    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }


    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_BUTTON_PRESS:
                // check click action
                v.performClick();
            case MotionEvent.ACTION_UP:
                //pression release
                maxcount = 0;

                break;
            default:
                //get number of pression point
                int count = event.getPointerCount();
                if(count == 1 && maxcount <2 ) {
                    //Only 1 finger : we set center of the cropping rect
                    cpv.setcenter((int) event.getX(), (int) event.getY());
                    maxcount = count;
                }else if(count > 1){
                    // 2 finger : get  pointer indice
                    int ind1 = event.findPointerIndex(event.getPointerId(0));
                    int ind2 = event.findPointerIndex(event.getPointerId(1));
                    //set center of the cropping rect
                    int cx = (int) ((event.getX(ind1)+event.getX(ind2))/2);
                    int cy = (int) ((event.getY(ind1)+event.getY(ind2))/2);

                    int height = cpv.getHeight();
                    int width = cpv.getWidth();

                    //get the x and y space between fingers
                    float dx = Math.abs(event.getX(ind1) - event.getX(ind2));
                    float dy = Math.abs(event.getY(ind1) - event.getY(ind2));
                    // f : the % used for the seeking bar update
                    float f;

                    if(width> height){
                        if(dx>dy){
                            f = dx/width;
                        }
                        else {
                            f = dy/width;
                        }
                    }else{
                        if(dx>dy){
                            f = dx/height;
                        }
                        else{
                            f = dy/height;
                        }
                    }
                    f = f*100;
                    // update the cropview
                    cpv.setcenter(cx, cy);
                    cpv.resizerec((int) f);
                    sb.setProgress((int)f);
                    maxcount = count;
                }
                break;
        }
        return true;
    }


    @Override
    public void onClick(View v) {
        if (v instanceof Button)
            if ( v.getTag().equals("Save")) {
                new save(this.cpv.getBitmap()).execute(this);
                v.setEnabled(false);

            }
    }

    @Override
    public void result(String msg) {

        DialogInterface.OnClickListener pbuttonaction = (dialog, id) -> {
            parent.onBackPressed();

        };
        parent.alert(null,parent.getResources().getString(R.string.ucomplete),"ok",pbuttonaction,null,null,null,null,null,true);
    }


    /*
        save:
        class used to save the bitmap on temporary file in cache dir
        begore sending to the uploadjob service
     */
    static class save extends AsyncTask<Cropfragment,Void , Integer> {

        Bitmap bitmap;

        save(Bitmap bm){
            bitmap = bm;
        }

        @Override
        protected Integer doInBackground(Cropfragment... d) {
            //get the bitmap from the crop view
            byte[] ba = d[0].cpv.save(bitmap);
            try {
                //get cache directory and create file
                File outputDir = d[0].parent.getCacheDir();
                File outputFile = File.createTempFile("image", "jpg", outputDir);
                FileOutputStream fos = new FileOutputStream(outputFile,
                        false);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                //encode bitmap as base64 and write into the file
                bos.write(Base64.encode(ba,Base64.DEFAULT));

                bos.close();


                Intent in = new Intent();
                //url to upload to
                in.putExtra("url", d[0].parent.getResources().getString(R.string.uploadpicurl,BuildConfig.BASE_URL));
                //the file to upload
                in.putExtra("pic", outputFile.getPath());
                //account used to upload the picture ( in case multiple account exist)
                in.putExtra("acc", MyApp.getSelectedaccount());
                MyUploadService.enqueueWork(d[0].parent, in);
                d[0].parent.runOnUiThread(()-> d[0].parent.onBackPressed());



            }catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

    }
}
