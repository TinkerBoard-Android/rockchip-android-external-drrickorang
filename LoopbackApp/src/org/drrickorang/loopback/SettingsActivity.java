/*
 * Copyright (C) 2015 The Android Open Source Project
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

package org.drrickorang.loopback;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnValueChangeListener;

public class SettingsActivity extends Activity implements OnItemSelectedListener,
OnValueChangeListener {
    /**
     * Called with the activity is first created.
     */
    Spinner mSpinnerSamplingRate;
    Spinner mSpinnerAudioThreadType;
    NumberPicker mNumberPickerPlaybackBuffer;
    NumberPicker mNumberPickerRecordBuffer;
    int bytesPerFrame;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the layout for this activity. You can find it
        View view = getLayoutInflater().inflate(R.layout.settings_activity, null);
        setContentView(view);

        bytesPerFrame = getApp().BYTES_PER_FRAME;
        int samplingRate = getApp().getSamplingRate();
        //init spinner, etc
        mSpinnerSamplingRate = (Spinner) findViewById(R.id.spinnerSamplingRate);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.samplingRate_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mSpinnerSamplingRate.setAdapter(adapter);
        mSpinnerSamplingRate.setOnItemSelectedListener(this);
        //set current value
        String currentValue = String.valueOf(samplingRate);
        int nPosition = adapter.getPosition(currentValue);
        mSpinnerSamplingRate.setSelection(nPosition);

        //spinner native
        int audioThreadType = getApp().getAudioThreadType();
        mSpinnerAudioThreadType = (Spinner) findViewById(R.id.spinnerAudioThreadType);
        ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(this,
                R.array.audioThreadType_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mSpinnerAudioThreadType.setAdapter(adapter2);
        mSpinnerAudioThreadType.setOnItemSelectedListener(this);
        //set current value
//        String currentValue = String.valueOf(samplingRate);
//        int nPosition = adapter.getPosition(currentValue);
        mSpinnerAudioThreadType.setSelection(audioThreadType);
        if (!getApp().isSafeToUseSles())
            mSpinnerAudioThreadType.setEnabled(false);

        //playback buffer
        mNumberPickerPlaybackBuffer = (NumberPicker) findViewById(R.id.numberpickerPlaybackBuffer);
        mNumberPickerPlaybackBuffer.setMaxValue(8000);
        mNumberPickerPlaybackBuffer.setMinValue(16);
        mNumberPickerPlaybackBuffer.setWrapSelectorWheel(false);
        mNumberPickerPlaybackBuffer.setOnValueChangedListener(this);
        int playbackBuffer = getApp().getPlayBufferSizeInBytes()/bytesPerFrame;
        mNumberPickerPlaybackBuffer.setValue(playbackBuffer);
        log("playbackbuffer = " + playbackBuffer);
        //record buffer
        mNumberPickerRecordBuffer = (NumberPicker) findViewById(R.id.numberpickerRecordBuffer);
        mNumberPickerRecordBuffer.setMaxValue(8000);
        mNumberPickerRecordBuffer.setMinValue(16);
        mNumberPickerRecordBuffer.setWrapSelectorWheel(false);
        mNumberPickerRecordBuffer.setOnValueChangedListener(this);
        int recordBuffer = getApp().getRecordBufferSizeInBytes()/bytesPerFrame;
        mNumberPickerRecordBuffer.setValue(recordBuffer);
        log("recordBuffer = " + recordBuffer);
        refresh();
    }

    public void onDestroy() {
        super.onDestroy();
    }
    @Override
    public void onBackPressed() {

        log("on back pressed");
        settingsChanged();
        finish();
    }
    private void refresh() {
        int playbackBuffer = getApp().getPlayBufferSizeInBytes()/bytesPerFrame;
        mNumberPickerPlaybackBuffer.setValue(playbackBuffer);
         int recordBuffer = getApp().getRecordBufferSizeInBytes()/bytesPerFrame;
        mNumberPickerRecordBuffer.setValue(recordBuffer);
        if (getApp().getAudioThreadType() == LoopbackApplication.AUDIO_THREAD_TYPE_JAVA)
            mNumberPickerRecordBuffer.setEnabled(true);
        else
            mNumberPickerRecordBuffer.setEnabled(false);
    }
     public void onItemSelected(AdapterView<?> parent, View view,
            int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        log("item selected!");
        switch(parent.getId()) {
            case R.id.spinnerSamplingRate:
                String stringValue = mSpinnerSamplingRate.getSelectedItem().toString();
                int samplingRate = Integer.parseInt(stringValue);
                getApp().setSamplingRate(samplingRate);
                settingsChanged();
                log("Sampling Rate: "+ stringValue);
                break;
            case R.id.spinnerAudioThreadType:
                int audioThreadType = mSpinnerAudioThreadType.getSelectedItemPosition();
                getApp().setAudioThreadType(audioThreadType);
                settingsChanged();
                log("AudioThreadType:" + audioThreadType);
                refresh();
                break;
        }
    }

    public void onValueChange (NumberPicker picker, int oldVal, int newVal) {
        if (picker == mNumberPickerPlaybackBuffer) {
            log("playback new size " + oldVal + " -> " + newVal);
            getApp().setPlayBufferSizeInBytes(newVal*bytesPerFrame);
        } else if (picker == mNumberPickerRecordBuffer) {
            log("record new size " + oldVal + " -> " + newVal);
            getApp().setRecordBufferSizeInBytes(newVal*bytesPerFrame);
        }
    }

    private void settingsChanged() {
        Intent intent = new Intent();
        setResult(RESULT_OK, intent);
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    /** Called when the user clicks the button */
    public void onButtonPlaybackDefault(View view) {
        int samplingRate = getApp().getSamplingRate();
        int minPlayBufferSizeInBytes = AudioTrack.getMinBufferSize(samplingRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        getApp().setPlayBufferSizeInBytes(minPlayBufferSizeInBytes);
        refresh();
    }

    public void onButtonRecordDefault(View view) {
        int samplingRate = getApp().getSamplingRate();

        int minRecBufferSizeInBytes =  AudioRecord.getMinBufferSize(samplingRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        getApp().setRecordBufferSizeInBytes(minRecBufferSizeInBytes);

        refresh();
    }

    private LoopbackApplication getApp() {
        return (LoopbackApplication) this.getApplication();
    }

    private static void log(String msg) {
        Log.v("Settings", msg);
    }

}
