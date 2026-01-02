import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class WhiteNoiseGenerator {

    public static void main(String[] args) {
        final int durationSeconds = 10; // length of audio in seconds
        final int sampleRate = 44100;   // samples per second
        final int bitsPerSample = 16;   // bits per sample
        final int channels = 2;         // mono audio

        int totalSamples = durationSeconds * sampleRate;
        int bytesPerSample = bitsPerSample / 8;
        int totalBytes = totalSamples * bytesPerSample * channels;

        byte[] audioData = new byte[totalBytes];

        Random rand = new Random();

        // Generate white noise samples
        for (int i = 0; i < totalSamples; i++) {
            // Generate a random sample in the range of a signed 16-bit PCM
            short sample = (short) (rand.nextInt(65536) - 32768);
            // Little endian byte order
            audioData[2 * i] = (byte) (sample & 0xFF);
            audioData[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        // Define audio format
        AudioFormat format = new AudioFormat(
                sampleRate,
                bitsPerSample,
                channels,
                true,   // signed
                false   // little endian
        );

        // Create an AudioInputStream from the byte array
        try (AudioInputStream ais = new AudioInputStream(
                new java.io.ByteArrayInputStream(audioData),
                format,
                totalSamples
        )) {
            // Write to WAV file
            File outputFile = new File("white_noise.wav");
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
            System.out.println("White noise audio file generated: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
