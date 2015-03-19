package de.rub.dks.audioPlayer;

import java.util.ArrayList;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;

/*
* Audio player class to play back arrays of audio bytes.
* All byte arrays passed to the player will be played in order of insertion until all tasks are done, or a new byte array list is passed.
*/
public class AudioSamplePlayer {
	private Thread thread;
	private int bufferSize, sampleRate;
	private AudioPlaybackListener listener;

	/**
	* Constructor.
	* Creates a new AudioSamplePlayer which will notify the given listener and use the provided sample rate.
	* The minimal buffer size for this sample rate will be automatically calculated.
	* @param listener a listener which will be notified, must not be null!
	* @param sampleRate the players audio sample rate
	*/
	public AudioSamplePlayer(AudioPlaybackListener listener, int sampleRate) {
		this.sampleRate = sampleRate;
		this.listener = listener;
		bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
	}

	/**
	* Plays the given byte arrays. If there is already audio playing it will be stopped.
	* @param audioData the byte arrays to be played
	*/
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
	
	/**
	* Stops the current playback (if available).
	*/
	public void stop() {
		if (thread != null)
			thread.interrupt();
		thread = null;
	}
	
	// getters

	public int getBufferSize() {
		return bufferSize;
	}

	public int getSampleRate() {
		return sampleRate;
	}

}
