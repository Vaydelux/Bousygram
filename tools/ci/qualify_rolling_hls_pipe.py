#!/usr/bin/env python3
"""Host-only continuous pipe remux qualification of finite synthetic fixtures.
Not an Android producer. Run under a host deadline, e.g. timeout 180s.
Never run with -O: assertions are qualification gates.
"""
import collections
import json
import pathlib
import struct
import subprocess
import sys
import tempfile

MIB = 1024 * 1024
MAX_UNIT = 8 * MIB
MAX_INIT = 512 * 1024
WINDOW_BYTES = 32 * MIB
WINDOW_COUNT = 6


def read_exact(stream, size):
    result = bytearray()
    while len(result) < size:
        part = stream.read(min(65536, size - len(result)))
        if not part:
            raise ValueError('truncated box')
        result.extend(part)
    return bytes(result)


def box(stream, limit, eof=False):
    first = stream.read(1)
    if not first and eof:
        return None
    if not first:
        raise ValueError('missing box')
    header = first + read_exact(stream, 7)
    size, kind = struct.unpack('>I4s', header)
    if not 8 <= size <= limit:
        raise ValueError('invalid box size')
    return kind, header + read_exact(stream, size - 8)


def children(data):
    pos = 8
    while pos < len(data):
        if len(data) - pos < 8:
            raise ValueError('truncated child')
        size, kind = struct.unpack_from('>I4s', data, pos)
        if not 8 <= size <= len(data) - pos:
            raise ValueError('invalid child')
        yield kind, data[pos:pos+size]
        pos += size


def require_box(data, kind):
    found = [b for k, b in children(data) if k == kind]
    if len(found) != 1:
        raise ValueError(f'expected one {kind!r}')
    return found[0]


def validate_moof(data, sequence):
    mfhd = require_box(data, b'mfhd')
    if len(mfhd) != 16 or struct.unpack_from('>I', mfhd, 12)[0] != sequence:
        raise ValueError('sequence mismatch')
    tracks = [b for k, b in children(data) if k == b'traf']
    if not 1 <= len(tracks) <= 2:
        raise ValueError('track count')
    for track in tracks:
        tfhd = require_box(track, b'tfhd')
        tfdt = require_box(track, b'tfdt')
        require_box(track, b'trun')
        if len(tfhd) < 16:
            raise ValueError('truncated tfhd')
        flags = int.from_bytes(tfhd[9:12], 'big')
        if flags & 1 or not flags & 0x020000 or len(tfdt) not in (16, 20):
            raise ValueError('fragment is not independently addressable')


def probe(file):
    out = subprocess.check_output([
        'ffprobe', '-v', 'error', '-show_streams', '-show_packets',
        '-show_entries', 'stream=index,codec_type,codec_name:packet=stream_index,pts_time,dts_time,flags',
        '-of', 'json', str(file)
    ], timeout=15)
    data = json.loads(out)
    types = {x['index']: x['codec_type'] for x in data['streams']}
    if sorted((x['codec_type'], x['codec_name']) for x in data['streams']) != [('audio','aac'),('video','h264')]:
        raise ValueError('unexpected tracks')
    result = collections.defaultdict(list)
    for p in data['packets']:
        result[types[p['stream_index']]].append((float(p['pts_time']), float(p['dts_time']), p['flags']))
    return result


def qualify(source):
    # Whole-timeline ffprobe arrays are for finite synthetic qualification only.
    if not 0 < source.stat().st_size <= 16 * MIB:
        raise ValueError('fixture size out of range')
    reference = probe(source)
    assert len(reference['video']) == 864
    assert len(reference['audio']) == 3376
    observed = collections.defaultdict(list)
    window = collections.deque()
    resident = high_water = total = count = 0
    with tempfile.TemporaryDirectory() as tmp, source.open('rb') as stdin:
        root = pathlib.Path(tmp)
        cmd = [
            'ffmpeg','-nostdin','-v','error','-xerror','-i','pipe:0',
            '-map','0:v:0','-map','0:a:0','-c','copy','-bsf:a','aac_adtstoasc',
            '-movflags','+delay_moov+empty_moov+default_base_moof+frag_keyframe+skip_trailer',
            '-min_frag_duration','4000000','-f','mp4','pipe:1'
        ]
        with subprocess.Popen(cmd, stdin=stdin, stdout=subprocess.PIPE) as process:
            try:
                kind, ftyp = box(process.stdout, MAX_INIT)
                assert kind == b'ftyp'
                kind, moov = box(process.stdout, MAX_INIT-len(ftyp))
                assert kind == b'moov'
                require_box(moov,b'mvex')
                init = ftyp + moov
                while True:
                    head = box(process.stdout, MAX_UNIT, eof=True)
                    if head is None:
                        break
                    kind, moof = head
                    assert kind == b'moof'
                    count += 1
                    assert count <= 12
                    validate_moof(moof, count)
                    kind, mdat = box(process.stdout, MAX_UNIT-len(moof))
                    assert kind == b'mdat' and len(mdat)>8
                    size = len(moof)+len(mdat)
                    # Simulated consumer acknowledges oldest before admitting next.
                    while window and (len(window)>=WINDOW_COUNT or resident+size>WINDOW_BYTES):
                        path, old = window.popleft()
                        path.unlink()
                        resident -= old
                    assert resident+size<=WINDOW_BYTES
                    path = root / f'{count}.m4s'
                    # Validate using just init plus the current fragment, not previous media.
                    check = root / 'probe.mp4'
                    with check.open('wb') as file:
                        file.write(init)
                        file.write(moof)
                        file.write(mdat)
                    packets=probe(check)
                    subprocess.run([
                        'ffmpeg','-v','error','-xerror','-i',str(check),
                        '-map','0:v:0','-map','0:a:0','-f','null','-'
                    ], check=True, timeout=15)
                    assert packets['video'] and 'K' in packets['video'][0][2]
                    for track, values in packets.items():
                        observed[track].extend(values)
                    check.unlink()
                    with path.open('wb') as file:
                        file.write(moof)
                        file.write(mdat)
                    window.append((path,size))
                    resident += size
                    total += size
                    high_water=max(high_water,resident)
                assert process.wait(timeout=10)==0
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()
    assert count == 12
    for column in (0,1):
        src=reference['video'][0][column]-reference['audio'][0][column]
        dst=observed['video'][0][column]-observed['audio'][0][column]
        print(source.suffix,'A/V origin drift',round(abs(src-dst),6))
        assert abs(src-dst)<=0.0021
    for track, values in reference.items():
        actual=observed[track]
        assert len(values)==len(actual),(track,len(values),len(actual))
        for column in (0,1):
            drift=max(abs((a[column]-actual[0][column])-(b[column]-values[0][column])) for a,b in zip(actual,values))
            print(source.suffix,track,'PTS' if column==0 else 'DTS','max drift',round(drift,6))
            assert drift<=0.0021, drift
    print(f'{source.suffix}: {count} fragments; video={len(observed["video"])} audio={len(observed["audio"])}; total={total}; resident_peak={high_water}')


if __name__ == '__main__':
    if not __debug__:
        raise SystemExit('Qualification assertions must be enabled')
    if len(sys.argv) != 3:
        raise SystemExit('Usage: qualify_rolling_hls_pipe.py synthetic.mkv synthetic.ts')
    for name in sys.argv[1:]:
        qualify(pathlib.Path(name))
