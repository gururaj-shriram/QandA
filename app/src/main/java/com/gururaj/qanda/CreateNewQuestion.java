package com.gururaj.qanda;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;

public class CreateNewQuestion extends AppCompatActivity {

    private static final int MAX_QUESTION_SIZE = 300;
    private Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_new_question);

        intent = getIntent();

        Toolbar myToolbar = (Toolbar) findViewById(R.id.new_thread_toolbar);
        myToolbar.setTitle(R.string.post_question_label);
        setSupportActionBar(myToolbar);
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
            case R.id.cancel_button:
                setResult(RESULT_CANCELED, intent);
                finish();
                return true;
            case R.id.post_button:
                String thread_title = ((EditText)findViewById(R.id.new_question_text)).getText().toString();
                if (thread_title.length() > MAX_QUESTION_SIZE) {
                    Toast.makeText(
                            getApplicationContext(),
                            "Please keep questions to under 300 characters.",
                            Toast.LENGTH_SHORT)
                            .show();
                    return false;
                } else if (thread_title.isEmpty()) {
                    Toast.makeText(
                            getApplicationContext(),
                            "Your question is empty!",
                            Toast.LENGTH_SHORT)
                            .show();
                    return false;
                }

                intent.putExtra("thread_title", thread_title);
                setResult(RESULT_OK, intent);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
