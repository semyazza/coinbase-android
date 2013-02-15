package com.siriusapplications.coinbase.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Log;

import com.siriusapplications.coinbase.Constants;
import com.siriusapplications.coinbase.R;

public class LoginManager {

  private static LoginManager INSTANCE = null;

  public static LoginManager getInstance() {

    if(INSTANCE == null) {
      INSTANCE = new LoginManager();
    }

    return INSTANCE;
  }

  protected static final String CLIENT_ID = "34183b03a3e1f0b74ee6aa8a6150e90125de2d6c1ee4ff7880c2b7e6e98b11f5";
  protected static final String CLIENT_SECRET = "2c481f46f9dc046b4b9a67e630041b9906c023d139fbc77a47053328b9d3122d";

  private LoginManager() {

  }

  public boolean isSignedIn(Context context) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

    return prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1) > -1;
  }
  
  public String[] getAccounts(Context context) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int numAccounts = prefs.getInt(Constants.KEY_MAX_ACCOUNT, -1) + 1;
    
    String[] accounts = new String[numAccounts];
    for(int i = 0; i < numAccounts; i++) {
      
      accounts[i] = prefs.getString(String.format(Constants.KEY_ACCOUNT_NAME, i), null);
    }
    
    return accounts;
  }

  public String getAccessToken(Context context) {

    if(!isSignedIn(context)) {
      return null;
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

    return prefs.getString(String.format(Constants.KEY_ACCOUNT_ACCESS_TOKEN, activeAccount), null);
  }

  public void refreshAccessToken(Context context) {

    Log.i("Coinbase", "Refreshing access token...");

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String refreshToken = prefs.getString(String.format(Constants.KEY_ACCOUNT_REFRESH_TOKEN, activeAccount), null);

    List<BasicNameValuePair> parametersBody = new ArrayList<BasicNameValuePair>();
    parametersBody.add(new BasicNameValuePair("grant_type", "refresh_token"));
    parametersBody.add(new BasicNameValuePair("refresh_token", refreshToken));

    String[] newTokens;
    
    try {
      newTokens = doTokenRequest(context, parametersBody);
    } catch(Exception e) {

      e.printStackTrace();
      Log.e("Coinbase", "Could not fetch new access token!");
      return;
    }

    if(newTokens == null) {

      // Authentication error.
      Log.e("Coinbase", "Authentication error when fetching new access token.");
      return;
    }

    Editor e = prefs.edit();

    e.putString(String.format(Constants.KEY_ACCOUNT_ACCESS_TOKEN, activeAccount), newTokens[0]);
    e.putString(String.format(Constants.KEY_ACCOUNT_REFRESH_TOKEN, activeAccount), newTokens[1]);

    e.commit();
  }

  public String addAccount(Context context, String username, String password) {

    List<BasicNameValuePair> parametersBody = new ArrayList<BasicNameValuePair>();
    parametersBody.add(new BasicNameValuePair("grant_type", "password"));
    parametersBody.add(new BasicNameValuePair("username", username));
    parametersBody.add(new BasicNameValuePair("password", password));

    try {
      String[] tokens = doTokenRequest(context, parametersBody);

      if(tokens == null) {
        return context.getString(R.string.login_error_auth);
      }

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      Editor e = prefs.edit();

      int accountId = prefs.getInt(Constants.KEY_MAX_ACCOUNT, -1) + 1;
      e.putInt(Constants.KEY_MAX_ACCOUNT, accountId);
      e.putInt(Constants.KEY_ACTIVE_ACCOUNT, accountId);

      e.putString(String.format(Constants.KEY_ACCOUNT_ACCESS_TOKEN, accountId), tokens[0]);
      e.putString(String.format(Constants.KEY_ACCOUNT_REFRESH_TOKEN, accountId), tokens[1]);
      e.putString(String.format(Constants.KEY_ACCOUNT_NAME, accountId), username);

      e.commit();

      return null;

    } catch (IOException e) {

      e.printStackTrace();
      return context.getString(R.string.login_error_io);
    } catch (ParseException e) {

      e.printStackTrace();
      return context.getString(R.string.login_error_io);
    } catch (JSONException e) {

      e.printStackTrace();
      return context.getString(R.string.login_error_io);
    }
  }

  private String[] doTokenRequest(Context context, Collection<BasicNameValuePair> params) throws IOException, JSONException {

    DefaultHttpClient client = new DefaultHttpClient();

    String baseUrl = "https://coinbase.com/oauth/token";

    HttpPost oauthPost = new HttpPost(baseUrl);
    List<BasicNameValuePair> parametersBody = new ArrayList<BasicNameValuePair>();
    parametersBody.add(new BasicNameValuePair("client_id", CLIENT_ID));
    parametersBody.add(new BasicNameValuePair("client_secret", CLIENT_SECRET));
    parametersBody.addAll(params);
    oauthPost.setEntity(new UrlEncodedFormEntity(parametersBody, HTTP.UTF_8));

    HttpResponse response = client.execute(oauthPost);
    int code = response.getStatusLine().getStatusCode();

    if(code == 401) {

      Log.e("Coinbase", "Authentication error.");
      return null;
    } else if(code != 200) {

      throw new IOException("Got HTTP response code " + code);
    }

    JSONObject content = new JSONObject(new JSONTokener(EntityUtils.toString(response.getEntity())));

    String accessToken = content.getString("access_token");
    String refreshToken = content.getString("refresh_token");

    return new String[] { accessToken, refreshToken };
  }
}