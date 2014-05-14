package com.wonkytonki.app;

import android.app.Activity;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.util.InputStreamSender;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;


public class MainActivity extends Activity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;



    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;
     static private BlockingQueue<byte[]> que = new ArrayBlockingQueue<byte[]>(1000);
     static int value = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
                .commit();
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = getString(R.string.title_section1);
                break;
            case 2:
                mTitle = getString(R.string.title_section2);
                break;
            case 3:
                mTitle = getString(R.string.title_section3);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final int RECORDER_SAMPLERATE = 8000;
        private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
        private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        private static final int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
        private static final int BytesPerElement = 2; // 2 bytes in 16bit format

        private AudioRecord recorder = null;
        private Thread recordingThread = null;
        private boolean isRecording = false;
        private static final String ARG_SECTION_NUMBER = "section_number";
        private Button button;
        private TextView tv;
        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public void onResume(){
            super.onResume();
            setup();
        }
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            button = (Button) rootView.findViewById(R.id.talk);
            tv = (TextView)rootView.findViewById(R.id.data);
            return rootView;
        }
        private void setup(){
            int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                    RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);


            button.setEnabled(false);

            final AudioTrack audioPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, BufferElements2Rec, AudioTrack.MODE_STREAM);

            if(audioPlayer.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
                audioPlayer.play();
            
            AsyncTask task = new AsyncTask() {
                @Override
                protected Object doInBackground(Object[] objects) {
                    final Client client = new Client(1024*1024, 1024*1024);
                    Kryo k = client.getKryo();
                    k.setRegistrationRequired(false);
                    client.start();


                    client.addListener(new Listener() {
                        @Override
                        public void connected(Connection connection) {
                            super.connected(connection);
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    button.setText("Connected!\nPush to talk!");
                                    button.setEnabled(true);
                                }
                            });
                        }

                        @Override
                        public void disconnected(Connection connection) {
                            super.disconnected(connection);
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    button.setText("Disconnected! :(");
                                }
                            });
                        }

                        public void received (Connection connection, Object object) {
                            if (object instanceof byte[]) {
                                final byte[] response = (byte[])object;
                                if(getActivity() != null) {
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {

                                            short[] sData = new short[response.length / 2];

                                            ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(sData);
                                            int writtenBytes = 0;
                                            if (AudioRecord.ERROR_INVALID_OPERATION != sData.length) {
                                                writtenBytes = audioPlayer.write(sData, 0, sData.length);
                                            } else {
                                                Log.v("ERR", "Error");
                                            }
                                        }
                                    });
                                }

                            }
                        }
                    });
                    try {
                        client.connect(5000, "78.73.132.182", 54555, 54777);
                        //client.connect(5000, "192.168.1.5", 54555, 54777);
                    }catch (IOException e){

                        Log.d("WT", "exception");
                    }

                    byte[] s;
                    try {

                        while((s = que.take()) != null){
                            //   int pos = 0;
                            //   while(pos < s.length()- s.length()/8)
                            //  {

                            client.sendTCP(s);
                            // pos += s.length()/8+1;
                            //  }

                        }
                    } catch (InterruptedException e) {

                    }
                    return null;
                }

            };
            task.execute();

            button.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_DOWN:
                            button.setPressed(true);
                            // Start action ...
                            isRecording = true;
                            startRecording();
                            button.setText("Push to stop talking");
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_POINTER_UP:
                        case MotionEvent.ACTION_OUTSIDE:
                            button.setPressed(false);
                            isRecording = false;
                            stopRecording();
                            button.setText("Push to talk");
                            break;
                        case MotionEvent.ACTION_POINTER_DOWN:
                            break;
                        case MotionEvent.ACTION_MOVE:
                            break;
                        default:
                            v.setPressed(false);
                            isRecording = false;
                            stopRecording();
                            button.setText("Push to talk");
                            break;
                    }

                    return true;
                }
            });
        }
        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MainActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
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

      //

        //    if(audioPlayer.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
          //      audioPlayer.play();
            while (isRecording) {
                // gets the voice output from microphone to byte format
                int readBytes = recorder.read(sData, 0, BufferElements2Rec);
           /* System.out.println("Short wirting to file" + sData.toString());
            try {
                // writes the data to file from buffer stores the voice buffer


                os.write(bData, 0, BufferElements2Rec * BytesPerElement);

            } catch (IOException e) {
                e.printStackTrace();
            }*/
                int writtenBytes = 0;
                if(AudioRecord.ERROR_INVALID_OPERATION != readBytes){
                    byte bData[] = short2byte(sData);
                    try {
                        que.put(bData);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //writtenBytes += audioPlayer.write(sData, 0, readBytes);
                }
            }
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

}
