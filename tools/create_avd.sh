#!/bin/bash
# Teyes CC3 AVD for Android Studio
# Usage: bash tools/create_avd.sh

set -e

AVD_NAME="Teyes_CC3"
API_LEVEL="29"
ARCH="x86"

echo "Creating Teyes CC3 AVD..."

# Создать device definition через avdmanager
avdmanager create avd \
  -n "$AVD_NAME" \
  -k "system-images;android-${API_LEVEL};default;${ARCH}" \
  -d "7.0\" WSVGA Tablet" \
  -f

echo "Applying CC3 hardware config..."

# Перезаписать config.ini CC3-специфичными настройками
CONFIG="$HOME/.android/avd/${AVD_NAME}.avd/config.ini"

cat >> "$CONFIG" << 'AVDCONFIG'

# Teyes CC3 Hardware Profile
hw.device.name=teyes_cc3
hw.device.manufacturer=Teyes

# Screen: 7" 1280x720 hdpi
hw.lcd.density=213
hw.lcd.height=720
hw.lcd.width=1280
hw.lcd.deepestPixelFormat=32

# RAM 2 GB
hw.ramSize=2048

# No hw buttons — CC3 has on-screen
hw.mainKeys=no
hw.keyboard=no

# Sensors CC3 has
hw.sensors.proximity=no
hw.sensors.light=no
hw.sensors.pressure=yes
hw.sensors.temperature=yes

# Storage
disk.dataPartition.size=8192M

# No GPS on most CC3 units
hw.gps=no

# Audio (CC3 has audio through CAN bus — disable mic)
hw.audioInput=no
hw.audioOutput=yes

# Performance
hw.gpu.enabled=yes
hw.gpu.mode=host
hw.camera.back=none
hw.camera.front=none

# Fast boot
fastboot.forceColdBoot=no

AVDCONFIG

echo "✅ AVD '$AVD_NAME' created."
echo ""
echo "Launch: emulator -avd $AVD_NAME"
echo "Or open Android Studio → Device Manager → $AVD_NAME"
