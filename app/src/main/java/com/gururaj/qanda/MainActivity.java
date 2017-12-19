package com.gururaj.qanda;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String GEOQUERY_LOG_TAG = "geoquery";
    private static final int PERMISSIONS_REQUEST_LOCATION = 0;
    private static final int RADIUS = 10;
    private static final int RC_SIGN_IN = 1;
    private static final int CREATE_QUESTION = 2;
    private static final int POST_COMMENTS = 3;

    private String mUsername;
    private String mUserId;

    private ListView threadListView;
    private ThreadListAdapter<Post> threadListAdapter;

    // Location Vars
    private LocationManager locationManager;
    private Location currentLocation;
    private GeoLocation center;
    private GeoFire geoFire;
    private GeoQuery geoQuery;
    private boolean isGeoQueryActive;

    // Firebase
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mThreadsDatabaseReference;
    private DatabaseReference mPostsDatabaseReference;
    private DatabaseReference mUsersDatabaseReference;
    private DatabaseReference mGeoFireDatabaseReference;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    private List<String> createdThreads;

    private static Context context;
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init vars
        context = getApplicationContext();
        mUsername = getString(R.string.anonymous);
        mUserId = getString(R.string.anonymous_uid);
        isGeoQueryActive = false;
        createdThreads = new ArrayList<>();

        // Init Firebase variables
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();

        mThreadsDatabaseReference = mFirebaseDatabase.getReference().child("threads");
        mPostsDatabaseReference = mFirebaseDatabase.getReference().child("posts");
        mUsersDatabaseReference = mFirebaseDatabase.getReference().child("users");
        mGeoFireDatabaseReference = mFirebaseDatabase.getReference().child("geofire");

        geoFire = new GeoFire(mGeoFireDatabaseReference);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        // Check location permissions
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION) &&
                    ActivityCompat.shouldShowRequestPermissionRationale(this,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION)) {

                final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setTitle("Permissions Required")
                        .setMessage("You have denied some of the required permissions for this " +
                                "application. Please open settings and allow the permissions.")
                        .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", getPackageName(), null));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .setCancelable(false)
                        .create()
                        .show();

            } else {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSIONS_REQUEST_LOCATION);
            }
        }
        // Already have permissions, init location manager
        else {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            // Runnable to get locations
            mHandler.postDelayed(onRequestLocation, DateUtils.MINUTE_IN_MILLIS * 10);
        }

        // Set up adapter for listview
        threadListAdapter = new ThreadListAdapter<Post>(
                mThreadsDatabaseReference.orderByChild("dateLastChanged/date").equalTo("geofire"),
                Post.class,
                R.layout.thread_layout,
                this) {

            @Override
            protected void populateView(View v, Post model) {
                ImageButton upvoteButton = ((ImageButton) v.findViewById(R.id.upvote_button));
                ((TextView) v.findViewById(R.id.thread_title)).setText(model.getTitle());
                ((TextView) v.findViewById(R.id.thread_title)).setMaxLines(2);
                ((TextView) v.findViewById(R.id.num_votes))
                        .setText(String.valueOf(model.getLikesCount()));

                if (model.getLikes().containsKey(mUserId)) {
                    upvoteButton.setBackgroundResource(R.drawable.ic_thumb_up_black);
                } else {
                    upvoteButton.setBackgroundResource(R.drawable.ic_thumb_up_white);
                }
            }

            @Override
            protected void updateLikes(final Post model, final String refKey) {
                // Map of updated objects
                Map<String, Object> children = new HashMap<>();
                children.put("likes", model.getLikes());
                children.put("likesCount", model.getLikesCount());

                mThreadsDatabaseReference.child(refKey).updateChildren(children);

                // Add user to thread/threadId/users/ if the user liked the thread
                mThreadsDatabaseReference.child(refKey + "/users").addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                GenericTypeIndicator<ArrayList<String>> t =
                                        new GenericTypeIndicator<ArrayList<String>>() {};
                                ArrayList<String> users = dataSnapshot.getValue(t);

                                if (users == null) {
                                    users = new ArrayList<>();
                                }

                                if (model.getLikes().containsKey(mUserId) && !users.contains(mUserId)) {
                                    users.add(mUserId);
                                }

                                mThreadsDatabaseReference.child(refKey + "/users").setValue(users);
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
            }
        };

        threadListView = (ListView) findViewById(R.id.thread_list);

        // If you click on a thread, go to the activity with the thread and comments
        threadListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Intent intent = new Intent(MainActivity.this, PostComments.class);

                intent.putExtra("threadId", threadListAdapter.getReferenceKey(i));
                intent.putExtra("userName", mUsername);
                intent.putExtra("userId", mUserId);
                startActivity(intent);
                threadListAdapter.notifyDataSetChanged();
            }
        });

        // Set up refresh
        ((SwipeRefreshLayout) findViewById(R.id.refreshThreads)).setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        ((SwipeRefreshLayout) findViewById(R.id.refreshThreads))
                                .setRefreshing(false);
                        mHandler.removeCallbacks(onRequestLocation);
                        mHandler.post(onRequestLocation);
                    }
                }
        );

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // user signed in
                    onSignedInInitialize(user.getDisplayName(), user.getUid());
                } else {
                    // user signed out
                    onSignedOutCleanup();
                    if (!isNetworkAvailable()) {
                        Toast.makeText(getApplicationContext(), "Please establish internet " +
                                "connectivity to login to Q&A.", Toast.LENGTH_LONG).show();
                        finish();
                    }

                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setProviders(AuthUI.EMAIL_PROVIDER,
                                            AuthUI.GOOGLE_PROVIDER)
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

        startGeoQuery();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);

        if (!isGeoQueryActive) {
            isGeoQueryActive = true;
            mHandler.post(onRequestLocation);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }

        if (isGeoQueryActive && geoQuery != null) {
            geoQuery.removeAllListeners();
            isGeoQueryActive = false;
        }

        mHandler.removeCallbacks(onRequestLocation);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (geoQuery != null) {
            geoQuery.removeAllListeners();
            mHandler.removeCallbacks(onRequestLocation);
        }

        threadListAdapter.cleanup();
    }

    @Override
    // Restart activity to check for permissions
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        finish();
        startActivity(getIntent());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        // Firebase Auth activity
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                finish();
                startActivity(getIntent());
            } else if (resultCode == RESULT_CANCELED) {
                finish();
            }
        } else if (requestCode == CREATE_QUESTION) {
            if (resultCode == RESULT_OK) {
                String threadTitle = data.getStringExtra("thread_title");
                ArrayList<String> threadUserIds = new ArrayList<>(1);
                Post newQuestion = new Post(threadTitle, mUsername, mUserId);

                // Create new question thread
                DatabaseReference pushedThreadRef = mThreadsDatabaseReference.push();
                pushedThreadRef.setValue(newQuestion);

                // Add current user to list of users involved in threads
                threadUserIds.add(mUserId);
                pushedThreadRef.child("users").setValue(threadUserIds);

                final String threadId = pushedThreadRef.getKey();

                // Create new post with the first post being the title
                DatabaseReference pushedFirstPostRef =
                        mPostsDatabaseReference.child(threadId).push();
                pushedFirstPostRef.setValue(newQuestion);

                if (createdThreads == null) {
                    createdThreads = new ArrayList<>();
                }

                createdThreads.add(threadId);

                // Add list of created threads to user table
                mUsersDatabaseReference
                        .child(mUserId + "/created_threads/")
                        .setValue(createdThreads);

                currentLocation = currentLocation == null ? getLastKnownLocation() : currentLocation;

                if (currentLocation == null) {
                    center = new GeoLocation(0, 0);
                } else {
                    center = new GeoLocation(currentLocation.getLatitude(), currentLocation.getLongitude());
                }

                geoFire.setLocation(threadId, center, new GeoFire.CompletionListener() {

                    @Override
                    public void onComplete(String key, DatabaseError error) {
                        if (error != null) {
                            Log.e("Saving thread location", "There was an error saving the location to GeoFire: " + error);
                        } else {
                            Log.i("Locations saved", "Location for thread " + threadId + " saved on server successfully!");
                        }
                    }
                });

                startGeoQuery();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;

        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                // sign out
                AuthUI.getInstance().signOut(this);
                return true;
            case R.id.new_thread:
                intent = new Intent(this, CreateNewQuestion.class);
                startActivityForResult(intent, CREATE_QUESTION);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onSignedInInitialize(String displayName, String userId) {

        final DatabaseReference mFcmTokensDatabaseReference =
                mFirebaseDatabase.getReference().child("fcm_tokens");

        mUsername = displayName;
        mUserId = userId;

        // Add fcm token to user table if not already there
        mUsersDatabaseReference.child(mUserId + "/fcm_tokens").addListenerForSingleValueEvent(
                new ValueEventListener() {

                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        GenericTypeIndicator<ArrayList<String>> t =
                                new GenericTypeIndicator<ArrayList<String>>() {};
                        ArrayList<String> tokenList = dataSnapshot.getValue(t);
                        String token = FirebaseInstanceId.getInstance().getToken();

                        if (tokenList == null) {
                            tokenList = new ArrayList<>();
                        }

                        if (!tokenList.contains(token)) {
                            tokenList.add(token);
                        }

                        mUsersDatabaseReference.child(mUserId + "/fcm_tokens").setValue(tokenList);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
        });

        // Get current created threads
        mUsersDatabaseReference.child(mUserId + "/created_threads/").addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {

                        GenericTypeIndicator<ArrayList<String>> t =
                                new GenericTypeIndicator<ArrayList<String>>() {};

                        createdThreads = dataSnapshot.getValue(t);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

        mFcmTokensDatabaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                GenericTypeIndicator<HashMap<String, String>> t =
                        new GenericTypeIndicator<HashMap<String, String>>() {};
                HashMap<String, String> fcmTokenMap = dataSnapshot.getValue(t);
                String token = FirebaseInstanceId.getInstance().getToken();

                if (fcmTokenMap == null) {
                    fcmTokenMap = new HashMap<>();
                }

                if (!fcmTokenMap.containsKey(token)) {
                    fcmTokenMap.put(token, mUserId);
                }

                mFcmTokensDatabaseReference.setValue(fcmTokenMap);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void onSignedOutCleanup() {
        mUsername = getString(R.string.anonymous);
        mUserId = getString(R.string.anonymous_uid);

        final DatabaseReference mFcmTokensDatabaseReference =
                mFirebaseDatabase.getReference().child("fcm_tokens");

        // Remove fcm token on sign out
        mUsersDatabaseReference.child(mUserId + "/fcm_tokens").addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        GenericTypeIndicator<ArrayList<String>> t =
                                new GenericTypeIndicator<ArrayList<String>>() {};
                        ArrayList<String> tokenList = dataSnapshot.getValue(t);
                        String token = FirebaseInstanceId.getInstance().getToken();

                        if (tokenList == null) {
                            tokenList = new ArrayList<>();
                        }

                        if (tokenList.contains(token)) {
                            tokenList.remove(token);
                        }

                        mUsersDatabaseReference.child(mUserId + "/fcm_tokens").setValue(tokenList);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
        });

        mFcmTokensDatabaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                GenericTypeIndicator<HashMap<String, String>> t =
                        new GenericTypeIndicator<HashMap<String, String>>() {};
                HashMap<String, String> fcmTokenMap = dataSnapshot.getValue(t);
                String token = FirebaseInstanceId.getInstance().getToken();

                if (fcmTokenMap == null) {
                    fcmTokenMap = new HashMap<>();
                }

                if (fcmTokenMap.containsKey(token)) {
                    fcmTokenMap.remove(token);
                }

                mFcmTokensDatabaseReference.setValue(fcmTokenMap);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private final LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            Log.d("New Location", location.toString());

            // We are starting a new GeoQuery so remove all existing data
            geoQuery.removeAllListeners();
            currentLocation = location;
            center = new GeoLocation(currentLocation.getLatitude(), currentLocation.getLongitude());
            threadListView.setAdapter(null);
            threadListAdapter.clear();
            threadListAdapter.notifyDataSetChanged();
            locationManager.removeUpdates(locationListener);

            startGeoQuery();
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    };

    private Location getLastKnownLocation() {

        if (locationManager == null) {
            return null;
        }

        List<String> providers = locationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e("Last known location", "Not able to retrieve last known location");
                return null;
            }

            Location l = locationManager.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                bestLocation = l;
            }
        }
        return bestLocation;
    }

    // Request location every 10 min
    private Runnable onRequestLocation = new Runnable() {
        @Override
        public void run() {

            Log.i("In location runnable", "Running");
            // Ask for a location
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0,
                    0,
                    locationListener);

            // Run this again in 10 min
            mHandler.postDelayed(onRequestLocation, DateUtils.MINUTE_IN_MILLIS * 10);
        }
    };

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void startGeoQuery() {
        double radius = RADIUS;
        // safety net in case that for some reasons the returned radius is 0
        if (radius < 1.0) {
            radius = 2.0;
        }

        if (geoQuery != null) {
            geoQuery = null;
        }

        // If we don't have an initial location, get one
        if (center == null) {

            currentLocation = currentLocation == null ? getLastKnownLocation() : currentLocation;

            if (currentLocation == null) {
                center = new GeoLocation(0, 0);
            } else if (center == null) {
                center = new GeoLocation(currentLocation.getLatitude(), currentLocation.getLongitude());
            }
        }

        geoQuery = geoFire.queryAtLocation(center, radius);
        Log.i("geoQuery", "center: " + center.toString() + ", radius: " + radius);
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                Log.i(GEOQUERY_LOG_TAG, "Key " + key + " entered the search area at ["
                        + location.latitude + "," + location.longitude + "]");
                DatabaseReference tempRef = mThreadsDatabaseReference.child(key);
                tempRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        // add the thread only if it doesn't exist already in the adapter
                        String key = snapshot.getKey();
                        if (!threadListAdapter.exists(key)) {
                            Log.i(GEOQUERY_LOG_TAG, "item added to list " + key);
                            threadListAdapter.addSingle(snapshot);
                            threadListAdapter.notifyDataSetChanged();
                        } else {
                            // otherwise update the thread
                            Log.i(GEOQUERY_LOG_TAG, "item updated: " + key);
                            threadListAdapter.update(snapshot, key);
                            threadListAdapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }

            @Override
            public void onKeyExited(String key) {

                DatabaseReference tempRef = mThreadsDatabaseReference.child(key);
                tempRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        threadListAdapter.removeSingle(snapshot);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

                Log.i(GEOQUERY_LOG_TAG, "Question " + key + " is no longer in the search area");
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Log.i(GEOQUERY_LOG_TAG, String.format("Key " + key + " moved within the search " +
                        "area to [%f,%f]", location.latitude, location.longitude));
            }

            @Override
            public void onGeoQueryReady() {
                Log.i(GEOQUERY_LOG_TAG, "GeoQuery activated!");
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.e(GEOQUERY_LOG_TAG, "There was an error with this query: " + error);
            }
        });

        threadListView.setAdapter(threadListAdapter);
        isGeoQueryActive = true;
    }
}
