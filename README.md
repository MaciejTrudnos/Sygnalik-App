# Sygnalik-App  
A mobile application designed for the [Sygnalik-Device](https://github.com/MaciejTrudnos/Sygnalik-Device)

## Overview  
Sygnalik-App connects to the [Sygnalik-Device](https://github.com/MaciejTrudnos/Sygnalik-Device) via Bluetooth and transmits notifications in real time.  
It also integrates with [Traccar](https://www.traccar.org) to provide GPS route tracking.

## Features  
- [x] **SMS alerts** – Get notified of incoming texts on your device.
- [x] **Call alerts** – Real-time incoming call notifications.
- [x] **Speed camera alerts** – Stay safe with localized alerts (Poland).
- [x] **Traccar integration** – Automatic GPS route tracking.
- [ ] **Navigation support** – (In progress) Visual [turn-by-turn](https://github.com/MaciejTrudnos/Sygnalik-Directions-API) cues.

## Configuration
To enable Geocoding (Nominatim) and Tracking (Traccar), configure the following system environment variables:

| Key | Description | Example |
| :--- | :--- | :--- |
| `NOMINATIM_USER_AGENT` | Identifying your app to OpenStreetMap | `Sygnalik (contact@example.com)` |
| `TRACCAR_DEVICE_ID` | Your unique ID from the Traccar panel | `2E73E605-77B6...` |
| `TRACCAR_HOST` | The address of your Traccar instance | `http://demo3.traccar.org:5055` |

## Prerequisites
To function correctly in the background, Sygnalik requires several sensitive permissions. Before the first run, please go to Settings > Apps > Sygnalik and grant them.
