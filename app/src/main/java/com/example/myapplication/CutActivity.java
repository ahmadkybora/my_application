package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import org.florescu.android.rangeseekbar.RangeSeekBar;

import java.io.File;

public class CutActivity extends AppCompatActivity {

    Uri uri;
    ImageView imageView;
    VideoView videoView;
    TextView textViewLeft, textViewRight;
    RangeSeekBar rangeSeekBar;
    boolean isPlaying = false;

    int duration;
    String filePrefix;
    String[] command;
    File dest;
    String original_path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cut);

        imageView = (ImageView) findViewById(R.id.pause);
        videoView = (VideoView) findViewById(R.id.videoView);
        textViewRight = (TextView) findViewById(R.id.tvvRight);
        textViewLeft = (TextView) findViewById(R.id.tvvLeft);
        rangeSeekBar = (RangeSeekBar) findViewById(R.id.seekbar);

        Intent intent = getIntent();
        if(intent == null)
        {
            String imgPath = intent.getStringExtra("uri");
            uri = Uri.parse(imgPath);
            isPlaying = true;
            videoView.setVideoURI(uri);
            videoView.start();
        }
        setListeners();
    }

    private void setListeners() {
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isPlaying) {
                    imageView.setImageResource(R.drawable.ic_play);
                    videoView.pause();
                    isPlaying = false;
                } else {
                    videoView.start();
                    imageView.setImageResource(R.drawable.ic_pause);
                    isPlaying = true;
                }
            }
        });

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                videoView.start();
                duration = mediaPlayer.getDuration()/1000;
                textViewLeft.setText("00:00:00");
                textViewRight.setText(getTime(mediaPlayer.getDuration()/1000));
                mediaPlayer.setLooping(true);
                rangeSeekBar.setRangeValues(0, duration);
                rangeSeekBar.setSelectedMaxValue(duration);
                rangeSeekBar.setSelectedMinValue(0);
                rangeSeekBar.setEnabled(true);

                rangeSeekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener() {
                    @Override
                    public void onRangeSeekBarValuesChanged(RangeSeekBar bar, Object minValue, Object maxValue) {
                        videoView.seekTo((int) minValue * 1000);
                        textViewLeft.setText(getTime((int) bar.getSelectedMinValue()));
                        textViewRight.setText(getTime((int) bar.getSelectedMaxValue()));
                    }
                });

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (videoView.getCurrentPosition() >= rangeSeekBar.getSelectedMaxValue().intValue() * 1000)
                            videoView.seekTo(rangeSeekBar.getSelectedMinValue().intValue() * 1000);
                    }
                }, 1000);
            }
        });
    }

    private String getTime(int seconds) {
        int hr = seconds / 3600;
        int rem = seconds % 3600;
        int mn = rem / 60;
        int sec = rem / 60;
        return String.format("%02d",hr) + ":" + String.format("%02d", mn) + ":" + String.format("%02d", sec);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.cut) {
            final AlertDialog.Builder alert = new AlertDialog.Builder(CutActivity.this);
            LinearLayout linearLayout = new LinearLayout(CutActivity.this);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(50, 0, 50, 100);
            final EditText input = new EditText(CutActivity.this);
            input.setLayoutParams(lp);
            input.setGravity(Gravity.TOP|Gravity.START);
            input.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            linearLayout.addView(input, lp);

            alert.setMessage("Set Video name?");
            alert.setTitle("Change Video name");
            alert.setView(linearLayout);
            alert.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            alert.setPositiveButton("submit", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    filePrefix = input.getText().toString();
                    trimVideo(rangeSeekBar.getSelectedMinValue().intValue() * 1000,
                            rangeSeekBar.getSelectedMaxValue().intValue() * 1000, filePrefix);

                    Intent intent = new Intent(CutActivity.this, ProgressBarActivity.class);
                    intent.putExtra("duration", duration);
                    intent.putExtra("command", command);
                    intent.putExtra("destination", dest.getAbsolutePath());
                    startActivity(intent);

                    finish();
                    dialogInterface.dismiss();
                }
            });

            alert.show();
        }
        return super.onOptionsItemSelected(item);
    }

    private void trimVideo(int startMs, int endMs, String fileName) {
        File folder = new File(Environment.getExternalStorageDirectory() + "/CutVideos");
            if(!folder.exists())
            {
                folder.mkdir();
            }
            filePrefix = fileName;
        String fileExt = ".mp4";
        dest = new File(folder, filePrefix + fileExt);
        original_path = getRealPathFromUri(getApplicationContext(), uri);

        duration = (endMs - startMs) / 1000;
        command = new String[] {"-ss", "" + startMs / 1000, "-y", "-i", original_path, "-t", "" + (endMs - startMs) / 1000, "-vcodec", "mpeg4", "-b:v", "2097152", "-b:a", "48000", "-ac", "2", "-ar", "22050", dest.getAbsolutePath()};
    }

    private String getRealPathFromUri(Context context, Uri ContentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(ContentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);

            cursor.moveToFirst();
            return cursor.getString(column_index);
        } catch (Exception e) {
            e.printStackTrace();
            return  "";
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }
}