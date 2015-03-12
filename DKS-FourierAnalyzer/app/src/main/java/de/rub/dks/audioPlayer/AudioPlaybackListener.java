package de.rub.dks.audioPlayer;

public interface AudioPlaybackListener {
	public void playbackFinished();

	public void playbackUpdate(double percentage);
}
