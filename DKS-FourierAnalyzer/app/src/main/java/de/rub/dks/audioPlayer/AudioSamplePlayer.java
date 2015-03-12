package de.rub.dks.audioPlayer;

import java.util.ArrayList;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;

public class AudioSamplePlayer {
	private Thread thread;
	private int bufferSize, sampleRate;
	private AudioPlaybackListener listener;

	public AudioSamplePlayer(AudioPlaybackListener listener, int sampleRate) {
		this.sampleRate = sampleRate;
		this.listener = listener;
		bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
	}

	public void play(final ArrayList<byte[]> audioData) {
		if (thread != null)
			stop();
		thread = new Thread() {
			public void run() {
				AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
				audioTrack.play();
				double i = 0;
				// play audio stream until all data is processed
				for (byte[] b : audioData) {
					listener.playbackUpdate(i / audioData.size());
					audioTrack.write(b, 0, b.length);
					i++;
					if (interrupted())
						break;
				}
				listener.playbackFinished();
				audioTrack.stop();
				audioTrack.release();
			}
		};
		thread.start();
	}

	public int getBufferSize() {
		return bufferSize;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public void stop() {
		if (thread != null)
			thread.interrupt();
		thread = null;
	}

}
