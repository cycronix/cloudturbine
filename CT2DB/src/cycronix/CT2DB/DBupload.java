

package cycronix.CT2DB;

import java.io.File;
import java.io.FileInputStream;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.UploadRequest;

/**
 * Upload a file to Dropbox in a background thread 
 */
public class DBupload extends AsyncTask<Void, Long, Boolean> {

    private DropboxAPI<?> mApi;
    private String mPath;
    private File mFile;
    private UploadRequest mRequest;
    private Context mContext;
    private boolean mDelete=false;
    private String mErrorMsg="Status";

    public DBupload(Context context, DropboxAPI<?> api, String dropboxPath, File file, boolean deleteFlag) {
        // Set the context this way so we don't accidentally leak activities
        mContext = context.getApplicationContext();
        mApi = api;
        mPath = "/" + dropboxPath + "/";
        mFile = file;
        mDelete = deleteFlag;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
    	mErrorMsg = "Uploaded: "+mFile.getName();
        try {		// put the file to Dropbox
            FileInputStream fis = new FileInputStream(mFile);
            // Create request to get a handle to the putFile operation, so we can cancel it later
            mRequest = mApi.putFileOverwriteRequest(mPath + mFile.getName(), fis, mFile.length(), null);
            if (mRequest != null)  mRequest.upload();
        } catch (Exception e) {
        	Log.e("DBupload","Dropbox file upload error: "+e.getMessage());
        	mErrorMsg = e.getMessage();
        	return false;
        }
   
        if(mDelete) mFile.delete();
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
    	showToast(mErrorMsg);   
    }

    public void cancel() {
        mRequest.abort();
    }
    
    private void showToast(String msg) {
        Toast error = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
        error.show();
    }
}
