package main

/*
#cgo LDFLAGS: -llzma

#include <lzma.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

// Wrapper untuk lzma_stream_decoder.
// Menyediakan interface streaming decoder yang aman dipanggil dari Go.
typedef struct {
    lzma_stream strm;
    int initialized;
} lzma_decoder_t;

// init_decoder: initialize lzma stream decoder.
// Returns 0 on success, or an lzma_ret error code on failure.
int init_decoder(lzma_decoder_t *d) {
    memset(d, 0, sizeof(lzma_decoder_t));
    // UINT64_MAX = no memory limit
    // LZMA_CONCATENATED = allow concatenated .xz streams
    lzma_ret ret = lzma_stream_decoder(&d->strm, UINT64_MAX, LZMA_CONCATENATED);
    if (ret == LZMA_OK) {
        d->initialized = 1;
        return 0;
    }
    return (int)ret;
}

// decode_chunk: feed input to lzma_code() and produce output.
//   in     - input buffer
//   in_len - input size
//   out    - output buffer
//   out_len - [in] output buffer capacity, [out] bytes written
//   action - LZMA_RUN (0) or LZMA_FINISH (1)
// Returns:
//   0  = LZMA_OK (more input/output needed)
//   1  = LZMA_STREAM_END (done)
//   -1 = error
int decode_chunk(lzma_decoder_t *d, const uint8_t *in, size_t in_len,
                 uint8_t *out, size_t *out_len, int action) {
    if (!d->initialized) return -1;

    d->strm.next_in  = in;
    d->strm.avail_in = in_len;
    d->strm.next_out  = out;
    d->strm.avail_out = *out_len;

    lzma_ret ret = lzma_code(&d->strm, (lzma_action)action);

    *out_len = (size_t)((uintptr_t)d->strm.next_out - (uintptr_t)out);

    switch (ret) {
        case LZMA_OK:
            return 0;
        case LZMA_STREAM_END:
            return 1;
        default:
            return -1;
    }
}

// end_decoder: release all resources.
void end_decoder(lzma_decoder_t *d) {
    if (d->initialized) {
        lzma_end(&d->strm);
        d->initialized = 0;
    }
}
*/
import "C"
import (
	"errors"
	"io"
)

// cgoXzReader wraps liblzma's lzma_stream_decoder as an io.ReadCloser.
// This replaces the pure-Go ulikunitz/xz reader with a CGO-based native
// liblzma implementation, giving 5-10x faster decompression on ARM64.
type cgoXzReader struct {
	src     io.Reader
	dec     C.lzma_decoder_t
	inBuf   []byte
	outBuf  []byte
	outOff  int
	outEnd  int
	streamEnded bool
	srcEof  bool
}

// newXzReader creates a new xz/lzma decompression reader using native liblzma.
func newXzReader(r io.Reader) (io.ReadCloser, error) {
	cr := &cgoXzReader{
		src:    r,
		inBuf:  make([]byte, 65536),   // 64KB input buffer
		outBuf: make([]byte, 65536),   // 64KB output buffer
	}
	ret := C.init_decoder(&cr.dec)
	if ret != 0 {
		return nil, errors.New("cgo_lzma: lzma_stream_decoder init failed")
	}
	return cr, nil
}

func (cr *cgoXzReader) Read(p []byte) (int, error) {
	// If we have buffered output, serve from buffer first
	if cr.outOff < cr.outEnd {
		n := copy(p, cr.outBuf[cr.outOff:cr.outEnd])
		cr.outOff += n
		return n, nil
	}

	if cr.streamEnded {
		return 0, io.EOF
	}

	// Reset output buffer
	cr.outOff = 0
	cr.outEnd = 0

	// Main loop: read input, decode, produce output
	for {
		// If we have no input buffered, read more
		if !cr.srcEof && len(cr.inBuf) > 0 {
			n, err := cr.src.Read(cr.inBuf)
			if err != nil && err != io.EOF {
				return 0, err
			}
			if n == 0 || err == io.EOF {
				cr.srcEof = true
			}

			if n > 0 {
				// Determine action: LZMA_RUN if more data expected, LZMA_FINISH if EOF
				action := 0 // C.LZMA_RUN
				if cr.srcEof {
					action = 1 // C.LZMA_FINISH
				}

				var outCap C.size_t = C.size_t(len(cr.outBuf))
				ret := C.decode_chunk(
					&cr.dec,
					(*C.uint8_t)(&cr.inBuf[0]),
					C.size_t(n),
					(*C.uint8_t)(&cr.outBuf[0]),
					&outCap,
					C.int(action),
				)
				cr.outEnd = int(outCap)

				if ret < 0 {
					C.end_decoder(&cr.dec)
					return 0, errors.New("cgo_lzma: lzma_code failed")
				}

				if ret == 1 { // LZMA_STREAM_END
					cr.streamEnded = true
				}

				// If we produced output, return it
				if cr.outEnd > 0 {
					n = copy(p, cr.outBuf[:cr.outEnd])
					if n < cr.outEnd {
						cr.outOff = n
						cr.outEnd = cr.outEnd
					} else {
						cr.outOff = cr.outEnd
					}
					return n, nil
				}

				// If stream ended but no output, signal EOF
				if cr.streamEnded {
					return 0, io.EOF
				}
				// Otherwise, loop to read more input
				continue
			}

			// srcEof with no bytes read
			if cr.srcEof {
				// Try LZMA_FINISH to flush any pending decoded data
				var outCap C.size_t = C.size_t(len(cr.outBuf))
				ret := C.decode_chunk(
					&cr.dec,
					(*C.uint8_t)(nil),
					C.size_t(0),
					(*C.uint8_t)(&cr.outBuf[0]),
					&outCap,
					C.int(1), // LZMA_FINISH
				)
				cr.outEnd = int(outCap)

				if ret < 0 {
					C.end_decoder(&cr.dec)
					return 0, errors.New("cgo_lzma: lzma_code finalize failed")
				}

				cr.streamEnded = true

				if cr.outEnd > 0 {
					n := copy(p, cr.outBuf[:cr.outEnd])
					if n < cr.outEnd {
						cr.outOff = n
					}
					return n, nil
				}
				return 0, io.EOF
			}
		}
	}
}

func (cr *cgoXzReader) Close() error {
	C.end_decoder(&cr.dec)
	return nil
}
