/*
 * Copyright (C) 2014 The Android Open Source Project
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

//import android.content.Context;
//import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
//import android.media.MediaPlayer;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import android.os.Handler;
import  android.os.Message;

/**
 * A thread/audio track based audio synth.
 */
public class LoopbackAudioThread extends Thread {

    public boolean isRunning = false;
    double twoPi = 6.28318530718;

    public AudioTrack mAudioTrack;
    public int mSessionId;

    public double[] mvSamples; //captured samples
    int mSamplesIndex;

    private RecorderRunnable recorderRunnable;
    Thread mRecorderThread;
    public int mSamplingRate = 48000;
    private int mChannelConfigIn = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;

    Pipe mPipe = new Pipe(65536);

    int mMinPlayBufferSizeInBytes = 0;
    int mMinRecordBuffSizeInBytes = 0;
    private int mChannelConfigOut = AudioFormat.CHANNEL_OUT_MONO;
    private byte[] mAudioByteArrayOut;

    boolean isPlaying = false;
    private Handler mMessageHandler;

    static final int FUN_PLUG_AUDIO_THREAD_MESSAGE_REC_STARTED = 992;
    static final int FUN_PLUG_AUDIO_THREAD_MESSAGE_REC_COMPLETE = 993;

    public void setParams(int samplingRate, int playBufferInBytes, int recBufferInBytes) {
        mSamplingRate = samplingRate;

        mMinPlayBufferSizeInBytes = playBufferInBytes;
        mMinRecordBuffSizeInBytes = recBufferInBytes;

    }

    public void run() {
        setPriority(Thread.MAX_PRIORITY);

        if ( mMinPlayBufferSizeInBytes <= 0 ) {
            mMinPlayBufferSizeInBytes = AudioTrack.getMinBufferSize(mSamplingRate,mChannelConfigOut,
                    mAudioFormat);

            log("Playback: computed min buff size = " + mMinPlayBufferSizeInBytes
                    + " bytes");
        } else {
            log("Plaback: using min buff size = " + mMinPlayBufferSizeInBytes
                    + " bytes");
        }

        mAudioByteArrayOut = new byte[mMinPlayBufferSizeInBytes *4];

        recorderRunnable = new RecorderRunnable(mPipe, mSamplingRate, mChannelConfigIn,
                mAudioFormat, mMinRecordBuffSizeInBytes);
        mRecorderThread = new Thread(recorderRunnable);
        mRecorderThread.start();

        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                mSamplingRate,
                mChannelConfigOut,
                mAudioFormat,
                mMinPlayBufferSizeInBytes,
                AudioTrack.MODE_STREAM /* FIXME runtime test for API level 9 ,
                mSessionId */);

        short samples[] = new short[mMinPlayBufferSizeInBytes];

        int amp = 10000;
        double fr = 440.0f;
        double phase = 0.0;

        isPlaying = false;
        isRunning = true;

        while (isRunning) {
            if (isPlaying)
            {
                //using PIPE
                int bytesAvailable = mPipe.availableToRead();

                if (bytesAvailable>0 ) {

                    int bytesOfInterest = bytesAvailable;
                    if ( mMinPlayBufferSizeInBytes < bytesOfInterest )
                        bytesOfInterest = mMinPlayBufferSizeInBytes;

                    mPipe.read( mAudioByteArrayOut, 0 , bytesOfInterest);
                    int bytesAvailableAfter = mPipe.availableToRead();

                    //output
                    mAudioTrack.write(mAudioByteArrayOut, 0, bytesOfInterest);

                    if ( !recorderRunnable.isStillRoomToRecord()) {
                        //stop
                        endTest();

                    }
                }

            }
            else
            {
                if (isRunning)
                {
                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        } //end is running

    }

    public void setMessageHandler(Handler messageHandler) {
        mMessageHandler = messageHandler;
    }

    public void togglePlay() {

    }

    public void runTest() {

        // start test
        if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING )
        {
            log("...run test, but still playing...");
            endTest();
        }
        else
        {
            //erase output buffer
            if (mvSamples != null)
                mvSamples = null;

            //resize
            int nNewSize = mSamplingRate * 2; //5 seconds!
            mvSamples = new double[nNewSize];
            mSamplesIndex = 0; //reset index

            //start playing
            isPlaying = true;
            mAudioTrack.play();
            recorderRunnable.startRecording(mvSamples);

            log(" Started capture test");
            if (mMessageHandler != null) {
                Message msg = Message.obtain();
                msg.what = FUN_PLUG_AUDIO_THREAD_MESSAGE_REC_STARTED;
                mMessageHandler.sendMessage(msg);
            }
        }
   }

   public void endTest() {
       log("--Ending capture test--");
       isPlaying = false;
       mAudioTrack.pause();
       recorderRunnable.stopRecording();
       mPipe.flush();
       mAudioTrack.flush();

       if (mMessageHandler != null) {
           Message msg = Message.obtain();
           msg.what = FUN_PLUG_AUDIO_THREAD_MESSAGE_REC_COMPLETE;
           mMessageHandler.sendMessage(msg);
       }

   }

    public void finish() {

        if (isRunning) {
            isRunning = false;
            try {
                sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        final AudioTrack at = mAudioTrack;
        if (at != null)
        {
            at.release();
            mAudioTrack = null;
        }

        Thread zeThread = mRecorderThread;
        mRecorderThread = null;
        if (zeThread != null) {
            zeThread.interrupt();
            while (zeThread.isAlive()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private static void log(String msg) {
        Log.v("Loopback", msg);
    }

    double [] getWaveData () {
        return recorderRunnable.mvSamples;
    }

    ///////////////////////
    //////////////////////

    static class RecorderRunnable implements Runnable
    {
        //all recorder things here
        private final Pipe mPipe;
        private boolean mIsRecording = false;
        private static final Object sRecordingLock = new Object();

        private AudioRecord mRecorder;

        private int mSelectedRecordSource = MediaRecorder.AudioSource.MIC;
        public int mSamplingRate = 48000;
        private int mChannelConfig = AudioFormat.CHANNEL_IN_MONO;
        public int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int mMinRecordBuffSizeInBytes = 0;
        private byte[] mAudioByteArray;
        private short[] mAudioTone;
        private int mAudioToneIndex;

        double twoPi = 6.28318530718;

        public double[] mvSamples; //captured samples
        int mSamplesIndex;

        RecorderRunnable(Pipe pipe, int samplingRate, int channelConfig, int audioFormat,
                int recBufferInBytes)
        {
            mPipe = pipe;
            mSamplingRate = samplingRate;
            mChannelConfig = channelConfig;
            mAudioFormat = audioFormat;
            mMinRecordBuffSizeInBytes = recBufferInBytes;
        }

        //init the recording device
        boolean initRecord() {
            log("Init Record");

            if (mMinRecordBuffSizeInBytes <=0 ) {

                mMinRecordBuffSizeInBytes = AudioRecord.getMinBufferSize(mSamplingRate,
                        mChannelConfig, mAudioFormat);
                log("RecorderRunnable: computing min buff size = " + mMinRecordBuffSizeInBytes
                        + " bytes");
            }
            else {
                log("RecorderRunnable: using min buff size = " + mMinRecordBuffSizeInBytes
                        + " bytes");
            }
            if (mMinRecordBuffSizeInBytes <= 0) {
                return false;
            }

            mAudioByteArray = new byte[mMinRecordBuffSizeInBytes / 2];

            try {
                mRecorder = new AudioRecord(mSelectedRecordSource, mSamplingRate,
                        mChannelConfig, mAudioFormat, 2 * mMinRecordBuffSizeInBytes);
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (mRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                mRecorder.release();
                mRecorder = null;
                return false;
            }

            createAudioTone(300, 1000,true);
            mAudioToneIndex = 0;

            return true;
        }

        void startRecording(double vCapture[]) {
            synchronized (sRecordingLock) {
                mIsRecording = true;
            }

            mvSamples = vCapture;
            mSamplesIndex = 0;

            boolean successful = initRecord();
            if (successful) {
                log("Ready to go.");
                startRecordingForReal();
            } else {
                log("Recorder initialization error.");
                synchronized (sRecordingLock) {
                    mIsRecording = false;
                }
            }
        }

        void startRecordingForReal() {
            mAudioToneIndex = 0;
            mPipe.flush();
            mRecorder.startRecording();
        }

        void stopRecording() {
            log("stop recording A");
            synchronized (sRecordingLock) {
                log("stop recording B");
                mIsRecording = false;
                stopRecordingForReal();
            }
        }

        void stopRecordingForReal() {
            log("stop recording for real");
            if (mRecorder != null) {
                mRecorder.stop();
            }

            if (mRecorder != null) {
                mRecorder.release();
                mRecorder = null;
            }

        }
        public void run() {

            double phase = 0;

            while (!Thread.interrupted()) {
                synchronized (sRecordingLock) {
                    if (mIsRecording && mRecorder != null) {
                        int nbBytesRead = mRecorder.read(mAudioByteArray, 0,
                                mMinRecordBuffSizeInBytes / 2);
                        if (nbBytesRead > 0) {
                            { //injecting the tone
                                int currentIndex = mSamplesIndex - 100; //offset
                                for (int i = 0; i < nbBytesRead/2; i++) {
                                    //   log(" <"+currentIndex +">");
                                    if (currentIndex >=0 && currentIndex <mAudioTone.length) {
                                        short value = (short) mAudioTone[currentIndex];
                                        // log("Injecting: ["+currentIndex+"]="+value);
                                        //replace capture
                                        mAudioByteArray[i*2+1] =(byte)( 0xFF &(value >>8));
                                        mAudioByteArray[i*2] = (byte) ( 0xFF &(value));

                                    }
                                    currentIndex++;
                                } //for injecting tone
                            }
                            mPipe.write(mAudioByteArray, 0, nbBytesRead);
                            if (isStillRoomToRecord()) { //record to vector
                                double maxval = Math.pow(2, 15);
                                for (int i = 0; i < nbBytesRead/2; i++) {
                                    double value = 0;
                                    byte ba = mAudioByteArray[i*2+1];
                                    byte bb = mAudioByteArray[i*2];
                                    value = (ba << 8) +(bb);
                                    value = value/maxval;
                                    if ( mSamplesIndex < mvSamples.length) {
                                        mvSamples[mSamplesIndex++] = value;
                                    }

                                }
                            }
                        }
                    }
                }
            }//synchronized
            stopRecording();//close this
        }

       public boolean isStillRoomToRecord() {
           boolean result = false;
           if (mvSamples != null) {
               if (mSamplesIndex < mvSamples.length) {
                   result = true;
               }
           }

           return result;
       }

       private void createAudioTone(int durationSamples, int frequency, boolean taperEnds) {
           mAudioTone = new short[durationSamples];
           double phase = 0;

           for (int i = 0; i < durationSamples; i++) {
               double factor = 1.0;
               if (taperEnds) {
                   if (i<durationSamples/2) {
                       factor = 2.0*i/durationSamples;
                   } else {
                       factor = 2.0*(durationSamples-i)/durationSamples;
                   }
               }

               short value = (short) (factor* Math.sin(phase)*10000);

               mAudioTone[i] = value;

               phase += twoPi * frequency / mSamplingRate;
           }
           while (phase > twoPi)
               phase -= twoPi;
       }

        private static void log(String msg) {
            Log.v("Recorder", msg);
        }

    } //RecorderRunnable
};  //end thread.
