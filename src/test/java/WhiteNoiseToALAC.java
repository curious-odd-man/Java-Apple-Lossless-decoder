import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;

public class WhiteNoiseToALAC {
    public static void main(String[] args) throws Exception {
        int durationSeconds = 10;
        int sampleRate = 44100;
        int channels = 1; // stereo
        int bitsPerSample = 16;
        String wavFilePath = "white_noise_mono.wav";
        String alacFilePath = "white_noise_mono.m4a";

        // Generate white noise PCM data
        byte[] pcmData = generateWhiteNoise(durationSeconds, sampleRate, channels, bitsPerSample);

        // Write PCM data to WAV file

        writeWavFile(wavFilePath, pcmData, sampleRate, channels, bitsPerSample);

        // Convert WAV to ALAC using ffmpeg
        convertWavToAlac(wavFilePath, alacFilePath);

        System.out.println("ALAC file generated at: " + alacFilePath);
    }

    private static byte[] generateWhiteNoise(int durationSeconds, float sampleRate, int channels, int bitsPerSample) {
        int totalSamples = (int) (durationSeconds * sampleRate);
        int bytesPerSample = bitsPerSample / 8;
        int totalBytes = totalSamples * channels * bytesPerSample;

        byte[] data = new byte[totalBytes];
        java.util.Random rand = new java.util.Random();

        for (int i = 0; i < totalSamples; i++) {
            // Generate a random sample value for white noise
            short sample = (short) (rand.nextInt(Short.MAX_VALUE + 1) - Short.MAX_VALUE / 2);
            for (int ch = 0; ch < channels; ch++) {
                int index = (i * channels + ch) * bytesPerSample;
                // Little endian
                data[index] = (byte) (sample & 0xFF);
                data[index + 1] = (byte) ((sample >> 8) & 0xFF);
            }
        }
        return data;
    }

    private static void writeWavFile(String filename, byte[] pcmData, float sampleRate, int channels, int bitsPerSample) throws IOException {
        AudioFormat format = new AudioFormat(sampleRate, bitsPerSample, channels, true, false);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
             AudioInputStream ais = new AudioInputStream(bais, format, pcmData.length / (channels * bitsPerSample / 8))) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(filename));
        }
    }

    private static void convertWavToAlac(String wavFilePath, String alacFilePath) throws IOException, InterruptedException {
        // Make sure ffmpeg is installed and available in PATH
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-i", wavFilePath, "-c:a", "alac", alacFilePath
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read ffmpeg output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("ffmpeg process failed with exit code " + exitCode);
        }
    }
}
