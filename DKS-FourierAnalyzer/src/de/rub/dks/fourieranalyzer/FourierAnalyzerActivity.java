package de.rub.dks.fourieranalyzer;

import java.util.ArrayList;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
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
		currentAudio = new ArrayList<>();
		currentFFT = new ArrayList<>();
		// init graphviews
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

		// init recorder,player and fft analyzer
		recorder = new AudioRecorder(this, SAMPLE_RATE);
		player = new AudioSamplePlayer(this, SAMPLE_RATE);
		// fft with 30 bars per frame -> frequency spectrum seperated in 30
		// bands
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

	protected void refreshButtons() { // handle button labels and enabled state
		Button bt = (Button) findViewById(R.id.startStopBt);
		bt.setEnabled(!playbackMode);
		bt.setText(!recordMode ? "Start Recording" : "Stop Recording");
		bt = (Button) findViewById(R.id.playPauseBt);
		bt.setEnabled(!recordMode && currentAudio.size() > 0);
		bt.setText(!playbackMode ? "Play" : "Stop");
	}

	protected void refreshLabels() { // handle label texts
		TextView txt = (TextView) findViewById(R.id.statusLb);
		double dur = audioLength / 1000.0;
		txt.setText("Duration: " + dur + "s");
	}

	public void handleAudio(final byte[] audio) { // listener for the recorder
		currentAudio.add(audio);
		audioLength = System.currentTimeMillis() - audioStart;
		new Thread() {
			public void run() {
				fft.generateFFT(audio);
			}
		}.start();
	}

	public void handleFFT(float[] data) { // listener for the fft
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

	public void showLineFFT() { // creates the upper graph
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

	public void showBarFFT() { // creates the lower graph from a single fft sample barFFT
		if (barFFT == null)
			return;
		GraphViewData[] dat = new GraphViewData[barFFT.length];
		for (int i = 0; i < barFFT.length; i++)
			dat[i] = new GraphViewData(i, barFFT[i]);
		barSeries.resetData(dat);
	}

	public void playbackFinished() { // listener for audio playback finished
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

	public void playbackUpdate(final double percentage) { // listener for audio playback progress
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


//	@Override
//	public boolean onCreateOptionsMenu(Menu menu) {
//		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.main, menu);
//		return true;
//	}
//
//	public boolean onOptionsItemSelected(MenuItem item) {
//
//		if (item.getItemId() == R.id.action_about_us) {
//			// Open new Activity for AboutUs
//			Intent intent = new Intent(this, AboutUsActivity.class);
//			startActivity(intent);
//			return true;
//		}
//
//		return super.onOptionsItemSelected(item);
//	}
	
	public void onPause() { //stop on pause
		super.onPause();
		recorder.stop();
		player.stop();
	}

	public void onResume() { //reset on resume (keep record data)
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
