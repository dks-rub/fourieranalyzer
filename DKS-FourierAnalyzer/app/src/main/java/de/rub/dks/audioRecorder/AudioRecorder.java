package de.rub.dks.audioRecorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;


/**
* Audio recorder class to get audio bytes from the devices microphone.
* A consumer will be notified whenever new audio bytes are available.
* 
* REQUIRES permission android.permission.RECORD_AUDIO
*/
public class AudioRecorder {
	private AudioRecordListener consumer;
	private Thread thread;
	private int bufferSize, sampleRate;

	/**
	* Constructor.
	* Creates a new AudioRecorder which will notify the given consumer and use the provided sample rate.
	* The minimal buffer size for this sample rate will be automatically calculated.
	* @param consumer a consumer which will be notified when new audio bytes are available, must not be null!
	* @param sampleRate the players audio sample rate
	*/
	public AudioRecorder(AudioRecordListener consumer, int sampleRate) {
		this.consumer = consumer;
		this.sampleRate = sampleRate;
		bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
	}

	/**
	* (Re-)starts recording from the microphone.
	* Recording lasts until stop() is called.
	*/
	public void start() {
		if (thread != null)
			stop();
		thread = new Thread() {
			public void run() {
				// prevent audio lagg
				android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
				AudioRecord recorder = null;
				try {
					// setup audio recorder
					recorder = new AudioRecord(AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
					recorder.startRecording();
					while (!isInterrupted()) {
						// read audio bytes and pass them to the listener
						byte[] buffer = new byte[bufferSize];
						recorder.read(buffer, 0, buffer.length);
						consumer.handleAudio(buffer);
					}
					recorder.stop();
					recorder.release();
				} catch (Exception x) {
				}
			}
		};
		thread.start();
	}
	
	/**
	* Stops recording.
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
