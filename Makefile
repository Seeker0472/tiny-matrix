ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
FRAME_RTL := $(ROOT)/frame/rtl
ELAB_DIR := $(ROOT)/out/elaborate
MILL ?= mill
# Force PATH bash: Make's default SHELL is often /bin/sh (missing on some NixOS setups).
SHELL := $(shell command -v bash)

.PHONY: build test clean help

help:
	@printf '%s\n' \
	  'Targets:' \
	  '  build   Emit SystemVerilog from Chisel into frame/rtl' \
	  '  test    Run chiseltest suites' \
	  '  clean   Remove local Mill/elaborate outputs under out/'

build:
	rm -rf $(ELAB_DIR)
	mkdir -p $(ELAB_DIR)
	cd $(ROOT) && $(MILL) matrix.run matrix.Elaborate --target-dir $(ELAB_DIR)
	cp -f $(ELAB_DIR)/FrameMatrix.sv $(FRAME_RTL)/
	@printf 'Wrote %s/FrameMatrix.sv\n' $(FRAME_RTL)

test:
	cd $(ROOT) && $(MILL) matrix.test

clean:
	rm -rf $(ROOT)/out
	@printf 'Removed %s\n' $(ROOT)/out
