package com.retro.subsonic;

import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;

public class AudioVisualizerHelper {
    private Visualizer visualizer;
    private volatile boolean isCapturing = false;
    private volatile boolean isPlaying = false;
    private long lastDataTime = 0;
    private Handler fallbackHandler = new Handler(Looper.getMainLooper());

    public interface OnSpectrumDataListener {
        void onSpectrumUpdate(float[] spectrum, float overallEnergy);
    }

    private OnSpectrumDataListener listener;

    public void setListener(OnSpectrumDataListener listener) {
        this.listener = listener;
    }

    public synchronized void setPlaying(boolean playing) {
        this.isPlaying = playing;
        if (!playing) {
            fallbackHandler.removeCallbacks(fallbackRunnable);
            if (listener != null) {
                listener.onSpectrumUpdate(new float[32], 0f);
            }
        } else {
            startFallbackCheck();
        }
    }

    public synchronized void start(int audioSessionId) {
        stop();
        try {
            int targetSession = (audioSessionId > 0) ? audioSessionId : 0;
            try {
                visualizer = new Visualizer(targetSession);
            } catch (Throwable t1) {
                if (targetSession != 0) {
                    visualizer = new Visualizer(0);
                } else {
                    throw t1;
                }
            }

            int[] range = Visualizer.getCaptureSizeRange();
            int captureSize = Math.max(128, range[0]);
            visualizer.setCaptureSize(captureSize);

            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                    if (waveform != null && waveform.length > 0) {
                        processWaveform(waveform);
                    }
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    if (fft != null && fft.length > 0) {
                        processFft(fft);
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true);

            visualizer.setEnabled(true);
            isCapturing = true;
            lastDataTime = System.currentTimeMillis();
        } catch (Throwable t) {
            visualizer = null;
            isCapturing = false;
        }

        if (isPlaying) {
            startFallbackCheck();
        }
    }

    private void processWaveform(byte[] waveform) {
        if (!isPlaying) return;
        lastDataTime = System.currentTimeMillis();
        long sum = 0;
        for (byte b : waveform) {
            int val = (b & 0xFF) - 128;
            sum += (val * val);
        }
        float rms = (float) Math.sqrt(sum / (double) waveform.length) / 128.0f;
        // 采用平滑对数曲线映射，消除爆音冲击，起伏更自然精准
        float energy = Math.min(1.0f, (float) Math.pow(rms * 1.8f, 0.85));
        if (listener != null) {
            listener.onSpectrumUpdate(new float[32], energy);
        }
    }

    private void processFft(byte[] fft) {
        if (!isPlaying) return;
        lastDataTime = System.currentTimeMillis();
        if (fft.length < 8) return;
        float bassSum = 0;
        for (int i = 1; i < 7; i++) {
            byte rfk = fft[i * 2];
            byte ifk = fft[i * 2 + 1];
            bassSum += (float) Math.hypot(rfk, ifk);
        }
        float bassEnergy = Math.min(1.0f, (bassSum / 6.0f) / 56.0f);
        if (listener != null && bassEnergy > 0.05f) {
            listener.onSpectrumUpdate(new float[32], bassEnergy);
        }
    }

    private void startFallbackCheck() {
        fallbackHandler.removeCallbacks(fallbackRunnable);
        fallbackHandler.postDelayed(fallbackRunnable, 200);
    }

    private Runnable fallbackRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying) return;
            long now = System.currentTimeMillis();
            if (now - lastDataTime > 300) {
                float fake = (float) (Math.sin(now / 220.0) * 0.20 + 0.28);
                if (listener != null) {
                    listener.onSpectrumUpdate(new float[32], fake);
                }
            }
            fallbackHandler.postDelayed(this, 120);
        }
    };

    public synchronized void stop() {
        fallbackHandler.removeCallbacks(fallbackRunnable);
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Throwable ignored) {}
            visualizer = null;
        }
        isCapturing = false;
    }

    public boolean isCapturing() {
        return isCapturing;
    }
}
