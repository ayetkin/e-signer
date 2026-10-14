# Image for rendering UI screenshots headless (ScreenshotTest): Gradle + JDK 21 + fonts.
#   container build -m 8G -c 4 -t esigner-screenshots -f tools/screenshots.Dockerfile tools
FROM gradle:8-jdk21
RUN apt-get update -qq \
 && apt-get install -y -qq --no-install-recommends fontconfig fonts-inter fonts-dejavu-core libgl1 libglx-mesa0 libfreetype6 \
 && rm -rf /var/lib/apt/lists/*
# Prefer Inter for the generic sans-serif family, close to the system UI fonts on macOS and Windows.
RUN printf '<?xml version="1.0"?>\n<!DOCTYPE fontconfig SYSTEM "fonts.dtd">\n<fontconfig><alias><family>sans-serif</family><prefer><family>Inter</family></prefer></alias></fontconfig>\n' > /etc/fonts/local.conf \
 && fc-cache -f >/dev/null
