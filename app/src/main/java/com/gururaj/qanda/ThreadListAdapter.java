package com.gururaj.qanda;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Gururaj on 11/23/17.
 */

public abstract class ThreadListAdapter<T extends Post> extends BaseAdapter {

    private static final String LOG_TAG = "ThreadListAdapter";
    private Query mRef;
    private Class<T> mModelClass;
    private int mLayout;
    private LayoutInflater mInflater;
    private List<T> mModels;
    private Map<String, T> mModelKeys;
    private ChildEventListener mListener;

    public ThreadListAdapter(Query mRef, Class<T> mModelClass, int mLayout, Activity activity) {
        this.mRef = mRef;
        this.mModelClass = mModelClass;
        this.mLayout = mLayout;
        mInflater = activity.getLayoutInflater();
        mModels = new ArrayList<>();
        mModelKeys = new HashMap<>();
        // Look for all child events. We will then map them to our own internal ArrayList, which backs ListView
        mListener = this.mRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {

                T model = dataSnapshot.getValue(ThreadListAdapter.this.mModelClass);
                mModelKeys.put(dataSnapshot.getKey(), model);

                // Add new child to the first element to reverse list order
                // Since we want new children to appear first
                mModels.add(0, model);

                notifyDataSetChanged();
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                Log.d(LOG_TAG, "onChildChanged");
                // One of the mModels changed. Replace it in our list and name mapping
                String modelName = dataSnapshot.getKey();
                T oldModel = mModelKeys.get(modelName);
                T newModel = dataSnapshot.getValue(ThreadListAdapter.this.mModelClass);
                int index = mModels.indexOf(oldModel);

                mModels.set(index, newModel);
                mModelKeys.put(modelName, newModel);

                notifyDataSetChanged();
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                Log.d(LOG_TAG, "onChildRemoved");
                // A model was removed from the list. Remove it from our list and the name mapping
                String modelName = dataSnapshot.getKey();
                T oldModel = mModelKeys.get(modelName);
                mModels.remove(oldModel);
                mModelKeys.remove(modelName);
                notifyDataSetChanged();
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {
                Log.d(LOG_TAG, "onChildMoved");
                // A model changed position in the list. Update our list accordingly
                String modelName = dataSnapshot.getKey();
                T oldModel = mModelKeys.get(modelName);
                T newModel = dataSnapshot.getValue(ThreadListAdapter.this.mModelClass);
                int index = mModels.indexOf(oldModel);
                mModels.remove(index);
                if (previousChildName == null) {
                    mModels.add(0, newModel);
                } else {
                    T previousModel = mModelKeys.get(previousChildName);
                    int previousIndex = mModels.indexOf(previousModel);
                    int nextIndex = previousIndex + 1;
                    if (nextIndex == mModels.size()) {
                        mModels.add(newModel);
                    } else {
                        mModels.add(nextIndex, newModel);
                    }
                }
                notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError firebaseError) {
                Log.e("ThreadListAdapter", "Listen was cancelled, no more updates will occur");
            }
        });
    }
    public void cleanup() {
        // We're being destroyed, let go of our mListener and forget about all of the mModels
        mModels.clear();
        mModelKeys.clear();
    }

    public void clear() {
        mModels.clear();
        mModelKeys.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mModels.size();
    }

    @Override
    public T getItem(int i) {
        return mModels.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    public String getReferenceKey(int position) {
        T model = getItem(position);

        for (Map.Entry<String, T> entry : mModelKeys.entrySet()) {
            if (entry != null && entry.getValue().equals(model))
                return entry.getKey();
        }

        return null;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = mInflater.inflate(mLayout, viewGroup, false);
        }

        T model = mModels.get(i);
        ImageButton upvoteButton = (ImageButton) view.findViewById(R.id.upvote_button);

        upvoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Get row num of upvote button
                View parentRow = (View) v.getParent();
                ListView listView = (ListView) parentRow.getParent();
                int position = listView.getPositionForView(parentRow);

                // Get current post
                T newModel = mModels.get(position);
                HashMap<String, Boolean> likes = newModel.getLikes();
                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                String refKey = getReferenceKey(position);

                // Already liked, unlike
                if (likes.containsKey(userId)) {
                    v.setBackgroundResource(R.drawable.ic_thumb_up_white);
                    likes.remove(userId);
                }
                // Unlike
                else {
                    v.setBackgroundResource(R.drawable.ic_thumb_up_black);
                    likes.put(userId, true);
                }

                newModel.setLikes(likes);
                newModel.setLikesCount(likes.size());

                mModels.set(position, newModel);
                mModelKeys.put(refKey, newModel);

                updateLikes(newModel, refKey);
            }
        });

        if (model != null) {
            // Call out to subclass to marshall this model into the provided view
            populateView(view, model);
        }
        return view;
    }

    protected abstract void populateView(View v, T model);

    protected abstract void updateLikes(T model, String refKey);

    public void addSingle(DataSnapshot snapshot) {
        T model = snapshot.getValue(ThreadListAdapter.this.mModelClass);

        mModelKeys.put(snapshot.getKey(), model);
        mModels.add(0, model);

        notifyDataSetChanged();
    }

    public void removeSingle(DataSnapshot snapshot) {
        Log.i("removeSingle", mModelKeys.toString());
        T model = snapshot.getValue(ThreadListAdapter.this.mModelClass);
        mModelKeys.remove(snapshot.getKey());
        mModels.remove(model);

        notifyDataSetChanged();
    }

    public void update(DataSnapshot snapshot, String key) {
        T oldModel = mModelKeys.get(key);
        T newModel = snapshot.getValue(ThreadListAdapter.this.mModelClass);
        int index = mModels.indexOf(oldModel);

        if (index >= 0) {
            mModels.set(index, newModel);
            mModelKeys.put(key, newModel);

            notifyDataSetChanged();
        }
    }

    public boolean exists(String key) {
        return mModelKeys.containsKey(key);
    }
}