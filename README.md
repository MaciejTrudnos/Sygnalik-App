# Sygnalik-App  
A mobile application designed for the [Sygnalik-Device](https://github.com/MaciejTrudnos/Sygnalik-Device)

## Overview  
Sygnalik-App connects to the [Sygnalik-Device](https://github.com/MaciejTrudnos/Sygnalik-Device) via Bluetooth and transmits notifications in real time.  
It also integrates with [Traccar](https://www.traccar.org) to provide GPS route tracking.

## Features  
- [x] **SMS alerts** – Get notified of incoming texts on your device.
- [x] **Call alerts** – Real-time incoming call notifications.
- [x] **Speed camera alerts** – Stay safe with localized alerts (Poland).
- [x] **Speed control alerts** – Warnings about nearby police speed controls.
- [x] **Traccar integration** – Automatic GPS route tracking.
- [x] **Navigation support** – Simple turn-by-turn cues (instruction, distance to next maneuver, remaining distance) from a self-hosted [GraphHopper](https://github.com/graphhopper/graphhopper) server.

## Configuration
To enable Geocoding (Nominatim), Tracking (Traccar), Warnings (Warning Gateway) and Routing (GraphHopper), copy the example configuration file to `.env` in the project root and fill in your values:

```bash
copy .env.example .env
```

Values are read from the `.env` file in the project root; system environment variables are used as a fallback. `.env` is gitignored – never commit it.

| Key | Description | Example |
| :--- | :--- | :--- |
| `NOMINATIM_USER_AGENT` | Identifying your app to OpenStreetMap | `Sygnalik (contact@example.com)` |
| `TRACCAR_DEVICE_ID` | Your unique ID from the Traccar panel | `2E73E605-77B6...` |
| `TRACCAR_HOST` | The address of your Traccar instance | `http://demo3.traccar.org:5055` |
| `WARNING_GATEWAY_HOST` | The address of the Sygnalik Warning Gateway instance | `http://sygnalik-warning-gateway:5223` |
| `WARNING_GATEWAY_API_KEY` | API key used to authorize requests to the Sygnalik Warning Gateway | `MY_SECRET_KEY_123` |
| `GRAPHHOPPER_HOST` | The address of your GraphHopper routing server (has a hardcoded default; set this variable to override) | `http://graphhopper.example.com:8989` |

## Prerequisites
To function correctly in the background, Sygnalik requires several sensitive permissions. Before the first run, please go to Settings > Apps > Sygnalik and grant them.

## Real Device Photos
<table align="left">
  <tr>
    <td align="center">
      <img src="https://github.com/MaciejTrudnos/Sygnalik-Device/blob/main/assets/mysuzuki.jpg" height="260"><br>
      <sub><b>Installed in vehicle – front view</b></sub>
    </td>
    <td align="center">
      <img src="https://github.com/MaciejTrudnos/Sygnalik-Device/blob/main/assets/mysuzuki-2.jpg" height="260"><br>
      <sub><b>Installed in vehicle – side view</b></sub>
    </td>
  </tr>
</table>
