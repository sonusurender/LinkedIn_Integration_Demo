package com.android_linkedin_integration_demo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.linkedin.platform.APIHelper;
import com.linkedin.platform.DeepLinkHelper;
import com.linkedin.platform.LISessionManager;
import com.linkedin.platform.errors.LIApiError;
import com.linkedin.platform.errors.LIAuthError;
import com.linkedin.platform.errors.LIDeepLinkError;
import com.linkedin.platform.listeners.ApiListener;
import com.linkedin.platform.listeners.ApiResponse;
import com.linkedin.platform.listeners.AuthListener;
import com.linkedin.platform.listeners.DeepLinkListener;
import com.linkedin.platform.utils.Scope;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private ImageView userImageView;
    private TextView userDetails;
    private Button signInButton, logoutButton, shareButton, openMyProfileButton, openOtherProfileButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
    }

    /**
     * init the views by finding the ID of all views
     */
    private void initViews() {
        signInButton = findViewById(R.id.linkedin_login_button);
        logoutButton = findViewById(R.id.logout_button);
        userImageView = findViewById(R.id.user_profile_image_view);
        userDetails = findViewById(R.id.user_details_label);
        shareButton = findViewById(R.id.share_button);
        openMyProfileButton = findViewById(R.id.open_my_profile_button);
        openOtherProfileButton = findViewById(R.id.open_others_profile_button);
    }

    /**
     * method to get Hash key for current app package which will be used to add in LinkedIn Application settings
     * call this method once to get Key Hash
     */
    private void getPackageHash() {
        try {

            @SuppressLint("PackageManagerGetSignatures") PackageInfo info = getPackageManager().getPackageInfo(
                    "com.android_linkedin_integration_demo",//give your package name here
                    PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());

                Log.d(TAG, "Hash  : " + Base64.encodeToString(md.digest(), Base64.NO_WRAP));//Key hash is printing in Log
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            Log.d(TAG, e.getMessage(), e);
        }

    }

    // Build the list of member permissions our LinkedIn session requires
    private static Scope buildScope() {
        //Check Scopes in Application Settings before passing here else you won't able to read that data
        return Scope.build(Scope.R_BASICPROFILE, Scope.R_EMAILADDRESS, Scope.W_SHARE);
    }

    /**
     * on Sign In button do LinkedIn Authentication
     *
     * @param view
     */
    public void signInWithLinkedIn(View view) {
        //First check if user is already authenticated or not and session is valid or not
        if (!LISessionManager.getInstance(this).getSession().isValid()) {
            //if not valid then start authentication
            LISessionManager.getInstance(getApplicationContext()).init(this, buildScope()//pass the build scope here
                    , new AuthListener() {
                        @Override
                        public void onAuthSuccess() {
                            // Authentication was successful. You can now do
                            // other calls with the SDK.
                            Toast.makeText(MainActivity.this, "Successfully authenticated with LinkedIn.", Toast.LENGTH_SHORT).show();

                            //on successful authentication fetch basic profile data of user
                            fetchBasicProfileData();
                        }

                        @Override
                        public void onAuthError(LIAuthError error) {
                            // Handle authentication errors
                            Log.e(TAG, "Auth Error :" + error.toString());
                            Toast.makeText(MainActivity.this, "Failed to authenticate with LinkedIn. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    }, true);//if TRUE then it will show dialog if
            // any device has no LinkedIn app installed to download app else won't show anything
        } else {
            Toast.makeText(this, "You are already authenticated.", Toast.LENGTH_SHORT).show();

            //if user is already authenticated fetch basic profile data for user
            fetchBasicProfileData();
        }

    }

    /**
     * method to fetch basic profile data
     */
    private void fetchBasicProfileData() {

        //In URL pass whatever data from user you want for more values check below link
        //LINK : https://developer.linkedin.com/docs/fields/basic-profile
        String url = "https://api.linkedin.com/v1/people/~:(id,first-name,last-name,headline,public-profile-url,picture-url,email-address,picture-urls::(original))";

        APIHelper apiHelper = APIHelper.getInstance(getApplicationContext());
        apiHelper.getRequest(this, url, new ApiListener() {
            @Override
            public void onApiSuccess(ApiResponse apiResponse) {
                // Success!
                Log.d(TAG, "API Res : " + apiResponse.getResponseDataAsString() + "\n" + apiResponse.getResponseDataAsJson().toString());
                Toast.makeText(MainActivity.this, "Successfully fetched LinkedIn profile data.", Toast.LENGTH_SHORT).show();

                //update UI on successful data fetched
                updateUI(apiResponse);
            }

            @Override
            public void onApiError(LIApiError liApiError) {
                // Error making GET request!
                Log.e(TAG, "Fetch profile Error   :" + liApiError.getLocalizedMessage());
                Toast.makeText(MainActivity.this, "Failed to fetch basic profile data. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * method to update UI
     *
     * @param apiResponse after fetching basic profile data
     */
    private void updateUI(ApiResponse apiResponse) {
        try {
            if (apiResponse != null) {
                JSONObject jsonObject = apiResponse.getResponseDataAsJson();

                //display user basic details
                userDetails.setText("Name : " + jsonObject.getString("firstName") + " " + jsonObject.getString("lastName") + "\nHeadline : " + jsonObject.getString("headline") + "\nEmail Id : " + jsonObject.getString("emailAddress"));

                //use the below string value to display small profile picture
                String smallPicture = jsonObject.getString("pictureUrl");

                //use the below json parsing for different profile pictures and big size images
                JSONObject pictureURLObject = jsonObject.getJSONObject("pictureUrls");
                if (pictureURLObject.getInt("_total") > 0) {
                    //get array of picture urls
                    JSONArray profilePictureURLArray = pictureURLObject.getJSONArray("values");
                    if (profilePictureURLArray != null && profilePictureURLArray.length() > 0) {
                        // get 1st image link and display using picasso
                        Picasso.with(this).load(profilePictureURLArray.getString(0))
                                .placeholder(R.mipmap.ic_launcher_round)
                                .error(R.mipmap.ic_launcher_round)
                                .into(userImageView);
                    }
                } else {
                    // if no big image is available then display small image using picasso
                    Picasso.with(this).load(smallPicture)
                            .placeholder(R.mipmap.ic_launcher_round)
                            .error(R.mipmap.ic_launcher_round)
                            .into(userImageView);
                }

                //show hide views
                signInButton.setVisibility(View.GONE);
                logoutButton.setVisibility(View.VISIBLE);
                userDetails.setVisibility(View.VISIBLE);
                userImageView.setVisibility(View.VISIBLE);
                shareButton.setVisibility(View.VISIBLE);
                openMyProfileButton.setVisibility(View.VISIBLE);
                openOtherProfileButton.setVisibility(View.VISIBLE);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doLogout(View view) {
        //clear session on logout click
        LISessionManager.getInstance(this).clearSession();

        //show hide views
        signInButton.setVisibility(View.VISIBLE);
        logoutButton.setVisibility(View.GONE);
        userDetails.setVisibility(View.GONE);
        userImageView.setVisibility(View.GONE);
        shareButton.setVisibility(View.GONE);
        openMyProfileButton.setVisibility(View.GONE);
        openOtherProfileButton.setVisibility(View.GONE);

        //show toast
        Toast.makeText(this, "Logout successfully.", Toast.LENGTH_SHORT).show();
    }

    /**
     * method call on share click
     *
     * @param view
     */
    public void onShareClick(View view) {
        //LINK : https://developer.linkedin.com/docs/share-on-linkedin
        String url = "https://api.linkedin.com/v1/people/~/shares";

        JSONObject body = null;
        try {
            //prepare json object for sharing
            body = new JSONObject("{" +

                    "\"comment\": \"Test Comment\"," + //A comment by the member to associated with the share.
                    // If none of the above content parameters are provided, the comment must contain a URL to the content you want to share.  If the comment contains multiple URLs, only the first one will be analyzed for content to share.

                    "\"visibility\": { \"code\": \"anyone\" }," +//A collection of visibility information about the share.
                    //One of the following values:

                    // anyone:  Share will be visible to all members.
                    // connections-only:  Share will only be visible to connections of the member performing the share.
                    // This field is required in all sharing calls.

                    "\"content\": { " +
                    "\"title\": \"Test share\"," +//The title of the content being shared.

                    "\"description\": \"Testing the mobile SDK share feature!\"," +//The description of the content being shared.

                    "\"submitted-url\": \"http://www.androhub.com/\"," +//A fully qualified URL for the content being shared.

                    "\"submitted-image-url\": \"http://androhub.com/wp-content/uploads/2016/05/andro_new.png\"" +//A fully qualified URL to a thumbnail image to accompany the shared content. The image should be at least 80 x 150px for best results.

                    "}" +
                    "}");
        } catch (JSONException e) {
            e.printStackTrace();

        }

        APIHelper apiHelper = APIHelper.getInstance(getApplicationContext());
        apiHelper.postRequest(this, url, body, new ApiListener() {
            @Override
            public void onApiSuccess(ApiResponse apiResponse) {
                // Success!
                Log.d(TAG, "Share response : " + apiResponse.toString());
                Toast.makeText(MainActivity.this, "Shared successfully.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onApiError(LIApiError liApiError) {
                // Error making POST request!
                Log.e(TAG, "Share error : " + liApiError.getLocalizedMessage());
                Toast.makeText(MainActivity.this, "Failed to share. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * method to open current profile using LinkedIn DeepLink helper
     *
     * @param view
     */
    public void openMyProfile(View view) {
        DeepLinkHelper deepLinkHelper = DeepLinkHelper.getInstance();

        // Open the current user's profile
        deepLinkHelper.openCurrentProfile(this, new DeepLinkListener() {
            @Override
            public void onDeepLinkSuccess() {
                // Successfully sent user to LinkedIn app
                Toast.makeText(MainActivity.this, "Current profile opened successfully.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeepLinkError(LIDeepLinkError error) {
                // Error sending user to LinkedIn app
                Log.e(TAG, "Current profile open error : " + error.toString());
                Toast.makeText(MainActivity.this, "Failed to open current profile.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * method to open others profile by passing their IDs
     * NOTE : Storing member ID values on your own server allows your application to direct users to the official LinkedIn profiles
     * for any other users that have authorized your mobile application.
     *
     * @param view
     */
    public void openOthersProfile(View view) {

        //A sample member ID value to open for this first you have to get user id
        final String targetID = "any_id";

        DeepLinkHelper deepLinkHelper = DeepLinkHelper.getInstance();

        // Open the target LinkedIn member's profile
        deepLinkHelper.openOtherProfile(this, targetID, new DeepLinkListener() {
            @Override
            public void onDeepLinkSuccess() {
                // Successfully sent user to LinkedIn app
                Toast.makeText(MainActivity.this, "Other profile opened successfully.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeepLinkError(LIDeepLinkError error) {
                // Error sending user to LinkedIn app
                Log.e(TAG, "Other profile open error : " + error.toString());
                Toast.makeText(MainActivity.this, "Failed to open other profile.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Add this line to your existing onActivityResult() method
        LISessionManager.getInstance(getApplicationContext()).onActivityResult(this, requestCode, resultCode, data);

        // Add this line to your existing onActivityResult() method
        DeepLinkHelper deepLinkHelper = DeepLinkHelper.getInstance();
        deepLinkHelper.onActivityResult(this, requestCode, resultCode, data);

    }

    /**
     * cancel API calls
     */
    private void cancelAPIRequest(){
        APIHelper apiHelper = APIHelper.getInstance(getApplicationContext());
        apiHelper.cancelCalls(this);
    }

}
