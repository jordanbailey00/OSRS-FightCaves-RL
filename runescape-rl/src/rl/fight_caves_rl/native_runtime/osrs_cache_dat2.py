from __future__ import annotations

import bz2
import gzip
import io
import struct
from dataclasses import dataclass, field
from pathlib import Path


COMPRESSION_NONE = 0
COMPRESSION_BZIP2 = 1
COMPRESSION_GZIP = 2

INDEX_SIZE = 6
SECTOR_SIZE = 520
SECTOR_HEADER_SIZE_SMALL = 8
SECTOR_DATA_SIZE_SMALL = 512
SECTOR_HEADER_SIZE_BIG = 10
SECTOR_DATA_SIZE_BIG = 510


def read_u8(buf: io.BytesIO) -> int:
    data = buf.read(1)
    return data[0] if data else 0


def read_u16(buf: io.BytesIO) -> int:
    data = buf.read(2)
    return struct.unpack(">H", data)[0] if len(data) == 2 else 0


def read_u24(buf: io.BytesIO) -> int:
    data = buf.read(3)
    if len(data) != 3:
        return 0
    return (data[0] << 16) | (data[1] << 8) | data[2]


def read_u32(buf: io.BytesIO) -> int:
    data = buf.read(4)
    return struct.unpack(">I", data)[0] if len(data) == 4 else 0


def read_i32(buf: io.BytesIO) -> int:
    data = buf.read(4)
    return struct.unpack(">i", data)[0] if len(data) == 4 else 0


def read_big_smart(buf: io.BytesIO) -> int:
    position = buf.tell()
    data = buf.read(1)
    if not data:
        return 0
    buf.seek(position)
    if data[0] < 128:
        return read_u16(buf)
    return read_i32(buf) & 0x7FFFFFFF


def decompress_container(data: bytes) -> bytes:
    if len(data) < 5:
        raise ValueError(f"container too small: {len(data)} bytes")

    compression = data[0]
    compressed_length = struct.unpack(">I", data[1:5])[0]

    if compression == COMPRESSION_NONE:
        return data[5 : 5 + compressed_length]

    if len(data) < 9:
        raise ValueError(f"compressed container too small: {len(data)} bytes")

    decompressed_length = struct.unpack(">I", data[5:9])[0]
    payload = data[9 : 9 + compressed_length]

    if compression == COMPRESSION_BZIP2:
        result = bz2.decompress(b"BZh1" + payload)
    elif compression == COMPRESSION_GZIP:
        result = gzip.decompress(payload)
    else:
        raise ValueError(f"unknown compression type: {compression}")

    if len(result) != decompressed_length:
        raise ValueError(
            "decompressed size mismatch: "
            f"expected {decompressed_length}, got {len(result)}"
        )
    return result


@dataclass
class IndexManifest:
    protocol: int = 0
    revision: int = 0
    has_names: bool = False
    group_ids: list[int] = field(default_factory=list)
    group_name_hashes: dict[int, int] = field(default_factory=dict)
    group_crcs: dict[int, int] = field(default_factory=dict)
    group_revisions: dict[int, int] = field(default_factory=dict)
    group_file_ids: dict[int, list[int]] = field(default_factory=dict)
    group_file_name_hashes: dict[int, dict[int, int]] = field(default_factory=dict)


def parse_index_manifest(data: bytes) -> IndexManifest:
    buf = io.BytesIO(data)
    manifest = IndexManifest()

    manifest.protocol = read_u8(buf)
    if manifest.protocol < 5 or manifest.protocol > 7:
        raise ValueError(f"unsupported reference table protocol: {manifest.protocol}")

    if manifest.protocol >= 6:
        manifest.revision = read_u32(buf)

    flags = read_u8(buf)
    manifest.has_names = bool(flags & 0x01)
    has_whirlpool = bool(flags & 0x02)
    has_lengths = bool(flags & 0x04)
    has_uncompressed_crc = bool(flags & 0x08)

    group_count = read_big_smart(buf) if manifest.protocol >= 7 else read_u16(buf)

    accumulator = 0
    for _ in range(group_count):
        delta = read_big_smart(buf) if manifest.protocol >= 7 else read_u16(buf)
        accumulator += delta
        manifest.group_ids.append(accumulator)

    if manifest.has_names:
        for group_id in manifest.group_ids:
            manifest.group_name_hashes[group_id] = read_i32(buf)

    for group_id in manifest.group_ids:
        manifest.group_crcs[group_id] = read_u32(buf)

    if has_whirlpool:
        buf.read(64 * group_count)
    if has_lengths:
        buf.read(8 * group_count)
    if has_uncompressed_crc:
        buf.read(4 * group_count)

    for group_id in manifest.group_ids:
        manifest.group_revisions[group_id] = read_u32(buf)

    file_counts: dict[int, int] = {}
    for group_id in manifest.group_ids:
        file_counts[group_id] = read_big_smart(buf) if manifest.protocol >= 7 else read_u16(buf)

    for group_id in manifest.group_ids:
        accumulator = 0
        file_ids: list[int] = []
        for _ in range(file_counts[group_id]):
            delta = read_big_smart(buf) if manifest.protocol >= 7 else read_u16(buf)
            accumulator += delta
            file_ids.append(accumulator)
        manifest.group_file_ids[group_id] = file_ids

    if manifest.has_names:
        for group_id in manifest.group_ids:
            file_hashes: dict[int, int] = {}
            for file_id in manifest.group_file_ids[group_id]:
                file_hashes[file_id] = read_i32(buf)
            manifest.group_file_name_hashes[group_id] = file_hashes

    return manifest


def split_group(data: bytes, file_ids: list[int]) -> dict[int, bytes]:
    if len(file_ids) == 1:
        return {file_ids[0]: data}

    file_count = len(file_ids)
    chunk_count = data[-1]
    if chunk_count < 1:
        raise ValueError(f"invalid chunk count: {chunk_count}")

    trailer_length = chunk_count * file_count * 4
    trailer_start = len(data) - 1 - trailer_length
    if trailer_start < 0:
        raise ValueError(
            f"group data too small for {chunk_count} chunks and {file_count} files"
        )

    size_buf = io.BytesIO(data[trailer_start : len(data) - 1])
    chunk_sizes: list[list[int]] = []
    for _ in range(chunk_count):
        chunk_running = 0
        sizes: list[int] = []
        for _ in range(file_count):
            chunk_running += read_i32(size_buf)
            sizes.append(chunk_running)
        chunk_sizes.append(sizes)

    output: dict[int, bytearray] = {file_id: bytearray() for file_id in file_ids}
    offset = 0
    for chunk in range(chunk_count):
        for file_index, file_id in enumerate(file_ids):
            size = chunk_sizes[chunk][file_index]
            output[file_id].extend(data[offset : offset + size])
            offset += size
    return {file_id: bytes(payload) for file_id, payload in output.items()}


def djb2(name: str) -> int:
    value = 0
    for character in name.lower():
        value = (value * 31 + ord(character)) & 0xFFFFFFFF
    if value >= 0x80000000:
        value -= 0x100000000
    return value


class Dat2CacheReader:
    def __init__(self, cache_dir: str | Path) -> None:
        self.cache_dir = Path(cache_dir)
        if not self.cache_dir.is_dir():
            raise FileNotFoundError(f"cache directory not found: {self.cache_dir}")
        self.main_dat = self.cache_dir / "main_file_cache.dat2"
        if not self.main_dat.is_file():
            raise FileNotFoundError(f"missing cache file: {self.main_dat}")
        self._manifest_cache: dict[int, IndexManifest] = {}

    def index_path(self, index_id: int) -> Path:
        return self.cache_dir / f"main_file_cache.idx{index_id}"

    def _read_sector_chain(self, index_id: int, archive_id: int) -> bytes | None:
        index_path = self.index_path(index_id)
        if not index_path.is_file():
            return None

        with index_path.open("rb") as index_file, self.main_dat.open("rb") as data_file:
            index_offset = archive_id * INDEX_SIZE
            index_file.seek(0, io.SEEK_END)
            index_length = index_file.tell()
            if index_length < index_offset + INDEX_SIZE:
                return None
            index_file.seek(index_offset)
            index_entry = index_file.read(INDEX_SIZE)
            if len(index_entry) != INDEX_SIZE:
                return None
            data_size = (index_entry[0] << 16) | (index_entry[1] << 8) | index_entry[2]
            sector_position = (index_entry[3] << 16) | (index_entry[4] << 8) | index_entry[5]
            if data_size < 0 or sector_position <= 0:
                return None

            output = bytearray(data_size)
            output_offset = 0
            chunk = 0
            big_sector = archive_id > 65535
            header_size = SECTOR_HEADER_SIZE_BIG if big_sector else SECTOR_HEADER_SIZE_SMALL
            data_chunk_size = SECTOR_DATA_SIZE_BIG if big_sector else SECTOR_DATA_SIZE_SMALL

            data_file.seek(0, io.SEEK_END)
            main_length = data_file.tell()

            while output_offset < data_size:
                if sector_position == 0:
                    return None
                if sector_position * SECTOR_SIZE > main_length:
                    return None
                to_read = min(data_chunk_size, data_size - output_offset)
                data_file.seek(sector_position * SECTOR_SIZE)
                sector = data_file.read(header_size + to_read)
                if len(sector) != header_size + to_read:
                    return None
                sector_buf = io.BytesIO(sector)
                archive_check = read_i32(sector_buf) if big_sector else read_u16(sector_buf)
                chunk_check = read_u16(sector_buf)
                next_sector = read_u24(sector_buf)
                index_check = read_u8(sector_buf)
                if archive_check != archive_id or chunk_check != chunk or index_check != index_id:
                    return None
                if next_sector < 0:
                    return None
                output[output_offset : output_offset + to_read] = sector[header_size:]
                output_offset += to_read
                sector_position = next_sector
                chunk += 1
            return bytes(output)

    def read_raw(self, index_id: int, group_id: int) -> bytes | None:
        return self._read_sector_chain(index_id, group_id)

    def read_container(self, index_id: int, group_id: int) -> bytes | None:
        raw = self.read_raw(index_id, group_id)
        if raw is None:
            return None
        return decompress_container(raw)

    def read_index_manifest(self, index_id: int) -> IndexManifest:
        cached = self._manifest_cache.get(index_id)
        if cached is not None:
            return cached
        data = self.read_container(255, index_id)
        if data is None:
            raise FileNotFoundError(f"manifest not found for index {index_id}")
        manifest = parse_index_manifest(data)
        self._manifest_cache[index_id] = manifest
        return manifest

    def read_group(self, index_id: int, group_id: int) -> dict[int, bytes]:
        manifest = self.read_index_manifest(index_id)
        if group_id not in manifest.group_file_ids:
            raise KeyError(f"group {group_id} not in index {index_id} manifest")
        data = self.read_container(index_id, group_id)
        if data is None:
            raise FileNotFoundError(f"group data not found: index={index_id} group={group_id}")
        return split_group(data, manifest.group_file_ids[group_id])

    def read_config_entry(self, group_id: int, entry_id: int) -> bytes:
        files = self.read_group(2, group_id)
        if entry_id not in files:
            raise KeyError(f"config entry {entry_id} not found in group {group_id}")
        return files[entry_id]

    def read_named_group(self, index_id: int, archive_name: str) -> bytes | None:
        manifest = self.read_index_manifest(index_id)
        archive_hash = djb2(archive_name)
        for group_id, name_hash in manifest.group_name_hashes.items():
            if name_hash == archive_hash:
                return self.read_container(index_id, group_id)
        return None
