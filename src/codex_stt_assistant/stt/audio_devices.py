from __future__ import annotations


def list_input_devices():
    devices = []
    try:
        import pyaudio
    except Exception:
        return devices

    pa = pyaudio.PyAudio()
    try:
        for i in range(pa.get_device_count()):
            info = pa.get_device_info_by_index(i)
            if info.get('maxInputChannels', 0) > 0:
                devices.append({
                    'index': i,
                    'name': info.get('name', f'Device {i}'),
                    'channels': int(info.get('maxInputChannels', 0)),
                    'rate': int(info.get('defaultSampleRate', 0)),
                })
    finally:
        try:
            pa.terminate()
        except Exception:
            pass

    return devices
