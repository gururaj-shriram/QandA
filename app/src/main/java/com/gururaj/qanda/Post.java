package com.gururaj.qanda;

import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Gururaj on 11/19/17.
 */

public class Post {
    private String title;
    private String userName;
    private String userId;
    private int likesCount;
    private HashMap<String, Boolean> likes;
    private HashMap<String, Object> dateCreated;
    private HashMap<String, Object> dateLastChanged;
    private ArrayList<String> users;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getLikesCount() {
        return likesCount;
    }

    public void setLikesCount(int likesCount) {
        this.likesCount = likesCount;
    }

    public HashMap<String, Boolean> getLikes() {
        return likes;
    }

    public void setLikes(HashMap<String, Boolean> likes) {
        this.likes = likes;
    }

    public Post() {
        this.likesCount = 0;
        this.likes = new HashMap<>();
        this.users = new ArrayList<>();
    }

    public Post(String title, String userName, String userId) {

        this.title = title;
        this.userName = userName;
        this.userId = userId;
        this.likesCount = 0;
        this.likes = new HashMap<>();
        this.users = new ArrayList<>();


        HashMap<String, Object> dateLastChangedObj = new HashMap<>();
        dateLastChangedObj.put("date", ServerValue.TIMESTAMP);
        this.dateLastChanged = dateLastChangedObj;
    }

    public Post(String title, String userName, String userId, HashMap<String, Object> dateCreated) {

        this.title = title;
        this.userName = userName;
        this.userId = userId;
        this.dateCreated = dateCreated;
        this.likesCount = 0;
        this.likes = new HashMap<>();

        HashMap<String, Object> dateLastChangedObj = new HashMap<>();
        dateLastChangedObj.put("date", ServerValue.TIMESTAMP);
        this.dateLastChanged = dateLastChangedObj;
    }

    public String getTitle() {

        return title;
    }

    public HashMap<String, Object> getDateLastChanged() {
        return dateLastChanged;
    }

    public void setDateLastChanged(HashMap<String, Object> dateLastChanged) {
        this.dateLastChanged = dateLastChanged;
    }

    public HashMap<String, Object> getDateCreated() {
        //If there is a dateCreated object already, then return that
        if (dateCreated != null) {
            return dateCreated;
        }
        //Otherwise make a new object set to ServerValue.TIMESTAMP
        HashMap<String, Object> dateCreatedObj = new HashMap<>();
        dateCreatedObj.put("date", ServerValue.TIMESTAMP);
        return dateCreatedObj;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null && !(o instanceof Post)) {
            return false;
        }

        Post p = (Post) o;

        return p.title.equals(this.title) && p.userId.equals(this.userId);
    }

    public ArrayList<String> getUsers() {
        return users;
    }

    public void setUsers(ArrayList<String> users) {
        this.users = users;
    }
}