package de.rub.dks.fourieranalyzer;

import java.util.ArrayList;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jjoe64.graphview.BarGraphView;
import com.jjoe64.graphview.CustomLabelFormatter;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;
import com.jjoe64.graphview.GraphViewStyle;
import com.jjoe64.graphview.GraphViewStyle.GridStyle;
import com.jjoe64.graphview.LineGraphView;

import de.rub.dks.audioPlayer.AudioPlaybackListener;
import de.rub.dks.audioPlayer.AudioSamplePlayer;
import de.rub.dks.audioRecorder.AudioRecordListener;
import de.rub.dks.audioRecorder.AudioRecorder;
import de.rub.dks.fft.FFT;
import de.rub.dks.fft.FFTConsumer;
import de.rub.dks.helper.UpdateHelper;

/**
* The main activity, handling all the GUI, recording and playback.
* @author Tim Guenther, Max Hoffmann
* @version 0.0.1
* @since 01.07.2014
*
*/
public class FourierAnalyzerActivity extends Activity implements AudioRecordListener, FFTConsumer, AudioPlaybackListener{
	public static final int SAMPLE_RATE = 8000;
	private LineGraphView graphView;
	private BarGraphView barView;
	private GraphViewSeries graphSeries, barSeries;
	private FFT fft;
	private AudioRecorder recorder;
	private AudioSamplePlayer player;
	private ArrayList<byte[]> currentAudio;
	private ArrayList<float[]> currentFFT;
	private float[] barFFT;
	private boolean recordMode, playbackMode;
	private long audioStart, audioLength;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_fourier_analyzer);
		
		//Check Updates
		Handler mHandler = new Handler(Looper.getMainLooper());
		UpdateHelper uh = new UpdateHelper(this, getString(R.string.app_name), mHandler);
		uh.startThread();
		
		currentAudio = new ArrayList<>();
		currentFFT = new ArrayList<>();
		// init graphs
		graphView = new LineGraphView(getApplicationContext(), getString(R.string.graph_lable));
		graphSeries = new GraphViewSeries("FFT", new GraphViewSeriesStyle(Color.BLACK, 2), new GraphViewData[0]);
		graphView.addSeries(graphSeries);
		graphView.setGraphViewStyle(new GraphViewStyle(Color.BLACK, Color.BLACK, Color.BLACK));
		graphView.setManualYAxisBounds(1, 0);
		graphView.setViewPort(0, 50);
		graphView.setScrollable(true);
		graphView.setDrawBackground(true);
		graphView.getGraphViewStyle().setGridStyle(GridStyle.HORIZONTAL);
		graphView.setVerticalLabels(new String[] { "1", "0.5", "0" });
		graphView.setCustomLabelFormatter(new CustomLabelFormatter() {
			@Override
			public String formatLabel(double value, boolean isValueX) {
				if (isValueX) {
					int x = (int) (value / 15) * 15;
					return ((int) (1.0 * x / currentFFT.size() * audioLength / 10.0) / 100.0) + "s";
				}
				return null; // let graphview generate Y-axis label for us
			}
		});

		barView = new BarGraphView(getApplicationContext(), getString(R.string.bar_lable));
		barSeries = new GraphViewSeries("FFT", new GraphViewSeriesStyle(Color.BLACK, 3), new GraphViewData[0]);
		barView.setGraphViewStyle(new GraphViewStyle(Color.BLACK, Color.BLACK, Color.BLACK));
		barView.addSeries(barSeries);
		barView.setManualYAxisBounds(1, 0);
		barView.setHorizontalLabels(new String[] { "" });
		barView.setVerticalLabels(new String[] { "1", "0.5", "0" });

		((LinearLayout) findViewById(R.id.lineGraph)).addView(graphView);
		((LinearLayout) findViewById(R.id.barGraph)).addView(barView);

		// init recorder, player and fft analyzer
		recorder = new AudioRecorder(this, SAMPLE_RATE);
		player = new AudioSamplePlayer(this, SAMPLE_RATE);
		// fft with 30 bars per frame -> frequency spectrum seperated in 30 bands
		fft = new FFT(this, 30, SAMPLE_RATE, recorder.getBufferSize());

		// program button behavior
		Button bt = (Button) findViewById(R.id.startStopBt);
		bt.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				recordMode = !recordMode;
				if (recordMode) {
					currentAudio = new ArrayList<>();
					currentFFT = new ArrayList<>();
					recorder.start();
					audioStart = System.currentTimeMillis();
				} else {
					audioLength = System.currentTimeMillis() - audioStart;
					recorder.stop();
				}
				refreshButtons();
			}
		});
		bt = (Button) findViewById(R.id.playPauseBt);
		bt.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				playbackMode = !playbackMode;
				if (playbackMode)
					player.play(currentAudio);
				else
					player.stop();
				refreshButtons();
			}
		});
	}

	// handle button labels and enabled state
	protected void refreshButtons() { 
		Button bt = (Button) findViewById(R.id.startStopBt);
		bt.setEnabled(!playbackMode);
		bt.setText(!recordMode ? "Start Recording" : "Stop Recording");
		bt = (Button) findViewById(R.id.playPauseBt);
		bt.setEnabled(!recordMode && currentAudio.size() > 0);
		bt.setText(!playbackMode ? "Play" : "Stop");
	}

	// handle label texts
	protected void refreshLabels() { 
		TextView txt = (TextView) findViewById(R.id.statusLb);
		double dur = audioLength / 1000.0;
		txt.setText("Duration: " + dur + "s");
	}

	// listener for the recorder
	public void handleAudio(final byte[] audio) { 
		currentAudio.add(audio);
		audioLength = System.currentTimeMillis() - audioStart;
		new Thread() {
			public void run() {
				fft.generateFFT(audio);
			}
		}.start();
	}

	// listener for the fft
	public void handleFFT(float[] data) { 
		barFFT = data;
		currentFFT.add(data);
		runOnUiThread(new Runnable() {
			public void run() {
				showLineFFT();
				showBarFFT();
				refreshLabels();
			}
		});
	}

	// creates the upper graph
	public void showLineFFT() { 
		int m = currentFFT.size();
		GraphViewData[] dat = new GraphViewData[m];
		for (int i = 0; i < m; i++) {
			// arithmetic average of the fft samples of a buffer
			float d = 0;
			for (float x : currentFFT.get(i))
				d += x;
			d /= currentFFT.get(i).length;
			dat[i] = new GraphViewData(i, d);
		}
		graphSeries.resetData(dat);
		graphView.scrollToEnd();
	}

	// creates the lower graph from a single fft sample barFFT
	public void showBarFFT() { 
		if (barFFT == null)
			return;
		GraphViewData[] dat = new GraphViewData[barFFT.length];
		for (int i = 0; i < barFFT.length; i++)
			dat[i] = new GraphViewData(i, barFFT[i]);
		barSeries.resetData(dat);
	}

	// listener for audio playback finished
	public void playbackFinished() { 
		playbackMode = false;
		if (currentFFT.size() > 0)
			barFFT = currentFFT.get(0);
		runOnUiThread(new Runnable() {
			public void run() {
				showBarFFT();
				refreshButtons();
			}
		});
	}

	// listener for audio playback progress
	public void playbackUpdate(final double percentage) { 
		runOnUiThread(new Runnable() {
			public void run() {
				barFFT = currentFFT.get(Math.min(currentFFT.size() - 1, (int) (currentFFT.size() * percentage + 7)));
				graphView.setViewPort(currentFFT.size() * percentage + 7, 50);
				graphView.redrawAll();
				showBarFFT();
			}
		});
	}

	// Disable data loss on screen orientation change
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
	
	//stop on pause
	public void onPause() { 
		super.onPause();
		recorder.stop();
		player.stop();
	}

	 //reset on resume (keep record data)
	public void onResume() {
		super.onResume();
		recordMode = playbackMode = false;
		refreshButtons();
		refreshLabels();
	}

	public void onStop() {
		super.onStop();
		recorder.stop();
		player.stop();
	}

	public void onStart() {
		super.onStart();
	}
}
