"""Independent bounded Matroska header inspection for synthetic AAC fixtures.
Do not use the host demuxer's skip-side-data support as the source of truth:
older FFmpeg versions can ignore valid CodecDelay on AAC tracks.
"""
import pathlib

HEADER_MAX = 2 * 1024 * 1024


def vint(data, pos, end, is_id=False):
    if pos >= end or not data[pos]:
        raise ValueError('invalid EBML VINT')
    first = data[pos]
    mask = 128
    width = 1
    while not first & mask:
        mask >>= 1
        width += 1
    if width > (4 if is_id else 8) or pos + width > end:
        raise ValueError('invalid EBML VINT width')
    value = first if is_id else first & (mask - 1)
    for byte in data[pos+1:pos+width]:
        value = value * 256 + byte
    unknown = not is_id and value == (1 << (7*width))-1
    return value, pos+width, unknown


def elements(data, start, end, allow_segment=False):
    pos = start
    count = 0
    while pos < end:
        count += 1
        if count > 4096:
            raise ValueError('too many EBML elements')
        eid, pos, _ = vint(data, pos, end, True)
        size, pos, unknown = vint(data, pos, end)
        if allow_segment and eid == 0x18538067:
            # Header-only reading is deliberate. Tracks must fit in prefix.
            yield eid, pos, end if unknown else min(end, pos+size)
            return
        if unknown or size > end-pos:
            raise ValueError('truncated or unknown EBML element')
        yield eid, pos, pos+size
        pos += size


def codec_delay_ns(path):
    with pathlib.Path(path).open('rb') as file:
        data = file.read(HEADER_MAX)
    segment = next((x for x in elements(data, 0, len(data), True) if x[0] == 0x18538067), None)
    if segment is None:
        raise ValueError('missing Segment')
    tracks = next((x for x in elements(data, segment[1], segment[2]) if x[0] == 0x1654AE6B), None)
    if tracks is None:
        raise ValueError('missing bounded Tracks header')
    audio = []
    for eid, start, end in elements(data, tracks[1], tracks[2]):
        if eid != 0xAE:
            continue
        fields = {}
        for key, lo, hi in elements(data, start, end):
            if key in (0x83, 0x86, 0x56AA):
                if key in fields or hi-lo > 32:
                    raise ValueError('duplicate or oversized track field')
                fields[key] = data[lo:hi]
        if fields.get(0x83) == b'\x02':
            if fields.get(0x86) != b'A_AAC':
                raise ValueError('unexpected audio codec')
            delay = fields.get(0x56AA, b'\0')
            if not 1 <= len(delay) <= 8:
                raise ValueError('invalid CodecDelay')
            audio.append(int.from_bytes(delay,'big'))
    if len(audio) != 1:
        raise ValueError('expected exactly one AAC track')
    return audio[0]


def priming_samples(path, rate):
    if pathlib.Path(path).suffix != '.mkv':
        return 0
    samples = (codec_delay_ns(path)*rate+500_000_000)//1_000_000_000
    if samples not in (0, 1024):
        raise ValueError('unsupported priming sample count')
    return samples
