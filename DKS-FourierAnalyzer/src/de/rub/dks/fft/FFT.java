package de.rub.dks.fft;

/*
 * How this works??
 * Don't ask!
 * 
 * It's magic!!!
 */

public class FFT {
	private float[] left, right, old_FFT;
	private int sampleSize, desiredBands, desiredFPS, framesRead, position, sampleRate, offset, frameSize, sampleCount;
	private byte[] audioDataBuffer = null;
	private KJFFT fft;
	private float passCount = 0;
	private FFTConsumer consumer;

	public FFT(FFTConsumer consumer, int desiredBands, int sampleRate, int bufferSize) {
		this.consumer = consumer;
		this.desiredBands = desiredBands;
		this.desiredFPS = (int) (1.0 * sampleRate / bufferSize);
		this.sampleRate = sampleRate;
		this.sampleSize = bufferSize;
		fft = new KJFFT(bufferSize);
		old_FFT = new float[bufferSize];
		frameSize = 2;
		audioDataBuffer = new byte[(int) sampleRate << 1];
	}

	public void generateFFT(byte[] data) {
		framesRead += (data.length / frameSize);
		storeAudioData(data, 0, data.length);
		handleAudioBytes();
		passCount += 1000 / (sampleRate / 1000);
		if (passCount >= (1000 / desiredFPS) * sampleCount) {
			computeFFT(mergeStereo(left, right));
			sampleCount++;
		}
	}

	private void storeAudioData(byte[] pAudioData, int pOffset, int pLength) {

		int wOverrun = 0;

		if (position + pLength > audioDataBuffer.length - 1) {
			wOverrun = (position + pLength) - audioDataBuffer.length;
			pLength = audioDataBuffer.length - position;
		}

		System.arraycopy(pAudioData, pOffset, audioDataBuffer, position, pLength);
		if (wOverrun > 0) {

			System.arraycopy(pAudioData, pOffset + pLength, audioDataBuffer, 0, wOverrun);
			position = wOverrun;

		} else {
			position += pLength;
		}

	}

	private void handleAudioBytes() {
		left = new float[sampleSize];
		right = new float[sampleSize];

		int c = (int) ((long) (framesRead * frameSize) - (long) (audioDataBuffer.length * offset));

		if (c > 0) {
			for (int a = 0; a < sampleSize; a++, c += 4) {
				if (c >= audioDataBuffer.length) {
					c = (c - audioDataBuffer.length);
					offset++;
				}
				// channelMode == CHANNEL_MODE_STEREO && sampleType ==
				// SAMPLE_TYPE_SIXTEEN_BIT
				left[a] = ((audioDataBuffer[c + 1] << 8) + audioDataBuffer[c]) / 32767.0f;
				right[a] = ((audioDataBuffer[c + 3] << 8) + audioDataBuffer[c + 2]) / 32767.0f;
			}
		}
	}

	private float[] mergeStereo(float[] pLeft, float[] pRight) {

		for (int a = 0; a < pLeft.length; a++) {
			pLeft[a] = (pLeft[a] + pRight[a]) / 2.0f;
		}
		return pLeft;

	}

	private void computeFFT(float[] pSample) {
		int saMultiplier = (sampleSize / 2) / desiredBands;

		float[] wFFT = fft.calculate(pSample);

		float wSadfrr = (0.03f);

		float result[] = new float[desiredBands];

		for (int a = 0, bd = 0; bd < desiredBands; a += saMultiplier, bd++) {
			float wFs = 0;

			// -- Average out nearest bands.
			for (int b = 0; b < saMultiplier; b++) {
				wFs += wFFT[a + b];
			}

			// -- Log filter.
			wFs = (wFs * (float) Math.log(bd + 2));

			if (wFs > 1.0f) {
				wFs = 1.0f;
			}

			// -- Compute SA decay...
			if (wFs >= (old_FFT[a] /* - wSadfrr */)) {

				old_FFT[a] = wFs;

			} else {

				old_FFT[a] -= wSadfrr;

				if (old_FFT[a] < 0) {
					old_FFT[a] = 0;
				}

				wFs = old_FFT[a];

			}

			result[bd] = wFs;
		}
		consumer.handleFFT(result);
	}

}
