// Generates a small synthetic payload for testing the pinned dumper.
package main

import (
	"encoding/binary"
	"os"

	engine "github.com/ssut/payload-dumper-go/chromeos_update_engine"
	"google.golang.org/protobuf/proto"
)

func main() {
	data := make([]byte, 4096)
	copy(data, "AFFT test image")
	manifest := &engine.DeltaArchiveManifest{
		Partitions: []*engine.PartitionUpdate{{
			PartitionName:    proto.String("system"),
			NewPartitionInfo: &engine.PartitionInfo{Size: proto.Uint64(uint64(len(data)))},
			Operations: []*engine.InstallOperation{{
				Type:       engine.InstallOperation_REPLACE.Enum(),
				DataOffset: proto.Uint64(0),
				DataLength: proto.Uint64(uint64(len(data))),
				DstExtents: []*engine.Extent{{
					StartBlock: proto.Uint64(0),
					NumBlocks:  proto.Uint64(1),
				}},
			}},
		}},
	}
	if len(os.Args) > 2 && os.Args[2] == "bad-hash" {
		manifest.Partitions[0].Operations[0].DataSha256Hash = make([]byte, 32)
	}
	encoded, err := proto.Marshal(manifest)
	if err != nil {
		panic(err)
	}
	f, err := os.Create(os.Args[1])
	if err != nil {
		panic(err)
	}
	defer f.Close()
	if _, err = f.Write([]byte("CrAU")); err != nil {
		panic(err)
	}
	for _, v := range []any{uint64(2), uint64(len(encoded)), uint32(0)} {
		if err = binary.Write(f, binary.BigEndian, v); err != nil {
			panic(err)
		}
	}
	if _, err = f.Write(encoded); err != nil {
		panic(err)
	}
	if _, err = f.Write(data); err != nil {
		panic(err)
	}
}
