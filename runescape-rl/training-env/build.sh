#!/bin/bash
set -e

# Build Fight Caves for PufferLib 4.0 training.
#
# Usage:
#   ./build.sh                  # Default CUDA build
#   ./build.sh --cpu            # CPU-only (no CUDA)
#   ./build.sh --local          # Standalone debug binary
#   ./build.sh --fast           # Standalone optimized binary
#
# Prerequisites:
#   - PufferLib 4.0 cloned at $PUFFERLIB_DIR
#   - Python with pybind11, numpy installed
#   - CUDA toolkit (for GPU build) or --cpu flag

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
PUFFERLIB_DIR="${PUFFERLIB_DIR:-/home/joe/projects/runescape-rl-reference/pufferlib_4}"

if [ ! -d "$PUFFERLIB_DIR/src" ]; then
    echo "Error: PufferLib not found at $PUFFERLIB_DIR"
    echo "Set PUFFERLIB_DIR or clone PufferLib 4.0"
    exit 1
fi

# Parse args
MODE=""
PRECISION=""
for arg in "$@"; do
    case $arg in
        --cpu)   MODE=cpu; PRECISION="-DPRECISION_FLOAT" ;;
        --local) MODE=local ;;
        --fast)  MODE=fast ;;
        --float) PRECISION="-DPRECISION_FLOAT" ;;
        --debug) DEBUG=1 ;;
        *) echo "Unknown arg: $arg" && exit 1 ;;
    esac
done

ENV=fight_caves
SRC_DIR="$SCRIPT_DIR"
BINDING_SRC="$SRC_DIR/binding.c"

# Work from PufferLib directory (so relative paths to src/ work)
cd "$PUFFERLIB_DIR"

# Raylib
RAYLIB_NAME='raylib-5.5_linux_amd64'
if [ ! -d "$RAYLIB_NAME" ]; then
    echo "Downloading Raylib..."
    curl -sL "https://github.com/raysan5/raylib/releases/download/5.5/$RAYLIB_NAME.tar.gz" \
        -o "$RAYLIB_NAME.tar.gz" && tar xf "$RAYLIB_NAME.tar.gz" && rm "$RAYLIB_NAME.tar.gz"
fi
RAYLIB_A="$RAYLIB_NAME/lib/libraylib.a"

# Compiler settings
CLANG_WARN=(-Wall -ferror-limit=3 -Werror=return-type
    -Wno-error=incompatible-pointer-types-discards-qualifiers
    -Wno-incompatible-pointer-types-discards-qualifiers)

if [ -n "$DEBUG" ] || [ "$MODE" = "local" ]; then
    CLANG_OPT=(-g -O0 "${CLANG_WARN[@]}")
else
    CLANG_OPT=(-O2 -DNDEBUG "${CLANG_WARN[@]}")
fi

mkdir -p build

# Standalone executable (for testing without Python)
if [ "$MODE" = "local" ] || [ "$MODE" = "fast" ]; then
    echo "Compiling standalone $ENV..."
    ${CC:-clang} "${CLANG_OPT[@]}" \
        -I"$PUFFERLIB_DIR/src" -I"$SRC_DIR" -I"$SRC_DIR/src" \
        -I"$RAYLIB_NAME/include" \
        -DPLATFORM_DESKTOP -DFC_NO_HASH \
        "$SRC_DIR/$ENV.c" -o "$ENV" \
        "$RAYLIB_A" \
        -lGL -lm -lpthread -fopenmp
    echo "Built: ./$ENV"
    exit 0
fi

# Compile static library from binding.c
STATIC_OBJ="build/libstatic_${ENV}.o"
STATIC_LIB="build/libstatic_${ENV}.a"

echo "Compiling static library for $ENV..."
${CC:-clang} -c "${CLANG_OPT[@]}" \
    -I"$PUFFERLIB_DIR/src" -I"$SRC_DIR" -I"$SRC_DIR/src" \
    -I"$RAYLIB_NAME/include" \
    -DPLATFORM_DESKTOP -DFC_NO_HASH \
    -fno-semantic-interposition -fvisibility=hidden \
    -fPIC -fopenmp \
    "$BINDING_SRC" -o "$STATIC_OBJ"
ar rcs "$STATIC_LIB" "$STATIC_OBJ"

# Extract OBS_TENSOR_T from binding.c
OBS_TENSOR_T=$(awk '/^#define OBS_TENSOR_T/{print $3}' "$BINDING_SRC")
if [ -z "$OBS_TENSOR_T" ]; then
    echo "Error: Could not find OBS_TENSOR_T in $BINDING_SRC"
    exit 1
fi

# Python paths
PYTHON_INCLUDE=$(python -c "import sysconfig; print(sysconfig.get_path('include'))")
PYBIND_INCLUDE=$(python -c "import pybind11; print(pybind11.get_include())")
NUMPY_INCLUDE=$(python -c "import numpy; print(numpy.get_include())")
EXT_SUFFIX=$(python -c "import sysconfig; print(sysconfig.get_config_var('EXT_SUFFIX'))")
OUTPUT="pufferlib/_C${EXT_SUFFIX}"

if [ "$MODE" = "cpu" ]; then
    echo "Compiling CPU training backend..."
    ${CXX:-g++} -c -fPIC -fopenmp \
        -D_GLIBCXX_USE_CXX11_ABI=1 \
        -DPLATFORM_DESKTOP \
        -std=c++17 \
        -I. -Isrc \
        -I"$PYTHON_INCLUDE" -I"$PYBIND_INCLUDE" \
        -DOBS_TENSOR_T=$OBS_TENSOR_T \
        -DENV_NAME=$ENV \
        $PRECISION -O2 \
        src/bindings_cpu.cpp -o build/bindings_cpu.o

    ${CXX:-g++} -shared -fPIC -fopenmp \
        build/bindings_cpu.o "$STATIC_LIB" "$RAYLIB_A" \
        -lm -lpthread -lomp5 -O2 \
        -Bsymbolic-functions \
        -o "$OUTPUT"
    echo "Built: $OUTPUT"
else
    # CUDA build
    CUDA_HOME=${CUDA_HOME:-${CUDA_PATH:-$(dirname "$(dirname "$(which nvcc)")")}}
    CUDNN_IFLAG=""
    CUDNN_LFLAG=""
    for dir in /usr/local/cuda/include /usr/include; do
        [ -f "$dir/cudnn.h" ] && CUDNN_IFLAG="-I$dir" && break
    done
    for dir in /usr/local/cuda/lib64 /usr/lib/x86_64-linux-gnu; do
        [ -f "$dir/libcudnn.so" ] && CUDNN_LFLAG="-L$dir" && break
    done
    if [ -z "$CUDNN_IFLAG" ]; then
        CUDNN_IFLAG=$(python -c "import nvidia.cudnn, os; print('-I' + os.path.join(nvidia.cudnn.__path__[0], 'include'))" 2>/dev/null || echo "")
    fi
    if [ -z "$CUDNN_LFLAG" ]; then
        CUDNN_LFLAG=$(python -c "import nvidia.cudnn, os; print('-L' + os.path.join(nvidia.cudnn.__path__[0], 'lib'))" 2>/dev/null || echo "")
    fi

    if [ -n "$NVCC_ARCH" ]; then
        ARCH=$NVCC_ARCH
    elif command -v nvidia-smi &>/dev/null; then
        GPU_CC=$(nvidia-smi --query-gpu=compute_cap --format=csv,noheader 2>/dev/null | head -1 | tr -d '.')
        ARCH=${GPU_CC:+sm_$GPU_CC}
        ARCH=${ARCH:-native}
    else
        ARCH=native
    fi

    NVCC="ccache $CUDA_HOME/bin/nvcc"

    echo "Compiling CUDA ($ARCH) training backend..."
    $NVCC -c -arch=$ARCH -Xcompiler -fPIC \
        -Xcompiler=-D_GLIBCXX_USE_CXX11_ABI=1 \
        -Xcompiler=-DNPY_NO_DEPRECATED_API=NPY_1_7_API_VERSION \
        -Xcompiler=-DPLATFORM_DESKTOP \
        -std=c++17 \
        -I. -Isrc \
        -I"$PYTHON_INCLUDE" -I"$PYBIND_INCLUDE" -I"$NUMPY_INCLUDE" \
        -I"$CUDA_HOME/include" $CUDNN_IFLAG -I"$RAYLIB_NAME/include" \
        -Xcompiler=-fopenmp \
        -DOBS_TENSOR_T=$OBS_TENSOR_T \
        -DENV_NAME=$ENV \
        $PRECISION -O2 --threads 0 \
        src/bindings.cu -o build/bindings.o

    ${CXX:-g++} -shared -fPIC -fopenmp \
        build/bindings.o "$STATIC_LIB" "$RAYLIB_A" \
        -L"$CUDA_HOME/lib64" $CUDNN_LFLAG \
        -lcudart -lnccl -lnvidia-ml -lcublas -lcusolver -lcurand -lcudnn \
        -lomp5 -O2 \
        -Bsymbolic-functions \
        -o "$OUTPUT"
    echo "Built: $OUTPUT"
fi

echo "Done. Run: cd $PUFFERLIB_DIR && puffer train fight_caves"
