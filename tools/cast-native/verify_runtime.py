#!/usr/bin/env python3
"""Independent host inspection of media produced by Android JNI, not a producer."""
import collections
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent / 'ci'))
from qualify_rolling_hls_pipe import box, probe, require_box, validate_moof, MAX_INIT, MAX_UNIT


def decoded_hash(file, track):
    command = ['ffmpeg', '-v', 'error', '-xerror', '-i', str(file)]
    if track == 'video':
        command += ['-map', '0:v:0', '-c:v', 'rawvideo', '-pix_fmt', 'yuv420p', '-fps_mode', 'passthrough']
    else:
        command += ['-map', '0:a:0', '-c:a', 'pcm_s16le']
    result = subprocess.check_output(command + ['-f', 'hash', '-hash', 'sha256', '-'], timeout=30).decode().strip()
    assert result.startswith('SHA256=') and len(result) == 71, (file, track, result)
    return result


def aac_payload_hash(file):
    # Normalize all containers to the same ADTS encapsulation without decoding.
    # Finite qualification only: output is bounded by the fixture size.
    data = subprocess.check_output(['ffmpeg', '-v', 'error', '-xerror', '-i', str(file),
                                    '-map', '0:a:0', '-c:a', 'copy', '-f', 'adts', '-'], timeout=30)
    assert 0 < len(data) <= 20 * 1024 * 1024
    return hashlib.sha256(data).hexdigest()


def audio_diagnostics(file):
    info = subprocess.check_output(['ffprobe', '-v', 'error', '-select_streams', 'a:0',
                                    '-show_streams', '-show_packets',
                                    '-show_entries', 'stream=sample_rate,start_pts,duration_ts,time_base:packet=pts,dts,duration,size,side_data_list',
                                    '-of', 'json', str(file)], timeout=15)
    data = json.loads(info)
    packets = data.get('packets', [])
    print(file.name, 'audio stream', data.get('streams'), 'packets', len(packets),
          'first', packets[:3], 'last', packets[-3:])
    print(file.name, 'packet duration histogram', dict(collections.Counter(p.get('duration') for p in packets)))
    print(file.name, 'packet side data', [p.get('side_data_list') for p in packets if p.get('side_data_list')][:8])


def verify(root, ext):
    source = root / f'source.{ext}'
    converted = root / f'converted.{ext}.mp4'
    assert 0 < converted.stat().st_size <= 20 * 1024 * 1024
    original = probe(source)
    assert len(original['video']) == 864
    assert len(original['audio']) == 3376
    observed = collections.defaultdict(list)
    count = 0
    with converted.open('rb') as stream, tempfile.TemporaryDirectory() as temp:
        kind, ftyp = box(stream, MAX_INIT)
        assert kind == b'ftyp'
        kind, moov = box(stream, MAX_INIT - len(ftyp))
        assert kind == b'moov'
        require_box(moov, b'mvex')
        init = ftyp + moov
        while True:
            head = box(stream, MAX_UNIT, eof=True)
            if head is None:
                break
            kind, moof = head
            assert kind == b'moof'
            count += 1
            assert count <= 12
            validate_moof(moof, count)
            kind, mdat = box(stream, MAX_UNIT - len(moof))
            assert kind == b'mdat' and len(mdat) > 8
            check = pathlib.Path(temp) / 'fragment.mp4'
            with check.open('wb') as output:
                output.write(init)
                output.write(moof)
                output.write(mdat)
            values = probe(check)
            assert values['video'] and 'K' in values['video'][0][2]
            subprocess.run(['ffmpeg', '-v', 'error', '-xerror', '-i', str(check),
                            '-map', '0:v:0', '-map', '0:a:0', '-f', 'null', '-'],
                           timeout=15, check=True)
            for track, packets in values.items():
                observed[track].extend(packets)
    assert count == 12
    for file in (source, converted):
        audio_diagnostics(file)
    expected_payload = aac_payload_hash(source)
    actual_payload = aac_payload_hash(converted)
    print(ext, 'normalized AAC payload SHA256', expected_payload, actual_payload)
    assert expected_payload == actual_payload, (ext, 'AAC payload mismatch')
    # Count/timestamp equality does not prove payload conservation. Compare
    # decoded pixels and PCM on the entire synthetic clip as a separate gate.
    for track in ('video', 'audio'):
        expected = decoded_hash(source, track)
        actual = decoded_hash(converted, track)
        assert expected == actual, (ext, track, 'decoded content mismatch', expected, actual)
        print(ext, track, 'decoded content', actual)
    for column in (0, 1):
        src = original['video'][0][column] - original['audio'][0][column]
        dst = observed['video'][0][column] - observed['audio'][0][column]
        assert abs(src - dst) <= 0.0021, (ext, 'A/V offset', src, dst)
    for track, expected in original.items():
        actual = observed[track]
        assert len(expected) == len(actual), (ext, track, len(expected), len(actual))
        for column in (0, 1):
            drift = max(abs((a[column] - actual[0][column]) - (b[column] - expected[0][column]))
                        for a, b in zip(actual, expected))
            assert drift <= 0.0021, (ext, track, 'PTS' if column == 0 else 'DTS', drift)
            print(ext, track, 'PTS' if column == 0 else 'DTS', 'max drift', round(drift, 6))
    print(f'PASS {ext}: {count} independent fragments; video={len(observed["video"])} audio={len(observed["audio"])}')


if __name__ == '__main__':
    if not __debug__ or len(sys.argv) != 2:
        raise SystemExit('Use assertions enabled: verify_runtime.py fixture-directory')
    for extension in ('mkv', 'ts'):
        verify(pathlib.Path(sys.argv[1]), extension)
