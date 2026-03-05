#!/usr/bin/env bash

# AI Generated
# NOTE: this script requires ffmpeg.

set -e

OUT_DIR="alac_test_audio"
mkdir -p "$OUT_DIR"

echo "Generating nasty ALAC MP4 edge cases..."

# Base ALAC file
ffmpeg -y \
  -f lavfi -i "sine=frequency=440:duration=5" \
  -ar 44100 \
  -ac 2 \
  -c:a alac \
  "$OUT_DIR/base_alac.m4a" \
  -loglevel error

# Faststart version (moov at beginning)
ffmpeg -y \
  -i "$OUT_DIR/base_alac.m4a" \
  -c copy \
  -movflags +faststart \
  "$OUT_DIR/alac_faststart.m4a" \
  -loglevel error

# Non-faststart version (moov at end)
ffmpeg -y \
  -i "$OUT_DIR/base_alac.m4a" \
  -c copy \
  -movflags -faststart \
  "$OUT_DIR/alac_moov_at_end.m4a" \
  -loglevel error

echo "Generated:"
ls -1 "$OUT_DIR"/alac_*.m4a

echo "Generating ALAC test corpus in $OUT_DIR"

generate_alac () {
  name=$1
  filter=$2
  rate=$3
  channels=$4
  duration=$5
  samplefmt=$6

  ffmpeg -y \
    -f lavfi -i "$filter:duration=$duration" \
    -ar "$rate" \
    -ac "$channels" \
    -sample_fmt "$samplefmt" \
    -c:a alac \
    "$OUT_DIR/$name.m4a" \
    -loglevel error
}

# 1. Basic sine tones
generate_alac sine_16k_mono_16bit "sine=frequency=440" 16000 1 3 s16p
generate_alac sine_44k_stereo_16bit "sine=frequency=440" 44100 2 3 s16p
generate_alac sine_48k_stereo_24bit "sine=frequency=440" 48000 2 3 s32p

# 2. White noise
generate_alac noise_44k_stereo_16bit "anoisesrc=color=white" 44100 2 5 s16p

# 3. Silence
generate_alac silence_16k_mono "anullsrc=r=16000:cl=mono" 16000 1 5 s16p

# 4. Impulse (good for testing resampling)
ffmpeg -y \
  -f lavfi -i "aevalsrc=if(eq(n\,0)\,1\,0):s=44100:d=1" \
  -ac 1 \
  -c:a alac \
  "$OUT_DIR/impulse_44k_mono.m4a" \
  -loglevel error

# 5. Frequency sweep
ffmpeg -y \
  -f lavfi -i "aevalsrc=sin(2*PI*(20 + (20000-20)*t/5)*t):s=48000:d=5" \
  -ar 48000 \
  -ac 2 \
  -c:a alac \
  "$OUT_DIR/sweep_48k_stereo.m4a" \
  -loglevel error

# 6. Very short file
generate_alac short_200ms "sine=frequency=1000" 44100 2 0.2 s16p

# 7. Long file
generate_alac long_120s "sine=frequency=440" 44100 2 120 s16p

# 8. Stereo channel difference test
# left channel: 440Hz
# right channel: 880Hz
ffmpeg -y \
  -f lavfi -i "sine=frequency=440:duration=5" \
  -f lavfi -i "sine=frequency=880:duration=5" \
  -filter_complex "[0:a][1:a]join=inputs=2:channel_layout=stereo[a]" \
  -map "[a]" \
  -ar 44100 \
  -c:a alac \
  "$OUT_DIR/stereo_lr_different.m4a" \
  -loglevel error

# 9. Stereo, opposite phases
ffmpeg -y \
  -f lavfi -i "sine=frequency=440:duration=5" \
  -filter_complex "[0:a]asplit=2[a][b];[b]volume=-1[b_inv];[a][b_inv]join=inputs=2:channel_layout=stereo" \
  -ar 44100 \
  -sample_fmt s16p \
  -c:a alac \
  alac_test_audio/stereo_phase_test.m4a

echo "Done."
echo "Generated files:"
ls -1 "$OUT_DIR"


echo "Producing wav files"
for f in $OUT_DIR/*.m4a; do
  ffmpeg -i "$f" "${f%.m4a}.wav" -loglevel error
done
echo "done"
