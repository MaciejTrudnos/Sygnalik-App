# Sygnalik-App  
A mobile application designed for the [Sygnalik-Device](https://github.com/MaciejTrudnos/Sygnalik-Device)

## Overview  
Sygnalik-App connects to the [Sygnalik-Device](https://github.com/MaciejTrudnos/Sygnalik-Device) via Bluetooth and transmits notifications in real time.  
It also integrates with [Traccar](https://www.traccar.org) to provide GPS route tracking.

## Features  
- [x] **SMS notifications**  
- [x] **Incoming call notifications**  
- [x] **Speed camera alerts (Poland)**  
- [x] **Traccar integration (GPS route tracking)**  
- [ ] **Navigation support**

## Configuration
To enable Geocoding (Nominatim) and Tracking (Traccar), configure the following system environment variables:

| Key | Description | Example |
| :--- | :--- | :--- |
| `NOMINATIM_USER_AGENT` | Identifying your app to OpenStreetMap | `Sygnalik (contact@example.com)` |
| `TRACCAR_DEVICE_ID` | Your unique ID from the Traccar panel | `2E73E605-77B6...` |
| `TRACCAR_HOST` | The address of your Traccar instance | `http://demo3.traccar.org:5055` |

## Prerequisites
To function correctly in the background, Sygnalik-App requires several sensitive permissions. Before the first run, please go to Settings > Apps > Sygnalik-App and grant them.
