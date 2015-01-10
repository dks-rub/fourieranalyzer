package de.rub.dks.audioRecorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

public class AudioRecorder {
	private AudioRecordListener consumer;
	private Thread thread;
	private int bufferSize, sampleRate;

	public AudioRecorder(AudioRecordListener consumer, int sampleRate) {
		this.consumer = consumer;
		this.sampleRate = sampleRate;
		bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
	}

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
