"""Adapter: read standard OSRS .dat2/.idx* cache files via ModernCacheReader API.

The PufferLib export scripts expect either:
  - CacheReader: 317-format (.dat + .idx*, bz2 archives)
  - ModernCacheReader: OpenRS2 flat file (numbered dirs)

Our cache is standard OSRS modern format (.dat2 + .idx*, gzip containers).
This adapter reads .dat2/.idx* using the RS2 sector chain format, then
provides the ModernCacheReader API (read_container, read_group, etc.)
so the export scripts can use it.

Usage:
    from dat2_cache_reader import Dat2CacheReader
    reader = Dat2CacheReader("/path/to/cache")
    # Now use exactly like ModernCacheReader
"""

import struct
import sys
from pathlib import Path

# Import the modern cache reader's manifest/container parsing
_this_dir = Path(__file__).resolve().parent
_root_dir = _this_dir.parent.parent.parent  # claude/
scripts_dir = _root_dir / "pufferlib" / "pufferlib" / "ocean" / "osrs_pvp" / "scripts"
sys.path.insert(0, str(scripts_dir))
from modern_cache_reader import (
    IndexManifest,
    decompress_container,
    parse_index_manifest,
    split_group,
)

INDEX_SIZE = 6
SECTOR_SIZE = 520
SECTOR_HEADER_SIZE = 8
SECTOR_DATA_SIZE = SECTOR_SIZE - SECTOR_HEADER_SIZE
# Extended sector header for file IDs > 65535
SECTOR_HEADER_SIZE_EXT = 10
SECTOR_DATA_SIZE_EXT = SECTOR_SIZE - SECTOR_HEADER_SIZE_EXT


def _read_medium(data: bytes, offset: int) -> int:
    return (data[offset] << 16) | (data[offset + 1] << 8) | data[offset + 2]


class Dat2CacheReader:
    """Reads standard OSRS .dat2 + .idx* cache files, provides ModernCacheReader API."""

    def __init__(self, cache_dir: str | Path) -> None:
        cache_dir = Path(cache_dir)
        dat2_path = cache_dir / "main_file_cache.dat2"
        if not dat2_path.exists():
            raise FileNotFoundError(f"main_file_cache.dat2 not found in {cache_dir}")

        self.data_bytes = dat2_path.read_bytes()
        self.idx_bytes: dict[int, bytes] = {}
        for idx_path in sorted(cache_dir.glob("main_file_cache.idx*")):
            idx_id = int(idx_path.suffix.replace(".idx", ""))
            self.idx_bytes[idx_id] = idx_path.read_bytes()

        self._manifest_cache: dict[int, IndexManifest] = {}

    def _read_raw_sectors(self, cache_id: int, file_id: int) -> bytes | None:
        """Read raw data from cache using RS2 sector chain."""
        idx_data = self.idx_bytes.get(cache_id)
        if idx_data is None:
            return None

        idx_offset = file_id * INDEX_SIZE
        if idx_offset + INDEX_SIZE > len(idx_data):
            return None

        length = _read_medium(idx_data, idx_offset)
        sector_id = _read_medium(idx_data, idx_offset + 3)

        if length <= 0 or sector_id <= 0:
            return None

        # Use extended header for large file IDs
        use_ext = file_id > 65535
        hdr_size = SECTOR_HEADER_SIZE_EXT if use_ext else SECTOR_HEADER_SIZE
        data_per_sector = SECTOR_SIZE - hdr_size

        result = bytearray()
        chunk = 0

        while len(result) < length:
            read_size = min(length - len(result), data_per_sector)
            file_offset = sector_id * SECTOR_SIZE

            if file_offset + hdr_size + read_size > len(self.data_bytes):
                return None

            hdr = self.data_bytes[file_offset : file_offset + hdr_size]

            if use_ext:
                next_sector = (hdr[6] << 16) | (hdr[7] << 8) | hdr[8]
            else:
                next_sector = (hdr[4] << 16) | (hdr[5] << 8) | hdr[6]

            data_start = file_offset + hdr_size
            result.extend(self.data_bytes[data_start : data_start + read_size])

            sector_id = next_sector
            chunk += 1

        return bytes(result[:length])

    def read_container(self, index_id: int, group_id: int) -> bytes | None:
        """Read and decompress a container from the cache."""
        raw = self._read_raw_sectors(index_id, group_id)
        if raw is None:
            return None
        return decompress_container(raw)

    def read_index_manifest(self, index_id: int) -> IndexManifest:
        """Read and parse the reference table (manifest) for an index."""
        if index_id in self._manifest_cache:
            return self._manifest_cache[index_id]

        data = self.read_container(255, index_id)
        if data is None:
            raise FileNotFoundError(f"manifest not found for index {index_id}")

        manifest = parse_index_manifest(data)
        self._manifest_cache[index_id] = manifest
        return manifest

    def read_group(self, index_id: int, group_id: int) -> dict[int, bytes]:
        """Read a group and split into individual file entries."""
        manifest = self.read_index_manifest(index_id)

        if group_id not in manifest.group_file_ids:
            raise KeyError(f"group {group_id} not in index {index_id} manifest")

        data = self.read_container(index_id, group_id)
        if data is None:
            raise FileNotFoundError(
                f"group data not found: index={index_id} group={group_id}"
            )

        file_ids = manifest.group_file_ids[group_id]
        return split_group(data, file_ids)

    def read_config_entry(self, group_id: int, entry_id: int) -> bytes:
        """Read a single config entry from index 2."""
        files = self.read_group(2, group_id)
        if entry_id not in files:
            raise KeyError(f"config entry {entry_id} not found in group {group_id}")
        return files[entry_id]
