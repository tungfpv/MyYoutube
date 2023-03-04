package com.tungpv.myyoutube;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handleShareIntent(getIntent());
        finish();
    }

    private void handleShareIntent(Intent intent) {
        if (intent != null && intent.getAction().equals(Intent.ACTION_SEND)
                && intent.getType().equals("text/plain")) {
            Intent videoServiceIntent = new Intent(this, VideoService.class);
            videoServiceIntent.setAction(Intent.ACTION_SEND);
            videoServiceIntent.putExtra(Intent.EXTRA_TEXT, intent.getStringExtra(Intent.EXTRA_TEXT));
            startService(videoServiceIntent);
        }
    }
}
