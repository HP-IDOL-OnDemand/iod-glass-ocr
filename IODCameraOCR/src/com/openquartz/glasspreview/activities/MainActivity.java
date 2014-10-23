package com.openquartz.glasspreview.activities;

import java.io.File;
import java.io.FileOutputStream;

import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import com.google.android.glass.content.Intents;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.openquartz.glasspreview.CameraSurfaceView;

public class MainActivity extends Activity {
    private CameraSurfaceView cameraView;
	private static final int TAKE_PICTURE_REQUEST = 1;
	private GestureDetector mGestureDetector = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initiate CameraView
        cameraView = new CameraSurfaceView(this);

		// Turn on Gestures
		mGestureDetector = createGestureDetector(this);

        // Set the view
        this.setContentView(cameraView);

    }
    
    
	/*
	 * Send generic motion events to the gesture detector
	 */
	@Override
	public boolean onGenericMotionEvent(MotionEvent event) 
	{
		if (mGestureDetector != null) 
		{
			return mGestureDetector.onMotionEvent(event);
		}

		return false;
	}
    
    
    /**
	 * Gesture detection for fingers on the Glass
	 * @param context
	 * @return
	 */
	private GestureDetector createGestureDetector(Context context) 
	{	
		GestureDetector gestureDetector = new GestureDetector(context);

		//Create a base listener for generic gestures
		gestureDetector.setBaseListener( new GestureDetector.BaseListener() 
		{
			@Override
			public boolean onGesture(Gesture gesture) 
			{
				// Make sure view is initiated
				if (cameraView != null)
				{
					// Tap with a single finger for photo
					if (gesture == Gesture.TAP) 
					{
						Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
						if (intent != null)
						{
							
							Toast.makeText(getApplicationContext(), "Taking Picture",
									Toast.LENGTH_SHORT).show();
							startActivityForResult(intent, TAKE_PICTURE_REQUEST);
						}

						return true;
					}

				}

				return false;
			}
		});

		return gestureDetector;
	}
    
	

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
		// Handle photos
		if (requestCode == TAKE_PICTURE_REQUEST && resultCode == RESULT_OK) 
		{
			
			String picturePath = data.getStringExtra(Intents.EXTRA_PICTURE_FILE_PATH);
						processPictureWhenReady(picturePath);
		}



		super.onActivityResult(requestCode, resultCode, data);
	
	}
	
	
	
	public void toastResult(String result){
		Toast.makeText(getApplicationContext(), result,
				Toast.LENGTH_LONG).show();
	}
	
	private static class IODOCRTask extends AsyncTask<File,Void,String> {
		private MainActivity activity;

		protected IODOCRTask(MainActivity activity) {
			this.activity = activity;
		}
		
		@Override
		protected String doInBackground(File... params) {
			File file = params[0];
			String result="";
			
		    try {
		    		
		    	HttpResponse<JsonNode> response = Unirest
						.post("http://api.idolondemand.com/1/api/sync/ocrdocument/v1")
						.field("file",file)
						.field("mode", "scene_photo")
						.field("apikey","<yourapikey>")
						.asJson();
		    	
	    		JSONObject textblock =(JSONObject) response.getBody().getObject().getJSONArray("text_block").get(0);
	    		result=textblock.getString("text");		    	
			} catch (Exception e) {
				// keeping error handling simple
				e.printStackTrace();
			} 
		    
			Log.i("MARTIN"+file.getName(),result);

			return result;
		   
		}
		@Override
		protected void onPostExecute(String result) {
			// TODO Auto-generated method stub
			activity.toastResult(result);
		}
	}
	
    private void processPictureWhenReady(final String picturePath) {
    	final File pictureFile = new File(picturePath);
    	


		if (pictureFile.exists()) 
		{
			
			
			            
			            
			Log.i("MARTIN", "FILE EXISTS");
			Toast.makeText(getApplicationContext(), "FILE IS WRITTEN",
					Toast.LENGTH_SHORT).show();

			File dir = Environment
					.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
			Bitmap b = BitmapFactory.decodeFile(picturePath);
			Bitmap out = Bitmap.createScaledBitmap(b, 640, 960, false);

			File file = new File(dir, "resize.png");
			FileOutputStream fOut;
			try {
				fOut = new FileOutputStream(file);
				out.compress(Bitmap.CompressFormat.PNG, 100, fOut);
				fOut.flush();
				fOut.close();
				b.recycle();
				out.recycle();

				new IODOCRTask(this).execute(pictureFile);
				new IODOCRTask(this).execute(file);

			} catch (Exception e) { // TODO
				e.printStackTrace();
			}

			            
			// The picture is ready; process it.
		} 
		else 
		{
			// The file does not exist yet. Before starting the file observer, you
			// can update your UI to let the user know that the application is
			// waiting for the picture (for example, by displaying the thumbnail
			// image and a progress indicator).


			

			final File parentDirectory = pictureFile.getParentFile();
		//	Toast.makeText(getApplicationContext(), parentDirectory.getPath(),
			//		Toast.LENGTH_SHORT).show();
			
			FileObserver observer = new FileObserver(parentDirectory.getPath(),
					FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
				private boolean isFileWritten;

				@Override
				public void onEvent(int event, String path) {
					Log.i("MARTINEVENT", event+","+path);

					if (!isFileWritten) {
						Log.i("MARTINEVENT", isFileWritten+",");

						File affectedFile = new File(parentDirectory, path);
						isFileWritten = affectedFile.equals(pictureFile);

						if (isFileWritten) {
							stopWatching();

							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									processPictureWhenReady(picturePath);
								}
							});
						}
					}
				}
			};
			observer.startWatching();
		}		
		
	}


	@Override
    protected void onResume() {
        super.onResume();

        // Do not hold the camera during onResume
      /*
        if (cameraView != null) {
            cameraView.releaseCamera();
        }
        */
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Do not hold the camera during onPause
        if (cameraView != null) {
            cameraView.releaseCamera();
        }
    }
}
