"""Small lossless binary NBT reader/writer used by the structure conversion tool."""
from dataclasses import dataclass
import gzip
import io
import struct

@dataclass
class Tag:
    kind: int
    value: object

def string(value): return Tag(8, value)
def integer(value): return Tag(3, value)
def compound(value): return Tag(10, value)

def read(path):
    b = io.BytesIO(gzip.decompress(path.read_bytes()))
    def num(fmt): return struct.unpack('>' + fmt, b.read(struct.calcsize(fmt)))[0]
    def text(): return b.read(num('H')).decode('utf-8')
    def payload(t):
        if t in (1,2,3,4,5,6): return Tag(t, num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t]))
        if t == 7: return Tag(t, b.read(num('i')))
        if t == 8: return string(text())
        if t == 9:
            elem, count = num('B'), num('i')
            return Tag(t, (elem, [payload(elem) for _ in range(count)]))
        if t == 10:
            result = {}
            while (kind := num('B')):
                key = text(); result[key] = payload(kind)
            return compound(result)
        if t in (11, 12): return Tag(t, [num('i' if t == 11 else 'q') for _ in range(num('i'))])
        raise ValueError('Unknown NBT type ' + str(t))
    kind = num('B'); name = text(); root = payload(kind)
    if b.read(1): raise ValueError('Trailing NBT data')
    return name, root

def write(path, name, root):
    b = io.BytesIO()
    def num(fmt, v): b.write(struct.pack('>' + fmt, v))
    def text(s):
        raw = s.encode('utf-8'); num('H', len(raw)); b.write(raw)
    def payload(tag):
        t, v = tag.kind, tag.value
        if t in (1,2,3,4,5,6): num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t], v)
        elif t == 7: num('i', len(v)); b.write(v)
        elif t == 8: text(v)
        elif t == 9:
            kind, vals = v; num('B', kind); num('i', len(vals))
            for child in vals:
                if child.kind != kind: raise ValueError('Mixed NBT list types')
                payload(child)
        elif t == 10:
            for key, child in v.items(): num('B', child.kind); text(key); payload(child)
            num('B', 0)
        elif t in (11,12):
            num('i', len(v))
            for item in v: num('i' if t == 11 else 'q', item)
        else: raise ValueError('Unknown NBT type ' + str(t))
    num('B', root.kind); text(name); payload(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(b.getvalue(), mtime=0))

def plain(tag):
    if tag.kind == 10: return {k: plain(v) for k, v in tag.value.items()}
    if tag.kind == 9: return [plain(v) for v in tag.value[1]]
    return tag.value

def from_plain(value):
    if isinstance(value, str): return string(value)
    if isinstance(value, dict): return compound({k: from_plain(v) for k, v in value.items()})
    raise TypeError(value)
