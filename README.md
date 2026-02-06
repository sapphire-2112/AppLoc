# Android Live Location Sharing (GitHub-Based)

## Overview

This Android application demonstrates **live location sharing between two devices** using **GitHub as a synchronization medium**.

Each device:
- Captures its GPS location
- Pushes it to GitHub in JSON format
- Fetches the other device’s location
- Displays the fetched location on a map

> This project is for **learning and demonstration purposes**. GitHub is **not a real-time backend**.

---

## Design Principle

To avoid Git conflicts:

- **Device A** writes only to `nodeA.json`
- **Device B** writes only to `nodeB.json`
- Each device reads **only the other device’s file**

This ensures **conflict-free Git operations**.

---

## Data Format

```json
{
  "1707212123123": {
    "lat": 18.5204,
    "lon": 73.8567,
    "time": "06 Feb 2026, 04:35:23 PM"
  }
}
