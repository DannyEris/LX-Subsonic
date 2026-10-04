package com.retro.subsonic;

import android.media.audiofx.Visualizer;
import android.os.Build;
import java.util.Arrays;

public class AudioVisualizerHelper {
    private Visualizer visualizer;
    private final float[] smoothedFft = new float[32];
    private boolean isCapturing = false;

    public interface OnSpectrumDataListener {
        void onSpectrumUpdate(float[] spectrum, float overallEnergy);
    }

    private OnSpectrumDataListener listener;

    public void setListener(OnSpectrumDataListener listener) {
        this.listener = listener;
    }

    public synchronized void start(int audioSessionId) {
        stop();
        if (audioSessionId < 0) return;
        try {
            visualizer = new Visualizer(audioSessionId);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[0]); // 最小 128 / 256 点降低 CPU 负载
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {}

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    processFft(fft);
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);

            visualizer.setEnabled(true);
            isCapturing = true;
        } catch (Throwable t) {
            visualizer = null;
            isCapturing = false;
        }
    }

    private void processFft(byte[] fft) {
        if (fft == null || fft.length < 2) return;
        int n = Math.min(smoothedFft.length, fft.length / 2);
        float sum = 0f;
        for (int i = 0; i < n; i++) {
            byte rfk = fft[i * 2];
            byte ifk = fft[i * 2 + 1];
            float magnitude = (float) Math.hypot(rfk, ifk) / 128.0f;
            if (magnitude > 1.0f) magnitude = 1.0f;

            // 指数平滑滤波，消除高频抖动，形成如液态水般的柔顺波动
            smoothedFft[i] = smoothedFft[i] * 0.60f + magnitude * 0.40f;
            sum += smoothedFft[i];
        }
        float overall = n > 0 ? (sum / n) : 0f;
        if (listener != null) {
            listener.onSpectrumUpdate(smoothedFft, overall);
        }
    }

    public synchronized void stop() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Throwable ignored) {}
            visualizer = null;
        }
        isCapturing = false;
        Arrays.fill(smoothedFft, 0f);
    }

    public boolean isCapturing() {
        return isCapturing;
    }
}
