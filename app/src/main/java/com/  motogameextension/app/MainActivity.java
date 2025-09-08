package com.motogameextension.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int PERMISSION_REQUEST_CODE = 200;
    
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isRecording = false;
    private boolean isVoiceChangerActive = false;
    
    // UI Elements
    private Button btnToggleVoiceChanger;
    private SeekBar pitchSlider;
    private Spinner voiceEffectSpinner;
    private TextView deviceInfo;
    private LinearLayout gameToolsLayout;
    private Switch performanceBoostSwitch;
    private TextView temperatureMonitor;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Verificar que sea un dispositivo Motorola
        if (!isMotorolaDevice()) {
            showErrorAndExit("Esta aplicación solo funciona en dispositivos Motorola");
            return;
        }
        
        setContentView(R.layout.activity_main);
        initializeUI();
        requestPermissions();
    }
    
    private boolean isMotorolaDevice() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        String brand = Build.BRAND.toLowerCase();
        return manufacturer.contains("motorola") || brand.contains("motorola");
    }
    
    private void initializeUI() {
        // Voice Changer Section
        btnToggleVoiceChanger = findViewById(R.id.btnToggleVoiceChanger);
        pitchSlider = findViewById(R.id.pitchSlider);
        voiceEffectSpinner = findViewById(R.id.voiceEffectSpinner);
        
        // Game Tools Section
        gameToolsLayout = findViewById(R.id.gameToolsLayout);
        performanceBoostSwitch = findViewById(R.id.performanceBoostSwitch);
        temperatureMonitor = findViewById(R.id.temperatureMonitor);
        deviceInfo = findViewById(R.id.deviceInfo);
        
        setupVoiceEffects();
        setupGameTools();
        displayDeviceInfo();
        
        btnToggleVoiceChanger.setOnClickListener(v -> toggleVoiceChanger());
        performanceBoostSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> 
            togglePerformanceBoost(isChecked));
    }
    
    private void setupVoiceEffects() {
        String[] effects = {
            "Normal", "Robot", "Alien", "Deep Voice", "Chipmunk", 
            "Echo", "Reverb", "Monster", "Child", "Elderly"
        };
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, effects);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceEffectSpinner.setAdapter(adapter);
        
        pitchSlider.setMax(200);
        pitchSlider.setProgress(100); // Normal pitch
    }
    
    private void setupGameTools() {
        // Integración específica con Motorola Game Time
        try {
            Intent motoGameIntent = new Intent();
            motoGameIntent.setAction("com.motorola.gametime.EXTENSION");
            motoGameIntent.putExtra("extension_name", "Voice Changer Pro");
            sendBroadcast(motoGameIntent);
        } catch (Exception e) {
            // Manejar si Game Time no está disponible
        }
        
        // Monitor de temperatura (específico para Motorola)
        updateTemperatureMonitor();
    }
    
    private void displayDeviceInfo() {
        String info = String.format(
            "Dispositivo: %s %s\nAndroid: %s\nGame Time: %s", 
            Build.BRAND, Build.MODEL, Build.VERSION.RELEASE,
            isMotoGameTimeAvailable() ? "Disponible" : "No disponible"
        );
        deviceInfo.setText(info);
    }
    
    private boolean isMotoGameTimeAvailable() {
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo("com.motorola.gametime", PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    
    private void requestPermissions() {
        String[] permissions = {
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
            android.Manifest.permission.SYSTEM_ALERT_WINDOW
        };
        
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }
    
    private void toggleVoiceChanger() {
        if (!isVoiceChangerActive) {
            startVoiceChanger();
        } else {
            stopVoiceChanger();
        }
    }
    
    private void startVoiceChanger() {
        if (ContextCompat.checkSelfPermission(this, 
                android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permisos de audio requeridos", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );
            
            audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            );
            
            audioRecord.startRecording();
            audioTrack.play();
            
            isRecording = true;
            isVoiceChangerActive = true;
            btnToggleVoiceChanger.setText("Desactivar Voice Changer");
            
            // Iniciar thread de procesamiento de audio
            startAudioProcessing();
            
        } catch (Exception e) {
            Toast.makeText(this, "Error al iniciar voice changer: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    private void startAudioProcessing() {
        Thread audioThread = new Thread(() -> {
            short[] buffer = new short[1024];
            
            while (isRecording && audioRecord != null) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                
                if (read > 0) {
                    // Aplicar efectos de voz
                    short[] processedBuffer = applyVoiceEffect(buffer, read);
                    
                    if (audioTrack != null) {
                        audioTrack.write(processedBuffer, 0, read);
                    }
                }
            }
        });
        
        audioThread.start();
    }
    
    private short[] applyVoiceEffect(short[] buffer, int length) {
        short[] processed = new short[length];
        float pitchFactor = pitchSlider.getProgress() / 100.0f;
        String selectedEffect = voiceEffectSpinner.getSelectedItem().toString();
        
        for (int i = 0; i < length; i++) {
            float sample = buffer[i];
            
            // Aplicar modificación de pitch
            sample *= pitchFactor;
            
            // Aplicar efectos específicos
            switch (selectedEffect) {
                case "Robot":
                    sample = applyRobotEffect(sample, i);
                    break;
                case "Alien":
                    sample = applyAlienEffect(sample, i);
                    break;
                case "Deep Voice":
                    sample *= 0.5f;
                    break;
                case "Chipmunk":
                    sample *= 1.5f;
                    break;
                case "Echo":
                    sample = applyEchoEffect(sample, i);
                    break;
                case "Monster":
                    sample = applyMonsterEffect(sample);
                    break;
            }
            
            // Limitar el rango
            processed[i] = (short) Math.max(Short.MIN_VALUE, 
                Math.min(Short.MAX_VALUE, sample));
        }
        
        return processed;
    }
    
    private float applyRobotEffect(float sample, int index) {
        return sample * (1.0f + 0.3f * (float) Math.sin(index * 0.1));
    }
    
    private float applyAlienEffect(float sample, int index) {
        return sample * (1.0f + 0.5f * (float) Math.sin(index * 0.2));
    }
    
    private float applyEchoEffect(float sample, int index) {
        return sample + (sample * 0.3f);
    }
    
    private float applyMonsterEffect(float sample) {
        return sample * 0.7f + Math.signum(sample) * 1000;
    }
    
    private void stopVoiceChanger() {
        isRecording = false;
        isVoiceChangerActive = false;
        
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }
        
        btnToggleVoiceChanger.setText("Activar Voice Changer");
    }
    
    private void togglePerformanceBoost(boolean enabled) {
        if (enabled) {
            try {
                Runtime.getRuntime().exec("su -c 'echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor'");
                Toast.makeText(this, "Performance Boost activado", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Boost requiere permisos root", Toast.LENGTH_SHORT).show();
            }
        } else {
            try {
                Runtime.getRuntime().exec("su -c 'echo interactive > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor'");
                Toast.makeText(this, "Performance Boost desactivado", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                // Manejar error
            }
        }
    }
    
    private void updateTemperatureMonitor() {
        Thread tempThread = new Thread(() -> {
            while (true) {
                try {
                    String temp = "N/A";
                    try {
                        Process process = Runtime.getRuntime().exec("cat /sys/class/thermal/thermal_zone0/temp");
                        // Procesar resultado...
                    } catch (Exception e) {
                        // Usar método alternativo
                    }
                    
                    final String finalTemp = temp;
                    runOnUiThread(() -> temperatureMonitor.setText("Temperatura CPU: " + finalTemp + "°C"));
                    
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        tempThread.start();
    }
    
    private void showErrorAndExit(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }
    
    // Funciones adicionales para botones del layout
    public void openGameTime(View view) {
        try {
            Intent gameTimeIntent = getPackageManager()
                .getLaunchIntentForPackage("com.motorola.gametime");
            if (gameTimeIntent != null) {
                startActivity(gameTimeIntent);
            } else {
                Toast.makeText(this, "Game Time no encontrado", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al abrir Game Time", Toast.LENGTH_SHORT).show();
        }
    }
    
    public void toggleDND(View view) {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            Toast.makeText(this, "Modo Gaming DND activado", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error al activar DND", Toast.LENGTH_SHORT).show();
        }
    }
    
    public void boostRAM(View view) {
        try {
            Runtime.getRuntime().gc();
            System.runFinalization();
            Runtime.getRuntime().exec("su -c 'sync && echo 3 > /proc/sys/vm/drop_caches'");
            Toast.makeText(this, "RAM optimizada para gaming", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Boost básico aplicado", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVoiceChanger();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Todos los permisos son necesarios", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
        }
    }
}
