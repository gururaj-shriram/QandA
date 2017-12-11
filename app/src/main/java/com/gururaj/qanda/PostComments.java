package com.gururaj.qanda;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.database.FirebaseListAdapter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PostComments extends AppCompatActivity {

    private static final int DEFAULT_MSG_LENGTH_LIMIT = 300;
    private Intent intent;
    private String threadId;
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mPostDatabaseReference;
    private DatabaseReference mThreadsDatabaseReference;
    private DatabaseReference mUsersDatabaseReference;
    private FirebaseListAdapter<Post> firebaseThreadAdapter;
    private ListView threadListView;
    private String userName;
    private String userId;
    private Button commentSendButton;
    private EditText commentEditText;
    private Post titlePost;
    private String firstPostKey;
    private boolean firstPost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_comments);

        intent = getIntent();
        threadId = intent.getStringExtra("threadId");
        userName = intent.getStringExtra("userName");
        userId = intent.getStringExtra("userId");
        firstPost = true;

        if (userName == null) {
            userName = getString(R.string.anonymous);
        }

        if (userId == null) {
            userId = getString(R.string.anonymous_uid);
        }

        // Don't automatically open keyboard for post
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mPostDatabaseReference = mFirebaseDatabase.getReference().child("posts/" + threadId);
        mThreadsDatabaseReference = mFirebaseDatabase.getReference().child("threads/" + threadId);
        mUsersDatabaseReference = mFirebaseDatabase.getReference().child("users/" + userId);

        mThreadsDatabaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                titlePost = dataSnapshot.getValue(Post.class);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        mPostDatabaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                // Synchronize first post with thread
                if (firstPost) {
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        firstPostKey = snapshot.getKey();
                        break;
                    }

                    // Update 1st post to title
                    mPostDatabaseReference.child(firstPostKey).setValue(titlePost);
                    firstPost = false;
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        firebaseThreadAdapter = new CommentListAdapter<Post>(
                this,
                Post.class,
                R.layout.thread_layout,
                mPostDatabaseReference) {

            @Override
            protected void populateView(View v, Post model, int position) {
                ImageButton upvoteButton = ((ImageButton) v.findViewById(R.id.upvote_button));

                ((TextView) v.findViewById(R.id.thread_title)).setText(model.getTitle());
                ((TextView) v.findViewById(R.id.num_votes))
                        .setText(String.valueOf(model.getLikesCount()));

                if (model.getLikes().containsKey(userId)) {
                    upvoteButton.setBackgroundResource(R.drawable.ic_thumb_up_black);
                } else {
                    upvoteButton.setBackgroundResource(R.drawable.ic_thumb_up_white);
                }
            }

            @Override
            protected void updateLikes(final Post model, DatabaseReference ref, int pos) {
                ref.setValue(model);

                // Update thread likes if it's a title update
                if (pos == 0 && threadId.equals(ref.getParent().getKey())) {
                    mThreadsDatabaseReference.addListenerForSingleValueEvent(
                            new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    Map<String, Object> children = new HashMap<>();

                                    children.put("likes", model.getLikes());
                                    children.put("likesCount", model.getLikesCount());

                                    mThreadsDatabaseReference.updateChildren(children);
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });

                    mThreadsDatabaseReference.child("/users/").addListenerForSingleValueEvent(
                            new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    GenericTypeIndicator<ArrayList<String>> t =
                                            new GenericTypeIndicator<ArrayList<String>>() {};
                                    ArrayList<String> users = dataSnapshot.getValue(t);

                                    if (users == null) {
                                        users = new ArrayList<>();
                                    }

                                    if (model.getLikes().containsKey(userId)) {
                                        users.add(userId);
                                    }

                                    mThreadsDatabaseReference.child("/users/").setValue(users);
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                }
            }
        };

        threadListView = (ListView) findViewById(R.id.post_list);
        threadListView.setAdapter(firebaseThreadAdapter);

        commentSendButton = ((Button) findViewById(R.id.commentSendButton));
        commentEditText = ((EditText)findViewById(R.id.commentEditText));

        // Enable Send button when there's text to send
        commentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    commentSendButton.setEnabled(true);
                } else {
                    commentSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        commentEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        commentSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String commentText = commentEditText.getText().toString();
                Post newComment = new Post(commentText, userName, userId);
                HashMap<String, Object> dateChangedObj = new HashMap<>();
                DatabaseReference pushedCommentRef = mPostDatabaseReference.push();

                dateChangedObj.put("date", ServerValue.TIMESTAMP);
                newComment.setDateLastChanged(dateChangedObj);

                pushedCommentRef.setValue(newComment);
                pushedCommentRef.child("userId").setValue(userId);

                mThreadsDatabaseReference.child("/dateLastChanged").updateChildren(dateChangedObj);
                addUserToThreadIfNotThere();

                firebaseThreadAdapter.notifyDataSetChanged();
                commentEditText.setText("");
            }
        });
    }

    private void addUserToThreadIfNotThere() {
        final ArrayList<String> userList = new ArrayList<>();

        mThreadsDatabaseReference.child("/users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                GenericTypeIndicator<ArrayList<String>> t = 
                        new GenericTypeIndicator<ArrayList<String>>() {};
                ArrayList<String> temp = dataSnapshot.getValue(t);

                if (temp != null) {
                    userList.addAll(temp);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        if (!userList.contains(userId)) {
            userList.add(userId);
            mThreadsDatabaseReference.child("/users").setValue(userList);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.create_question_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                // sign out
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
