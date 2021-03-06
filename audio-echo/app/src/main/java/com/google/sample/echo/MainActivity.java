/*
 * Copyright 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sample.echo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;


public class MainActivity extends Activity
        implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final int AUDIO_ECHO_REQUEST = 0;
    Button   controlButton;
    Button   toggleFilter;
    TextView statusView;
    String  nativeSampleRate;
    String  nativeSampleBufSize;
    String nativeSampleBufSize_base;
    boolean supportRecording = false;
    Boolean isPlaying = false;
    boolean filterOn = false;
    int delay_factor=1;
    Thread mThread=null;
    SeekBar seekBar =  null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        controlButton = (Button)findViewById((R.id.capture_control_button));
        statusView = (TextView)findViewById(R.id.statusView);
        toggleFilter = (Button)findViewById((R.id.toggle_filter_button));
        final SeekBar delayBar = (SeekBar)findViewById(R.id.delay_factor);
        seekBar = delayBar;
        delayBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                delay_factor=i*10;
                if (i==0)
                    delay_factor = 1;
                if(!isPlaying) {
                    return;
                }

                // we are in echoing, re-start it
                mThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        restartEcho();
                    }
                });
                mThread.start();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        // initialize native audio system
        queryNativeAudioParameters();
        updateNativeAudioUI();
    }
    @Override
    protected void onDestroy() {
        if (supportRecording) {
            if (isPlaying) {
                if (filterOn) {
                    enableFilter(false);
                }
                stopPlay();
                updateNativeAudioUI();
                deleteAudioRecorder();
                deleteSLBufferQueueAudioPlayer();
                deleteSLEngine();
            }
            isPlaying = false;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    private void startEchoThread()
    {
        if (!isPlaying) {
            createSLEngine(Integer.parseInt(nativeSampleRate), delay_factor * Integer.parseInt(nativeSampleBufSize));
            enableFilter(filterOn);
            if(!createSLBufferQueueAudioPlayer()) {
                updateStatusUI(getString(R.string.error_player));
                return;
            }
            if(!createAudioRecorder()) {
                deleteSLBufferQueueAudioPlayer();
                updateStatusUI(getString(R.string.error_recorder));
                return;
            }
            startPlay();   // this must include startRecording()
            updateStatusUI(getString(R.string.status_echoing));
        } else {
            enableFilter(false);
            stopPlay();
            deleteAudioRecorder();
            deleteSLBufferQueueAudioPlayer();
            deleteSLEngine();
            updateNativeAudioUI();
        }
        isPlaying = !isPlaying;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                controlButton.setText(getString((isPlaying == true) ?
                        R.string.StopEcho: R.string.StartEcho));
            }
        });
        enableUI(true);
    }
    private void restartEcho() {
        // disabling UI...
        enableUI(false);
        // Stopping it first
        enableFilter(false);
        stopPlay();
        updateNativeAudioUI();
        deleteAudioRecorder();
        deleteSLBufferQueueAudioPlayer();
        deleteSLEngine();

        // Starting...
        createSLEngine(Integer.parseInt(nativeSampleRate), delay_factor * Integer.parseInt(nativeSampleBufSize));
        enableFilter(filterOn);
        if(!createSLBufferQueueAudioPlayer()) {
            updateStatusUI(getString(R.string.error_player));
            return;
        }
        if(!createAudioRecorder()) {
            deleteSLBufferQueueAudioPlayer();
            updateStatusUI(getString(R.string.error_recorder));
            return;
        }
        startPlay();

        // Enabling UI ...
        enableUI(true);
    }

    private void startEcho() {
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                startEchoThread();
            }
        });
        mThread.start();
    }

    public void onEchoClick(View view) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                                               PackageManager.PERMISSION_GRANTED) {
            statusView.setText(getString(R.string.status_record_perm));
            ActivityCompat.requestPermissions(
                    this,
                    new String[] { Manifest.permission.RECORD_AUDIO },
                    AUDIO_ECHO_REQUEST);
            return;
        }
        enableUI(false);
        startEcho();
    }

    public void onFilterClick(View view) {
        filterOn = !filterOn;
        toggleFilter.setText(((filterOn == true) ? "Disable" : "Enable") + " Filter");
        enableFilter(filterOn);

    }
    public void getLowLatencyParameters(View view) {
        updateNativeAudioUI();
        return;
    }

    private void queryNativeAudioParameters() {
        AudioManager myAudioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        nativeSampleRate  =  myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        nativeSampleBufSize_base =myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        nativeSampleBufSize =myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);




        int recBufSize = AudioRecord.getMinBufferSize(
                Integer.parseInt(nativeSampleRate),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        supportRecording = true;
        if (recBufSize == AudioRecord.ERROR ||
            recBufSize == AudioRecord.ERROR_BAD_VALUE) {
            supportRecording = false;
        }
    }
    private void updateNativeAudioUI() {
        if (!supportRecording) {
            statusView.setText(getString(R.string.error_no_mic));
            controlButton.setEnabled(false);
            seekBar.setEnabled(false);
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            statusView.setText("nativeSampleRate    = " + nativeSampleRate + "\n" +
                   "nativeSampleBufSize = " + delay_factor * Integer.parseInt(nativeSampleBufSize) + "\n");
            }
        });
    }

    private void enableUI(final boolean enable) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                controlButton.setEnabled(enable);
                seekBar.setEnabled(enable);
            }
        });
    }

    private void updateStatusUI(String msg) {
        final String nextStatus = msg;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusView.setText(nextStatus);
            }
        });

    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        /*
         * if any permission failed, the sample could not play
         */
        if (AUDIO_ECHO_REQUEST != requestCode) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 1  ||
            grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            /*
             * When user denied permission, throw a Toast to prompt that RECORD_AUDIO
             * is necessary; also display the status on UI
             * Then application goes back to the original state: it behaves as if the button
             * was not clicked. The assumption is that user will re-click the "start" button
             * (to retry), or shutdown the app in normal way.
             */
            statusView.setText(getString(R.string.error_no_permission));
            Toast.makeText(getApplicationContext(),
                    getString(R.string.prompt_permission),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        /*
         * When permissions are granted, we prompt the user the status. User would
         * re-try the "start" button to perform the normal operation. This saves us the extra
         * logic in code for async processing of the button listener.
         */
        statusView.setText("RECORD_AUDIO permission granted, touch " +
                           getString(R.string.StartEcho) + " to begin");


        // The callback runs on app's thread, so we are safe to resume the action
        startEcho();
    }

    /*
     * Loading our Libs
     */
    static {
        System.loadLibrary("echo");
    }

    /*
     * jni function implementations...
     */
    public static native void createSLEngine(int rate, int framesPerBuf);
    public static native void deleteSLEngine();

    public static native boolean createSLBufferQueueAudioPlayer();
    public static native void deleteSLBufferQueueAudioPlayer();

    public static native boolean createAudioRecorder();
    public static native void deleteAudioRecorder();
    public static native void startPlay();
    public static native void stopPlay();
    public static native void enableFilter(boolean enable);
}
