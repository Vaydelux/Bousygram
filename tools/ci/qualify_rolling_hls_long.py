#!/usr/bin/env python3
"""Host-only long-duration nonseekable output and bounded retirement gate."""
import collections
import json
import pathlib
import struct
import subprocess
import sys
import tempfile

MIB = 1024 * 1024


def box(stream, limit, eof=False):
    def exact(n):
        result = bytearray()
        while len(result) < n:
            part = stream.read(min(65536, n-len(result)))
            if not part:
                raise ValueError('truncated box')
            result.extend(part)
        return bytes(result)
    first = stream.read(1)
    if not first and eof:
        return None
    assert first
    header = first + exact(7)
    size, kind = struct.unpack('>I4s', header)
    assert 8 <= size <= limit
    return kind, header + exact(size-8)


def probe(path):
    data = json.loads(subprocess.check_output([
        'ffprobe','-v','error','-show_streams','-show_packets','-show_entries',
        'stream=index,codec_type,codec_name:packet=stream_index,pts_time,dts_time,flags',
        '-of','json',str(path)], timeout=30))
    assert sorted((s['codec_type'], s['codec_name']) for s in data['streams']) == [('audio','aac'),('video','h264')]
    tracks = {s['index']: s['codec_type'] for s in data['streams']}
    result = collections.defaultdict(list)
    for p in data['packets']:
        result[tracks[p['stream_index']]].append((float(p['pts_time']),float(p['dts_time']),p['flags']))
    return result


def qualify(source):
    assert 20*MIB < source.stat().st_size <= 64*MIB
    reference = probe(source)
    observed = collections.defaultdict(list)
    window = collections.deque()
    resident = peak = total = count = scratch_peak = 0
    cmd = ['ffmpeg','-nostdin','-v','error','-xerror','-i','pipe:0','-map','0:v:0','-map','0:a:0',
           '-c','copy','-bsf:a','aac_adtstoasc','-movflags',
           '+delay_moov+empty_moov+default_base_moof+frag_keyframe+skip_trailer',
           '-min_frag_duration','4000000','-f','mp4','pipe:1']
    with tempfile.TemporaryDirectory() as tmp, source.open('rb') as stdin:
        root = pathlib.Path(tmp)
        with subprocess.Popen(cmd,stdin=stdin,stdout=subprocess.PIPE) as process:
            try:
                kind, ftyp = box(process.stdout,512*1024)
                assert kind == b'ftyp'
                kind, moov = box(process.stdout,512*1024-len(ftyp))
                assert kind == b'moov'
                init = ftyp+moov
                while True:
                    head = box(process.stdout,8*MIB,eof=True)
                    if head is None:
                        break
                    kind, moof = head
                    assert kind == b'moof'
                    kind, mdat = box(process.stdout,8*MIB-len(moof))
                    assert kind == b'mdat' and len(mdat)>8
                    count += 1
                    assert count <= 60
                    size = len(moof)+len(mdat)
                    while window and (len(window)>=2 or resident+size>32*MIB):
                        old,n = window.popleft()
                        assert old.stat().st_size == n
                        old.unlink()
                        resident -= n
                    assert resident+size <= 32*MIB
                    segment = root/f'{count}.m4s'
                    with segment.open('wb') as out:
                        out.write(moof); out.write(mdat)
                    window.append((segment,size))
                    resident += size; total += size; peak = max(peak,resident)
                    check = root/'probe.mp4'
                    with check.open('wb') as out:
                        out.write(init); out.write(moof); out.write(mdat)
                    scratch_peak = max(scratch_peak, resident+check.stat().st_size)
                    values = probe(check)
                    assert values['video'] and 'K' in values['video'][0][2]
                    subprocess.run(['ffmpeg','-v','error','-xerror','-i',str(check),'-map','0:v:0',
                                    '-map','0:a:0','-f','null','-'],check=True,timeout=15,stderr=subprocess.PIPE)
                    check.unlink()
                    for track,samples in values.items():
                        observed[track].extend(samples)
                assert process.wait(timeout=15)==0
            finally:
                if process.poll() is None:
                    process.kill(); process.wait()
        while window:
            old,n = window.popleft(); old.unlink(); resident -= n
        assert resident == 0 and not list(root.iterdir())
    assert count == 60 and total > 20*MIB and len(reference['video']) == 8640
    for track, expected in reference.items():
        actual = observed[track]
        assert len(expected)==len(actual),(track,len(expected),len(actual))
        for col in (0,1):
            drift=max(abs((a[col]-actual[0][col])-(b[col]-expected[0][col])) for a,b in zip(actual,expected))
            assert drift<=0.0021,(track,col,drift)
    for col in (0,1):
        assert abs((reference['video'][0][col]-reference['audio'][0][col])-(observed['video'][0][col]-observed['audio'][0][col]))<=0.0021
    print(f'LONG_PIPE_PASS {source.suffix}: input={source.stat().st_size} output={total} fragments={count} video={len(observed["video"])} audio={len(observed["audio"])} retained_peak={peak} scratch_peak={scratch_peak} scratch_remaining=0',flush=True)


if __name__=='__main__':
    if not __debug__ or len(sys.argv)!=3:
        raise SystemExit('Usage: qualify_rolling_hls_long.py source.mkv source.ts')
    for name in sys.argv[1:]:
        qualify(pathlib.Path(name))
