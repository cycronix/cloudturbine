package cycronix.CT2DB;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;

public class DBauthenticate {
    // You don't need to change these, leave them alone.
    final static private String ACCOUNT_PREFS_NAME = "DBA_prefs";
    final static private String ACCESS_SECRET_NAME = "DBA_secret_name";
    private SharedPreferences prefs;
    
    DropboxAPI<AndroidAuthSession> mApi;

	// constructor
	public DBauthenticate(Context context, String APP_KEY, String APP_SECRET) {
        prefs = context.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);

    	// We create a new AuthSession so that we can use the Dropbox API.
        AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);
        AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
        loadAuth(session);
    	
    	mApi = new DropboxAPI<AndroidAuthSession>(session);
    	if(!session.authenticationSuccessful())
    		mApi.getSession().startOAuth2Authentication(context);
	}
	
    /**
     * Retrieve the access keys returned from Trusted Authenticator in a local store
     */
    private void loadAuth(AndroidAuthSession session) {
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if (secret == null || secret.length() == 0) return;
//        Log.i("CT2DB","loadAuth: "+secret);
        session.setOAuth2AccessToken(secret);
    }

    /**
     * Keep the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     */
    private void storeAuth(AndroidAuthSession session) {
        // Store the OAuth 2 access token, if there is one.
        String oauth2AccessToken = session.getOAuth2AccessToken();
        if (oauth2AccessToken != null) {
            Editor edit = prefs.edit();
            edit.putString(ACCESS_SECRET_NAME, oauth2AccessToken);
            edit.apply();
//            Log.i("CT2DB","storeAuth: "+oauth2AccessToken);
            return;
        }
    }

    public void clearKeys() {
        Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }
    
    // The next method must be called in the onResume() method of the
    // activity from which session.startAuthentication() was called, so
    // that Dropbox authentication completes properly.
    
    public DropboxAPI<AndroidAuthSession> resume() {
        AndroidAuthSession session = mApi.getSession();

        if (session.authenticationSuccessful()) {
            try {
                // Mandatory call to complete the auth
                session.finishAuthentication();
                // Store it locally in our app for later use
                storeAuth(session);
            } catch (IllegalStateException e) {
                mApi = null;
            }
        }
        return mApi;
    }
}
