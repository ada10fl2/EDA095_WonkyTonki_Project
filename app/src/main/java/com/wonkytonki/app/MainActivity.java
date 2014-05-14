package com.wonkytonki.app;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


public class MainActivity extends ActionBarActivity {

    private static final int RECORDER_SAMPLERATE = 8000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    private static final int BytesPerElement = 2; // 2 bytes in 16bit format
    public static final String LOG_TAG = "WT";

    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    private Button mButtonTalk;
    private TextView mTextViewBottom;

    private CharSequence mTitle;
    private Button mButtonForceReconnect;
    private TextView mTextViewTop;
    private Client mClient;

    private static BlockingQueue<byte[]> que = new ArrayBlockingQueue<byte[]>(1000);
    private static int value = 0;
    private AudioFrame mAudioFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mButtonTalk = (Button) findViewById(R.id.main_btn_talk);
        mTextViewBottom = (TextView) findViewById(R.id.main_txt_bottom);
        mTextViewTop = (TextView) findViewById(R.id.main_txt_top);
        mButtonForceReconnect = (Button) findViewById(R.id.main_btn_reconnect);

        mClient = new Client(1024*1024, 1024*1024);
        mClient.getKryo().setRegistrationRequired(false);

        setupActionBar();
        setTitle("Wonky Tonki");
    }

    public void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    @Override
    public void onResume(){
        super.onResume();
        setup();
    }

    private void setup(){
        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

        mButtonTalk.setEnabled(false);

        final AudioTrack audioPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, BufferElements2Rec, AudioTrack.MODE_STREAM);

        if(audioPlayer.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            audioPlayer.play();
        }

        final Handler handler = new Handler();

        mAudioFrame = new AudioFrame();
        AsyncTask task = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                mClient.start();
                mClient.addListener(new Listener() {
                    @Override
                    public void connected(Connection connection) {
                        super.connected(connection);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTextViewBottom.setText("Connected!\nPush to talk!");
                                mButtonTalk.setEnabled(true);
                                mButtonForceReconnect.setEnabled(false);
                            }
                        });
                    }

                    @Override
                    public void disconnected(Connection connection) {
                        super.disconnected(connection);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTextViewBottom.setText("Disconnected! :(");
                                mButtonForceReconnect.setEnabled(true);
                            }
                        });
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                connectClient();
                            }
                        }, 1000);
                    }

                    public void received (Connection connection, Object object) {
                        if(object instanceof AudioFrame){
                            mAudioFrame = (AudioFrame) object;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    short[] sData = new short[mAudioFrame.bytes.length / 2];
                                    ByteBuffer.wrap(mAudioFrame.bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(sData);
                                    int writtenBytes = 0;
                                    if (AudioRecord.ERROR_INVALID_OPERATION != sData.length) {
                                        writtenBytes = audioPlayer.write(sData, 0, sData.length);
                                        audioPlayer.flush();
                                    } else {
                                        Log.e(LOG_TAG, "Error: AudioRecord.ERROR_INVALID_OPERATION != sData.length");
                                    }
                                    setTitle(String.format("%d users connected", mAudioFrame.users));
                                }
                            });
                        }
                    }
                });

                connectClient();

                byte[] bytes;
                try {
                    while((bytes = que.take()) != null){
                        mClient.sendTCP(bytes);
                    }
                } catch (InterruptedException e) {}
                return null;
            }

        };
        task.execute();

        mButtonTalk.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        mButtonTalk.setPressed(true);
                        // Start action ...
                        isRecording = true;

                        startRecording();
                        mTextViewBottom.setText("Release to stop talking");
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                    case MotionEvent.ACTION_OUTSIDE:
                        mButtonTalk.setPressed(false);
                        isRecording = false;
                        stopRecording();
                        mTextViewBottom.setText("Push to talk");
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    default:
                        v.setPressed(false);
                        isRecording = false;
                        stopRecording();
                        mTextViewBottom.setText("Push to talk");
                        break;
                }
                return true;
            }
        });

        mButtonForceReconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectClient();
            }
        });
    }

    private boolean connectClient(){
        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextViewBottom.setText("Connecting...");
                    mButtonForceReconnect.setEnabled(false);
                }
            });
            mClient.connect(5000, "78.73.132.182", 54555, 54777);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextViewBottom.setText("Connected...");
                }
            });
            return true;
        }catch (IOException e){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextViewBottom.setText("Connection failed");
                    mButtonForceReconnect.setEnabled(true);
                }
            });
            Log.d(LOG_TAG, "Exception:mainserver", e);
            return false;
        }
    }

    private void startRecording() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);
        recorder.stop();
        recorder.startRecording();

        isRecording = true;

        recordingThread = new Thread(new Runnable() {

            public void run() {

                writeAudioDataToFile();

            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    private byte[] short2byte(short[] sData) {
        byte[] bytes2 = new byte[sData.length * 2];
        ByteBuffer.wrap(bytes2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(sData);
        return bytes2;
    }

    private void writeAudioDataToFile() {
        // Write the output audio in byte
        short sData[] = new short[BufferElements2Rec];

        // if(audioPlayer.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
        //      audioPlayer.play();
        while (isRecording) {
            // gets the voice output from microphone to byte format
            int readBytes = recorder.read(sData, 0, BufferElements2Rec);
            /* System.out.println("Short wirting to file" + sData.toString()); */
            int writtenBytes = 0;
            if(AudioRecord.ERROR_INVALID_OPERATION != readBytes){
                if(checkData(sData)) {
                    byte bData[] = short2byte(sData);
                    try {
                        que.put(bData);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                //writtenBytes += audioPlayer.write(sData, 0, readBytes);
            }
        }
    }
    private boolean checkData(short[] arr){
        //return true;
        int i = 0;
        for(short s : arr){
            i += s;
        }
        return i > 10;
    }
    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;


            recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;
        }
    }
}