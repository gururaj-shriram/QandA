package com.gururaj.qanda;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Gururaj on 12/5/17.
 */

public class MyFirebaseInstanceIDService extends FirebaseInstanceIdService {

    private static final String TAG = "MyFirebaseIDService";

    @Override
    public void onTokenRefresh() {
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "Refreshed token: " + refreshedToken);

        sendRegistrationToDatabase(refreshedToken);
    }

    private void sendRegistrationToDatabase(final String token) {
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        FirebaseDatabase mFirebaseDatabase = FirebaseDatabase.getInstance();
        final DatabaseReference mUsersDatabaseReference =
                mFirebaseDatabase.getReference().child("users/");
        final DatabaseReference mFcmTokensDatabaseReference =
                mFirebaseDatabase.getReference().child("fcm_tokens");

        if (user == null) {
            return;
        }

        // When the fcm token is changed, add it into the users table
        mUsersDatabaseReference.child(user.getUid() + "/fcm_tokens").addListenerForSingleValueEvent(
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

                        mUsersDatabaseReference.child(user.getUid() + "/fcm_tokens").setValue(tokenList);
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
                    fcmTokenMap.put(token, user.getUid());
                }

                mFcmTokensDatabaseReference.setValue(fcmTokenMap);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }
}
