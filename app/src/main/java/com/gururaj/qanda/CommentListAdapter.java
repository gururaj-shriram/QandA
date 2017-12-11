package com.gururaj.qanda;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListView;

import com.firebase.ui.database.FirebaseListAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;

import java.util.HashMap;

/**
 * Created by Gururaj on 12/9/17.
 */

public abstract class CommentListAdapter<T extends Post> extends FirebaseListAdapter<T> {
    /**
     * @param activity    The activity containing the ListView
     * @param modelClass  Firebase will marshall the data at a location into an instance of a class that you provide
     * @param modelLayout This is the layout used to represent a single list item. You will be responsible for populating an
     *                    instance of the corresponding view with the data from an instance of modelClass.
     * @param ref         The Firebase location to watch for data changes. Can also be a slice of a location, using some
     *                    combination of {@code limit()}, {@code startAt()}, and {@code endAt()}.
     */
    public CommentListAdapter(Activity activity, Class<T> modelClass, int modelLayout, Query ref) {
        super(activity, modelClass, modelLayout, ref);
    }

    @Override
    public View getView(final int i, View view, ViewGroup viewGroup) {

        final View finalView = super.getView(i, view, viewGroup);
        final ImageButton upvoteButton = (ImageButton) finalView.findViewById(R.id.upvote_button);

        // Synchronize like counter values
        upvoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                View parentRow = (View) v.getParent();
                ListView listView = (ListView) parentRow.getParent();
                final int position = listView.getPositionForView(parentRow);

                final T model = CommentListAdapter.super.getItem(position);
                final HashMap<String, Boolean> likes = model.getLikes();
                final String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                final DatabaseReference ref = CommentListAdapter.super.getRef(position);

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

                model.setLikes(likes);
                model.setLikesCount(likes.size());

                updateLikes(model, ref, position);
            }
        });

        return finalView;
    }

    // Update likes in Threads and Posts
    protected abstract void updateLikes(T model, DatabaseReference ref, int pos);
}
